# TASK-023 — One folder must be one album: album identity is case-sensitive, the filesystem is not

Milestone: v0.3 — space management (blocks nothing, but touches the deletion rules)
Raised by: Ian, 7 Sept 2026 — *"it appears as though the backup is splitting the Camera folder
into two different folders"*
Status: **specced, not started.** Needs Ian's ruling on the migration before any code moves.

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
