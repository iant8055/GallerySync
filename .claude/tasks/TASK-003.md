# TASK-003 — Room database schema for the cached media index

Milestone: v0.1.0 Foundation — "Room database schema for cached media index"
Depends on: TASK-001, TASK-002
Blocks: v0.2.0 sync work

## Goal
The local index of cloud-hosted media. This is a **cache and index only** — it is never the
source of truth. CLAUDE.md hard rules that apply directly here:
- "Never permanently delete a user's cloud file — local cache only"
- "All file operations on device are cache management, never source-of-truth writes"

This is database **version 1**, initial creation. No migration is required, therefore no
escalation. If you find yourself needing to change an already-shipped schema, STOP and
report — that is an escalation to Ian.

## What to build — package `com.gallery.sync.data.local`

### 1. `data/local/entity/MediaSource.kt`
```
enum class MediaSource { ONEDRIVE, GOOGLE_PHOTOS }
```
(Google Photos is Pro-gated at the feature level in v0.3.0; the enum value existing here gates
nothing and is correct.)

### 2. `data/local/entity/MediaItemEntity.kt`
Table `media_items`. Columns:

| field | type | notes |
|---|---|---|
| `id` | String | **PK**. Stable composite: `"${source.name.lowercase()}:$remoteId"` |
| `remoteId` | String | provider-native id |
| `source` | MediaSource | stored via converter |
| `name` | String | file name |
| `mimeType` | String | e.g. `image/jpeg` |
| `sizeBytes` | Long | |
| `widthPx` | Int? | nullable |
| `heightPx` | Int? | nullable |
| `createdAtUtc` | Long? | epoch millis |
| `modifiedAtUtc` | Long | epoch millis |
| `parentFolderId` | String? | FK-ish ref to `media_folders.id`, nullable = root |
| `eTag` | String? | change detection |
| `localCachePath` | String? | null = not cached locally |
| `cachedAtUtc` | Long? | |
| `lastAccessedUtc` | Long? | drives LRU eviction in v0.2.0 |

Indices on `parentFolderId`, `source`, and `lastAccessedUtc`.
Do **not** declare a real `@ForeignKey` to folders — cloud listings arrive out of order and a
FK constraint would reject a child that arrives before its parent. Add a comment saying so.

### 3. `data/local/entity/MediaFolderEntity.kt`
Table `media_folders`. Columns: `id` (PK, same composite scheme), `remoteId`, `source`,
`name`, `parentFolderId` (String?), `childCount` (Int), `modifiedAtUtc` (Long),
`path` (String — provider path, for display). Index on `parentFolderId` and `source`.

### 4. `data/local/converter/MediaSourceConverter.kt`
`@TypeConverter` both directions, String <-> MediaSource. Unknown/garbage string must map to a
deterministic result — throw `IllegalArgumentException` with the offending value in the message
(do not silently default to ONEDRIVE; a silent default hides corruption).

### 5. `data/local/dao/MediaItemDao.kt`
All reads return `Flow<...>`, all writes are `suspend`:
- `fun observeByParent(parentFolderId: String?): Flow<List<MediaItemEntity>>` — must handle the
  null (root) case correctly in SQL; `= :parentFolderId` never matches NULL. Use
  `WHERE (:parentFolderId IS NULL AND parentFolderId IS NULL) OR parentFolderId = :parentFolderId`.
- `fun observeAll(): Flow<List<MediaItemEntity>>`
- `suspend fun getById(id: String): MediaItemEntity?`
- `suspend fun upsertAll(items: List<MediaItemEntity>)` (`@Upsert`)
- `suspend fun touchLastAccessed(id: String, timestampUtc: Long)` (`@Query` UPDATE)
- `suspend fun clearLocalCacheRef(id: String)` — sets `localCachePath` and `cachedAtUtc` to NULL.
  **Name it this way on purpose**: it drops the cache reference, it does not delete anything remote.
- `suspend fun deleteIndexRowsBySource(source: MediaSource)` — index cleanup only; add a KDoc
  line stating this removes local index rows and never touches cloud content.

### 6. `data/local/dao/MediaFolderDao.kt`
- `fun observeChildren(parentFolderId: String?): Flow<List<MediaFolderEntity>>` (same NULL handling)
- `suspend fun getById(id: String): MediaFolderEntity?`
- `suspend fun upsertAll(folders: List<MediaFolderEntity>)`
- `suspend fun deleteIndexRowsBySource(source: MediaSource)`

### 7. `data/local/GallerySyncDatabase.kt`
```
@Database(
    entities = [MediaItemEntity::class, MediaFolderEntity::class],
    version = 1,
    exportSchema = true
)
@TypeConverters(MediaSourceConverter::class)
abstract class GallerySyncDatabase : RoomDatabase() {
    abstract fun mediaItemDao(): MediaItemDao
    abstract fun mediaFolderDao(): MediaFolderDao
}
```
Set the schema export dir in `app/build.gradle.kts` so `exportSchema = true` does not warn:
```
ksp { arg("room.schemaLocation", "$projectDir/schemas") }
```
Commit the generated `app/schemas/**/1.json`.

### 8. `di/DatabaseModule.kt`
`@Module @InstallIn(SingletonComponent::class)` providing `GallerySyncDatabase` (via
`Room.databaseBuilder`, db name `"gallery_sync.db"`) and both DAOs, all `@Singleton`.
**Do not call `fallbackToDestructiveMigration()`** — destructive migration silently drops the
user's index and is exactly the kind of thing CLAUDE.md wants escalated instead.

## Test requirements — `app/src/test/java/com/gallery/sync/data/local/`
- `MediaSourceConverterTest` — round-trip both enum values; invalid string throws
  `IllegalArgumentException` and the message contains the bad value.
- `MediaItemEntityTest` (or a small `MediaId` helper test) — the composite id scheme produces
  `onedrive:ABC123`, and is stable/collision-free across sources for the same `remoteId`.
- DAO tests need a real SQLite instance and therefore must be **instrumented**, not unit.
  Write `app/src/androidTest/java/com/gallery/sync/data/local/GallerySyncDatabaseTest.kt`
  using `Room.inMemoryDatabaseBuilder` + `runTest`, covering: upsert then observe by parent;
  root (null parent) query returns only root rows; `touchLastAccessed` updates the timestamp;
  `clearLocalCacheRef` nulls the path but **leaves the row present**.
  Mark it clearly as requiring a connected device/emulator. It will NOT run in CI right now.

## Acceptance criteria
1. Room's KSP processor generates the `_Impl` classes — build fails loudly if an
   `@Query` is malformed, which is the real check here.
2. `app/schemas/com.gallery.sync.data.local.GallerySyncDatabase/1.json` is generated.
3. Unit tests in `app/src/test/` pass.
4. `./gradlew assembleDebug testDebugUnitTest` green.

## Out of scope
- Any repository that uses these DAOs (v0.2.0)
- Cache eviction / LRU logic (v0.2.0) — only the `lastAccessedUtc` column exists for it now
- WorkManager sync
- Any migration (this is version 1)

## Report back
Files created, the generated schema JSON path, and any `@Query` Room rejected and how you fixed it.
