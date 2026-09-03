# TASK-020 — Wizard Step 4: auto-discover media directories with checkboxes

Milestone: v0.4 — first-run polish
Requested by: Ian, 3 Sep 2026
Depends on: nothing (replaces current Step 4)

## Problem

Step 4 currently asks the user to manually pick folders via the SAF document picker. This
requires knowing where photos and videos live on the phone (DCIM, Pictures, Download, etc.),
which most users do not know. The consequence was demonstrated on the Moto G: only DCIM was
granted, missing 3,022 files (1,906 in Pictures/BudgetPhotos, 1,116 in Pictures/BudgetMixed)
— more than half the library.

The reverse is also a problem: a tech-savvy user might not want to back up every directory
(e.g. Download, WhatsApp Images) but has no way to see what is there before picking.

## Design

Replace the manual SAF folder picker with an auto-discovered checklist.

### Flow

1. **Scan MediaStore** — query `images/media` and `video/media` for every file's
   `RELATIVE_PATH`. Group by top-level directory (the first path segment: `DCIM`, `Pictures`,
   `Download`, etc.). For each top-level directory, compute:
   - Number of sub-folders (albums)
   - Total file count (photos + videos separately)
   - Total size in bytes

2. **Present the checklist** — show each top-level directory as a row with a checkbox:
   ```
   ☑ DCIM — 4 folders, 269 files (2.1 GB)
   ☑ Pictures — 2 folders, 3,022 files (10.5 GB)
   ☐ Download — 1 folder, 1 file (298 MB)
   ```
   Pre-check directories that are clearly media-heavy: `DCIM` and `Pictures` at minimum.
   Leave ambiguous ones unchecked (e.g. `Download`, `Telegram`, app-specific folders).

   The user can check or uncheck any directory. Tech-savvy users uncheck what they do not
   want; non-technical users tap Next and get everything that matters.

3. **Grant SAF access up front** — when the user taps Next, walk through one SAF
   `OpenDocumentTree` picker per checked directory. Each picker should be pre-navigated to the
   target directory if the API allows (use the `initialUri` parameter of `OpenDocumentTree`).
   All grants happen at setup time so both backup and future proxying are covered.

4. **Store grants as before** — each confirmed SAF tree URI is stored in `ScopedDirectories`
   exactly as the current flow does. No change to the downstream scoping, scanning, or backup
   logic.

### Pre-check heuristic

A directory is pre-checked if:
- It is `DCIM` or `Pictures` (the two standard Android media locations), OR
- It contains more than some threshold of media files (e.g. 50+)

Directories that are clearly not user photos should be excluded from the list entirely:
- `.thumbnails`, `.tmp`, cache directories
- Directories with zero media files

### What does NOT change

- `TreeScope`, `ScopedDirectories`, `MediaScanner.scanAll()` scoping — all stay as-is
- The SAF grant mechanism — still `OpenDocumentTree` with persistent permission
- Downstream backup, reconciliation, upload logic — untouched
- The ability to add/remove folders later from Settings — unchanged

## Secondary fix: scope-aware counts in startBackupAndObserve

While investigating this task, a related issue was found: `startBackupAndObserve()` calls
`outstandingCountAll()` and `uploadPending(allAlbums = true)`, which operate on ALL pending
ledger entries regardless of current scope. Entries from a prior broader grant remain in the
ledger and get counted/uploaded even after the scope narrows.

Fix: either
- (a) Filter `outstandingCountAll` and `nextPendingAll` by the current scope, or
- (b) Clean out-of-scope pending entries when the scope changes

Option (a) is safer — it does not delete data, and a re-broadened scope would pick them up
again. The count and the upload selection should both use `countPendingInSelectedAlbums()`
logic but extended to also respect the directory scope, not just album mode.

## Files likely touched

- `app/src/main/java/com/gallery/sync/ui/setup/SetupTour.kt` — new `DirectoryDiscoveryContent`
  composable replacing `LocalGalleryContent` at step 4
- `app/src/main/java/com/gallery/sync/data/local/media/MediaScanner.kt` — new
  `discoverDirectories()` method that groups MediaStore results by top-level path
- `app/src/main/java/com/gallery/sync/ui/setup/ReconcileViewModel.kt` — new state fields for
  discovered directories and their checked state; logic to walk SAF grants sequentially
- `app/src/main/res/values/strings.xml` — new strings for the discovery UI
- `app/src/main/java/com/gallery/sync/domain/backup/BackupEngine.kt` — scope-aware counting
  for the secondary fix

## Acceptance

- Fresh install on the Moto G shows DCIM and Pictures pre-checked with correct counts
- Unchecking Pictures and completing setup backs up only DCIM files
- Checking both and completing setup backs up files from both directories
- The `outstandingCountAll` total matches only in-scope files
- Settings still allows adding/removing folders after setup
