# TASK-023 — One folder must be one album: album identity is case-sensitive, the filesystem is not

Milestone: v0.3 — space management (blocks nothing, but touches the deletion rules)
Raised by: Ian, 7 Sept 2026 — *"it appears as though the backup is splitting the Camera folder
into two different folders"*
Status: **built and verified on the Moto G, 16 Sept 2026** (the Off-to-Off merge and the Archive merge). One gap remains open: the Archive screen does not re-check its list before Android's trash dialog. No schema change and
no migration (see *Why not `BUCKET_ID`*).

## Where the two names came from — Ian, 15 Sept 2026

Two folders as far as he is concerned, and they arrived separately:

- **`camera`** came in with the backup folders he copied onto the phone as test data.
- **`Camera`** was created by the system when he took new photos with the camera app.

So this is not the app inventing a second album out of one folder, and not a naming scheme anyone
chose. It is one folder he supplied and one the system made, whose names differ only in case.

**What the disk check found, and why both are true.** On 7 Sept `ls -di` gave the same inode (`45845`)
for both spellings and identical listings. Android's emulated storage is case-insensitive and
case-preserving, so two directories differing only in case cannot both exist: when the camera app
wrote `DCIM/Camera`, the bytes landed in the folder that was already there. Each name is real, and
each was created by someone different — the filesystem merged them, and **MediaStore kept both
spellings**, which is why the app saw two albums. That is the condition the fix has to handle.

## Corrected 15 Sept 2026 — the app split nothing

Ian: *"The app did not split the folder into two."* He had copied a folder named `camera` into DCIM;
new pictures taken afterwards went to `Camera`, the camera app's default. **Two writers, two spellings.**
GallerySync created no folder, renamed nothing and moved nothing, on the phone or in OneDrive.

What the app did was *read* MediaStore's two spellings as two albums — and that reading is the whole
of this task. Nothing below says otherwise, but the quote above reads as though backup caused it, and
it did not.

It is also a realistic path rather than a test artefact: a user who copies a library over from an old
phone or a PC, then keeps shooting, reproduces it exactly.

**Not present on the Moto G today.** Checked 15 Sept: one directory, `DCIM/Camera` (inode `49065`,
reachable under both spellings), and all 15 MediaStore rows carry `bucket_display_name = Camera`, so
the Albums tab correctly shows one album. The `camera` spelling went because **Ian renamed the folder
in his copied files from `camera` to `Camera`**, so both writers now agree. That is the condition being absent, not
the code being fixed — keying on `BUCKET_DISPLAY_NAME` is unchanged, and a user who never renames
their copy keeps two albums.

## The fix — spec, 16 Sept 2026

### Rulings it implements (Ian, 16 Sept)

1. Every spelling of a folder that differs only in case is **one album with one mode**.
2. **Any discrepancy sets the merged album to `Off`, and the user is warned** about the discrepancy and
   the change of mode. This is the CLAUDE.md exception, and it may only ever write `Off`.
3. The spelling shown is the agent's call.
4. The wizard is untouched. It writes no modes.

### What the tests showed the fix must cover

- **Two MediaStore spellings over one directory** (7 Sept): two albums straight from the scan.
- **The ledger and MediaStore disagreeing** (restore test): MediaStore has one spelling, but ledger rows
  written under the old spelling make a second album, and **one file gets two ledger rows sharing one
  `remoteItemId`**.
- A spelling can change **at any time**, for example when the folder is renamed on disk. So the merge is
  **a runtime step that runs on every scan, not a one-off migration.**

### Why not `BUCKET_ID`

`BUCKET_ID` is the principled key, but the album *name* is load-bearing across the app:
- the OneDrive path (`remotePathFor(album)`)
- the ledger id prefix (`backupKeyOf`)
- `RestoreScope.signature`
- `DownloadMissingFile.relativePathFor`
- the Restore and Albums view models
- 22 `albumName` uses outside the DAOs

Re-keying all of it is a schema migration and a wide rewrite. Folding case onto a single canonical name
per folder gives exactly Ian's ruling with none of that. OneDrive is case-insensitive too (tested), so a
canonical name addresses the same remote folder either way.

Deliberately unchanged: albums are still keyed by folder *name*, so `DCIM/Camera` and `Pictures/Camera`
remain one album, as they are today. That is a separate question and not this task.

### 1. Canonical spelling at the source — `MediaScanner`

After `readAll`, group items by `album.lowercase(Locale.ROOT)`. When a group holds more than one
spelling, rewrite every item's `album` to the **canonical spelling**: the spelling of the item with the
**highest `mediaStoreId`** in the group.

Why that one: MediaProvider records the on-disk directory spelling for new inserts. That was observed
twice on 16 Sept, for a restore into `DCIM/camera/` against a `Camera` folder and for camera-app shots
into an existing `camera` folder. The newest row is therefore the best available reading of the disk.
The Files app is not usable, because it showed MediaStore's view, not the disk. `File.listFiles` needs
all-files access, which this app does not hold.

Put the rule in `MediaScanRules` as a pure function, `canonicalAlbumNames(items)`, so it can be unit
tested. From then on, every consumer of `scanAll` / `scanEverything` sees one name per folder.

### 2. Merge the stored state — new `AlbumIdentityReconciler` (domain/backup)

Runs **inside one Room transaction**, as the **first step of `BackupEngine.refreshLedger`**, before
`insertIfNew` or seeding any preference row.

Input: the canonical names from the scan, plus every distinct name in `album_preferences`,
`album_cloud_status` and `backup_entries.album`. Case-fold them into groups. For every group holding
more than one stored spelling, or a stored spelling different from the canonical one:

- **Target name:** the canonical scan spelling if the folder is on the device. Otherwise, the spelling
  of the most recently written ledger row.
- **`album_preferences`:** collapse to one row under the target name.
  - Write **`Off`** and **always record a warning**, including when every row was already `Off`.
    Ian, 16 Sept 2026: *"yes warn anyway"*.
- **`album_cloud_status`:** delete the group's rows. The next reconcile recomputes it; this is bookkeeping.
- **`backup_entries`:** rewrite `album` and the `id` prefix to the target name.
  - Where two rows then share an id, or share a `mediaStoreId` (the restore case), **keep one**. Prefer,
    in order: `isProxied`, a non-null `remoteItemId`, `UPLOADED` state, then the older
    `uploadedAtEpochMillis`. Carry over `localProxySizeBytes`, `modeOverride` and
    `localMissingSinceEpochMillis` from the dropped row when the kept one lacks them.
  - The dropped row is **ledger bookkeeping only**. Dropping it must never cause a trash request, a Graph
    `DELETE` or a missing-file flag. Assert this in a test.
- Log each merge with the Logger utility: names, mode before, mode after.

### 3. Nothing acts on modes before the merge

`filesInArchiveAlbums`, `redundantLocalCopies`, the optimise candidate queries and the upload gate all
read modes. Each must run **after** the reconciler in the same flow.

The failure this prevents: canonicalisation renames `Camera` items to `camera`, whose preference still
says Archive, before the merge sets it to `Off`. That would archive new camera shots under a consent
given for the old spelling, which is exactly the hazard in CLAUDE.md.

Implement this as a guard: those entry points call `reconciler.reconcile()` (cheap when there is
nothing to merge) before reading modes, rather than trusting call order.

### 4. The warning

- **Storage:** `BackupSettings` DataStore, as a set of pending warnings, each holding
  `{albumName, spellings, previousModes, atEpochMillis}`. This avoids a schema change.
- **Display:** a card at the top of the Albums tab, one per affected album. It stays until the user
  dismisses it or sets a mode for that album. Suggested copy, for Ian to edit:

  > **Camera — mode set to Off.** This folder appeared under two names (`camera`, `Camera`). They are
  > the same folder, so GallerySync combined them and switched the album to Off rather than guess
  > which setting you meant. It was Archive. Choose a mode again when ready.

- Theme colours only (CLAUDE.md dark-mode rule). Check both themes on the Moto G.
- No notification. That would need `POST_NOTIFICATIONS` on API 33+, which is a listing-affecting
  permission and so Ian's decision. The merge running headless is safe without one, because `Off` removes
  nothing.

### Not in scope

- Renaming anything on disk or in OneDrive. OneDrive keeps its folder's first spelling (tested:
  `camera` still holds files uploaded to `DCIM/Camera`).
- `DCIM/Camera` vs `Pictures/Camera` sharing an album.
- Why the camera app split the spelling on 7 Sept but not on 16 Sept.

### Tests (app/src/test)

- `MediaScanRules.canonicalAlbumNames`: one spelling is unchanged; two spellings go to the highest
  `mediaStoreId`; names that differ in more than case (`Camera (1)`) are never merged.
- `AlbumIdentityReconciler`:
  - Archive + Off becomes Off with a warning.
  - Backup + Backup becomes Off with a warning.
  - Off + Off becomes Off **with** a warning.
  - A single stored spelling that differs from the canonical one is renamed and set to Off with a warning.
  - Duplicate rows for one `mediaStoreId` collapse to the uploaded row, keeping `remoteItemId`.
  - `isProxied` survives the merge.
  - Dropping a row makes no call to the trash, Graph `DELETE` or missing-file paths.
  - Idempotent: a second run changes nothing.
- Guard ordering: with an unmerged `camera|ARCHIVE` / `Camera` split, `redundantLocalCopies` returns
  nothing for the `Camera` files.

### Device verification (Moto G)

Re-run the 16 Sept restore scenario, which reliably produces the ledger split:
1. `DCIM/camera` with files, a clean install and the wizard.
2. Set `camera` to Archive and let it trash.
3. Rename the folder on disk to `Camera`.
4. Restore one file, then open the Albums tab.

Expect:
- one album, spelled `Camera`, mode Off
- the warning card naming `camera`/`Camera` and "It was Archive"
- a single ledger row for the restored file, with its `remoteItemId`
- nothing new in `.trashed-*`
- an empty crash buffer

Check both themes. Then set Backup on the merged album and confirm `already there`, not a re-upload.

### Answered — Ian, 16 Sept 2026

Warn even when both spellings were already `Off`. **Spec approved; build started.**

## Built — 16 Sept 2026

As specced, with two changes found while building:

- **Duplicate ledger rows are matched on the full key, not on `mediaStoreId`.** `BackupEntryEntity.id`
  records that MediaStore reuses ids, so an id is not identity. The restore case gives identical keys
  once the spelling is rewritten, so nothing is lost by this. A test pins it: two different files
  sharing a reused id are both kept.
- **The warning card sits in the album list, not above it.** In landscape on the Moto G, the hero
  filled the fixed area, and a card there pushed the albums and its own Dismiss off the screen. As the
  list's first item, it scrolls.

**Files:**
- `MediaScanRules.folderKeyOf` / `canonicalAlbumNames`, applied in `MediaScanner.scanEverything`.
- `AlbumIdentityRules` (pure planning and warning encoding).
- `AlbumIdentityReconciler` (Room transaction and `Mutex`).
- DAO additions: `deleteAlbums`, `albumNames`, `distinctAlbums`, `entriesForAlbums`, `deleteForAlbums`,
  `replaceAll`.
- `BackupSettings.albumMergeWarnings` (DataStore string set).
- Guards in `refreshLedger`, `uploadPending` (mode-gated path), `archiveAlbumNames`,
  `filesInArchiveAlbums`, `redundantLocalCopies`, `ProxyApplier.candidates`, `VideoOptimiser.run`.
- The card in `BackupScreen`; `setAlbumMode` clears the warning.

**Tests:** 18 in `AlbumIdentityRulesTest` and 6 new in `MediaScanRulesTest`. The full unit suite is
343/343.

**Verified on the Moto G** (16:54, installed over the fresh-install state):
- The folder was renamed on disk from `camera` to `Camera`, with the ledger holding 4 rows under
  `camera` and its mode Off.
- The log shows `merged [Camera, camera] into 'Camera': modes were {camera=OFF}, now OFF; 4 ledger
  rows`.
- `album_preferences` holds one `Camera|OFF`, and the ledger holds 9 rows under `Camera` (2 uploaded,
  plus 7 pending including 5 new camera shots). No `camera` rows remain.
- The Albums tab shows one `Camera` album and the card: *"Before: camera was Off."*
- Readable in light and dark. Nothing uploaded, empty crash buffer.

**Not verified:** the untested residue is below.
- ~~The Archive merge on a device.~~ **Verified 21:03.** `Camera` was Archive; after the trash, a rename
  to `camera` and two restores, the log shows `modes were {Camera=ARCHIVE}, now OFF; 9 ledger rows`.
  The result is one `camera` album with no duplicate rows, the card shown, and nothing trashed or
  uploaded by the app. The 7 photos gone from disk were removed by Ian emptying the trash.
- **Where the Archive screen can still slip:** if a merge lands after the Archive screen has shown its
  list and before Android's trash dialog, that batch is still requested. Both of the user's taps
  (Yes, then Android's dialog) come after seeing the list, so it is not unconsented, but it is not
  re-checked either.

## The warning card, as Ian laid it out — 16 Sept 2026

- **One card for all merges**, as a table under a centred, larger **DUPLICATE ALBUM NAMES DETECTED**.
- Columns *Album Name 1 · Album Name 2 · Merged Album Name*, one row per merge. A third spelling, if
  one ever appears, is listed in column 2.
- Then: *"These Albums have been merged to avoid confusion." / "The combined Album's mode was switched
  to OFF." / "Please reassign a Mode to the combined Album."*
- **Dismiss clears every row.** Choosing a mode for an album removes its row. The previous modes are no
  longer shown (Ian removed them) but are still stored and logged.
- Checked on the Moto G in light and dark at phone width. **No 344dp check was possible**: the Fold 4
  is gone, so the three-column table at that width has not been verified on hardware.

## Ruling — the spellings share one mode — Ian, 16 Sept 2026

Ian tried to reproduce it: Android will not let a second `Camera`/`camera` directory exist, and a copy
that collides is renamed `camera (1)` / `Camera (1)`. That agrees with the inode check — one directory,
always. It does not make the task moot, because the split lives in MediaStore's per-writer spelling,
not on disk, and the camera app writes into the existing folder without renaming it.

**Decided: all case-only spellings of one folder share a single album mode.** `Camera (1)` is a
separate directory and stays a separate album — the shared mode must not extend to it.

This settles what the fix must achieve. It leaves decision 1 below (how: `BUCKET_ID` or case-folded
name) and decision 2 (merge rule for rows already holding different modes) with Ian.

## Rulings — Ian, 16 Sept 2026

- **Spelling is the agent's call.** Ian cannot see `BUCKET_ID`, only displayed names.
- **Supersedes decision 2 below:** on any discrepancy the merged album goes to `Off`, and the user is
  warned about the discrepancy and the mode change. This is Ian's explicit exception to "modes are set
  only by the user", and it is limited to moving a mode to `Off`.
- The wizard is unaffected: it writes no modes, and everything starts `Off`.
- ~~Restore must not reintroduce a spelling.~~ **Withdrawn the same day by test, below.** It was
  inferred from the code, and MediaStore does the opposite of what was predicted.

## Tested on the Moto G, 16 Sept 2026 — restore across a spelling change

Setup: renamed `DCIM/Camera` to `DCIM/camera` on disk (18 MediaStore rows followed, `bucket_id`
unchanged at `-1739773001`), `pm clear`, full wizard, first backup recorded 18 files under album
`camera`. Ian set `camera` to Archive: 18 confirmed, and all 18 became `.trashed-` files. Renamed the
folder back to `Camera` on disk. Ian then restored `IMG_20260915_143513095_HDR.jpg` from the Restore tab.

- **MediaStore corrects the spelling.** `DownloadMissingFile` inserted with `RELATIVE_PATH =
  DCIM/camera/`. The row came back as `relative_path = DCIM/Camera/`, `bucket_display_name = Camera`,
  `_data = …/DCIM/Camera/…`, 499,165 bytes. So a restore does **not** put a second spelling into
  MediaStore. The platform uses the directory that already exists.
- **The app splits it anyway, on its own side.** After the Albums tab rescanned, the ledger held two
  rows for the same `mediaStoreId` 5329: `camera/…` (`UPLOADED`) and `Camera/…` (`PENDING`, no remote
  id). `album_preferences` gained `Camera|OFF` next to `camera|ARCHIVE`. The Albums tab showed
  **Camera: 1 file, Off, "1 pending", "1 verified in OneDrive"**, and **camera: Archive, "All files
  Archived"**. The ledger keeps the album name from backup time, while the scan uses MediaStore's name
  now. This is the same album-identity defect, reached without any second writer.
- **The restored file was not archived again.** `redundantLocalCopies: 0`, because the file now
  belongs to `Camera`, which is Off. That is the safe direction, but only by accident of the split.
- **OneDrive is case-insensitive too, which answers decision 4.** Reconcile listed
  `Samsung Gallery/DCIM/Camera` and got the 18 files whose `parentReference.name` is `camera`. The
  path resolves to the one folder, and reconcile counted all 240 as already in OneDrive.
- **Setting `Camera` to Backup uploaded nothing twice.** Ian set it. The backup listed
  `DCIM/Camera` (18 files, the folder named `camera`), logged `already in OneDrive, not re-uploading`,
  and finished `0 uploaded, 1 already there`. The `Camera/…` row became UPLOADED with **the same
  `remoteItemId` as the `camera/…` row**. Two ledger rows now own one OneDrive item, so anything that
  acts on the cloud copy through one row acts on the other's too. `camera` (Archive) still did not touch
  the file.
- **The Files app shows a different spelling from the disk.** At the same moment, `ls` showed
  `DCIM/Camera`, while Motorola's Files app listed `camera` (38.6 MB, the trashed bytes) and a
  `DCIM/Screenshots` folder that does not exist on disk (the real one is `Pictures/Screenshots`). The
  Files app shows MediaStore's view, not the directory. So "the spelling a file manager shows" cannot be
  the rule for choosing a display name: two views of one phone disagree.
- **New camera photos follow the disk's spelling.** Ian took 5 photos at 14:53. The Motorola camera
  (`com.motorola.camera5`) wrote them to `DCIM/Camera/`, and MediaStore recorded `bucket_display_name =
  Camera`. The content trigger ran a backup at 14:54:04: `5 uploaded, 0 failed`. The uploads went to
  `…/DCIM/Camera/<name>:/content`, and Graph filed them in the existing folder, which is still named
  **`camera`** (`parentReference.path = …/DCIM/camera`). The listing grew from 18 to 23. So one
  OneDrive folder now holds files from both albums, under the spelling it had when first created.
  `camera` (Archive) did not act on any of them.

Under Ian's rule, the merge fix would find `camera` (Archive) and `Camera` (Off) as one folder,
set it to Off and warn. So restoring into an Archive album whose folder has since changed case ends
with the album Off.

## Camera app into an existing `camera` folder — no split (Moto G, 16 Sept 2026)

`DCIM/camera` was created with `adb mkdir` and `cp`, with no `Camera` on the phone, followed by a clean
install and the wizard. A photo and a video shot with the Motorola camera app then went to MediaStore
as `DCIM/camera/`, `bucket_display_name = camera`, giving one album. **The 7 Sept split did not
reproduce.** The untested differences are how the folder was created (Ian's copy then, adb today) and
whether `Camera` had existed before. The fix stays needed: see the restore test above, where the ledger
split an album with no second MediaStore spelling.

## What was seen

After a clean install and a wizard run on the Moto G, the ledger held two albums where the phone has
one folder:

| album | files |
|---|---|
| `Camera` | 3 |
| `camera` | 12 |

`album_preferences` had a row for each (`Camera|OFF`, `camera|OFF`), and so did `album_cloud_status`.

## Root cause — established by experiment, not inference

**They are the same directory.** Same inode, identical listings of the same eight files:

```
45845 /sdcard/DCIM/camera
45845 /sdcard/DCIM/Camera
```

Android's emulated storage is **case-insensitive but case-preserving**. There is one directory on
disk. MediaStore, however, records the literal path string the *writing* app supplied, and derives
`BUCKET_DISPLAY_NAME` from it:

| `_id` | `_data` | `bucket_display_name` | `bucket_id` |
|---|---|---|---|
| 5188–5191 | `/…/DCIM/camera/IMG_*.jpg` | `camera` | `-1739773001` |
| 5192–5194 | `/…/DCIM/Camera/VID_*.mp4`, `IMG_*_HDR.jpg` | `Camera` | `-1739773001` |

The fixture folder was created as `camera`; the Moto's camera app writes to `DCIM/Camera`. Two
spellings, one folder.

GallerySync keys albums on `BUCKET_DISPLAY_NAME` (`MediaScanner.kt:165` and `:193`), and
`album_preferences` is `PRIMARY KEY(albumName)` on a TEXT column — SQLite compares that with BINARY
collation, so the two spellings never collide. Hence two albums.

**Not a regression from TASK-022.** The three `Camera` rows are timestamped 13:53:46 onward on
7 Sept, the first video shot after that afternoon's clean install. Before that the fixture contained
only `camera`, which is why this had never appeared.

## Why this is not cosmetic

**One physical folder can carry two album modes.** Both read `OFF` when found, so nothing happened.
But set `Camera` to Archive and leave `camera` Off, and archiving trashes three files and leaves
twelve — from one folder, in the gallery, with nothing on screen explaining the split. Worse, the
division runs along *which app wrote the file*, so every new camera capture lands in the Archive half
automatically.

That is the hazard CLAUDE.md names directly: **the album's membership is not a free variable**, and
anything that widens what an Archive album contains widens what will be removed under a choice made
earlier. A second name for the same folder does exactly that, invisibly.

## The fix has a clean target

**`BUCKET_ID` is identical for both spellings** — `-1739773001` across all seven files, because
Android derives it from the lower-cased path. Keying albums on `BUCKET_ID` collapses them at the
source, rather than papering over it with a case-folded string comparison that would still store two
rows.

## What Ian has to rule on first

This changes the key of `album_preferences`, and `album_cloud_status` and `backup_entries.album`
carry the display name too. That is **a Room schema change requiring a migration**, which CLAUDE.md
puts on Ian's desk, not an agent's. Four decisions:

1. **Key on `BUCKET_ID`, or keep the name and fold case on read?** The ID is the honest key; folding
   case is smaller but leaves two rows that must never disagree.
2. **What does a merged album inherit when the two rows hold different modes?** The safe answer is
   the least destructive of the two — Off beats Backup beats Sync beats Archive — because the
   alternative silently widens an Archive album, which is the failure this task exists to prevent.
   **This is the decision that matters most; do not pick it without Ian.**
3. **Which spelling is displayed** once they merge, and does the remote folder get renamed to match?
4. **What happens to remote folders already created under both spellings.** OneDrive is also
   case-insensitive and case-preserving, so `Samsung Gallery/DCIM/camera` and `…/Camera` may already
   resolve to one folder — **unverified**, and it must be checked on the account before the migration
   is designed, not assumed.

## Not in scope

- Renaming anything on disk. The app does not reorganise the user's folders.
- Any change to what Archive does. This is about which files an album contains, not what happens to
  them.

## Verification

The split is reproducible: shoot a photo with the Moto's camera app into a `DCIM/camera` fixture and
rescan. Note that **wiping GallerySync does not clear it** — the two bucket names live in MediaStore,
not in app data, so a `pm clear` or reinstall leaves the condition in place.
