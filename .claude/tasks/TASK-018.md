# TASK-018 — Restore means replacing the proxy, not downloading a second copy

Milestone: v0.4 — retrieval, reworked
Requested by: Ian, 27 Aug 2026
Replaces: the drive-listing Restore tab built 25–26 Aug 2026
Depends on: `ProxyMarker.isProxy`, the persisted SAF tree grant write path (`ProxyApplier`),
`RestoreFromCloud`, `MediaStoreWriter`

## What Ian asked for

On the name:

> *"This really ISN'T a 'Restore' — we are not restoring a file just downloading it. If we truly want
> to restore we'd be replacing an existing file with the one we're pulling from the cloud."*

On what the tab is for:

> *"If the user wants a straight download they can use OneDrive."*

> *"When the Gallery is scanned when Restore is open, it needs to check for our optimized files
> (which will eventually include videos as well). Only restorable files should be listed here, in
> their folders, and when downloaded they will replace the files in their original folders."*

On why this is safe to retry:

> *"A restore does not remove the file from OneDrive — only replaces the file in the Gallery — so a
> restore can try again and again if there is an issue."*

On the feedback while it runs:

> *"After the files are selected and Restore is pressed we can give the user a progress screen
> (similar to Archive) which tells the user file by file that the restore was successful."*

## Why this is better than what it replaces

The tab as built lists what OneDrive holds under the backup roots and fetches a chosen file into
`DCIM/Restored` as `name_restored.ext`. Two things are wrong with that, and the name is only the
second one.

**It is a file browser, and OneDrive already has a better one.** Browsing a drive, finding a file and
downloading it is precisely what the OneDrive app does, with search, thumbnails and the user's whole
drive rather than two roots. Rebuilding a worse version of it inside GallerySync is the failure mode
CLAUDE.md's design principle exists to prevent.

**It never restored anything.** The fetched file lands *beside* the file the user already has,
under a different name, in a different folder. For an optimised photo that is the wrong outcome
twice over: the small copy is still the one the gallery shows in the album the user looks at, and
the full-size copy is somewhere they did not put it.

Replacing the proxy in place is the one operation OneDrive cannot perform, because only GallerySync
knows which local files are proxies and what they were made from. That is the whole justification
for the tab existing.

## The decision this reverses

MILESTONES v0.4 records, from 25 Aug 2026:

> **Retrieval reads the drive, not the ledger.** … The case that settles it is a new phone. The
> ledger records what left *this* device, so on a fresh install it is empty by construction … if we
> offer restore then it has to restore any file, not just the ones we backed up, synced or archived.

A list driven by a local scan for proxies has exactly the property that argument rejected: **on a new
phone this tab is empty**, because a new phone has no proxies on it. Ian accepted that consequence on
27 Aug 2026, and the reason is better than "use OneDrive": **the initial setup downloads.** TASK-014's
guided first run is where a new phone gets its library back — bulk, one time, before this tab is
relevant to anything. Restore is what happens *after* that, to files this app has since optimised.
So the tab being empty on a fresh install is not a gap; it is the correct answer to "what have I
optimised on this device?" when the answer is nothing yet.

That also settles the shape of the two features. Initial setup **downloads** files that are not here.
Restore **replaces** files that are here but shrunken. They are different verbs on different
populations, and neither one is the other's fallback.

That paragraph in MILESTONES must be rewritten as part of this task rather than left standing. Two
contradictory settled decisions in the same file is worse than either of them.

## How it works

### What the tab lists

Three local states, all three shown, two of them actionable. Ian settled this over 27 Aug 2026.

| Local state | Row offers | Selectable |
|---|---|---|
| Shrunken by us — carries the proxy marker | **Restore** — the full-size original replaces it in place | yes |
| Absent from the phone entirely | **Download** — the file lands in its album, under its own name | yes |
| Present at full size | nothing — greyed, and labelled as already full size | **no** |

**The third is shown, not hidden.** Ian, 27 Aug 2026: *"keep the file listed, just grey out,
unavailable for restore."* It is the same conclusion the old tab reached — *"files already on the
phone are listed and labelled rather than hidden"* — and for the same reason. A file absent from the
list reads as one the app does not have; greyed out it reads as one there is nothing to do about.
Together the three states make a folder legible at a glance: this much is full quality, this much is
shrunken, this much is gone.

**Why it is not actionable, having briefly been going to be.** Ian argued for restoring it first —
*"sometimes you may want to restore the exact same file: if you edit something, you don't like the
edit and need a fresh full-sized copy to work from"* — which is a real use. He withdrew it the same
day: *"if the user wants it they can do it manually via OneDrive and the consequences are their
own."*

That is the right call, because overwriting a full-size local file is the most destructive thing this
app could do. Replacing a proxy cannot lose anything: the local copy is a downscale and the original
is verified in the cloud. Replacing a full-size file destroys whatever is in it, and if that is an
edit which has not been uploaded, it exists nowhere afterwards — no trash, no undo. The app declining
to be the instrument of that, while OneDrive remains available to anyone who wants it, puts the
consequence with the person choosing it.

Two things worth recording so the idea is not re-proposed without them:

- **The cloud copy may already be the edit.** In a Backup or Sync album an edit changes size and
  mtime, the scanner computes a new ledger key, and the file is uploaded. Restoring then returns the
  very edit the user was trying to escape. The feature only does what it promises if the edit has not
  yet been backed up.
- **"The pre-edit original" is a different feature.** Graph exposes OneDrive's version history and
  this app does not use it. Restoring returns the *current* cloud version, never a prior one. If
  rolling back to an earlier version is wanted, it is its own task, not a checkbox on this one.

`Select all` takes the first two states and skips the third, which falls out of the rows simply not
being selectable.
### Classifying a file — and the trap in the existing test

`ProxyMarker.kindOf(uri)` is the test for the first state, not the ledger. Its own reasoning covers
why:

> The ledger cannot answer this reliably: it is wiped by an uninstall, absent on a new phone, and has
> been observed going stale. A stamp inside the file survives all of that, and survives being copied
> or shared as well.

For a photo it reads the EXIF header only, never the pixels, which is what makes asking it of every
file in the scan affordable. A file that cannot be read answers `null`; refusing to claim a file is a
proxy stays the safe direction, since a wrong "yes" would offer to overwrite a file that is already
the original.

**Presence is decided by name, and by name alone.** `BackupEngine` line 802 currently answers "is this
already on the phone?" with `RestoredAlbum.contentSignature(name, ref.sizeBytes)` — name plus the size
OneDrive reports. **That test must not be reused here.** A proxy's local size is deliberately about a
tenth of the cloud original's, so it returns *not on this phone* for every proxied file — and the
download path would then offer to fetch the exact files the restore path is already offering to
replace, which is the second-copy behaviour this whole task exists to remove.

So: the name tells you whether the file is present, and the marker tells you which of the two present
states it is in. Size is not part of either question.

**The two run in that order, and cannot run in the other.** It is tempting to think the marker is what
separates a proxy from a file that is genuinely gone — it is not, and it cannot be, because *you
cannot read a marker off a file that is not there*. A missing file has no local copy to open and no
EXIF header to inspect. Presence has to be established first, by name; only then is there a file to
ask the second question of.

| | Question | Answered by | Outcome |
|---|---|---|---|
| 1 | is there a local file with this name in this album? | **name** | present / absent |
| 2 | if present — is it one of ours? | **the marker** | proxy / full size |

**Strip `_restored` before comparing names.** Files fetched by the old flow are on the phone as
`name_restored.jpg`. A name-only test that does not strip the suffix reads them as missing and offers
them again — which is `contentSignature`'s whole reason for existing, applied to a test that no longer
uses size.

Note the marker is not new work for this task. It already stops `BackupEngine` re-uploading a proxy
as though it were a fresh photo; this is a second use of the same stamp.

The folder card's "17 of 19 already on this phone" is no help either. It comes from
`scanEverything().groupingBy { it.album }.eachCount()`, and `RestorableFolder`'s own doc calls those
counts "deliberately not an identity claim". Per-file classification is new work.

The scan runs when the tab is opened. Rows group under the folder the file actually lives in —
`MediaFolderEntity`'s bucket, not a OneDrive path — because "in their original folders" is where the
user will look for the result.

A proxy with no cloud copy to restore from is not listed. The full-size original must be present in
OneDrive and identifiable, or the row is a promise the app cannot keep.

### Downloading what is missing

A folder can hold both kinds. Ian, 27 Aug 2026, on a folder with proxies *and* files the phone no
longer has: offer to *"Download files not already in Album"*. This closes a gap the earlier
restorable-only rule left open — a file the user deleted from the phone themselves is still in
OneDrive and nothing in the app would otherwise offer it back.

A downloaded file lands **in its album, under its real name**. No `Restored` folder and no
`_restored` suffix: that suffix exists only to distinguish a fetched copy from a file already
present, and by definition this one is not. A download that puts the album back the way it was should
be indistinguishable from never having lost the file.

**Open — when the offer appears.** Ian suggested a prompt on selecting such a folder. A prompt per
swipe is a poor trade when several folders are being picked; the recommendation is one confirmation
when Restore is pressed, covering everything selected, naming the count and the total size — the same
confirm-once-before-the-run shape Archive uses, and it can state the megabytes, which matters because
downloading consumes space rather than freeing it. Not yet decided.

### Replacing the file

The write goes through the **persisted SAF tree grant**, the same route `ProxyApplier` already uses
to shorten a file in place, unattended since 26 Aug 2026. No `createWriteRequest`, no per-batch tap.

MediaStore reports the old size until a rescan — established 19 Aug 2026 and already handled on the
proxy path. Reuse that step; a restored file that still reads as 4 KB in the gallery has not been
restored as far as any other app is concerned.

### Restoring must not start a loop

Ian, 27 Aug 2026, before any of this was built: *"when a proxied file is restored, will sync see it as
a new file and automatically try to sync/optimize it again?"*

It would. `BackupEntryDao.proxyCandidates` selects on:

```
state = UPLOADED
AND remoteSizeBytes = sizeBytes
AND isProxied = 0
AND isProxySkipped = 0
AND isVideo = 0
AND album IN (SELECT albumName FROM album_preferences WHERE mode = SYNC)
```

A successfully restored file satisfies every one of those again — the local copy is once more
byte-identical to the cloud original, so `remoteSizeBytes = sizeBytes` holds. The only thing keeping
it out is `isProxied`, and that leaves two bad options:

- **Leave `isProxied = true`.** No re-optimise, but the ledger now lies. `localProxySizeBytes` says
  small while the file is full size, and `ProxyApplier`'s requirement that "which photos are still
  full quality" stay answerable is broken.
- **Clear it.** The ledger is honest and the file is a proxy candidate on the next run. With
  automatic optimising on — which it is by default — that is restore, shrink, restore, shrink: a loop
  the user drives without understanding why the photo keeps changing.

**A per-file flag is the answer.** Ian, 27 Aug 2026. `isProxied` goes false so the ledger stops lying,
and a new column — `restoredToFullSize`, or whatever it ends up called — records that the user
deliberately un-shrunk this file. `proxyCandidates` gains one more `AND`.

It must be its own column and not a second meaning for `isProxySkipped`. That flag means *cannot
usefully shrink*; this means *must not be shrunk*. One is a fact about the file, the other is a
standing instruction from the user, and a query that conflated them would silently start
re-optimising restored files the day someone widened the skip logic.

This is a Room schema change and therefore an escalation under CLAUDE.md. Raised with Ian and agreed
in the same conversation, 27 Aug 2026.

**Rejected: switching the album to Off.** Considered and dropped the same day. Mechanically it works
— `proxyCandidates` filters on album mode — but `AlbumMode.OFF` means *not uploaded*, not merely *not
optimised*. Restoring one photo would stop backing up every other file in that album and every photo
taken into it afterwards, silently, five weeks before Samsung's sync stops. It also inverts the
consent model: CLAUDE.md has behaviour following from "a mode the user set, and from nothing else",
and an app that rewrites a mode on the user's behalf is deciding for them in the direction of less
protection.

`SYNC → BACKUP` would have been the safe version of that idea — `proxiesPhotos` is `mode == SYNC`, so
Backup uploads normally and never touches the local file. It is still album-wide for a per-file
intent, so it is not the mechanism. It is a reasonable thing to **offer**: someone who restores most
of an album is saying that album should not be in Sync, and a prompt — *"you have restored 12 of 15
photos here; keep this album at full quality?"* — puts that switch under their thumb rather than the
app's.

**Restore is not offered in Archive albums.** `redundantLocalCopies` matches proxied rows by
`mediaStoreId`, and a restored file is still verified in the cloud — so Archive would offer to take it
off the phone again immediately. Restoring into an album whose standing instruction is "remove these
from the gallery" is contradictory intent, and the tab should say so rather than let the two fight.

**Still to check: does the changed mtime cause a re-upload?** A non-proxied row is matched by
`contentSignature`, which is name plus size, and after a restore the local size matches the ledger's
recorded original — so it should read as present rather than new. What has not been traced is whether
`backupKeyOf(album, displayName, sizeBytes, dateModifiedEpochSeconds)` treats the file as new anywhere
upstream, since a restore necessarily changes the mtime. Worth confirming rather than assuming; a
wasted re-upload of a file already in the cloud is harmless but looks like a bug.

### The per-file flag is a mode, not a boolean

Ian, 27 Aug 2026: *"when a file is restored or downloaded the flag moves from SYNC to Backup for that
file only."*

Framing it as a mode rather than a `restoredToFullSize` boolean is the better shape, and it costs
nothing extra. `AlbumMode.BACKUP` already means exactly the thing that is wanted — *"uploaded; the
local file is never touched"* — and `proxiesPhotos` is already `mode == SYNC`. So the file keeps being
backed up and stops being a proxy candidate, with no new vocabulary invented for it.

A nullable per-file mode column on `backup_entries`. Null means *inherit the album*, which is every
row that exists today, so the migration has no backfill. `proxyCandidates` reads the override where
one is set and the album's mode where it is not.

Two things this buys over a boolean:

- **It says why, not just what.** A row reading `BACKUP` explains itself; a row reading
  `restoredToFullSize = 1` needs the reader to know what that implies about optimising.
- **It generalises.** The same column later lets a user pin any individual file out of Sync without a
  second mechanism, which is a thing people will ask for once they understand what Sync does.

It must still not be confused with `isProxySkipped`. That means *cannot usefully shrink* — a fact
about the file. This means *the user chose full quality for this file* — a standing instruction. A
query that treated them as interchangeable would silently start re-optimising restored files.

### The user sees it worked when the badge goes

Ian, 27 Aug 2026: *"identification is the removal of the Cloud Icon."*

The badge is drawn into the proxy's own pixels — `ProxyBadge`'s doc: *"Baked into the image because
that is the only mechanism there is."* A restore replaces the whole file with the cloud original,
which never had a badge, so it disappears without anything having to remove it.

That makes it the acceptance test a person can run without the app: open the album in Samsung Gallery
and the cloud corner is gone. It is also why the badge cannot be the *machine* test — it is pixels,
and asking "is this a proxy?" of pixels means image analysis. The EXIF marker stays the readable
answer; the badge is the human one. The two are set and cleared together, so they cannot disagree.

**Still open:** what identifies the *cloud item* to restore from — the remote item id on the ledger
row. Confirm the row survives proxying with that id intact and that a rescan does not orphan it. The
badge answers "has this been restored?"; it does not answer "which OneDrive file does this proxy come
from?"

### One prompt, with a way out

Ian, 27 Aug 2026: *"one prompt works — with the option to cancel and open the folder to select
individual files."*

So the download offer is a single confirmation when Restore is pressed, covering everything selected,
naming the count and the total size. Downloading consumes space rather than freeing it, so the
megabytes belong in the sentence.

Three ways out of it, not two: take everything, take only the restores and skip the downloads, or
cancel back to the list to pick by hand.

**Detail to settle:** "open the folder" is unambiguous when one folder is selected and meaningless
when four are. Either the prompt offers to open only in the single-folder case, or cancelling simply
returns to the list and the user opens what they want. The second is simpler and never wrong.

### A restore does not trigger a sync

Ian, 27 Aug 2026: *"a restored file should not trigger a sync — only if the file is moved or saved."*

A restore changes the local size and mtime, and the scan's identity key is
`backupKeyOf(album, displayName, sizeBytes, dateModifiedEpochSeconds)` — so left alone, the next run
would compute a key it has never seen and treat a file already in OneDrive as new work.

The ledger row is therefore updated as part of the restore, from the file just written rather than
from MediaStore. That is the pattern `SafMediaWriter` already relies on for the proxy path — *"the
ledger records `localProxySizeBytes` from the file this app just wrote rather than from MediaStore"* —
applied in the other direction. The restore knows exactly what it wrote and what size it is; the row
should say so before the next scan runs.

The distinction Ian is drawing is the right one. A restore is the app putting back what it took, and
nothing about the file's content has changed from OneDrive's point of view. A *user* moving or
editing the file is a real change and should upload. Keying on mtime alone cannot tell those apart,
which is why the restore has to say which one it was.

### The rule that makes overwriting acceptable

**Download and verify first. Overwrite second.**

The bytes land in a staging file and the byte count is checked against the size Graph reports for the
item. Only a match may be written over the proxy. Writing the stream straight onto the original would
mean a dropped connection costs the user both files — the proxy gone and the original not arrived —
which is the one outcome this app must never produce. `MediaStoreWriter` already states the
principle: *a truncated photo that looks whole is worse than a failure the user can retry.* Here
there is something to lose, so it applies with more force.

**The cloud copy is never touched.** A restore is a read from OneDrive and a write to the phone;
nothing is removed, marked, or moved remotely. That is what makes a failed restore harmless and a
retry free, and it is why the progress screen may say "could not restore" without alarm — the proxy
is still there and the original is still in OneDrive.

**Both halves of that are now observed rather than argued.** Fold 4, 27 Aug 2026: Ian stopped two
restores mid-transfer — 36 MB and 47 MB of a 70 MB and a 200 MB clip already written — and checked
afterwards for the two ways it could have gone wrong. No degradation to the OneDrive copies, and no
partial file in the gallery. The abandoned bytes were discarded and the remote originals were exactly
as they had been.

That matters more here than it did for the old download tab, because this design overwrites a file
the user has. The evidence says an interrupted restore costs nothing on either side, which is the
premise the "Could not restore — your file is unchanged" wording rests on.

## The progress screen

Modelled on the Archive tab, which already does this shape: select, confirm, then a live per-file
list. Same component vocabulary, same row states, so the two screens do not read as two products.

Per-file states, in order:

| State | Meaning |
|---|---|
| Waiting | queued, nothing has happened to it |
| Downloading… | bytes moving, with the per-file percentage |
| Verifying… | full size arrived, being checked against what OneDrive reported |
| Restored to full size | the proxy has been replaced and the rescan has run |
| Downloaded to <album> | a file that was missing is back, under its own name |
| Could not restore — your file is unchanged | any failure; states plainly that nothing was lost |

A row says which of the two things it did. They look alike while bytes move and diverge at the end,
and a user who selected a folder of both should be able to see afterwards which files were replaced
and which were fetched back.

The last row's wording matters and should not be softened into an apology. It is the sentence that
tells the user a failure costs them nothing.

**Archive says the same thing, and Ian was right to say so.** An earlier draft of this spec claimed a
failure costs nothing "here and not on the Archive tab". That is wrong: Archive gates removal on
`verifiedInCloud()`, so an upload that fails removes nothing and the file is still in the gallery,
untouched. Both tabs are safe to fail. The distinction that does hold is narrower and worth keeping
straight — **a completed Archive removal is the outcome with no guarantee**, because
`createTrashRequest` was observed on the Fold 4 removing files outright with nothing in Samsung
Gallery's Recycle Bin. That is not a failure, it is the intended path, and CLAUDE.md's rule stands:
never tell the user a local removal is recoverable. What may be promised there is the verified cloud
copy, and here it is the untouched proxy.

Sequential, one file at a time, for the reason already recorded on this screen: parallel transfers
compete for one connection and make the progress figure meaningless.

Stop stays available throughout — the control added to the Restore bar on 27 Aug 2026, which cancels
mid-file rather than at the end of the current one. A stopped restore leaves every already-restored
file restored and every untouched proxy untouched.

## Unknowns — two answered, one open

1. ~~**Does the SAF tree grant permit a file to grow?**~~ **ANSWERED — yes.** Fold 4, 27 Aug 2026,
   via the in-app probe (`SafGrowProbe`, Settings → Debug in debug builds): a 4,096-byte file in
   `DCIM/Camera` rewritten to 524,288 bytes through the persisted tree grant. No dialog, and
   MediaStore reported the new size after the rescan rather than the old one. The probe removed the
   file it had created; nothing of the user's was touched.

   What this settles: restore-in-place is viable on the same unattended write path that makes the
   proxy, and TASK-018 does not need a different write route. What it does **not** settle: this was
   512 KB into `DCIM/Camera`. A 40 MB photo, and a directory granted separately, are the same
   mechanism but have not been watched. Repeat the measurement on the first real restore rather than
   assuming it scales.

   The reason this was a probe and not an instrumented test is worth keeping: `connectedDebugAndroidTest`
   uninstalls the app to install the test APK, taking the ledger, the signed-in session, and the very
   grant the question is about. A test would have run without a grant and answered nothing.

2. **Video detection is built; video stamping is not.** Ian, 27 Aug 2026: *"let's build in the
   framework, whether it's truncated or optimized."* Done on the same day — `ProxyKind` names the
   three cases (`PhotoDownscaled`, `VideoTranscoded`, `VideoTruncated`) and `ProxyMarker` routes on
   MIME type, reading EXIF `Software` for a photo and the MP4 `©wrt` writer field for a video. Both
   halves answer `kindOf(uri)`, so no caller has to know which format it is holding.

   Two things remain, and neither blocks this task:

   - **Nothing writes the video stamp yet.** There is no framework API to set MP4 metadata on an
     existing file; the value has to go in as the container is muxed, which happens inside TASK-013's
     transcode. `ProxyMarker.videoStampValue(kind)` exists as the contract that work must honour, so
     the muxing code and the detection code cannot drift. There is deliberately no `stampVideo()`
     that quietly does nothing — that would produce proxies no later scan could recognise.
   - **`VideoTruncated` needs its own wording wherever it is offered.** A truncated clip is not a
     lower-quality copy of the whole thing; it plays a fragment and stops. Any list showing one has
     to say so rather than treating it like a downscaled photo.

   Back-compatibility is handled: a bare `"GallerySync proxy"` — the exact string on every photo
   stamped before this — reads as `PhotoDownscaled`. Those files are on real devices, and reading
   them as "not a proxy" would offer them for upload as though they were originals.

3. ~~**What identifies the cloud original for a given proxy?**~~ **ANSWERED — the ledger row, and it
   survives.** Measured against the live ledger on the Fold 4, 27 Aug 2026, not reasoned about.

   | Check | Result |
   |---|---|
   | Proxied rows | 26 |
   | Still carrying a `remoteItemId` | 26 of 26 |
   | Distinct remote ids among them | 26 — no collisions |
   | `remoteSizeBytes = sizeBytes` | 26 of 26 |
   | Carrying a `mediaStoreId` | 26 of 26 |
   | Duplicate `album + displayName` rows anywhere in the ledger | none, across 147 rows |

   So a proxy's row already holds all three things a restore needs: **which** cloud item to fetch
   (`remoteItemId`), **how many bytes** to expect (`remoteSizeBytes`, still equal to the original's
   `sizeBytes`), and **which local file** to write over (`mediaStoreId`).

   The id survives because the row does. `markProxied` is `UPDATE backup_entries SET isProxied = 1,
   localProxySizeBytes = :size WHERE id = :id` — a field update on the existing row, never a delete
   and re-insert, so nothing that was on it is lost.

   **The no-duplicates result is the load-bearing one.** The danger was never `markProxied`; it was a
   later scan computing a fresh content key for the shrunken file, inserting a second row, and
   orphaning the one holding the remote id. That has not happened once on this device, which is the
   skip-by-`mediaStoreId` path in `BackupEngine` working as its doc claims.

   **What is not proven, and cannot be from a snapshot:** this measures a device where every scan has
   behaved. The failure mode is a `mediaStoreId` that changes — the entity's own doc calls that field
   "unfit for identity" — after which the skip misses, a duplicate row appears, and the restore for
   that file loses its cloud pointer. Re-run the check after any change to the scanner or to
   proxying:

   ```bash
   adb exec-out run-as com.gallery.sync cat databases/gallery_sync.db > ledger.db
   sqlite3 ledger.db "
     SELECT COUNT(*) FROM backup_entries WHERE isProxied=1 AND remoteItemId IS NULL;
     SELECT album, displayName, COUNT(*) FROM backup_entries
       GROUP BY album, displayName HAVING COUNT(*) > 1;"
   ```

   Both should return nothing. The first says every proxy still knows where its original is; the
   second says no rescan has orphaned a row. Note `sqlite3` is not on the device — it ships with the
   platform-tools on the desktop — and this needs a debug build for `run-as` to be permitted.

## Not in scope

- **Browsing OneDrive.** That is the OneDrive app's job and the Open OneDrive button already points
  at it. The folder list stays confined to the backup roots, with no search, grid, thumbnails or
  sort — the constraint is held by the absence of those, not by hiding rows.
- **Overwriting a file already on the phone at full size.** It is listed and greyed rather than
  offered. Ian, 27 Aug 2026: the user can do it through OneDrive, and the consequences are then
  theirs. See *What the tab lists* for why this app declines to be the instrument of destroying an
  unbacked-up edit.
- **Rolling back to an earlier OneDrive version.** Graph exposes version history; restoring here
  returns the current cloud copy and nothing else. A separate task if it is ever wanted.
- **Listing video**, until TASK-013 produces video proxies for it to list. Ian confirmed 27 Aug 2026
  that truncated clips will eventually be offered for restore, so `VideoTruncated` is in scope, not an
  edge case to design away. The detection framework
  is in place and costs nothing while there is nothing to find.
- **Removing `RestoredAlbum`.** `contentSignature` must keep stripping `_restored` for files already
  fetched by the old flow — it is the last check before a cloud copy goes to the recycle bin, and
  breaking it would put a file the user has back on the removal list.

## Acceptance

- Opening the tab scans the gallery and lists, grouped by their own folder, every file in the cloud
  folder in one of three states: restorable, downloadable, or already full size
- A file present on the phone at full size is shown greyed and labelled, and cannot be selected —
  including by `Select all`
- Presence is decided by name alone. A proxied file is never classed as missing — the regression test
  is a folder of proxies offering zero downloads
- A proxy with no cloud original to restore from is not offered
- Restoring replaces the proxy in its original folder — same name, same album, no second copy
  anywhere
- A downloaded file lands in its album under its real name, with no `_restored` suffix
- The overwrite happens only after the downloaded bytes match the size OneDrive reports
- A failed or stopped restore leaves the proxy intact and the cloud copy untouched, and the same file
  can be restored again immediately
- MediaStore reports the restored size after the run, verified by opening the file in Samsung Gallery
- The per-file progress screen reports each file's outcome by name, and says which of the two things
  it did
- A restored file is never re-optimised. `isProxied` is cleared and a per-file flag records the
  user's choice; the regression test is a restored photo surviving an automatic optimise run
- A restored file's per-file mode reads BACKUP while its album stays SYNC, and the album's mode is
  unchanged
- The cloud badge is gone from the restored file, checked by opening the album in Samsung Gallery
- The ledger row is updated from the file just written, so the next scan does not treat a restore as
  a new file to upload
- Restore is not offered for albums in Archive mode
- No album's mode is changed by the app. Any mode change is a switch the user taps
- The ledger invariants hold after the feature ships: no proxied row without a `remoteItemId`, and no
  duplicate `album + displayName`. The query is in *Unknowns*, and is the check to re-run after any
  change to scanning or proxying
- MILESTONES' "Retrieval reads the drive, not the ledger" paragraph is rewritten to match
- Verified on hardware in both themes, per CLAUDE.md
