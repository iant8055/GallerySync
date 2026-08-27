# GallerySync Milestones

**Samsung turns off Gallery Sync on 30 September 2026.**

---

## Release gate — decided by Ian

**Nothing is published to Google Play until v0.3.0 and v0.4.0 are built and tested.**

Shipping v0.2.0 alone would deliver "back up your photos, and they disappear from your gallery" —
the space is freed but nothing keeps the photos visible, because that is what v0.3 (photo proxies)
and v0.4 (retrieval) are for. That is a broken product no matter how well the backup works.

- **The Sept 30 date is a personal deadline, not a release deadline.** Ian's own library still needs
  protecting when Samsung's sync stops. Backup alone covers that.
- **v0.3 can be built properly rather than rushed**, since no store listing depends on it.

Cutover rule: run alongside Samsung's sync for at least two weeks before trusting this alone.

---

## Design principle — GallerySync is invisible

**It is not a gallery app and must never become one.**

Its only job is making files *present*. Viewing, search, face grouping, editing, sharing, albums —
the phone's existing gallery already does all of that, with years of work behind it. Rebuilding any
of it would produce something worse than what the user already has.

- **Feed the existing gallery, do not replace it.** A file with local bytes appears in Samsung
  Gallery, CapCut and everything else automatically, because it is an ordinary file.
- **The UI stays minimal**: setup, album modes, a storage budget, and a plain list for retrieving
  what is not on the phone. No photo grid, no thumbnail browser, no search, no editing.
- **Set up and mostly forget.** The user chooses once and the worker maintains it. **Backing up is
  fully unattended** — auto-syncing an album never asks the user to approve anything, and that is
  the point of it. What Android insists on approving is each batch of photos the app *rewrites*, and
  each removal. Unattended-forever is not available to a third-party app for those two, so the UI
  must not promise it — but it must not undersell the upload path either, which genuinely is
  set-and-forget.

---

## Platform constraints — established by experiment, do not re-litigate

Verified on a Galaxy Z Fold 4 (Android 16), 17–19 August 2026.

- **A file with no local bytes cannot appear in any gallery app.** MediaStore rows must point at a
  real file, and the system opens that file directly.

  *Hydration hook* is the term for what Windows calls Files On-Demand: the OS lets an app intercept
  the moment a file is opened, fetch the real bytes, and hand them over, so a zero-byte placeholder
  behaves like a real file to every program. Windows has it (the Cloud Files API, which is how
  OneDrive shows files it has not downloaded); macOS has it (File Provider extensions).
  **Android has no equivalent for media files.** That single absence is the root of most of what
  follows.

- **Rewriting a photo always needs the user, and cannot be granted once and for all.**
  `MediaStore.createWriteRequest` launches only from an Activity, so no background worker can obtain
  consent by itself. A single request is capped at **2000 URIs** — apps targeting Android 15+;
  exceeding it throws `IllegalArgumentException`, and the same cap applies to `createDeleteRequest`,
  `createTrashRequest` and `createFavoriteRequest`. It is a cap per request, not a lifetime quota.

  Everything except the final write already runs unattended — noticing eligibility, choosing what to
  do, generating the proxy, updating the ledger. Only the write needs a tap. Samsung did it silently
  because Samsung Gallery **is** the system gallery.

  **Narrowed 19 Aug 2026 — this describes the MediaStore path, not the whole app.** A persisted
  SAF tree grant writes to media this app does not own with no dialog, verified on hardware; see the
  SAF entry in the verification log below. Delete and the truncating write are still untested, so
  the MediaStore facts above remain what the app relies on today.

- **A trash request is not a guarantee of recoverability.** See the deletion rule in CLAUDE.md.

- **Therefore: storage can be reduced, never eliminated.** Any plan that assumes zero local storage
  while remaining visible in the gallery is impossible, not merely hard.

---

## How Samsung actually did it — checked against vendor docs, 18 Aug 2026

The project replaces this, so the mechanism is recorded rather than recalled.

1. **Bidirectional sync of photos *and* videos** to OneDrive. Deleting on either side deletes on the
   other.
2. **"Free up phone space"** removes local originals of synced media — **all-or-nothing**, with no
   way to pick.
3. **Samsung Gallery keeps a cached thumbnail and its own index entry**, so a cloud-only item still
   appears in the grid. Tapping it downloads the original on demand — a deliberate tap, not
   streaming.
4. This applied to video exactly as to photos.

Steps 2–4 work only because Samsung owns both the index and the viewer. The thumbnail lives inside
Samsung Gallery, not in MediaStore, which is why **no third-party app ever saw those cloud-only
items** — and why CapCut could not. That is the reason this project exists.

| | Samsung Gallery Sync | GallerySync |
|---|---|---|
| Cloud-only item visible in the phone's gallery | yes | **no** — platform limit |
| Cloud-only item visible to CapCut | **no, ever** | n/a — our files are real |
| Choosing what to free | all-or-nothing | per album |
| Photo kept usable while space is freed | no | **yes** — 2048px proxy |
| Local delete removes the cloud copy | **yes, silently** | no — opt-in, never inferred |
| Retrieving a cloud-only item | deliberate tap, in the gallery | deliberate tap, in our list |

Row five is the one to keep in view while designing v0.4: bidirectional delete is what a migrating
user has been trained on, and it is what this project deliberately refuses.

---

## Naming — retired 18 Aug 2026, replaced by a per-screen test

The old rule banned "Sync" in the UI until v0.3 and v0.4 landed. v0.3's photo proxies landed and
were verified, which is exactly the behaviour the ban was waiting for. What replaces it:

> **Say "sync" where the file ends up in the cloud *and* stays in the gallery.
> Where the file leaves the gallery, say that plainly instead.**

| Operation | Local outcome | Wording |
|---|---|---|
| Upload, local copy kept | unchanged, visible | **sync** — photos and video alike |
| Optimise, photo proxied | ~10x smaller, visible | **sync** — the flagship case |
| Remove local copy | **gone from the gallery** | never "sync" — "Remove from this phone" |

The third row is what the old rule was really protecting. "Move to backup" was a soft name for a
hard action, and the softening was the actual risk.

---

## Video — one place, because it spans three milestones

### The founding use case
> *"I would record a video, then ten minutes later want to edit it in CapCut or Canva. I couldn't
> find the video I had just shot because Gallery had moved it to OneDrive."* — Ian

The problem was never that old video is inaccessible. It was that **backing up was coupled to
removing**, and the coupling was fast enough to catch a clip ten minutes old.

**The rule this produced: nothing leaves the gallery unless the user chose that for that album.**
Ian, 19 Aug 2026, in two clarifications on the same day, each narrowing a reading that had drifted:

- **It is about consent, not about upload.** Phrasing it as "uploading must never remove anything"
  describes the *mechanism* of the original failure rather than the rule, and scopes it to the
  upload path — leaving a background worker or a storage budget outside a rule that should cover
  them. It holds whatever the trigger.
- **The consent is the album mode**, given once per album. Setting an album to Archive *is* the
  authorisation to take it off the phone once it is verified in OneDrive. It is not a per-file
  approval, and reading it that way would make the mode unbuildable.

It now lives as a hard rule in CLAUDE.md rather than only here. GallerySync already works this way:
the engine uploads and touches nothing local, and removal sits behind its own explicitly-tapped
control. That separation is the entire difference between this app and the thing that caused the
problem, and it must survive every future change.

### Where video stands
| | Status |
|---|---|
| Backed up to OneDrive | ✅ Verified 19 Aug 2026 — a 164 MB clip, byte-identical |
| Proxied / downscaled | ⬜ **Old** clips only, and how old is the user's setting; recent video never touched |
| Local copy removed | ⬜ Explicit per-file or per-album choice, never a background policy |
| Retrieved on demand | ⬜ v0.4, same path as photos |

### Decisions
- **Recent video is never touched.** Not a compromise forced by the platform — it is the
  requirement, and both middle-state options would attack it. Truncation hands CapCut two seconds;
  full-length downscale hands it 480p and caps the export.
- **Old video may be downscaled full-length**, marked, on charge, Sync albums only. For footage
  people *watch* rather than edit, a downscaled clip is fine, and retrieval covers the rare edit —
  exactly as for photos. Needs Media3 Transformer and a transcode cost measured on real 8K footage
  before committing.
- **"Old" is a user setting, decided by Ian 19 Aug 2026.** **Immediately · 1 week · 1 month ·
  1 year**, defaulting to 1 year — the same four values the Archive age uses, so the app has one
  vocabulary of ages. "Old" is not a fact about anyone's footage: client work gets edited for
  months, family video never gets opened again. **It gates downscaling only and never uploading** —
  a clip is uploaded immediately whatever its age, because a threshold that held new video out of
  OneDrive would rebuild the founding failure while wearing the name of the fix. Measured against
  `dateModifiedEpochSeconds`, which needs no schema change and errs toward leaving video alone.
  **Immediately reaches a clip shot this morning**, which is in tension with *recent video is never
  touched* above; the clip stays in the gallery and stays playable, but an export from it is capped
  until the original is fetched back. Flagged in TASK-011 rather than silently reconciled.
- **Truncating to a stub is rejected.** It destroys the one thing old video is for.
- **Writing our own thumbnail into Samsung Gallery is impossible** — private index, another app's
  sandbox, and the whole mechanism is being switched off anyway.
- **"Never proxy video silently" was an agent's note, not Ian's rule.** It is not in CLAUDE.md.
  Revising it is a normal design decision, not an amendment to a hard rule.

### The limit no milestone resolves
Sync means visible while remote. A photo gets there through its proxy; video cannot without being
degraded. So a video is either whole on the device, or absent from the gallery until fetched. There
is no middle state that is both free and safe to edit from — and the store listing must not imply
otherwise.

---

## v0.1.0 — Foundation ✅ TAGGED
Scaffold, Logger, Room, OneDrive Graph adapter, MSAL sign-in, browse UI.
Verified on hardware: sign-in completes and the real drive lists.

## v0.2.0 — Backup ✅ WORKING (not yet tagged)
- [x] `Files.ReadWrite` scope
- [x] Upload ledger keyed on content, not MediaStore ids
- [x] Media scanner, partial-access aware
- [x] Resumable Graph upload — verified byte-identical on hardware
- [x] Per-album include/exclude
- [x] Skip files already present in OneDrive
- [x] Backup UI with a manual run
- [x] Move redundant local copies out, once the cloud copy is verified
- [x] Schedule the periodic worker — content-triggered on new media plus a 6-hourly safety net
- [x] Metered-network preference, defaulting to unmetered-only
- [x] **Verify a large video actually uploads** — 19 Aug 2026, 164 MB byte-identical
- [x] **Bound the upload batch by bytes, not just file count** — 512 MB cap, a lone oversized file
      still attempted
- [x] **Automatic sync on by default**, armed at application start rather than only by the toggle
- [x] **Persist the upload session URL and its expiry on the ledger row.** The failure that prompted
      this: a run killed at 96% of a 164 MB video restarted from byte zero, so any file too large to
      finish inside one run could never complete, with the threshold scaling on upstream rather than
      file size. **Built and proven end to end on the Fold 4, 26 Aug 2026** — a 1,938 MB video was
      force-stopped at ~50%, the process killed outright, and the next run resumed at byte
      1,069,547,520 of 2,032,370,426. **Caveat: the session expires about 15 minutes after the last
      chunk**, extending as chunks land. This covers a run killed and restarted promptly; it does not
      cover a phone left overnight.
- [ ] Retry failed items from the UI
- [x] **Start time for the first backup.** The initial whole-library upload is the heaviest thing the
      app ever does. User-set, default overnight (1am, six-hour window), charging required for that
      first run. Only automatic runs are gated — "Sync now" is never held, because someone who asked
      has already decided this is a good moment. The gate lifts for good once the backlog clears.

## v0.3.0 — Space management
The milestone that delivers the actual product: the phone stops filling up, and the existing gallery
keeps working.

- [x] **Photo proxies.** Downscale to ~2048px, EXIF preserved, proxy kept in MediaStore permanently.
      **Unattended from 26 Aug 2026** — written through the persisted SAF tree grant, no dialog.
      Roughly 10x smaller, and every photo stays visible and editable in the phone's own gallery.
- [x] **Never proxy video silently** — kept as guidance, not as a hard rule; see the video section.
- [x] **Clear marker showing which items are optimised.** Cloud badge burned into the proxy plus an
      EXIF marker, verified across square and 16:9 at orientation=90.
- [x] **Stop offering photos that can never shrink.** Schema 5 records a file examined and found
      already small enough, so the candidate count reaches zero instead of sticking.
- [ ] **Storage budget.** User sets a free-space floor, default 20 GB, with an enforced minimum so it
      stays clear of Android's low-storage threshold — below that the backup worker stops running and
      nothing new becomes eligible. Proxying is the only lever; nothing is deleted. If it cannot
      reach the floor it stops and says so. Notifies when free space drops below the floor, which is
      also how it asks for the next batch of write consent. See TASK-011.
- [ ] **Album modes in the UI.** Schema 4 carries Off/Backup/Sync/Archive; the screen is still a
      switch. See TASK-012.
- [ ] **Running count of space saved, per album and in total.** Each album row says what has already
      been freed and what its selected mode could free, updating as the mode changes. Same
      aggregates the floor uses, so the two screens cannot disagree. Added by Ian 19 Aug 2026. See
      TASK-011.
- [ ] **Sync scope — Photos only / Video only / Both.** One universal setting applying to every Sync
      album. Default Both, which is today's behaviour. Photos only leaves video in Sync albums
      unuploaded, so it has to say what it is excluding. Added by Ian 19 Aug 2026. See TASK-011.
- [ ] **Video transcode for old clips**, age a user setting — see TASK-013. The write needs no tap
      (SAF, verified 19 Aug 2026); the blocker is a transcode cost measured on real 8K footage, and
      it is gated on v0.4 retrieval.
- [ ] **Guided first run** — language, cloud, sign-in, permissions, then two gates the engine cannot
      start without: which directories to pull from, and what to do with the existing library. The
      directory picker is also the SAF write grant. See TASK-014.
- [ ] **Move to backup should distinguish photo from video**, or be replaced by Archive mode.

## v0.4.0 — Retrieval and deletion sync
- [x] Fetch a cloud-only item back on demand, registering it in MediaStore so every app sees it.
      Photos and video both — it is the only route back to a full-quality edit from a proxy.
      **Verified byte-identical on the Fold 4, 25 Aug 2026.** **Video exercised 26 Aug 2026** — a 2 GB
      clip fetched back and SHA-256 matched the local original exactly.
- [x] Plain retrieval list — **not** a photo browser. Also the only place a fetch can be triggered:
      there is no hydration hook, so tapping an item in Samsung Gallery cannot reach us.
      Populates, fetches, and clears itself once the file is back.
- [x] **Retrieval reads the drive, not the ledger.** 25 Aug 2026. `observeRetrievable()` offers a
      file only when the ledger knows it *and* it has already left the phone, which answers "what
      have I lost from this device?" rather than what a restore feature promises. `DCIM/12345clips`
      showed the gap in miniature — seven videos backed up, one offered, the other six absent only
      because they were still on the phone.
      **The case that settles it is a new phone.** The ledger records what left *this* device, so on
      a fresh install it is empty by construction: the user signs in, their whole library is in
      OneDrive, and a ledger-driven list offers nothing at all. That is the moment someone most
      wants a restore and the moment the ledger knows least. Ian, 25 Aug 2026: if we offer restore
      then it has to restore any file, not just the ones we backed up, synced or archived.
      Files already on the phone are listed and labelled rather than hidden — retrieving one is
      allowed, and what lands carries `_restored` before the extension so the two are told apart.
      That rename is why `RestoredAlbum.contentSignature` exists: three places test `name|size` to
      decide whether content is on the phone, and one of them is the last check before a cloud copy
      goes to the recycle bin.
      **Confined to the backup roots**, and no thumbnails, grid, search or sort. Ian: only the roots
      for now — likely to need revisiting once other cloud services arrive, since a second provider
      will not lay its files out under a Samsung path. Real browsing stays with the Open OneDrive
      button; looking *through* photos is a different activity from getting specific ones back.
- [x] Deletion sync, opt-in and batched. Highest-risk feature in the product; it only follows a
      backup engine that has been watched working. Never infers deletion from absence alone.
      **Built 25 Aug 2026**, default Leave, no automatic option. Screens verified; a real cloud
      deletion has not been performed, and should be watched once on a disposable file.

## v0.5.0 — Google Photos + Billing
- [ ] Google Play Billing (`pro_unlock`)
- [ ] Google Photos adapter (requires OAuth — Ian)
- [ ] Settings: sync frequency, account management

---

## Hardware verification log

Two handsets from 24 Aug 2026. Every entry names the device it was taken on, because several findings
here have turned out to be device-specific rather than platform-wide.

- **Galaxy Z Fold 4 (SM-F936U)** — Android 16, API 36. Every entry before 24 Aug is this device.
  Being returned as a trade-in in the week of 24 Aug, so it is the last disposable phone available:
  anything destructive should be run on it while it exists.
- **Galaxy Z Fold 8 (SM-F976U1)** — Android 17, **API 37**, One UI 9. Ian's daily driver, holding a
  real 148 GB library. The only device that can verify targetSdk 37 behaviour, and the one place
  where a mistake costs real photos.

### 25 Aug 2026 — the skip-existing path checked against the drive by hand

**Fold 4.** `DCIM/12345clips` holds seven videos. The ledger recorded all seven as verified, but only
one was ever uploaded by this app: its row was written at 19:40:59, and the other six were all marked
at **19:48:36 — the same second**, which is the only way ~475 MB could be recorded as backed up that
fast. Those six went through the skip-existing check, which matches name and size against a listing
of the remote folder and records the listing item id without sending a byte.

**Ian opened OneDrive and confirmed all six are there.** That is the first hand-check of this path,
and it matters more than its size suggests: on a real library the skip path covers almost everything
— 6,278 of 6,371 files on this device — so nearly every `verifiedInCloud` row in the ledger is one it
wrote. Those rows are what Archive consults before removing a local copy. Had the match been loose,
the app would have been removing files on the strength of a listing that meant nothing.

Note the direction this does **not** prove. The 25 Aug entry on the cloud re-check still stands: a
row records what was true *once*, and a file deleted from OneDrive by hand leaves the ledger
asserting it is safe forever. This confirms the row was right when written, not that it stays right.
`confirmStillInCloud` exists for the second question.

The album was set to **Off** afterwards, deliberately — so nothing in it is being confirmed by
current runs, and its counts are a snapshot rather than a live figure.

### 18 Aug 2026 — proxying
11 photos optimised, 40,283,338 bytes reclaimed; five correctly skipped as already small. EXIF
orientation and dates carried across. Videos untouched at 103 / 163 / 178 MB. OneDrive originals
intact at full size beside a 348 KB local proxy.

**CapCut can see the backed-up folder and its files** — the problem the project exists to solve,
confirmed against the app that motivated it.

*Consequence:* what an editor imports for an optimised photo is the 2048px proxy, so exports from it
are capped at that resolution. Retrieval is therefore load-bearing, not a nicety.

### 19 Aug 2026 — schema 4 and 5, the UI, and video upload
- **25 instrumented tests pass**, including every migration test. Schema 3 → 4 and 4 → 5 verified on
  a real database, with the 3 → 4 mapping asserted: an enabled album becomes `BACKUP`, never `SYNC`
  or `ARCHIVE`.
- **Both themes check out** for the rebuilt Settings screen and Album Modes.
- **Video upload works.** A 164 MB clip and a 35 MB clip both byte-identical in OneDrive —
  198,648,011 bytes of video verified, first attempt, no retries.
- **Resume across runs does not.** Force-stopped at 157,286,400 of 163,846,425 bytes (96%), the next
  run opened a fresh session (`nextExpectedRanges: ["0-"]`) and restarted from byte zero. The ledger
  row was still `PENDING` with `attemptCount = 0`.

  Quantified: at the ~3 MB/s observed, a ten-minute background run reaches about 1.8 GB. But the
  ceiling scales with upstream — at 2 Mbps that window covers ~150 MB, and this same video would
  then never complete. Graph returns an `expirationDateTime` about five hours out, so the fix stores
  the session URL with its expiry.
- **The skip-existing check works on real data.** With the ledger empty, a run marked 15 files as
  already in OneDrive instead of re-uploading them — and recorded `remoteSizeBytes` for each, so
  they are verified to the same bar as files actually uploaded.

**Note on the empty ledger:** it was emptied by `./gradlew connectedDebugAndroidTest`, which
uninstalls the app after running. An earlier version of this file recorded it as an unexplained
clearing between sessions; that was wrong. Do not run the full instrumented suite against a working
install without expecting to lose its data.

---

### 19 Aug 2026 — the SAF tree grant, and a platform constraint that turns out to be narrower

Probed with `ui/debug/StorageAccessProbe.kt` on the Fold 4, device API 36, app targetSdk 37.

**A persisted SAF tree grant writes to media this app does not own, with no consent dialog.**
Confirmed twice, and the second version of the probe performs a real write rather than only opening
a descriptor — the first version proved less than it appeared to.

```
Picked: content://com.android.externalstorage.documents/tree/primary%3ADCIM
Persisted read+write on the tree.
Write target: Screenshot_20260720_223735_Gallery.jpg (image/jpeg)
Owner per MediaStore: com.android.systemui
OPEN OK — rw descriptor granted, no consent dialog. Size 80329.
WRITE OK — wrote 1 identical byte to a file we do not own. Size unchanged.
```

- **DCIM is selectable**, and so is DCIM/Camera. Android 11's directory restrictions do not cover
  them at this API level.
- `takePersistableUriPermission` succeeded for read and write.
- The target was owned by `com.android.systemui`, so this is not the "app may modify what it
  created" exemption.
- The write was byte-identical by construction — first byte read, seek 0, same byte written — with
  `fstat` size checked before and after. Nothing was altered.

**The truncating write works too — this is the proxy operation, on a real camera photo.**

```
Truncate target: 20260819_132753.jpg
Owner per MediaStore: com.sec.android.app.camera
MediaStore size before: 4420894
TRUNCATING WRITE — 4420894 -> 4096 bytes on disk.
SHORTEN OK — ftruncate through the tree grant works, no dialog.
MediaStore size immediately after: 4420894
Rescan completed for /storage/emulated/0/DCIM/Camera/20260819_132753.jpg
MediaStore size after rescan: 4096
```

A 4.4 MB photo owned by Samsung's camera app, shortened to 4 KB through the tree grant, with no
consent dialog at any point. That is proxying, minus the part that makes a good proxy.

**MediaStore does not notice on its own, and that is now a build requirement.** The index still
reported 4,420,894 bytes immediately after the file on disk became 4,096.
`MediaScannerConnection.scanFile` reconciled it. So **every write through this route must be
followed by a rescan of that path** — without it the gallery shows stale sizes and dimensions, and
the ledger's `album + name + size + mtime` key is computed against a size that is no longer true.

| | Status |
|---|---|
| Write to a file owned by another app, no dialog | ✅ verified twice |
| **Truncating write** — the proxy case | ✅ verified, `com.sec.android.app.camera` photo, 4.4 MB → 4 KB |
| MediaStore consistency after a size change | ✅ answered: **stale until rescanned**, rescan fixes it |
| **Delete** via `DocumentsContract.deleteDocument` | ⚠️ works, and is a **permanent delete** — forbidden by CLAUDE.md, see below |
| **Grant surviving a reboot** | ✅ verified — survived reboot *and* an app reinstall, and still wrote |

**Delete works, and it is the wrong kind of delete.**

```
DELETE OK — removed via SAF with no consent dialog.
MediaStore row after delete: gone
$ ls /sdcard/DCIM/Camera/20260819_132753.jpg
ls: No such file or directory
```

`DocumentsContract.deleteDocument` removed the file outright, with no dialog, and MediaStore
reconciled itself without needing a rescan — unlike the write case.


**Ian checked the Recycle Bin: empty.** So this is observed, not inferred from the API name — the
file did not go anywhere recoverable. `deleteDocument` is now named as forbidden in CLAUDE.md.

**It is a permanent delete, and CLAUDE.md forbids it.** The deletion rule is absolute: a removal
always goes to a trash the user can recover from, and the app must never call a permanent-delete
API. `deleteDocument` bypasses Android's media trash entirely — nothing lands in the Gallery's
recycle bin, and there is no undo. That it happens to work is not permission to use it.

So the SAF route splits cleanly by operation, and the split is not a compromise but a rule:

| Operation | Route | Tap? |
|---|---|---|
| **Proxying** — write and shorten in place | ✅ SAF tree grant | none |
| **Archive** — remove the local file | ❌ SAF is a hard delete; use `createTrashRequest` | one per batch, unavoidably |

Archive therefore keeps everything already designed for it in TASK-012: the nightly pass that
prepares a batch, the approval the user taps, and the 2000-URI cap on each request. Nothing there is
wasted. Proxying is the half that gets simpler.

Worth stating because "DELETE OK" in a log is exactly the sort of line that gets acted on later
without the rule being reread. **The finding is that SAF can delete, not that it may.**

### What this collapses

If the grant survives a reboot, the whole consent apparatus in TASK-011 is unnecessary for
proxying: no `createWriteRequest`, no 2000-URI cap, no grant pool, no `ClipData`, no second
execution mechanism, and no fork between WorkManager and raw JobScheduler. The storage-budget worker
becomes ordinary background work, and "set up and mostly forget" stops needing its caveat for
photos.

The reboot question is therefore the one left worth answering, and it is cheap: restart the phone,
open the app, run the write probe without re-picking the folder.


### Conclusion — the SAF route is proven for proxying

Every question this probe was built to answer is closed, on hardware, in one session.

```
[after reboot and an app reinstall, with no folder re-picked]
Persisted: …/tree/primary%3ADCIM read=true write=true
Restored tree without re-picking: …/tree/primary%3ADCIM
OPEN OK — rw descriptor granted, no consent dialog. Size 80329.
WRITE OK — wrote 1 identical byte to a file we do not own. Size unchanged.
```

**Proxying needs no tap, no cap, and no user present.** One folder pick at setup, and the storage
budget worker becomes ordinary background work. What TASK-011 spent most of its length designing
around — the grant pool, the `ClipData` hand-off, the WorkManager-versus-JobScheduler fork, options
1/2/3 for where the applying step runs — is not needed for photos.

**Two things carry over regardless:**

1. **A MediaStore rescan must follow every write.** The index does not notice a size change on its
   own. Not optional: the ledger keys on size, and the gallery shows stale dimensions without it.
2. **Archive still needs `createTrashRequest` and its tap**, because SAF's delete is permanent and
   CLAUDE.md forbids it. Everything designed for Archive in TASK-012 stands unchanged.

**Not yet built, only proven.** The probe writes one byte; the real path generates a validated
proxy, checks it decodes with EXIF intact, then writes it — TASK-010's rules are untouched by this
and still apply. What changed is how the bytes get written, not what is written or how carefully.

**`MANAGE_EXTERNAL_STORAGE` is off the table** unless something later forces it. The cheaper route
works, and the Play-listing scrutiny does not have to be spent.

### The constraint this narrows

The platform-constraints section says rewriting a photo always needs the user and cannot be granted
once and for all. **That is true of the MediaStore path and not of the app as a whole.**
`createWriteRequest` does need an Activity, and its 2000-URI cap is real — but it is not the only
route to the bytes. A tree grant taken once at setup is exactly the "granted once and for all" that
section rules out.

Recorded rather than rewritten there, because the MediaStore facts in it are still correct and are
still what the app does today. Until delete and the truncating write are also verified, the SAF
route is a strong candidate and not yet a decision.

### 19 Aug 2026 — the skip-existing check was reading one page in a hundred-item world

Measured with the debug cloud-coverage probe, which lists the whole library against OneDrive and
uploads nothing. It calls `BackupEngine.remoteIndexFor` itself rather than a copy, so what it
verifies is the shipping code path.

**Two defects, both of which made an already-backed-up file look absent, and absent means upload.**

**1 — Graph pages at 100 items and the check read only the first page.** `remoteIndexFor` called
`listFolderByPath` once and used `result.value.nodes`. Any album larger than a page was invisible
past its hundredth file.

**2 — A failed listing returned an empty map**, which is indistinguishable from "the folder is
empty". One bad moment on the network therefore re-uploaded an entire album. Not hypothetical: the
19:08 run lost connectivity partway and **81 of 87 albums failed to list**, reporting 8,177 files as
missing from a drive that held nearly all of them.

Both are fixed — walk every page to `MAX_REMOTE_PAGES`, and return `null` on a failed listing so the
caller defers the file with its attempt count untouched rather than uploading it.

**Verified run, 19:15, both fixes in, no listing failures:**

| | files |
|---|---|
| local, 87 albums | 8,482 |
| already in OneDrive, walking every page | 8,276 (97.6%) |
| visible one page at a time | 2,753 |
| **duplicate uploads prevented** | **5,523** |
| genuinely not in OneDrive | 206 |

**Two things this settles beyond the bug.**

**Ian's assumption holds, and it is now load-bearing.** 97.6% of the library is already in OneDrive,
so first run is reconciliation and not a bulk upload — 206 files, not 8,482. That is what makes
TASK-014's Gate 2 offer of "back up everything" reasonable to present at all, and it is why the
first-run experience must be designed around checking rather than transferring.

**`REMOTE_ROOT = "Samsung Gallery/DCIM"` is confirmed against a real drive.** The match rate is only
achievable because the path deliberately mirrors the layout Samsung's own sync created. Changing it
would strand every existing backup and re-upload the library; treat it as fixed.

Neither defect was reachable by unit test — both need a real drive with more than a hundred files in
a folder, and a real network to fail.

*Superseded 24 Aug 2026.* The probe was kept for exactly that reason, and has now been replaced by
`ReconcileWithCloud`, which does the same measurement on the same code path but as a real setup step
rather than a debug screen. Both probes were removed with it — see the entry below — which also took
the forbidden `DocumentsContract.deleteDocument` call out of the tree.

### 24 Aug 2026 — the upload gate was opt-out, and a fresh install uploaded what nobody chose

**Fold 8, first run after a Smart Switch migration.** 23 files went to OneDrive from five albums the
user had never seen — `Camera`, `Messages`, `Screen recordings`, `Snapchat`, `WhatsApp Images` —
including a 75 MB and a 48 MB video. No mode had been set on anything.

The gate read:

```sql
album NOT IN (SELECT albumName FROM album_preferences WHERE mode = 'OFF')
```

`album_preferences` was empty, and `NOT IN` over an empty set is true for every row, so the whole
library was eligible. The table was empty because **only `BackupViewModel` ever wrote to it** — the
engine and the worker never did. A content-triggered run firing before anyone opened the album screen
therefore saw no preferences at all and read that as universal consent.

The intent had been right since `5f292c9` ("new albums do nothing until chosen"); the polarity was
inverted, and the seeding sat in the layer that headless runs never reach.

**Nothing was deleted.** Local removal goes through `MediaStore.createTrashRequest`, which needs an
Activity and a tap, so a background worker cannot remove anything no matter what the gate says. That
guarantee held exactly as designed. But files left the phone that the user had not chosen to send,
and the rule is that this follows from a mode the user set and from nothing else.

Fixed both halves:

- The gate is now `album IN (… WHERE mode != 'OFF')`. An album with no row is never eligible, so the
  failure mode is "backs up too little" — visible and recoverable — instead of "backs up what you did
  not ask for", which is neither.
- `BackupEngine.refreshLedger` seeds a row for every album it discovers, via an `IGNORE` insert so a
  choice already made is never overwritten. It seeds with the user's configured `defaultAlbumMode`
  rather than a hardcoded one: the engine now runs before the screen looks, so hardcoding would have
  silently disabled that setting. `canBeDefault` keeps Archive out, so seeding can never arm a mode
  that removes files.

**Verified on the Fold 4: 31 instrumented tests pass, 0 failures** — up from 25, with six new cases in
`UploadGateTest`. `unknownAlbumIsNotEligible` is the regression test; if it fails, the gate has been
flipped back. All ten migration tests still pass against the changed DAO.

Worth recording about *where* this showed up: the Fold 4 could never have caught it. Every album in
its ledger already had a preference row after weeks of use, so both gate directions agreed there. The
defect needed an empty preference table, which only a fresh install produces — the state every new
user starts in, and the one a long-lived dev device never returns to.

### 24 Aug 2026 — the layout breaks folded, and the app data exclusions hold

**Fold 8.** Two findings from the same session.

**The migration exclusions work.** Smart Switch carried the APK (`installer=com.sec.android.easyMover`)
but no app data: zero ledger rows carried a `remoteItemId`, the token store was absent, and MSAL's
credential cache was an empty stub. That is the `<device-transfer>` block in
`data_extraction_rules.xml` doing its job, confirmed end-to-end for the first time. Re-signing in and
rescanning is the whole recovery, as designed.

**The UI is unusable on the cover screen.** At 320dp x 747dp with `font_scale` 1.7 — Ian's own
settings, not a stress case — album rows collapse to one character per line, body text runs underneath
buttons, and the Appearance segmented control deforms. One mechanism explains nearly all of it: every
broken spot is a `Row` of `[text] [control]` where the control takes its width first. Itemised in
TASK-012 under "Known: the layout breaks on the folded cover screen", with a repro that needs no Fold.

### 24 Aug 2026 — a dead network looks exactly like a broken installer

**Fold 4.** `./gradlew connectedDebugAndroidTest` failed twice with ddmlib hanging in
`installCommit` for about four minutes, then `DELETE_FAILED_INTERNAL_ERROR` cleaning up. The obvious
readings were all wrong: storage had 125 GB free, `verifier_verify_adb_installs` was already 0, and
`install_non_market_apps` was 1.

**The phone had no internet.** The install commit blocks while a verifier tries to reach a server it
cannot, and the failure surfaces as an installer bug rather than a connectivity one. With the network
back the same command ran 33 tests in 29 seconds, unchanged.

Worth writing down because the diagnosis was initially recorded here as "ddmlib's split-APK installer
is broken on this handset", which was wrong. **Check the device has a working connection before
believing anything about a failed install.**

Two things that are still true and useful:

- `adb install` succeeds where the split installer stalls, so it is a quick way to tell a genuine APK
  problem from an environmental one.
- Driving the runner directly takes about two seconds:

  ```
  adb shell am instrument -w com.gallery.sync.test/androidx.test.runner.AndroidJUnitRunner
  ```

  **and, unlike `connectedDebugAndroidTest`, it does not uninstall the app afterwards.** That makes it
  the only safe way to run these tests against a device holding a real ledger — the warning elsewhere
  in this file about losing app data applies to the Gradle task, not to this.

### 24 Aug 2026 — the reconciliation, measured on hardware

**Fold 4**, folded dimensions forced to 320dp x 747dp at `font_scale` 1.7, signed in, ~90 albums.

| | On the phone | Already in OneDrive | To upload |
|---|---|---|---|
| Photos | 4,636 | 4,578 | 58 (98 MB) |
| Videos | 1,735 | 1,700 | 35 (2.7 GB) |

**6,371 files, 6,278 of them already safe. 2.8 GB to send rather than ~120 GB.** Every album listed
without failure, so nothing fell into the unchecked category on this run.

This is the assumption the first-run flow rests on, now measured rather than argued: most of the
library is already in OneDrive because Samsung's own sync put it there, so setup is reconciliation
and not a bulk transfer. It also confirms `REMOTE_ROOT` is still matching Samsung's layout on a
second device.

**One defect found by watching it run.** The screen announced "Everything on this phone is already in
OneDrive" after a single album of ninety. Mid-run the totals cover only what has been checked so far,
and `isComplete` is trivially true while nothing has failed yet. Fixed by requiring the run to have
finished. Worth recording because it is the mirror of the unchecked rule: that one stops the app
claiming files are missing when it does not know, and this one stops it claiming they are safe when
it does not know. Both are the same error, and only one of them was anticipated.

### 24 Aug 2026 — both research probes removed

`ui/debug/` is now empty. `StorageAccessProbe`, `CloudCoverageProbe` and `CloudCoverageViewModel` are
gone, along with the two debug sections in Settings that reached them.

Both existed to answer a question, and both questions are answered: the SAF tree grant on 19 Aug, and
the cloud coverage today, at 6,278 of 6,371 files already backed up. The coverage measurement now
lives in `ReconcileWithCloud` as a real setup step, on the same `remoteIndexFor` code path, so nothing
was lost by deleting the screen that used to make it.

**This also removes `DocumentsContract.deleteDocument` from the source tree**, which CLAUDE.md
forbids outright. Worth being precise about what the risk actually was, because it was recorded
loosely earlier in this session: the call sat in the `main` source set and so was compiled into
release builds, but its only entry point was inside a `BuildConfig.DEBUG` block in Settings, so no
user could reach it. Unreachable, not shipped-and-callable. Deleting it is still better than moving
it to a debug source set — the API this project must never call is now simply absent.

### 25 Aug 2026 — the destination became a setting, and stayed safe

`REMOTE_ROOT` was recorded on 19 Aug as "treat it as fixed", because changing it "would strand every
existing backup and re-upload the library". That was true of a single constant. It is no longer the
design.

**Destination and search are now separate concerns** (`RemoteRoots`):

- the **destination** is where new uploads go, and the user can change it
- the **search set** is every root checked before concluding a file is missing, and it always
  contains `Samsung Gallery/DCIM` whether or not that is still the destination

That separation is what makes the setting safe. The Samsung path is not an arbitrary default — it
mirrors the layout Samsung's own sync created, which is the only reason the skip-existing check finds
anything at all. If changing the destination also moved the search, that reconciliation would vanish
the moment someone picked another folder, and the app would re-upload a library the user had already
paid to store.

`remoteIndexFor` now merges every root, and **one unreachable root makes the whole answer null**.
Merging only what listed would under-report what is backed up, and under-reporting means re-uploading
— the same "failing to ask is not evidence of absence" rule the per-album null exists for, applied
across roots. A root that does not exist yet is not a failure: the repository already turns 404 into
an empty page, so a newly chosen destination reads as empty rather than unknown.

**Verified end to end on the Fold 4** at 320dp with `font_scale` 1.7: the dialog renders, a typed path
persists to DataStore, the screen updates, and the "this is where Samsung already put yours"
explainer correctly disappears once the destination is no longer the Samsung root — that sentence is
only true while it is.

A text field rather than a folder browser, deliberately. Browsing OneDrive is the thumbnail browser
the design principle rules out, and the default is right for almost everyone; the field exists for
the few who want somewhere else, not as the main path through setup.

### 25 Aug 2026 — the first backup waits for a moment the user chose

The last open v0.2 item. The initial whole-library upload is the heaviest thing this app does — 148 GB
across 8,520 files on the Fold 8, roughly fourteen hours at the ~3 MB/s measured — so it no longer
starts at whatever moment setup happens to finish.

Default 1am, six-hour window, charging required. All three are settings.

Three decisions worth keeping:

- **Only automatic runs are gated.** "Sync now" goes straight to the engine and is never held. The
  window exists to stop the app choosing a bad moment on its own, not to stop the user choosing one
  the app disagrees with — and the screen says so rather than leaving it to be discovered.
- **The reason for waiting is named, not reduced to "waiting".** `FirstBackupHold` distinguishes
  `OUTSIDE_WINDOW` from `NOT_CHARGING`, because "waiting until 1am" and "waiting for you to plug in"
  ask different things of the user. A phone that appears to be doing nothing for an unexplained
  reason is the failure this is avoiding.
- **Charging is read by the app, not left to a WorkManager constraint.** A constraint that silently
  never fires is indistinguishable from a broken app, and this is the run a user is most likely to be
  watching for. `ChargingState` is shared by the worker and the screen so the two cannot disagree.

**The gate lifts permanently once the backlog clears**, one-way. Every later run is incremental, and
keeping it would make a photo taken at noon wait until 1am for no reason.

The midnight wrap carries most of the test weight: an overnight window is the normal configuration
here, not an edge case, and a window starting at 22:00 spends most of its life on the far side of
midnight.

Verified on the Fold 4 at 320dp with `font_scale` 1.7. At 11:04 with the phone plugged in it read
"Waiting until 1:00 AM" — the clock reported ahead of charging, and the time formatted for the
device's locale rather than hardcoded.

### 25 Aug 2026 — Gate 1, and a race that quietly undid it

The scan is now scoped to folders the user granted with `ACTION_OPEN_DOCUMENT_TREE`. One pick serves
both purposes the design needs: it says where to read, and it carries the persisted write grant that
lets a background worker proxy a photo without an Activity.

**Measured on the Fold 4: 72 albums in scope against roughly 90 unscoped.** That gap is the point —
the rest is app caches, thumbnails and screenshots that nobody means by "my photos".

Three things worth keeping:

- **Nothing is in scope until Gate 1 is answered.** `scanAll` returns nothing when no folder is
  granted, so the engine has nothing correct to do — which is what the gate means.
- **The reconciliation is hidden until then.** With an empty scope the check would report zero
  outstanding and announce that the whole library is already backed up. False, and false in the
  direction that stops someone acting.
- **Pruning is driven by an unscoped scan.** `scanEverything` exists solely for that. Asking "does
  this album still exist?" with a scoped result answers a different question, and would forget the
  ledger rows and album modes of every folder someone merely narrowed away. Narrowing hides; it must
  never forget.

**The bug worth writing down.** Granting a folder worked at every layer — permission taken, tree
persisted, scan rescoped, log confirming 72 albums — and the screen went on saying "No folders chosen
yet" until the app was restarted. Removing a folder updated instantly, which is what made it
findable.

The cause was one line:

```kotlin
_state.value = _state.value.copy(directoryRefused = !sources.add(treeUri))
```

Kotlin evaluates the `.copy` receiver — `_state.value` — **before** the suspending `add()` in the
argument. During that suspension the directories collector wrote the new folder into state; then
`.copy` was applied to the stale snapshot and assigned back, undoing it. A read-then-suspend-then-write
race in a single statement that looks atomic.

Fixed by completing the suspending call first. Checked the rest of the UI layer for the same shape;
nothing else puts a suspend call inside a `copy` argument. **Worth remembering as a pattern, not an
incident:** any `_state.value = _state.value.copy(x = someSuspendCall())` is this bug.

### 25 Aug 2026 — Gate 2, and telling the truth about "free space"

The second gate: what happens to the library already on the phone. Three options, safest as default,
and Archive absent by construction — a test asserts that no Gate 2 option maps to it, because setting
every album at once to the only mode that removes files, before v0.4 retrieval exists to undo it, is
the largest irreversible action this product can take at the moment the user knows least.

Selecting does nothing; applying is a separate tap. A radio list that acted on touch would make the
most consequential screen in the app the easiest to trigger by accident.

**The wording problem worth recording.** "Back up and free space" sounds proportional to the library
and is not, because only photos shrink. On the Fold 8 that is 16 GB of photos against 130 GB of
video: proxying everything reclaims about 14 GB, under 10%, while the phrase invites someone to
expect most of 148 GB back.

So the estimate lives in the domain layer (`LibraryEstimate`) rather than in copy, and the screen
picks its wording from the answer. Below a fifth of the library the saving is treated as marginal and
the sentence leads with what *stays*:

> Only photos shrink, and most of this library is video. That frees about 2.7 GB of 132.5 GB — the
> rest stays on your phone.

Confirmed firing on real data on the Fold 4. This is the naming rule from 18 Aug pointed the other
way: that one guarded against a soft name for a hard action, and this guards against a hard-sounding
promise over a soft result.

`ApplyLibraryChoice` is scoped to Gate 1, so a bulk choice never reaches a folder the user did not
pick, and uses `REPLACE` — correct here and only here, because an explicit bulk instruction is
exactly the case where overwriting an earlier per-album choice is what was asked for.

### 25 Aug 2026 — retrieval, built and half-verified

The download side of v0.4. Before this the app could list and upload and had no way to fetch anything
back, which made proxying a one-way door: an optimised photo is capped at 2048px for any editor, and
nothing could recover the original.

**What is proven.** Schema 6 -> 7 migrated cleanly on the real 6,371-row ledger on the Fold 4, the
screen renders, and its empty state is correct — nothing on that device is verified in OneDrive, so
there is genuinely nothing to fetch and it says so.

**What is not.** No file has been fetched back. The download, the MediaStore write and the size check
have never run against a real Graph response. That needs a row that is verified in the cloud *and*
missing locally, which the Fold 4 does not currently have, and producing one means writing to Ian's
OneDrive and arming an album — neither of which should happen unasked.

**Driven from the ledger, not a remote walk.** Decided by Ian. The obvious approach — list every
remote folder and diff against local — misses the case that matters most: once Archive removes a
file its album may hold no local files at all, so `scanAlbums` never returns it, and the files most
worth getting back become exactly the ones the list cannot see.

So schema 7 records `localMissingSinceEpochMillis`, set by diffing the ledger against an **unscoped**
scan. Unscoped matters: driving it from the scoped scan would mark a user's whole library as deleted
the moment they narrowed Gate 1, and then offer it all back. It also catches more than Archive — a
photo deleted in the gallery app, or a proxied original that genuinely is no longer on the phone.

**The diff is done in Kotlin, not SQL.** `NOT IN (:sixThousandKeys)` binds one variable per file and
exceeds SQLite's parameter limit on a real library, and it cannot be chunked — a file in the second
chunk would be marked missing by the first. The ledger's keys are loaded once and the difference
taken in memory, with the updates chunked at 500.

### 25 Aug 2026 — two defects that would have made retrieval offer almost nothing

Found by trying to fetch one file back on the Fold 4. Neither was reachable by reasoning; both needed
the round trip.

**1. The prune erased the rows retrieval is built from.** Removing an album's last file makes the
album absent from the scan, so `forgetAlbumsNotOnDevice` fired and deleted the ledger row for a file
that had just been verified in OneDrive. That is the Archive path exactly — take the files off the
phone, the album empties, and the app forgets everything it ever backed up from it. Rows still
verified in the cloud are now exempt from the prune; `LedgerPruningTest` guards it.

**2. The skip-existing path recorded no remote id.** When a file is found already in OneDrive by name
and size, the engine marked it `UPLOADED` with `remoteItemId = ""` — enough to say "already backed
up", and not enough to ever download it again. `remoteIndexFor` had the id from the listing and threw
it away.

The scale is what makes this serious: **that path covers 6,278 of 6,371 files on the Fold 4**, because
Samsung's own sync put most of the library there first. Retrieval would have shipped able to offer
almost nothing, while the ledger insisted everything was safe. The index now carries
`RemoteFileRef(id, sizeBytes)` and the skip path records the real id.

**Not yet verified on hardware:** that the corrected skip path writes a usable id. The code change is
two lines and the instrumented guard is in place, but the round trip has not been watched. Worth
doing before v0.4 is called done.

**Pre-existing rows stay unretrievable.** Anything marked uploaded before this fix carries an empty
id and, being already `UPLOADED`, is never re-checked. Deliberately not migrated: the app has no
users, the only affected ledgers are on two phones here, and both rebuild themselves from a rescan.

**A UI note that cost several cycles.** The backup header grows a line when nothing is selected
("Choose an album to sync"), which shifts every button below it. Fixed-coordinate taps against that
screen are unreliable, and one landed on "Deselect all" and reset every album mode. Read the screen
before tapping it.

### 25 Aug 2026 — retrieval proven end to end, and a third defect on the way

A file went local -> OneDrive -> deleted -> fetched back, and returned **byte-identical**:
`d60369934efda95c...` on both sides, 126,162 bytes. Every stage watched on the Fold 4.

The chain, and what each step proved:

1. Backed up through the **skip-existing** path, which now records a real Graph id where it used to
   write `""`. That is the path covering 6,278 of 6,371 files here, so it is the one that mattered.
2. Local copy removed; the row was marked missing **and survived the prune** —
   `kept 1 rows for files still in OneDrive but not on the device`, beside
   `forgot 1 rows` for the older row whose id was empty and which genuinely could not be fetched.
3. Listed in "Get back" with name, album and size.
4. Fetched: streamed download, `IS_PENDING` write, size check, published into `DCIM/Restored`.
5. Rescan cleared the flag and emptied the list.

**The third defect, found at step 5.** The list would never clear. A restored file lands in
`Restored` with a fresh timestamp, so its content key — `album|name|size|mtime` — can never match the
row describing where it used to live, and the ledger went on offering a file the user was already
looking at.

Fixed by clearing on **name and size** rather than on the content key. That is the same bar
`verifiedInCloud` uses to call a copy safe, so it is a fair test of "this content is on the phone
somewhere", and it is deliberately a different question from the one marking asks. Where the two
disagree — absent by key, present by content, which is exactly a restore — **back wins**.

Three defects in one feature, none of them reachable by reading the code. All three needed the
round trip.

### 25 Aug 2026 — the drive is asked again before anything is removed

`verifiedInCloud` reads a **remembered** byte size. It says a copy was confirmed once, which is a
different claim from "there is a copy now" — and removal is the one operation where only the second
will do. Nothing in the app re-checked, so a file deleted from OneDrive by hand left a row insisting
it was safe forever.

Demonstrated rather than argued, an hour ago: Ian deleted the retrieval test file from his drive and
the ledger went on asserting it was backed up, with nothing anywhere to notice.

`BackupEngine.confirmStillInCloud` now re-lists the drive at the moment of removal, one listing per
album, and only files it confirms **right now** go into the trash request. Three outcomes:

- **confirmed** — the only category that may be removed
- **missing** — checked, and not there. The ledger was wrong and the file is not backed up at all;
  the user is told, because that is a fact about their photos rather than an internal detail
- **unconfirmed** — the listing failed. Not an answer, and never treated as one

**If we could not ask, we do not remove.** The same rule the reconciliation follows, applied where
being wrong costs a photo rather than a wasted upload. `CloudConfirmationTest` pins it.

**Proven on the Fold 4, same afternoon.** A file was left on the phone with the ledger asserting it
was verified in OneDrive, and its cloud copy deleted by hand. Tapping "Remove 123 KB from this phone"
produced:

```
confirmStillInCloud: 0 confirmed, 1 no longer in OneDrive, 0 could not be checked
not removing: OneDrive confirmed none of 1
```

No system trash dialog appeared and the local file was untouched at 126,162 bytes. The screen said
so plainly, in error colour: *"Nothing was removed. OneDrive did not confirm a single one of these
files."*

Under the old code that is a deleted photo — the ledger said verified, and the trash request would
have gone out on that word alone.

**A line in Settings became true.** "Your OneDrive copy is checked first and is never touched" was
already in the app, describing a check against a remembered value. It now describes what happens.

The two held-back categories are reported separately in Settings and deliberately not merged: "OneDrive
no longer has this" and "could not check" ask different things of the user.

This is the guarantee CLAUDE.md's deletion rule actually rests on. The rule says removal is safe
because the cloud copy is verified; until now "verified" meant "was verified, once, possibly months
ago".

### 25 Aug 2026 — deletion sync, and the option that was not built

Ian asked for three behaviours when a file leaves the phone: leave the cloud copy, ask, or delete
automatically. **The third is not built**, and the reason lives next to the type in
`CloudDeletionPolicy` rather than only in a conversation.

The rhetorical objection is that CLAUDE.md requires confirmation of *that specific action*, and that
MILESTONES names silent bidirectional delete as the Samsung behaviour this project exists to replace.
The mechanical objection is stronger, and is the one that settles it: to delete automatically the app
must *notice* a file has gone, and the only available signal is absence from a scan. An unmounted
card, a revoked permission and a partial scan all produce that signal. An automatic mode turns one
bad scan into cloud deletions across a library.

**Four guards, none redundant:**

- policy must be `ASK`; default is `LEAVE`, and an unreadable stored value falls back to `LEAVE`
  rather than `ASK` — a corrupt preference must not be able to arm this
- a **grace period**, default 7 days. This is the answer to "never infers deletion from absence
  alone": what turns absence into evidence is not looking at one scan harder, it is that the absence
  keeps being true. A card that was out at 9am is back by lunchtime
- a **fresh scan immediately before deleting**, so a file that came back is dropped from the batch
  however recently the list was drawn — the mirror of the removal re-check
- an **explicit confirmation** naming the count, the size, and where the files go. `delete()` takes
  an already-approved list rather than re-deriving one, so the consent is to those files

Deletion has its own repository interface, the way uploading was split from browsing. One small class
is the only thing in the app able to remove anything from OneDrive, and it can only soft-delete: the
file lands in the recycle bin, which the user empties. A 404 counts as success, since already-absent
is the state the caller wanted.

**The 7 days is a guess and is labelled as one in the source.** Nothing measures it. It is offered as
1 / 7 / 30 / 90 so being wrong is cheap.

**Proven end to end on the Fold 4, 25 Aug 2026.** A test file was uploaded, deleted locally, its
absence backdated past the grace period, and the confirmation answered:

```
--> DELETE https://graph.microsoft.com/v1.0/me/drive/items/1D653E117DA59436!s392bfadc...
```

**Ian confirmed the file in the OneDrive recycle bin.** That is the guarantee CLAUDE.md's remote
deletion rule rests on — Graph soft-deletes, and the user restores or empties it themselves. The
ledger row was forgotten, since the file is then on neither the phone nor the drive.

**The request carries only an item id** — no name, no path. Ian noticed the entry in the recycle bin
had no file extension; that cannot originate here, because nothing in the request names the file. It
is OneDrive's own rendering of its bin. Worth a spot check on restore if it ever matters, but it is
their behaviour and not something this app can influence.

*Also fixed from that run:* the candidate summary read "1 file left your phone and **are** still in
OneDrive". Rephrased so no verb has to agree with the count, which is a worse problem in every
language than simply not needing one.

### 25 Aug 2026 — the trash request *did* reach the trash

**Fold 4.** GallerySync removed one local copy from `12345clips` — a 461 MB video already verified in
OneDrive — through `MediaStore.createTrashRequest`. Android's own dialog said "move to trash". Result:

```
-rwxrwx--- 461492580  .trashed-1790278932-20230819_121939.mp4
```

On disk, intact, byte-for-byte the original size, renamed into Android's media trash with an expiry
of **24 Sept 2026 — a 30-day window**. Ian confirmed it visible in Samsung Gallery's Recycle Bin.

**This contradicts the observation CLAUDE.md's rule was built on**, which says a trash request on this
same device removed files outright. Same handset, same API, opposite outcome.

Worth noting what the record actually contains. MILESTONES has no entry describing a
`createTrashRequest` failure; what it does record, on 19 Aug, is that `DocumentsContract.deleteDocument`
left nothing in the Recycle Bin — which is expected, because SAF's delete is permanent. The
"trash request removed files outright" claim exists only in CLAUDE.md. It is possible the two were
conflated. **That is a reading, not a finding**, and the original run cannot be re-examined.

**A capability nobody knew the app had.** The outcome is not detectable *before* the request, but it
is trivially detectable *after*: a trashed file is renamed to `.trashed-<expiry>-<name>` and sits in
the same folder. So the app could confirm what happened and say so, rather than warning about the
worst case unconditionally.

**Also learned:** querying MediaStore for `is_trashed=1` over adb returns nothing, because trashed rows
are owner-scoped. The disk is the reliable check; the query is not.

**Not settled by one run.** A different handset, a different One UI, a full or disabled trash could
all behave differently, and the platform still offers no way to know in advance. What has changed is
that recovery is demonstrably possible here, and that the app is currently promising less than it
could. Whether the rule moves is Ian's call — it is a safety rule, born of a data-loss worry, and one
successful test is not the same as a guarantee.

### 25 Aug 2026 — the Archive consent dialog promised a safeguard that does not exist

Seen on the Fold 4 while testing Archive. The dialog read:

> Files in *1999 Tioga* will leave your gallery and move to OneDrive once they are verified there
> **and older than your archive age.**

**There is no archive age setting anywhere in the app.** No such preference, no such gate, nothing
reading it. The sentence described protection that applied to nothing.

That matters more than a wording slip, because this dialog *is* the consent. CLAUDE.md is explicit
that setting an album to Archive is where the user agrees, once, to everything that album does
afterwards. Someone reading "older than your archive age" would reasonably conclude only their older
files were at stake — when in fact **every verified file in the album becomes eligible immediately**.

Clause removed. If the age gate specified in TASK-012 is built later, it comes back; until then it
must not be implied.

**The rest of the dialog is correct**, checked against the rule line by line: the files leave the
gallery, files added later are covered by the same choice, the verified cloud copy is the guarantee,
and a local removal cannot be promised as recoverable. The standing-instruction clause is the one
most easily left out, and it is there.

*Found only because Ian selected Archive on an album to show the dialog rather than to use it.* No
test asserts the text of a consent dialog against the behaviour behind it, and this is the second
time today that reading the screen caught something reading the code did not.

### 25 Aug 2026 — Archive: removal scoped to the mode, and one prompt rather than two

Two problems found by using the app rather than reading it.

**Removal was not scoped to Archive.** `redundantLocalCopies` returned every verified file whatever
its album mode, so Settings offered to remove files from **Backup** albums — while Backup's own
description promises "nothing on your phone changes and no space is freed". Observed live: a 440 MB
video was removed from `12345clips`, an album set to Backup.

CLAUDE.md settles which of the two contradicting statements gives way: *"Nothing leaves the gallery
unless the user chose that for that album... Removal follows from a mode the user set, and from
nothing else."* Now scoped to albums in `ARCHIVE`; with none set, nothing is offered at all.

**Archive mode did nothing.** `AlbumMode.removesLocal` was defined and read by no one — the only
references to `ARCHIVE` anywhere were UI labels and the confirmation dialog. Setting an album to
Archive uploaded it and removed nothing. The consent dialog described behaviour that did not exist,
which makes the "archive age" clause fixed earlier the smaller half of the problem.

**One prompt, not two.** Ian proposed a second dialog after the mode confirmation — *"Archived
confirmed on OneDrive, OK to remove from Gallery?"* — then withdrew it on being reminded that Android
asks its own question. CLAUDE.md is explicit: Android's trash dialog "is not where the consent comes
from, and it is not to be mirrored by an app-level prompt."

But the instinct caught something real. `createTrashRequest` only launches from an Activity, so
Archive **cannot** run unattended and something must bring the user back when files become eligible.
And Android's dialog says only "move to trash" — it cannot say the cloud copy is verified.

So the prompt is a **summons, not a consent**: it names the album, the count and the size, states that
the copies are confirmed in OneDrive, and launches Android's dialog directly. The user answers one
question. Asking the same thing twice is how a confirmation stops being one.

### 26 Aug 2026 — the first true fresh install, and a gate nobody could find

**Fold 4**, prepared as an isolated test rig: Samsung Gallery Sync off, the shared OneDrive account
removed from the device, `pm clear com.gallery.sync`, signed into a newly created Microsoft account.
Verified clean before starting — empty app data directory, no persisted SAF grants, 4,639 images and
1,736 videos still on the phone.

**Ian reported no albums in the Albums tab, unchanged by Rescan.** Nothing was broken. `scanAll`
returns nothing until Gate 1 grants a source tree, which is deliberate, and the log said so plainly:
`scanAll: no folders granted yet, returning nothing`, `refreshLedger: 0 files seen`.

What the run actually exposed is that the app **opens on a screen that is empty by construction and
cannot explain itself**, while the gate that fills it sits on another tab. Rescan is offered there
and has no chance of succeeding. See TASK-014, which specified the gates as wizard steps on 19 Aug;
they were built as a reachable tab instead.

**Why this had never been seen before.** Every previous run on this device was an upgrade over
existing app data, so a source grant was already persisted from an earlier session. The defect is
only reachable from a state no prior test had created.

*Isolation note, for the account switch:* the ledger is not bound to an account — `verifiedInCloud`
selects on state and byte size alone, and `signOut` does not clear it. Stale UPLOADED rows therefore
survive a switch and claim to be verified. Nothing removes on that claim: `confirmStillInCloud`
re-asks the live drive first, and `cloudDeletionCandidates` keys on local absence rather than cloud
absence. Both gates held. The rows were cleared anyway so the test would start from zero.

### 26 Aug 2026 — resumable uploads proven, and the window they actually give you

**Fold 4**, isolated rig, new Microsoft account, empty drive. The last open item in v0.2 tested
deliberately rather than waited for.

**Haku first, as a smoke test.** 19 files, 1,107 MB, all uploaded and verified — real `remoteItemId`
on every row and `remoteSizeBytes = sizeBytes` on every row, no failures, no retries. The empty-drive
path that had never run: `reconcile: 0 already in OneDrive, 6277 outstanding, 0 in 0 albums that
could not be checked`, the exact inverse of the 97.6% this drive normally reports. Missing album
folders returned 404 throughout and none of them landed in the unchecked category, which is the
19 Aug fix holding from the direction nobody had tried.

**Then the resume itself, on a 1,938 MB video.**

| | |
|---|---|
| Upload started | 11:52:55, `byte budget trimmed 25 candidates to 1` |
| `am force-stop` | 11:56:21, 206s in, roughly half sent |
| Session on the row after the kill | present, with an expiry |
| Restarted | 11:57:57, **new PID** — a cold start, not a backgrounded coroutine |
| Resumed at | `1069547520 of 2032370426 bytes (52% already accepted)` |

**1,020 MB not sent twice.** Persist, survive a process death, and resume from offset — all three
halves of the feature, on hardware.

**The caveat is the expiry, and it is shorter than it looks.** Graph returned about fifteen minutes,
measured twice: the Haku session at 11:22:07 expired 11:37:06, and this one moved to 12:07:55 as
chunks landed. So the window extends with activity but is always ~15 minutes from the last chunk.

That bounds what the feature promises. A run killed and restarted promptly is saved. A phone that
dies overnight is not — `resumeOffsetOf` will find the session unusable, log
`stored session is no longer usable`, and open a fresh one at byte zero. The milestone should not be
read as "large files can now always finish".

**A lone oversized file is still attempted**, confirmed twice: 25 candidates trimmed to 1 for a
1,938 MB file against a 512 MB budget.

### 26 Aug 2026 — a full drive, and what it did not damage

**Fold 4**, same rig, deliberately filled. The 5 GB test account reached 100% at 12:10:50, confirmed
in the OneDrive app: *"Your storage is full (100%)"*, Samsung Gallery folder at 5.1 GB. 23 files and
5,188 MB uploaded by this app to get there.

The next run, against a drive that could accept nothing:

```
12:20:35.574  W OneDriveUpl: upload: drive is full
12:20:35.575  W BackupEngin: uploadPending: stopping run — DRIVE_FULL
```

| Checked | Result |
|---|---|
| 507 surfaces as `StopReason.DRIVE_FULL`, and reaches the screen | yes — Ian saw the message |
| Run aborts rather than failing the other 23 pending files | yes, one attempt then stop |
| `attemptCount` unchanged | yes — 0 across all 6,277 rows, no `lastError` written |

**The third row is the one worth having tested.** Burning an attempt per file per run against a full
drive would walk the ledger toward `MAX_ATTEMPTS` and permanently abandon files that are undamaged
and would upload the moment space existed — silently, and discoverable only months later. It does
not happen.

**It also bounds the "no warning" defect rather than widening it.** Four runs today ended without the
screen saying anything, and this one did not: when a run has a `StopReason`, the UI reports it. The
silence is specific to `stoppedBecause == null` with files still pending, which is exactly the gap
FIX-001 describes and nothing larger.

### 26 Aug 2026 — a 2 GB restore, and the logger that was eating it

**Fold 4.** Fetching the 1,938 MB video back killed the process twice, identically:

```
java.lang.OutOfMemoryError: ... target footprint 536870912, growth limit 536870912
    at okio.Buffer.writableSegment$okio
    at okhttp3.internal.http2.Http2Stream.receiveData
```

**Cause: `HttpLoggingInterceptor.Level.BODY`.** It buffers an entire response body into memory in
order to print it. `@Streaming` cannot stop that — the annotation is Retrofit's, the interceptor is
OkHttp's, one layer below, and it has no idea the caller intends to stream. So the single endpoint
returning gigabytes was the single endpoint guaranteed to be buffered whole, and it died at the
512 MiB heap ceiling every time.

The irony is on the record: `downloadItem`'s own doc comment said `@Streaming` was required because
"a 2 GB clip would take the process down". It did, by the other route.

**Debug builds only** — release logs at `NONE` and never buffered. But debug is the build large-file
restore gets tested on, which is why video retrieval sat unverified in this milestone for eight days.

**Fixed** by giving downloads their own client: `GraphDownloadService` on a `@DownloadClient` that
logs at `HEADERS`, authenticated as before, same timeouts, `@Streaming` retained. Both defences are
now present and both are needed — one stops Retrofit buffering before the call returns, the other
stops the interceptor buffering after. `GraphApiService` no longer carries the endpoint at all.

**Then the test it was blocking, which passed on all three counts at once:**

| | |
|---|---|
| 2 GB download completed | no crash, `is_pending` cleared, 2,032,370,426 bytes on disk |
| Video retrieval | exercised for the first time — closes the v0.4 item |
| Resumed upload byte-identical | `sha256 2db7a4d6bc68a633ebd7fea301b8b15cc3d338c484f19d4b1ea5fa4dd570fb32`, local and restored |

That last row is the one worth having. The cloud copy was assembled from two upload sessions across
a process kill, and the file that came back is bit-for-bit the original. Size matching was already
known; this is content.

**Still open, noticed here:** the Restore tab shows file counts only, so a seven-minute single-file
download reads as a hang. `RestoreFromCloud` already emits byte progress and `RetrieveViewModel`
discards it. The Backup screen solved this and wrote down why: "a three-minute upload with no
feedback reads as a hang, and the biggest files are exactly the ones that take longest."

### 26 Aug 2026 — proxying does not cost editability

**Fold 4.** v0.3 promises a proxied photo "stays visible and editable in the phone's own gallery".
The visible half was verified on 18 Aug. The editable half never was, and it rested on an untested
assumption: that rewriting a file through the SAF tree grant leaves its MediaStore row alone.

Checked against files this app proxied on 25 Aug, alongside untouched files in the same album:

| File | `owner_package_name` | Size |
|---|---|---|
| `20260103_120938.jpg`, proxied | `com.samsung.android.scloud` | 819 KB, was 3.79 MB |
| `20260103_114450.jpg`, proxied | `com.samsung.android.scloud` | 624 KB, was 6.99 MB |
| `20260103_140149.jpg`, untouched | `com.samsung.android.scloud` | — |

**Ownership is unchanged.** A proxied file still belongs to whichever app created it, so it is exactly
as editable as before — the SAF write changes bytes, not the row. Reduction measured at 8–9x, against
the milestone's "roughly 10x".

**A note on how this was found, and one explanation withdrawn.** Ian edited a photo in Haku on 26 Aug
and Samsung Gallery would not save over it, writing `…(1).jpg` instead. That was recorded here the
same day as a consequence of file ownership — the original being owned by `com.samsung.android.scloud`
rather than by the editor. **That explanation is withdrawn.** It was tested and it does not hold.

Six controls, all edited and saved in place without complaint:

| | Resolution | Folder | Owner | Editable |
|---|---|---|---|---|
| `20260626_114338.jpg` | 4000 x 3000 | Camera | camera | yes |
| `20240621_050917.jpg` | 4000 x 2252 | Camera | **scloud** | yes |
| `Screenshot_20260701_181125_Messages.jpg` | 1812 x 2055 | Screenshots | systemui | yes |
| `Screenshot_20230615_054302_YouTube.jpg` | 1812 x 1968 | things to keep | scloud | yes |
| `IMG_5311.jpg` | **180 x 240** | 1999 Tioga | scloud | yes |
| `Screenshot_20250410_123138_YouTube Music.jpg` | 561 x 413 | Haku | scloud | **no** |

So ownership, folder, screenshot class, resolution and the `Samsung_Capture_Info` SEF marker are each
eliminated — the editable screenshots carry that marker too. The file itself is structurally sound:
SOF0 says 561 x 413 matching MediaStore, baseline JPEG, image data complete, ordinary 719-byte SEF
trailer. **The same image opens and edits fine from OneDrive**, so it is not the content either.

**Cause unknown.** It is one file in 6,375 and nothing about it touches this app.

**What it did prove is that backup writes nothing.** The Anne album, 53 images, was backed up with
before/after captures on both axes: every MD5 unchanged, and every MediaStore row unchanged —
`owner_package_name`, `is_pending`, `is_trashed`. Backup opens files read-only and alters no row.
That is worth having recorded, because editability is governed by the MediaStore row rather than by
the bytes, and "we did not touch it" is otherwise an argument rather than a measurement.

*Practical consequence for the ledger:* an edit saved as a copy is a new file with a new
`backupKeyOf` key, so it arrives as a fresh PENDING row and the original's row and cloud copy are
untouched. Combined with `conflictBehavior = rename` on every upload, editing a photo can never
overwrite the backup of what it was edited from.

### 26 Aug 2026 — emptying a folder makes the album vanish, and what survives it

**Fold 4.** Ian deleted all six files in `DCIM/12345clips` to exercise deletion sync, confirmed they
reached Samsung's Recycle Bin, and found the album gone from the Albums tab. "Show empty folders" did
not bring it back — that setting governs which *cloud* folders Restore lists, not the album list, and
reaching for it was a reasonable reading of the name.

**Samsung deletes a folder when its last file goes.** GallerySync derives its album list by scanning
local directories, so no directory means no album row.

**What survived, all of it deliberately.** Ian noticed on restoring the files that the album came back
with its GallerySync data intact, and asked whether the ledger had kept it — whether those files could
still have been marked missing. Checked, and yes:

| | |
|---|---|
| Ledger rows for the vanished album | kept — `forgetAlbumsNotOnDevice` exempts anything verified in OneDrive |
| Album preference (`SYNC`) | kept |
| `refreshLedger` | album-agnostic; diffs `uploadedKeys()` against the scan |
| Deletion sync on an emptied folder | would have worked — nothing marked only because the files were restored before a scan ran |

The prune has a companion query whose only purpose is counting the exemption for the log. So the
record of what is safely backed up is never lost to a folder disappearing.

**The consequence, which is a design hole rather than a defect.** Archive's whole purpose is removing
local copies once verified. Run it to completion and it removes the last file in an album; Samsung
deletes the folder; the album leaves the Albums tab. The user can then no longer see or change the
mode of an album they set to Archive — while that mode is still in force. CLAUDE.md is explicit that
Archive is a **standing instruction**: a file added to an Archive album later is covered by the mode
already set. So the instruction keeps applying and becomes undiscoverable and unrevokable.

**Only the display is broken, and the fix is already supported.** Every piece of data needed to keep
showing that album is retained on purpose. Album rows want sourcing from the ledger *and* the scan,
not the scan alone — an album with a surviving preference and verified rows should stay listed with
no local files, which is also exactly the state Archive is trying to reach.

**For the next deletion-sync attempt:** delete *some* files from a folder, not all. One file left
behind keeps the folder alive and the test on the thing being tested.

### 26 Aug 2026 — the content-signature safeguard, caught deciding a real case

**Fold 4.** Setting up a deletion-sync test: five of six files deleted from `DCIM/12345clips`, one
left so the folder would survive, mode set to Ask, grace set to 1 day. No files were ever offered for
cloud deletion, and the ledger reported all six as still present.

**Correct, and for a reason nobody had predicted.** Every one of those six had a `_restored` twin in
`DCIM/Restored` from an earlier retrieval test. `refreshLedger` compares by
`RestoredAlbum.contentSignature`, which strips the `_restored` suffix, so a file deleted from its
album but present in `Restored` matches by content and is classified **back**, not gone. The rule is
stated in the code — *"Back wins over gone: a restored file is absent by key and present by content,
and the second reading is the one the user would recognise"* — and this is the first time it has been
seen deciding an actual case rather than defending against a hypothetical.

The app was declining to offer up the only cloud copy of content the user still holds. Any other
answer would have been wrong.

**A restore test can silently make a deletion test impossible**, because content matching spans
folders. Worth knowing before designing either.

**Verified after removing the five twins:** all five marked missing at 20:02:24, and
`20230811_113841.mp4` — whose original is still in the folder — stayed `present`. The control behaved
differently from the test files, which is what shows the mechanism is discriminating rather than
merely reacting.

**Still unperformed:** a real `DELETE /me/drive/items/{id}`. The five become eligible when the 1-day
grace elapses, and it was left to elapse rather than backdated — the timestamp is not what is under
test, and a verification log is worth more without a doctored input in it.

**Suggested by this:** the deletion screen shows nothing when a file is held back for this reason. "Still
on this phone in Restored" is the explanation, and the screen not giving it is the same defect this
day kept producing.

### 26 Aug 2026 — automatic backup of new photos had never worked

**Fold 4.** Three photos moved into a Sync album. Nothing uploaded, nothing reached the ledger, and
the log stopped dead one line after `backup run starting`.

**The content-triggered worker cancelled itself.** Its first action was to re-arm the watch, and
`enqueueContentTriggered` uses `REPLACE` on `CONTENT_TRIGGER_WORK` — the same unique name as the run
executing it. WorkManager named the mechanism precisely:

```
Work [ id=dd48f3c7, tags={ BackupWorker } ] was cancelled
androidx.work.impl.WorkerStoppedException
    at CancelWorkRunnable.forNameInline
    at EnqueueRunnable.enqueueWorkWithPrerequisites
```

188 ms after starting, mid-`refreshLedger`.

**Why it hid for so long, which is the interesting part.** A run with nothing to do finishes in about
44 ms and beats its own cancellation — so it logs its answer and returns looking perfectly healthy.
Only a run with real work lives long enough to be killed. Every prior observation was of a run that
found nothing outstanding, which is exactly the case that cannot expose the bug.

New media therefore only ever reached OneDrive through the 6-hourly periodic net, while the app's own
description promised *"New photos sync shortly after you take them"*.

**Fixed** by re-arming at the end of the run rather than the start. The original comment argued for
arming first so a crash later still left the watch armed; that protection already exists twice, in
`enable()` at application start and in the periodic pass. The no-media-permission return still does
not re-arm, which was already correct — a timer cannot obtain a permission.

**Verified:** four photos moved into a Sync album uploaded across two unattended content-triggered
runs, each re-arming the next, nothing pressed, nothing pending afterwards.

**Residual, recorded rather than hidden.** The end-of-run re-arm still `REPLACE`s the work that is
finishing, so that run is recorded CANCELLED rather than SUCCEEDED and its `Result` is discarded.
Everything real is committed by then — uploads, ledger, continuation — but the outcome does not reach
the screen on those runs. The fix is a dedicated arm job under its own name, so nothing replaces a
running one. `APPEND_OR_REPLACE` was considered and rejected: appended work is cancelled when its
parent fails, so a drive-full run would leave the trigger silently unarmed — trading a visible flaw
for an invisible one.

### 26 Aug 2026 — optimising made unattended, seven days after it was possible

**Fold 4.** "Optimise automatically" meant "ask me about it automatically". `ProxyApplier` wrote
through `MediaStore.createWriteRequest`, which raises a system dialog per batch and only launches
from an Activity — so the rewrite could never happen without the user present, whatever the setting
said.

The 19 Aug probe had already proved the alternative, on this exact operation: a 4.4 MB photo owned by
`com.sec.android.app.camera` shortened to 4 KB through a persisted tree grant, no dialog, surviving a
reboot and a reinstall. CLAUDE.md names this as the grant's proper use. **The proxy path was simply
never migrated to it** — the finding sat in the log for a week while the feature it unblocked kept
asking for taps.

**Result, with an album switched to Sync:**

```
optimising 53 files through the tree grant
proxied 9 files, reclaimed 8071092 bytes, 44 not worth proxying
```

Ian: *"no pop up required."* 3.77 MB → 0.93, 3.25 → 1.10, 1.28 → 0.38. The other 44 were correctly
marked `isProxySkipped` — already under 2048px, recorded permanently so the count reaches zero
instead of sticking.

**The rescan lands.** MediaStore and on-disk sizes matched exactly on every file checked afterwards —
925513, 1095887, 384169 — so the staleness the 19 Aug run measured is handled by the
`MediaScannerConnection.scanFile` that follows every write. It is fire-and-forget rather than awaited,
which is the right trade: the ledger records the proxy size from the file this app just wrote, not
from MediaStore, so a briefly stale index costs a thumbnail that is a moment behind rather than a
wrong decision.

**A second defect, found in the same table.** A proxy came out *larger* than its original — 404 KB in,
490 KB out. The generator decides on pixel dimensions, which is the right test for whether
downscaling is possible and the wrong one for whether it helps: a heavily compressed source above
2048px re-encodes larger. It spent space, quality and a cloud badge to save nothing, and made the
reclaimed total negative. Now guarded where both sizes are already known, before the file is touched.

**Still true, and worth not forgetting:** video is never proxied, and Archive still needs
`createTrashRequest` because SAF deletes permanently. This changes the write path only.

### 26 Aug 2026 — optimising a photo made the app think it had been deleted

**Fold 4.** Found by watching an Archive run behave oddly, and it turned out to be the most dangerous
defect of the day.

`refreshLedger` decides what is still on the phone by comparing **name and size**. Proxying rewrites a
file in place and changes both its size and its mtime, so a proxied row's remembered key can never
match the file on disk. Six optimised photos sitting in the gallery were all classified as deleted
from the phone; fifteen such rows across the ledger.

**Two features broke on that one cause.**

**Deletion sync would have offered the full-quality originals of photos the user still has.** That is
the inverse of what optimising promises. Worse, Ian made the point that sharpens it: the local copy
carries a **cloud badge burned into its pixels**, and that badge is a standing promise that the
original is in OneDrive. Delete the original and every badged photo asserts something false, while
being indistinguishable from one still telling the truth. The user's only signal that their originals
exist would have become noise — and the badge is exactly what a careful user relies on when freeing
space.

Nothing was lost. Cloud deletion moves to OneDrive's recycle bin, the policy defaults to Leave, the
grace period had not elapsed, and the confirmation lists names. But a user on Ask would have been
asked the wrong question about the right files, and recovery would mean a different app's web recycle
bin inside 30 days, with nothing on the phone hinting anything was wrong.

**Archive could not see them either.** An album taken Sync then Archive offered **2 of 13** files, and
the 11 it could not see were the ones it had shrunk itself — so such an album could never be archived
at all. That is the whole point of the two modes composing.

**Fixed** by judging a proxied row on its **MediaStore id**, which survives a rewrite when size and
mtime do not. `refreshLedger` already relied on that property for the upload path — the comment there
calls it "the single most important line in this method" — and it simply was not extended to the two
places that ask whether a file is still present.

**And separately, not redundantly:** `cloudDeletionCandidates` now excludes `isProxied`. The
classification fix makes the answer correct; this makes it *safe* if the classification is ever broken
again. Wiring the badge and the cloud copy together so they cannot disagree is a stronger guarantee
than a check that happens to catch it.

**Verified:** 15 wrongly-missing rows fell to 0 on the next scan, Archive's count on the same album
went 0 → 6, and nothing was grace-eligible at any point in between.

**The method note.** This was found because a *user* said "only 2 files" about a result that looked
plausible. Every automated signal was healthy — no errors, no failures, correct-looking logs. The two
mismatched numbers were only visible to someone who knew what the album contained.

### 26 Aug 2026 — the founding use case, in 37 seconds, with nothing pressed

**Fold 4.** Ian took a photo, moved it from Camera into `Anne`, and opened the app. It was already
uploaded. Not a staged test — an ordinary action on a phone, which is the first time this feature has
been exercised that way.

Read out of the ledger afterwards rather than from the screen:

| | |
|---|---|
| `20260826_205441.jpg` | shutter 20:54:41, 4,643,976 bytes, 6112 x 6112 |
| Landed in `Anne` (mode `SYNC`) | 20:55:14 |
| `UPLOADED`, `remoteSizeBytes` = `sizeBytes` | **20:55:51** |
| `remoteItemId` | a real 50-character Graph id, not `""` |
| `attemptCount` | 0 — first attempt, no retries |
| `localMissingSinceEpochMillis` | null — **still on the phone** |

**37 seconds from the move into a Sync album to verified in OneDrive**, with the app closed and
nothing tapped. The whole ledger was clean afterwards: 136 rows, all `UPLOADED`, nothing pending.

This is the founding use case answered on its own terms. The original complaint was a clip that was
safe and *gone* ten minutes after recording; this is a photo that is safe in well under a minute and
still in the gallery, which Ian confirmed by looking at it in Samsung Gallery while this was being
checked. Both halves at once is the thing the product is for, and until tonight only the halves had
been tested separately.

It is also the strongest available confirmation of the content-trigger fix from earlier today. The
staged run moved four photos deliberately; this one nobody set up, and the trigger fired on a *move*
rather than a capture.

**What it exposed, in the code rather than on the device.** Checking how the screen came to say
"Uploading 1 of 1" led to `BackupViewModel.observeBackgroundWork`, which handles exactly two work
states: `RUNNING` sets `BackupStatus.Uploading`, and `SUCCEEDED` clears it. Nothing else clears it,
so an automatic run leaves "Uploading" on screen after it has finished, with the `refresh()` beside
it never running and the counts underneath stale. The manual path solved this earlier the same day
and wrote down why: *"The outcome, not the last thing we happened to see."*

**The first diagnosis was wrong, and the correction matters more than the defect.** It was recorded
here as "the run ends `CANCELLED` and there is no `CANCELLED` branch", with the fix being to add one.
Ian reported the status was gone by the time he looked again, which did not fit, so WorkManager's own
database was read:

| name | state | enqueued |
|---|---|---|
| `gallery-sync-backup-on-change` | **ENQUEUED** | 21:05:06 |
| `gallery-sync-backup-continuation` | SUCCEEDED | 17:03:02 |
| `gallery-sync-backup-manual` | SUCCEEDED | 16:51:58 |

**The 20:55 run has no row at all** — not `CANCELLED`, absent. That is not age-pruning, because the
`SUCCEEDED` rows from hours earlier are still there. `ExistingWorkPolicy.REPLACE` *deletes* the
WorkSpec it replaces, so the run erased its own record when it re-armed.

So `getWorkInfosForUniqueWorkFlow` never emits any terminal state for content-triggered work: the
list goes from `[RUNNING]` straight to `[ENQUEUED]` for a new spec. **No terminal-state branch can
ever fire, and adding a `CANCELLED` branch would fix nothing.** Only the structural split works —
give the trigger its own name so the run is not the thing being replaced, and it can end `SUCCEEDED`
where the observer can see it. The cheap patch was available, plausible, and would have shipped a fix
that changed nothing.

**Blast radius, narrower than first written.** The stuck status does not survive the app dying. Ian
saw it because he opened the app mid-run and the ViewModel was alive to catch `RUNNING`; the process
was frozen at 20:59:00, and on relaunch a fresh ViewModel starts at `status = null` with no `RUNNING`
work to find, so the screen reads clean. The defect is confined to one foreground session and clears
itself when the user leaves. Still a false claim on screen; not the persistent one first recorded.

**And then it optimised itself.** Recorded above as a live proxy candidate; at 21:04 the proxy ran,
unattended, closing the chain in the same unbroken action.

| | |
|---|---|
| `isProxied`, `localProxySizeBytes` | 1, 478,497 |
| On disk | 478,497 bytes, written 21:04 |
| MediaStore `_size` | 478,497 — the rescan landed, no staleness |
| `owner_package_name` | `com.sec.android.app.camera`, unchanged — still editable |
| `remoteSizeBytes` | 4,643,976 — the OneDrive original untouched at full size |
| `localMissingSinceEpochMillis` | null — still on the phone |
| `mediaStoreId` | 41946, unchanged by the rewrite |

**4,643,976 → 478,497 bytes. 9.7x**, the best measured yet against the milestone's "roughly 10x" and
the 8–9x of 25 Aug.

Nine minutes from shutter to safe, shrunk and still in the gallery, with the 37MP original one
Restore tap away. What makes it worth more than the ratio is how much of the project it exercised at
once, four pieces of it fixed the same day:

1. the content trigger fired on a **move**, not a capture — this morning's self-cancelling worker fix
2. uploaded and verified in 37 seconds
3. proxied through the **SAF tree grant with no dialog** — the 19 Aug finding, unused for seven days
4. the rescan reconciled MediaStore, so the staleness requirement from 19 Aug held
5. the proxy came out *smaller*, exercising today's guard on the case that motivated it
6. `mediaStoreId` survived the rewrite, which is what stops it being read as deleted — today's most
   dangerous defect
7. ownership unchanged, so the editability finding from earlier today held on a fresh file

Every previous test drove one of these with the others held still. This was one ordinary action with
nothing pressed, which is the only way to find out whether they compose.

**The badge, checked by looking at the file.** Pulled off the device and inspected: white cloud,
upright, on the dark rounded scrim, bottom-right, legible against a pale background. Geometry matches
`ProxyBadge.boundsFor` — a 2048 x 2048 proxy, badge 266 px at `SIZE_FRACTION` 0.13, inset 51 px at
`MARGIN_FRACTION` 0.025, spanning 1731–1997 on both axes.

**It does not test what it was flagged for.** EXIF Orientation is **1**, so the `when (rotation)`
block never ran: the badge is upright because no correction was needed, not because the correction
works. The `AaSync` sideways badges were a rotation failure specifically, and the case that retests it
is a **portrait photo**, which Samsung writes at orientation 6. Still worth having on real camera
output, because a square image is the degenerate case of the centred-square anchoring — the largest
centred square is the whole frame, so the badge lands in the true corner.

### 26 Aug 2026 — the Archive tab removes six files, and all six reach the trash

**Fold 4.** Ian validated `12345clips` on the new Archive tab and pressed Yes. Six files, one system
dialog — under the 2000-URI cap, so the batching path was not exercised. Every one of them left the
gallery.

**They are in Android's media trash, intact.** `ls -la` on the folder, which is the check MILESTONES
proposed on 25 Aug and nobody had run on a real removal:

```
.trashed-1790444333-123_1 (1).jpg                           493769
.trashed-1790444333-20260826_161003.jpg                     274441
.trashed-1790444333-20260826_161049.jpg                     851082
.trashed-1790444333-20260826_161057.jpg                     581083
.trashed-1790444333-20260826_162457.jpg                     691868
.trashed-1790444333-Screenshot_20240902_085700_Facebook.jpg 399920
```

Every byte size matches what was on disk before. Expiry 1790444333 is **26 Sep 2026** — a 30-day
window. `ls -l` showed the folder as empty, which is worth writing down on its own: the rename starts
with a dot, so the ordinary listing hides it and a removal that *did* reach the trash looks identical
to one that did not.

**And the cloud half held.** OneDrive has the full originals, not the proxies: `_161003` at 5.2 MB
against a 274 KB local proxy, `_161049` at 6.5 MB against 851 KB, `_161057` at 4.9 MB, `_162457` at
5.5 MB. `remoteSizeBytes` equals `sizeBytes` on every archived row. The thing the screen promised is
the thing that is true.

**This does not change the rule, and must not be read as changing it.** CLAUDE.md forbids telling the
user a local removal is recoverable. The tally on this one handset is now two recoveries against one
outright deletion, which is not a guarantee — it is the same unpredictability with a larger sample.
The guarantee the UI may state remains the verified cloud copy. What this does establish is that the
recoverable path is real and reachable, so the wording *"on some phones the local copy goes to your
gallery's Recently deleted; on others it is removed straight away"* is accurate rather than cautious
hedging.

**One finding, and it is not a live defect.** `123_1 (1).jpg` had a local proxy of 493,769 bytes
against an original of 404,241 — the proxy was 89 KB *larger*, so optimising it spent space to save
nothing. `ProxyApplier` already refuses this (`proxy.sizeBytes >= entry.sizeBytes` → `NotWorthwhile`),
and its comment cites this exact file as the case that motivated the guard. It is a pre-fix leftover:
1 of 15 proxied rows, and now archived. Every proxy written since the guard is smaller than its
original.

Worth noting what the archive did here regardless — it removed the *proxy*, and the full original was
already safe. That is the design working as intended on a file whose local copy was the wrong size for
the wrong reason.

### 27 Aug 2026 — a new album, end to end, and a defect that was never there

**Fold 4.** Ian made a `Test` album — 10 photos and one 115 MB video, 171 MB — and set it to Sync.
Nothing else was pressed.

| | |
|---|---|
| 11 files | `UPLOADED`, `remoteSizeBytes` = `sizeBytes` on every row |
| The video | `isProxied` 0, 115,244,716 bytes, untouched |
| The 10 photos | 56,277,329 → 7,156,743 bytes, **7.9x**, about 46.8 MB freed |

The video rule had never been tested on real content before. It held: videos are never optimised,
because a degraded clip fails silently inside an editor and is only found in the export.

**The defect that was never there, and how it was manufactured.** Mid-run the ledger appeared to show
3 of the 10 photos proxied on disk but unmarked in their rows. That is a real failure mode on paper —
`ProxyApplier` writes the file and *then* records it, and the two are not atomic — so it was
diagnosed confidently, written up with its consequences for Archive, and a repair pass was built and
shipped into `refreshLedger`.

It was a measurement error. **Room journals in WAL mode, and the database was being copied without
`gallery_sync.db-wal`.** Recent writes live in that file until a checkpoint, so every snapshot showed
pre-checkpoint state. The first pull of the evening included the WAL; every later one did not, and
nothing announced the difference.

Ian caught it by asking whether the original assumption was wrong. It was: all 10 had been proxied and
recorded correctly the whole time. The repair pass was reverted — it fixed nothing, and it would have
cost an EXIF read per candidate on every scan of a six-thousand-file library to guard a failure that
has never been observed.

**The rule this leaves.** Reading this app's ledger means reading `gallery_sync.db`, `-wal` and
`-shm` together, or checkpointing first. A single-file `cat` of a Room database is not a snapshot of
it. Every ledger figure quoted from one is suspect, including the ones in this file that were gathered
that way.

**The diagnostic that settled it** is worth keeping too: the repair pass logged `row=null` for every
file, which read as "the lookup is broken" and was actually "the live database has nothing to repair".
An instrument disagreeing with the evidence is a reason to doubt the evidence.

## targetSdk — researched 19 Aug 2026, resolved in favour of 37

CLAUDE.md said 35 while the build file said 37. **35 was the stale one**, and keeping it would have
blocked the first Play submission.

### The Play deadline decides it
| | Requirement |
|---|---|
| **New apps and updates, from 31 Aug 2026** | must target **API 36** (Android 16) or higher |
| Existing published apps | must target API 35 to stay available on newer devices |
| API 37 | no Play deadline until roughly Aug 2027 |

GallerySync will be a **new submission**, and the release gate puts that after v0.3 and v0.4 — well
past 31 August. So 35 is not merely conservative, it is non-compliant. 36 is the floor; 37 is valid
and buys a year.

**API 37 is Android 17, released 16 June 2026** — a stable version, not a preview, so there is no
risk of targeting something Play will not accept.

### What targeting 37 pulls in, checked against this app
| Change at targetSdk 37 | Affects GallerySync? |
|---|---|
| **Large screens ignore orientation, resizability and aspect-ratio limits** (`sw>=600dp`) | **Yes** — see below |
| `ACCESS_LOCAL_NETWORK` now required for LAN access | No. Graph and MSAL are ordinary internet hosts; the permission covers local addresses, mDNS and SSDP |
| Background audio hardening | No audio |
| Contacts Provider PII columns restricted | No contacts |
| Bluetooth RFCOMM `read()` returns -1 | No Bluetooth |
| SMS OTP three-hour delay | No SMS |
| Reflection on static final fields throws | Not our code; a dependency risk to watch |
| Encrypted Client Hello | OkHttp handles TLS; worth watching, nothing to do |

Nothing in MediaStore, the media permissions or `createWriteRequest` changes at 37, so the whole
consent design above is unaffected.

### The one change that does apply, and one real defect it exposes
The large-screen adaptation has **no opt-out at 37** — the Android 16 escape hatch was removed. On
any display wider than 600dp, `screenOrientation`, `resizableActivity` and the aspect-ratio
attributes are ignored and the app fills the window.

Structurally this app is already fine: the manifest sets none of those attributes, and it is being
developed on a Fold's large inner screen. But the compatibility requirement includes *properly
saving and restoring UI state across configuration changes*, and it does not:

- `MainActivity` holds the selected tab in `remember`, not `rememberSaveable`
- there are **zero** uses of `rememberSaveable` in the app

So folding, unfolding, rotating or entering split-screen throws the user back to the first tab. On a
foldable that is a routine gesture, not an edge case. Observed during theme testing and misread at
the time as a side effect of relaunching.

**Not fixed here** — it is a UI behaviour change and wants both-theme device verification, so it is
Ian's to schedule. It is small: `rememberSaveable` for the tab index.

## Versioning — decided 19 Aug 2026

**`versionName` tracks the milestone being built.** Currently `0.3.0`. A crash report or a Play
console entry then says which milestone it came from, and the number means the same thing in the
build file as it does in this document.

Not `1.0`: that would claim a maturity the app does not have, and the release gate means nothing
ships before v0.4 regardless.

**`versionCode` is a plain incrementing integer** with no relationship to the name. Play only ever
accepts a higher one, so it is bumped per upload and never reset. Still `1`, because nothing has been
uploaded.

---

## Open questions

**Needs Ian's decision**
- **Where TASK-011's applying step runs.** WorkManager cannot attach the `ClipData` that carries the
  write grant, and Android 12+ blocks starting a foreground service from the background. Recommended:
  background detection and notification, applying on the tap. See TASK-011.
- **Whether the tap can be removed entirely.** Two routes: `MANAGE_EXTERNAL_STORAGE`, which works
  and spends Play-listing scrutiny, and a persisted SAF tree grant, which is cheaper and unverified.
  Recommended: test the SAF route on hardware first. A new Play-visible permission and a fork in the
  architecture are both escalations. See TASK-011.

  **Answered 19 Aug 2026 — the SAF route works, and it is neither.** A persisted tree grant does
  the proxy write with no dialog and survives reboot; `MANAGE_EXTERNAL_STORAGE` is not needed and
  the Play listing is untouched. Archive still needs `createTrashRequest`, because SAF deletes
  permanently. See the SAF entry in the hardware log.
- **`POST_NOTIFICATIONS`** — the only live question is **when to ask**, not whether. The SAF finding
  removed its consent role for photos, so it is now informational plus the Archive batch prompt, and
  nothing breaks if denied. Play cost is negligible. Recommended: media permissions at first run,
  notifications when the user first sets a floor or an Archive album. See TASK-011.
- **Language dropdown** — **answered 19 Aug 2026:** it belongs in a first-run wizard alongside
  permissions, cloud choice and defaults. Ship English only behind it. See TASK-012.

**Known and unbuilt**
- **Album selections are device-only.** The one part of the ledger that cannot be rebuilt from
  OneDrive or from the files, so a phone move loses them. Needs a new remote write path.
- **Proxy recovery is untested on hardware.** `LedgerRecovery` guards against re-uploading proxies
  once the ledger is lost, but reproducing it means destroying a real ledger. Wants an in-memory Room
  test seeding a pending proxy row.
- **UI state does not survive configuration changes.** No `rememberSaveable` anywhere, so folding,
  rotating or entering split-screen resets the selected tab. Routine on a foldable, and an explicit
  compatibility requirement of the large-screen change at targetSdk 37. Small fix; wants both-theme
  device verification.
- **The XML theme is hardcoded Light** with no `values-night` variant, so a cold launch in dark mode
  starts light. Deferred to the visual refresh, which touches that file anyway. See TASK-012.
- **Six photos in `AaSync` carry the pre-fix sideways badge.** Harmless and marked as proxies. Delete
  locally and re-fetch to tidy.
