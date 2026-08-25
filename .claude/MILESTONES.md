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
- [ ] **Persist the upload session URL and its expiry on the ledger row.** Confirmed on hardware: a
      run killed at 96% of a 164 MB video restarted from byte zero. Any file too large to finish
      inside one run can never complete, and the threshold scales with upstream, not file size.
- [ ] Retry failed items from the UI
- [x] **Start time for the first backup.** The initial whole-library upload is the heaviest thing the
      app ever does. User-set, default overnight (1am, six-hour window), charging required for that
      first run. Only automatic runs are gated — "Sync now" is never held, because someone who asked
      has already decided this is a good moment. The gate lifts for good once the backlog clears.

## v0.3.0 — Space management
The milestone that delivers the actual product: the phone stops filling up, and the existing gallery
keeps working.

- [x] **Photo proxies.** Downscale to ~2048px, EXIF preserved, proxy kept in MediaStore permanently.
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
- [ ] Fetch a cloud-only item back on demand, registering it in MediaStore so every app sees it.
      Photos and video both — it is the only route back to a full-quality edit from a proxy.
- [ ] Plain retrieval list — **not** a photo browser. Also the only place a fetch can be triggered:
      there is no hydration hook, so tapping an item in Samsung Gallery cannot reach us.
- [ ] Deletion sync, opt-in and batched. Highest-risk feature in the product; it only follows a
      backup engine that has been watched working. Never infers deletion from absence alone.

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
