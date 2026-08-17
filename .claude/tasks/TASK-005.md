# TASK-005 — ContentProvider skeleton

Milestone: v0.1.0 Foundation — "ContentProvider skeleton (registers with Android, returns empty cursor)"
Depends on: TASK-001, TASK-002
Blocks: v0.2.0 "ContentProvider serves real media to third-party apps"

## Goal
Register a ContentProvider with Android that other apps can address, and have it return a
**well-formed empty cursor**. Real media serving is v0.2.0. The value of this task is getting
the authority, the URI contract, and the manifest registration nailed down early, because
changing a published authority later breaks every third-party integration.

## Security decision baked into this spec — read it
The provider ships **`android:exported="false"`** in v0.1.0.

Rationale: an exported provider is a live attack surface, and this one returns nothing useful
yet. Exporting it is a deliberate, security-relevant decision that belongs to Ian, not to a
skeleton task. The Lead Agent has flagged the export flip as an open item for Ian.
**Do not set `exported="true"`.** Do not add `<path-permission>`, custom permissions, or
`<grant-uri-permission>` beyond what is specified below.

## What to build

### 1. `provider/MediaContract.kt`
The public contract. Third-party apps (and our own v0.2.0 code) address the provider through
this and nothing else.
```
object MediaContract {
    const val AUTHORITY = "com.gallery.sync.provider"
    val CONTENT_URI: Uri = Uri.parse("content://$AUTHORITY/media")

    object Columns {
        const val ID = "_id"
        const val DISPLAY_NAME = "_display_name"   // OpenableColumns.DISPLAY_NAME
        const val SIZE = "_size"                   // OpenableColumns.SIZE
        const val MIME_TYPE = "mime_type"
        const val DATE_MODIFIED = "date_modified"
        const val SOURCE = "source"
    }

    val DEFAULT_PROJECTION = arrayOf(...all of the above...)
}
```
Use the literal column names `_id`, `_display_name`, `_size` — third-party apps (CapCut is the
stated target) read `OpenableColumns`, so these names are load-bearing. Do not "clean them up".

`AUTHORITY` must be a compile-time constant matching the manifest exactly. Hardcoding the
string here is correct; do not derive it from `BuildConfig.APPLICATION_ID` at runtime, because
the manifest cannot use a Kotlin constant and the two would drift.

### 2. `provider/MediaUriMatcher.kt` — pure, testable URI logic
`android.content.UriMatcher` and `Uri` are android.jar stubs and are painful in unit tests.
Extract the routing decision into pure Kotlin operating on the **path segments**, not on `Uri`:
```
internal enum class MediaRoute { MEDIA_DIRECTORY, MEDIA_ITEM, UNKNOWN }

internal object MediaUriMatcher {
    fun match(pathSegments: List<String>): MediaRoute
    fun itemIdFrom(pathSegments: List<String>): String?
}
```
- `["media"]` -> `MEDIA_DIRECTORY`
- `["media", "<id>"]` -> `MEDIA_ITEM`, and `itemIdFrom` returns `<id>`
- `[]`, `["bogus"]`, `["media","a","b"]` -> `UNKNOWN`
This is the part that gets real unit-test coverage.

### 3. `provider/MediaContentProvider.kt`
`class MediaContentProvider : ContentProvider()`

- `onCreate(): Boolean` — return `true`. Log via `Logger`. Do **no** heavy work here; it runs on
  the main thread before `Application.onCreate` completes. Do not touch Room or Retrofit here.
- `query(uri, projection, selection, selectionArgs, sortOrder): Cursor?`
  - Route via `MediaUriMatcher.match(uri.pathSegments)`.
  - `MEDIA_DIRECTORY` and `MEDIA_ITEM` -> return an **empty `MatrixCursor`** built with
    `projection ?: MediaContract.DEFAULT_PROJECTION`. Zero rows. Not null.
  - `UNKNOWN` -> throw `IllegalArgumentException("Unknown URI: $uri")`. That is the documented
    ContentProvider contract for a bad URI; returning null would be wrong.
  - Call `cursor.setNotificationUri(context?.contentResolver, uri)` so v0.2.0 can push updates.
- `getType(uri)`:
  - `MEDIA_DIRECTORY` -> `"vnd.android.cursor.dir/vnd.com.gallery.sync.media"`
  - `MEDIA_ITEM` -> `"vnd.android.cursor.item/vnd.com.gallery.sync.media"`
  - `UNKNOWN` -> `null`
- `insert`, `update` -> `throw UnsupportedOperationException("...read-only in v0.1.0")`
- `delete` -> `throw UnsupportedOperationException(...)`. Add a KDoc line: deletion is never
  supported through this provider because CLAUDE.md forbids deleting a user's cloud file; any
  future implementation may only evict the local cache.
- `openFile(uri, mode)` -> `throw FileNotFoundException("On-demand download lands in v0.2.0")`,
  with `// TODO(v0.2.0): on-demand download`.
- **No Hilt field injection.** A ContentProvider is created before the Hilt component is ready.
  When v0.2.0 needs dependencies, it will use an `EntryPointAccessors` lookup. Add nothing now.

### 4. `AndroidManifest.xml`
Inside `<application>`:
```
<provider
    android:name=".provider.MediaContentProvider"
    android:authorities="com.gallery.sync.provider"
    android:exported="false"
    android:grantUriPermissions="true" />
```
`grantUriPermissions="true"` is what will let us hand out per-URI temporary read grants in
v0.2.0 without exporting the whole provider. It is safe with `exported="false"`.

## Test requirements

### Unit — `app/src/test/java/com/gallery/sync/provider/MediaUriMatcherTest.kt`
- `["media"]` -> MEDIA_DIRECTORY
- `["media","abc123"]` -> MEDIA_ITEM; `itemIdFrom` -> `"abc123"`
- empty list -> UNKNOWN
- `["notmedia"]` -> UNKNOWN
- `["media","a","b"]` -> UNKNOWN
- `itemIdFrom(["media"])` -> null
- URL-encoded id with a colon (`onedrive%3AABC`) decodes/round-trips as expected — note the
  provider receives already-decoded segments from `Uri.pathSegments`, so assert on the decoded form.

### Unit — `app/src/test/java/com/gallery/sync/provider/MediaContractTest.kt`
- `AUTHORITY` equals `"com.gallery.sync.provider"` **and equals the value in AndroidManifest.xml**
  (assert the constant; a mismatch here is the single most expensive bug in this task)
- `DEFAULT_PROJECTION` contains `_id`, `_display_name`, `_size`

### Instrumented — `app/src/androidTest/java/com/gallery/sync/provider/MediaContentProviderTest.kt`
Requires a connected device/emulator; mark it clearly with a TODO. Cover:
- `contentResolver.query(MediaContract.CONTENT_URI, null, null, null, null)` returns a non-null
  cursor with `count == 0` and `columnNames` matching `DEFAULT_PROJECTION`
- querying a bogus URI throws `IllegalArgumentException`
- `getType` returns the dir/item MIME strings
- `delete`/`insert` throw `UnsupportedOperationException`
These will NOT run in this environment. Do not claim they passed.

## Acceptance criteria
1. Provider is registered in `AndroidManifest.xml` with `exported="false"`.
2. `query()` returns an empty, non-null cursor with the right columns for valid URIs.
3. `delete()` cannot delete anything.
4. Unit tests pass; `./gradlew assembleDebug testDebugUnitTest` green.

## Out of scope
- Serving real media, `openFile` implementation, thumbnails (v0.2.0)
- Reading from Room (v0.2.0)
- Exporting the provider or defining custom permissions (Ian's decision)
- `call()`, `openTypedAssetFile`, `bulkInsert`

## Report back
Files created, the exact manifest block added, and confirmation that the authority string in
`MediaContract.AUTHORITY` is byte-identical to the manifest's `android:authorities`.
