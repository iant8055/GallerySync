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

### Finding the restorable files

`ProxyMarker.isProxy(uri)` is the test, not the ledger. Its own reasoning already covers why:

> The ledger cannot answer this reliably: it is wiped by an uninstall, absent on a new phone, and has
> been observed going stale. A stamp inside the file survives all of that, and survives being copied
> or shared as well.

It reads the EXIF header only, never the pixels, which is what makes asking it of every file in the
scan affordable. A file that cannot be read answers `false`; refusing to claim a file is a proxy
stays the safe direction here, since a wrong "yes" would offer to overwrite a file that is already
the original.

The scan runs when the tab is opened. Rows group under the folder the file actually lives in —
`MediaFolderEntity`'s bucket, not a OneDrive path — because "in their original folders" is where the
user will look for the result.

A proxy with no cloud copy to restore from is not listed. The full-size original must be present in
OneDrive and identifiable, or the row is a promise the app cannot keep.

### Replacing the file

The write goes through the **persisted SAF tree grant**, the same route `ProxyApplier` already uses
to shorten a file in place, unattended since 26 Aug 2026. No `createWriteRequest`, no per-batch tap.

MediaStore reports the old size until a rescan — established 19 Aug 2026 and already handled on the
proxy path. Reuse that step; a restored file that still reads as 4 KB in the gallery has not been
restored as far as any other app is concerned.

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
| Could not restore — your file is unchanged | any failure; states plainly that nothing was lost |

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

## Unknowns — one answered, two open

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

3. **What identifies the cloud original for a given proxy?** The proxy has the same name as what was
   uploaded, but `contentSignature` exists because `name|size` is how three separate places answer
   "is this content on the phone?" — and a proxy's size is deliberately not the original's. Confirm
   the ledger row survives proxying with the remote item id intact, and that a rescan does not orphan
   it.

## Not in scope

- **Browsing OneDrive.** That is the OneDrive app's job and the Open OneDrive button already points
  at it. This tab lists local proxies and nothing else.
- **Restoring a file that was never optimised.** There is nothing to replace, and downloading a
  second copy of a file the user already has is the behaviour this task removes.
- **Listing video**, until TASK-013 produces video proxies for it to list. The detection framework
  is in place and costs nothing while there is nothing to find.
- **Removing `RestoredAlbum`.** `contentSignature` must keep stripping `_restored` for files already
  fetched by the old flow — it is the last check before a cloud copy goes to the recycle bin, and
  breaking it would put a file the user has back on the removal list.

## Acceptance

- Opening the tab scans the gallery and lists only files carrying the proxy marker, grouped by their
  own folder
- A file with no restorable cloud original is not offered
- Restoring replaces the proxy in its original folder — same name, same album, no second copy
  anywhere
- The overwrite happens only after the downloaded bytes match the size OneDrive reports
- A failed or stopped restore leaves the proxy intact and the cloud copy untouched, and the same file
  can be restored again immediately
- MediaStore reports the restored size after the run, verified by opening the file in Samsung Gallery
- The per-file progress screen reports each file's outcome by name
- MILESTONES' "Retrieval reads the drive, not the ledger" paragraph is rewritten to match
- Verified on hardware in both themes, per CLAUDE.md
