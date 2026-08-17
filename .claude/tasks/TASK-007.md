# TASK-007 — Local media scanner

Milestone: v0.2.0 Backup — "Local media scanner: enumerate DCIM albums"
Depends on: TASK-006 (upload transport)
Blocks: TASK-008 (upload ledger), TASK-009 (backup worker)

## Goal
Enumerate the photos and videos on the device, grouped by album, so the backup worker knows what
exists. This task only *reads and reports* — deciding what still needs uploading is TASK-008's job,
and doing the uploading is TASK-009's.

## Permissions — approved by Ian, now in the manifest
`READ_MEDIA_IMAGES`, `READ_MEDIA_VIDEO`, `READ_MEDIA_VISUAL_USER_SELECTED`, and
`READ_EXTERNAL_STORAGE` capped at `maxSdkVersion="32"`.

## Read through MediaStore, never through raw file paths
Query `MediaStore.Images` and `MediaStore.Video` and keep the **content URI** for each item.

Do not build a `java.io.File` from the `DATA` column and do not walk `/storage/emulated/0/DCIM`
directly. Direct path access to media is restricted under scoped storage and behaves differently
across API 26 → 37; the content URI works everywhere and is the only thing guaranteed to remain
readable. `DATA` may be read for display or debugging, never for opening bytes.

### Consequence for TASK-006 — flag, do not fix here
`ChunkedUploader.upload` currently takes a `java.io.File`. Scanner output is content URIs, so the
uploader will need a source abstraction (name + size + random-access reads) with implementations
for a `File` and for a `ContentResolver` descriptor. **Do that in TASK-009 when the two are wired
together**, not here — TASK-006 is verified working and should not be churned speculatively.

## What to build

### 1. `domain/model/LocalMediaItem.kt`
```
data class LocalMediaItem(
    val mediaStoreId: Long,
    val contentUri: Uri,        // how the bytes are actually opened
    val displayName: String,
    val album: String,          // BUCKET_DISPLAY_NAME, e.g. "Camera"
    val relativePath: String?,  // e.g. "DCIM/Camera/" — null before API 29
    val sizeBytes: Long,
    val dateModifiedEpochSeconds: Long,
    val mimeType: String,
    val isVideo: Boolean
)
```
`Uri` is an Android type, so this sits in `data/`, not `domain/` — CLAUDE.md requires the domain
layer to stay Android-free. Put it in `data/local/media/`.

### 2. `data/local/media/MediaScanner.kt`
- `suspend fun scanAlbums(): List<MediaAlbum>` — album name, item count, total bytes
- `suspend fun scanAlbum(album: String): List<LocalMediaItem>`
- `suspend fun scanAll(): List<LocalMediaItem>`

Details that matter:
- Query images and videos **separately** (different collections) and merge.
- On API 29+ use `MediaStore.VOLUME_EXTERNAL`; below that, `EXTERNAL_CONTENT_URI`.
- Sort by `DATE_MODIFIED DESC` so the newest media is offered first — if a backup run is cut
  short, the most recent photos should already be safe.
- Skip items where `SIZE` is 0 or null: a placeholder row for a file still being written will
  fail to upload and would retry forever.
- Skip `IS_PENDING = 1` rows on API 29+ for the same reason.
- Run on the IO dispatcher; close every cursor with `use {}`.

### 3. `data/local/media/MediaPermissionState.kt`
Reports what the app is actually allowed to see:
```
enum class MediaAccess { FULL, PARTIAL, NONE }
```
- `FULL` — `READ_MEDIA_IMAGES` and `READ_MEDIA_VIDEO` granted (or `READ_EXTERNAL_STORAGE` ≤ API 32)
- `PARTIAL` — API 34+ with only `READ_MEDIA_VISUAL_USER_SELECTED` granted
- `NONE` — nothing granted

**`PARTIAL` must never be treated as `FULL`.** A backup app that reports success while it can only
see the twelve photos the user happened to select is actively harmful: the user believes their
library is safe when it is not. The UI must say plainly that access is limited and offer a route to
grant full access.

## Test requirements
`app/src/test/` with Robolectric or a fake `ContentResolver` — MediaStore cannot be queried on a
plain JVM:
- items are grouped into the right albums
- items sorted newest first
- zero-size items are excluded
- `IS_PENDING` items are excluded on API 29+
- an empty MediaStore yields an empty list, not a crash
- `MediaAccess` resolves to FULL / PARTIAL / NONE for each permission combination, including the
  API 34 partial case
- an item whose `BUCKET_DISPLAY_NAME` is null falls back to a sensible album name rather than
  throwing

## Acceptance criteria
1. Scanning returns real albums matching what Samsung Gallery shows.
2. Every item carries a usable content URI.
3. Partial access is reported as `PARTIAL`, never silently as full.
4. `./gradlew assembleDebug testDebugUnitTest` green.

## Out of scope
- Deciding what needs uploading (TASK-008)
- Uploading (TASK-009)
- Requesting permissions from the UI — the scanner reports state; the UI asks
- Watching for new media in real time; a scan on each run is enough for v0.2.0

## Report back
Files created, the exact permission set, and confirmation that no code path opens media by file path.
