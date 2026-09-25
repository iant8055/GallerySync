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
- **Strip the testing affordances before submission.** *Run setup again* (Settings → Setup) is a test
  tool and will not ship; it goes as soon as the wizard is settled, which is expected to be well
  before submission — see "The wizard and the Settings tab are independent" below.

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
  full-length downscale hands it 480p to edit from.

  **"Caps the export" was wrong and is withdrawn — Ian, 28 Aug 2026:** *"In CapCut you CAN upscale a
  480p to 1080p and even higher."* He is right, and it was said twice in this file. What a downscale
  actually costs an edit is **detail, not resolution**: the export can be any size, and upscaling
  cannot put back information that is not in the source. That is a real cost and a smaller one than
  claimed, and the claim should not be repeated in the stronger form.

  **The rule does not rest on it.** Recent video is protected because of the founding use case —
  shooting something and wanting to edit it properly ten minutes later — which stands whatever CapCut
  can be persuaded to output.
- **Old video may be downscaled full-length**, marked, on charge, Sync albums only. For footage
  people *watch* rather than edit, a downscaled clip is fine, and retrieval covers the rare edit —
  exactly as for photos. Needs Media3 Transformer and a transcode cost measured on real 8K footage
  before committing.
- **"Old" is a user setting, decided by Ian 19 Aug 2026 and rescaled 28 Aug.** Now **Straight away ·
  1 hour · 12 hours · 1 day · 1 week**, shared by photos and video so the app still has one
  vocabulary of ages. The original values were Immediately / 1 week / 1 month / 1 year defaulting to
  a year.

  **The rescale weakens "recent video is never touched", knowingly.** Under a one-hour age a clip
  shot this morning can be reduced to 480p. Two measurements make that defensible: the founding
  failure was *absence* rather than quality — the clip had been moved off the phone, where optimising
  leaves it in the gallery playing normally — and 480p proved indistinguishable from the original on
  the Fold's inner display. A wait measured in months was protecting against a difference nobody
  could see. What survives is the edit case, which is what the age gate is now for.

  **The age is asked of each file, not of the setting.** "1 day" means each file becomes eligible a
  day after it was last modified — so within a Sync album, everything old qualifies at once and the
  gate only ever holds back the recent end. Ian's correction, 28 Aug: this is scoped to Sync albums,
  and an earlier note here saying "the entire back catalogue" was wrong. Worth remembering that
  Gate 2's *"Back up and free space"* sets **every** album to Sync in one tap, so the whole-library
  case is reachable by someone who has not yet met this setting. "Old" is not a fact about anyone's footage: client work gets edited for
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
- [x] Retry failed items from the UI
      **Built 21 Sept 2026, see the entry at the end of this file.** An album that is being backed up and has failed
      files shows "N failed" on its row and, opened, a hint and a **Retry N failed** button. The audit note below
      describes the gap as it stood before that.
      **Audited 20 Sept 2026: not built, and it is a gap rather than a nicety.** A file that fails five
      times (`BackupEngine.MAX_ATTEMPTS`) is marked `FAILED` and `nextPending` never selects it again.
      `BackupEntryDao.resetFailures()` exists to put such rows back, and **nothing calls it**. The Albums
      tab reports "N failed" after a run and an album's file list labels the file "✗ failed" with its
      error, but there is no control: a file that hit its five attempts stays unsent until the ledger is
      rebuilt. Network trouble does not count against a file (the run leaves it `PENDING`), so this is
      only for genuine per-file failures.
- [x] **Start time for the first backup.** The initial whole-library upload is the heaviest thing the
      app ever does. User-set, default overnight (1am, six-hour window), charging required for that
      first run. Only automatic runs are gated — "Sync now" is never held, because someone who asked
      has already decided this is a good moment. The gate lifts for good once the backlog clears.

## The wizard and the Settings tab are independent — in both directions

Stated by Ian in capitals, 7 Sept 2026: **SETTINGS HAS NO EFFECT ON THE WIZARD**, and **THE WIZARD
HAS NO EFFECT ON THE SETTINGS**. Also its own section in CLAUDE.md, because the same shape of error
had just recurred in a third place.

Two separate surfaces. The wizard collects its own answers and is not a view onto Settings; Settings
is not a record of what the wizard was told. Neither reads the other and neither writes the other.

**What prompted it.** A wizard walk on the Moto G (`ZT422CTZQV`) on 7 Sept 2026 produced a report
listing two differences as defects. Both were struck:

- *Choose folders to back up* opened with **no folder checked**, while the Settings tab listed
  `Internal storage / DCIM` under *Folders to back up* and the SAF tree grant was already held.
- The wizard's Cloud Storage card named the destination `Samsung Gallery/DCIM`, while the Settings
  tab read `OneDrive / GallerySync`. (The Graph calls in the same session went to
  `/me/drive/root:/Samsung Gallery/DCIM/...`.)

Neither is an inconsistency to reconcile. The same holds in the other direction: **Settings unchanged
after the wizard has been answered is correct** — which is the Area 1 rule below, stated from the
other side.

**The test.** If an explanation you are building requires one of these surfaces to write the other,
the explanation is wrong by construction. Discard it rather than checking it — the same test the
optimising areas below already carry.

**The *Run setup again* button in Settings is a testing affordance and WILL NOT SHIP.** Stated by Ian,
7 Sept 2026. It exists so the wizard can be re-entered on a device without wiping app data, which is
the only practical way to exercise the flow repeatedly. Consequences:

- **Do not treat its copy as product copy.** It currently reads "It opens on your current settings,
  changes no album on its own, and does not ask you to re-confirm anything you have already read" —
  that string is not a spec, creates no obligation on the wizard, and is not worth correcting.
- **Do not build on it, test against it, or polish it.** No user-facing behaviour may depend on the
  wizard being re-enterable after setup completes.
- **It goes away once the wizard is settled.** Ian, 7 Sept 2026: *"Once we finally get the Wizard set
  that setting will go away."* The trigger is the wizard work being finished, not the submission date
  — it is simply that the wizard is still being iterated on, and re-entering it is how that is done.
  It must be gone by the Play Store submission regardless; tracked under the release gate above.

## Optimising: two independent areas — do not conflate them

Stated by Ian, 29 Aug 2026, after an agent repeatedly welded these together. **They are separate. One
does not set, read, or imply the other.** Written here exactly as specified so future runs build to
this and not to a guess.

### The invariant that binds all of it

**Album modes (Off / Backup / Sync / Archive) are set ONLY by the user, per album.** Nothing else
ever writes an album's mode — not the install wizard, not the optimise settings, not a worker. Every
sentence below rests on this.

### Area 1 — the initial-install choice (the four modes)

- **Present ONLY at initial install.** It is a one-time choice, not a stored setting.
- **Acts ONLY on the files currently on the device** at install time. A one-time bulk action over the
  existing library.
- **Has NO effect on Area 2** (the optimise settings tree) and **does NOT set album modes.**

The four options, verbatim:

1. Check cloud storage and back up everything that isn't already backed up.
2. All of #1 — plus optimise **all** files on the phone, for **maximum** storage saving.
3. All of #1 — but optimise **only newly backed-up files** (ones that were not already in cloud
   storage), for **moderate** storage saving.
4. Check cloud storage but do **not** back up any new files — no storage savings. The user will
   manually select which albums to Backup/Sync afterward.

- **If #2 OR #3 is chosen**, the user is then asked the optimisation **level: High / Medium / Low**,
  mirroring the same three levels in Settings (High 480p · Medium 720p · Low 1080p). This level is
  used only for the one-time bulk optimise and **does not persist into Settings.**
- **The level applies to video only** (Ian, 29 Aug 2026). Photos optimise to their fixed proxy size —
  there is no photo level to ask for. So the H/M/L prompt is a video-quality choice, and it is asked
  because #2/#3 may optimise video.
- **#3 identifies "newly backed up, not already in cloud" by comparing the device against cloud
  storage** (Ian, 29 Aug 2026) — the same reconcile the app already performs. This choice runs after
  access to both cloud storage and the local device has been granted, so the comparison is available:
  the files that were **not** already in the cloud are exactly the ones this pass backs up, and those
  are the ones #3 then optimises.

This is the guided first run — see TASK-014.

### Area 2 — the ongoing optimise settings (after install)

- **One global settings tree**, establishing how photos and videos are optimised from install onward.
  It is **not** a per-album setting.
- **Applies ONLY to albums the user has set to Sync.** Backup never optimises. Archive never
  optimises. Off does nothing. The candidate queries already enforce this (`videoOptimiseCandidates`
  and the photo equivalent only pick files in Sync-mode albums).
- **Does NOT set album modes**, and is **not** touched by the Area 1 install choice.

The tree, exactly:

```
isOptimiseEnabled     master switch, off until asked
  optimisePhotos      Y / N
  photoOptimiseMode   Auto | Manual
                      (no photo age - photos are proxied whatever their age, TASK-011. One was
                      added 28-30 Aug in contradiction of that and removed 15 Sept, TASK-022 B)
  optimiseVideo       Y / N
  videoOptimiseMode   Auto | Manual
  videoOptimiseAge    same five
  videoQuality        High 480p · Medium 720p · Low 1080p
```

See TASK-012 for where this lives in the Settings screen.

### Resolved 29 Aug 2026

Both points that were open here are now answered by Ian and folded into Area 1 above: the H/M/L level
is **video only** (photos take their fixed proxy size), and #3's "newly backed up" set is found by the
**device-vs-cloud reconcile** the app already runs, once cloud and device access is granted.

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
- [x] **Storage budget — resolved as an internal concern, 29 Aug 2026.** Ian: *"storage floor has
      become an internal coding issue — nothing the user needs to set."* The app still reads free
      space so it does not begin work it cannot finish — a restore needs room for the file it fetches
      — but there is no floor to choose, no worker managing to a target, and no notification. The
      original specification, kept for the reasoning it contains, was: ~~User sets a free-space floor, default 20 GB, with an enforced minimum so it
      stays clear of Android's low-storage threshold — below that the backup worker stops running and
      nothing new becomes eligible. Proxying is the only lever; nothing is deleted. If it cannot
      reach the floor it stops and says so. Notifies when free space drops below the floor, which is
      also how it asks for the next batch of write consent. See TASK-011.~~
- [x] **Album modes in the UI.** Schema 4 carries Off/Backup/Sync/Archive; the screen is still a
      switch. See TASK-012. **Built, ticked 20 Sept 2026 by audit.** Each album row on the Albums tab
      carries a mode pill that opens an Off / Backup / Sync / Archive menu (`AlbumModeDropdown`), Archive
      behind its confirmation, with a default mode for new albums in Settings and a mode filter above the
      list. The Camera album's menu has no Sync (20 Sept 2026).
- [ ] **Running count of space saved, per album and in total.** Each album row says what has already
      been freed and what its selected mode could free, updating as the mode changes. Same
      aggregates the floor uses, so the two screens cannot disagree. Added by Ian 19 Aug 2026. See
      TASK-011.

      **Audited 20 Sept 2026: half built, so left unticked.** *In total* exists: with the Sync filter on,
      the Albums card reads "N Optimised · X Saved" (`AlbumsSummary.savedBytes`). *Per album* is computed
      (`AlbumRow.savedBytes`, from the ledger) **but no row draws it**: an album row shows only how many
      files are optimised. *What its selected mode could free* is not built for albums at all; the only
      forecasts in the app are the wizard's estimate and the Camera album's per-age estimate.
- [x] **Sync scope — two toggles, photos and video, and they gate optimising only.** Revised by Ian,
      29 Aug 2026, replacing a tri-state (Photos only / Video only / Both) that gated *uploading*.

      **The old shape was wrong about what Sync is.** TASK-011's table said the excluded medium was
      "not uploaded, untouched", and MILESTONES repeated it. But uploading is governed by the album
      mode — Backup, Sync and Archive all upload, and only Off does not. Sync is backup **plus**
      space management, so a setting scoping Sync can only sensibly scope the second half.

      Ian, 29 Aug: *"turning off syncing/optimising doesn't mean it can't still be backed up."*
      Correct, and it makes the setting far safer: video with optimising off is still in OneDrive at
      full size, and still archivable and restorable — all of which need a verified cloud copy. Under
      the old reading, one toggle silently disabled three features and left the largest files on the
      phone unprotected.

      **Built, ticked 20 Sept 2026 by audit.** Settings → Sync has *Optimise photos* and *Optimise videos*
      switches (`optimisePhotos`, `optimiseVideo`), each with its own Automatic/Manual mode. They are read
      only by the optimise policies and workers (`PhotoOptimisePolicy`, `VideoOptimisePolicy`,
      `OptimiseWorker`, the two launchers); nothing in the upload path reads them, so turning either off
      leaves that medium backed up, archivable and restorable.
- [x] **Video transcode for old clips**, age a user setting (wired to Settings 18 Sept 2026, see the last entry) — see TASK-013. The write needs no tap
      (SAF, verified 19 Aug 2026); the blocker is a transcode cost measured on real 8K footage, and
      it is gated on v0.4 retrieval.
- [x] **Guided first run** — language, cloud, sign-in, permissions, then two gates the engine cannot
      start without: which directories to pull from, and what to do with the existing library. The
      directory picker is also the SAF write grant. See TASK-014.
      **Built, ticked 20 Sept 2026 by audit, with one part not done.** `SetupTour` walks sign-in, media
      permission, directory discovery and the SAF grant, the cloud destination, the four library
      choices, the video-quality level, the first-backup start time and the backup progress, with tab
      tooltips over mock screens. Run from a fresh install on the Moto G on several dates, most recently
      the 2,079-file run of 19 Sept. **Language is not built:** Settings shows a *Language* line reading
      "Multi-Language Support Coming Soon", and the language step was scoped and shelved by Ian on 4 Sept
      (see Open questions). *Run setup again* in Settings is the testing affordance that will not ship.
- [x] **Move to backup should distinguish photo from video**, or be replaced by Archive mode.
      **Replaced by Archive, ticked 20 Sept 2026 by audit.** There is no "Move to backup" control or
      string left; removal is the Archive mode and goes through `MediaStore.createTrashRequest`.
      Two leftovers carry the old name and are unused by any screen: `BackupViewModel.buildMoveToBackupRequest`
      / `onMoveToBackupFinished`. (`LocalCopyRemover.createMoveToBackupRequest` is still called, by the
      Archive screen.)

## v0.4.0 — Retrieval and deletion sync
- [x] Fetch a cloud-only item back on demand, registering it in MediaStore so every app sees it.
      Photos and video both — it is the only route back to a full-quality edit from a proxy.
      **Verified byte-identical on the Fold 4, 25 Aug 2026.** **Video exercised 26 Aug 2026** — a 2 GB
      clip fetched back and SHA-256 matched the local original exactly.
- [x] Plain retrieval list — **not** a photo browser. Also the only place a fetch can be triggered:
      there is no hydration hook, so tapping an item in Samsung Gallery cannot reach us.
      Populates, fetches, and clears itself once the file is back. **What it lists changes with
      TASK-018** — see the restore entry below; the "not a photo browser" constraint is the part
      that carries over unchanged.
- [x] **Never trust the ledger for what is on the phone.** 25 Aug 2026, and it still holds — it is
      the reasoning `ProxyMarker` was later built on. The ledger records what left *this* device, so
      on a fresh install it is empty by construction: the user signs in, their whole library is in
      OneDrive, and a ledger-driven list offers nothing at all. `DCIM/12345clips` showed the gap in
      miniature — seven videos backed up, one offered, the other six absent only because they were
      still on the phone. Anything answering "is this on the phone, and is it ours?" asks the file,
      not the ledger.
- [x] **Restore replaces the proxy; it does not download a second copy.** Supersedes the
      drive-listing tab built 25–26 Aug 2026. See TASK-018.

      The old tab listed what OneDrive holds under the backup roots and fetched a chosen file into
      `DCIM/Restored` as `name_restored.ext`. Ian, 27 Aug 2026: *"This really ISN'T a restore — we
      are not restoring a file, just downloading it,"* and *"if the user wants a straight download
      they can use OneDrive."* It was a worse version of a file browser the user already has, and
      the copy it produced landed beside the file they were looking at rather than replacing it.

      What replaces it: opening the tab scans the gallery for files carrying the proxy marker,
      groups them under their own folders, and restoring one writes the full-size original back
      over the proxy, in place, through the persisted SAF tree grant.

      **The new-phone case moves to initial download, not to this tab.** TASK-014's guided first run
      is where a fresh handset gets its library back — bulk, once. Restore is what happens
      afterwards, to files this app has since shrunk. Initial setup **downloads** what is not here;
      restore **replaces** what is here but smaller. Different verbs, different populations, and
      neither is the other's fallback. So this tab being empty on a new phone is the correct answer
      to "what have I optimised on this device?", not a gap.

      **The grow is verified.** Fold 4, 27 Aug 2026, via `SafGrowProbe` — a 4,096-byte file rewritten
      to 524,288 bytes through the tree grant, no dialog, MediaStore reporting the new size after the
      rescan. Until that measurement existed the whole design was resting on an assumption, since
      what had been proven on 19 Aug was a *truncating* write and this is its reverse. Not yet
      measured at 40 MB or in a separately granted directory.

      **An interrupted restore costs nothing on either side.** Fold 4, 27 Aug 2026. Two restores
      stopped mid-transfer, 36 MB and 47 MB already written: no degradation to the OneDrive copies,
      no partial file in the gallery, and the stop was immediate rather than running on to the end of
      the current file. That is the premise the whole design rests on, since this one overwrites a
      file the user has.

      `RestoredAlbum` and the `_restored` suffix stay. Files fetched by the old flow carry that name,
      and `contentSignature` must keep stripping it — three places test `name|size` to decide whether
      content is on the phone, and one of them is the last check before a cloud copy goes to the
      recycle bin.

      **Built, ticked 20 Sept 2026 by audit.** `RestoreProxyInPlace` writes the original back over the
      proxy; tagged **v0.3.1** on 27 Aug and verified on the Fold 4 (a 496 KB proxy back to 738,695 bytes,
      a 200 MB clip stopped at 47 MB with nothing left behind), and again on the Moto G on 18–19 Sept.
      **It has since grown:** from 18 Sept the tab is drive-based again and *also* downloads files
      OneDrive holds that are not on the phone, and greys out the ones already there. Replacing a proxy is
      still what a restore of an optimised file does; downloading is the other half, not a replacement.
- [x] Deletion sync, opt-in and batched. Highest-risk feature in the product; it only follows a
      backup engine that has been watched working. Never infers deletion from absence alone.
      **Built 25 Aug 2026**, default Leave, no automatic option. Screens verified. **A real cloud
      deletion has since been performed and watched:** Ian confirmed the file in the OneDrive recycle bin
      on 25 Aug (see that day's entry), and the Ask window built 19 Sept moved test files to it on the
      Moto G (see the 19 Sept entries).

## v0.5.0 — Google Photos + Billing
- [ ] Google Play Billing (`pro_unlock`)
      **In progress, 24 Sept 2026 — see `.claude/tasks/TASK-026.md`, which is the record.** Built and pushed:
      BillingRepository + signature check, AppAuth Google sign-in, Photos upload client, per-folder destination
      (schema v14) and the wizard's cloud + folder→cloud steps (built 24 Sept, not yet seen on a device). Not yet
      exercised with a real account. The 30-day multi-cloud hard-gate trial is built too (unseen). All the optional clouds (Google Photos, Google Drive, Dropbox, pCloud, IDrive e2, Backblaze B2) are built on one provider layer, backup-only; the three OAuth ones are off until their app ids are filled in.
- [ ] Google Photos adapter (requires OAuth — Ian)
- [ ] Settings: sync frequency, account management
      **Audited 20 Sept 2026: account management partly there, sync frequency not.** Settings shows the
      signed-in account with a *Sign out* button and a *Delete Account Info* page (how to sign out, remove
      the app's access and clear its data). There is **no sync-frequency control**: the schedule is fixed
      (a content trigger on new media plus the 6-hourly safety net), with only the metered-network switch,
      the first-backup start time and the Automatic on/off switch to choose from. Nothing in v0.5 is
      built: `MediaSource.GOOGLE_PHOTOS` is an enum value and nothing more, and the app has no billing
      dependency and no `BillingRepository`.

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

**Corrected 27 Aug 2026 — Samsung does not delete the folder, and the cause is ours.** This entry
said "Samsung deletes a folder when its last file goes". Ian doubted it; the evidence was already in
hand. After the Archive tab removed all six files from `DCIM/12345clips` on 26 Aug, `ls -la` on that
folder returned `total 30952` and eight `.trashed-` entries. The directory was never deleted.

**The album disappears because our own scan cannot see the files.** `MediaScanner` queries the
MediaStore images and video collections, which exclude trashed items, and `scanAlbums()` groups
whatever comes back by album name. A folder whose files are all trashed yields no items, so no group,
so no album row. The list is built from MediaStore contents, not from directories.

The distinction changes what the hole is:

| | attributed to Samsung | actually ours |
|---|---|---|
| Where to fix it | work around a vendor | change how album rows are sourced |
| Which devices | Samsung | every device |
| When | when the folder is deleted | the instant the files are trashed |

It also means the hole appears even where the trash works perfectly — which is the case the app most
wants to be correct in.

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

### 28 Aug 2026 — the percentage, settled in four passes

Getting one number onto one label took four corrections, each exposing the next. Recorded as a
sequence because every wrong version was wrong for a reason that would recur.

1. **Per-run byte counter over a persisted baseline.** Two quantities that reset at different times.
   Produced the frozen 21% and the 48→15→48→18→22→67→2 lurching Ian caught on video.
2. **Ledger figure plus the file in flight.** Correct in principle — `(baseline − pending) +
   currentBytesSent`, both halves surviving a restart, no double counting because an uploading file
   is still `PENDING`. But the halves update at different moments.
3. **The stale baseline.** `openRunBaseline` only ever *raised* the denominator or cleared it at
   zero, and it ran at the start of a run. A drained queue left the old baseline behind, so a new
   62-file album opened at **79%** against an 884 MB denominator from an hour earlier.
   `(884 − 180) / 884 = 79.6%`, exactly what was on screen.
4. **The flash to zero.** `currentBytesSent` drops the instant a file completes; `pendingBytes` only
   falls when counts refresh. In that window the sum collapsed and the hero flashed a percentage and
   fell back to 0% on every file.

**What finally holds**, and it needed all three:

- counts refresh **per file completion**, not per run, so the two halves move together
- a **floor** the figure cannot fall below inside a run — a bar going backwards is worse than one
  briefly stale
- the baseline **closes wherever the app observes no outstanding work**, not only on a worker exit
  path. That mattered: the closing call was added to the worker and *still* left 172 MB stored after
  a drained run, which would have opened the next one part-finished all over again

**Confirmed by Ian on the Fold 4:** *"the % look good"*.

**The lesson worth keeping** is about denominators. Every failure here was the same shape — a
numerator and a denominator that were measured over different spans, or updated at different
moments. Whenever this app shows a proportion, the two halves must come from the same place and
change at the same time.

### 28 Aug 2026 — icons that were not buttons

The compact layout dropped Pause and Stop to bare `IconButton`s, which draw no container. Folded, they
floated beside the text with nothing marking them as controls — Ian: *"they were not buttons"*. Now
`OutlinedIconButton` carrying the same border as [HeroOutlinedButton], derived from
`LocalContentColor` so it follows the hero in either theme.

`SignalIcons.Resume` was also still wired to `SignalIcons.Albums` — a placeholder that rendered
Resume as a **folder** on the cover screen. Now a transport triangle, drawn as a closed stroked path
so it carries the weight of the Pause bars beside it.

**Unverified:** both were installed after the run drained, and these controls only exist while
something is uploading.

### 28 Aug 2026 — two numbers wearing one label, caught on video

Ian screen-recorded the hero through two pause/resume cycles. The first recording sat frozen at
**21% for 39 seconds** — 156 frames, three pixel-changes, all of them the word and the icons. The
second jumped: **48 → 15 → 48 → 18 → 22 → 67 → 2 → 7 → 10 → 75 → 4 → 5**.

Two symptoms, one cause. The label was being fed by **two different quantities**, and which one won
depended on whichever collector spoke last:

| Series | Values seen | What it was |
|---|---|---|
| High | 48, 67, 75 | `(baseline − pending)` from the ledger — correct, but only moving when a file completed |
| Low | 2, 4, 15, 18 | a per-run byte counter — reset to zero every time the worker chain restarted |

The frozen recording is the same bug from the other side: a **finished** `WorkInfo` carries empty
progress data, so reading it with a default of zero wrote zero over the live figure, and the two
collectors took turns clearing each other. The pause values were the honest ones, which is why
pausing appeared to fix the number — it only stopped the low series overwriting it.

**The error was conceptual, not wiring.** A per-run total and a persisted baseline measure different
things: the counter resets when the process does, the baseline does not. Dividing one by the other
was never going to hold.

Now a single quantity: `(baseline − pending) + bytes of the file in flight`. The finished part comes
from the ledger and survives a restart; the in-flight part comes from the worker and is the only
piece the ledger cannot see. They cannot double-count, because a file being uploaded is still
`PENDING` and therefore still inside `pendingBytes`.

**Unverified.** The run finished before the fixed build was installed — by five minutes — so the
corrected behaviour has not been observed. It will prove itself on the next run either phone does.

**Method note.** A screen recording turned out to be a far better instrument than repeated
screenshots: 146 frames diffed against each other located every change to the label in seconds and
showed the interleaving that single captures had made look like a freeze.

### 28 Aug 2026 — a ledger gap that was real, and a diagnosis that was not

Two batches of 25 files ran against an album holding 11, which looked like the engine queueing rows
for files that no longer exist. It was not. `uploadedAtEpochMillis` settled it: **46 files from
`Political humour`**, an album set to Backup earlier with a 55-file backlog. 46 uploaded plus 9
pending is exactly 55. The queue is global and ledger-ordered, not scoped to whichever album was
changed most recently — which is why the album being watched never moved.

**The 14 suspicious rows were not ghosts either.** `camera roll` had 14 `PENDING` rows against an
empty `DCIM/camera roll`, and the files turned out to be alive in `Pictures/camera roll` — outside
the granted tree, so invisible to the scan but present on the device. The reconciliation checked the
whole device, found them, and correctly did nothing. See TASK-014: album identity is the bucket name,
so two directories in different trees are one album.

**One real gap did come out of it.** `markWhatIsNoLongerOnTheDevice` reads `uploadedKeys()`, so it
only ever examines uploaded rows — correct for its purpose, since the missing flag drives the
cloud-deletion question and that only exists for a file with a cloud copy. The consequence is that a
**pending** row whose file has genuinely left the device is reconciled by nothing, and becomes queued
work the moment its album is given a mode: an upload the engine attempts on a file it cannot open.

Now handled by `forgetPendingFilesThatAreGone`, deleting rather than flagging — nothing was sent, so
nothing in OneDrive depends on the row, and a returning file is re-seeded by the scan. Guarded by the
same conditions as the marking it sits beside, so a revoked permission cannot read as a mass
deletion.

**Worth recording that the fix was built on a wrong diagnosis and is still correct.** The case it
guards is real; it simply was not the case in front of us.

### 28 Aug 2026 — three filters, four numbers, none of them wrong

Ian counted 45 files in Samsung Gallery while the hero claimed "52 Images · 34 Videos" and the phone
held 150. Chasing it turned up three independent filters, each correct, stacked:

| Layer | Sees | Why |
|---|---|---|
| Disk, `DCIM/` | 150 | everything |
| MediaStore | 99 | **`Anne`'s 51 trashed files are excluded** — the archive run of 27 Aug, invisible until the bin is emptied |
| `MediaScanner` | 86 | **`Restored` is filtered out** — those files came from OneDrive, and counting them would upload a second copy |
| Samsung Gallery | 45 | **nested folders are not shown** |

**The Gallery gap was a nested folder.** `DCIM/Test/Treasure Island/` — MediaStore buckets by the
immediate parent, so it indexed those files under `Treasure Island 4th of July`, and Samsung Gallery
never displayed them. Worth keeping, because it has an edge on Archive: **the scan can offer an album
the user cannot see in their gallery**, and the consent model assumes they recognise the name on the
card.

Moving the folder up to `DCIM/` fixed Gallery's view and completed MediaStore's indexing — it had
only 4 of the 11 while nested.

**A rescan that changed nothing was also correct.** Moving a folder within `DCIM` leaves the same
files in the same granted tree, so the total should not move. Confirmed by then moving 20 files in
from `Pictures`: 86 → 106, 5 → 6 albums, exactly.

**The line was removed rather than explained.** Ian, 28 Aug: *"each folder has a count and that
should be enough"*. A figure needing three filters explained before it can be read is not a summary,
and the album rows already carry per-album counts beside the album they describe. The hairline added
on 27 Aug went with it — it existed to separate the mode split from those counts.

**Also confirmed here:** files under `Pictures/` stay invisible to the app while only `DCIM` is
granted — 2,001 of them indexed by MediaStore and correctly ignored by the scan. The grant scoping
holds at real scale.

### 28 Aug 2026 — a crash on every launch, found by accident

**Fold 4.** `BoxWithConstraints` was added inside the Albums hero to measure the row for the compact
icon fallback. `HeroCard` applies `Modifier.height(IntrinsicSize.Min)` when its actions sit at the
bottom, so it asks its children for intrinsic measurements — and a `SubcomposeLayout` cannot answer
one:

```
java.lang.IllegalStateException: Asking for intrinsic measurements of SubcomposeLayout
layouts is not supported.
```

Fatal on the first draw, so the app died on **every** launch of the Albums tab. Four crashes in the
buffer inside two minutes. Both phones had the build installed.

**It was found because Ian mentioned the crash dialog in passing**, while asking about something
else entirely — not by testing, and not by me. Nothing in the build, install or screenshot loop
noticed that the app was dead, because a screenshot of a crashed app looks like a screenshot of a
launcher, and the install had reported success.

**The lesson is about the loop, not the API.** Installing and screenshotting proves a package landed,
not that it runs. A launch check — `logcat -b crash` after starting the activity — costs one command
and would have caught this the moment it was introduced.

The fix is `LocalConfiguration.current.screenWidthDp`, which needs no subcomposition and is the right
measure anyway: the row spans the hero card, which spans the window.

### 28 Aug 2026 — what a pause should cost, settled in three reversals

The question looked trivial and took four rounds, each overturning the last. Worth recording as a
sequence, because each step was wrong for a reason worth keeping.

1. **"Finish the current file, then pause."** Rejected. Built on the premise that interrupting an
   upload re-sends it — which is **false**. `resumeOffsetOf` asks Graph what it has already
   accepted. Verified three times on the Moto G: `at 10485760 of 127247142`, `at 20971520 of
   117668262`, and once at 57%. Every offset an exact multiple of the 5 MB chunk size, so at most
   one chunk is ever re-sent.
2. **"Then suspend indefinitely, it is free."** Rejected by Ian: suspend was not what he asked for.
3. **"Always roll back."** Built, then superseded. The tidiness argument for it evaporated on
   inspection — a suspended upload leaves **nothing** in OneDrive, since Graph does not create the
   DriveItem until the final chunk. Confirmed against the live drive: six files present, six
   `UPLOADED` rows, and a seventh sitting at 57% entirely invisible. What remained was one real
   argument, below.
4. **Ian's compromise: hold the session for ten minutes, then discard.** Short pauses free, long
   pauses clean.

**The one argument that survived all four.** Resuming reads the *current* local file at a stored
offset, so a file rewritten while a session was held would be spliced from two versions into
something Graph accepts and marks complete. The ledger key carries size and modification time, so a
changed file normally lands on a different row — but "normally" is not this app's standard
elsewhere.

**And the mechanism that closes it is not the timer.** The size check at the call site is:
`existingSession` is withheld unless the file's current size still matches the row it was opened for.
Ten minutes only bounds the window. Recorded explicitly because it would be easy to remember this as
"the timer made it safe", and then to remove the size check as redundant.

**Ten minutes was chosen to sit inside Graph's own window**, measured at `expires 11:34:26, 5.7
minutes left` on a live suspended file — so a session is never held past the point it would work.

**No timer was built.** The staleness test runs when the next run starts, not on a schedule: a job
that must survive process death, reboot and Doze to fire correctly is a great deal of machinery for a
question answerable at the moment it matters. A crash leaves the interruption stamp unset rather than
stale, which fails in the safe direction — unset reads as old, and old discards.

### 28 Aug 2026 — pause, resume, and four bugs that only a thumb could find

**Moto G.** TASK-019 built and verified. The Albums hero now reports rather than acts: the left slot
shows overall progress, and Pause, Resume and Stop sit beside it. All three fit at 443 dp.

**The claim the design rests on is proven for the pause path.** Pausing mid-clip and resuming logged:

```
resuming REC_1661620432421.mp4 at 10485760 of 127247142 bytes (8% already accepted)
```

Byte 10,485,760 of 127,247,142, not zero. This had been shown for a killed process on 26 Aug; it is
now shown for a deliberate pause, which is the path users take. That is what makes an instant pause
free, and it is why Ian's question — *"do we want to complete the file or rollback?"* — overturned
the first draft's "finish the current file" design. The premise behind that draft, that interrupting
re-sends the file, was simply false.

**Four defects, every one found by pressing the screen rather than by reading the code, and every one
of them would have shipped.**

| Defect | Cause |
|---|---|
| "Paused at 95%" beside a **Pause** button | `isRunning` tested before `isPaused`; both are true while the cancellation lands |
| Pause waited for the current file | only `MANUAL_WORK` was cancelled, and the run was automatic — so the pause took hold only when the worker next declined |
| "Syncing 0%" on a cold start | the percentage ignored `hasLoadedCounts`, rendering zero as fact. The comment warning about exactly this trap was already in the file |
| The wizard flashing over a set-up install | **fixed three times before it was right** |

That last one is the one worth remembering. The decision between wizard and app depends on three
independent async sources — stored preferences, the upgrade backfill, and the granted-directory list
— and **each defaults to the value meaning "show the wizard"**. Each was gated in turn, and each fix
looked complete until the next input was observed doing the same thing. They are now behind one
`setupDecisionReady`, so a fourth input cannot reintroduce it quietly.

**Also fixed, and the reason the feature exists:** an automatic run is now reported as running.
`isRunning` was set only from the manual chain, so during the 21 GB upload the button read "Sync now"
throughout and nothing could touch it. Four flows were racing to write one boolean; they now report
into a set.

**Method note.** Driving the app over `adb input tap` cost far more turns than it saved: taps landed
on the wrong row while a fling settled, one dismissed a menu, one exited the app. Screenshots must be
compared for equality before trusting a coordinate read from them. Handing the taps to Ian was faster
than automating them, and found more.

### 28 Aug 2026 — the engine at twenty-two times its previous scale

**Moto G 2026 (Android 16), a second test device.** 3,326 files and 21.02 GB pushed into four purpose-built
albums, then uploaded to a dedicated `MotoG/Gallery` root.

| | |
|---|---|
| Uploaded | **3,326 of 3,326** |
| Verified — `remoteSizeBytes = sizeBytes` | **3,326** |
| Errors, retries, skips | **0** |
| Largest single file | 1.44 GB video, first attempt, no resume needed |

The previous largest library this engine had handled was 149 ledger rows. The scanner met 3,327 rows without
a pagination defect — the specific failure class that bit on 19 Aug — and the byte-capped batching chained
across roughly forty runs without intervention.

**Throughput is file-size bound, not bandwidth bound.** Measured across the run: 0.73 MB/s on 0.24 MB files,
rising to 3.32 MB/s once multi-hundred-megabyte video dominated. Real upstream is about 22 Mbps; everything
below that was per-request overhead. `ChunkedUploader` already sends anything under 4 MB as a single PUT
rather than opening a session, so the obvious optimisation was checked and found already present. What
remains is one round trip plus a verification per file, which simply dominates at a quarter of a megabyte.

**The rig is left armed.** `isProxied` is 0 across all 3,326 rows because auto-optimise was deliberately left
off, so the ~8.9 GB proxy lever is unspent on a phone held at 18 GB free by a 58 GB ballast file against a
20 GB default floor. The first thing TASK-011 ever does will be a real decision against a real deficit.

### 28 Aug 2026 — the guided first run, and a migration that undid itself

**TASK-014 built.** Eighteen panels: two gates, a scan report, ten explanations and seven questions. Three
panels advance only on a named acknowledgement — *"I understand — Archive takes these files off my phone"*
— recorded per topic in `BackupSettings.acknowledgedTopics`. The topic strings are the single source the
Help screen (TASK-017) will read, so the wizard and Help cannot drift.

> **Withdrawn 7 Sept 2026.** None of that survives. `SetupTour` replaced the eighteen-panel wizard on
> 31 Aug (`e6a0794`) with hardcoded bubble text and never carried the acknowledgement across, so nothing
> has written an acknowledgement since. TASK-017's Help screen was superseded by the approved (?) tooltip
> decision, and the just-in-time prompt was premised on a Skip button that no longer exists. `SetupTopic`,
> `acknowledgedTopics` and `KEY_ACKNOWLEDGED_TOPICS` were deleted in TASK-022 — see the entry at the end
> of this file. Do not rebuild the acknowledgement gate: the Archive confirmation in `BackupScreen` is
> what CLAUDE.md requires, and it is independent of all of this.

**Verified on the Fold 4 in both themes.** Dark mode is correct — dark container, light body text, no
hardcoded colours. The migration was checked first: an install with granted folders lands on its tabs with
every album and mode intact, not in the wizard.

**Two defects found by looking rather than by reasoning.**

*The backfill undid an explicit request.* It marked setup complete whenever the stored flag was false and a
source grant existed — but "Run setup again" stores exactly that. Pressing it, closing the app and reopening
returned the user to the tabs. DataStore distinguishes an absent key from a stored `false`, and the fix is to
key the backfill on whether a decision was ever *written*. It was racy, which is why the first test passed
and the second did not.

*The layout ignored the large screen.* One card at the top of the Fold's inner display above roughly sixty
percent emptiness, with a paragraph set to a 140-character measure. Content is now centred and capped at
600dp. This is the `targetSdk` 37 adaptation requirement arriving in practice rather than in principle.

**Two of two fresh installs have now hit the Gate 1 wall** — Fold 4 on 26 Aug, Moto G on 28 Aug, different
vendors and different Android versions, both landing on "0 Albums · 0 B" beside a Rescan that cannot
succeed. That is not an edge case; it is what every new user meets, and it is what the wizard exists to
prevent. The milestone box stays unticked because TASK-014's scope-narrowing acceptance lines — removing a
directory hides albums without forgetting them, re-adding restores modes and re-uploads nothing — are still
unverified.

**Found while testing, unfixed:** an automatic backup run cannot be stopped from the UI. `isRunning` is set
only from the manual work chain, so during the Moto's upload the button read "Sync now" throughout, and
`canRunBackup` would have let a second, manual run be queued alongside. Automatic sync is on by default, so
the runs a user most wants to interrupt are the ones with no control attached. This is the gap Ian's
Pause/Resume proposal closes.

### 27 Aug 2026 — the trash request confirmed at scale, and the bytes that do not come back

**Fold 4.** `Anne` switched from Sync to Archive — 51 photos, all verified in OneDrive. After the
validation pass and Android's own dialog, every one of the 51 was renamed in place:

```
/storage/emulated/0/DCIM/Anne/.trashed-1790483890-123_1(1).jpg
/storage/emulated/0/DCIM/Anne/.trashed-1790483890-5189.jpeg
… 51 files, none missing
```

Expiry `1790483890` decodes to **27 Sept 2026 — a 30-day window**, matching the single-file result of
25 Aug. Two runs, two days apart, one file and fifty-one: this is the device's behaviour, not a fluke.

**What it settles.** CLAUDE.md's "the files were removed outright and were not in Samsung Gallery's
Recycle Bin" is withdrawn. It appeared only there, never here, and was a conflation with
`DocumentsContract.deleteDocument`. The rule has been rewritten, and "never tell the user a local
removal is recoverable" went with it. Ian had already reported the Recycle Bin result on 25 Aug; the
correction landed in this file and not in the rules file, which is exactly how a withdrawn claim
keeps coming back. Hence the MILESTONES step now in the backup procedure.

**What it does not settle — the space.** `du` reported **18.6 MB in `DCIM/Anne` before the archive and
18.6 MB after**. A trashed file keeps its bytes for the full 30 days. Archive frees nothing at the
moment it runs, and the prompt's "Archiving them will free up XXX GB on your phone" is describing
what happens once the user empties the Recycle Bin. TASK-016 lists that self-checking claim as an
acceptance criterion; it currently passes arithmetic and fails the filesystem.

**Consequence for the storage budget.** Photo proxying is not merely the weakest lever, it is the only
one that frees space *immediately*. Archive's contribution arrives up to 30 days later, on a user
action the app is forbidden to take for them.

**Method note, and a wasted hour.** Three `content query` reads of the same MediaStore table disagreed
within one session — 67 rows, then 8,447 rows and 147.64 GB, then 67 again — and the middle reading was
reported to Ian as his library being at risk. Two handsets were connected at different moments (a
Fold 4 being returned and a new Fold 8), and adb was never pinned with `-s <serial>`. `ls` and `du`
were stable throughout. **Pin the serial on every call, and prefer the filesystem to the provider
when the two disagree.** This is the WAL lesson of the same date in a different costume: a confident
diagnosis built on an instrument nobody had checked.

### 28 Aug 2026 — the exit warning, and a snooze that did not survive being left

**Fold 4, cover screen (344dp), both themes.** Ian, 28 Aug: *"We can warn the user when they go to close
the app if there are files still in Archive that haven't been attended to."* Built as a third surface for
the Archive summons, in place of a notification.

**Why a dialog rather than a notification.** The notification half of TASK-011 was designed when the
notification was the *consent mechanism* — a background worker cannot obtain write consent, so it was the
only way to ask for the next batch. The SAF finding of 19 Aug removed that job. What was left was telling
someone their storage is low, which Android already does. Ian, 28 Aug: *"no need to duplicate their
systems."* A dialog needs no permission, cannot be denied, and cannot be silently switched off, which is
exactly the failure `POST_NOTIFICATIONS` carries.

**It is a net, not a guarantee, and this is the part not to forget.** Android has no general "app is
closing" event. Only the back gesture from the root can be intercepted; Home and a swipe from Recents
cannot, and on gesture navigation Home is the common way out. The Albums tab summons remains the surface
that is always there — nothing may become reachable only from the dialog.

**Measured, with `Header` (62 files, 190 MB) switched to Archive for the test and switched back after:**

```
redundantLocalCopies: 62 files in Archive albums are safely in OneDrive
[back gesture] -> "Files ready to Archive - 62 files are verified in OneDrive
                  and ready to leave this phone."   Leave | Archive now
```

Archive now landed on the Archive tab; Leave closed the app to the launcher. Both themes correct, no
hardcoded colours, no crash. **Nothing was archived** — validation was run (62 confirmed, 0 unchecked) and
the Delay branch taken rather than Yes.

**A defect found by building it.** The Archive snooze lived in `ArchiveViewModel` as in-memory state, so
it died the moment the app closed — which is precisely when this dialog fires. Someone who chose Delay and
then left would have been warned anyway, by the very act the snooze was meant to cover. Now persisted as
`archive_delayed_until`, confirmed in the DataStore file and confirmed surviving a full close and relaunch:
back went straight to the launcher with the hour still running.

**Buttons name their actions.** Against a sentence about leaving, "OK" reads as both "yes, close it" and
"yes, take me there". The 18 Aug naming rule, pointed at buttons.

**The Archive consent copy, rewritten the same day.** Ian, having opened the old dialog on the Moto and
declined it, replaced the body with three plain steps:

> Archive will verify all files are uploaded to the cloud.
> Archived files will be moved to your phone's Recycle Bin.
> Please empty your Recycle Bin to free up storage.

The third line is the one the app could never say before. A trashed file keeps its bytes for 30 days, so
every earlier "frees up X" described a moment that had not arrived; this asks the user to do the thing that
actually returns the space, which is also the only version CLAUDE.md permits — the app must never empty a
trash itself. Verified on the Moto G and the Fold 4, both themes, cancelled rather than accepted so no
album changed.

Two clauses went with it, on Ian's instruction. The recoverability caveat, because the 27 Aug entry above
supersedes it. And the standing-instruction clause — *files added to this album later are covered by the
same choice* — on the grounds that an emptied album stops being visible.

**That second removal was raised as a concern and then settled.** The objection was that CLAUDE.md
required the wording, and that the stated reason is narrower than the clause: the 27 Aug correction in this
file says the folder is *never deleted* — the album row vanishes only because `MediaScanner` cannot see
trashed files — so a camera, a download or a file manager can refill it and the mode set earlier still
applies.

Ian's answer, 28 Aug 2026: **change the rule.** CLAUDE.md now requires the dialog to say what Archive
*does* rather than to enumerate its consequences, on the judgement that the mode's name and the album row
showing it carry the standing-instruction property well enough.

**The property itself was not weakened, and CLAUDE.md now says so explicitly.** A file added to an Archive
album later is still removed with nobody asked again. So the album's membership is not a free variable:
anything that lets files enter an Archive album by a new route widens what gets removed under a choice made
earlier, and counts as touching the deletion rule. That is the part to keep hold of — it was previously
carried, weakly, by a sentence in a dialog.

**Still unfixed:** `backup_move_trash_note` on the validated-files prompt still says the local copy *"is
removed straight away on some phones, so treat this as permanent"*. It now contradicts the dialog above as
well as the 27 Aug measurement.

### 28 Aug 2026 — the Archive tab never reloaded, and the test that could not have caught it

**Moto G.** Ian set an album to Archive and the Archive tab said *"No album is set to Archive. Nothing here
will remove anything from your phone."* The engine disagreed in the same minute:

```
17:03:20  filesInArchiveAlbums: no album is set to Archive
17:03:30  redundantLocalCopies: 8 files in Archive albums are safely in OneDrive
17:04     [screen] Files to Archive - 0 - "No album is set to Archive"
```

**`load()` ran once per app session.** It was called only from `ArchiveViewModel.init`, and the ViewModel is
scoped to the Activity, so it ran at whatever moment the tab was first shown and never again. Open the tab
before setting any album to Archive and the empty list built then was permanent: setting a mode afterwards
took the user straight to a screen still describing the state from before. The screen's only
`LaunchedEffect` keys on `phase` and `batchIndex` and drives the removal batch loop, not loading.

`load()`'s own doc comment said *"Cheap, and safe to call whenever the screen appears"* — describing a
contract nothing in the UI honoured.

**Why the hardware pass that shipped the summons did not catch it.** That test only ever exercised the
other ordering: set the mode, accept, get carried to the tab by `onAlbumArchived()` with the ViewModel not
yet built, so `init` ran with the album already in place. In that ordering the screen is correct, and 62
files listed. Two orderings, one of them right by accident, and the wrong one is the one a user reaches by
visiting the tab first to see what it does. **Verifying the path the feature creates is not the same as
verifying the paths a user takes into it.**

**Fixed** with `LaunchedEffect(Unit)` reloading on entry, guarded to `IDLE` — a reload from `VALIDATING` or
`REMOVING` would cut across a run, from `READY` it would discard the validation the user is being asked
about, and from `DONE` it would wipe the report of what was just removed. Verified on the Moto: three
`filesInArchiveAlbums` calls across one session of tab entries where there was previously one, and
`PauseTest` with its eight files now listed.

### 28 Aug 2026 — a file that is in OneDrive at zero bytes, and a ledger that says otherwise

**Moto G, album `PauseTest`.** Ian: *"It indicated 1 file not on OneDrive — staying on your phone. But
the fact that it isn't uploaded yet should not stop it from being Archived."* Correct as a principle, and
already the design — validation treats "not in OneDrive" as work, backs the file up, and only then judges
it. Two things were wrong underneath it, and the second is the serious one.

**1. The back-up-and-recheck step could not do its job.** `nextPending` selects `state != UPLOADED`, and
a file reaches the missing category precisely because its row already says `UPLOADED`. So the run enqueued
to fix the problem had nothing to select:

```
17:08:48  validate: 1 files are not in OneDrive — backing them up
17:08:48  backup run starting (manual)
17:08:49  backup run finished: 0 uploaded, 0 already there, 0 failed, 0 remaining
17:08:51  confirmStillInCloud: 0 confirmed, 1 no longer in OneDrive
```

600 ms, nothing selected, same answer. `BackupEngine.requeueMissingFromCloud` and
`BackupEntryDao.requeueForUpload` were written to close this — return the row to pending and clear the
remote columns it has just been shown to be wrong about — and with them the run uploaded for real
(`1 uploaded`, 24 s). **They are deliberately not wired in**, for the reason below.

**2. The file is on the drive, at zero bytes, and the ledger records a matching size.** Found by adding
the failing name to the log rather than by reasoning:

```
confirmStillInCloud: '20251220_120042.mp4' (117668262 B) not matched in PauseTest
  — listing held 10 names, same name present: true, its size there: 0
```

The ledger row for it reads `state=UPLOADED, sizeBytes=117668262, remoteSizeBytes=117668262` with a real
`remoteItemId`. The drive says 0. **So `remoteSizeBytes` is not always what Graph reported** — and that
column is half of `verifiedInCloud()`, the gate every removal in this app passes through. A file in this
state would pass the check that is supposed to make removal safe. What caught it was the Archive tab
asking the drive live; the ledger alone would have said yes.

Re-uploading did not clear it: the name still resolved to 0 afterwards. So requeueing against a bad remote
item buys traffic and no correctness, which is why it is left out until the questions below are answered.

**Correction, same evening.** The first hypothesis was that Graph had *omitted* the size and the
mapper's `size ?: 0L` had rendered that absence as a confident zero. Wrong. The mapper was changed to
carry null through and the diagnostic to print `not reported` for it, and on the next run it printed
**`its size there: 0`** — a reported zero. The file genuinely is a zero-byte item in OneDrive, and
Archive refusing it is correct behaviour, not a misreading. Ian, before the test ran: *"it is a good
test of the Archive flagging a failed file."* It was.

**The nullable-size change was kept**, because the latent bug it removes is real even though it is not
this one: `size ?: 0L` still made "Graph did not say" indistinguishable from "the file is empty", and
`confirmStillInCloud` would have called that gone. Absence now routes to *could not check*, the
skip-existing path defers rather than risking a duplicate, and a test that asserted the old coercion —
`a file with no size defaults to zero bytes` — was asserting the defect and has been replaced by two
that separate unknown from genuinely empty.

**What is actually wrong, still open.** A 117 MB upload reported success and left a zero-byte item, and
re-uploads do not replace it — the folder listing went 10 names, then 11, against 8 local files, so each
attempt files a renamed sibling beside the bad item while the original name still resolves to zero. The
name is occupied by something empty and nothing reclaims it.

**The requeue is proven, 28 Aug 2026, on the case Ian built by hand.** He deleted `PauseTest` from
OneDrive, set the album to Archive, and pressed Check these files. The ledger still held eight
`UPLOADED` rows with item ids pointing at deleted objects, so `nextPending` could not see a single one
of them — the exact condition that made the earlier run finish in 600 ms having uploaded nothing.

```
listed 'MotoG/Gallery/PauseTest': 8 files      <- was 0 before the run
confirmStillInCloud: 8 confirmed, 0 no longer in OneDrive, 0 could not be checked
validate: 8 confirmed, 0 could not be archived
```

1.07 GB of video re-uploaded and every file verified. Ian: *"all 8 files validated."* This is the
behaviour he asked for at the outset — *"if a file isn't on OneDrive then it should be uploaded there
as part of the Archiving process"* — working for the first time.

**Open, and worth answering before anything else in Archive:**
- How does an item reach OneDrive at zero bytes while the row records a matching size? The album is
  `PauseTest`, used for the 28 Aug pause/resume work, so an interrupted resumable session is the first
  place to look. Confirmed a reported zero, not a missing field.
- Why does a re-upload file a renamed sibling instead of replacing a wrong-sized item of the same name?
  `conflictBehavior` is the thing to check. As it stands a bad remote item is permanent and every retry
  adds another file.
- Should `markUploaded` record the size Graph returns for the item rather than the local size?
- What should an upload do when it finds an item of the wrong size already at the destination?
- Is `verifiedInCloud()` safe on its own, given it trusts a column this can falsify?

**Method note.** "1 no longer in OneDrive" was undiagnosable — it cannot separate absent from
present-but-wrong, and those want opposite fixes. One log line naming the file, the size, whether the name
was in the listing and what size it had there turned an hour of hypotheses into one reading. That line is
kept.

**Also corrected here:** a first reading of the ledger appeared to show two `UPLOADED` rows for this one
file and was reported as duplication caused by the requeue. Wrong — the rows are `BudgetVideo` and
`PauseTest`, two albums holding the same video, different `mediaStoreId`s, both legitimate. The query was
not scoped to the album. Zero duplicate name+album rows across all 3,335.

### 28 Aug 2026 — the Albums tab stops claiming what it never checked

Ian, after deleting an album's OneDrive folder by hand and watching the row carry on regardless:
*"if the Album tab never syncs with Cloud then it should not proclaim X files backed up."*

**He was right, and the evidence was unambiguous.** Ledger: eight `PauseTest` rows, all `UPLOADED`,
all with real OneDrive item ids. Drive: `listed 'MotoG/Gallery/PauseTest': 0 files` and
`listed 'Samsung Gallery/DCIM/PauseTest': 0 files`. The row's "8 backed up" came from
`SUM(CASE WHEN state = UPLOADED)` over local rows — a record of what this phone once sent, worded in
the present tense about a drive nobody had asked.

**The honest number already existed and was being thrown away.** `ReconcileWithCloud` walks every
album against OneDrive and calls `ReconciliationRules.tallyAlbum` per album — then added each result
to a running total for the setup wizard and dropped the per-album detail. So the one part of the app
that knew what the drive holds told the wizard and nothing else.

**What changed.** A new `album_cloud_status` table (schema 9, additive, migration verified on the
Moto's real 3,335-row database) keeps each album's answer: when it was checked, how many the drive
verified, how many it did not hold, and whether the listing failed at all. Deliberately **not** stored
on `album_preferences`, whose own documentation calls it the one table that cannot be rebuilt — a
disposable cache does not belong in the table holding pure user intent.

`AlbumCloudClaim` turns a stored row into what may be said, and `NeverChecked` is a first-class state
rather than a zero. It cannot see the ledger at all, which is what makes the old claim impossible to
reintroduce by accident. Six unit tests, including the case Ian created by hand.

**The rows now carry two lines that describe different things**, which was the other half of the
problem. `"%1$d backed up"` became **"N uploaded from this phone"** — true, and about the phone — and
the green tint moved off it onto the line that actually asks the drive: **"N verified in OneDrive"**,
**"N of M verified in OneDrive"**, **"Could not reach OneDrive when this was last checked"**, or
**"Not checked against OneDrive yet"**. An unchecked album is never tinted as good news, because a
reassuring colour on an unverified claim is the same lie in a different medium.

**Seen on the Moto before any rescan:** every row reading "Not checked against OneDrive yet" under a
plain-coloured upload count. Which is the correct thing for the app to say about a question it has
not asked.

**Then the upload count went too.** Ian, on seeing the two lines together: *"get rid of the XXX
uploaded from this phone line — it can get confusing as files are moved, added, deleted."* He is
describing a real drift, not a preference. The ledger counts rows this phone once sent, keyed on
content; the file count beside it comes from a live device scan. Move a file between albums, delete
one, or add one the cloud already has, and the two numbers move independently — leaving a pair nobody
can reconcile by looking. What remains describes the phone in the present tense and cannot drift:
optimised, and pending. `album_status_backed_up` and `album_status_none` went with it rather than
being left as callerless strings.

**A defect found by looking, immediately after building it.** The first run showed `BudgetMixed`
verified while the five albums checked seconds later still read "not checked". The reconciliation
writes a row per album as it walks, and it runs at launch alongside the Albums tab building its list,
so a one-shot read caught whichever albums happened to finish first. The rows observe the table now
and fill in as the answers land.

**Rescan, then the tab itself.** The button had never triggered the reconciliation at all — it
refreshed the file counts and left the "verified in OneDrive" lines beside them untouched, which is
the one thing somebody pressing it after moving files is trying to find out. Wired, then widened on
Ian's call: *"a move to the Albums tab is ok to trigger a refresh — just so we know the user is
getting fresh data."* The cost was weighed and lost — about 55 seconds for 3,335 files across six
albums, one listing per album plus one per page — on the grounds that a screen whose job is telling
somebody their photos are safe should not be showing an answer from an hour ago.

Two guards came with it. An in-flight check, because entry-triggered plus button-triggered would
otherwise stack full drive walks on top of each other from a few tab switches; and the button says
**"Checking OneDrive…"** and disables while it runs, since a control that looks idle for a minute
invites a second press. `refresh()` is deliberately left alone — several callers want only the device
counts, and a rebuild after a mode change has no business walking OneDrive.

### 28 Aug 2026 — the trash request is the platform's, not Samsung's

**Moto G 2026, stock Android 16, Google Photos — the first trash request ever run on a non-Samsung
handset.** `PauseTest`, eight videos, 1.07 GB, switched to Archive and taken all the way through.

```
archive: 8 files removed from this phone
-rw-rw---- 163707204  .trashed-1790554145-20241020_124036.mp4
-rw-rw---- 149944718  .trashed-1790554145-20250606_221541.mp4
… all eight, renamed in place, byte sizes unchanged
du -sh  ->  1.0G
```

Expiry `1790554145` decodes to **28 Sept 2026 — 31 days**, against the Fold 4's `1790483890` at
27 Sept. MediaStore no longer lists them: a `content query` for `bucket_display_name='PauseTest'`
returns nothing, which is the owner-scoping noted on 27 Aug and also why our own scan stops seeing the
album.

**What this settles.** CLAUDE.md carried the caveat *"a different handset or One UI version may still
behave differently"* — reasonable while the only evidence came from one Samsung device. Two vendors,
two Android skins, identical behaviour: rename in place, bytes retained, ~30-day expiry. **The trash
request is the platform's behaviour and not Samsung's.** It is still not a promise the UI should make
unconditionally, because the population is two devices, but the shape of the answer is no longer in
doubt.

**It also confirms the consent copy Ian wrote the same evening.** *"Archived files will be moved to
your phone's Recycle Bin. Please empty your Recycle Bin to free up storage."* The second sentence is
the one this measurement earns: 1.0 GB is still sitting in `DCIM/PauseTest` after the removal, and it
comes back when the bin is emptied or the 31 days run out, never on the tap.

**Confirmed by eye, minutes later.** Ian: *"checked Files — all 8 are in the Trash."* So on stock
Android the trashed files are visible and recoverable through the **Files** app's Trash, not through
Google Photos. That completes the chain on a second vendor: renamed on disk, bytes retained, listed
in a user-facing trash, recoverable for 31 days.

**One consequence for the copy.** The Archive confirmation says *"moved to your phone's Recycle
Bin"* — which is Samsung's name for it. On this handset the place the user actually finds them is
the Files app's **Trash**. Same mechanism, different label per vendor, and the sentence currently
names one vendor's. Worth a vendor-neutral wording; flagged, not changed, because the copy is Ian's.

### 28 Aug 2026 — Restore asks about folders, and the deletion guard is left alone

Ian, after an archive of eight files left the Restore tab offering none of them: *"Restore should only
offer files that are NOT on the phone."* Two decisions settled the shape.

**A proxied file does not count as on the phone.** The 2048px copy is here; the full-quality original
is not, so proxies stay listed. Restore keeps two populations and one verb.

**A copy in a different album does not count either.** This is the case that started it: eight videos
archived out of `PauseTest` while byte-identical copies sat in `BudgetVideo`. Ian's call is per folder
— a copy in an unrelated album is not an answer to "get that album back".

**The trap in that second decision, and why it did not get built the obvious way.** "Has this file
left the phone?" is recorded in `localMissingSinceEpochMillis`, set by a **content** test that ignores
folders — and that column is what `cloudDeletionCandidates` keys on. Making the marking stricter would
have been a two-line change and would have quietly widened what is eligible for **deletion from
OneDrive**: every file with a duplicate elsewhere would have become a deletion candidate after the
grace period.

So the column keeps the cautious, album-blind answer and Restore asks its own question, in
`RestoreScope` + `BackupEngine.filesNotOnThePhone`, computed from a live scan on entry to the tab. The
two readings now sit side by side with a comment each explaining why they differ. Being wrong in
Restore costs a redundant download; being wrong in the other costs a cloud copy.

**Scope stays "what this app uploaded"**, not everything in the drive — Ian's 27 Aug rule, and what
keeps the tab from becoming the cloud file browser the design principle rules out.

**No pulling files back out of the trash.** Considered and rejected by Ian: *"always pull from the
cloud despite the cost."* The shortcut was real — an archived file inside its 30 days could be
untrashed with `createTrashRequest(..., false)` in a second instead of re-downloading a gigabyte — but
it depends on state the app does not control. The user can empty the bin at any moment, the window
expires, and `owner_package_name` on these rows is not ours (`com.android.shell` on the rig, the
camera app on a real phone), so the URIs would have to be remembered at removal time and might not
still resolve. One reliable path beats two, one of which sometimes works.

A consequence worth knowing: a file restored while its trashed original is still in the bin means the
user briefly holds both, and the trashed one keeps its bytes until the bin is emptied.

**Verified on the Moto, minutes after the commit that called it unverified.** The tab reads:

```
Folders to Restore: 1
PauseTest — 8 files · 1.1 GB
0 to restore · 8 to download
```

The eight archived videos are offered despite byte-identical copies sitting in `BudgetVideo`, which is
exactly the case the per-folder rule was built for. `0 to restore` is correct rather than empty: no
file on this device is proxied, so that half has nothing to show — the two populations reporting
separately is what makes the row readable.

Backed by five `RestoreScopeTest` cases, including the duplicate-in-another-album case and the
empty-scan guard, which returns nothing rather than offering the entire library. 289 tests green.

### 28 Aug 2026 — the app would upload nothing and call it a backup

Chasing the zero-byte item Ian found in `PauseTest`. The original artefact went with the folder he
deleted, so this is reasoned from the code rather than reproduced — but the path is real, it is short,
and it ends in data loss.

```kotlin
val bytes = ByteArray(total.toInt())
if (total > 0) source.open().use { it.readFully(0, bytes, total.toInt()) }
uploadApi.uploadSmallFile(remotePath, bytes.toRequestBody(OCTET_STREAM))
```

A source reading zero bytes took the small-file path — `0 < 4 MiB` — and **uploaded an empty body**.
The `if (total > 0)` skipped the read and sent the empty array anyway. Graph stores a zero-byte file
under the photo's name and returns it as a success.

**Then every check downstream agrees with it.** The response reports size 0; the local file reads 0;
`item.sizeBytes == entry.sizeBytes` passes; the row is marked `UPLOADED` with a real `remoteItemId`
and `remoteSizeBytes = 0`. `verifiedInCloud()` compares those same two numbers, finds them equal, and
**the photo becomes eligible for removal from the phone.** Every individual step is correct.

**And it cannot be undone by retrying.** `conflictBehavior` is `rename`, which is right — CLAUDE.md
forbids destroying a user's cloud file, and two phones easily produce the same camera filename. The
consequence is that the name stays occupied by the empty file for good, and each later attempt files
a sibling beside it. That is exactly the shape observed: a folder growing 8 names, then 10, then 11,
while the original name went on resolving to zero.

**A zero-length read is nearly always transient** — a file caught mid-write, mid-proxy, or just
trashed. So the fix is to refuse, not to fail: `UploadOutcome.EmptySource` and
`RemoteError.EmptyLocalFile`, deferred by the engine with no attempt spent and the row kept, tried
again next run. Deferring costs one run; uploading costs the name forever.

**Two smaller things fixed alongside.** `sizeBytes = item.size ?: 0L` in the upload response was the
same absence-rendered-as-zero coercion found in the listing mapper earlier the same day — now `-1`,
so an unreported size fails the equality test rather than accidentally passing it for an empty file.
And a test named *"an empty file still takes the single-request path and completes"* was asserting the
defect, exactly as `a file with no size defaults to zero bytes` had been that morning. **Two tests in
one day pinning behaviour that was wrong.** Worth noticing as a pattern: both were written to
describe what the code did rather than what it should do.

**Not proven on hardware**, and deliberately not manufactured: reproducing it means getting a real
file to read as zero at the moment of upload. The unit test asserts the thing that matters — nothing
reaches the network — and 290 tests pass.

### 28 Aug 2026 — the 8K transcode cost, measured at last

TASK-013 has been blocked since 19 Aug on one sentence in this file: *"Needs Media3 Transformer and a
transcode cost measured on real 8K footage before committing."* Ian shot a clip for it tonight —
`20260828_210759.mp4`, **7680×4320 HEVC, 31.1 s, 312.8 MB, 80 Mbps, HDR10+ with a PQ transfer**.

**Galaxy Z Fold 4, 8K HDR → 1080p SDR H.264:**

```
TRANSCODE OK: 312.8 MB in, 31.8 MB out (9.8x smaller),
15.63s for 31.1s of footage, ratio 0.50x realtime
```

**Twice as fast as playback, and 9.8× smaller** — within a whisker of the ~10× the photo proxies
achieve, which makes the two levers comparable for the first time. A minute of 8K costs about thirty
seconds of transcoding. The encoder ran in hardware at 1920×1080, 24 fps, 6.96 Mbps.

**On this evidence the feature is affordable.** That is the thing that was unknown.

**Three failures on the way, each worth keeping.**

*H.264 cannot carry HDR10.* The first run failed with `ERROR_CODE_VIDEO_FRAME_PROCESSING_FAILED`
against a target of H.264 while Transformer defaulted to `HDR_MODE_KEEP_HDR`. Samsung's 8K is PQ with
HDR10+ metadata, so that combination is a contradiction — reported as a generic frame-processing
error, with the cause three `Caused by` levels down. Fixed by tone-mapping to SDR, which is also the
right product choice: a downscaled clip is for watching, and SDR H.264 plays everywhere. **It is a
quality decision as well as a size one, and the UI should not pretend otherwise.**

*Media3 cannot be an androidTest-only dependency.* Two constraints meet and only a real dependency
satisfies both: Transformer needs an application context, which the instrumentation context does not
have; and it loads its GLSL shaders as assets from whichever context it is handed, so those assets
must sit in the same APK. Test-only placement fails one way or the other. Media3 is now an
`implementation` dependency, provisionally — the measurement that would have justified deferring it
is the one that needed it present.

*`adb pull` was never the problem.* Ian tried several times and failed each time. Git Bash's MSYS
layer rewrites any argument starting with `/`, so `/storage/emulated/0/...` silently became
`C:/Program Files/Git/storage/...` and adb reported a file that does not exist. `MSYS_NO_PATHCONV=1`,
or a doubled leading slash, or PowerShell. The file then pulled in 7.8 s at 38 MB/s. **Worth
remembering for every adb path argument, not just pull.**

**Moto G 2026: it cannot decode the file at all.** `c2.mtk.hevc.decoder` rejected the 7680×4320
configuration outright, and the vendor codec table says why:

```xml
<MediaCodec name="c2.mtk.hevc.decoder" type="video/hevc">
    <Limit name="size" min="16x16" max="2560x1440" />
```

**1440p is the ceiling, and it is not an HEVC quirk** — `c2.mtk.avc.decoder` and
`c2.mtk.vp9.decoder` declare the same 2560×1440, and the software fallbacks are worse at 1920×1088.
So downscaling is not slow on this device, it is impossible, and no amount of patience or charging
changes that.

**Measured versus declared, and the difference matters.** The 8K failure is measured: the decoder was
handed the file and threw. That 4K would also fail is *inferred* from the table above and has not been
run — worth stating plainly rather than leaving as a fact nobody checked, which is the failure mode
this file exists to prevent.

The phone is coherent on its own terms, and Ian said as much: it is an inexpensive handset, its camera
records 1080p, and it holds no 4K content of its own. A 1440p ceiling is a sensible thing to build for
that price. The problem is not the phone.

**That makes the feature device-dependent, and it needs a capability check before it is offered.**
Query `MediaCodecInfo.VideoCapabilities` for the input's codec and resolution and only offer
downscaling where the decoder supports it. Without that, a user gets an unexplained failure on
exactly the largest files they most wanted shrunk.

**An earlier version of this entry argued the cross-device case was ordinary. Ian disagreed and was
right.** The claim was that GallerySync moves files between phones through OneDrive, so footage shot
on a Fold and restored onto a Moto is routine. It is not, and the reason is a decision made hours
earlier the same evening: **Restore is scoped to what this app uploaded from this device.** Anything
it offers back was on that phone before, so the phone could handle it. The case cannot arise through
Restore at all.

**And the feature turns out to be self-limiting**, which is the more useful observation. The 9.8×
saving comes from 8K being enormous to begin with; a phone that only shoots 1080p produces footage
barely worth downscaling. Capability and benefit scale together — the phones that generate video worth
shrinking are the phones that can shrink it.

**So the capability check is insurance, not a load-bearing part of the design.** The one path where it
still matters is TASK-014's initial bulk download onto a *different* handset, which is deliberately
not Restore, and which means moving to a less capable phone. A thin edge, worth a guard because a
guard is cheap, and not the justification the feature rests on.

**What is still unmeasured**, and none of it is small:
- **One clip, 31 seconds, from cold.** Sustained transcoding heats a phone; 0.5× realtime is a
  cold-start figure and a ten-minute clip may not hold it.
- **Battery cost**, entirely unmeasured. Charging-only blunts this, but the number should exist.
- **Where the real ceiling is on the devices that can do it.** The Fold managed 8K; nothing has
  established what a mid-range phone that *can* decode 4K costs to transcode it.

### 28 Aug 2026 — on 1080p footage the saving is bitrate, not pixels

Ian, after the 8K number: *"maybe testing what a 1080p video looks like when it is transcoded down to
720, 540 or even 480p and how much room that would save."* The right question, because 8K is
spectacular and unrepresentative — most libraries are 1080p, and the Moto records nothing else.

**Fold 4, one 59.8 s clip at 1920×1080, 221.6 MB — about 30 Mbps:**

| Target | Out | Saved | Elapsed |
|---|---|---|---|
| **1080p, re-encode only** | **64.8 MB** | **71%** | 11.2 s |
| 720p | 31.0 MB | 86% | 9.3 s |
| 540p | 18.1 MB | 92% | 8.7 s |
| 480p | 13.1 MB | 94% | 8.3 s |

**Re-encoding at the same resolution saves 71% without losing a pixel.** Everything below that is
diminishing: 720p adds 15 points, 540p adds 6, 480p adds 2 — so dropping to 480p buys 23 further
points at the cost of three-quarters of the linear resolution.

**This is a different answer from the photo proxies, and worth not assuming otherwise.** A photo is
shrunk by throwing away pixels; this clip is shrunk by throwing away bitrate. Samsung records at
~30 Mbps and Media3 re-encoded at ~8.7. The pixels were never where the space was.

**So the honest feature for 1080p footage may be "re-encode", not "downscale"** — which would keep
full resolution, sidestep most of the quality argument, and make the setting easier to explain.

**Speed is a non-issue here.** 0.14–0.19× realtime, roughly six times faster than playback: a minute
of 1080p costs about ten seconds, against fifteen seconds for thirty-one seconds of 8K.

**Two things this does not settle.**

*What it looks like.* Numbers cannot answer it. The four outputs are left on the Fold at
`/sdcard/Download/transcode-samples/` to be watched, because whether 8.7 Mbps is distinguishable from
30 on a phone screen is the entire decision and only eyes can make it.

**Re-run on daylight footage, and the numbers moved a lot.** Ian picked a replacement — 1080×1920
portrait, 18.0 s, 38.4 MB, **~17.1 Mbps**, which is an ordinary phone bitrate rather than the
fireworks clip's 30.

| Target | Out | Saved | Dark clip said |
|---|---|---|---|
| **1080p, re-encode only** | 20.2 MB | **47%** | 71% |
| 720p | 10.3 MB | **73%** | 86% |
| 540p | 6.3 MB | **84%** | 92% |
| 480p | 4.8 MB | **88%** | 94% |

**Every figure was flattered, worst at the top: 71% was really 47%.** Two causes compounding — the
near-black frames were cheap to encode, and the source bitrate was nearly double. Content-dependence
is not a footnote on this measurement, it is most of the variance.

**The shape survives; the conclusion shifts.** Re-encoding alone still returns about half with no
resolution loss, but it no longer dominates the way it appeared to. The 1080→720 step is now worth a
real 26 points, where against the dark clip it looked like a marginal 15 on top of an already-huge
saving. Below 720p the returns still collapse: 540p adds 11, 480p adds 4.

**Then Ian watched them, and the numbers stopped being the argument.** *"Even the 480p at 4.77 MB is
a good looking clip — I can't tell the difference in the quality between them."*

That overturns the paragraph this replaced, which recommended two settings on the grounds that 540 and
480 spend too much picture for too little space. They do not, on this clip, on a phone screen. **The
useful setting is the aggressive one, and the saving is 88% rather than 47%.**

**What that does to the product is the real point.** Gate 2 records the Fold 8 as ~16 GB of photos
against ~130 GB of video, with photo proxying reclaiming about 14 GB — under 10% of the library, which
is why the wording there leads with what *stays*. At 88%, that video becomes roughly 15 GB and frees
about **115 GB**. Eight times what the photo lever can reach, aimed at the part of a library that
actually fills a phone.

It also confirms the premise the video decisions were built on rather than upsetting it. This file has
said since 19 Aug that old video is *for watching* and that editing from a degraded clip caps the
export — which is precisely why only old video is touched and retrieval covers the rare edit. "I
cannot tell the difference watching it" is the criterion that argument assumed, now tested rather than
asserted.

**The screen question is already answered, and in the hardest way available.** Ian made the
comparison on the Fold 4's **inner display** — 2176×1812, 7.6 inches — where a 480×853 clip is
upscaled roughly 2.5× linear on a large, dense panel. That is the most demanding surface this app
runs on, the opposite end of the range from the 344dp cover screen the compact layout is proven
against. It was not a forgiving test and 480p passed it.

**Two things still worth checking before this is built on:**
- **A high-motion, detailed clip.** Content dependence has moved these numbers twice already — the
  dark clip flattered them by up to 24 points — and fine texture with fast panning is where low
  resolution shows first. One clip judged is one clip.
- **What an edit really costs.** Stated here first as "exports at that resolution permanently",
  which Ian corrected: CapCut will export a 480p clip at 1080p or higher. The cost is detail rather
  than resolution — upscaling cannot invent what the source does not hold. Worth a look rather than
  an assumption, and cheap to check: export one of these 480p clips from CapCut at 1080p and watch
  it. Given 480p already proved indistinguishable on the inner display, the honest expectation is
  that this matters less than the original claim assumed.

Speed remains negligible — 0.11–0.19× realtime, an 18-second clip in 2–3.4 seconds.

Outputs on the Fold at `/sdcard/Download/transcode-samples/`. **The comparison that matters is now
1080p against 720p**, because that is the actual decision.

*The dark-clip caveat, kept for the record.* Two reasons it was held loosely, both confirmed:

30 Mbps is a high source bitrate; a phone recording 1080p at a more typical 17 Mbps has less fat to
trim.

And **the clip is bad content for this test**, which Ian spotted straight away: a sped-up fireworks
display, shot at night, very dark. Compression is content-dependent, and darkness is the easy case —
large near-black areas cost almost nothing to encode, so the saving is probably flattered. The
time-lapse cuts the other way, since every frame differs sharply from the last and that is expensive,
but "the two effects partly cancel" is not a measurement.

**To be re-run on ordinary footage** — daylight, texture, faces or foliage, handheld motion, normal
speed. The shape of the curve should hold, since it comes from bitrate rather than scene content; the
numbers on it are this clip's and should not be quoted as the feature's.

### 29 Aug 2026 — the wizard and Settings need a rewrite, and here is the inventory

Ian, having watched the Gate 2 copy go through four revisions in one evening: *"both the Wizard and
Settings will need a complete rewrite."* He is right, and the reason is not that the writing is bad.
**The model changed underneath the copy.** Video optimising, a master switch, per-medium modes, an
age vocabulary and a quality setting all arrived on 28 Aug; the screens still describe the app as it
was that morning.

Written down now so the rewrite starts from an inventory rather than a reading of every string.

**Copy that is now false, not merely dated:**

- `settings_auto_optimise_on` — *"Android still asks you to confirm each batch — it does not allow
  this to happen unattended."* Untrue since 26 Aug, when the proxy write moved to the SAF tree grant.
  `ProxyApplier` picks by `safWriter.covers(paths)`: unattended inside a granted tree, a tap outside
  one. The string states the worse case as the only case.
- `wizard_auto_optimise_body` — same claim, same problem, on the screen users actually meet.
- Three strings still promise video is never touched: `proxy_videos_excluded`,
  `library_free_space_detail`, and the video line in `settings_optimise_explainer`. All were true on
  27 Aug.
- `LibraryEstimate` counts photos only, so any byte figure it produces now understates by roughly
  eight times. Gate 2 quotes no number at all rather than a wrong one, which is a stopgap.

**Structural, rather than wrong:**

- **Two spellings.** The wizard says *Optimize* — Ian's words, kept verbatim at his request — and
  everything else says *Optimise*. One of them has to move.
- **`settings_optimise` is photo-only** and predates the master switch. The new model is
  `isOptimiseEnabled` plus per-medium mode, age and quality, and Settings has no home for any of it.
- **`settings_auto_optimise_off`** describes a manual tap that the new `OptimiseMode.Manual`
  supersedes.
- The wizard's optimise panel asks a question — *ask me first, or tell me* — that the master switch
  and mode now answer better.

**The thing to hold on to while rewriting.** Every screen in this app that has caused trouble caused
it by describing a mechanism rather than an outcome, or by describing yesterday's mechanism. The copy
that has survived — the Archive confirmation, Gate 2's numbers, "N verified in OneDrive" — says what
the user gets and where their files are. The copy that keeps breaking explains how Android works.

### 29 Aug 2026 — the video proxy marker has a route, proven on hardware

The open hole from `c910a26`: a transcoded clip is not stamped as a proxy, so a reinstall leaves
smaller copies nothing can recognise. The plan `ProxyMarker` was written around assumed the transcode
would write an MP4 `©wrt` atom that `MediaMetadataRetriever.METADATA_KEY_WRITER` reads. **That plan
cannot work, and a different one can — both settled by a probe on the Moto G (Android 16, Media3
1.9), `VideoMarkerProbeTest`.**

- **Media3's muxer cannot emit `©wrt`.** Its metadata is orientation, location, capture-FPS,
  timestamp, XMP, and custom key/value (`MdtaMetadataEntry`, the `mdta` box). No iTunes writer atom.
  So the contract as written — write `©wrt`, read `METADATA_KEY_WRITER` — is unbuildable from both
  ends: the write side can't produce the atom, and `MediaMetadataRetriever` can't read the `mdta`
  store the muxer *can* write. The one field both sides share is location, unusable without clobbering
  GPS.
- **The `mdta` route works.** Writing `MdtaMetadataEntry("com.gallery.sync.proxy", …,
  TYPE_INDICATOR_STRING)` through `InAppMp4Muxer.Factory`'s `MetadataProvider` hook — wired via
  `Transformer.Builder.setMuxerFactory` — put the key and value into the muxed output. Read back by a
  ~40-line `moov → ilst → data` byte walk (no `media3-exoplayer`), the value came out exactly:
  `GallerySync proxy/video-transcoded`. The probe's summary: `optionA_write=true optionA_read=true`.
- **The current reader confirms it must change.** On the same file `ProxyMarker.isProxy` returned
  false and `METADATA_KEY_WRITER` was null — an `mdta` marker is invisible to today's read path.
- **The `©wrt`-injection alternative is not cheap.** Media3 writes `moov` **before** `mdat`
  (`[ftyp, moov, free, mdat]`), so inserting a `udta/©wrt` into `moov` would shift `mdat` and force a
  rewrite of every chunk offset. That kills the keep-the-reader-as-is option; the `mdta` route is the
  one to build.

**So Option A is confirmed:** write the marker as an `mdta` key via the muxer hook, and replace
`ProxyMarker.videoStamp`'s `MediaMetadataRetriever` read with the small box parser. The shared key
string must live as a constant in `ProxyMarker` so write and read cannot drift.

**Confirmed on two vendors.** Moto G 2026 and Galaxy Z Fold 4 (SM-F936U) gave byte-for-byte the same
verdict — write, read, and the `[ftyp, moov, free, mdat]` order all identical — so this is Media3's
behaviour, not one skin's. `media3-container` was added for `MdtaMetadataEntry`, provisional like the
rest of media3 here.

### 29 Aug 2026 — the video marker, built and verified end to end

Option A is now the code. `VideoTranscoder` stamps the clip as it muxes — an `MdtaMetadataEntry` under
`ProxyMarker.MDTA_KEY` (`com.gallery.sync.proxy`) added through `InAppMp4Muxer.Factory`'s metadata
hook — and `ProxyMarker.videoStamp` reads it back by seeking to the `moov` box and scanning it, with
no `MediaMetadataRetriever` and no `media3-exoplayer`. The shared key lives as one constant so writer
and reader cannot drift.

`VideoMarkerProbeTest` now runs the shipping path, not just the mechanism: it drives the real
`VideoTranscoder` and asserts `ProxyMarker` then reads the output back as `VideoTranscoded`.
**Verified on both devices, 29 Aug 2026** — Fold 4 (1.93 MB → 839 KB) and Moto G (→ 920 KB), each
detected correctly.

**One real bug fell out of building the test.** `ProxyMarker.isVideo` decided the MIME type only from
`ContentResolver.getType`, which returns null for a `file://` uri — so a video handed in by file path
was routed down the photo (EXIF) path and its marker was lost. It now falls back to the extension.
Restore uses file uris, so this was not merely a test artefact.

### 3 Sept 2026 — a wizard run on the Moto G, and three defects it surfaced

A full first-run pass on the Moto G, stock Android 16, clean install. Two folders granted (DCIM,
Pictures), signed in, 157 files uploaded, then photo and video optimising. The wizard reached the end,
so the flow works — but three separate defects turned up along the way and all three are now fixed.

**Setup was marked complete without being completed.** `ReconcileViewModel` carried an upgrade
backfill: no setup decision recorded plus a granted tree means an install that predates the wizard, so
mark setup done rather than dropping an existing user into the tour. Its comment argued a fresh
install could never hit it — *"a fresh install has no grants at this moment"* — which is true only of
the **first** construction of the ViewModel. Reinstalling over a run mid-wizard killed the process; on
relaunch the grants from step 4 were there, no decision had been written, and it declared setup
finished. The user lands on the tabs with every album Off, never seeing the library choice, the
optimise step or the first backup. A crash, a force-stop, or the system reclaiming memory does the
same thing.

Fixed twice over: the backfill now records on disk that it ran (`upgrade_backfill_checked`), so "once"
survives the process dying; and it skips anyone with a wizard in progress. That second half needed
`saveWizardStep` to actually be called — it existed but had no caller, so `wizard_step` only ever held
0 or 9. The tour now records each step as it advances. Resume behaviour is unchanged: anything other
than the final step still opens at step 1.

**Close on step 9 did nothing.** Three states across three commits: `e6a0794` left `onComplete` empty
and relied on state changes to make the tour disappear; `aee7125` made it `activity?.finish()`, which
killed the app; `b6e60f2` fixed that by making it empty again. Empty works for **Finish**, because
`completeSetup()` changes state and the parent stops drawing the tour — but Close deliberately does
not complete setup, so nothing changed and the button was inert. The only way out of step 9 was to
wait. Close now sets a session-local `tourDismissed`: the wizard goes away, setup stays unfinished, the
WorkManager chain keeps running, and the next launch resumes at step 9 and re-attaches to the job
rather than enqueuing a second one.

**Step 9 lied during optimising.** `BackupProgressContent` was told only whether the *upload* had
finished, so the moment uploads ended the ring hit 100% and the body read *"Congratulations… Press
Finish"* — while the phone was still transcoding video and the button still said Close. It is now
three phases (uploading, optimising photos, optimising video, done), derived once, with `backupComplete`
defined as `phase == DONE` so the label and the copy cannot disagree. Both optimisers report per-file
progress through a new `onProgress(done, total)`, so the phase shows "Optimising 4 of 9" rather than
sitting silent for minutes.

**Video transcode cost, measured on 1080p.** Three clips on the Moto G: 284 MB → 67 MB, 98 MB → 24 MB,
80 MB → 19 MB, all 1080p → 720p, at roughly 20–27 seconds each. Not the 8K figure the transcode item is
gated on, but it is the first real cost from the wizard's own path, and it is why the optimise phase
needs a counter at all.

**The upload runs in batches of 25**, each a separate WorkManager execution — two distinct work ids
observed for one run. So the dispatch delay before the first byte is not a one-off; it recurs at every
batch boundary, seven times for 157 files.

### 4 Sept 2026 — Close reset the install choice, and nothing was optimised

**Close must not stop or reset anything.** Stated by Ian, repeatedly, and this is what broke it.

A full run on the Moto G: 155 files uploaded, the wizard closed at 40% and reopened at 48%, and when
the upload finished **not one photo or video was optimised** — no attempt, no failure, no log line.

The install choice lived only in `ReconcileUiState.libraryChoice`. Nothing wrote it to disk. Closing
the wizard mid-backup ends the process, so the ViewModel came back holding the default —
`BACK_UP_EVERYTHING`, which does not optimise — and step 9's pass collected zero candidates:

```kotlin
val shouldOptimise = _state.value.libraryChoice.mode?.proxiesPhotos == true
val photoCandidates = if (shouldOptimise) proxyApplier.candidatesAll() else emptyList()
val videoCandidates = if (shouldOptimise) videoOptimiser.wizardCandidates() else emptyList()
```

With no candidates, `backupComplete` went true immediately and Finish appeared, so the wizard reported
success for a run that had silently skipped half of what the user asked for.

**The weakness is old; making Close work is what exposed it.** Until 3 Sept, Close did nothing at all,
so the process stayed alive through step 9 and the choice was never lost. Giving Close its proper
behaviour turned process death mid-backup from impossible into the ordinary way to leave, and this was
the first thing to fall through. Worth remembering when anything else is found to be in-memory-only:
the wizard now expects to die and come back.

Fixed by persisting it — `library_choice` in `BackupSettings`, read back by name via
`LibraryChoice.fromNameOrDefault` so inserting a fifth choice later cannot reinterpret one already
made. Video quality was already persisted; the install choice was the only part of steps 6 and 7 that
was not.

**This is Area 1 and nothing else.** It writes no album mode — those are the user's alone — and it does
not touch the ongoing optimise settings. An earlier attempt to explain the same symptom by pointing at
`applyLibraryChoice` not being called from the tour was wrong on exactly that point: the tour is right
not to call it, and albums reading Off after an initial backup is correct.

### 4 Sept 2026 — the app does not open until the backup is done, and Close is not a dismissal

Stated by Ian: **the user should not have access to the full app until the backup and optimising are
complete, unless they chose to back up manually.** The wizard is a gate, not a tour you can step out
of, and three things had to change for that to hold.

**Close closes the app; Finish opens it.** They had been one branch through one `onComplete`, which
is how they kept trading each other's behaviour — `aee7125` made it `activity.finish()` (right for
Close, wrong for Finish, which killed the app on a completed setup), `b6e60f2` emptied the lambda to
fix Finish and left Close inert, and the 3 Sept fix made Close dismiss into the app, which breaks the
rule above. They are now separate: Finish records setup and hands over the app; Close ends the app
while the WorkManager chain runs on without it. Reopening lands on step 9 re-attached to that chain —
watched working on the Moto G, 3 Sept, after a Recents swipe killed the process mid-run.

**Finish appears only when everything is done.** Already gated on `backupComplete`, which since the
phase change means uploaded *and* photos optimised *and* video optimised. It read as early only
because the ring's own text was driven by `state.backupFinished` — the upload alone — so it said
"Finish" while video was still transcoding.

**Choosing to back up manually now ends the wizard at step 8.** `CHOOSE_PER_ALBUM` is *"check cloud
storage but do not back up any new files"*, and `LibraryChoice.uploads` returns false for it — but
nothing at step 9 read that. `outstandingCountAll()` is `SELECT COUNT(*) WHERE state != UPLOADED`
with no album-mode filter, and the run it enqueued passed `allAlbums = true`, which routes the worker
past mode filtering deliberately. **So the one choice that exists to prevent an upload started one.**

Step 8 is now the last step for that user: the button reads Finish, setup is recorded, and the app
opens. One derived value — `lastStep` — decides it, and the button label, the branch that acts on it
and `isLast` all read the same value so they cannot drift apart the way Close and Finish did.

Note what this does **not** do: files already uploaded by the old behaviour stay in OneDrive. Nothing
here removes them, and nothing should.

**Resolved by deletion, the same day.** The counter was first made truthful — derived from the steps
this user is actually shown, nine less one for optimising when there is nothing to configure and less
one more for the backup when they chose to do it manually. That worked, and it was still the wrong
answer. Ian: *"It was only added so we could talk efficiently about the steps."* It was scaffolding for
a conversation, not something a user needs, and **every counter is now gone** — the step number, and
the per-card count inside step 2 that had briefly read "(1/6)" through "(6/6)".

Two arguments were made for keeping it and both were wrong. That a first-run flow owes the user a
sense of how much is left: the wizard is nine short screens with a Next button, and nobody is lost in
it. And that it stays useful while we iterate: it is useful to *us*, which is exactly the reason to
take it out before it ships. `tour_step_of` and `TourBubble`'s suffix parameter went with it rather
than being left dead, and `stepNumber` survives only to decide whether Back is offered.

The conditional-steps problem dissolved with it. There is no longer anything that can promise nine
steps and deliver eight, and no total that grows mid-wizard when a choice on step 6 adds step 7.

**The OneDrive picker's path scrolls rather than wraps.** A dialog is narrow and a drive path is not
bounded, so `OneDrive > Samsung Gallery > DCIM` broke mid-word into "DCI / M" and would have got worse
with depth. Every crumb is now one line with no soft wrap, the row scrolls, and it scrolls to the end
on each move because the folder you are standing in is the one you need to see. A back button was
added beside the crumbs — jumping via a crumb already worked, but the common move is backing out of
the folder you just opened.

### 3 Sept 2026 — a withdrawn claim, and the instrument that caused it

**`dumpsys uri-grants` does not exist on the Moto G.** It returns `Can't find service: uri-grants`, and
an unchecked `grep -c` over that empty output reads as zero. On the strength of it this log's author
reported that a wizard run had persisted **no** SAF grants and called the cancel-skips-silently bug
*confirmed on hardware*. Both claims are withdrawn. The app's own store had two live grants —
`primary%3ADCIM` and `primary%3APictures` — and `forgetRevokedGrants()` prunes anything the system no
longer honours on every start, so their survival is positive evidence they are held.

The reliable check is `run-as com.gallery.sync cat files/datastore/media_scope.preferences_pb`, which
lists `granted_tree_uris` directly. This is the second time on this project that a silent instrument
has been read as data — see the `content query` row counts of 28 Aug. **An empty result from a tool
that never ran is not a measurement.**

### 3 Sept 2026 — three SAF picker defects, found by reading, not yet fixed

None of these are demonstrated on hardware — see the withdrawal above — but all three are plain in the
source and are recorded so they are not lost.

1. **The picker opens in the wrong place.** `EXTRA_INITIAL_URI` on `ACTION_OPEN_DOCUMENT_TREE` wants a
   *tree* uri; `SetupTour` builds one with `DocumentsContract.buildDocumentUri`. A document uri is
   ignored, so the picker lands wherever it was last rather than on the folder being asked for —
   observed opening on `Documents` when it had asked for `DCIM`. Note the Moto G's document provider is
   **Files by Google**, not the stock DocumentsUI, so behaviour may differ again on the Fold.
2. **Cancelling skips a folder silently.** `onSafGrantReceived` drops the head of `safGrantQueue`
   whether or not a uri came back, and `directoryRefused` is only set when a uri arrived and was
   rejected. Cancel the dialog and the wizard advances reporting nothing wrong.
3. **The returned tree is never checked against the one requested.** Navigate into a subfolder and
   `USE THIS FOLDER` grants that subfolder while the parent is ticked off the queue. Nothing loses
   data — the tree grant is for proxying and restore, never deletion — but optimising later has no
   write path and no explanation.

### 3 Sept 2026 — the OneDrive destination can be browsed instead of typed

The destination was a text field, which required knowing the drive's folder layout. SAF is the wrong
tool for it: the OneDrive app publishes a DocumentsProvider, but it hands back a tree uri that cannot
be turned into the Graph path the uploader needs, and it only exists if that app is installed.

Built in-app instead, on what `OneDriveRepository` already exposed — `listRoot`, `listFolder`,
`listNextPage` — with folders picked out of the pages by type. **Folders only**, so a page can come
back empty while more remains (a folder of a thousand photos and one subfolder pages several times
before the subfolder appears); the picker says "Show more" rather than showing an empty list that is a
lie. `createFolder` is new, on `OneDriveUploadRepository` rather than the read-only interface, and it
uses `conflictBehavior: fail` — the opposite of uploads, and deliberately: an upload renames because
two cameras honestly produce the same filename, but silently creating `Backups 1` beside an existing
`Backups` and pointing the destination at the empty one is a trap.

Browse sits **beside** the text field, not instead of it. Typing a known path beats walking ninety
albums to reach it, and the field still works when the network does not.

### 4 Sept 2026 — the wizard's optimise numbers, measured twice, and five defects around them

**Moto G 2026, stock Android 16, `ZT422CTZQV`.** A 155-file fixture in `DCIM` — 150 camera photos at
642,867,972 bytes and 5 clips at 613,900,657 — restored byte-identical between runs, so the two runs
are directly comparable.

**The savings card showed nothing at all, and the cause was ordering.** The reconcile fires from the
directories flow, which settles at step 4; sign-in is step 5. With no token every album listing fails,
so `ReconciliationRules.tallyAlbum` files each album under `unchecked` rather than `outstanding` —
correct on its own terms, and fatal downstream, because every figure on the optimise and summary cards
is computed from `photosOutstanding` and `videosOutstanding`. `album_cloud_status` said so plainly:
three rows, all written 12:07:17, all `couldNotCheck = 1`, `missingFiles = 0`. Nothing re-ran the check
after sign-in. It does now, and the same table then read `couldNotCheck = 0` with 50 + 100 + 5 = 155
missing — every file in the fixture.

The deeper fault was that a blank card is indistinguishable from a zero. Both tallies read zero whether
the library is fully backed up or the drive was unreachable, and the card now names which, the same way
`reconcile_incomplete` does. **Never let "could not ask" render as an answer.**

**The measured savings, against what the app promised.**

| | assumed | measured | |
|---|---|---|---|
| Photos (2048px proxy) | 70% | **83.5%** | 642,867,972 → 105,800,737, *identical to the byte across both runs* |
| Video High (480p) | 90% | **84.5%** | over-promises |
| Video Medium (720p) | 75% | **75.4 / 75.7 / 76.3%** | three clips, accurate |

`ProxyGenerator.APPROXIMATE_SAVING_PERCENT` was added at 70 from the 9-file run recorded above, whose
album was full of small images; a camera roll proxies far harder, and **all 150 photos here were over
2048px so none were skipped** — the skip rate is the figure that swings, not the ratio. 70 should become
about 80. Video **Medium is sound and High is not**; High is the one that over-promises, which is the
worse direction, and it is Ian's recorded figure so it stands until he moves it.

Ian caught the photo error from outside the code: the card offered to save 1.2 GB out of a 1.26 GB
folder, which is the whole library and change. It had been quoting `totalPhotoBytes` whole — a claim
that every photo comes back as zero bytes.

**155 files before, 155 after, no rename, no `.trashed*`.** Proxying shortens in place and removes
nothing.

**The delayed start was stored and then ignored.** The chips wrote `firstBackupStartHour` and
`FirstBackupWindow` gated on it correctly — but the wizard enqueues a **manual** run, and manual runs
are exempt from that window by design ("Sync now goes straight to the engine"). Reaching step 9 also
called `startBackupWorker()` unconditionally, with no `startNow` check, and nothing anywhere called
`setInitialDelay`. So "Start in 1 hour" uploaded immediately.

Now the delay is an absolute due time (the hour-of-day form could not express real minutes: chosen at
13:25 it meant 14:00, i.e. 35 minutes) handed to WorkManager, which owns it from then on. Verified in
WorkManager's own table: `gallery-sync-backup-manual`, ENQUEUED, `initial_delay = 3,549,487`. `SYNC NOW`
replaced it — same row, RUNNING, delay 0.

**Back from the progress card cancelled the running upload.** Arming a delay re-enqueues the manual
chain, and `enqueueContinuation` uses `ExistingWorkPolicy.REPLACE`, so navigating back mid-run and
choosing a delay killed the upload in flight — observed stopping dead at 9 of 155, which looked like a
hang rather than a cancellation. Found by Ian, by pressing Back.

Aborting a run is now a **feature rather than a side effect**: the control reads `Cancel` while a run is
moving bytes, raises a confirmation, and `abortBackup()` cancels the chain, cancels the polling loop
and returns the wizard to the settings cards. Cancelling the WorkManager chain alone was not enough —
`observeBackupWorker` runs a `while(true)` loop writing counts back into state, so the screen would
reset and then watch the old numbers reappear a poll later. `setFirstBackupDelay` also refuses outright
while a run is under way, because the UI fix closes the route we know about and a second route would be
silent.

**Two things Close does not do, one fixed and one open.**

`finish()` destroyed the activity but left the task in Recents as a live-looking card, which is what
minimising looks like — `finishAndRemoveTask()` now. The process is deliberately left alive: the upload
runs inside it.

> **WITHDRAWN the same evening, 4 Sept 2026.** The last sentence is false and was never checked.
> `finishAndRemoveTask()` **kills the process** — `Killing <pid> (setSvc -10000): remove task` — which
> stopped the upload and left the app force-stopped so its queued work was never dispatched. Reverted
> to `finish()`. See the evening entry at the end of this file.

**The open one: the optimise passes die with the wizard.** Uploading survives Close because it is a
WorkManager chain; `applyWizardProxies` and `applyWizardVideoOptimise` run in `viewModelScope`, which is
cleared with the activity. Closing during the video pass left three clips transcoded and two untouched,
and nothing resumed them — the album modes were all `Off` by then, so the ongoing Sync-only path could
not pick them up either. The card says "You can check progress any time by opening the app", which is
true of the upload and not of this. **Unfixed.** Either move the passes into a worker or say plainly
that optimising needs the app open.

**A counter that went backwards.** Reopening mid-phase recomputes the *remaining* candidates, so after
two clips were done the ring read "0 of 3" where it had read "2 of 5" — each number true, the pair of
them reading as work undone. Both passes now offset by `BackupEntryDao.countProxied`, so the count
describes the phase rather than the attempt.

**One layout fault, twice.** `ButtonDefaults.ContentPadding` spends 24dp a side, which is most of what a
button gets when three or six share a dialog. It broke "High — 480p" into "Medi / um / — / 720p" and
clipped the last delay chip off the card edge. Both rows now carry trimmed content padding, `maxLines =
1` and `softWrap = false`; the delay label moved above its row to free the width. Worth remembering as a
shape of bug rather than two incidents: **a row of weighted buttons in a dialog has far less room than
it looks, and Compose wraps rather than shrinks.**

**Also seen, not chased.** After proxying, album rows read "0 of 50 verified in OneDrive" while the hero
above them says "Everything here is backed up". The reconcile matches on name *and* byte size, and a
proxy no longer matches its full-size original, so the row is describing the check honestly while
reading as though the backup evaporated. The ledger still holds `remoteSizeBytes = sizeBytes` from
upload time, so the information to say it properly is there.

### 4 Sept 2026 — optimising moved off the wizard's lifetime, and the constants recalibrated

**Moto G 2026, same restored 155-file fixture.** Follows the entry above; read it first.

**The optimise passes now run in [OptimiseWorker].** They used to run in `viewModelScope`, which is
cleared with the activity, so closing the wizard abandoned them — uploading survived Close because it
is a WorkManager chain and optimising did not, a difference the card never admitted to. Consent still
comes from the activity, because `MediaStore.createWriteRequest` can only be raised there; the grant is
per-URI and persists, which is what lets the worker carry on once the wizard is gone.

Batched at 60 photos and 3 clips with a continuation while work remains, the same shape as
[BackupWorker] and for the same reason: a transcode is tens of seconds a clip and WorkManager stops a
worker that runs too long.

**Verified twice over.** WorkManager held a `gallery-sync-optimise` chain with batches succeeding
individually, and the ledger stepped `14 → 60 → 115 → 150` in batch-sized jumps, so the re-enqueue is
real rather than one long run. **Ian confirmed closing the app several times during the optimise phase
and the work continuing** — that half is his observation, not the log's: the watcher polled every 20s
and never caught the launcher in front, so a close and reopen between polls left no trace. Worth noting
as an instrumentation lesson, not a doubt about the result.

**The constants, recalibrated against three runs and then checked.**

| | was | now | measured |
|---|---|---|---|
| `ProxyGenerator.APPROXIMATE_SAVING_PERCENT` | 70 | **80** | 83.5% |
| `VideoQuality.High` | 90 | **85** | 84.5% |
| `VideoQuality.Medium` | 75 | 75 | 75.4 / 75.7 / 76.3% — already right |

Photos returned **105,800,737 bytes on all three runs, identical to the byte**; video landed within
1,231 bytes of the previous run, which is encoder non-determinism. With the new figures the card
promised ~514 MB of photos against 537.1 delivered and ~522 MB of video against 518.5 — both now within
a few percent, and photos under-promising rather than over. Before, the photo figure claimed the entire
byte count of every photo.

**Two places the app knew a file was safe and said otherwise.** Ian, reading a folder of 100 rows:
*"all the files are labeled as optimized NOT backed up"*.

`isProxied` was tested first in `statusLabel`, so an optimised file read "✓ optimized" and nothing said
it was in the cloud — on exactly the files where that matters most, since a proxy is the case where the
full-resolution image exists **only** in OneDrive. The two were never alternatives: a proxy is written
only over a file the ledger has verified. The row now reads "✓ backed up · optimized".

At folder level the same fact was lost differently, and Ian's framing is the right one — *"if it can
say that in the files then it can say it on the folder level"*. The Albums tab read "2 of 5 verified in
OneDrive" for an album whose five were all uploaded, falling further with every clip optimised, because
the reconcile matches on name **and byte size** and a proxy is deliberately smaller than the original
OneDrive holds. `ReconciliationRules.tallyAlbum` now takes the pre-proxy sizes and compares against
those. Confirmed on the device immediately afterwards: three albums went from 0/50, 0/100 and 2/5 to
50, 100 and 5 verified, 0 missing.

Note where the sizes come from: `sizeBytes` keeps the **original** when a proxy is written and
`localProxySizeBytes` holds the shrunken one, so the ledger already knew — nothing new had to be
recorded, only read.

**Spelling settled: British, everywhere the user can see it.** Ian, 4 Sept 2026, asked whether it had
been changed globally — it had not, and the app was about 80% `-ise` with an American pocket in the
wizard card titles ("Optimization Settings") and the album detail rows ("optimized"), so someone moving
between Settings and the wizard met both. Nine strings and four hardcoded literals moved to `-ise`;
string *names* (`tour_optimise_*`) were left alone, being invisible to users and pure churn to rename.
Comments quoting the old label or Ian's own words keep their original spelling, because they are
describing what was written rather than what is shown.

**A percentage nobody had counted yet was rendering as zero.** Reopening mid-phase builds a fresh view
model, so `optimiseTotal` is 0 until the first ledger read returns, and the ring announced a confident
"0%" before jumping to the real figure. It shows "…" until the count exists. The count line beneath it
already followed this rule — *"0 of 0 would be worse than saying nothing"* — and the headline simply was
not covered by it. **The same rule keeps having to be applied in one more place: unknown is not zero.**

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
- **Where TASK-011's applying step runs.** ~~WorkManager cannot attach the `ClipData` that carries the
  write grant, and Android 12+ blocks starting a foreground service from the background.~~

  **Answered 19 Aug 2026, and it should have been struck through then.** The SAF conclusion in the
  hardware log says it directly: options 1/2/3 for where the applying step runs are *"not needed for
  photos"*, because the tree grant needs no `ClipData` and no Activity. The bullet below carries the
  finding and this one never got it, so for nine days the record showed a blocker against TASK-011 that
  the same day's probe had already removed. Noticed 28 Aug 2026 while auditing why the floor was never
  built.
- **Whether the tap can be removed entirely.** Two routes: `MANAGE_EXTERNAL_STORAGE`, which works
  and spends Play-listing scrutiny, and a persisted SAF tree grant, which is cheaper and unverified.
  Recommended: test the SAF route on hardware first. A new Play-visible permission and a fork in the
  architecture are both escalations. See TASK-011.

  **Answered 19 Aug 2026 — the SAF route works, and it is neither.** A persisted tree grant does
  the proxy write with no dialog and survives reboot; `MANAGE_EXTERNAL_STORAGE` is not needed and
  the Play listing is untouched. Archive still needs `createTrashRequest`, because SAF deletes
  permanently. See the SAF entry in the hardware log.
- **`POST_NOTIFICATIONS`** — **answered 28 Aug 2026 by Ian: not needed, and not asked for.**

  Its two remaining uses both fell. Saying free space is low duplicates Android, which warns on its own
  — *"no need to duplicate their systems."* Summoning the user to an Archive batch is now the exit
  warning, which needs no permission and cannot be denied or silently switched off. See the exit-warning
  entry in the hardware log.

  What this closes is larger than one permission: it removes the last CLAUDE.md escalation standing
  against TASK-011, and with the applying-step bullet above, **TASK-011 has no open questions left.**
  It was never blocked on a decision — see the exit-warning entry for what it was blocked on instead.

  Still available if a later feature earns it: FIX-001's shade-level Stop control is the one candidate.
- **Language dropdown** — **answered 19 Aug 2026:** it belongs in a first-run wizard alongside
  permissions, cloud choice and defaults. Ship English only behind it. See TASK-012.

  **Scoped 4 Sept 2026, then shelved by Ian to revisit later.** Recorded so the survey does not have
  to be done twice.

  - **The groundwork is largely done.** 509 strings and 9 plurals are already externalised —
    527 translatable units, **4,054 words**, 22.4k characters. Byte and time formatting already goes
    through `Locale.getDefault()`, and `android:supportsRtl="true"` is already set.
  - **About 30 strings are still hardcoded, and nearly all sit in one place:** the tour's mockups —
    the imitation Albums, Restore, Archive and Settings screens drawn behind the wizard cards. They
    read as screenshots but are live Compose, so they would ship English inside a translated app.
    Either extract them or give the mockups less text. The rest are `AlbumDetailScreen`'s status
    labels plus one each in `SetupWizardScreen` and `DeletionSection`.
  - **One real platform decision.** `MainActivity` extends `ComponentActivity` and **AppCompat is not
    a dependency**. `LocaleManager` is API 33+ against a minSdk of 26, so system-integrated per-app
    language means adding AppCompat and changing that base class; the alternative is an in-app-only
    picker with no system integration and the edge cases owned here. Either way needs
    `res/xml/locales_config.xml` and `android:localeConfig`.
  - **Text expansion is a live risk on this app, not a theoretical one.** 4 Sept produced two layout
    defects from text not fitting — the video-quality buttons and the delay chips — and both were in
    English. German and French run about 30% longer, and the narrowest surface is the Fold 4 cover
    screen at 344dp. **Run the `en-XA` pseudolocale before any money reaches a translator**: it
    inflates every string ~40% and would have caught both of those before a device did.
  - **Some copy is safety-critical and must not be treated as UI chrome.** TASK-012 already says it —
    a mistranslated mode explanation is the one that costs somebody their photos. 32 units run to 25+
    words (longest 92) and that is where those live. A translation must not promise recoverability
    the English never promised, which is a CLAUDE.md constraint rather than a style preference. The
    vendor-trash naming problem ("Recycle Bin" on Samsung, "Trash" in Files) is unresolved in English
    and multiplies per locale.
  - **Suggested order:** extract the hardcoded strings (worth doing regardless), then the locale
    mechanism and picker with English only behind it, then the pseudolocale pass, then the first real
    locale with the safety-critical copy reviewed separately.

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

---

### 4 Sept 2026 (evening) — Close was killing every backup, and a foreground service did not fix it

Five hours of debugging that began with a delayed backup that never fired, and ended with a one-line
revert, a rejected architecture and a hardware note that should have been written a week ago. Nearly
all of it was measured on the Moto G; almost none of it was visible in code.

**The regression: `finishAndRemoveTask()`.** Introduced at 14:17 in `3585125` to fix a real complaint
— Close left a live-looking card in Recents, so it read as minimising. Removing the task fixes that
and **kills the process**, which was asserted otherwise in both the commit and this file without
anyone checking. From 14:17 until the revert, pressing Close silently stopped the backup.

Worse than stopping it: Android then treats the app as force-stopped, so its **queued work is never
dispatched**. WorkManager says so itself on the next launch — `Application was force-stopped,
rescheduling` — which is why the wizard's delayed start, built in that same commit with the explicit
promise that "it fires with the app closed", could never have worked. The delay was armed correctly
every time; nothing was there to run it.

**Why it was not caught.** The 3 Sept entry above verified the right thing the wrong way: *"Reopening
lands on step 9 re-attached to that chain — watched working after a Recents swipe killed the
process."* Reopening is exactly the action that clears the force-stopped state, so the test cannot
distinguish "the chain kept going" from "opening the app restarted it". Both hypotheses predict the
same observation. The delayed start is the first feature that depends on the app *not* being
reopened, which is why the flaw surfaced four hours after it shipped.

**Reverted to `finish()`, and measured properly.** Five minutes, app closed, nothing touched:

| Gesture | Process | Uploads in 5 min |
|---|---|---|
| **Close** (`finish()`) | alive | **151 files**, continuous |
| **Swipe out of Recents** | killed, never restarted | **0** |

Close is safe. The card returns to Recents and that is the lesser problem: it is untidy, and the
alternative silently stops backups. Home is also safe — tested with `KEYCODE_HOME`, process alive ten
seconds later, no kill line.

**A swipe is not the same as Close, and never was.** Both log `: remove task`, but the swipe is the
system removing the task and no app-side change affects it. After a swipe the process is gone, the
manual chain is gone from JobScheduler, and only the content trigger and the 6-hour periodic remain.
Android logs `Scheduling restart of crashed service ... in 1000ms` and then, twice observed, never
carries it out.

**The foreground service: built, measured, removed the same evening.** Scoped to the wizard's first
backup so ongoing sync kept using uncapped JobScheduler work. It started correctly — `isForeground=true`,
`types=0x00000001`, `uidState: TOP`, no refusal — and a swipe taken while it was up did no harm: the
upload ran to completion with the task gone. Then:

- **It covers one batch.** Both workers re-enqueue continuations, and those start in the background:
  `ForegroundServiceStartNotAllowedException ... mAllowStartForeground false`, 21:19:42. `fgs=0` at
  every subsequent sample.
- **A swipe with the service down is fatal, as before.** Predicted aloud, then confirmed at 21:56:58
  — `Killing 20310 (setSvc -10000): remove task`, optimising frozen mid-pass, nothing restarted in
  the three minutes after.
- **Android 15 caps `dataSync` at six hours per twenty-four.** Ian: *"This app needs to be running
  24/7 - not 6 hours out of 24."* It can never be the answer for ongoing sync.

Removed entirely at Ian's instruction, with a hard rule added to CLAUDE.md so it is not proposed
again. The unsolved case stays unsolved and is now written down rather than assumed away: **a delayed
start armed before a swipe never fires.** A foreground service cannot fix that one either — a pending
delay has no run in flight to hold up. Routes worth investigating: `setExpedited` on the continuations
(quota-limited), or a battery-optimisation exemption.

**Also shipped tonight**

- **A 3-minute delay choice.** The card counted in whole hours, so the shortest delay it could offer
  was 60 minutes and the feature could not be watched. Minutes end to end now; chips read `3m` through
  `24h` and wrap at four per row, because seven do not fit one row at any padding. Verified arming to
  the millisecond: `first_backup_delay_millis = 180000`.
- **Folders no longer arrive pre-ticked.** The card pre-checked DCIM and Pictures always, plus
  anything with 50+ files — which on this phone is 17.3 GB across 36 albums, queued on a default
  nobody chose. Every folder starts off; `canAdvance()` already blocked the step until one is checked,
  so this asks for a choice rather than silently backing up nothing.
- **The Fold 4 is gone.** Shipped out 30 Aug 2026, five days before I proposed testing on it, because
  the hardware table still listed it as a live rig. Corrected there. Two capabilities went with it and
  neither has a replacement: the **development-only OneDrive account**, so every upload test now costs
  Ian real cleanup, and the **344dp cover screen**, the only hardware that could prove the compact
  layout.

**Open, and deliberately not chased tonight**

- **A clean reinstall re-uploads everything.** After a wipe the ledger has no record of which files
  were proxied, so `refreshLedger` cannot skip them; 230 DCIM files already in OneDrive were queued
  and re-sent. `conflictBehavior` is `rename`, so nothing is overwritten and no original is at risk —
  but reinstalling is not a free test reset, and a user who clears app data pays the same cost.
- **A Graph request that wedged.** After one task-removal kill the restarted worker issued a single
  `listFolderByPath` GET that never returned and never timed out, against a 30-second read timeout.
  Seen once. Unexplained.

**Method notes, both earned the hard way**

- **Every reading tonight was contaminated until the reopening was removed.** Both of us kept opening
  the app within two minutes to see whether it was working, which is the one action that restarts it.
  The five-minute untouched samples are the only clean data here.
- **Check the instrument before reporting the finding.** A duplicate-file alarm rested on a regex that
  matched the `(12345 bytes)` at the end of every log line, and then on reading a local filename —
  `..._1 (1).mp4`, which is what the file is actually called on disk — as a server-side rename. Both
  wrong, both stated confidently, and Ian caught both.

---

### 5 Sept 2026 — the upload half honoured "walk away" and the optimise half never had

Reported by Ian: a four-hour delayed start set the previous evening *"did not start — when I opened
the phone the Backup started."* The premise turned out to be wrong, and the real defect was one step
further on. Everything below is from the device: the ledger, WorkManager's own database, file mtimes
and a logcat capture written to disk.

**The delayed start fired exactly on time.** 230 files uploaded between **02:47:19 and 02:55:48**,
four hours to the minute after arming. The manual chain's last continuation was enqueued at 02:55:45
and succeeded, `first_backup_completed` was set, and the 12:04 reconcile found all four albums
verified with 0 missing. Nothing was wrong with the delay, the window, or the network.

**What never happened was optimising.** Every proxy on disk carried a **12:04 mtime** — the minute the
phone was woken, nine hours later. The upload→optimise handoff was a `LaunchedEffect` on the wizard's
progress card, so it only ran while a composition was alive to run it. `BackupWorker` never handed off
to `OptimiseWorker`; the card did. So a first run left alone did the upload and stopped, and what Ian
saw on waking was the handoff finally firing, which reads exactly like the backup starting then.

Same class as the 4 Sept defect where optimising died with `viewModelScope`: **work that outlives the
screen must be owned by something that also does.** That lesson was applied to the passes themselves
and not to the transition between them.

**Two wrong hypotheses, both killed by one question.** Doze was proposed first and is impossible — the
phone was plugged in, and neither light nor deep idle engages while charging. Vendor force-stop was
proposed second and is wrong too: the Moto is stock, the app is in the ACTIVE standby bucket, no appop
restricts it, and the run demonstrably happened. Asking which handset and whether it was charging cost
one message and removed both.

#### The fix

- **The handoff moved into the workers.** `BackupWorker` enqueues the photo pass when a chain drains;
  `OptimiseWorker` queues the video pass when photos drain. [WizardBulkOptimise] is the gate, and it is
  deliberately narrow: setup not yet complete, `allAlbums` (only the wizard's runs route past album-mode
  filtering, so a later "Sync now" cannot drag a bulk optimise behind it), and a Gate 2 choice that
  optimises. It **reads** the install choice and writes no album mode — the two areas stay independent.
- **Consent is checked where the writing happens.** Both passes bail with a log line when the files sit
  outside the granted trees, since no worker can raise `createWriteRequest`. Inside a tree — the ordinary
  case after Gate 1 — there is no dialog and nothing to wait for.
- **`enqueueOptimiseIfAbsent`, with a per-phase tag.** Two things now start these passes and both are
  correct, so the second must not append a duplicate. The worker's own continuation still enqueues
  directly, because its phase is `RUNNING` and carries the tag the check looks for.

#### A second defect, found by testing the first

The 3-minute rehearsal did not run at all, and for an unrelated reason: **Close during arming lost the
arm.** `scheduleDelayedBackup()` did a ledger refresh and a four-album cloud reconcile *before*
enqueueing, all inside `viewModelScope`, and Close clears that. The due time was written to the
DataStore, so the card drew a real countdown over work that did not exist, and `onDelayElapsed` then
watched an upload that could never begin. Silent in both directions. It survived last night only
because the app was left open on the card.

- **Armed first, prepared second.** The enqueue is the promise; the totals under it are what the card
  says while it waits, and losing those to a Close costs a number on a screen rather than the backup.
- **`onDelayElapsed` asks WorkManager** whether a chain exists rather than assuming one, and starts the
  run if it does not. A due time and a queued job are written by two different systems and only one of
  them survives the activity going away mid-arm.

#### Verified unattended, Moto G, 13:19–13:27

```
13:19:50  delayed first backup armed: 165618ms        <- the remainder, not a restarted clock
13:25:15  backup run starting (manual)                <- app closed
13:25:31 / 13:25:34 / 13:25:37   continuations, 67 -> 48 -> 23 remaining
13:26:30  backup run finished: 21 uploaded, 0 remaining
13:26:30  upload drained; photo optimise handed to the worker   <- BackupWorker, not the card
13:26:31  photos: proxying 20 of 20
13:26:31  photos done; video pass queued                        <- second handoff
13:26:33  video: optimising up to 3 of 102 at High
```

71 files uploaded across four batches, then both optimise phases starting themselves. The duplicate
guard was exercised for real: reopening at 13:26:39 made the card try to start the video pass and it
was refused with `video optimise already under way`, twice. Without it there would have been two chains
transcoding the same clips.

**It fired late and that is worth knowing: due 13:22:36, started 13:25:15** — 2m39s after the countdown
expired. JobScheduler's discretion, not a fault, but a delay is *about* then rather than *at* then, and
the copy must not promise a time it cannot keep.

#### Decided, not defects

- **The 0% flash on reopening is accepted, and the reason is not taste.** Reopening mid-run shows
  "starting upload" at 0% for a few seconds until the first ledger read returns — observed while the
  run was already 41 of 71 in. Ian, 5 Sept: he is content with it **because fixing it before broke
  other, more critical functionality.**

  That is the whole argument, and the 28 Aug entry *"the percentage, settled in four passes"* is the
  evidence for it: every attempt to correct this display broke the figures around it — a frozen 21%,
  a percentage lurching 48→15→48→18→22→67→2 on video, a new album opening at 79% against an hour-old
  denominator, and a hero flashing to zero on every completed file. What holds now needed three
  separate mechanisms agreeing (per-file refresh, a floor inside a run, and the baseline closing
  wherever no work is outstanding), and it holds.

  So the cost of touching it is demonstrated and the benefit is cosmetic. **Do not "fix" this by
  citing *unknown is not zero*.** That rule stands for the optimise headline, where it was cheap;
  here the same change has repeatedly cost the accuracy of a number the user actually relies on.

  **The dead end, written out so it is recognisable next time.** The approach that keeps suggesting
  itself, and was proposed again on 5 Sept before Ian stopped it:

  > *On reopening, show "…" rather than 0% until the first ledger read returns, and suppress the
  > "starting upload" label for a run that is plainly mid-flight.*

  It sounds free. It is not: Ian's account is that fixing this before **broke other, more critical
  functionality**. This record does not name that commit — if it is ever identified it belongs here —
  but the 28 Aug sequence explains why the shape of the change is dangerous rather than the change
  being unlucky. The figure is a proportion whose numerator and denominator are gathered from
  different places and refresh at different moments, and every past attempt to make it report "not
  yet known" disturbed one half of that pair. The symptom moves; what breaks is the progress number
  the user actually reads.

  If it is ever revisited, the bar is: the three mechanisms from 28 Aug held intact (per-file count
  refresh, a floor inside a run, the baseline closing wherever no work is outstanding), and hardware
  verification across a **whole** run rather than its first two seconds — the flash lives in the
  first two seconds, which is exactly the window a quick check watches and a regression hides behind.
- **The Recents card was not missing.** It appeared absent after a Close, which briefly looked like the
  exclusion job being unnecessary — it was an artifact of `install -r` having killed the process and
  rebuilt the task. A normal icon launch followed by Close leaves the card, so the 4 Sept entry stands
  and hiding it is still the open job. Ian's framing of why the card matters: it is the route back in to
  *see the backup actually running*. Reopening does not depend on it — the launcher icon relaunches and
  the tour resumes on the progress card re-attached to the live chain — but that route only works while
  the stored wizard step is 9. After an abort it is 8, which starts the wizard at step 1 instead.

#### Method notes

- **The log buffer on this phone rolls in about four minutes.** Two diagnoses nearly went unmade because
  `logcat -d` no longer held the moment. Capture to a file *before* the test, not after.
- **WorkManager's database is the authority on what was armed**, and it lives in
  `no_backup/androidx.work.workdb` rather than `databases/`. `WorkName` maps a unique name to its current
  spec, so a lost arm is visible as a name still pointing at yesterday's run. Pull it with its `-wal` and
  `-shm`.
- **`getWorkInfosForUniqueWorkFlow` needs a tag to be useful** when one unique name carries two phases,
  which is why the phase tag was added rather than inferring from input data.

---

### 5 Sept 2026 — no card to swipe, and the wizard that could not finish

Built the Recents-card exclusion that was the morning's first job, verified it against the three
questions asked of it, and in doing so found a defect the handoff had introduced earlier the same
day. All measured on the Moto G.

#### What was built

[RecentsCard] takes the app's card out of Recents while the first backup runs — the runtime
`ActivityManager.AppTask.setExcludeFromRecents`, not the manifest attribute, because this is a state
with a beginning and an end. **No card means no swipe target**, which is the only route found that
closes the unsolved case from 4 Sept: a swipe kills the process and Android then withholds the app's
jobs until someone opens it, and a delayed start armed before a swipe never fires at all.

One derived condition drives it — `step == TOTAL_STEPS && !backupComplete` — rather than calls
sprinkled through the paths that start and stop work, because the failure that matters is a flag left
set. `MainActivity` restores the card on every launch as the net beneath that.

#### The three checks, answered

- **Does excluding trip the kill path?** No. The process survived two Closes and a 230-file run with
  the card gone; the only `remove task` kill in the window belonged to OneDrive.
- **Does the flag clear on every exit?** Yes. Restored at the Finish state, and restored on every
  launch regardless.
- **Does the exclusion outlive the process?** **Yes** — and this is the one worth knowing. After
  `am crash`, task #128 still carried `FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS` (`flg=0x10a00000`) with no
  process alive, and Ian confirmed Recents was empty. So the launch-time restore is not
  belt-and-braces, it is the only thing that recovers a crash mid-setup.

Two smaller findings. The task id churns on every relaunch — #126, #127, #128, #129, #134 — because
the launcher will not resume a task it cannot see, so `RecentsCard` applies the flag to every task the
app owns rather than the current one. And an earlier reading that a finished activity leaves no card
was **wrong**: it was an artifact of `install -r` rebuilding the task. A normal launch and Close leaves
a card, so the 4 Sept complaint stands and this work was needed.

#### The defect this surfaced: the wizard could not reach Finish

With every file uploaded and every candidate optimised, the card sat on **"Optimising photos"** with a
`…` ring and no Finish button. The ledger said 0 photo candidates and 0 video candidates; every
optimise pass had `SUCCEEDED`; nothing was queued. **The app was gated shut with no work left to do**,
which is worse than the problem the handoff fixed.

Two causes, both created by moving the handoff into the workers earlier the same day:

- `observeOptimise()` opened with `if (!videoOptimiseRunning && !optimiseRunning) return`. Those flags
  are set only by `applyWizardProxies()` — the card starting a pass itself. Once the **worker** started
  the passes, they were false, the observer returned on its first line, and nothing ever cleared the
  count. It now reads the ledger to decide which phase is live, and terminates when neither is.
- `buildWizardProxyRequest()` returned null on an empty candidate list without marking the phase
  finished. Its video twin had always done so. That asymmetry was harmless while the card was the only
  thing that could drain the list, and fatal once the worker could.

Resuming at step 9 now starts the observer too, which also fixes the frozen "10 of 128" seen earlier
while the worker was demonstrably transcoding.

**Verified in one launch:** `recents card restored` → `hidden` → `optimising finished: nothing eligible
remains` → `restored`, task flags back to `0x10000000`, and the card reading **100% · Finish**.

**The lesson generalises beyond this screen.** Moving work into a worker is only half the change: the
screen that used to own it must be rewritten to *follow* work it did not start. The 4 Sept move of
optimising into `OptimiseWorker` needed the same thing and got it; this one did not, and the gap
appeared as an app that could never be opened.

#### A finding about standby buckets, which is not a defect but shapes the product

A wiped install's first delayed run **did not fire for twelve minutes**. Not Doze — the device was
awake, charging and on Wi-Fi, every constraint satisfied and `Ready: true`. The job record said:

```
Standby bucket: RARE
Time since first force batch attempt: -9m42s
Run time: earliest=-10m8s, latest=none
```

`pm clear` erases usage history, so the app fell into the **RARE** bucket and JobScheduler
**force-batched** its ready work. Opening the app dispatched it in **one second**
(`backup run starting (manual)` at 14:45:23, one second after `recents card restored`), and the job
record then read `Standby bucket: ACTIVE`. A clean causal result.

**Why this matters beyond the test rig:** GallerySync is designed to be set up and never opened, which
is exactly how an app earns the RARE bucket. CLAUDE.md says ongoing sync is "uncapped JobScheduler
work", and that remains true — but **uncapped is not unbatched**. A phone whose owner never opens the
app will have its sync work bundled and delayed by the system. That deserves measuring on its own
rather than being filed as an artifact of the wipe.

#### Method notes

- **`pm clear` is the right reset for this project's test protocol**, and the earlier advice against it
  was wrong. It clears the ledger, the DataStore, the WorkManager database, MSAL's token cache, the
  runtime permissions **and the persisted SAF tree grant** (verified: `persisted grants = 0`), giving a
  genuine first-install run. The objection to it — that a wiped ledger re-uploads everything — does not
  apply, because Ian deletes the OneDrive copies between runs anyway.
- **Ian resets both sides between runs**: local files restored to full size, cloud copies deleted. So a
  ledger row count that jumps between runs is the protocol, not an anomaly to chase, and files proxied
  in an earlier run return as candidates.
- **`am crash <pkg>` is the way to simulate a crash**; `kill -9` is refused and `am kill` declines a
  process running a job. Unlike a force-stop it leaves job dispatch intact, which is what makes it the
  right instrument for "what does a crash leave behind".

---

### 5 Sept 2026 (afternoon) — the standby bucket, and a delay held for 26 minutes

Chasing why a delayed start fired late produced the most consequential finding of the day, and it is
about the platform rather than this app. Moto G, awake, charging, unmetered Wi-Fi throughout.

**A user-scheduled job sat ready and undispatched for 26 minutes 42 seconds.** Armed 15:35:09, due
15:38:04, still not run at 16:04. Every constraint satisfied the whole time, and JobScheduler said so:

```
Standby bucket: RARE
Time since first force batch attempt: -13m30s
Run time: earliest=-14m21s, latest=none
Ready: true (job=true user=true !restricted=true !pending=true !active=true ...)
```

It moved only when Ian opened the app: bucket 40 → 10 (`reason=u-si`) at 16:04:46, and
`backup run starting (manual)` one second later. **Three times today** the same sequence — held in
RARE, dispatched within a second of a human touching the phone.

#### Why it is in RARE at all, which is not the documented path

This device's thresholds, read from `dumpsys usagestats`:

```
mElapsedThresholds = [0, 12h, 24h, 48h, 8 days]
mScreenThresholds  = [0,  0,  1h,  2h,  6h]
                      ACTIVE  WORKING_SET  FREQUENT  RARE  RESTRICTED
```

So the ordinary decay into RARE takes about **48 hours** of disuse. That is not what happened. The
bucket history shows repeated demotions with `reason=s` — **forced by system** — landing seconds after
the app left the foreground:

```
15:34:40  bucket=10  reason=u-si
15:35:36  bucket=40  reason=s      <- six seconds after Close
```

`am set-standby-bucket active` did not survive either: forced to 10 (`reason=f`) at 14:41:34, back to
40 (`reason=s`) at 14:41:35.

**The trigger is idleness, not a timer.** Three observations agree:

| When | What had just happened | Result |
|---|---|---|
| 15:35:36 | Close, with only a pending delay armed and nothing running | RARE after **6 seconds** |
| 16:07 → 16:38 | upload chain running with the app closed | stayed **ACTIVE** for 31 minutes |
| 17:31:55 | last optimise batch finished at 17:31:51 | RARE after **4 seconds** |

So a running chain holds the app at ACTIVE, and the demotion lands within seconds of it having nothing
left to run. The exposure is precisely the gap *between* runs — where a delayed start, the content
trigger and the six-hourly net all live — while a backup already moving is safe. That is the good news
and the bad news in one: the first backup is fine once it starts, and everything that has to *start*
is what gets batched.

#### Why this is a product finding and not a test artifact

`pm clear` drops the app into RARE, so the wiped-install protocol makes it easy to hit. But the deeper
point stands without the wipe: **GallerySync is designed to be set up and never opened, which is
exactly how an app earns this bucket.** CLAUDE.md's "ongoing sync is uncapped JobScheduler work"
remains true, and needs a rider — **uncapped is not unbatched**. 26 minutes is a floor, not a ceiling:
the run ended because we ended it.

What this does not touch: a chain already running was never interrupted. 622 files and 11.4 GB uploaded
with the app closed, at a steady ~4.6 MB/s, then the photo pass (455 proxied) and the video pass
starting themselves — all unattended, all with no Recents card.

**The Albums tab was correct at this scale, which matters more than it sounds.** Ian checked every
album's backup and optimise figures after the run and found them all as expected — with **464 of 622
files proxied**. That is the 4 Sept `ReconciliationRules.tallyAlbum` fix under real load: the reconcile
matches on name *and byte size*, and a proxy is deliberately smaller than the copy OneDrive holds, so
before that fix an optimised album under-reported its own verified count and fell further with every
file optimised. It was fixed against three albums; this run put 464 proxies through it.

**Next measurement, and it wants no attention:** arm a delay, leave the phone genuinely alone
overnight, and read the dispatch time off the ledger in the morning. That gives the ceiling, and
answers the same question for new photos arriving in the steady state.

#### Solved by exemption — measured, same evening

The power allowlist fixes it completely. Same phone, same 230-file fixture, same charger and Wi-Fi,
**Adaptive Battery on in both runs**, one variable changed:

| | Standby bucket | Delay due | Run started | Late by |
|---|---|---|---|---|
| Baseline, afternoon | RARE (40) | 15:38:04 | never, until the app was opened at 16:04:46 | **26m42s+** |
| Exempted, evening | EXEMPTED (5) | 19:18:33 | `19:18:33.483` | **0.5s** |

The exemption came from `dumpsys deviceidle whitelist +com.gallery.sync`, which is precisely what
`REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` grants in production — so this measures the shippable lever
rather than a debug switch. The bucket went to 5 with `reason=d` at 19:11:08 and **never left it**,
where an unexempted app was demoted to RARE within six seconds of Close three times that afternoon.

Note what this does *not* need: Adaptive Battery stayed on, because it is on by default for every user
and no product can ask them to turn a system-wide battery feature off.

**One device-specific finding that shapes how we would ask.** This Moto has **no per-app
"Unrestricted" battery option** — only an "Allow background usage" checkbox, already ticked, and a
phone-wide Adaptive Battery toggle. So the vendor UI does not expose the allowlist the way stock
Android's three-way battery setting does, and pointing users at "set the app to Unrestricted" is advice
that does not match what they will see on this handset.

#### The remedies, and what each costs

- **The user setting the app to Unrestricted by hand** costs nothing and touches no Play policy. Worth
  testing whether it stops the `reason=s` demotions — if it does, this is vendor battery management
  and a documented setup step is a real answer.
- **`REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`** — the app raising the exemption dialog itself — is a
  restricted-use permission with a published acceptable-use list, and a photo backup app is not
  obviously on it. That is a Play-listing decision and a CLAUDE.md escalation, not a code change.
- **`ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS`**, which opens the battery settings screen and lets
  the user do it, needs no permission and no declaration. The middle route most sync apps take.

#### Instrument notes, both earned today

- **A file count and a completed-byte total both flatline during a large file**, because each only
  moves when a file finishes. A 1.5 GB upload froze both for six minutes on a perfectly healthy
  transfer, and that is what "stuck at 9 of 622" was. The honest live signal is WorkManager's own
  `WorkProgress` row, which carries the in-flight file name, `currentSent` and a percent — read it from
  `no_backup/androidx.work.workdb` and decode the `abef` blob.
- **`dumpsys usagestats | grep STANDBY_BUCKET_CHANGED package=<pkg>`** gives the bucket history with
  reasons, which is what separates a timeout demotion (`t`) from a system-forced one (`s`) from usage
  (`u-*`). Without the reason code this looks like ordinary standby and gets dismissed.

---

### 5 Sept 2026 (evening) — what the bucket actually costs, and a loop the app inflicts on itself

Research against the platform documentation, after the exemption result, plus one defect the same run
surfaced.

#### The quota table, which reframes the whole problem

Per-bucket limits, from [Power management resource limits](https://developer.android.com/topic/performance/power/power-details):

| Bucket | Regular jobs | Alarms |
|---|---|---|
| Active | 20 min per rolling hour | no limit |
| Working set | 10 min per 4 hours | 10/hour |
| Frequent | 10 min per 12 hours | 2/hour |
| **Rare** | **10 min per rolling 24 hours** | **1/hour** |
| Restricted | once daily, 10 min | 1/day |

Ten minutes of job runtime a day would make this app impossible — **except that charging supersedes
it**: *"a device in the charging state is given unrestricted resource access regardless of its app
standby bucket"*, with no execution limits (outside the restricted bucket) and no network restrictions.

**So today's 26-minute hold was not the quota.** The phone was charging throughout. What we measured is
*dispatch latency* — the force-batching of a ready job — which charging does **not** relax. Two separate
mechanisms, and only one of them bites while plugged in. That narrows the problem sharply: the first
backup already requires charging by default, so its throughput is safe once it starts. Getting it to
*start* is the whole issue.

#### Routes that avoid the restricted permission

- **`AlarmManager.setAndAllowWhileIdle()` for the delayed start.** No permission for the inexact
  variant, fires in Doze, and even in RARE an app gets one alarm per hour — unlimited while charging.
  It replaces the one mechanism measured unreliable, and it is what the Doze/Standby guide recommends.
- **User-initiated data transfer jobs** (`setUserInitiated(true)`, API 34+) for the first backup and
  Sync now. **Exempt from the ordinary job quotas**, and `RUN_USER_INITIATED_JOBS` is a normal
  permission rather than a restricted-use one. The cost is real: **no Jetpack library supports them**,
  so it means JobScheduler directly beside our WorkManager engine, plus a fallback path for minSdk 26.
- **FCM high-priority messages** are Google's first recommendation and are not available to us — there
  is no server in this product.

**On `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`:** the acceptable-use table's *task automation* row reads
"App's core function is scheduling automated actions, such as for instant messaging, voice calling, or
**new photo management**", which is closer to this app than first assumed. It is an argument a reviewer
might accept, not a certainty, and not one to discover the answer to on a first submission. Ian's
instruction, 5 Sept: find workarounds rather than request the exemption.

#### Defect: optimising triggers no-op backup runs

Observed during the exemption run, once a minute through the whole optimise phase:

```
19:34:06  backup run starting
19:34:06  scanAll: 230 items across 4 albums
19:34:06  refreshLedger: 190 files seen
19:34:06  backup run finished: 0 uploaded, 0 already there, 0 remaining
```

**The app is reacting to its own writes.** `ProxyApplier` rewrites a file, MediaStore changes, the
content trigger fires, `BackupWorker` wakes, scans 230 items, finds nothing to upload, re-arms the
trigger — and the next batch of proxies fires it again. No network, about 600 ms a time.

Harmless while charging. On battery in RARE it is not: those wakeups would spend the app's entire
ten-minute daily job budget on scans that upload nothing, so **the app would starve itself precisely
while doing the work that saves the user space**. Worth fixing at the trigger — either suppressing the
content trigger while an optimise chain is live, or recognising the URIs we just wrote — rather than
anywhere downstream.

---

### 6 Sept 2026 (overnight) — the first clean unattended delayed start

The counterpart to yesterday's 26-minute hold: the same mechanism, left alone overnight on the Moto G,
and it fired on time and ran the whole way through with nobody watching. Charging throughout, and no
reboot — `uptime` 1d 23:57 when this was read.

| | |
|---|---|
| Delay armed | 00:44:34, `first_backup_delay_millis` = 4 h |
| Due | 04:44:34 |
| App last on screen | 00:45:00, and **never reopened** — usagestats shows no `ACTIVITY_RESUMED` until after the run |
| First upload completed | 04:50:34 |
| Last upload completed | 04:59:28 |

**Under six minutes from due to the first completed upload**, and that figure includes worker start plus
the first file's transfer, so the dispatch latency itself is smaller than it. Against 26 minutes 42
seconds yesterday afternoon, and against three separate holds that only moved when Ian touched the
phone. One night is one sample and the bucket demotion is idleness-triggered rather than scheduled, so
this does not retire the finding above — but the delayed start now has a clean run to its name.

**The backup**: 303 files, 2.58 GB, in 8 minutes 54 seconds. No errors, `attemptCount` 0 on every row,
no retries. All 303 carry a `remoteItemId` *and* `remoteSizeBytes == sizeBytes` — the `verifiedInCloud()`
bar met for every file, not merely for most of them.

**Optimising followed on its own**, 04:59:28 → 05:28:08, 26 chained `OptimiseWorker` runs with the app
closed: 255 proxied, 48 recorded not-worth-proxying. Local footprint 2.58 GB → 0.73 GB, and `du` agrees
independently at 748 MB across `DCIM`. About 1.85 GB genuinely reclaimed — proxying shortens in place,
so unlike a trashed file that space is back immediately.

Nothing was removed: all six albums read `OFF`, and no row has `localMissingSinceEpochMillis` set.

#### Reading a run whose logs are gone

The logcat buffer held nothing from the run, for a reason worth knowing on its own — see the buffer
note below. The whole reconstruction came from state on disk instead, which is worth recording as a
method:

- **`backup_entries`, read with its `-wal` and `-shm`**, gives the minute-by-minute upload histogram
  from `uploadedAtEpochMillis`, the error and attempt columns, and the cloud-verification check.
- **`files/datastore/backup_settings.preferences_pb`** holds `first_backup_start_at` and
  `first_backup_delay_millis`, which is the only surviving record of *when the delay was due*.
  WorkManager had already pruned the delayed-start `WorkSpec`.
- **`dumpsys usagestats`** fixes when the app was last on screen, which is what makes "unattended"
  a measurement rather than an assumption.

#### Two things this run did not settle

- **The no-op backup loop** from the 5 Sept evening entry does not appear in `androidx.work.workdb` —
  but unique work keeps only its latest row, so absence there is not evidence of absence. It needs a
  live logcat during an optimise pass.
- **28 videos in `Funny stuff`, up to 43 MB each, came back `NotWorthwhile`** while 31 videos elsewhere
  proxied down from as little as 3 MB. The 20 photos skipped alongside them average 0.5 MB and are
  obviously correct. **Answered the same day — see the entry below.**

#### The reconcile, watched by eye at 12:14

Ian opened the app after the run and **watched the verification figures update on screen** — the check
this entry had so far only inferred from the ledger. `album_cloud_status`, written 12:14:51–12:14:56:

| Album | Verified | Missing | Could not check |
|---|---|---|---|
| BudgetMixed | 50 | 0 | 0 |
| BudgetPhotos | 100 | 0 | 0 |
| Camping | 39 | 0 | 0 |
| Car Show | 34 | 0 | 0 |
| Funny stuff | 75 | 0 | 0 |
| PauseTest | 5 | 0 | 0 |

303 of 303, nothing missing, nothing unverifiable — and **255 of the 303 are proxies**, deliberately
smaller than the copies OneDrive holds. That is the 4 Sept `ReconciliationRules.tallyAlbum` fix under a
*majority*-proxied library, a harder case than the 464-of-622 run on 5 Sept, and this time watched
happening rather than read afterwards.

#### Instrument: this device's logcat holds about 97 seconds

Measured 6 Sept, and it invalidates a plan written earlier in this same entry.

```
main: ring buffer is 256 KiB (242 KiB consumed, 877 KiB readable)
```

At 12:22 the main buffer spanned **12:20:25 → 12:22:02**. Ordinary UI activity — `BufferQueueProducer`
and `SurfaceFlinger` chatter — churns it in under two minutes, so the 12:14 reconcile had already aged
out seven minutes later, and the overnight run never stood a chance.

**This is not a logging defect.** The installed build is `DEBUGGABLE` and `isLoggable` never gates
INFO, so `Logger.i` was emitting the whole time. The lines were simply evicted.

The consequence: **"catch it in a live logcat" is not a plan on this device** unless logcat is already
streaming to a file before the run starts. Raise the buffer first — `adb -s ZT422CTZQV logcat -G 16M`
per buffer, until reboot, or `setprop persist.logd.size 16M` to persist it — or redirect to a file and
accept that a rolled buffer is the default outcome otherwise. The no-op-backup-loop question from the
5 Sept evening entry is blocked on exactly this.

---

### 6 Sept 2026 — why 28 clips were "not worth proxying", and why the answer is bitrate

Ian, looking at the overnight figures: *"I can not tell what the threshold is for when the app
optimizes or doesn't for the video files."* Neither could the ledger, so this is what it turned out to
be. **The intuitive threshold — file size — plays no part whatsoever.**

#### There are two thresholds, and they sit either side of the encode

**Before decoding**, `VideoTranscoder.transcode()` refuses on the short edge:

```kotlin
if (source.shortSide in 1..quality.targetShortSide)   // 480 at the default High
    return NotWorthwhile("already ${w}x${h}, at or under the target")
```

**After encoding**, `validate()` throws the output away if `created.sizeBytes >= source.sizeBytes`.

Of the 28: **4 were refused on the short edge** (218, 346, 368, 384). The other **24 got a full
transcode and then had it discarded**.

#### The bitrate table, which is the actual finding

Every one of the 24 was already between **0.20 and 2.98 Mbps**, most of them under 1.5:

| File | WxH | Short | MB | Secs | Mbps |
|---|---|---|---|---|---|
| Screen_Recording_20260421_171340_YouTube.mp4 | 1074x832 | 832 | 43.1 | 313 | 1.16 |
| Screen_Recording_20260521_233124_YouTube.mp4 | 1080x1298 | 1080 | 35.1 | 199 | 1.48 |
| Screen_Recording_20260125_145925_Instagram.mp4 | 694x686 | 686 | 29.8 | 178 | 1.41 |
| Screen_Recording_20251201_144658_YouTube.mp4 | 1080x620 | 620 | 21.6 | 135 | 1.34 |
| 75be685608807b53e5a56521ff96d260.mp4 | 576x1024 | 576 | 11.7 | 170 | 0.58 |
| ed5d6e4ca70a2df30667360f2d2d7052.mp4 | 576x694 | 576 | 0.5 | 20 | 0.20 |

The app sets **no explicit bitrate** — `Transformer` is built with a MIME type and a `Presentation`,
and Media3's `DefaultEncoderFactory` chooses the rate. For a 480p target that lands in the same
1.5–2 Mbps band these sources already occupy, and 576 → 480 on the short edge sheds only about 30% of
the pixels. There is nothing to win, and the validate check correctly says so.

**So the 43 MB clip was skipped for being long, not for being big** — 313 seconds at 1.16 Mbps. The 31
clips that did proxy were ordinary phone video at a camera's bitrate. **Size is a red herring; the
axis is bitrate, and secondarily the short edge.**

#### Ruled out rather than assumed

- **"Already a proxy."** Every MP4 in the folder was grepped for the `GallerySync proxy` marker.
  Exactly 18 carry it, and they are precisely the 18 that transcoded — so none of the 28 was a
  leftover from an earlier run, and the fixture reset is not implicated.
- **"This phone cannot decode it."** All 24 have even dimensions and sit far inside the software AVC
  decoder's 1920x1088, and `VideoCapability.canDecode` polls every decoder rather than the default.

#### What is inference, and what would settle it

The bitrate arithmetic is strong but it is **not the log line**. `VideoOptimiser` records the reason at
`Logger.d`, and this device's 97-second logcat buffer had evicted it hours before anyone looked. To
settle it: raise the buffer, clear `isProxySkipped` on two or three of these rows, and run the pass
again — the reason string is then read directly rather than reconstructed.

#### The product question it raises

**24 full transcodes were spent to produce nothing.** Self-limiting, because `markProxySkipped` is
permanent and they are never offered again — but the class comment says *"Every refusal is decided
before a frame is decoded. Starting a transcode is the expensive act and all of these answers are
cheap."* A source-bitrate check belongs in that same set of cheap answers, since duration and size are
both already in hand before anything is decoded. Worth weighing against the risk of refusing a clip
that would in fact have shrunk.

Related: the wizard's `approximateSavingPercent = 85` is measured on camera-bitrate footage. A library
of social-media downloads and screen recordings will not come close to it, and today it silently did
not.

---

### 6 Sept 2026 (afternoon) — the no-op loop caught in the act, and a card that promised 60 MB of 2.26 GB

A full wizard run on the Moto G with the logcat buffer raised first, which is the only reason any of
this is readable. `pm clear`, local files restored to full size, **OneDrive deliberately left intact**
so the reconcile would match everything and the run could reach optimising without uploading anything.
It worked: 8 files uploaded, 378 already there.

#### The instrument fix that made the rest possible

`adb logcat -G 16M` before starting. At the stock 256 KiB nothing above survives two minutes; at 16 MiB
a 36-minute run fits with room to spare. **Raise the buffer before any run whose logs matter** — it is
one command and it is the difference between measuring and inferring.

#### 1. The no-op backup loop, confirmed and measured

The 5 Sept hypothesis was right, and the shape is worse than "about once a minute". **20 no-op runs
across the 36-minute optimise phase**, each a full scan that uploads nothing:

```
13:34:19.890  backup run starting
13:34:20.010  scanAll: 386 items across 6 albums within 1 granted folders
13:34:20.164  refreshLedger: 135 files seen
13:34:20.171  backup run finished: 0 uploaded, 0 already there, 0 failed, 0 remaining
```

**The rate tracks the rate of proxy writes, not the clock.** Six of the twenty landed inside the
85-second photo pass — one every 14 seconds — because photo proxying rewrites 60 files in about 20
seconds. The video pass writes 3 files per 40 seconds and triggered them far more sparsely:

| Phase | Duration | Files rewritten | No-op runs |
|---|---|---|---|
| Photos | 13:26:04 – 13:27:29 | 233 | 6 |
| Video | 13:27:29 – 14:03:40 | 49 | 14 |

That matters for the fix: **the trigger fires per batch of writes, so the cost scales with how fast the
app optimises**, and the photo pass is the fast one. Charging makes it free; in RARE on battery it is
the app spending its ten-minute daily budget on scans that do nothing.

#### 2. The video refusals, measured rather than inferred

The morning's bitrate arithmetic (see the entry above) predicted that "transcode came out no smaller"
would dominate. The log agrees, and this is now the reason string rather than a reconstruction:

| Reason | Count |
|---|---|
| `transcode came out no smaller` | **52** |
| `already ${w}x${h}, at or under the target` | 9 |
| `already a proxy` | 3 |
| **Total refused** | **64** |
| Transcoded successfully | 49 |

`0 failed` across 38 batches. Where a clip has bitrate to give up the saving is large — 56.6 MB → 8.0 MB,
50.7 MB → 6.4 MB, 1920x1080 → 854x480 — and where it has not, 52 full transcodes ran and were thrown
away. **The morning's proposal stands: a source-bitrate pre-check would refuse those before the encode
rather than after**, which is what the class comment already claims the refusals do.

#### 3. Defect: the Step 7 estimate describes the wrong population

Ian, mid-wizard: *"step 7 lists no information for Photo optimization values."* It is not a rendering
bug. `OptimizationContent` computes its figures from `result.photosOutstanding` and
`result.videosOutstanding` — files **not yet in OneDrive** — which `ReconcileScreen` documents as *"what
`BACK_UP_AND_OPTIMISE_NEW` would actually touch"*. That is option 3's scope, and the card is shown for
every choice.

Under option 2, `BACK_UP_AND_FREE_SPACE`, optimising runs `proxyCandidatesAll` and
`videoOptimiseCandidatesAll`, both of which ignore whether this run uploaded the file. So on a library
already in the cloud:

| | Promised on Step 7 | Actually reclaimed |
|---|---|---|
| Photos | *no line at all* | **714 MB** (233 proxied, 40 refused) |
| Video | 60 MB | **1,602 MB** (49 optimised, 64 refused) |
| **Total** | **60 MB** | **2,316 MB — 2.26 GB** |

Confirmed on the device afterwards: ledger 386 rows, all uploaded and verified, 3.51 GB → 1.25 GB, and
`du` independently at 1.2G.

**Off by 38x, on the option whose entire selling point is space saved, in the ordinary case of
installing on a phone already backed up.** The photo line vanishes rather than reading zero, which is
the specific thing `SetupTour.kt:1218` says the card must never do: *"the one thing it must never do is
leave the space blank and let the user decide for themselves what a switch with no figure under it
means."* The total falls through to *"Everything on this phone is already in OneDrive, so there is
nothing new to optimise yet"* — which is true of uploads and false of optimising.

The fix is to pick the population from the `LibraryChoice`: outstanding for option 3, everything
eligible for option 2. Not yet built.

---

### 6 Sept 2026 (evening) — two fixes, and a third that the data refused

Built after the afternoon run, in the order the findings justified rather than the order they were
found.

#### Fixed: Step 7 described a population the worker does not use

`OptimizationContent` read `photosOutstanding` / `videosOutstanding` for every choice. That is
`BACK_UP_AND_OPTIMISE_NEW`'s population, and the card is shown for all four.

- The estimate now reads `CloudReconciliation.photos` / `.videos` — which already documented itself as
  *"Every photo in scope, backed up or not — what proxying could act on"*, so the right number was
  there to be asked for.

  **Corrected the same evening, and this half was wrong when first written.** It applied the whole-
  library population to **#3 as well as #2**, on the strength of the two behaving identically in the
  code. They are not supposed to: Ian's design, recorded above under Area 1 on 29 Aug 2026, is that #3
  optimises only *"newly backed up, not already in cloud"*. So `photosOutstanding` was the correct
  population for #3 all along, and changing it was a regression introduced while fixing #2. The reason
  the code looked identical is a separate defect — see the entry below.
- **`LibraryChoice.optimisesAtInstall`** replaces three copies of `mode?.proxiesPhotos == true`, so the
  screen that estimates the saving and the worker that performs it read one predicate. A unit test
  asserts the two agree for every choice, because nothing caught them disagreeing for a week.
- Gate 2's #1 and #4 do not optimise at install at all. They now say so — *"These settings apply to any
  album you later set to Sync"* — rather than quoting a figure computed from a population they will
  never touch.
- `tour_optimise_nothing` was reworded. It read *"Everything on this phone is already in OneDrive, so
  there is nothing new to optimise yet"*, which is true of uploads and false of optimising — a
  fully-backed-up library is the best placed to optimise, not the worst.

#### Fixed: the app no longer scans the library because it rewrote it

`BackupWorker` declines a run when `triggeredContentUris`/`triggeredContentAuthorities` is non-empty
*and* the optimise chain is live — the wake it caused itself. Authorities as well as URIs, because
Android reports the authority alone when a trigger's URI list overflows, which a burst of proxy writes
is exactly what would do.

**Declined rather than disarmed**, and that choice is the point. Suppressing the trigger for the
duration of the chain needs something to re-arm it afterwards, and a chain that dies would leave the
app blind to new photos until the six-hourly net noticed. This keeps the watch armed exactly as before
and drops only the scan. Liveness is asked of WorkManager through a new `BackupScheduling.
optimiseChainLive`, a sibling of `manualRunLive`, rather than a flag that a killed process would leave
stuck — a stuck flag here would silence the content trigger permanently.

#### Not built: the video bitrate pre-check, because the measurement says no

The morning's proposal was to refuse a low-bitrate clip *before* spending the transcode. Tested against
the afternoon's 54 clips, and **the groups overlap on every discriminant tried**:

| | Refused (came out no smaller) | Shrank |
|---|---|---|
| Source bitrate | 0.20 – **2.98** Mbps | **1.95** – 18.35 Mbps |
| Source ÷ estimated 480p target | median 1.59, **max 6.69** | **min 3.35**, median 13.17 |

The medians are far apart, so a cut would catch most of the waste — but there is a genuine band where
both outcomes occur. A cut placed safely below the lowest success (3.35) still catches about 21 of 24,
which is tempting until two things are weighed:

- **The target-bitrate model is unvalidated.** It assumes 30fps and guesses at
  `DefaultEncoderFactory`'s heuristic rather than reading it. A 60fps clip is wrong by a factor of two,
  which is wider than the whole overlap band.
- **A wrong refusal is permanent.** `NotWorthwhile` writes `isProxySkipped`, and the clip is never
  offered again. That is a one-way door on a file the user wanted shrunk, spent to save a transcode.

**What would settle it:** read Media3's actual bitrate heuristic and the per-clip frame rate rather
than assuming either, then test the rule against a second library. Until then the waste stands — 52
transcodes per install on a library like this one, bounded, self-limiting, and only at install.

#### Verified on hardware, both of them

**The trigger guard**, on the 15:12 run — the same phases that produced twenty no-op scans in the
afternoon:

| Photo pass | Afternoon (before) | After |
|---|---|---|
| Duration | 85 s | 81 s |
| No-op scans | **6** | **0** |
| Declines logged | — | **6** |

Across the whole run: **16 declines, 0 no-op scans, 0 files uploaded.** One-for-one — the same triggers
arrive and each is declined instead of scanning the library, with the watch still armed. That is the
design intent, measured rather than argued.

**The Step 7 estimate**, captured on a restored fixture (311 files, 2.6 GB local, 7 outstanding):

| Line | Predicted | Actual | Old build would have shown |
|---|---|---|---|
| Estimated photo savings | ~702 MB | **718 MB** | ~16 MB |
| Estimated video savings | ~1.6 GB | **1.6 GB** | *no line at all* |
| Total estimated savings | ~2.2 GB | **2.3 GB** | ~16 MB |

The video line is the unambiguous half: outstanding videos were zero, so the old code had nothing to
draw it from. Screenshot taken at Step 7 before any upload, so nothing about the run could have
influenced it.

**One instrument note.** The phone dropped its adb connection mid-check and the advertised mDNS port
was stale — `adb connect` refused on the port `adb mdns services` was still publishing. Toggling
Wireless debugging off and on restored it, and the device then reconnected on both transports by
itself. CLAUDE.md already warns the port changes on every toggle; what this adds is that **the mDNS
record can outlive the port**, so a refused connection on a freshly-discovered address is not evidence
the phone is gone.

---

### 6 Sept 2026 (late) — MediaStore lags a bulk copy, and a reconcile taken too early is wrong

Found by accident while confirming the Step 7 fix, and it bears on the whole day's method.

The wizard sat at Step 7 across a phone restart. Nothing was touched but the power button, and the
reconcile came back different:

| | Files scanned | Already in OneDrive | Outstanding |
|---|---|---|---|
| Before the reboot | 311 | 304 | **7** |
| After the reboot | **314** | 250 | **64** |

**314 is the true count** — Ian, checking local `DCIM` directly. So the pre-reboot scan was three files
short and, on the evidence of the outstanding column, holding stale sizes for many more. Both figures
came from the same app on the same library minutes apart; the earlier one was simply asking a
MediaStore that had not caught up with a bulk file copy.

**Why it matters beyond this run.** Every fixture test in this project restores files by copying them
in, then immediately clears the app and reads what the reconcile says. That is precisely the window
where MediaStore is stale, and the reconcile compares **name and size** against OneDrive — so stale
sizes produce wrong answers in both directions: files reported missing that are present, and files
reported present that no longer match. The "a clean reinstall re-uploads everything" defect turns on
exactly this comparison, so some of its behaviour may be measurement rather than mechanism.

**What to do about it:** after restoring a fixture, force the index and check the count before trusting
any reconcile — the file count against `ls`/`du` is the cheap tell, as it was here. A figure that
disagrees with the disk is the instrument, not the app. This is the same lesson as the 28 Aug entry
about `content query` returning three different row counts in one session, arriving from a new
direction: **when an instrument disagrees with the evidence, doubt the instrument.**

**It does not disturb the Step 7 verification.** That screenshot was taken against the 311-file scan,
and after the reboot — with outstanding swinging 7 → 64 — the card still read 718 MB / 1.6 GB / 2.3 GB,
unchanged. Which is the fix's whole point: the estimate now follows the population that will be
optimised, not the one that will be uploaded, so it is immune to exactly this swing. The old code would
have lurched with it.

**Answered the same evening, and it was not the instrument.** Ian: *"There were optimized images
copied in places they were not supposed to be."* The library genuinely held stray proxies, so **64
outstanding was the accurate figure** and the earlier 7 was the wrong one. The scan being three files
short is still real evidence that MediaStore lags a bulk copy, and the guard above still stands — but
the swing itself was the app correctly reporting a messy fixture, not a mis-measurement.

**Which is the actual lesson, and Ian stated it as a standing instruction:** *"It is worth noting to
ask me if I did anything if there is a size / # of files issue — before you assume the code is wrong."*
Twice in one day a state change was explained with a code path when the cause was him — album modes
switching to Sync, and this. He restores fixtures, deletes strays and re-downloads from OneDrive
between runs, so **the library is not a controlled variable unless he says it is**. One question costs
a sentence; the wrong hypothesis costs an afternoon.

---

### 6 Sept 2026 (night) — option 3 has not existed since 31 August

Found by Ian reading the delay card after choosing #1: *"why the savings if I'm just uploading and not
optimizing?"* The card was the symptom; the cause is older and larger.

#### What Gate 2 is supposed to do — Ian, restated 6 Sept

| | Verify local against OneDrive | Upload what is missing | Optimise |
|---|---|---|---|
| **#2** `BACK_UP_AND_FREE_SPACE` | yes | yes | **all files on the phone** |
| **#3** `BACK_UP_AND_OPTIMISE_NEW` | yes | yes | **only what this run actually backed up** |

This is not new. It is the Area 1 design recorded here on 29 Aug 2026, and Ian had tested both options
and *seen the differing file counts* — in Step 7's output and on the device after each run.

#### Why they became identical, traced

`e6a0794`, **31 Aug 2026**, *"replace wizard with 9-step tooltip tour overlay"*, swapped `MainActivity`
from `SetupWizardScreen` to the new `SetupTour`. It did not carry over the `applyLibraryChoice()` call,
and git's pickaxe confirms `SetupTour.kt` has never contained that string.

`ApplyLibraryChoice` is the only caller of `settings.setOptimiseCutoff(...)`, and the cutoff is the only
thing separating #3 from #2 — its own comment says so. Orphaning it meant **the cutoff was never
written**, so `optimiseCutoffEpochMillis` stayed at `EVERYTHING` for every install from that day. And
even had it been written, `proxyCandidatesAll` and `videoOptimiseCandidatesAll` took no cutoff
parameter at all.

**Why it went unseen for six days.** The class was left in the tree rather than deleted, so nothing
looked amputated. A dropped call throws nothing. The visible half of Gate 2 kept working — four
options, `library_choice` persisted, `WizardBulkOptimise` still reading the choice to decide *whether*
to optimise. Only the *scope* was lost. And on a fresh library with an empty cloud, #2 and #3 genuinely
are identical, which is the case a wipe-and-reinstall protocol tests most often. The commit's own note
reads *"Verified on Moto G in both light and dark mode"* — a visual check cannot catch a behaviour that
is never invoked.

#### The fix

- **`proxyCandidatesAll` and `videoOptimiseCandidatesAll`** carry
  `AND (:cutoffMillis = 0 OR uploadedAtEpochMillis >= :cutoffMillis)` — the same clause and sentinel the
  ongoing video query already used, so the two cannot drift.
- **`ProxyApplier` and `VideoOptimiser` read the cutoff themselves.** Thirteen call sites between them;
  a parameter each would have to remember is one that a call site eventually gets wrong, which is the
  failure this area has already had twice.
- **`setLibraryChoice` writes the cutoff**, recorded when Gate 2 is answered. Nothing uploads between
  then and the first batch, and answering again with a different option must produce the new cutoff
  rather than leave the old one standing.
- **Both wizard cards pick their population from the choice**: whole library for #2, outstanding for #3,
  nothing at all for #1 and #4.

**The cutoff rather than the reconcile's outstanding set**, deliberately. Ian's words are *"only
optimizes files that were actually backed up in the previous step"* — `uploadedAtEpochMillis >= cutoff`
is what this run actually uploaded, where the outstanding set would also include files that were
missing and then failed to upload.

**No album mode is written anywhere in this.** Only the cutoff was rescued out of `ApplyLibraryChoice`;
the bulk mode write stays dead, which is the requirement.

#### The dead code is a trap, not just clutter

`ApplyLibraryChoice.kt`, `SetupWizardScreen.kt` and `ReconcileScreen.kt` have been unreachable since
31 Aug — `SetupWizardScreen.kt:385` even says *"only in ReconcileScreen, which nothing renders"*. They
still read as the live wizard. `ApplyLibraryChoice` is a class named for the install choice whose body
sets every album's mode in bulk, and **it is what makes agents keep concluding the wizard writes album
modes** — the error CLAUDE.md calls wrong by construction, and which recurred in this very session at
13:00. Ian, on why that rule is in the always-loaded file: *"I had to repeat and repeat those
instructions to you as you over and over tried to make the wizard change album modes."* Deleting the
three files removes the temptation permanently. Not yet done.

#### The install choice governs the one-time pass and nothing after it

Ian, 6 Sept 2026: *"Once this ONE TIME backup has been completed the user then sets their preference
for how the backup/sync works going forward."*

So the cutoff belongs to the wizard's pass alone. `VideoOptimiser.run` — the **ongoing** path — was
reading `optimiseCutoffEpochMillis` too, which meant a #3 install left videos already in OneDrive
permanently exempt from optimising, months later, under a choice made once at install. It now passes
`OptimiseCutoff.EVERYTHING`, and the cutoff is read only by the two wizard queries.

Note which half was the defect. The photo query never read the cutoff, and **that was correct** — the
asymmetry was video reaching for something it should not have had, not photos missing something they
needed. An earlier reading of this entry had it the wrong way round.

This is the three-areas rule in the direction easiest to miss: Area 1 must not reach into Area 2's
*behaviour* any more than it may write Area 2's settings.

#### Age belongs after the wizard — and photos have no age, by decision

Ian, 6 Sept 2026: *"The initial Wizard should NOT have any age predicate for either photos or videos.
There should be a setting in the app (after the wizard) to dictate how long a **video** can age before
being available for backing up and optimizing."*

**The wizard was already right** — `proxyCandidatesAll` and `videoOptimiseCandidatesAll` carry no age
clause, and must not gain one. The install pass acts on the library as it stands.

**The ongoing video age works and always has.**

**Photos have no age, and that is a decision from 19 Aug 2026** recorded in TASK-011:

> only the Sync age is limited to video. Photos are proxied whatever their age, because a 2048px proxy
> leaves the photo in the gallery and costs an edit nothing until the export. **There is no photo age
> setting and none is wanted.**

**A wrong fix, made and reverted the same evening.** Seeing `photoOptimiseAge` on the Settings screen
doing nothing, I wired it into `proxyCandidates` — implementing a feature Ian had rejected three weeks
earlier. Reverted in the next commit. The lesson is the order of the question: *should this control
exist?* comes before *why doesn't it work?*, and TASK-011 answered it in one grep.

**What is actually wrong is the control.** `photoOptimiseAge` entered the code on 28 Aug (`54f6124`),
reached the Settings screen on 30 Aug (`f650536`), and was written into the Area 2 tree in this file on
29 Aug (`c0c9b81`) — all after the 19 Aug decision that none is wanted. It should be removed from
`BackupSettings`, from `SettingsScreen`, and from the tree above. **Done 15 Sept 2026** — TASK-022
Part B.


### 7 Sept 2026 — TASK-022 Part A, and a fence checked before it came down

**Three orphaned files deleted, plus a fourth found by asking why the fence was there.**
`ApplyLibraryChoice.kt`, `SetupWizardScreen.kt` and `ReconcileScreen.kt` had been unreachable since
`e6a0794` (31 Aug). `SetupTopic.kt` went with them — 1,236 deletions against 14 insertions.

**Chesterton's Fence, applied at Ian's instruction, changed the scope.** The spec called this a pure
removal. It was not. `SetupWizardScreen.kt:208` was the **only caller anywhere** of
`acknowledgeTopic()`, and behind that one line sat a fully-built chain nothing else touched:
`ReconcileViewModel.acknowledgeTopic` and its `acknowledgedTopics` state, `BackupSettings.acknowledgeTopic`,
`KEY_ACKNOWLEDGED_TOPICS`, and the 26-value `SetupTopic` enum whose KDoc claimed three consumers — the
first-run bubbles, the Help screen, and a just-in-time prompt. Only the first was ever wired, and it was
the dead one.

Ian retired both remaining reasons: **the (?) tooltips were an approved decision, replacing the Help
menu** (so TASK-017 is superseded), and **the just-in-time prompt was premised on a Skip button the tour
no longer has**. The code agreed — `R.string.wizard_skip` was referenced by exactly one file, the dead
wizard. Only then was the chain removed.

**What deleting it does not touch.** The Archive confirmation is independent: `ArchiveConfirmDialog` at
`BackupScreen.kt:745`, raised unconditionally, never consulting the acknowledgement record. CLAUDE.md's
consent rule is satisfied without any of the deleted code. TASK-014's stronger precondition — a destructive
mode cannot be chosen before its explanation is acknowledged — is now permanently unenforceable, and had
been unenforced in practice since 31 Aug.

**One substantive correction fell out of it.** `MediaAge.kt:52` claimed Gate 2's *"Back up and free space"*
maps to `AlbumMode.SYNC` and that `ApplyLibraryChoice` *"applies it to every album at once, with REPLACE"*.
Wrong twice: the class is gone, and no install choice writes an album mode. It now attributes the
whole-library reach to the optimise cutoff, which is what actually causes it.

**`setOptimiseCutoff` survived, deliberately.** `ReconcileViewModel.setLibraryChoice` writes it one line
from a deletion, and it is the only thing separating Gate 2 #3 from #2 — the defect fixed on 6 Sept.

**Verified.** `compileDebugKotlin` and `compileDebugUnitTestKotlin` pass including Hilt/KSP codegen, which
was the real risk in a constructor change. Unit suite 315/315, 0 failures. `assembleDebug` packaged.
Clean install on the Moto G at 13:14:55 (`firstInstallTime` == `lastUpdateTime`, so genuinely fresh), crash
buffer empty on launch, wizard entering at step 1. **Ian walked the wizard on the device and reported it
good.** The Gate 2 #2-vs-#3 distinction was not separately re-measured in that pass.

**27 strings are now orphaned** — the 26 `topic_*` and `wizard_skip`. Left in `strings.xml` by decision:
unused string resources are inert, and Ian ruled against rehoming `topic_promise_body`'s prose absent any
consequence to leaving it.


### 7 Sept 2026 (afternoon) — one folder, two albums, and a default that rewrote files unasked

Both found by Ian on the Moto G during wizard testing, after the clean install at 13:14.

**`Camera` and `camera` are the same directory.** Same inode — `45845` for both — and identical
listings of the same eight files. Android's emulated storage is case-insensitive but case-preserving;
MediaStore records the literal path the writing app supplied and derives `BUCKET_DISPLAY_NAME` from
it. The fixture folder was made as `camera`; the Moto's camera app writes `DCIM/Camera`. Ids 5188–5191
carry bucket `camera`, ids 5192–5194 carry `Camera`, and **`bucket_id` is `-1739773001` for every one
of them**, because Android derives that from the lower-cased path.

GallerySync keys albums on the display name (`MediaScanner.kt:165`), and `album_preferences` is
`PRIMARY KEY(albumName)` on TEXT with BINARY collation, so the spellings never collide. Result: two
albums, two `album_cloud_status` rows, `Camera` 3 files and `camera` 12.

**Why it matters more than the count being wrong.** One physical folder can hold two album modes. Set
`Camera` to Archive and leave `camera` Off and archiving removes three files and leaves twelve, split
along which app wrote each file — so every new camera capture joins the Archive half on its own. That
is the membership rule in CLAUDE.md exactly: what an Archive album contains must not widen under a
choice made earlier.

Specced as **TASK-023**, not started. `BUCKET_ID` is the clean key, but it is a Room migration and so
Ian's call, and the merge rule for two rows holding different modes has to be his too — the safe
answer is least-destructive-wins, and picking it silently is not on. Whether OneDrive already holds
one folder or two under the two spellings is **unverified**.

**Not caused by TASK-022.** The `Camera` rows are timestamped 13:53:46 onward, the first video shot
after that afternoon's clean install; the fixture had only ever contained `camera` before.

**Nor by backup — corrected by Ian, 15 Sept 2026:** *"The app did not split the folder into two."* He
copied a `camera` folder into DCIM and the camera app then wrote new shots to `Camera`, its default.
The app split, created and renamed nothing; it read MediaStore's two spellings as two albums, which is
what TASK-023 is about. Absent on the Moto G by 15 Sept because Ian renamed the folder in his copied
files from `camera` to `Camera`; every row now reads `Camera`. The keying is unchanged.

**Optimise photos defaulted On — fixed** (`03fdc48`). `isOptimiseEnabled` is off by default so nothing
was optimising, but the Settings screen showed the switch on while nothing happened, and the video
row's handler clears the master only `if (!optimisePhotos)`. Turning video on and off again therefore
left the master on with photos still marked wanted, and photo optimising began having never been asked
for — a default rewriting files nobody opted into. Changed in three places: the field, its DataStore
read fallback, and the transient UI state in `BackupViewModel`.

`DEFAULTS.md` had no entry for `optimisePhotos` at all — the existing entry covers the automatic
master, and the per-medium switches were added later without one. That absence is why it drifted, so
both `optimisePhotos` and `optimiseVideo` are documented there now.

**Checked on device 15 Sept 2026:** *Optimise photos* reads Off on the Moto G, on an install that had
walked the wizard on 7 Sept. A fresh DataStore holds only `upgrade_backfill_checked` and
`wizard_step`, so `optimise_photos` is absent and the code default is what applies.

**Also noted, unfixed:** OkHttp logs full response bodies at INFO. Reading a run means wading through
complete Graph JSON — every file name, size and hash — which buries the app's own `GallerySync/*`
lines and puts file names and drive IDs in a buffer. Worth dropping to `BASIC` before release.

### 15 Sept 2026 — the delayed first backup: the countdown holds, the start waits up to 31 minutes

Moto G (`ZT422CTZQV`), two clean installs of `ba65024`, wizard option 1 with a 3-minute delay, Close
pressed, app not reopened. Measured with a watcher polling `dumpsys jobscheduler`, `usagestats` and
`logcat` every 20–30 s.

| | Run A | Run B |
|---|---|---|
| Armed | 10:28:59 | 10:53:27 |
| Due | 10:31:57 | 10:56:24 |
| Power | battery until 10:30:53, then charging | charging throughout |
| Last `ACTIVITY_RESUMED` before the start | 10:29:13 pause, then **10:49:50** | **10:53:12**, before arming |
| Started | 10:49:50.445 | **11:23:39.716** |
| Late by | 17m53s — **released by Ian opening the app** | **27m15s — unattended** |

Run A does not count as an unattended start: the run began 0.4 s after the app was resumed, with the
bucket moving to ACTIVE (`reason=u-si`) in the same second. Run B is clean. Together with 5 Sept
(26m42s+, released by opening) that is three holds of 18–27 minutes on the charger and one on-time
overnight start (6 Sept).

#### The countdown is not the problem

`TIMING_DELAY` was satisfied on time in both runs. From then on the job read `Ready: true` with **no
unsatisfied constraint**, network included, and simply was not dispatched. The countdown is held by
Android, not by the app, so it runs whether the app is open or not.

An in-app timer was proposed and would not work: with the app closed, `dumpsys activity processes`
showed the process alive but `isFrozen=true` (cached-app freezer). A frozen process runs no code, so a
countdown inside it stops the moment the user leaves.

#### What holds it: JobScheduler batching for non-active apps

This device's JobScheduler constants:

```
min_ready_non_active_jobs_count=5
max_non_active_job_batch_delay_ms=1860000        (31 minutes)
conn_max_connectivity_job_batch_delay_ms=1860000
```

A ready job from an app outside the ACTIVE bucket waits until five such jobs are ready system-wide or
31 minutes pass. GallerySync is in RARE within seconds of Close on a fresh install (`reason=s`, at
10:29:17 and 10:53:37), and the one-second blips to 10 during Run B (`reason=s`, 10:59:50 and
11:01:40) did not release it. 27m15s and 26m42s both sit under the 31-minute cap.

#### On battery the app loses network as well

Before Run A's charger went in, `ConnectivityController` reported
`UID: 10499; Network: 100 (blocked=REASON_APP_BACKGROUND|REASON_APP_STANDBY)`. Plugging in removed
`REASON_APP_STANDBY` at 10:30:53. How long a delayed start waits on battery is **not measured**.

#### Decided by Ian, 15 Sept 2026 — the first backup requires charging

- **The initial backup runs only while the phone is charging**, and the user is told so.
- **Setting the delay is never affected by the charging state at that moment.** The user can always
  choose it; charging is a condition at the moment the delay runs out, and an unplugged phone waits
  and starts when it is plugged in.
- **Day-to-day sync does not require charging.** Battery behaviour is still to be tested — TASK-021.

Charging also removes `REASON_APP_STANDBY`, so it leaves only the batching hold to solve.

**The code does not do this yet.** The wizard's delay is `enqueueDelayedManualRun` — a manual run, and
manual runs skip the first-backup charging check (`BackupWorker.kt:101`, *"the user picked this
moment"*). The job carries `BATTERY_NOT_LOW`, not `CHARGING`. The requirement belongs on the job the
wizard enqueues, set by the wizard itself — **not** read from Settings' *First backup needs charging*
switch, which would have the wizard reading Settings.

**Still for Ian:** does *Start now* follow the same rule, and does unplugging mid-backup pause it?

#### What remains for the delay card

The card promises a start time that Android holds to within about half an hour. Two ways out: say so
(*"starts within about half an hour of…"*), or move the countdown to an inexact `AlarmManager`
while-idle alarm (no permission) that starts the backup as an expedited job, which is not batched.
The second is unmeasured — whether the expedited start gets network, and whether the batches after it
keep going, are the questions — and is the same research TASK-021 needs.

**Also seen, not chased:** a fresh install logs `backup run starting` (not manual) at 10:27:43, three
seconds after launch and before the wizard had granted any folder. Presumably the automatic arm at
application start, finding nothing to do.

#### Built and verified the same afternoon — the delayed start waits for the charger

`enqueueDelayedManualRun` now sets `requiresCharging = true`, as does `onDelayElapsed`'s recovery path
for a lost arm. Continuations and *Sync now* are unchanged, pending Ian's answer above. Copy, all
Ian's: the delay card says *"Backup may start after the set time due to the verification process"*
(no figure, deliberately), the countdown card body carries the same sentence in place of *"when the
countdown ends"*, and a bold line reads *"Make sure your phone is plugged in for the backup to
start."* Ian saw all three on the Moto G. **Dark mode not yet checked for them.**

Clean install 11:44:41, phone unplugged, 3-minute delay, Close:

| Time | State |
|---|---|
| 11:47:58 | armed; the job's required constraints include **`CHARGING`** — the first run ever to carry it |
| 11:50:19 | due |
| 11:55:47 | **not started** — `Unsatisfied constraints: CHARGING CONNECTIVITY`, no app open since arming |
| 12:07:44 | app opened on battery — still not started, `Unsatisfied: CHARGING` only (foreground returned the network) |
| 12:08:29 | plugged in with the app open — **`backup run starting (manual)` about a second later** |

#### Defect found by it: the reopened card says the backup is running

Opened at 12:07 on battery, past due, the card read *"Your backup is running. The backup runs in the
background — you can use your phone normally."* with *"0% · Starting upload…"*. Nothing was running.
The phase is `WAITING` only while `remainingMillis > 0` (`SetupTour.kt:314`); at zero it becomes
`UPLOADING` whatever the job is doing, and the plug-in line — shown only while waiting — disappears
exactly when it is the one thing the user needs to read.

Not a pop-up, and it cannot be one without a notification: while `CHARGING` is unmet the job never
starts, so no app code runs at the due time. `POST_NOTIFICATIONS` was ruled out on 28 Aug.

#### Fixed: the card waits until the backup begins, not until the countdown ends

The stored due time now stays set past zero and is cleared only when `observeBackupWorker` sees the
backup begin — a manual batch in `RUNNING`, or a file landed, or nothing outstanding. The card's
`WAITING` phase follows the stored due time rather than the clock, and at zero the ring reads
*"0:00 · Waiting to start"* instead of *"0:00 until backup starts"*. The plug-in line and SYNC NOW stay
up for the whole wait.

Verified on the Moto G from a clean install (12:27:28), unplugged, 3-minute delay, Close; opened at
12:44 on battery, well past due: *"0:00 · Waiting to start"*, plug-in line, SYNC NOW — checked in dark
and light — with the job on `Unsatisfied: CHARGING` and no run started. Plugged in at 12:47:39 with
the app open; `backup run starting (manual)` the same second and the card switched to *"Uploading 10
of 256"*.

**The countdown starts on the chip, not on Next — defect, found by Ian in the same run.** Tapping a
delay chip calls `setFirstBackupDelay`, which stores *now + delay* at once, so time spent reading the
card comes off the delay: armed with 161.8 s, 173.3 s and 176.9 s of a 180 s choice.

**Decided by Ian, 15 Sept 2026 — a deliberate start overrides the charger.** SYNC NOW on the countdown
card, and *Right now*, start the backup immediately whatever the battery state; only the automatic
start at the end of a delay waits for charging. The bold line is reworded to say it is about the start
*on its own*, so it no longer contradicts the button beneath it. Unplugging mid-backup — pause or carry
on — is still open.

**An instrument note.** One minute past due on an asleep, unplugged phone, the job still showed
`TIMING_DELAY` unsatisfied. JobScheduler's delay uses a non-waking alarm, so the constraint is only
re-evaluated when the phone next wakes. Harmless here — `CHARGING` was unmet anyway, and plugging in
wakes the phone — but a stale `TIMING_DELAY` in a dump is not evidence the countdown failed.

#### Fixed: the countdown starts on Next

The delay card no longer stores anything while the user chooses. Its selection is held by the wizard,
and Next calls `commitFirstBackupDelay`, which writes *now + delay* and only then advances — the order
matters, because the countdown card starts the backup at once if it finds no due time. The bold line
now reads *"…plugged in for the backup to start on its own"*, per the SYNC NOW decision above.

Verified on the Moto G from a clean install (13:11:34): a 3-minute choice, left for about 30 s before
Next, armed with **179,879 ms**; Back then Next again re-armed with **179,876 ms** — Ian saw 2:59 both
times. SYNC NOW started the run 0.1 s after the tap and replaced the delayed job — no job requiring
`CHARGING` was left queued. The *Right now* route after Back shares the same cancel-and-start path but
was not tapped through.

### 15 Sept 2026 (afternoon) — Gate 2 #3 optimised the whole library

The test the 7 Sept pass did not run: #2 against #3 on a library mostly in OneDrive. Moto G, test
account. Run 1 (option 1) finished at 13:43:53 with 0 remaining, so OneDrive held all 254 files. Ian
then deleted **15 photos from `BudgetMixed` and 5 videos from `PauseTest`** in OneDrive. Clean install
13:49:43, option 3 chosen — `library_choice = BACK_UP_AND_OPTIMISE_NEW` confirmed in the DataStore.

- **The cloud check was right:** *"234 already in OneDrive, 22 outstanding"* — the 20 plus two new
  screenshots. `album_cloud_status` put the gaps exactly where Ian made them: `BudgetMixed` 15 missing,
  `PauseTest` 5, `Screenshots` 2.
- **The Step 7 card was right:** 52 MB photos, 438 MB video, 491 MB total — consistent with the 22
  outstanding (80% of ~65 MB of photos; 85% of ~515 MB of video), not the library.
- **The optimiser was not.** *"photos: proxying 60 of 77 … 17 of 17"* across batches — **176 photos
  proxied, 22 refused as too small**, i.e. every photo — and *"video: optimising up to 3 of 59"*,
  every video. Local footprint by `du`: **2.33 GB → 0.71 GB**, every album shrunk, including the five
  Ian never touched. Ian confirmed by eye that everything was optimised.

**Cause.** On a fresh install the ledger is empty, so a file found already in OneDrive is marked
backed up during the run by the skip-existing path — which stamped `uploadedAt = now`. The ledger read
back all 256 rows at 13:56:57–14:00:45, every one after the cutoff written when #3 was chosen. #3's
test is `uploadedAtEpochMillis >= cutoff`, so every file passed and #3 behaved exactly as #2. The 6
Sept fix (`f706d9e`) restored the cutoff but not what it is compared against; on an empty cloud, which
is what a wipe-and-reinstall tests, the two options are identical, so nothing showed it.

**For a real user this rewrites their whole library against the choice they made.** Nothing is lost —
proxying requires a verified cloud copy and leaves every file in the gallery — but "optimise only new
files" optimising everything is exactly the kind of widening a user's choice must not undergo.

**Fix, built and awaiting a run:** the skip-existing path (and the recovered-proxy path beside it)
record Graph's `createdDateTime` — when the file arrived in OneDrive — instead of now. The listing
already requested the field and the mapper dropped it; it is carried through `RemoteMediaNode.File`
and `RemoteFileRef`. Files already in OneDrive then date from before the cutoff, and only what this
run uploads falls after it. No date from Graph records `0`, which #3 treats as old and leaves alone.
`uploadedAtEpochMillis` is read nowhere but the three cutoff clauses, so nothing else moves.

**Also from this run, not fixed:**

- **The progress ring counts skipped files as uploads.** On a fresh install every file is pending until
  checked, and one found in OneDrive is ticked off as done — so the card read *"Uploading 133 of 256"*
  with two files actually sent, and Ian read it as uploading too much. The reconcile had already said
  22; that is the figure the card should count against.
- **OkHttp's body logging evicted the evidence.** Graph listings logged in full at INFO filled the 16
  MB buffer fast enough that `upload: stored` and skip lines were gone within minutes, and a grep read
  zero on a run that had skipped 131. The database was the reliable instrument. Dropping OkHttp to
  `BASIC` is now a testing need, not only a release one.
- **The Optimisation Settings card said** *"These settings can be changed at any time in the Settings
  tab"* — removed at Ian's request: the wizard and Settings do not touch each other.

#### The fix, verified the same afternoon

Fixture reset by Ian, then deleted from OneDrive: **10 photos from `BudgetMixed`, 5 from `camping`, 5
videos from `PauseTest`**, and he shot new photos into `DCIM/Camera` (8 on disk). Clean install of the
fix 14:34:05, option 3.

- Cloud check: *"219 already in OneDrive, 30 outstanding"* — exact. The Optimisation Settings card no
  longer carries the Settings-tab line.
- Mid-run, with the cutoff at 14:38:14: **165 skipped rows carried OneDrive's own arrival dates from
  earlier days — all before the cutoff** — and the rows uploaded by the run after it.
- After the run: **30 rows new** (10 BudgetMixed, 5 camping, 8 Camera, 5 PauseTest, 2 screenshots);
  **27 optimised** — 23 photos and 4 videos — and **zero optimised files that were not new**. The
  three new files left alone were refused on merit: a 10 MB low-bitrate clip that transcoded no
  smaller, and two screenshots too small to shrink.
- `du`: `budgetphotos`, `car show` and `funny stuff` **byte-for-byte unchanged**; `camping` −20 MB for
  its 5 photos, `BudgetMixed` −32 MB, `PauseTest` −412 MB. The previous run had shrunk all of them.

Two unit tests added on the mapper: `createdDateTime` carried, and absent maps to `0`.

#### Fixed: the progress card counts what is actually sent

The card's denominator is now the cloud check's outstanding count, when the check covered every
album (`filesToSend`; an incomplete check falls back to the ledger rather than presenting a floor as a
total). Its numerator is the number of files uploaded since the run began — `countUploadedSince`,
against a `wizard_run_started_at` persisted beside the total — which counts real uploads only because
a skipped file now keeps its OneDrive arrival date. Whether the run happens, and when it is done, still
follow the ledger's pending count; only the display changed.

Verified on the Moto G from a clean install (15:07:39), option 1, after Ian reset the fixture and
deleted 25 files from OneDrive: *"total pending: 247, to send: 33"*; 165 skips left the card at *"0% ·
Starting upload…"*; it then read *"Uploading 3 of 33"* and climbed; **25 files sent** (BudgetMixed 10,
camping 9 photos and 1 video, PauseTest 5); the card went from 25 of 33 to *100% · Finish*.

**Known limit — a proxy is counted as outstanding.** The 8 extra in the 33 were `DCIM/Camera`'s
photos, proxied by the previous run and outside the fixture Ian restores. The cloud check matches name
*and* size, so a proxy never matches its full-size original and is tallied as missing. The run itself
recognised all 8 as backed-up proxies (`isProxied = 1`) and sent nothing — only the count is wrong, so
the card stops short of its total and jumps to done. On a real phone this is the reinstall case, the
same root as "a clean reinstall re-uploads everything"; `ReconciliationRules` would need the proxy
marker to fix it.

### 15 Sept 2026 (evening) — the folder picker, verified: what is backed up is not what was ticked

The three SAF picker defects recorded on 3 Sept ("found by reading, not yet fixed") were checked on the
Moto G before any fix, at Ian's request. One does not reproduce; the other two do, and are worse than
recorded, because of a fact the 3 Sept entry did not have.

**The scan scope is the granted trees whenever any exist.** `ScopedDirectories.currentScope()` returns
the SAF grants if there are any, and falls back to the wizard's ticked folder names only when there
are none. So the picker does not merely decide what can be *optimised* — it decides what is *backed
up*, and a tick without a grant counts for nothing once any other folder has one.

- **"The picker opens in the wrong place" — does not reproduce.** The document URI the wizard builds
  (`buildDocumentUri(…, "primary:DCIM")`) opened DocumentsUI directly on DCIM, and on Pictures, from a
  closed picker. The tree form did the same. One launch *was* delivered to a still-open picker
  instance (*"intent has been delivered to currently running top-most instance"*), which may be what
  3 Sept saw. Left unchanged.
- **Cancel skips silently — confirmed.** Both pickers cancelled: the wizard went on to Cloud Storage
  with no grants and no word. The scan then falls back to the ticked names, so everything is backed up
  and nothing can ever be optimised, unannounced.
- **Cancel one, grant another — confirmed, and the worst case.** DCIM and Pictures ticked; DCIM
  cancelled, Pictures granted. Before the grant: `scanAll: 257 items across 8 albums`. After it:
  `scanAll: 10 items across 1 albums within 1 granted folders`. **DCIM's 247 files dropped out of the
  backup** with DCIM still ticked (`selected_directories: DCIM, Pictures`) and nothing on screen.
- **Unticking does not narrow either.** An earlier run granted both, then unticked Pictures:
  `selected directories: [DCIM]`, but the scan stayed at 257 items within 2 granted folders — a
  folder the user removed stayed in the backup because its grant was still held.
- **Found by reading the same code:** `buildSafGrantQueue` decides a folder is already covered with a
  bare `startsWith`, so a held grant on `DCIM/Camera` counts as covering all of `DCIM` and the picker is
  never shown for it; `TreeScope.isInScope` has the correct boundary check and was not used.

This is the album-membership rule in CLAUDE.md from a different side: which files are backed up — and
so, later, which an Archive or Sync album contains — changed without the user choosing it.

**Also found, separate:** the folder counts on *Choose folders to back up* are taken once and never
refreshed. Ian added eight screenshots, went back to the card, and it still read two; MediaStore held
all ten. Discovery runs only while the list is empty.

**Decided:** the wizard enforces the grant rather than the engine changing what it scans (Ian, no
preference between the two; `TreeScope`'s "one grant, two jobs" is the documented design). A cancel or
a wrong pick stops the walk and says so, with Try again or Skip; Skip unticks the folder and says it is
left out of the backup; a subfolder asks whether to use the whole folder or keep only the subfolder;
an unrelated folder is never granted, so it cannot widen the backup.

#### Fixed the same evening, verified on the Moto G

The walk now advances only when the pick covers the folder asked for — that folder or a parent. Any
other result pauses it on a `SafGrantIssue` shown at the top of the folder card, and the pick is not
granted until the user decides: **cancelled** and **unusable** offer *Try again* or *Skip <folder>*;
a **narrower** pick offers *Choose all of <folder>* or *Keep <subfolder> only*; a folder **elsewhere**
is never granted, since it would add something unticked to the backup. *Skip* unticks the folder so
the ticks, the grants and the scan agree. Coverage uses `TreeScope.isInScope` instead of the two-way
`startsWith`. If every folder is skipped the wizard stays on the card. The folder counts are recounted
on every visit, keeping the ticks.

Clean installs of the fix, DCIM and Pictures ticked:

- **Cancel → Try again → subfolder → Choose all of DCIM** (17:53): `CANCELLED`, notice shown; *Try
  again* reopened the picker on DCIM; `NARROWER (DCIM/Camera)`, then `NARROWER (DCIM/camping)`, each
  held back ungranted with the subfolder notice; DCIM itself granted with no notice; Pictures granted.
  Scan: **257 items across 8 albums within 2 granted folders** — against 10 on the unfixed build.
  Pictures read *10 photos* on the card, the recount working. The notice checked in light and dark.
- **Skip** (18:01): `CANCELLED` → *Skip DCIM* → `selected directories: [Pictures]`, DCIM unticked on
  screen; Pictures granted; scan **10 items within 1 granted folder**; stored scope `selected:
  Pictures`, `granted: Pictures`.

*Keep <subfolder> only* was not tapped through. One *Try again* tap was lost to an activity recreation
caused by switching the theme from adb at the same moment — not a defect, but a reason not to toggle
`uimode` while someone is tapping.

#### *Keep <subfolder> only* — verified on the Moto G, 17 Sept 2026

The one path left untested. `Settings → Run setup again`, `Pictures` ticked (an empty `TestSub`
subfolder added first, since `Pictures` itself had no nested folder to narrow into), the system picker
navigated into `TestSub` and `USE THIS FOLDER` tapped there rather than on `Pictures`.

- `ReconcileVM`: `grant for Pictures: NARROWER (Pictures/TestSub)`, then `Choose all of Pictures` /
  `Keep Pictures/TestSub only` — the same notice as the other two outcomes.
- Tapping *Keep Pictures/TestSub only* logged `granted Pictures/TestSub` and `grant for Pictures: kept
  narrower Pictures/TestSub`, and the walk advanced on its own — no third option, no stall.
- The scope narrowed correctly: `scanAll` read *2 granted folders* (the pre-existing `DCIM` grant from
  before this run, held independently of what got ticked here, plus the new `Pictures/TestSub` one).
  All 50 scanned items came from `DCIM`; `TestSub`, empty by construction, contributed nothing — exactly
  what a correctly scoped grant should do.
- Crash buffer empty before and after. `TestSub` removed from the device afterward; nothing else in
  the fixture touched.

All three outcomes of a narrower pick (*Choose all*, *Keep only*, and the notice itself) are now
verified on hardware.

The picker's *Back* takes two presses when it opens inside a folder: the first goes up a level. That
is DocumentsUI, not the app.

### 15 Sept 2026 (evening) — the tour's backdrops are drawn as a phone again

Ian, with the concept art the tour was built from: the backdrops behind the step 2 cards were meant to
look like a phone, and did not. The welcome card is an image of a phone, frame and all; every backdrop
after it was drawn edge to edge, so the tour changed register at step 2.

**Nothing regressed — the frame never existed in code.** The tour was added on 31 Aug (`e6a0794`)
drawing the real tabs behind its cards, and `aee7125` replaced those with the drawn mockups on 3 Sept,
inside a 1,194-line commit whose message does not mention it. No image has ever been committed except
`welcome_screen.png`, and the concept art was never in the repo. The reason the mockups exist is worth
keeping: lifting the opaque background for the Help card on 3 Sept put the **live** Settings screen
behind a wizard card, with real controls reachable round the edges of it.

What was built: `PhoneScreenBackdrop` draws a solid dark bezel with the screen cut out of it, corner
radius 36dp, an 8dp body and a camera dot — the bezel colour is a new `phoneFrame` token in
`GallerySyncColors`, dark in both themes for the same reason the nav bar is.

**The bar of tabs is drawn inside the frame**, as the app's own `SignalNavBar` with its taps dropped,
so the picture cannot drift from the bar it is a picture of. `MainActivity` now hides the real bar for
the whole tour rather than only on the welcome step; it used to stay visible because the cards point at
it, which is exactly what left it stranded outside the frame when the frame arrived. The cards and
their arrows are inset by the frame so the weights still divide the width the drawn bar occupies, and
the card leaves room measured from the drawn bar rather than the 60dp that was assumed.

A first attempt framed only the backdrop and left the real bar below it — Ian: *"that looks awful"*,
and it was, so it was reverted rather than kept. Verified on the Moto G in light and dark: Albums,
Restore, Settings and Help each show the framed phone with its own tab lit, and each arrow lands on its
pill. The Settings arrow sits a few pixels left of centre — the weights approximate tab centres, as
they always have.

#### Four decisions from Ian, 15 Sept 2026 (evening)

- **Unplugging mid-backup does not pause it.** The charger is a condition for the delayed first backup
  to *launch*, and nothing more; a run already moving bytes carries on. That is what the continuations
  already do, so nothing was built — it is recorded so it is not "fixed" later.
- **The 85% video-savings estimate stands.** The Step 7 card keeps it. It is measured on camera
  footage and will over-promise on screen recordings; Ian accepts that rather than complicating the
  figure.
- **The "old enough" copy goes.** `settings_optimise_master_on` and `settings_auto_optimise_on` said
  photos and video are optimised "once verified in OneDrive **and old enough**" — but photos are
  proxied whatever their age, and only video has an age, set by its own *Older than* control. Both now
  end at "verified in OneDrive". The orphaned `settings_photo_age` label went with them.
- **OkHttp keeps logging bodies for now.** It buries the app's own lines within minutes and should drop
  to `BASIC` before release, but the full Graph traffic is worth having while backup, sync, archive and
  delete are still being watched on hardware.

#### The Settings mockup mirrors the Settings tab — 15 Sept 2026

Ian would rather the tour's Settings backdrop were a screenshot of the real tab; a drawing is the
nearest thing that survives a screen changing or being translated, so the mockup now **mirrors**
`SettingsScreen` instead of resembling it: General, Albums, Backup, Sync in that order, the same rows,
and the same strings read from the same resources. Only the sample values are invented. The switches
show the real defaults — mobile data off, Optimise photos and video off — so the picture cannot teach a
setting the app does not ship with; it had shown Optimise photos on since before the 7 Sept default fix.

**The Help card now rings the Albums help button**, not Backup. The longer mockup pushed Backup's
button down behind the card, hiding the very thing the card points at; the tooltip is `help_albums` to
match, which is also what the concept art shows.

**A crash, caught by the rule that catches them.** `sources_full_path` is `"%1$s / %2$s"` and the first
version passed one argument: `MissingFormatArgumentException` on the Settings card, every time. Found
by launching after install and reading `logcat -b crash`, which is exactly what that habit exists for.
Fixed by passing volume and path as `SourcesSection` does.

Verified on the Moto G in light and dark: all four Step 2 backdrops, the Help ring with its real
tooltip, and an empty crash buffer.

#### TASK-023, how the two names arose — Ian, 15 Sept 2026

Not one folder the app split in two, and not a naming scheme anyone chose: **`camera` came in with the
backup folders he copied onto the phone**, and **`Camera` was created by the system when he took new
photos**. Two folders, as far as he is concerned, arriving separately and differing only in case.

The 7 Sept disk check still stands beside that — same inode for both spellings, identical listings —
because Android's emulated storage is case-insensitive: the camera app's writes landed in the folder
already there. Both names are real and were made by different hands; the filesystem merged them and
MediaStore kept both spellings, which is what the app read as two albums. Do not restate this as "one
folder showing up as two".

#### TASK-023, ruling — one mode shared across spellings — Ian, 16 Sept 2026

Ian tried to reproduce the two folders on the Moto G. **Android will not let a second `Camera`/`camera`
directory exist beside the first**: copying one in produced `camera (1)` / `Camera (1)` instead. That
confirms the 7 Sept inode check from the other side — there is only ever one directory, and case alone
cannot make a second.

It does not remove the condition. The split was never two directories; it is MediaStore keeping the
spelling each *writer* used for the one directory (the copied `camera`, the camera app's `Camera`).
The camera app writes into the existing folder without renaming, so two spellings can still sit in
MediaStore under one folder.

**Ruling: the spellings share one album mode.** Every spelling of a folder that differs only in case
is one album with one mode. A folder that Android renamed on collision, `Camera (1)`, is a genuinely
separate directory and remains a separate album with its own mode — the shared mode must not reach it.

Still open, and still Ian's: the key (`BUCKET_ID` vs case-folded name — either delivers a shared mode,
both need a migration) and the merge rule when existing rows for two spellings already hold different
modes.

#### TASK-023, rulings on spelling, conflicts and restore — Ian, 16 Sept 2026

- **Spelling: agent's judgement.** Ian sees only the name a UI displays, never `BUCKET_ID`. With no
  existing folder the camera app creates `DCIM/Camera`.
- **Any folder/album discrepancy sets the merged album's mode to `Off`, and the user is warned** that
  a discrepancy was found and that the mode changed. This replaces least-destructive-wins. It is an
  **explicit exception Ian made to "album modes are set only by the user"**, and it only ever moves a
  mode to `Off`, which can never remove a file.
- **The wizard is untouched by this.** It writes no album modes, and everything starts `Off`, so a
  conflict can only carry a mode the user set afterwards.
- ~~Restore is a route that can create the split.~~ **Withdrawn the same day by test on the Moto G**:

#### TASK-023, restore across a spelling change — tested on the Moto G, 16 Sept 2026

The 18-file album was backed up as `camera`, archived (18 `.trashed-` files), and the folder renamed to
`Camera` on disk. Then one file was restored.

- **MediaStore corrected the spelling.** It was inserted with `RELATIVE_PATH = DCIM/camera/` and came
  back as `DCIM/Camera/`, `bucket_display_name = Camera`. A restore does not create a second MediaStore
  spelling.
- **GallerySync split it anyway.** The rescan left two ledger rows for one `mediaStoreId`
  (`camera/…` UPLOADED, `Camera/…` PENDING) and a new `Camera|OFF` beside `camera|ARCHIVE`. The Albums
  tab showed `Camera` with 1 file, "1 pending" and "1 verified" at once, and `camera` as "All files
  Archived". The ledger keeps the name from backup time; the scan uses today's name. This is the
  TASK-023 defect with no second writer involved.
- **Not archived again** (`redundantLocalCopies: 0`), only because the split put the file under Off.
- **OneDrive is case-insensitive**: listing `DCIM/Camera` returned the 18 files whose parent folder is
  named `camera`. This settles TASK-023 decision 4: one remote folder.

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

#### "8 optimised photos in Camera, now 1" — explained, nothing optimised today (16 Sept 2026)

Ian emptied the trash, and the Files app then showed one small photo in `Camera` where there had been
eight. **Nothing was optimised on this install.** No ledger row has `isProxied` or
`localProxySizeBytes` set, and no optimise log lines appear.

The eight are the burst taken 15 Sept 14:35 (`IMG_20260915_1435*`, 367–601 KB, and Graph reports
2048×1536, against 4096×3072 for the 21:31 shots). They were **already shrunk when today's clean
install ran**, left over from the 15 Sept optimise tests and not put back to full size in the reset.
Two siblings from the same burst, trashed on 15 Sept before the optimiser ran, were 3.2 MB each. With
an empty ledger, today's first backup uploaded the shrunk bytes as the only OneDrive copy. That is
expected after `pm clear`: the app has no record that a file is a proxy.

Seven of the eight were in `camera` (Archive) and went to the trash, and Ian's emptying removed them.
The eighth is `IMG_20260915_143513095_HDR.jpg`, the one restored from OneDrive at 499,165 bytes. So
8 → 1 is the trash being emptied, not a change of optimisation.

#### TASK-023 — the camera app did NOT split the spelling this time (Moto G, 16 Sept 2026, 16:32)

After Ian cleared OneDrive and cut DCIM below 1 GB, `DCIM/camera` was created with `adb shell mkdir`
(there was no `Camera` at all), and one photo and one video were copied in with `cp`. After an uninstall,
reinstall and full wizard, both were uploaded under album `camera`, with all albums Off. Ian then took
one photo and one video with the Motorola camera app (`com.motorola.camera5` 10.0.40.37).

- Both landed in the existing directory (inode `45910`, the same under either spelling).
- **MediaStore recorded both as `relative_path = DCIM/camera/`, `bucket_display_name = camera`.** No
  `Camera` rows exist, and GallerySync shows one album, `camera`, with the two new files PENDING (the
  album is Off).

**So the 7 Sept split did not reproduce.** The camera app and MediaProvider were both last updated
3 Sept, before 7 Sept, so a version change does not explain the difference. What differs and is
untested: how the lowercase folder was created (the 7 Sept fixture was copied onto the phone by Ian;
today it was `adb mkdir` plus `cp`), and whether `DCIM/Camera` had existed on the phone earlier. Today's
result agrees with the restore test: when a directory already exists, MediaProvider records its on-disk
spelling.

The fix is still needed. The 7 Sept split was real, and the restore test showed the ledger splitting
an album with no second MediaStore spelling at all.

#### TASK-023 — fix specced, 16 Sept 2026

Spec written in TASK-023 (*The fix — spec*), awaiting Ian's approval.
- **No `BUCKET_ID` re-key and no Room migration.** Instead, one canonical spelling per case-folded
  folder name: the spelling of the newest MediaStore row, which MediaProvider writes from the disk.
- **Merge at runtime.** An `AlbumIdentityReconciler` runs in a transaction at the start of
  `refreshLedger`, and as a guard before anything reads modes.
- **Discrepancy handling:** every merge goes to Off with a warning card on
  the Albums tab, stored in DataStore.
- **Duplicate ledger rows** for one `mediaStoreId` collapse to the uploaded row, with no deletion anywhere.
- **Ian, same day: warn even when both were already Off. Spec approved; build started.**

#### TASK-023 — built, 16 Sept 2026

One spelling per folder in the scanner, plus a runtime merge (`AlbumIdentityReconciler`) guarding
every mode reader. Every merge sets the album to Off and shows a card in the Albums list. 343/343
unit tests pass.

**Verified on the Moto G:** renaming `camera` to `Camera` on disk merged 4 ledger rows and the Off
preference into one `Camera` album, and the card read *"Before: camera was Off."* It was readable in
light and dark, nothing was uploaded, and the crash buffer was empty.

**Built differently from the spec:**
- Duplicate rows are matched on the full key, not `mediaStoreId`, because MediaStore reuses ids.
- The card moved into the scrolling list, after landscape pushed Dismiss off the screen.

**Still open:**
- ~~The Archive merge on a device.~~ Verified 21:03, below.
- The narrow window between the Archive screen's list and Android's trash dialog.

#### TASK-023 — the Archive merge verified on the Moto G, 16 Sept 2026 (21:03)

1. `Camera` (9 files) was set to Archive by Ian; all 9 became `.trashed-` files and the ledger flagged
   them missing.
2. The folder was renamed on disk to `camera`.
3. Ian restored two videos (one intended; the second by mistake, which makes no difference) and
   emptied the trash himself. That emptying, not the app, is why the 7 trashed photos left the disk.

- The restores were inserted as `DCIM/Camera/` and MediaStore recorded `DCIM/camera/`,
  `bucket_display_name = camera`: the disk's spelling, as before.
- The log shows `merged [Camera, camera] into 'camera': modes were {Camera=ARCHIVE}, now OFF; 9 ledger
  rows`. Straight after, `redundantLocalCopies: no album is set to Archive, so nothing is offered`.
- `album_preferences` holds `camera|OFF`. The ledger holds 9 rows, all under `camera`, all with a
  remote id. The 2 restored rows are no longer flagged missing, and there are **no duplicate rows**
  (before the fix, the same restore left two rows for one file).
- The Albums tab shows a single `camera` album (2 files, Off, 2 verified in OneDrive) and the card
  *"camera — mode set to Off … (Camera, camera)"*, in the copy Ian edited. The crash buffer is empty
  and nothing was uploaded or trashed by the app.

**Both device checks have now passed:** the Off-to-Off merge (16:54) and the Archive merge (21:03).

#### TASK-023 — warning card redesigned by Ian, 16 Sept 2026

The card is now a single table covering every merge (*Album Name 1 · Album Name 2 · Merged Album Name*)
under a centred, larger *DUPLICATE ALBUM NAMES DETECTED*, followed by Ian's three sentences. Dismiss
clears all rows. Verified on the Moto G in light and dark. It is not verified at 344dp, which no device
here can show.

#### TASK-023 — card and header polish, and open items, 16 Sept 2026 (evening)

- **Dismiss is an outlined button**, bordered and labelled in the card's content colour.
- **Albums header card:** *"Total Album Count/Size: 3 Albums · 892 MB"* and *"Album Mode Count: 0 Backup
  · 0 Sync · 0 Archive · 3 Off"*. Each label sits on its figure's line at the same size (Ian's request).
- **The warning card vanished once, around 21:18–21:19, and Ian did not dismiss it. The cause is not
  found.** Only two paths clear it: Dismiss, and `setAlbumMode` for that album. The second fires even
  when the chosen mode equals the current one, so choosing Off on an Off album clears it with no
  visible change. That is the leading suspect. It did not reproduce through dark-mode toggles,
  `install -r`, a launch, closing the app, other apps, or new photos. A watcher polling the DataStore
  every 3 s saw no clearing after 21:22.
- **The merged name follows the disk.** At 21:22 the folder was renamed `camera` → `Camera` for the
  test; the new merge was named `Camera` and its row reads *Camera · camera · Camera*. New camera shots
  into that folder caused no further merge.

**Open, awaiting Ian:**
1. ~~Should re-choosing an album's current mode keep its warning?~~ **Decided by Ian: only Dismiss removes
   the warning.** Setting a mode no longer touches it (`BackupViewModel.setAlbumMode`), and the per-album
   clear is gone from `BackupSettings`.
2. Should the Archive screen re-check membership before each trash request? Today a merge landing between
   the list and Android's dialog does not stop that batch.

#### TASK-023 — only Dismiss removes the warning (Ian, 16 Sept 2026)

Choosing a mode no longer clears the duplicate-name warning; Dismiss is the only way it goes. This also
removes the leading suspect for the card vanishing unasked.

The DataStore separators in `AlbumIdentityRules` were raw control characters in the source since the
first commit. They are now written as `''` / `''`. The bytes are identical, and the
stored warning on the Moto G still decoded and rendered after install. 343/343 unit tests pass, and the
crash buffer is empty.

### 17 Sept 2026 — TASK-021 reproduced: RESTRICTED bucket withholds a ready job, even while charging

Wizard defects were closed (the last one, *Keep <subfolder> only*, verified the same day), so TASK-021
was cleared to start. Reproduced on the Moto G, wired to this machine over wireless debugging.

**Method.** Camera set to Sync, backed up to a clean baseline (5/5 verified, optimise chain settled —
`dumpsys jobscheduler` showed only the periodic job waiting on `TIMING_DELAY` and the content-trigger
job waiting on `CONTENT_TRIGGER`, nothing `ENQUEUED`/`BLOCKED`). A file copied into `DCIM/Camera` under
a fresh name plus a `MEDIA_SCANNER_SCAN_FILE` broadcast stands in for a new camera photo. The app was
backgrounded with Home, never foregrounded again until the probe was read — foregrounding is what
dispatches the job and would have destroyed the measurement. `am set-standby-bucket` forced the bucket
directly rather than waiting out a real demotion.

**Probe 1 — RARE.** New photo in, bucket forced to `rare`, watched for 8 minutes without touching the
device. `backup run starting` at T+31s, uploaded, `backup run finished: 1 uploaded` at T+36s. The
bucket that the 5 Sept entry measured a 26-minute *batching* delay in did not reproduce anything here —
this device, this run, dispatched almost immediately.

**Probe 2 — RESTRICTED.** A second new photo, bucket forced to `restricted` (the bucket this exact app
was independently seen to reach earlier the same day, per `dumpsys usagestats` — not a bucket invented
for the test). Watched for 12 minutes, polling once a minute:

```
Satisfied constraints: BATTERY_NOT_LOW STORAGE_NOT_LOW CONNECTIVITY FLEXIBILITY CONTENT_TRIGGER
                        DEVICE_NOT_DOZING BACKGROUND_NOT_RESTRICTED WITHIN_QUOTA
Unsatisfied constraints: (none)
```

Fully satisfied — including `WITHIN_QUOTA` — from the first minute onward, for all twelve. No
`backup run starting` line ever appeared. The job was ready and JobScheduler was not running it.

**Not lost — withheld.** Setting the bucket back to `active` dispatched the job the same second:
`backup run starting` at 20:38:13, upload logged two seconds later. This is the guard's re-arm working
exactly as designed; the platform, not the app, was sitting on it.

**This is the 5 Sept quota table, confirmed on hardware rather than read off a doc page.** *"A device in
the charging state is given unrestricted resource access regardless of its app standby bucket... outside
the restricted bucket."* The device was on AC power for both probes. RARE's quota is lifted by charging,
which is why it dispatched in 31 seconds; **RESTRICTED's `once daily, 10 min` is the one quota charging
does not lift**, which is why an otherwise-satisfied job sat for the full 12 minutes untouched. Ian's
6 Sept report — new photos taken, several minutes waiting, nothing backed up — is explained: the app had
reached RESTRICTED, most likely from being closed rather than force-stopped for a stretch, and the
minutes he was willing to wait were well inside a window the bucket can legitimately hold a ready job
for.

**Still open — the fix.** The 5 Sept entry scoped two routes short of the foreground-service dead end:
`AlarmManager.setAndAllowWhileIdle()` and user-initiated data transfer jobs (`setUserInitiated(true)`,
API 34+, exempt from ordinary job quotas but with no Jetpack support and no minSdk-26 fallback). Neither
was chosen. The quota table complicates the alarm route specifically for this bucket — Restricted caps
alarms at 1/day too — which leaves user-initiated jobs as the one route the table calls unconditionally
exempt, at the cost of a second scheduling path beside WorkManager. This is a real architecture fork,
not a bug-fix-with-clear-root-cause, and belongs to Ian per CLAUDE.md's escalation rule for that.

Test files left in `DCIM/Camera` on the Moto G (test account): `IMG_20260917_201547_TEST.jpg`,
`IMG_20260917_202459_TEST2.jpg` — both real uploads now, harmless to leave.

### 18 Sept 2026 — TASK-021 overnight: a real batch recovers, the periodic net works, RESTRICTED doesn't let go

Continuation of the 17 Sept reproduction, on the same factory-reset Moto G, with Ian taking real camera
photos through the evening rather than synthetic files. Three batches, 5 photos + 2 videos each:
21:38 (used for the wizard's own manual backup, synced immediately), 21:47 (autonomous trigger silent
for the rest of the session), 22:51–22:52 (arrived while 21:47's batch was still stuck).

**The stuck 21:47 batch recovered — but not on its own.** At 22:53:24, one `BackupWorker` run uploaded
all 14 outstanding files — the full 21:47 batch *and* the full 22:51 batch — together, ending
`0 remaining`. The 22:51 batch's arrival woke a trigger that correctly swept up everything outstanding,
not just the newest file. Whatever consumed the 21:47 batch's own individual trigger earlier never
actually ran an upload for it; it took an unrelated third event, roughly 66 minutes later, to clear it.
Bucket was `RARE` throughout this whole window, never `RESTRICTED` — this is not the quota mechanism
from the 17 Sept entry, and remains unexplained. The one new fact: a later trigger recovers everything
a missed one left behind, so nothing is silently lost forever so long as *something* eventually fires.

**The periodic 6-hour net fired on schedule and worked.** 03:38:41, unattended, correctly reported
`0 remaining` since everything was already caught up by then. This is the backstop CLAUDE.md describes
(`the content trigger and the periodic net are uncapped JobScheduler work`), now confirmed live rather
than assumed.

**RESTRICTED reached again after ~10 hours of genuine overnight idle — far short of Android's documented
8-day disuse threshold.** 08:48:21, no interaction since the previous evening. Consistent with a
fresh-install app (installed that same day, almost no usage history) being judged harder by the
system's usage predictor than an established app would be — the 8-day figure is for the disuse path;
this looks like the separate “excessive activity / low confidence” path discussed 17 Sept, now
reachable from pure idle alone on a new install, not only from heavy test churn.

**`cmd usagestats delete-package-data` does not reset the standby bucket — the 17 Sept mitigation plan
does not work.** From 08:48 to at least 11:50 (over 3 hours, 13 consecutive 15-minute checks), the
monitor ran that command every time it found RESTRICTED, and every single time the bucket read
RESTRICTED again immediately after — `bucket after clear: 45` every time, no exception. The command
is real and runs without error; it simply is not the lever that controls bucket assignment. Recorded
so this is not tried again expecting a different result.

**The wireless-debugging session dropped around 12:05**, most likely the device going into deep sleep
and suspending its own debug service — mdns discovery found nothing afterward. Monitoring paused there,
resuming once the phone is reconnected. The background poll script is left running; it needs no
reconnect logic of its own, since each 15-minute cycle re-invokes `adb` fresh and will simply start
succeeding again once the device is reachable.

**Still open:** whether a real photo taken *while confirmed RESTRICTED* behaves like the synthetic
TEST2 probe from 17 Sept (withheld indefinitely) or differently — no new photos were taken overnight
while the phone was in that state, since Ian was asleep. Worth checking once reconnected.

### 18 Sept 2026 (morning) — a real photo under RESTRICTED synced in ~2 minutes, qualifying yesterday's finding

Ian took one real photo (`IMG_20260918_092230915_HDR.jpg`, 09:22:30) with the bucket confirmed
`RESTRICTED` at the moment it was taken — the direct test the 17 Sept entry left open. Unlike the
synthetic `TEST2` probe (created via `adb shell cp` + a manual `MEDIA_SCANNER_SCAN_FILE` broadcast,
watched 12 minutes with zero dispatch), this one moved:

- `dumpsys jobscheduler`, mid-run: the content-trigger job listed under **Active jobs**, tagged
  `Standby bucket: RESTRICTED`, `Time since first force batch attempt: -1m29s938ms`, `Changed URIs:
  content://media/external/images/media/40`.
- Logcat: `backup run starting` at 09:24:31, `upload: stored` at 09:24:37, `0 remaining` — about
  **two minutes** from shutter to verified in the cloud, entirely unattended, bucket never left
  RESTRICTED throughout.

**"Force batch attempt" is a real, named mechanism in the platform's own job dump**, distinct from the
`once daily, 10 min` quota in the official docs — it appears to periodically force a RESTRICTED app's
content-trigger job to run regardless of the daily budget, on the order of roughly a minute or two,
rather than deferring it to the next quota window. The job dump also shows `Has media backup
exemption=false`, meaning GallerySync isn't using whatever grants that (undocumented, not found in
public references — worth a deeper platform search if the two-minute figure ever needs to be relied
on rather than just observed).

**This qualifies, rather than reverses, the 17 Sept finding.** RESTRICTED is still a real, measured
state this app reaches unusually fast (~10h idle on a fresh install). What changes is the consequence:
a genuine new photo taken while RESTRICTED did not sit indefinitely — it went out in about the same
order of magnitude as ordinary background latency, not the many-minutes-to-hours picture the synthetic
probe suggested. Two explanations are open and not distinguished by this test: either
the synthetic `TEST2` file would also have gone out shortly after the 12-minute window closed, or the
`adb shell cp` + manual broadcast method used to create it doesn't generate the same MediaStore
notification a real camera write does, and so never properly armed the content observer at all. Given
this result, **the RESTRICTED-bucket case is markedly less urgent than yesterday's entry implied** —
worth weighing before committing to the user-initiated-data-transfer-jobs architecture fork.

### 18 Sept 2026 (late morning) — TASK-021's real cause found: WorkManager's own force-stop detection cancels the content trigger

Second factory reset, same method as 17 Sept, this time with continuous `adb logcat` running to a file
from before the wizard started — the 17 Sept run lost the critical window to buffer rotation and had
to reconstruct it from memory and periodic snapshots. This run caught it directly.

**Setup:** wizard backup (7 files) finished and verified at 10:35:29. Camera set to Sync at 10:36:26.
App backgrounded with Home, never touched again. A second batch (5 photos, 2 videos) taken at ~10:40.

**What happened, in full, from the continuous capture:**

```
10:41:58.470  ActivityManager: Start proc 13105:com.gallery.sync ... for service SystemJobService
10:41:59.533  GallerySync/MediaProvid: onCreate: authority=com.gallery.sync.provider
10:41:59.681  WM-SystemJobService: onStartJob for WorkGenerationalId(workSpecId=e32a2df3-..., generation=0)
10:41:59.845  WM-ForceStopRunnable: Application was force-stopped, rescheduling.
10:41:59.856  WM-SystemJobService: onStopJob for WorkGenerationalId(workSpecId=e32a2df3-..., generation=0)
10:41:59.952  WM-SystemJobScheduler: Scheduling work ID 72961344-... Job ID 1
10:41:59.977  WM-SystemJobScheduler: Scheduling work ID e32a2df3-... Job ID 5
10:41:59.983  WM-GreedyScheduler: Ignoring {WorkSpec: e32a2df3-...}. Requires ContentUri triggers.
10:42:00.083  WM-Processor: Processor cancelling e32a2df3-...
10:42:00.085  WM-Processor: WorkerWrapper interrupted for e32a2df3-...
10:42:02.974  ActivityManager: freezing 13105 com.gallery.sync, reason = moto_freezer, adj=915, adjType=cch-empty
```

**The content-trigger job fired correctly, on the new photos, exactly as designed — and then WorkManager
cancelled its own job a few hundred milliseconds into running it.** `ForceStopRunnable` runs early on
every process start and decides whether the app was force-stopped since it last ran, using a canary
alarm it sets for itself; if that alarm isn't where it expects, it concludes a force-stop happened and
reschedules — rather than trusts — every WorkSpec, including the one already executing. The
replacement it schedules is a fresh, inert watch (`Ignoring {WorkSpec: ...}. Requires ContentUri
triggers.`) that waits for the *next* change; it does not retroactively act on the photos already
sitting there. `freezing ... reason = moto_freezer` two seconds later, on the same process, is the
likely trigger: Motorola's own process freezer almost certainly cleared the canary alarm between the
wizard finishing and this dispatch, which is exactly the condition `ForceStopRunnable` misreads as a
user-initiated force-stop.

**This is not the RESTRICTED-bucket story from 17–18 Sept — it's a different, more direct cause,
and it explains everything the bucket theory left open.** The bucket was `RARE` this whole time, not
`RESTRICTED`. It explains the 32-minute silence after the 17 Sept 21:47 batch, the mystery `MainActivity`
launch at 22:21 (a fresh process start runs `ForceStopRunnable` again, which is what reset the job's
tracked constraint history), and why nothing recovered until a *later* trigger arrived and its
`ForceStopRunnable` pass found a healthy canary that time. It also explains why the manual "Right now"
path has never failed: it doesn't depend on a content-trigger `WorkSpec` surviving a process gap at all.

**Not yet root-caused further:** what specifically clears WorkManager's canary alarm on this device
between a normal background and the next dispatch — Motorola's `moto_freezer` is the visible suspect
in this capture, but isn't proven as the mechanism (as opposed to, say, Doze, or something in
WorkManager's own alarm handling under a frozen process). Worth reading `ForceStopRunnable.java` and
searching for known interactions between process freezing/cached-app policies and WorkManager's
force-stop detection before deciding on a fix.

### 18 Sept 2026 (late morning, continued) — the canary is cleared by an automatic task removal, not a swipe

Traced what clears WorkManager's force-stop canary, per the entry above. Followed process 10132 (the
wizard's own process, which set Camera to Sync and was then backgrounded with Home) through the full
continuous capture.

```
10:36:26  Camera set to Sync (this session's own action)
10:37:47.680  BufferQueueConsumer: MainActivity ... disconnect
10:37:47.833  HWUI: setGrContext validptr-->nullptr
10:37:48.526  ActivityManager: Killing 10132:com.gallery.sync (adj 915, setSvc -10000): remove task
10:37:48.675  ActivityManager: appDiedLocked ... isKilledByAm=true
```

**`remove task` is Android's standard reason string for a task being explicitly removed from
Recents — the same code path a user swipe goes through.** Nobody swiped anything here: the app was
backgrounded with Home at ~10:36:2x and never touched again, confirmed directly. About 70 seconds
of being backgrounded was enough for something — almost certainly Motorola's own recents/process
management, given `moto_freezer` is the reason string on every other lifecycle event this process
went through minutes earlier — to remove the task and kill the process automatically, indistinguishable
in the log from the user doing it by hand.

**This is the same failure CLAUDE.md already names, just not user-triggered the way it assumed.**
The "Do not add a foreground service" section calls a swipe out of Recents "the unsolved case,"
on the premise that it requires the user's own gesture and so is somewhat containable. This capture
shows the *identical* kill path firing on its own, well within two minutes of an ordinary background,
on a device with no usage history for this app yet. That reframes the whole investigation: this was
never really about standby buckets or WorkManager quotas — a killed process has no surviving canary
alarm, so the very next content-trigger dispatch finds `ForceStopRunnable` concluding force-stop and
cancelling itself, which is the mechanism traced two entries up. Bucket state, RARE vs RESTRICTED,
was never the variable that mattered; process survival is.

**Still unconfirmed:** whether this is purely Motorola's behavior, whether it would ease once the app
has enough usage history to earn OS trust (the fresh-install angle already suspected for the RESTRICTED
findings applies here too), and whether Ian's real Fold 8 — never used for testing — shows the same
speed. Worth checking `dumpsys activity processes` for the exact policy name behind `moto_freezer`'s
task-removal behavior before scoping a fix, since the fix needed for "an OEM kills backgrounded apps
fast" is a different shape than the fix needed for "WorkManager mishandles a real force-stop."

### 18 Sept 2026 (afternoon) — the AlarmManager-canary explanation above is withdrawn

The two entries above describe `ForceStopRunnable`'s force-stop detection as an AlarmManager
`PendingIntent` canary, and blame Motorola's `moto_freezer` for clearing it. **That mechanism is
wrong and is withdrawn**, checked directly rather than left standing: `dumpsys alarm` shows **zero**
registered alarms for `com.gallery.sync`, both right after a cold start that concluded *"Found
unfinished work, scheduling it"* (i.e. did **not** conclude force-stop) and again minutes later.
If the canary were a real, present alarm, at least one of those checks should have found it. The
AlarmManager source quoted two entries up was from an old WorkManager release; this app runs
work-runtime-ktx `2.11.2`, and whatever `ForceStopRunnable` actually keys off in that version is not
visible in `dumpsys alarm` — not pinned down.

**What still stands, because it's direct observation rather than inferred mechanism:**
- The `remove task` kill, firing automatically ~70s after an ordinary Home background with no swipe
  — confirmed straight from the log, nothing withdrawn there.
- `WM-ForceStopRunnable: Application was force-stopped, rescheduling.` firing immediately after a
  content-trigger job started, and cancelling that job — also a direct log capture, not an inference.
- The **inconsistency** across today's four cold starts (two concluded force-stop, two didn't) rules out
  a deterministic cause like a missing permission throwing every time; no `SecurityException` appears
  anywhere in the capture.
- External corroboration: a Google-codelab GitHub issue titled *"Doesn't work when removed from recent
  tasks,"* reported on Pixel hardware, citing the same `dontkillmyapp.com` class of OEM-vs-WorkManager
  problem this looks like.

So the honest summary is: **task removal → process restart → WorkManager's force-stop detection
sometimes wrongly concludes force-stop and cancels real pending work** is solid, evidence-backed and
reproduced twice. *Why* WorkManager 2.11.2 concludes that, mechanically, is not established — the
AlarmManager canary theory was the wrong guess at it. A fix does not need that mechanism nailed down
to be scoped, since the actionable lever is the same either way: keep the process alive longer, or stop
depending on a content-trigger `WorkSpec` surviving a process restart at all.

### 18 Sept 2026 (afternoon) — the Doze whitelist is confirmed as the actual lever, diagnostically

Direct test of the hypothesis raised after the withdrawal above: does keeping the app off the
platform's own battery-optimization exemption list explain the task removal? Added via
`adb shell dumpsys deviceidle whitelist +com.gallery.sync` — diagnostic only, not a route a real user
has on this device (see below).

**Before (17–18 Sept, not whitelisted): every background cycle ended in `Killing ... remove task`
within ~70–90 seconds**, cold-starting the next dispatch into `ForceStopRunnable`'s misdetection.

**After (whitelisted): backgrounded for a full 3 minutes, same PID (15488) alive throughout.** It still
froze normally (`freezing 15488 ... reason = moto_freezer, adj=700`) — freezing is untouched, and still
happens — but did **not** escalate to a kill this time. A batch of 5 photos taken while frozen-but-alive
woke the same process, ran `backup run starting` → `backup run finished: 12 uploaded ... 0 remaining`
in the same PID, and **no `ForceStopRunnable` instance anywhere in the window logged "force-stopped"**
— every one that ran logged `Found unfinished work, scheduling it.`, the healthy branch. The 12 (not 5)
confirms it also swept up files stuck from before the app was whitelisted, the same recovery pattern
seen on 17 Sept.

**So: staying off the kill list, not the force-stop heuristic itself, is the actual lever.** Freezing a
process is harmless and reversible — WorkManager's state survives it fine. Killing it is what forces a
cold restart into `ForceStopRunnable`, which is what intermittently misfires. Keep the process out of
the kill path and the misfire has nothing to trigger it.

**Why this isn't a shippable fix as tested.** Confirmed with Ian, this Moto G has no per-app
"Unrestricted" battery screen — only a device-wide "Use Adaptive Battery" switch, which would trade
away Doze/Standby behaviour for every app to fix one. The only *app-side* way onto this list is
requesting `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`, which is the Play-review-gated permission Ian
ruled out on 5 Sept for exactly this reason. This test answers the mechanism question cleanly; it does
not by itself answer the product question of how a real install reaches the same protected state.

### 18 Sept 2026 (evening) — TASK-021 fixed: every cold start now checks what's outstanding

Built and verified per the spec in `TASK-021.md`. `GallerySyncApplication.armAutomaticSync()` now
enqueues a real `BackupScheduling.enqueueContinuation()` alongside the existing future-watch re-arm,
on every process start, gated the same way (`isAutomaticEnabled`). 343/343 unit tests pass.

**Verified on the same Moto G, against the real bug rather than a whitelisted or otherwise cheated
environment.** Doze whitelist entry removed first (undoing the 18 Sept diagnostic), fresh install of
the fix over the existing app (settings survived: Camera stayed Sync, 19/19 already verified). The
process was force-killed directly (`am kill`, standing in for the automatic `remove task` kill this
session hadn't reproduced cleanly a second time) to guarantee a genuine cold start, then a real photo
taken:

```
13:02:19.337  WM-ForceStopRunnable: The default process name was not specified.
13:02:19.342  WM-ForceStopRunnable: Performing cleanup operations.
13:02:19.506  WM-ForceStopRunnable: Application was force-stopped, rescheduling.
13:02:20.099  GallerySync/BackupWorke: backup run starting
13:02:26.711  … upload: stored IMG_20260918_130153039.jpg
13:02:29.511  … upload: stored IMG_20260918_130150487_HDR.jpg
13:02:31.903  … upload: stored IMG_20260918_130148173_HDR.jpg
13:02:34.639  … upload: stored IMG_20260918_130146171.jpg
13:02:37.383  … upload: stored IMG_20260918_130144375.jpg
13:02:37.398  GallerySync/BackupWorke: backup run finished: 5 uploaded, 0 failed, 0 remaining
```

**`ForceStopRunnable` still misfired — "Application was force-stopped, rescheduling" — and it no
longer mattered.** The platform-level misdetection is exactly as present as it was all day; the fix
doesn't touch it and was never meant to. What changed is that the same cold start now also runs a fresh
continuation, enqueued after that misfire had already completed, and that continuation is what actually
caught the photo: five files, one run, zero remaining, no delay beyond a few seconds, no second batch
needed to rescue it. This is the first time all day a single real photo synced unattended without help
from a later, unrelated trigger.

TASK-021 moves from investigation to closed-pending-Ian's-review. Not yet done: instrumented-build
testing of the cost caveat in the spec (a cold start for unrelated reasons now always costs a reconcile
check), and confirming behaviour is the same on a device that doesn't reach RESTRICTED or get killed
this aggressively — both lower priority than the fix itself being correct, which tonight's capture
settles.

### 18 Sept 2026 (evening, continued) — TASK-021 fix: skip the cold-start scan during a live optimise chain

Ian caught a gap the fix above shipped with, before it went further: the new continuation enqueue in
`armAutomaticSync()` isn't a content-triggered dispatch, so `BackupWorker`'s existing
`selfTriggered && optimiseChainIsLive()` guard — the one that stops a content-triggered wake from
scanning for no reason while an optimise chain is rewriting files — never sees it and can't apply. A
cold start landing mid-optimise was running an unconditional scan the equivalent content-triggered wake
would have declined.

**Not the 5 Sept self-triggering loop returning.** That loop was per-*file* — each proxy rewrite fired
its own content trigger, so a sixty-file batch could wake the app sixty times. The new call fires once
per cold start, not once per file, so a continuously-running optimise chain inside an already-alive
process causes no extra wakes at all. The real, narrower cost was one unconditional scan on the specific
case of a cold start landing while a chain from an earlier process session was still live.

**Fix:** `armAutomaticSync()` now checks `BackupScheduling.optimiseChainLive(workManager)` — the same
function `doWork()` already uses — before enqueueing the continuation, and skips the scan (not the
re-arm) when a chain is live. 343/343 unit tests pass. Verified on the Moto G: clean install, no crash,
cold-start sync still runs normally with no optimise chain active (`backup run starting` →
`0 uploaded … 0 remaining`, matching a still-caught-up library). The mid-optimise branch itself wasn't
separately forced on hardware — low priority, since it reuses an already-tested function under a
guard shaped exactly like the one `doWork()` has run in production all along.

### 18 Sept 2026 (night) — a documentation review, the privacy pages that were false, and the Settings changes that came out of it

Ian asked for every document, file and earlier session to be reviewed for accuracy, on the grounds that
the app is near completion. What follows is what that found, what was changed in the same sitting, and
what was found and deliberately left for Ian.

**Coverage, stated so it is not over-read.** Read in full: `CLAUDE.md`, this file, `DEFAULTS.md`,
`SETTINGS-AUDIT.md`, the six agent files, the README, all 24 memory files, both Play pages and the
manifest. Read as headers and status lines only: the 23 task specs (about 5,300 lines). Earlier chat
sessions: all 27 were listed and keyword-searched (`before release`, `before submission`, `don't
forget`, `Play Console`, `remind me`, `come back to`, `Fold 8`), and only the newest was read, and only
its tail. Code: targeted rule checks (logging, hardcoded colours, Java files, deletion APIs, token
storage, debug affordances), not a line-by-line review.

#### The privacy policy and the deletion page were false

Both were written 17 Aug 2026, before Archive, proxies, transcoding, deletion sync and the folder write
grants existed, and had not been touched since. Between them they said the app never deletes anything,
never modifies files on the phone, "only ever adds copies", lets you "browse what is stored" in OneDrive,
"may cache copies of files you open so they can be used by other apps", and that signing out "immediately
deletes ... the local index of your files". None of that is true now: Archive moves local files to the
phone's trash, Optimise replaces photos and video in place, Restore writes originals back, the optional
*Ask* policy can move OneDrive copies to the OneDrive recycle bin, the ContentProvider is an unexported
empty skeleton that serves nothing, and `signOut` only calls MSAL's sign-out and clears no table. An
18 Aug session had already noted that the policy "needs revising before submission"; nothing tracked it.

Both pages were rewritten. The policy now states what the app can change on the phone and in OneDrive,
that Optimise is off unless turned on (and that first-time setup can run it once across the chosen
folders), the write access granted through Android's folder picker, that Microsoft is the only network
destination, that there is no analytics, crash reporting or advertising SDK, and that the app never
permanently deletes and never empties a trash. The deletion page no longer claims sign-out clears the
index, and says what removing the app leaves behind. Both are dated 18 Sept 2026.

**They are not published.** The in-app cards read GitHub Pages (`iant8055.github.io/GallerySync/`),
which serves `main`. Until this commit is pushed, the live pages, and so the app, still show the 17 Aug
text.

#### Changes made

- **Initial Optimise is Off for both photos and video (Ian).** The stored defaults were already `false`,
  but `BackupUiState.optimiseVideo` in `BackupViewModel` still started `true` — the same shape as the
  7 Sept photo defect, which was fixed in three places and missed this one. Now `false` in every layer.
- **Settings → Setup (*Run setup again*) removed (Ian).** Along with `restartSetup()` and its three
  strings. CLAUDE.md said this button would not ship; it is gone, so that paragraph is now stale (see
  below).
- **Settings → "Optimise automatically" switch removed (Ian).** The Photos and Video switches, each with
  its own Auto/Manual, remain, as does the manual Optimise button. `BackupViewModel.setAutoOptimiseEnabled`
  and three strings went with it; nothing else called them. The setup tour keeps its own optimise choice.
- **Three cards at the foot of Settings.** *Privacy Policy* and *Delete Account Info* open the pages
  full-screen inside the app (`InAppPage.kt`): a WebView with JavaScript off, no file or content access,
  no mixed content, and navigation confined to `https://iant8055.github.io/GallerySync/` by
  `SupportLinks.staysInApp`; anything else opens in the phone's browser. A failed load says so and offers
  the browser. *Contact Info* opens a native page showing `IanDev@Currently.com`, selectable, with a
  **Copy address** button. **No mail app is launched** — Ian ruled that out after the first build did.
  Seven unit tests pin the URL check, including look-alike hosts.
- **Settings sections reordered:** General, **Backup**, Albums, Sync, Restore, Archive. Backup moved to sit
  under General.
- **Section headings are green bands**, edge to edge, title left-aligned and centred top to bottom, in the
  same green as the heading box on each tab (`heroContainer` / `onHero`). Albums is included although
  Ian's list omitted it. The first version used `accent`, the bright green of the selected nav pill,
  which is not the tab heading box; Ian caught it. The dividers that sat above each heading were removed.
- **`heroContainer` lightened at Ian's request** ("comes off too dark"): light `#003525` → `#0A5238`
  (new `SignalHeroLight`), dark `#074231` → `#0B5039`. `SignalDeepGreen` is the light theme's Material
  `primary`, so it was left alone: changing it would have recoloured every button and switch. This
  changes the hero card on Albums, Restore and Archive and the tour's mockups as well as the Settings bands.

**Verified.** 350/350 unit tests (343 + 7 new), 0 failures. On the Moto G (`ZT422CTZQV`, Android 16), in
light and dark, crash buffer empty after every install: the section order and bands; the band and the
Albums hero card sampling to the same pixel in both themes (`(10,82,56)` light, `(11,80,57)` dark); no
Setup section and no "Optimise automatically" switch; both viewer pages opening, Back and Close working,
and an external link (the Microsoft consent page) handing off to Chrome; the Contact page in both themes,
Copy changing to "Copied" and Android's clipboard preview showing the address.
**Not verified:** the Optimise-video default on a fresh install — the Moto G's stored settings still
carry its earlier choices, so only the unit level and the code were checked; anything at 344dp; anything
on Samsung One UI or API 37.

#### Corrections this file needs, found by the review and not yet made

Recorded here rather than edited into the old entries, so the withdrawn text stays visible.

- **The v0.2–v0.4 checkboxes lag the log.** *(Reconciled by audit on 20 Sept 2026; see that day's "checklist
  audited against the code" entry at the end of this file. The text below is left as it was written.)* Still unticked: *Album modes in the UI*, *Guided first run*,
  *Video transcode*, *Restore replaces the proxy*, *Retry failed items*. The v0.4 deletion-sync line
  still says *"a real cloud deletion has not been performed"*; the 25 Aug entry proves one. Some
  unticked items are genuinely unbuilt (*space saved per album*, *Sync scope toggles*), so they were not
  ticked in bulk.
- **Platform-constraints text is stale:** "Delete and the truncating write are still untested" (and the
  matching line under *The constraint this narrows*) were both verified on 19 Aug in the entries below them.
- **4 Sept (evening) says the disposable OneDrive account left with the Fold 4 and every upload test now
  costs Ian real cleanup.** Corrected 7 Sept in CLAUDE.md only: the Moto G's account and every file on it
  are test data.
- **28 Aug says the vendor-neutral trash wording was "flagged, not changed", and that
  `backup_move_trash_note` is "still unfixed".** Both are resolved in the code: the copy reads
  "Trash/Recycle Bin", and that string no longer exists.
- **19 Aug says there are zero uses of `rememberSaveable`.** `SetupTour` now uses it; whether
  `MainActivity` saves the selected tab is unchecked.
- **`SETTINGS-AUDIT.md`** said the `SafGrowProbeSection` was still in Settings (removed in `c910a26`),
  that `optimise_photos` and `optimise_video` default `true`, and that eight of nine optimise settings
  have no UI. It now carries a banner listing what is stale; its tables are unchanged. **`DEFAULTS.md`**
  carries a matching note that the "automatic" entry uses a pre-28-Aug name.

#### Found and left for Ian

- **CLAUDE.md is stale in three places.** Its project blurb still names an Android ContentProvider with
  on-demand download as the core mechanism (the provider is an empty skeleton with a `TODO(v0.2.0)`, and
  the product is backup, proxies and restore); its *Run setup again* paragraph describes a button that no
  longer exists; and the monetisation rules refer to gating the ContentProvider. Not edited: it is
  Ian's rules file.
- **Settings → Archive shows only "Coming soon"** (it reuses `settings_language_detail`), though Archive
  is built.
- **The tour's Settings mockup** was built to mirror the Settings tab and now differs in section order and
  heading style.
- **No task tracks Play submission:** the Data-safety form, store listing, signing, a release build
  (no minify or signing config was found in `app/build.gradle.kts`), and `versionCode` is still 1.
- **Samsung has not been tested since the Fold 4 left on 30 Aug**, so every September change was verified
  only on the Moto G, on stock Android with Google Photos. No device on API 37 exists except Ian's real
  phone, so `targetSdk 37` behaviour is unverified.
- **The 2-week parallel-run rule** cannot be met in full before Samsung's sync stops on 30 Sept, and no
  document says what is protecting the Fold 8's library today.
- Release builds log INFO with file names (`Logger` emits INFO, WARN and ERROR in every build); there are
  two stale `TODO`s in the source; SignalIcons uses `Color.Black` as an overridden placeholder; there are
  no `TASK-008` or `TASK-009` spec files; `backup-agent.md` still signs as Sonnet 4.6.
- Still open from earlier entries: the TASK-023 Archive re-check, the warning card that vanished once, the
  clean-reinstall re-upload, the wedged Graph GET, and untested proxy recovery.
- **TASK-021 is closed and needs no battery exemption.** The Doze-whitelist run was a diagnostic to find
  the mechanism; the fix was verified without it.

The memory files were corrected in the same pass: TASK-021 no longer listed as an open defect, the docs
map updated, and a note that this file's older checkboxes lag its dated entries.

### 18 Sept 2026 (night, continued) — the How To Guide, a (?) on every item, and what building them found

Ian asked for a complete "How To Guide" explaining every line of every tab (what it is, where its
information comes from, in layman's terms), a **(?)** on each UI item opening a pop-up of that
explanation, and a Settings link to the guide in the Privacy Policy / Delete Account look and feel —
with an accordion version as well if it fitted better. Both were built.

#### What was built

- **One source, four outputs.** The guide is written once in `tools/guide/content/` (eight chapters, 92
  topics, 52 of which have a (?)) and `python tools/guide/build_guide.py` generates
  `docs/how-to-guide.html` (one long page, Privacy Policy style), `docs/how-to-guide-accordion.html`
  (expandable sections), `res/values/help_topics.xml` (the pop-up text) and `ui/help/HelpTopic.kt`
  (the list of topics). Written twice the pop-ups and the guide would drift, so they are not. `--check`
  exits 1 if any output is stale. **To change help text, edit `tools/guide/content/` and rebuild;
  editing the generated files by hand will be overwritten.**
- **Accordion is what Settings opens** (`SupportPage.HOW_TO_GUIDE`), because about ninety topics as one
  scroll is a lot to get through on a phone. Both pages link to each other and carry the same anchors;
  to make the long page the default, change that one URL. The accordion needs no JavaScript (the viewer
  has it off): it is `<details>`, and a link to an anchor *inside* a closed one opens it, which is why
  the id sits on the body and not on `<details>`. Checked in Chrome 152 and on the phone.
- **`ui/help/`:** `HelpButton` (the (?), 28dp drawn, the platform widens the touch area to 48dp — probed
  on the device: a tap 20dp off the icon opens it, 30dp off does not), `WithHelp`, `TitleWithHelp`,
  `HelpDialog` (what it is / where it comes from, bold and lists, **Read more in the How To Guide**
  opening the guide at that topic) and `HelpText` (the pop-up's parser, which mirrors the generator's).
  Colours are `LocalContentColor` throughout; nothing hardcoded.
- **Where the (?) are:** Albums (permission message, the "All Albums" pill for the album cards, the
  filter hint, the two figure lines, "Everything here is backed up", the Sync/Archive lines, the run
  controls, the status line, the merge warning, the Archive confirmation, and both on the album detail
  screen); Restore (headline, message line, selection line, buttons, path line for the folder and file
  lists, action bar); Archive (headline, empty states, check button, file list, the prompt, the leaving
  reminder); Settings (all six section bands and every setting, the destination dialog, the OneDrive
  picker and the deletion confirmation). The section-band and *Folders to back up* tooltips became the
  same pop-up, and their three strings were removed.
- **Not given a (?):** the first-run wizard (it is a guided tour that explains itself; its nine cards
  are covered in the guide's *First-time setup* chapter) and the four link cards at the foot of Settings.

#### Verified

- **363/363 unit tests**, 0 failures. Thirteen are new: `HowToGuideConsistencyTest` fails if a topic has no
  string, is not an anchor in **both** pages, is offered by no screen, or if **any line of any pop-up is
  missing from the guide**; also that every link inside the guide lands and that the Settings card points
  at a file that exists. It was checked to be able to fail: corrupting one sentence in the flat page made
  `everyLineOfEveryPopUpIsInTheGuide` fail, and regenerating restored it.
- **On the Moto G** (`ZT422CTZQV`, 443dp), light and dark, crash buffer empty: every tab; the pop-up in
  both themes; the guide page in Chrome on the phone in both, including a deep link opening its section;
  the destination dialog, the Archive confirmation (opened and **cancelled**; nothing archived), the
  Sync and Archive filtered figures, "Ask" mode's two extra sections (flipped and put back).
- **At 320dp,** emulated with `wm density 360` because the Fold 4 cover screen is gone, so this is weaker
  evidence than hardware. Measured against a build of the untouched `HEAD`. My first layout cost the
  narrow screen its first album card and made *Album Mode Count* wrap with an orphaned word at the Moto's
  width; both were fixed (no heading row of its own, one (?) for the two figure lines). Header card is now
  ~20dp taller than before at 320dp, and identical at 443dp.

#### Not verified

- **The in-app viewer showing the guide.** The pages are not on GitHub Pages until this is pushed, so the
  Settings card and "Read more" currently show GitHub's 404. What was checked instead is the same engine
  (Chrome on the phone) with the same page. The viewer's own behaviour with JavaScript off on a
  fragment link is untested.
- **Anything at 344dp on hardware, on Samsung One UI, or on API 37.**
- The leaving reminder and the Restore/Archive states that need real files (a running restore, an archive
  run, a partial or empty Archive prompt) were read from the code, not watched.

#### Found while writing the guide, for Ian

1. **The Settings video controls are not wired to anything after setup.** *Optimise video*, *Mode*,
   *Older than* and *Quality* are stored, but `VideoOptimiser.run()` — the entry that honours them — has
   **no caller**. The only video pass is the wizard's (`wizardCandidates()` / `runForWizard`), which is why
   MILESTONES still has "Video transcode for old clips" unticked. The guide says this plainly ("saved, but
   the app does not yet shrink video on its own after setup"). The in-app line *"Video is optimised
   separately, and only clips older than the age you set"* is therefore not true yet. Either wire it, or
   hide the controls and reword.
2. **Settings → Restore → *Show empty folders* changes nothing.** Only `RetrieveViewModel` reads it, and
   `RetrieveScreen` is not reachable; the live Restore tab already lists only folders with something to
   bring back. The guide says so.
3. **The Albums header card does not scroll,** so at 320dp with 1.7x text *Sync now* and *Rescan* are
   off-screen. **Pre-existing:** the same overflow was measured on the untouched build. Making the card a
   list item would fix it; not done, because it changes a layout Ian has tuned.
4. **`LibraryChoice.CHOOSE_PER_ALBUM`'s doc comment says it is the default.** The code default is
   `BACK_UP_EVERYTHING` (plan 1), which is what the wizard preselects. The guide follows the code.
5. **Unreachable screens still in the tree:** `RetrieveScreen`, `BrowseScreen`, `SignInScreen`,
   `FirstBackupSettings`. Not touched; the guide describes only what a user can reach.
6. **Not investigated:** with the page missing, the viewer showed GitHub's 404 page with no *could not be
   loaded* overlay, although `onReceivedHttpError` should set it.

#### Keeping it true

A guide that describes the app is only worth having while it does. Any change to a screen's wording,
a control or a number's source should change `tools/guide/content/` in the same commit; the consistency
test catches the guide and the pop-ups disagreeing, but not the guide and the screen.

### 18 Sept 2026 (night, continued) — video optimising is now wired to Settings

Ian, on the previous entry's finding that the Settings video controls did nothing: *"Build this"*. They
now drive a real background chain. What was built, what it found on the way, and what was watched.

#### What was built

- **`VideoOptimiseWorker`**, a separate worker on its own unique chain (`VIDEO_OPTIMISE_WORK`), not the
  wizard's `OptimiseWorker`. The wizard's pass is Area 1 and ignores modes, age and switches; this is
  Area 2 and obeys nothing else. Sharing a chain would also have queued a button press *behind* an
  automatic run waiting for the charger (`APPEND_OR_REPLACE`), so pressing Optimise now would have done
  nothing until the phone was plugged in.
- **`VideoOptimisePolicy`** (pure, 11 tests): automatic runs need setup complete, the master switch, the
  video switch and Mode = Automatic. A chain continues only if the batch attempted something and clips
  remain; failed clips ride in the next batch's input data as exclusions, so every pass either shortens
  the candidate list or grows the exclusions and the chain always ends. Capped at 40 (WorkManager's
  10 KB input limit).
- **`VideoOptimiseLauncher`**: `requestAutomatic()` (charging required, TASK-013 rule 5) and
  `requestNow()` (the button: no charger, replaces a run that is only *waiting*, never one executing).
- **Triggers.** The end of every *complete* backup run (reached by every content trigger and the
  six-hourly net, which is also what notices a clip that has just grown older than the age setting),
  and any change to Optimise video, Mode, Older than or the master switch.
- **Settings.** A video status line and a **Optimise N MB of video** button, four states (ready, none,
  waiting for the charger, working), an "outside the granted folders" line, and the *Straight away*
  warning that had been written and never shown. The photo status lines now appear only while the photo
  switch is on. `proxy_videos_excluded` ("Video is optimised separately…") is gone; it described a pass
  that did not exist.
- **The guide** (`tools/guide/`) says how it works now; the pop-up for the new status line is
  `settings-optimise-video-status`.

#### Two defects in the code that was already there

1. **`VideoOptimiser.run()` could never have optimised anything.** It asked `SafMediaWriter.covers()` —
   which takes folder paths — about a content URI. No path starts with `content://`, so every clip would
   have been reported as outside the granted folders. Fixed with `SafMediaWriter.coverage(uri)`, which asks
   MediaStore where the file is.
2. **Uncovered clips could starve the batch.** The query took the ten biggest clips and filtered after;
   clips outside a granted folder stay candidates for ever, so a run whose ten were all uncovered would
   never reach anything smaller. The optimiser now reads a pool of 2000, narrows it, then takes the batch.

Also corrected: a comment saying transcoded clips are not stamped with the proxy marker. They have been
since 29 Aug (`VideoTranscoder`); it was verified here (marker present in a pulled clip).

#### A hazard avoided

`optimiseChainLive()` is what makes `BackupWorker` decline a content-triggered run as "our own optimise
writes woke it" and what holds back the cold-start scan. It counted a chain that was merely *queued*. A
video batch waiting hours for the charger would therefore have made the app ignore every real new photo
for as long as it waited. The video chain now counts only while **executing**. **Watched:** with a chain
blocked on the charger, a new photo pushed into Camera still produced a backup run and uploaded.

#### Verified on the Moto G (`ZT422CTZQV`), unattended

- **Six real clips**, 20–40 MB each, 1080×1920, Sync album, verified: the chain ran on its own after a
  backup run finished, two batches of three, 26.6→3.4, 39.5→4.9, 34.1→4.3, 27.7→3.5, 39.2→4.9,
  20.4→2.4 MB, 480×854, 0 failed, ~5 s a clip. "3 left, queueing another batch" appeared once and the
  chain ended. A pulled clip: H.264 + AAC, 8.09 s, proxy marker present.
- **No re-upload:** the backup run that followed reported 0 uploaded, 0 remaining.
- **Waiting for the charger:** with the battery simulated as *discharging*, a new clip uploaded, the log
  said *"queueing video optimising for the charger"*, JobScheduler held the job, and Settings said so and
  offered the button. **The button** started it with no charger (tags no longer carry the charging tag)
  and Settings showed *"Optimising video in the background. 1 clip left."*
- **Manual mode:** nothing queued after an upload; the button appeared once the count refreshed.
  Switching back to Automatic queued the chain; restoring the charger released it.
- **Restore round-trip:** one clip restored to exactly 29,256,747 bytes, its original. `markRestored`
  sets a per-file mode override of Backup, so a restored clip is not shrunk again, and nothing was
  queued after it.
- Crash buffer empty throughout. Full suite 374/374 on a clean rerun.

**A fixture trap, recorded so nobody repeats it:** `dumpsys battery unplug` alone leaves the *status* as
FULL at 100 percent, which WorkManager treats as charging, so a charging-constrained job ran anyway.
Use `set ac 0`, `set usb 0`, `set status 3` and `set level 80`, then `reset`.

#### Not verified

- The **outside the granted folders** line and the **failed-clip exclusion** path: neither can be
  produced without breaking something on purpose, and only their pure decision logic is tested.
- The **Older than** gate on hardware (only *Straight away* was run); it is the existing query's
  predicate, not new code.
- **A very long clip.** WorkManager stops a worker after about ten minutes. Three 1080p clips take
  under a minute; a long 4K or 8K one might not fit, would be stopped and retried, and could repeat.
  The wizard's pass has the same shape. Not measured.
- Anything at 344dp, on Samsung One UI, or on API 37.

#### For Ian

1. **Setup and Settings share these preferences.** The wizard's video switch and quality write the same
   stored values Settings reads (`setOptimiseVideo`, `setVideoQuality`), so a phone set up with plan 2 or
   3 and video on now has Optimise video on in Settings and, once an album is set to Sync, will optimise
   its old clips on the charger. What Settings shows is what runs. CLAUDE.md says the two are independent
   in both directions; the code is not. Not changed.
2. **The count on the button lags a backup run** until you change tab (it refreshes on tab entry, as the
   photo count does): probably because a content-triggered run is replaced by its own re-arm, so the observer never sees it SUCCEED (not confirmed). Left alone.
3. The Settings *Archive* band still says *Coming soon*.

### 18 Sept 2026 (evening) — How To Guide moved into General, Language wording, Albums (?) and the tour's Help card

Ian's requests, all done and looked at on the Moto G (`ZT422CTZQV`, over wireless), light and dark:

- **Albums tab.** The (?) after *Tap to filter by mode* is gone. Its guide topic (`albums-filter`) stays in the
  guide as a page with no pop-up, so `HelpTopic.ALBUMS_FILTER` no longer exists. The (?) inside the
  *All Albums* pill (the album cards) is unchanged.
- **Settings.** The *How To Guide* card is now the first thing in **General**, above *Language*, and no
  longer among the cards at the bottom. Those are now three (Privacy Policy, Delete Account Info, Contact
  Info), and the guide says so (`settings-how-to-guide-card` is new; `settings-about-cards` retitled).
  Supersedes the earlier entry's "How To Guide LinkCard placed first among the cards".
- **Language** now reads *Multi-Language Support Coming Soon*. The Archive band had been borrowing the same
  string (`settings_language_detail`), so it has its own now (`settings_archive_detail`, still *Coming soon*).
  Watched on the device: Archive still reads *Coming soon*.
- **The tour's Help card.** It now rings the (mock) How To Guide card, at the top of the Settings picture,
  and says: *Press any (?) for a quick explanation of that line. For everything in one place, open the How To
  Guide at the top of Settings.* The old tooltip that hung off the Albums (?) is gone, and with it the
  `help_albums` string. The ring is a rounded rectangle now (a circle sized to a card's width would have lit
  half the screen). The mock Settings picture gained the same card above Language.
- The generated pages, `help_topics.xml` and `HelpTopic.kt` were regenerated (94 topics, 52 with a (?)).
  Full suite 374/374. Crash buffer empty after install and after the tour.

**Fixture note:** to see the tour I ran `pm clear` on the Moto G, so it is signed out of the test OneDrive
and set up again from scratch; it was left on the tour's Help card. Not a defect.

### 18 Sept 2026 (night) — a setup-only page, and the wizard's link to it

Ian: add *"For a more detailed breakdown of the set up process click here"* to the wizard, opening a
separate page that mirrors the First-Time Setup section of the How To Guide, *"so that is all the user can
access during the Wizard"*. Then, mid-build: *"Put the link in the very first card of the setup"*.

- **The page**, `docs/setup-guide.html`, is generated from the same source as the guide
  (`tools/guide/build_guide.py`): the ten topics of the First-time setup chapter, as expandable sections,
  same anchors. Nothing on it links anywhere but itself. The source marks sentences that only make sense
  beside the rest of the guide with `{{full: ...}}` (two: the *See "Backup destination dialog"* and *See
  "Albums read Off after a backup"* references); the full guide keeps them, the setup page drops them, and a
  reference to a topic outside the chapter fails the build rather than becoming a dead link.
- **The link** is on the **What we'll set up** card (step 3), under Back and Next: the phrase is the
  tappable line and *click here* is underlined in the theme's primary colour. It opens the page in the
  in-app viewer (`SupportPage.SETUP_GUIDE`).
- **Placed on the wrong card first.** I read "the very first card" as the tour's *How the app works* card
  (step 2, the first bubble with buttons) and put it there. Ian's second message, with the phone on step 3,
  corrected that: the first card of the *setup* is *What we'll set up*. It is on that card only now.
- **The wizard reaches nothing wider.** While the wizard is showing, a (?) pop-up leaves out **Read more in
  the How To Guide** (`LocalSetupOnlyGuide`, provided around `SetupTour` in `MainActivity`). Reachable today:
  the destination dialog's two (?) buttons. The pop-up text itself still shows; only the way into the full
  guide is withdrawn. Nothing in the wizard opens the Settings tab, where the How To Guide card lives.
- **Tests.** `HowToGuideConsistencyTest` gains four: the page exists and stays in-app; its topics are exactly
  the chapter's, in order; every link on it stays on it; every line on it is in the full guide. Suite 378/378.
- **Watched on the Moto G** (fresh `pm clear`): the link on step 3, tapping it opens *First-Time Setup* in
  the viewer. The page itself was checked in the browser pane, not in the app.

**Not verified.** (1) The page has not been seen *inside the app*: GitHub Pages returns 404 for
`setup-guide.html` until `docs/` is pushed, so the viewer shows GitHub's 404 page. (2) The (?) pop-up with
Read more hidden was not seen: reaching the destination dialog needs a signed-in OneDrive on the Moto G,
which `pm clear` removed. (3) Dark mode of the link line. (4) The welcome picture (step 1) and the tour
cards (step 2) have no link.

**Also.** The failure overlay (*This page could not be loaded*) did not show for the 404 above: the viewer
displayed GitHub's own 404 page. `onReceivedHttpError` sets the flag; something in the sequence clears it, or
the overlay sits behind the page. Same viewer, so the How To Guide has it too. Not investigated.

**Update, 18 Sept 2026, after the push (`5cacda3`):** GitHub Pages served `setup-guide.html` about a minute
after the push (404, 404, then 200). Watched **inside the app** on the Moto G: the link on *What we'll set
up* opens *First-Time Setup* in the viewer, the page loads with its note, intro and ten collapsed sections, and
a section opens on tap (*Ready to back up*, with its bullets and sub-heading). That closes item (1) under
*Not verified* above. Items (2) to (4) stand.

### 18 Sept 2026 (night) — archived albums missing from the Restore tab (`Test 4`, `Test 5`)

Ian archived two albums on the Moto G. Both archived correctly (files in OneDrive in full, local copies in the
Files app's Trash) and **neither was listed on Restore**.

**Measured, from the phone's ledger (`gallery_sync.db` with its `-wal`).** No rows at all for `test 4` or
`test 5`. Every one of the 59 rows that remained had `remoteItemId = ''` and `isProxied = 1`. At 21:05:43 the
log said `forgot 8 rows for albums no longer on the device`: the archive emptied both albums and the prune
deleted their rows. Restore is built from rows, so there was nothing to list.

**Three faults in a chain, one from each layer:**

1. **The recovered-proxy path wrote an empty OneDrive id.** After the `pm clear` the phone's photos were
   already optimised, so the reconcile matched them as *backed-up proxies* (`markRecoveredAsProxied`) and
   passed `remoteItemId = ""`, although the listing it was reading held the real id. The sibling path for
   files at their exact size had been fixed for this on an earlier day; this one had not.
2. **The prune protects a row only if it has a real id.** `forgetAlbumsNotOnDevice` spares *"a file still in
   OneDrive that can be fetched"* by testing `remoteItemId != ''`. An empty id made the rows look stale, so
   the archive that had just confirmed them in OneDrive also erased the record of them.
3. **Even with an id, an archived optimised file was on neither Restore list.** `restorableProxies` needs the
   file still on the phone; `fetchableFromCloud` said `isProxied = 0`. An optimised file whose album is then
   archived (a path the engine deliberately supports, 26 Aug) is off the phone *and* proxied: neither query
   returned it. This one was found by reading the queries, then proven on the device (below).

**The fix.** (1) `BackupEngine` records the listing's id on the recovered-proxy path. (2)
`confirmStillInCloud`, which runs before an archive, writes the id it has just been handed onto any row that
lacks one (`BackupEntryDao.fillMissingRemoteItemId`; bookkeeping only, touches only rows whose id is empty),
so **rows already in a ledger are healed at the moment their album is archived**. (3) `fetchableFromCloud`
no longer excludes optimised rows; `filesNotOnThePhone` compares an optimised row at the size it has *on the
phone* (`RestoreScope.onDiskSizeBytes`), so a proxy still in its folder is not also offered as a download;
`RestoreViewModel` additionally never lists one entry as both kinds. Suite 382/382 (four new, on the size rule).

**Watched on the Moto G, Test 6 (Off, four optimised photos, rows with empty ids):**
- Set to Archive, *Check these files*: `confirmStillInCloud: 4 confirmed`. Ledger read straight after, before
  the trash tap: `test 6` had 4 of 4 ids (`F6D661310DF0…`), every other album still empty. That is the heal.
- Allowed the trash request: `kept 4 rows for files still in OneDrive but not on the device`, where the same
  step on Test 4 and 5 had logged `forgot 8 rows`.
- Restore tab: **test 6 — 4 files · 13 MB · 0 to restore · 4 to download.**
- Restore: `downloaded … into test 6` four times; the folder holds 3,250,107 / 3,317,173 / 3,782,577 /
  3,104,202 bytes, against 568 to 745 KB for the optimised copies that were archived. Summary *4 back on this
  phone*.
- Test 6 put back to **Off** afterwards, as it was. (Set by tapping the UI, as the user would, on the test
  phone. The app wrote no mode.)

**Not fixed, and why it matters.**
- **`Test 4` and `Test 5` stay off the Restore tab.** Their rows are gone and Restore only offers what this
  app has a record of; the 8 optimised copies are in the phone's Trash. To get them back: restore the 8 files
  from the Trash, let the app rescan (the recovered-proxy path now records ids), and archive again.
- **The other 41 rows on this phone still have empty ids** (Camera and tests 1, 2, 3, 7, 8): the heal happens
  only when an album is archived. **Restore in place fails on them**: pressing Restore on `test 8` gave *None
  recovered. 4 unchanged.* The cause was not read from the log (nothing was written for it); an empty id is
  the one difference from `test 6`, whose downloads succeeded. A backup-time heal, or a guard that offers a
  restore only for a row with an id, would close it.
- **Reinstall is the door for both.** Any phone whose optimised photos are re-recognised after a reinstall or
  on a new phone gets these rows. Older rows on the Fold 8 may already hold empty ids.

### 18 Sept 2026 (late night) — "keep at full size": a per-file pin you can see, a count on the card, a sort

Ian, after the Restore discussion: *"Safer only ('keep this one at full size'): a per-file Backup pin inside a
Sync or Archive album… have Restore set the flag ON, allow the user to switch it off"*, then *"just a check box
I think will suffice"*, a count on the Albums card after *optimised*, and a sort in the album's file list.

- **The pin is the flag Restore already wrote.** `modeOverride = BACKUP` on a row; `FilePin` names it. Restore
  (`markRestored`, both the in-place and the download path) has set it since 27 Aug, hidden. It is now a tick
  box on every file in an album's list, headed **Keep at full size**, and the user can clear it. Ticking can
  only make the app do less, so it has no confirmation. A per-file Archive or Sync was not built: that is
  removal or rewriting decided file by file, a different consent from the album's mode.
- **Archive now honours it.** Until now only the optimiser read the flag. `filesInArchiveAlbums` (the Archive
  tab's list) and `redundantLocalCopies` (what may be removed, and the *Scheduled to leave* count) both leave
  pinned files out, matched by ledger key **or** MediaStore id, since a restore changes the key and not the id.
  This closes the loop found earlier tonight: a restored file in an Archive album was offered for archiving
  again.
- **Albums card:** *3 optimised · 4 kept at full size · 2 pending*, each part only when non-zero (counted from
  files still on the phone). **File list:** the same count, a **Sort** row (Name A–Z, Date newest first,
  Status: failed, pending, optimised, backed up; ties by name), and the tick column. Three new guide topics
  and pop-ups (`album-file-sort`, `album-file-pin`, plus edited `albums-list`, `album-detail`,
  `album-file-status`, and the Restore overview). 96 topics, 54 with a (?).
- **Tests:** 392/392 (ten new: the pin's values, the Archive filter by key and by MediaStore id, and the three
  orderings).

**Watched on the Moto G:**
- Test 6 (Off, four files Restore had put back) showed **4 kept at full size** on the card with no action from
  me, and four ticked boxes in its list: Restore's flag, made visible.
- Unticking one file: list count and card both went to 3. Date sort reversed the order as expected.
- Set to Archive (the confirm dialog, then the Archive tab): **Files to Archive 1**, the unticked file only,
  and the log line *1 files in 1 albums (kept-at-full-size files left out)*. **Yes was not pressed; nothing was
  removed.** Test 6 was then set back to Off and all four files re-ticked, as they were.
- The pop-up for *Keep at full size* in dark mode, and the list in both themes. Crash buffer empty.
- Slip while testing: after sorting by Name my tap unticked a different file than the one I meant; re-ticked.

**Not done, deliberately waiting on Ian.** The larger redesign is still open: Restore listing what OneDrive
holds rather than what the ledger recorded, greyed-out files with a help item, forgetting the mode of an
emptied Archive album, and the header message. Restore ticking files with **no** ledger row depends on it.

**A note for the next reader:** switching the theme while a file list is open returns to the album list (the
open album is not saved across a recreate). It was that way before this change.

**Correction, same night (Ian): the tick box is for restored files only.** *"We only need the check box for
files that have been RESTORED not every file."* The box and its *Keep at full size* heading now appear only
beside files that were pinned when the list opened, which is the files Restore has put back, since Restore is the
only thing that sets the flag; a file never restored has no box and a blank of the same width so statuses line
up. Nothing can be pinned from the list any more, only cleared and put back. The guide's *Keep at full size*,
*An album's file list* and *An album card* topics say so. **The cost:** a file that is unticked keeps its box only
while that list is open. After leaving, nothing records that it was ever restored (the flag was its only trace),
so it shows no box and cannot be re-ticked. Keeping the box would need a stored "restored" marker, which is a
database migration, so it was not done without asking. Watched on the Moto G: `test 7` (never restored) shows
no boxes and no heading; `test 6` shows all four, ticked. Suite still 392/392.

**Layout changes, same night (Ian):** the *Keep at full size* heading is two lines (*Keep at / full size*), centred
over the box column (56dp, no end padding, the boxes' centre is 28dp from the edge). **Sort** became **Sort by**
with a dropdown (the box shows the order in force; the menu offers Name, Date, Status), the same pattern as
Settings' dropdowns. The (?) over the *Keep at full size* heading was removed, so that topic (`album-file-pin`)
is guide-only again (96 topics, 53 with a (?)); the album file list's own (?) still links to it. Watched on the
Moto G: heading over the boxes, dropdown open with its three choices, Date reversing the order. Suite 392/392.

**Correction to the note above (Ian):** the (?) I removed was the wrong one. He meant the one on the **Sort by**
line, not the one over **Keep at full size**. Now: Sort by has no (?) and its topic (`album-file-sort`) is
guide-only; the (?) over *Keep at full size* is back, to the left of the two-line heading, and opens
`album-file-pin`. 96 topics, 53 with a (?). Watched on the Moto G. Suite 392/392.

**Back arrow in an album's file list (Ian):** it was a "←" character in a text button, the only back control that
did not match the rest. It is now `SignalIcons.Back` (Ian's `←┘` return glyph, the one the Restore folder view
and the OneDrive picker use) at 32dp, in the theme's primary colour inside an `IconButton`. Watched on the Moto G:
drawn larger, and tapping it returns to the album list. Suite 392/392.

### 18 Sept 2026 (night) — Restore lists what OneDrive holds; an emptied Archive album is forgotten

Ian: *"I think we need to change how RESTORE chooses what files are available… RESTORE should be able to
download ANY file on OneDrive… and put it right back in the album it came from"*, then *"an archived album that
is empty should just be deleted, and if a RESTORE is called the restore can recreate the Album with the default
mode"*, *"grey out, with a help item"*, and a general message in the Albums header. The tick boxes that came
first were a side path to the loop this closes; the source of the problem was Restore only seeing the ledger.

- **Restore is drive-based again.** It was on 25 Aug (`RestorableFile`: *"we need to be able to restore any file,
  not just the ones we backed up"*), was narrowed to the ledger on 27 Aug, and is this again. The tab shows the
  ledger's rows at once, then `BackupEngine.driveRestoreFiles()` lists each OneDrive folder and adds what the
  ledger does not know: photos and videos not on the phone become **downloads**, files the phone has at full
  size become **greyed-out "already on this phone" rows** (shown, never selectable, explained by a new (?),
  *Why are some files greyed out?*). A file of the same name at a different size is neither offered nor greyed
  (it may be an edit, and Restore never overwrites an edit). Comparison ignores case of folder and name.
  Settings' **Show empty folders** now does what it says (folders with nothing to bring back are listed only
  when it is on).
- **It also mends the ledger.** Listing a folder fills in the OneDrive id of any uploaded row that never
  recorded one, when name and size match. The restore-in-place failure on the 41 empty-id rows was this.
  A restore or download with no id now says *OneDrive has not been checked for this file yet. Press Refresh*
  instead of asking OneDrive for an empty id.
- **A downloaded file with no ledger row gets one, written when it arrives.** Never before: a row for a file
  never on this phone would sit in the ledger as "missing", the shape the cloud-deletion review looks for. The
  row is uploaded, pinned to Backup (Restore's flag), and known, so it is neither re-uploaded nor re-shrunk.
  This is the answer to the note for files with no record that Ian found unclear: yes, and it is the same
  flag as every other restored file.
- **An emptied Archive album is forgotten.** `forgetEmptiedArchiveAlbums()` runs right after an Archive run
  removes files: an Archive album no longer in the scan has its preference row deleted (nothing is written).
  It drops off the Albums tab and returns as a new album at the default mode, which can never be Archive
  (`canBeDefault`), when Restore refills the folder. Never from a plain rescan, and guarded like the prune
  (full access, non-empty scan). **`CLAUDE.md` is updated:** *emptying an album retires its mode* replaces the
  27 Aug rule that it does not. The 27 Aug block that lists an Archive album with a stored mode at zero files is
  left in as the safety net for one that predates this.
- **The Albums header says where to look:** *To restore archived files or albums, check the Restore tab.*
- **Guide:** Restore chapter rewritten around what OneDrive holds; new topic *Why are some files greyed out?*;
  Archive, Albums, Settings and Help topics changed where they said an emptied Archive album keeps its mode.
  97 topics, 54 with a (?). Suite 398/398 (six new, on the classification rule).

**Watched on the Moto G** (a build installed, the Restore tab opened): the tab listed **10 folders**, including
**`car show` (34 files, 0 to restore · 34 to download)** and **`PauseTest` (11 files, 11 to download)**, folders
that exist in OneDrive from earlier tests and were never on this phone, and the log said
`driveRestoreFiles: 53 to download, 4 already here` (34 + 11 + Test 4's 4 + Test 5's 4 = 53; the 4 here are Test
6's restored files). Crash buffer empty.

**Not verified yet.** Ian began using the phone while this was being checked, so I stopped sending taps. Not seen:
(1) downloading a drive-only file end to end, and the ledger row it writes; (2) the greyed-out rows and their
(?) on screen; (3) the Archive step forgetting an album's mode on a real run; (4) the header line; (5) dark mode of
the new rows; (6) a large library, where listing every folder may take a while (the tab shows *Checking
OneDrive…* and the ledger's rows first, but it has not been timed).

**Cost to keep an eye on:** the drive pass lists every OneDrive folder each time the tab is entered or Refresh is
pressed, one request per page per folder per search root. The Albums tab already does the same walk (about 55 s
for 3,335 files across six albums on the Moto G).

**Fix, same night (Ian): the Restore tab reloaded from scratch every time he left and came back.** Two causes.
Entering the tab always re-listed OneDrive, and while it did the list fell back to only the ledger's rows (the
folder count dropped and `car show` and `PauseTest` vanished) with the figure a dash and the bar running. Now the
OneDrive listing is held in the view model (`DriveListing`, split out of `driveRestoreFiles` as
`listDriveFolders`) and read again only when it is more than ten minutes old or **Refresh** is pressed (Refresh
is forced). Every entry still re-compares the held listing with the phone as it is now, which is cheap and stays
right after an archive or a restore. Nothing is blanked while re-reading: the dash and the bar show only when
there is nothing yet to show. Watched on the Moto G: one `cloudFolders:` read (23:50:27) on first entry and none
after leaving to Albums and returning, the full 10-folder list on screen two seconds after returning with no
dash, bar or "Checking OneDrive…". Suite 398/398. A cost that remains: a file added to OneDrive from a computer
can take up to ten minutes to turn up unless Refresh is pressed.

**Albums header trimmed, names bold, pointer moved (Ian, late night).** The four mode buttons are one line of four
(each half the width they were as two rows of two; chip padding 12dp to 4dp). The **Total Album Count/Size** line
is gone entirely, taking its (?) with it, so the *Album Mode Count* line has none and `albums-totals` is
guide-only (retitled *Album Mode Count*; 97 topics, 53 with a (?)). *To restore archived files or albums, check
the Restore tab* moved from the Albums header to the **Archive** tab's card (`archive_restore_pointer`). Album
names on the Albums list are bold, and so are the file names inside an album's list (Ian said "file name on the
album tab list"; both were done, and either can be reverted alone). **Read of an ambiguous request:** "the album
count line" was taken as *Total Album Count/Size*, the one containing those words, not *Album Mode Count*.
Watched on the Moto G: header on three lines instead of five, Archive card showing the pointer, bold names on
both lists. Suite 398/398.

**Drill-down rebuilt to match Restore (Ian, 19 Sept).** `AlbumDetailScreen` was rewritten: (1) the top is the same
green card the other tabs open with (`heroContainer`/`onHero`, 28dp corners), holding the return arrow in the
card's ink, the **folder name in bold**, the mode line, the counts and the controls; (2) **Sort by and Keep at
full size share one line** inside it; (3) each file is a rounded card in Restore's `FileCard` style (22dp corners,
1dp outline, name in `titleMedium`, two columns from 600dp); (4) **backed up on one line and optimised on the one
below it**. **Correction to the earlier bold request:** Ian meant the *folder* name, not files. File names are
regular weight again, matching Restore; album names on the Albums list stay bold. The header is drawn in the file
rather than through `HeroCard` because that card splits into two columns on a wide screen. The counts on the green
are plain text in the card's ink (the old coloured counts used the theme's primary and tertiary, which are not
made to sit on green). Watched on the Moto G in light mode: test 6 (four ticked cards, Keep heading beside Sort by)
and Camera (two-line marks, one ticked). **Not checked:** dark mode of the new header, the 600dp two-column layout.
Suite 398/398.

**Follow-up (Ian, 19 Sept): size and marks on one line.** *"Since you moved it around you can put backed up ·
optimized on the same line next to size."* Each file card's second line is now *3 MB · ✓ backed up · optimised*,
each part in its own colour (one annotated `Text`, so it wraps if a line is ever too long). Watched on the Moto G:
photo rows and the longest case, video rows (*29 MB · video · ✓ backed up · optimised*), all on one line at 360dp.
Guide updated. Suite 398/398.

**"Backed up" text made obviously green (Ian, 19 Sept).** In the light theme `primary` is `#003525`, nearly black,
so the mark read as dark grey. New theme token `GallerySyncColors.safeText`: `#157F37` in light (about 5.1:1 on
white and 4.7:1 on the off-white surface, clearing the 4.5:1 that 14sp text needs; a brighter green failed it),
and the existing `SignalBrightGreen` in dark. Used for the drill-down's *backed up* mark only. The Albums cards'
*verified in OneDrive* line is still the deep green and could take the same token. Watched on the Moto G in both
themes, which also gives the new drill-down header and cards their dark-mode check. Suite 398/398.

**Restore keeps its list when the app is closed (Ian, 19 Sept: "when you close the app the RESTORE tab loses its
list").** The held OneDrive listing was memory only. It is now also written to `cache/restore-drive-listing.txt`
(`DriveListingStore`, `DriveListingCodec`: one tab-separated line per file, escaped, versioned, and anything cut
short or foreign decodes to `null` so a bad file costs a read of the drive and never a wrong list). It is loaded on
the first refresh after a launch, shown at once and refreshed behind it if older than ten minutes, stored under the
OneDrive folder it was read from (a different destination is treated as nothing stored), not written after a
partial read, and cleared on sign-out. Watched on the Moto G: listing written (12.5 KB), app force-stopped and
reopened, Restore opened, the full 10-folder list on screen three seconds in with no dash or bar and **no
`cloudFolders:` read in the log**. Nine codec tests, including tabs, newlines, backslashes and non-Latin names in
file names. Cost: the file holds names and OneDrive ids of the library, in the app's private cache.

**Restore's cards match the Albums cards (Ian, 19 Sept).** Both already shared the shape (22dp corners, 1dp outline,
18 by 14 padding); the difference was type. Folder cards: name in bold `bodyLarge`, detail lines in `bodySmall`, no
gap between lines, 14dp vertical padding (was headline-sized name, larger lines, 18dp). File cards: name in
`bodyLarge` (not bold: files are not bold, folders are), `bodySmall` lines. **The drill-down's file cards got the
same type, so it still matches Restore.** Watched on the Moto G: the folder list and a folder's files. Suite
407 (398 plus the nine codec tests).

**Swipe animation on the Restore folder cards (Ian, 19 Sept: "add some animation to the Swipe").** The card is now
pulled aside as the finger moves (55 percent of the travel, capped, so it has weight), with a panel uncovered
behind it that fades in with the pull: a tick on the right for selecting, a cross on the left for putting down. A
short haptic tick fires when the pull passes the point where letting go will act, and again if it is taken back
under. On release the card springs back (medium bounce) while the selection applies. Colour and border ease over
220 ms between selected and not, on folder cards and on file cards, and the arrow and tick crossfade. Watched on
the Moto G with a slow swipe on `car show`: mid-swipe the card had slid right with a mint tick panel behind it;
after release it had returned and sat in the selected look. **Not judged:** the feel of the spring and the haptic
(a screenshot cannot show either), and the pull reaches about 65dp, which pushes a card's right edge past the
screen edge. Suite 407/407.

**Restore's folder view has the Albums drill-down header (Ian, 19 Sept).** Inside a folder the top is now the same
green card as an album's file list on the Albums tab: the return arrow and the folder name in `headlineSmall`
bold at the left (three quarters), **Files in this folder** under the name, and the number centred in the right
quarter; below, across the card, what is selected, the instruction or result line, and Select all and Clear. The
path line is gone inside a folder (the header names it and carries the way back) and stays on the folder list,
which keeps the card every tab opens with. The (?) that sat on the path line is beside the name, and the card's
own (?) is beside *Files in this folder*. Watched on the Moto G on `car show` (34 files). The dead
"open folder" branches inside the folder-list `HeroCard` call were left in place. Suite 407/407.

**Restore headers, second pass (Ian, 19 Sept).** (1) Inside a folder: **Files in this folder** moved under the
number, and the number centred in the right *half* (not quarter) of the card; the left half holds the way back
and the folder name (bold, up to two lines). (2) The folder list has the same layout: **Folders to** with
**Restore** directly under it in the left half at `headlineMedium` (Ian: "increase the size"), the number centred
in the right half. Both share `HeaderLower` (selected summary, message line, and the two buttons), so the
`HeroCard` call and its now-dead open-folder branches are gone. (3) The **All folders** path line and its (?) are
gone from the list, and the (?) beside the folder name inside a folder is gone too (`Breadcrumb` deleted). Their
topics, `restore-folders-list` and `restore-files-list`, are guide-only now: 97 topics, 51 with a (?). The title is
two strings, `restore_hero_label_top` and `restore_hero_label_bottom`. Watched on the Moto G: the list header (10)
and the `PauseTest` header (11). Suite 407/407.

**Restore folder instruction (Ian, 19 Sept).** *"Nothing moves until you press Restore"* became *Then press
Restore.*, on its own line under *Tap a file to select it.* (`restore_intro_files`, one string with an escaped
newline). The first build showed both sentences on one line because the newline reached the XML as a real line
break instead of `
`; fixed and watched on the Moto G. The guide's message-line topic says the same, and still
says nothing moves until Restore is pressed. Suite 407/407.

**Restore folder header, third pass (Ian, 19 Sept).** Inside a folder: the (?) on *Files in this folder* and the (?)
beside Select all and Clear are gone; the message's (?) now sits straight after *Tap a file to select it. / Then
press Restore.* rather than at the far end of the row (`HeaderLower(compact = true)`); the number is larger
(`displayMedium`, was `displaySmall`) and, with *Files in this folder*, sits about 14dp lower, with the row now
top-aligned so the folder name stays where it was. The folder list's number took the same size so the two headers
still match ("make # larger to make restore main header" was read that way); the list header otherwise keeps its
(?) buttons, which Ian did not ask to change. Watched on the Moto G: the `PauseTest` header and the list header.
Suite 407/407.

**Archive tab restyled to match Albums and Restore (Ian, 19 Sept).** *Header:* the Restore list's layout, **Files
to** with **Archive** directly under it on the left half (`headlineMedium`, the (?) beside *Archive*) and the number
of files waiting centred in the right half (`displayMedium`); under them the album names, the intro, the pointer
to Restore, and the Check these files control, all unchanged in behaviour. *Files:* each is a rounded card in the
Restore file card's style (22dp, 1dp outline, 18 by 14 padding, name in `bodyLarge`, the line under it in
`bodySmall`, the tick, cross or spinner still at the right), listed with 10dp between and in two columns from
600dp. The **Files** heading and its (?) stay. The header was the shared `HeroCard`; the tab no longer uses it.
The prompt (*All files validated*) was left as it was. The title is two strings, `archive_hero_label_top` and
`archive_hero_label_bottom`; the guide's *Files to Archive* and *file list* topics describe the new layout.
Watched on the Moto G with test 8 (which Ian had set to Archive): the header and four cards. **Not checked:** dark
mode, the tick and cross marks after a check, the two-column layout, or the prompt under the new header. Suite
407/407.

**Restore folder header, fourth pass, and the checks Ian left for the morning (19 Sept).**

*Header (Ian, 19 Sept).* Inside a Restore folder: the folder name is larger (`headlineMedium`, bold, was
`headlineSmall`) and centred vertically on the same line as the number (the row is `CenterVertically`; the 14dp
top offset on the number is gone). *N selected · X MB to recover* now sits in the left half on the same line as
*Files in this folder*, in the right half; it is stacked on two lines (`restore_selected_summary_stacked`: the count,
then the size) so it breaks between the facts rather than mid-phrase, and its (?) follows it. The row keeps a
44dp minimum height, so the card does not change height as files are ticked; watched with zero and one file
selected. The one-line instruction is now **Tap a file → Then press Restore** on a single line
(`restore_intro_files`), and once something is selected the first half reads **Tap again to deselect it → Then
press Restore** (`restore_intro_files_selected`). Ian typed `>>>>` for the separator; I used an arrow, which is a
one-line change if he wants the literal characters. The guide's `restore-hero`, `restore-message-line` and
`restore-selected-summary` topics say the same; regenerated (97 topics, 51 with a (?)).

*Verified on the Moto G, all against the debug build installed 19 Sept:*
- **Archive step forgets an emptied album's mode: yes, on a real run, twice.** test 8 (`ARCHIVE`, four files, all
  verified): Check these files, Yes, Allow. The four files were renamed in place to `.trashed-<expiry>-<name>` with
  unchanged sizes, `album_preferences` lost the `test 8` row, and the Albums tab dropped to *0 Archive* with no test 8
  listed. Repeated with test 7 after setting it to Archive through the mode dialog (Cancel/Archive confirmation
  shown as designed): same result.
- **A real download of files the app had no ledger row for: yes.** test 8 was offered by Restore from the OneDrive
  listing (*4 to download*), swiped to select, restored: four full-size files landed in `DCIM/test 8` (3.3 MB against
  the 589 KB optimised copies), and four ledger rows were written, each `UPLOADED`, `remoteSizeBytes` equal to
  `sizeBytes`, not proxied, `modeOverride = BACKUP` (Keep at full size). The folder then left the Restore list
  (10 to 9), and test 8 came back on the Albums tab as a **new album at Off**, *4 kept at full size, 4 verified in
  OneDrive*, with no `album_preferences` row written for it. That is the whole loop the redesign was for, and it
  does not repeat: archive, restore, and the album does not re-archive.
- **Greyed-out rows: yes.** test 1 after a restore-in-place showed *1 already on this phone* on the folder card;
  inside the folder the file is faded and reads *Already on this phone · 2 MB*, tapping it selects nothing, the line
  *Greyed-out files are already on this phone.* appears above the list, and its (?) opens *Why are some files greyed
  out?*.
- **Restore in place after ids are healed: yes**, by accident. A stray tap of mine landed on the Restore bar with
  test 1's one selected file and ran it: *1 back to full quality*, the file now greyed and kept at full size.
- **Dark mode:** the Albums tab and drill-down, the Restore list, the Restore folder header with and without a
  selection and its cards (selected and not), the Archive tab header and cards, and the *Files ready to Archive*
  dialog. All readable. `cmd uimode night no` restored afterwards.
- **Two columns from 600dp:** emulated with `wm density 180` (720px wide = 640dp), reset afterwards to the
  physical 260. Albums, the Restore list and a Restore folder (PauseTest, 11 files) all lay out in two columns and
  read correctly. **The Archive tab's two columns were not seen**: nothing was waiting to archive at that moment.
- **Swipe select on a folder card: yes**, one swipe with `adb shell input swipe`, folder selected with the count
  and size in the header. **The feel of the spring and the haptic cannot be judged over adb** and remain Ian's call.

*Defect found and fixed.* After an Archive run finished, `ArchiveViewModel` re-read the file list but not
`archiveAlbums`, so the tab kept believing the emptied album was still an Archive album. The header then showed only
the Restore pointer, and *No album is set to Archive...* did not appear until the app was restarted. The finish block
now re-reads `engine.archiveAlbumNames()` after `forgetEmptiedArchiveAlbums()`. Confirmed on the Moto G by the test 7
run above: the message appears immediately after the run with no restart. No unit test: it is ViewModel state
reading the engine, and the run needs the platform's trash dialog.

**Not verified:** performance of the OneDrive pass on a large library (the test account holds about 100 files);
the Archive tab's two-column layout; the haptic and spring feel; the tick and cross marks in dark mode (ticks were
seen in dark and light, crosses were not, since every file verified). Suite 407/407. Uncommitted at the time of
writing.

### 19 Sept 2026 (morning) — a 2,079-file wizard run, and "For a more detailed explanation Click Here" on four cards

**The run.** Moto G, `pm clear` at 10:46 after Ian had reset both sides (1,888 images copied into `Pictures`, ten `Temp`
folders of ten photos in `DCIM`, OneDrive emptied, the old test folders removed, full-size copies of the files pulled
back to the phone). Wizard plan 1, 3-minute delay, Close; the app left closed with nothing in Recents. **2,079 of 2,079
files uploaded, 8.03 GB, every `remoteSizeBytes` equal to `sizeBytes`, every row carrying a remote id, none proxied, none
failed, none remaining**, started 10:59 and finished 12:22:26 (about 83 minutes, ~2.5 MB/s on the large videos, one to
three seconds per photo). The last worker result was `SUCCESS` (`backup run finished: 1 uploaded, 0 already there,
0 failed, 0 deferred, 0 remaining`), and reopening the app showed the wizard on its final card, *"Congratulations your
backup has finished successfully and has been verified. Press Finish."*, at 100%.

**The delay did not fire on time. Not new: this is the 5 Sept and 15 Sept batching hold, and I reported it wrongly at first
as unexplained.** Armed 10:51:59 for 179.9 s, due about 10:55. From then `Ready: true` with no unsatisfied constraint
(`CHARGING` satisfied, AC powered), `Standby bucket: RARE`, empty pending queue, nothing active, process alive but frozen
(`freezing ... reason = moto_freezer`). Not dispatched by 10:59, four minutes late. Released at 10:59:13 by
`cmd jobscheduler run -u 0 -n androidx.work.systemjobscheduler com.gallery.sync 4` (no `-f`, so constraints were
respected); it started within a second, which is the same release that opening the app gave on 5 and 15 Sept. Ian's
correction, which I record because I had first called it unexplained: the earlier entries above already hold the
mechanism (JobScheduler batches a ready job from a non-active app until five are ready or 31 minutes pass, and `pm clear`
drops the app into RARE within seconds of Close) and the decisions (the delay card promises no time; the first backup
waits for the charger). One difference worth keeping: this release came from `cmd jobscheduler run`, not from opening the
app, so the batching hold is not specific to a foreground transition. It confirms nothing else and changes nothing.
The wizard's first `BackupWorker` (`manual`, `all_albums`), enqueued at 10:46:28 seconds after the wipe, is recorded
`FAILED` with no output; that is almost certainly the "backup run starting (not manual) three seconds after launch,
before any folder was granted" already noted on 15 Sept, but I did not read its log to confirm it.

**The links.** A line reading *For a more detailed explanation Click Here* (`wizard_detail_link`,
`wizard_detail_link_action`) now ends the four cards **Choose folders to back up** (step 4), **Cloud Storage** (5),
**Choose your backup plan** (6) and **Ready to back up** (8). Each opens the setup guide page at that card's own
section, via the existing `InAppPageDialog` `anchor` parameter and `DetailAnchors` in `SetupTour.kt`
(`setup-choose-folders`, `setup-cloud`, `setup-backup-plan`, `setup-ready`); the step-3 link is unchanged and still opens
the page from the top. `SetupGuideLink` takes the string resources as parameters. `HowToGuideConsistencyTest` gained
`everyWizardDetailLinkLandsOnASectionOfTheSetupPage`: the four step keys are exactly 4, 5, 6, 8 and each id is on
`docs/setup-guide.html`. Suite green.

**Installed and launched; the links themselves are NOT seen on the phone.** Back is withdrawn once a backup is done
(`canGoBack` is false at `WizardBackupPhase.DONE`), so the four earlier cards cannot be reached from where the wizard
stopped, and *Run setup again* is a testing affordance the project says not to test against. Seeing them takes a fresh
wizard: `pm clear`, then Ian signs in at the Cloud Storage card, which the agent cannot do. **Not verified:** that the
link is readable in both themes on each card, that the card layout holds with the extra line (step 4's card is the
tallest), that each link lands on the right section inside the in-app viewer, and that the anchors resolve on the
published page (the test proves the working tree, not GitHub Pages). Uncommitted at the time of writing.

**Follow-up, 19 Sept 2026 (afternoon): the links have been seen, by Ian.** After a second `pm clear` at 12:44 Ian took the
fresh wizard over on the Moto G, signed in himself, and reported *"done - looks great"*. That answers the "not seen on
the phone" line above for the links as a whole; he did not say which of the smaller checks it covered (dark mode, the
Choose folders card's height, each anchor). The agent's own incidental sightings while both were tapping at once (the
agent had not realised until the log showed a signed-in scan): the in-app **First-Time Setup** page open twice at a
section, expanded and outlined, first *Cloud storage: signing in and choosing where backups go* and then *Choose your
backup plan*, which is what the step-5 and step-6 links are meant to open. The agent's taps may have overlapped
Ian's, so it does not claim to have driven those two; it only saw where the page was. The other two links (steps 4 and 8)
were not seen by the agent. Lesson for the log: **when the phone is shared, read `logcat` for a signed-in scan before
tapping**, since a wizard moving without my input means somebody else has it.

### 19 Sept 2026 (afternoon) — an edited photo is not a deleted photo

**The defect (found by reading, from Ian's question about "Wait before asking").** The ledger identifies a file by folder,
name, size and modified time. Saving an edit over `IMG_1234.jpg` changes the last two, so the original's row stops
matching, is stamped missing, and (once past the waiting period, under **Ask**) `cloudDeletionCandidates` offers its
OneDrive copy for removal on the *Gone from this phone* list, while a file of exactly that name is in the gallery. The
edited file uploads as a new one (OneDrive `rename` conflict behaviour), so nothing is lost, but every in-place edit would
have looked like a deletion. Ian: *"we can't have every edited file look like a deletion"*, then agreed the rule below.

**The rule (Ian's, 19 Sept).** *Don't offer a file if something with the same name is still in that folder on the phone.*
`EditedInPlace` (new, pure, Android-free): key is the folder lower-cased plus the name with any `_restored` suffix taken off
(`RestoredAlbum.originalNameOf`), so folder case never matters (TASK-023) and name case does. Applied twice in
`SyncDeletionsToCloud`: `candidates()` now scans and drops any candidate whose key is on the phone, and returns **nothing**
when media access is not FULL or the scan is empty (the same refusal `delete()` already made, and toward not offering);
`delete()` re-checks each approved file by the same key beside the existing name-and-size check, so a photo edited after the
list was drawn is counted as back (`cameBack`) and its OneDrive copy is left alone. A file moved to another folder was already
caught by `contentSignature` (name and size anywhere). It errs toward not offering: a new photo reusing a deleted photo's
name in the same folder hides the old one, which costs a cloud copy left behind, the setting hardest to regret.

**Tests.** `EditedInPlaceTest` (5) and `SyncDeletionsToCloudTest` (7, the real class against mocked ledger, scan and drive):
an edited photo is not offered while a really deleted one is; folder case; same name in another folder still offered; a
`_restored` name counts as here; an empty scan or PARTIAL access offers nothing; Leave never scans; an approved file edited since
the list was drawn is not deleted. **Mutation check:** with the fix disabled, 4 of the 7 fail (the edited, case, `_restored` and
approved-then-edited cases), and the other 3 are the ones that should not change. Suite green. The guide's *Gone from this
phone* topic now says an edited photo is not listed and the unedited original stays in OneDrive as a backup; regenerated.

**Not verified on the phone.** The Moto G was in Ian's hands, mid-Archive-test (Temp 9 set to Archive, 10 files waiting), so the
app was not restarted and this build is **not installed**. A hardware check needs: an uploaded photo edited in place
(`echo x >> file` plus a media scan), a second photo moved out of its folder, **Ask** set, the two rows' `localMissingSince`
back-dated past the 1-day minimum (there is no `sqlite3` on the phone: force-stop, pull the database with its `-wal`/`-shm`,
edit on the PC, push back), then the *Gone from this phone* list should show the removed photo and not the edited one.

**Design still open with Ian (not built).** Replace the days-based wait and the Settings review list with a screen shown when
the app opens, listing files gone since it was last shown, with Keep / Remove per file, defaults to keep. Open: the
Archive-removed marker column and migration (mandatory under that design, otherwise every Archive run raises the screen),
whether anything starts selected, and whether it shows on every return or only when there are new files. Also decided in
principle: external storage is excluded from backup (scan `VOLUME_EXTERNAL_PRIMARY` only, refuse non-primary folder grants),
which removes the SD-card-out case from the false-absence list; not built either.

### 19 Sept 2026 (afternoon) — opt a file out of Archive, on the Archive tab

Ian: *"right now EVERYTHING in a selected folder gets Archived... allow a user to select certain files to opt out of
Archiving in a folder, the database to remember that choice, and the option to reverse it at a later point when going
back into the Archive Tab... if a user put a new file in an Album set as Archive it will appear... swiping left greys out
the files so it won't Archive; [swiping the other way] makes a greyed out file ungrey and be archived."* His second
"swiping left" was read as **right**, matching Restore's directional swipe; not yet confirmed with him.

**Design: no new column.** The opt-out **is the file's pin** (`FilePin`, `modeOverride = BACKUP`, the flag Restore already
writes and Archive already skips). In an Archive album the pin means exactly "do not offer this file for removal", so the
per-file choice is stored, survives the app being closed, and is there to reverse. It can only make the app do less, so it
adds no removal and needed no migration (CLAUDE.md's escalation for a schema change did not apply). `FilePin`'s comment and
the Archive paragraph of CLAUDE.md now say the Archive tab is a second thing that sets it.

**What was built.**
- `BackupEngine.archiveFiles()` returns `ArchiveFiles(toArchive, optedOut)`; `filesInArchiveAlbums()` is now just
  `.toArchive`, so **every path that removes a file still reads a list that never holds a pinned file**.
  `FilePin.split` gives both halves (`withoutPinned` is its first). `setArchiveOptOut(item, optedOut)` writes or clears the
  pin: it refreshes the ledger first if a just-arrived file has no row and returns false, saving nothing, if there is still none;
  putting a file back also clears a row pinned under a drifted key (matched by MediaStore id, as when a restore rewrites the mtime).
- **Opted-out files are never in `ArchivePlan`.** They live in `ArchiveUiState.optedOut`; the check and the removal act on the plan
  alone. Defence in depth: `nextRemovalRequest` (first call of an operation) drops any plan entry opted out since the check.
- `ArchivePlan.reconciledWith(files, checkFinished)` (pure) merges the plan with the phone after a swipe or when the tab is opened:
  the plan **only loses files or gains unchecked ones**, so nothing unconfirmed can become confirmed, and if a check had finished
  and a file joined (or nothing confirmed is left) every mark is reset and the screen returns to waiting for a check, rather than
  saying "all files validated" about a file nobody validated. `refreshFiles()` applies it every time the tab is opened past IDLE
  (before, a file added after a check or a run did not show until the app restarted; from IDLE `load()` does it).
- `SwipeChoiceBox` (`ui/common`): the Restore folder card's swipe feel (threshold, resistance, spring, haptic, tick and cross
  revealed) written once, directional, with a TalkBack custom action ("Keep on this phone" / "Archive this file") for anyone not
  swiping. Restore's own folder card still has its own copy and could move to it. Disabled during a check or a removal.
- Archive tab list: every file in an Archive album in name order, opted-out ones faded (`alpha 0.5`, as Restore fades unavailable
  files) reading *size · Not archiving*, a hint line under **Files**, header count = files that *will* be archived. An album holding
  an opted-out file is not empty, so `forgetEmptiedArchiveAlbums` leaves its mode alone.
- Guide: `archive-file-list` explains the swipes, that the choice is remembered, that the mode stands, and the screen-reader actions;
  `archive-hero` and `archive-overview` no longer count or check kept files. Regenerated.

**Tests (suite green).** `ArchiveOptOutTest` (7, the real engine against mocks): an opted-out file is never in the removal list
(by key, and by MediaStore id when the key drifted), other albums excluded, ordering, the pin is written, nothing is saved and false
returned when there is no row, and putting a file back clears both keys. `ArchivePlanReconcileTest` (8): a swiped-out file leaves and
the rest keep their marks, a put-back or arrived file joins unchecked and resets a finished check, a gone file leaves, opting out
every confirmed file asks for a new check, nothing unconfirmed can become confirmed, ordering. `FilePinTest` +1 (`split`).
**Mutation checks:** letting pinned files back into the removal list fails 6 of 18; a joined file arriving pre-confirmed fails the
unchecked-merge test.

**Verified on the Moto G** (Temp 9, set to Archive by Ian, 10 files waiting), debug build of the day:
- Swipe left on one file: it fades, reads *1 MB · Not archiving*, header 10 to 9; the database row was `modeOverride = BACKUP`.
  A second left swipe did nothing (directional). Force-stop and relaunch: still faded, still 9.
- **Check these files** with one kept: *All files validated*, 9 confirmed ("frees 18 MB"), the kept file with no tick.
  **Yes** raised Android's dialog **"move 9 photos to trash"** (not 10), so the kept file is excluded all the way to the platform.
  **Deny** was pressed: *Nothing was removed*, 10 files still in the folder, none `.trashed`.
- Swipe right (after that run, phase DONE): back to 10 and no pinned rows left in the database.
- A new photo copied into Temp 9 while *All files validated* was showing: opening the tab again showed 11, the old prompt
  withdrawn and Check back (the new file is unverified). It was swiped out (ledger row already existed, pinned, state `UPLOADED`:
  the automatic sync had already sent it), count back to 10; swiped back in to clear the pin; the file removed from the phone.
- Dark mode: the faded row and the list are readable. Light mode restored.
- **Nothing was archived**: every Yes was answered Deny.

**Left over from that test, in OneDrive:** `zz_arrival_test.jpg` (7 MB) in `Temp 9`, uploaded by the automatic sync before it was
removed from the phone. The app never deletes from OneDrive, so it stays until removed by hand; Restore will list it as available.

**Not verified.** The two-column layout with the new rows (the density trick was not repeated); TalkBack's custom action; the swipe
feel with a real finger; an opt-out while a check is running (disabled, so only reasoned); an Archive album that holds *only*
opted-out files after a run (`forgetEmptiedArchiveAlbums` reads the phone, so it should stay, but was not run). Two wording
points not changed: the Albums tab counts a pinned file as *kept at full size* even in an Archive album, and a file the user edits in
place gets a new key, so its opt-out is lost and it rejoins the list unchecked (visible before anything is archived, as every file is).

**Follow-up, 19 Sept 2026 (later): a file swiped out of Archive and back keeps its green tick.** Ian, from the phone: swiping a file out
and back in on the Archive tab did not bring its green tick back. It was my design: a file that rejoined was treated as never
checked, and any join reset every tick and withdrew the *All files validated* prompt. Wrong for a file that was verified a moment
ago and only set aside. `ArchivePlan.reconciledWith` now takes the confirmations of the current check that the user has swiped out
(`ReconciledPlan.setAside`, held in `ArchiveUiState.setAside`, never shown and never acted on) and returns a file with its
confirmation when it comes back **unchanged** (same name, album, size and modified time), without disturbing the other ticks or the
prompt. Only *confirmed* files are remembered (a red cross is not), a new or edited file still joins unchecked and still withdraws the
check, and nothing is remembered or applied outside a finished check. The memory is cleared when a new check starts or the list is
reloaded. This is no more trust than the removal step already places in the check, which does not ask OneDrive again at Yes. The
guide's *file list* sentence now says so. Six new tests in `ArchivePlanReconcileTest` (out and back keeps every tick, an edited file
is checked again, a failed file is not remembered, a new file still forces a re-check, several files independently, nothing applied
without a finished check); suite green. **Not yet seen on the phone:** it was in Ian's hands, on the Restore tab, so nothing was
installed. The hardware check is: Check (ticks appear), swipe one left (its tick goes, the others stay, the prompt stays), swipe it
right (the tick returns, the prompt is unchanged, the count follows).

**Ian then asked: "Can we use the same functionality from the RESTORE tab?"** Read as: make the Archive file cards select and
deselect the way Restore's file cards do (tap a file to toggle it, a selected card highlighted green with a check on the right).
Not built; it collides with what the tick means on the Archive tab today (confirmed in OneDrive, Ian's 26 Aug choice), so the
question went back to Ian.

**Green tick after swipe out and back: verified on the Moto G, 19 Sept 2026 (3:04 pm), after Ian closed the app.** Temp 9, ten files:
Check these files gave ten ticks and *All files validated, frees 20 MB*. Swiping the second file left faded it (*1 MB · Not archiving*),
kept the other nine ticks and the prompt, count 9, *frees 18 MB*. Swiping it right returned its green tick, the prompt stayed as it was,
count 10, *frees 20 MB*. **Yes** then raised Android's dialog **"move 10 photos to trash"**, so the returned file's confirmation is real
and in the removal set, not only drawn; **Deny** was pressed. Ten files still in the folder, none `.trashed`, no pinned rows left.
So the "Not yet seen" line above is closed for this fix. Still open: the phone was on the Restore tab when Ian asked whether Archive can
"use the same functionality" as Restore, and he has not yet said which part he means.

### 19 Sept 2026 (evening) — the tick means "checked against OneDrive" again; Settings loses the wait and the review list

**The tick-memory fix is reversed (Ian's decision).** Ian, after seeing it work: *"the Green Check Mark on the files indicates that the file has
been 'checked' against OneDrive. Once it is Unselected (swipe left, greyed out) the file needs to be re-checked against OneDrive in order for the
green check to appear."* So `e064900` (a swiped-out file keeps its confirmation and gets its tick straight back) is **withdrawn**, by
`git revert` of its code, tests and guide text; the two MILESTONES entries about it above are left in place as history and are superseded by
this one. The behaviour is the one of `a4d7429`: a file swiped back in joins **unchecked**, every tick is reset, the *All files validated*
prompt is withdrawn and **Check these files** returns; the next Check verifies everything again and the ticks reappear. That is the safer
reading of the same rule: a tick is a verification made in the current check, never a memory of one. The earlier suite case *a file put back
joins unchecked and the finished check is dropped* covers it. The guide now says it in words (*A green tick means a file has been checked
against OneDrive in the current check, so a file you bring back has no tick until the files are checked again*).
Verified on the Moto G (3:27 pm, Temp 9): Check gave ten ticks and *All files validated*; swipe one left then right left **no ticks**, no prompt,
*Check these files* showing; Check again gave ten ticks and the prompt. No pinned rows left.

**Settings: *Wait before asking* and *Gone from this phone* removed (Ian).** *"we need to remove the entire 'Wait before Asking' and 'gone from
phone' as this will be handled in the new Pre-app Pop-up window."* Removed: the waiting-period choice (1/7/30/90 days), the review list with its
names and the *Remove these from OneDrive* button, the confirmation dialog and the result lines, from `DeletionSection` (now the title and the
Leave/Ask choice only), `DeletionViewModel` (now just the policy), the fourteen strings and the days plural only they used, and three guide topics
(`settings-deletion-wait`, `settings-deletion-review`, `dialog-deletion-confirm`; **97 to 94 topics, 51 to 48 with a (?)**). The `settings-deletion`
topic no longer points at the removed review. The (?) beside the setting was checked on the phone and reads correctly. The
*edited-photo-is-not-a-deletion* paragraph went with the review topic; the rule itself is not affected.
**Kept for the new window, so it can reuse it:** `SyncDeletionsToCloud` (list, re-scan, delete to the OneDrive recycle bin), `EditedInPlace`,
`CloudDeletionGrace` and the stored `cloudDeletionGraceDays`, and their tests. Nothing calls `candidates()` or `delete()` from a screen now.

**Consequence to keep in view: until the pre-app window exists, choosing *Ask* does nothing at all.** Nothing is offered, nothing is removed from
OneDrive, and nothing asks. The Ask label and its (?) text still describe the intended behaviour. The window itself is not built; the open design
questions (Archive-removed marker column and migration, whether anything starts selected, when it shows, the timing choices, cancelled files) are
in the entry above, and external storage is still to be excluded from backup.

Suite green (94 topics). Verified on the Moto G: the Settings section with **Ask** selected shows only the title and the two choices, and the
Archive tab as above. Uncommitted at the time of writing.

### 19 Sept 2026 (evening) — the window that opens with the app: files deleted from the phone

Ian: *"build the Pre_pop up"*, after removing *Wait before asking* and *Gone from this phone* from Settings. His answers on the open questions:
*"ARCHIVE Marker - ok"* (a ledger column, so the migration was approved), *"Default selections to OFF"*, *"Shows only when new files"*, *"no delays are
need"*, *"cancelled files are left out unless they are deleted again"*, and *"any file deleted since the last app opening; any file that has not been
decided stays until a decision has been made"*. The design (mine, from the earlier thoughts message, on those answers):

**What it is.** A full-screen window, `DeletedFilesGate` in `MainActivity`'s set-up branch (never the wizard), that replaces the app while it is up. It
appears when the app comes to the front and **something has left the phone since it was last shown**, only under **Ask**. Listed: every undecided file
that has left the phone and is still in OneDrive at the right size, old and new. **Nothing is ticked to start with.** Tap a card to tick it (a ticked card is
red and says *Will be removed from OneDrive*). **Remove N from OneDrive** opens a confirmation (count, size, *goes to the OneDrive recycle bin, Gallery Sync never
empties it*); only its **Remove from OneDrive** removes anything. The ticked files' OneDrive copies go to the recycle bin; the **unticked files are marked
`KEPT`**. **Keep all in OneDrive** marks everything `KEPT`. **Decide later** and the back button close it and decide nothing (the files stay undecided and the
window does not come back until something new has left). A result screen says what happened. The header is *Files deleted / from phone (?)* with the number
on the right, like the other tabs.

**Data (approved migration).** `backup_entries.cloudDecision` (`CloudCopyDecision`: `ARCHIVED`, `KEPT`), `MIGRATION_9_10` (`ADD COLUMN ... TEXT`, null for every
row), database version 10, schema `10.json` exported. `cloudDeletionCandidates` no longer takes a time and adds `cloudDecision IS NULL`. `ARCHIVED` is written by
`BackupEngine.markRemovedByArchive` when an Archive removal completes (by key and by MediaStore id, before the ledger refresh); `KEPT` by
`SyncDeletionsToCloud.keep`. **Both are cleared when the file is back on the phone** (`clearLocalMissing` now also nulls `cloudDecision`, and a restore that
replaces the row does too), which is how *"unless they are deleted again"* is met. The waiting period is gone for good: `CloudDeletionGrace`,
`cloudDeletionGraceDays` and its tests are removed; `deletionPromptSeenUpToEpochMillis` (DataStore) records the newest departure the window has been shown for,
set **as it is shown**, so leaving without deciding does not bring it back for the same files.

**Guards kept or added.** The edited-in-place rule (`EditedInPlace`) both when the list is built and again before deleting; nothing offered on a scan that
cannot be trusted; and **`MassAbsence`** (new, pure): more than 20 files *and* more than half of everything uploaded missing at once reads as a bad scan (an index
rebuild, a permission change) and offers nothing. It looks at most once a minute and never disturbs a window that is up.

**Tests (suite green, 460).** `DeletedFilesViewModelTest` (16): under Leave nothing is scanned; nothing shows when nothing has left; nothing ticked to start; not shown
again for files already shown; a newer file brings it back with the older undecided ones listed too; the seen-up-to stamp is set on showing; once a minute; a window
that is up is not disturbed; tick, select all, clear; decide later and keep all; **removal needs a tick and the confirmation**; cancelling keeps the ticks; only ticked
files are removed and the rest kept; a failed removal is not marked kept; finish. `MassAbsenceTest` (5), `SyncDeletionsToCloudTest` (+2: the guard, keep in chunks),
`ArchiveOptOutTest` (+2: Archive marks by key and MediaStore id), `CloudDeletionPolicyTest` (the two policy tests the deleted grace test held). **Mutation check:**
removing the confirmation requirement and the seen-up-to rule fails 2 of the window's tests. Guide: `deleted-files-window` and `dialog-remove-from-onedrive` added, the
`settings-deletion` text points to the window (96 topics, 50 with a (?)); CLAUDE.md records how the consent is taken.

**Verified on the Moto G.** The migration ran on the existing database (version 10, 2,080 rows, no decisions, no crash). The window then opened on the first launch with a real
case, my leftover `zz_arrival_test.jpg` (uploaded, deleted from the phone earlier): nothing ticked, *Remove 0* disabled; ticking it turned the card red; the confirmation
appeared; **Remove** gave *1 file moved to the OneDrive recycle bin* and the ledger row was forgotten (that test file is now in OneDrive's recycle bin). Then, with Temp 9's
uploaded files moved to a hidden folder as stand-in deletions (all restored afterwards): three deleted gave a window of 3; **Decide later** and a relaunch showed nothing;
one more deleted gave a window of **4** (the three undecided and the new one); ticking one and confirming gave *Moved to the OneDrive recycle bin: 1 file. Left in
OneDrive: 3 files.* and the database showed the removed row forgotten and the other three `KEPT`; a relaunch showed nothing; putting the files back cleared the `KEPT` marks
and the missing flag; **deleting two of them again brought them back into a window of 2**. Dark mode readable, and the window survived the theme change. The header title
wrapped to three lines at first and was shortened; two strings that did not agree in number (*These 1 file are*) were rephrased.

**Not verified on hardware.** (1) **Archive marking end to end.** Temp 9, the only album with uploaded, full-size files, now carries Ian's own Archive setup (an *Asked to
wait* and two files swiped out), so I did not archive it. The `WHERE mediaStoreId IN` update is checked by Room at compile time and by the tests, and the `WHERE id IN`
twin ran on the phone, but *archive, then reopen, then nothing offered* was not seen. (2) The mass-absence guard, the failed-removal message, and a very long list. (3)
Whether the OneDrive recycle bin really holds the removed files: read off the app's own result, not looked at in OneDrive. Side effect of the test: `20190620_053058.jpg`
had its OneDrive copy removed and the automatic sync sent it again. **Also seen, unrelated:** most of the ledger is `PENDING` (only Camera, Car Show and Temp 9 read
uploaded) after Ian's wipe and re-run, so the window can only offer files the ledger records as uploaded.

### 19 Sept 2026 - the deleted-files window was meant to cover ALL deleted files, and the app can read the trash

Ian, after deleting ten photos from `Temp 8` and seeing no window: *"this was written to include ALL deleted files NOT just the files backed up by GS."* **The window as built (7e64391) is
narrower than that and was the wrong reading.** It offers only files the ledger records as uploaded. The ten photos were never uploaded (`Temp 8` is `OFF`, its rows `PENDING`),
and `forgetPendingFilesThatAreGone` deletes the row of a never-uploaded file that leaves the phone, so they left no trace at all. The window was silent because it had nothing to offer,
not because it failed; the database, the Ask policy and the log all agreed.

**The design Ian gave, replacing the single window:**
- **Window 1** - *these files have been deleted but are backed up on the Cloud; what do you want to do with the Cloud copies?* **Keep** or **Delete**.
- **Window 2** - *these files have been deleted but no backup can be found; what do you want to do with these files?* **Remain in Trash** or **Back up to Cloud**.
- "On the Cloud" means a copy in OneDrive **whether the app put it there or not**. Ian chose to look in the album's own OneDrive folder, matching by name and size, not to search the drive.
- Every file in either window is in the phone's trash, because that is how it left.

**Measured, on the Moto G, from inside the app (a throwaway probe, deleted afterwards):** a MediaStore query with `QUERY_ARG_MATCH_TRASHED` (`MATCH_ONLY` and `MATCH_INCLUDE` both) returns
all ten trashed photos of `Temp 8`, owned by another package (`com.android.providers.downloads`), and `openInputStream` reads every byte of every one at full size (1.4 MB to 5.1 MB, 20 of 20
reads, 0 failures). **So a trashed file can be uploaded without untrashing it, which is what "Back up to Cloud" needs.** Ian had said it should work because Restore does; Restore in fact
downloads from OneDrive rather than reading the trash (28 Aug), so this was not known until it was measured. **An adb `run-as` probe was inconclusive** and is not evidence either way:
that shell cannot list `DCIM/Temp 8` at all, which the app can. **This does not reopen the 28 Aug decision** that Restore always pulls from the cloud and never from the trash; it only says
Backup may read a trashed file.

**Not built yet.** Keep the rows of never-uploaded files that leave the phone (flag them missing instead of deleting them; the upload queue must skip a flagged row), the OneDrive folder
lookup, the two windows, and the upload from the trash. The ten `Temp 8` photos are already forgotten and cannot be listed retroactively.

### 19 Sept 2026 - the deleted-files window covers ALL deleted files, in two windows

Built to the design Ian gave (see the entry above). **Verified on the Moto G, both themes, in the real flow.**

**What was built.** `unsent_departures` (database **version 11**, migration 10 to 11, additive, schema `11.json`): the ledger still forgets a pending row whose file has gone, but writes it here
first. `BackupEngine.cloudCopiesOf` lists each album's OneDrive folder (the existing `remoteIndexFor`, every page) and matches by name and size. `MediaScanner.trashedIds` says which departed files are
in the phone's trash. `BackupEngine.backUpFromTrash` uploads a trashed file through its URI and records an uploaded, gone-from-the-phone ledger row with the decision set, so Restore can fetch it and the
window does not offer to remove the copy just made. `SyncDeletionsToCloud.offer` builds the two parts, the view model steps through them, the screen has a per-window wording, colour (red only where a removal is
possible) and buttons, and a **checking screen** holds the app until the first look is done (Ian: *before even the Album tab is displayed*), with *Skip for now* when OneDrive has to be asked. Both guide
topics and the Settings copy were rewritten.

**Seen on the phone.**
- Migration 10 to 11 on a live database: 2,069 rows intact.
- Three photos trashed the way another app does it (`content update is_trashed`): the three pending rows were forgotten **and recorded**, all three found in the trash, and OneDrive's own `Temp 8` folder
  already held them, **so a file the ledger called PENDING was correctly offered as "backed up in OneDrive"**. That is the *whether we put it there or not* case working.
- Window 1: tick one, confirm dialog, *Moved to the OneDrive recycle bin: 1 file. Left in OneDrive: 2 files.*
- Window 2: two files that exist nowhere in OneDrive. Ticked one, *Back up*: it listed the 1,888-file Pictures folder first, then sent. Ledger row **UPLOADED, drive size 194,952 = local size, gone from the
  phone, decision KEPT**; the unticked one was forgotten; **both stayed in the phone's trash**; OneDrive's Pictures folder went from 1,888 to 1,889 files.
- Older undecided files come back with the new ones (3 old + 2 new = 5). Reopening with nothing new shows nothing.
- *Skip for now* on the checking screen: the app opened, nothing was shown or marked as seen, and **the next open brought the window back**.
- Dark mode: the checking screen, both windows and a ticked card in each are readable.

**Found on the way, and fixed.** With a large album the lookup took about 30 s and the Albums tab drew first with the window popping over it later, which broke Ian's rule. The checking screen is the fix.

**Tests.** 497 pass. New: `DeletedFilesEngineTest` (17), the rewritten `SyncDeletionsToCloudTest` (22) and `DeletedFilesViewModelTest` (29). **Mutation-checked**, eight safeguards each broken and each caught: departure not
recorded before the row is forgotten; files outside the trash offered; an unlistable album read as empty; any same name counted as a copy; the window stamped as seen when OneDrive could not be asked; the second
window able to delete; a backup that does not stop on a failure that repeats; the just-made copy left undecided.

**Side effects of testing, all on the test account.** One OneDrive copy (`20181225_093534.jpg`, `Temp 8`) is in the OneDrive recycle bin. `20181005_201215.jpg` was decided *keep*. `zz_nocloud_1.png` is now in
`Pictures` in OneDrive and `zz_nocloud_1..3.png` are in the phone's trash. Ten test photos I trashed were put back. Ian's own ten trashed `Temp 8` photos were left alone.

**Not verified.** A long failure mid-backup on hardware (the stop-on-drive-full path is unit-tested only); more than one album with departures; a phone with no trash (API below 30, where window 2 never appears);
TalkBack; the Restore tab offering the backed-up file. **Known cost:** *Back up* lists the album's OneDrive folder again (about 20 s for Pictures) although the lookup just did; a short-lived index cache would avoid it and was not added.

**Settled without asking, worth knowing.** The whole window, backing up included, stays Ask-only. Files deleted outright (not to the trash) that have no OneDrive copy are not offered, since nothing can be done, and their record
is dropped after 45 days.

### 19 Sept 2026 - the deleted-files window remembers a OneDrive folder listing for three minutes

Ian asked for the cache the *Back up* step was missing: after the window's lookup had walked the 1,888-file `Pictures` folder (about 20 s), *Back up* walked it again. This closes the **known cost** named in the entry above.

**What it is.** `BackupEngine.cachedRemoteIndexFor`, used only by `cloudCopiesOf` and `backUpFromTrash`, remembers an album's listing for three minutes. The upload queue, Restore and the cloud check are untouched and still read the
drive as it is now. **Never kept: a listing that came back short** (`remoteIndexFor` now says so through `onPartial`, including when the page cap stops it), because a half-read folder says "no copy" for files it never reached and a cache
would repeat that; **and a failed listing.** **Dropped whenever this app changes the drive:** after an upload into an album, and after a removal from it (`forgetCachedRemoteIndex`).

**Seen on the Moto G.** Two never-uploaded files, ticked and backed up: the Pictures folder was listed only by the lookup, none after *Back up*, and the two files were **sent in about 3 s** where the same step took about 25 s. Both rows
UPLOADED at the drive's size, settled, and left in the trash.

**Tests.** 505 pass (8 new: one walk answers both steps, expiry at three minutes, an upload and a removal each drop the album, forgetting works, a short listing and a failed listing are not kept). **Five mutations, each caught:** short listing cached, no expiry,
upload does not drop the album, removal does not drop the album, nothing cached.

**Still true, and small.** A file put in that folder by something other than this app in the last three minutes is not seen, which can cost a renamed duplicate and nothing worse (uploads rename on conflict and never overwrite).

### 19 Sept 2026 - Archive: a finished check did nothing, because an expired Delay still counted

Ian set `Temp 5` to Archive (20 files across Temp 5 and Temp 9), swiped out five, pressed *Check these files*, and **nothing happened after the check: "the Tab just sat there."**

**Cause.** The log showed the check finishing correctly (*validate: 17 confirmed, 0 could not be archived*, then the two swipe-outs, then 15 to archive). The screen then sat in READY with the question hidden, because `ArchiveUiState.showPrompt` was
`phase == READY && delayedUntil == null` and `delayedUntil` was set from **any** stored time above zero. Temp 9 carried a **Delay** pressed earlier that day (stored as 17:08), which had run out nearly six hours before. **A stored Delay was read as
"delayed" for ever, so once anyone had pressed Delay the Archive question could never be asked again.** Nothing else in the app had this: `ExitWarning` already judged the stored time against the clock. It went unseen because nobody had reached the
prompt again after a Delay expired; the tab in that state said only a small *Asked to wait* line, easy to read past.

**Fix.** `ArchiveUiState.isDelayed(now)` is true only while the stored time is in the future, and `showPrompt(now)` uses it. The screen's *Asked to wait* line uses it too. Not changed: a Delay that runs out while the tab is open is noticed on the next
interaction rather than at the moment it ends.

**Seen on the Moto G, with Ian's own state (Temp 5 + Temp 9, five swiped out).** *Check these files*, then *All files validated... Do you want to continue? Yes / No / Delay* with green ticks; **Yes** raised Android's dialog for **15 photos**, which is
20 minus the five swiped out; **Deny** gave *Nothing was removed* and all 20 files were still there. **Not done: an Allow.** The removal itself was not run, so *archive, then empty the album's mode, then reopen* is still unseen since the tick change.

**Tests.** 510 pass (5 new in `ArchiveDelayTest`). Mutation-checked: put the old reading back and two tests fail.

### 19 Sept 2026 - Archive: no way to ask again after a refused removal

Ian, straight after the fix above: *"how to restart Archive after cancel??"* Denying Android's trash dialog left the tab on *Nothing was removed* with the 15 files still waiting and **no button**. A removal that has reported moves the tab to
its DONE state, which showed the report only, and nothing moved it back: reopening the tab reloads only from IDLE, so the only way out was to kill and reopen the app. The same held after a removal Android only part-allowed.

**Fix.** DONE now shows *Check these files* under the report whenever files are still waiting (`ArchiveUiState.offersCheck`). Starting a check clears the old report, and `validate()` now refuses to start while a check or a removal is already running. *No* on the question already
went back to IDLE and was not affected.

**Seen on the Moto G.** Check, Yes, Deny: *Nothing was removed* with **Check these files** back beneath it; pressed it: the check ran again and *All files validated... Yes / No / Delay* returned. All 20 files still on the phone.
**Tests.** 515 pass (5 new in `ArchiveCheckButtonTest`); the old behaviour put back fails one. **Still not done on hardware:** pressing **Allow**.

### 19 Sept 2026 - Archive removal seen end to end on the Moto G, after the tick change

Ian pressed **Yes** and **Allow** on Temp 5 (10 files) and Temp 9 (10 files) with five files swiped out. **This closes the open item: the Archive marking, the removal and the deleted-files window's silence were unseen since the window was built.**

**What happened.** Android's dialog offered **15 photos**. After Allow: *archive: 15 files removed from this phone*; on disk Temp 5 holds 2 live and 8 trashed, Temp 9 holds 3 live and 7 trashed (renamed in place, as recorded for the Fold 4 and the Moto G). The 5 swiped-out files stayed.
**Ledger.** All 15 rows are UPLOADED, flagged gone from the phone, and marked `ARCHIVED` (8 + 7); the 5 kept files carry their pin; `unsent_departures` is empty. **Both albums keep the Archive mode**, because each still holds files (the emptying rule, as designed).
**The deleted-files window did not open on the next launch**, so files Archive removed are not offered for deletion from OneDrive. **Restore** offers them: *Temp 5, 8 files, 49 MB, 8 to download, 2 already on this phone*. The Albums tab reads *2 kept at full size / 10 verified in OneDrive* for Temp 5 and *3 kept* for Temp 9.

**Seen and left alone.** After a run that takes every waiting file the tab shows the count at zero and **no message that 15 files were archived**: the report is drawn only while files are waiting (the header's action slot is gated on that, and Ian's 27 Aug note says the zero says it). Whether a *15 files moved to the trash* line should show is his call.
Also still true: the Albums tab counts the swiped-out files as *kept at full size*, which is the pin's older meaning.
**Side effect.** 15 test photos are in the phone's trash, 30 days, and fetchable from Restore.

### 20 Sept 2026 - optimising runs from its Settings: Automatic on arrival, Manual on Sync now; the Settings status lines are gone

Ian: *"All optimizations should run based on their Settings in Settings. Automatically - as soon as a file hits an Album who's mode is SYNC or an Album mode is switched to SYNC. Manually - through the "Sync Now" Button on the Albums Tab. So let get rid
of the "Every single photo is already optimized" and the "No video is ready......" lines in Settings, along with the associated (?) in the "How To Guide"."*

**What it was.** Video Automatic already started at the end of every complete backup run (and waited for the charger). **Photo Automatic had no background trigger at all**: it was a `LaunchedEffect` that fired when the Albums or Settings tab was on screen. Manual for both was a button in Settings,
with a status line above it (the two lines Ian named are `proxy_none_all_done` and `video_none`, the "nothing to do" states of those blocks).

**What it is now.**
- **Photos, Automatic:** a new `OptimiseWorker` phase, `sync-photos` (Area 2: Sync albums only, honouring the Settings switches and the "Keep at full size" pin; not the wizard's `photos` phase). Started by `PhotoOptimiseLauncher.requestAutomatic()` at the end of every complete backup run, when an album is switched to Sync, and when a photo
  switch or mode changes. Works only through folders granted at setup (no dialog), so it can run unattended; `ProxyApplier.splitByConsent` sorts each candidate. It stops when photos are switched off and **stops rather than loops** if a photo will not replace.
- **Manual, both kinds:** the backup run started by **Sync now** ends by starting whatever is set to Manual (`requestOnSyncNow`, video without waiting for the charger). **Sync now is enabled when Manual optimising is waiting**, even with nothing to send (`OptimiseOnSyncNow`, `BackupUiState.canSyncNow`), because it was only ever enabled for files to upload.
- **Photos outside the granted folders** need Android's dialog. `buildProxyWriteRequest` now asks only about those. Automatic offers it when the app is open; Manual offers it when Sync now finishes. **Not seen on the phone:** every folder on the Moto G is inside a grant.
- **Settings:** the status-and-button blocks for photos and video are gone, with the auto-offer effect, the results text, the button's entry point and the strings, state fields and the `ProxyStatus`/`VideoOptimiseRun` types that only they used. What is left under the switches is the "needs Android 11" note on an old phone. **Guide:** topics `settings-optimise-status` and `settings-optimise-video-status` are deleted
  (94 topics, 48 with a (?)); Mode, Optimise photos, Optimise video, Sync now and "When things happen" were rewritten to match; DEFAULTS.md and CLAUDE.md's Area 2 line updated, and DEFAULTS' two *Waiting period* rows removed (that Settings option went earlier this session).

**Seen on the Moto G** (photo and video both on Manual to start, as Ian had them; photo Mode flipped to Automatic for the test and put back to Manual):
1. **Manual:** a 7.2 MB photo dropped into `Temp 0` (a Sync album) uploaded and **stayed full size**; **Sync now went from greyed to enabled with nothing to send**; pressed: *photo optimising queued, proxying 1 of 1, reclaimed 6,576,488 bytes*, the file 7,164,060 to 587,572 bytes; Sync now greyed again.
2. **Automatic, hands off:** a second photo added at 00:23:15; the backup ran at 00:23:45, *photo optimising queued*, optimised by 00:23:52 (7,301,107 to 485,685 bytes).
3. **Album switched to Sync:** a new album `zz_album` set to Sync at 00:24:52; uploaded, optimised by 00:24:58 (7,302,296 to 403,971 bytes). After Rescan the album reads *1 optimised, 1 verified in OneDrive*. **It briefly read "0 of 1 verified" from the cloud check that predates the upload**, until Rescan.
4. Settings: the Sync section holds the two switches, their Mode, Older than and Quality, and nothing under them; the Mode (?) shows the new text.
No crash; no dialog appeared in any run.

**Tests.** 530 pass (15 new: `PhotoOptimisePolicyTest`, `OptimiseOnSyncNowTest`, three more in `VideoOptimisePolicyTest`). **Six mutations each caught:** Manual photos run alone, Manual photos never run on Sync now, a chain continues after photos are switched off, Automatic video pushed past the charger by Sync now, Sync now ignoring waiting Manual optimising, an Automatic kind counted as waiting for the button.
**Not covered by a test:** the worker phase and the launchers, which need WorkManager and are seen only on the phone; the album-switch hook (the run it queues did the work here; the direct call finds nothing for an album that still has files to upload).

**Side effects of testing, all on the test rig.** New files `zz_opt_1.jpg`, `zz_opt_2.jpg` in `Temp 0` and `zz_album/zz_opt_3.jpg`, each now optimised and in OneDrive; `zz_album` is set back to Off. Photo Mode is back to Manual. Nothing of Ian's was rewritten: the only candidates were the three new photos.

**Decisions made without asking, worth knowing.** (1) The Settings **buttons** went too, not only the two named lines: with Manual meaning Sync now they would have been a second, contradicting route. (2) Photos do **not** wait for the charger (video does); a switch on a large existing album will therefore optimise a batch of 60 at a time in the background whenever the battery is not low.
(3) **Sync now optimises whatever is set to Manual, not only what it uploaded**, since Manual means the button and not "the files this run sent".

### 20 Sept 2026 (night) - three data-integrity defects found and two fixed; a long test campaign on the Moto G

Ian asked, before bed, whether anything stops an optimised file being backed up over its full version, then told me to test backups, syncs, restores and archives under different settings while he slept, using his disposable pictures.

**1. FIXED - a small upload REPLACED a cloud file of the same name.** `GraphUploadService.uploadSmallFile` (`PUT ...:/content`, the route for files under 4 MiB) sent no conflict setting, and Graph's default for that route is to **replace**. Only `createUploadSession` carried `rename`. Measured on the phone: a 133,017-byte file uploaded, then
a different 187,856-byte file under the same name: the folder count did not change, the name read 187,856 bytes, the first was overwritten. **Every optimised photo is under 4 MiB**, so the route an optimised copy would take past the other guards was the one that overwrote the full-size original; an edited photo did the same. The `rename` is now in the URL (`?@microsoft.graph.conflictBehavior=rename`) so no caller can leave
it out. Re-measured: the second upload came back `zz_replace2 1.png` and the original survived; both the over- and under-4 MiB edits produced `name 1.jpg`. The existing test "uploads never request replace on conflict" only ever covered the resumable route (its file is over 5 MiB), which is how this survived; two tests now cover the simple route and the 4 MiB boundary, and putting the old URL back fails them.
**This corrects two claims written earlier:** the 19 Sept cache entry said uploads "rename on conflict and never overwrite", and the uploader's own class comment said it never overwrites. Both were false for small files until now. OneDrive keeps prior versions of a replaced file, so an overwrite made before this fix may be recoverable from OneDrive's version history; nothing in this app can do it.
**The other layers that keep an optimised file out of the upload path were already sound** and are what protected real data: an optimised row stays UPLOADED so it is never pending; the scan skips a proxied file by MediaStore id; the upload loop skips a same-name-same-size file and recognises a proxy (marker plus a larger same-named cloud file) after a lost ledger.

**2. FIXED - Pause then Resume uploaded every pending file twice.** Resume started the automatic chain and the manual chain within 15 ms; both read the same pending rows and uploaded them side by side, so 24 photos became 30 files in OneDrive, six of them ` 1.jpg` copies. An old race, not from today's work. `BackupEngine.uploadPending` now holds a process-wide `Mutex`: a second run waits and then reads the ledger as the first left it.
Re-run with the fix: 24 files, no duplicates. `UploadRunExclusionTest` pins it, and removing the lock fails it.

**3. KNOWN, not fixed - a hard kill mid-upload can leave one duplicate.** The file in flight when the process dies is finished by OneDrive after the app has gone, and the next run's listing may not show it yet, so it is sent again as ` 1.jpg` (1 in 25 in the run; the earlier 40-file run that included a kill had the same cause plus the Resume race). Loses nothing. **Proposed fix:** send with `conflictBehavior=fail`; on a 409 list the folder once, and if a same-size copy is there treat the file as already uploaded, otherwise retry with `rename`.

**Also found and fixed:** after a background run uploaded a file, **Sync now stayed grey with Manual photos waiting**, and stayed enabled after they were optimised, because nothing told the open screen the ledger had changed. `BackupViewModel.observeWhatOptimisingWaits` now re-reads when the uploaded count or the optimise chain changes.

**Campaign results** (test albums `zzT1..zzT13` and `zz_*`; files drawn from Ian's `camera roll` and `temp` folders; 13 scenarios; every PASS below was checked against the ledger, the files on disk and OneDrive's own listing):
- **Backup and Off albums are never optimised**, even with photos and video on Automatic; a Backup album with two files over 4 MiB uploaded through the resumable route at exact sizes.
- **Sync + photos Automatic:** 8 uploaded and optimised, 26.7 MB down to 5.1 MB; OneDrive lists 8 files at the original sizes; later runs upload nothing.
- **Sync + photos Manual:** uploaded untouched; **Sync now enabled with nothing to send**; pressed, all optimised; grey again afterwards (re-verified in one process after the refresh fix).
- **Optimise photos switch:** off, nothing optimised; on, optimising starts by itself. **Mixed formats:** HEIC optimised; small PNG, WebP and JPEG deliberately skipped and never retried; no file left half-processed.
- **Video Automatic** (AC power): 2 clips optimised (4.4 MB to 0.8 MB, 1440x1080 to 640x480), a third judged "no smaller" and marked; lengths preserved. **Video Manual:** untouched on arrival, transcoded by Sync now, length within 55 ms.
- **Album switched Backup to Sync with files already in OneDrive:** the direct hook fired (log shows optimising queued before the run finished), 6 s, 26.3 MB reclaimed. **Sync to Backup:** new files uploaded, not touched.
- **Restore an optimised album:** every file back to its exact original size, pinned Keep at full size, OneDrive unchanged, Automatic optimising did not shrink them again.
- **Archive with opt-outs:** swipe-out remembered; Android asked about exactly 4 of 6; 4 trashed, 2 kept; rows ARCHIVED and flagged gone; OneDrive holds all 6; the deleted-files window stayed silent; **Restore returned the 4 at full size and pinned.**
- **Edited photo** in a Backup album: both versions kept, renamed, on both upload routes. **An edit saved over an already-optimised photo is not uploaded at all** (same MediaStore id, so it is taken to be the proxy). Nothing is overwritten, but the edit has no cloud copy until the file is restored to full size; pre-existing design, worth Ian's decision.
- **Pause / Resume / force-kill,** 40 files: all uploaded at the right sizes; duplicates as above.

**Test-rig notes.** My first attempts at driving the UI mis-tapped neighbouring albums, which is why `zzT1_backup` briefly ended up on Sync; **none of Ian's albums, pins or settings changed** (checked against a baseline taken before testing) and his optimise switches are back to photos Manual, video Manual. A file that exists elsewhere on the phone by name and size is never marked "gone" after Archive, by design; the test set had to avoid Ian's `Pictures` album to see the flag. Left on the phone and in OneDrive (`Samsung Gallery/DCIM/zz*`): the test albums, in modes SYNC, BACKUP and ARCHIVE. Nothing was deleted.


### 20 Sept 2026 - the Camera album: no Sync, and a manual "optimise what is old" control in its file list

Ian's design, after the Automatic-optimise change: *"I don't want a user to take a picture/video and then BAM it's optimized already."* Answers taken as given: Archive stays available for Camera; the control respects the Optimise photos / Optimise video switches; age is read from the file's modified time; and *"this isn't a Backup - this is basically a Manual Optimization for a single folder"*.

**What was built.**
- **`CameraAlbum`** (by name, ignoring case): the Camera menu offers Off, Backup and Archive. Three doors shut: the menu (`AlbumModeDropdown` takes the list), `setAlbumMode` (refuses Sync), and seeding (`BackupEngine.refreshLedger` and Select all write `CameraAlbum.seeded`, so a default of Sync gives a new Camera album Backup). **An album already at Sync is left alone**, which is why Ian's Moto G Camera still read Sync until I moved it by hand for the test.
- **The control**, in Camera's file list only: *Only list Photos/Videos older than* 1 day / 1 week / 1 month / 6 months / 1 year (`CameraOptimiseAge`, its own scale, not `MediaAge`). Picking one fills the list with the files it would touch and the card says how many and about how much space (`CameraOptimisePlan.estimatedSavedBytes`: 80% of a photo, the chosen video quality's figure for a clip, marked "(estimate)"). Nothing happens until **Optimise N files** is pressed. The choice is screen state and is not stored: it is not a rule.
- **Swipe a file out** (left = keep at full size, greys it and takes it out of the count; right = back in). It is `FilePin`, the same pin Restore and the Archive tab use, so there is no new column and no migration. **The invariant: a pinned file is never in `CameraOptimisePlan.eligible`**, the only list the worker acts on. A file at full size in a Camera list that is also an Archive opt-out is why **the control is not shown while Camera is set to Archive**: the swipe would silently opt the file out of Archive too.
- **The work** is a new phase of the existing `OptimiseWorker` (`PHASE_CAMERA`, inputs: album, cutoff, excluded ids). It recomputes the plan from the ledger with the cutoff fixed at the tap, so the set done is the set shown; a file can leave the plan meanwhile (swiped out, finished, gone) and never join it. Photos one at a time through `ProxyApplier.apply`; clips three a batch through the new `VideoOptimiser.optimiseEntries`. A file that fails is stepped over, its id carried in the continuation; a batch that makes no progress ends the chain. Files outside a granted folder go through Android's own dialog first (`prepareCameraOptimise` -> `NeedsConsent`), then the worker runs; **that route was not exercised on hardware, because every Camera file on the Moto G sits inside a granted folder** and the scanner only follows granted folders, so there is no way to put one outside from the UI.
- Screens: the drill-down re-reads the ledger every two seconds while a Camera run is live and once when it ends, and the album cards refresh when it finishes (found stale on the first hardware pass and fixed).
- Guide: new topic `album-camera-optimise` with a (?) beside the control's heading; the modes topic says Camera has no Sync. 95 topics, 49 with a (?), 0 out of date.

**Tests.** 20 new (`CameraAlbumTest`, `CameraOptimisePlanTest`); 553 in all, 0 failed. Mutation-checked: seeding ignoring the rule, the menu offering Sync, pinned files becoming eligible, the OneDrive size check removed, the master switch ignored, and the age comparison inverted each fail a test.

**Hardware (Moto G, a fixture of 12 files aged 0-500 days in DCIM/Camera, then a second of 12).**
- Camera's menu shows Off, Backup, Archive. Choosing 1 week listed exactly the 8 files 7+ days old (7 photos and 1 clip) and "about 40 MB saved"; swiping one out gave 7 and "about 37 MB" and a greyed row; the button optimised the 6 photos and the clip, left the swiped one and the four newer ones at their original bytes, and OneDrive kept every original size.
- The switches: video off listed 5 photos and said so; photos off listed the clip; both off said "Optimising is switched off in Settings" with no button. All 6 swiped out said "Everything this old is kept at full size"; swiping right returned the file to the list.
- Second run: the card read "Optimising... 6 left", then 1 left, then "Nothing this old is ready to optimise", the list emptying as it went; the album counts went 42 to 48 optimised.
- After both runs OneDrive lists 59 files for Camera, equal to the ledger: optimising re-uploaded nothing. Dark mode reads correctly (a theme change drops the open drill-down, which every album's drill-down already did). Crash log clean after every install.
- Not driven by hand: the harness's swipes sometimes missed a row when scrolling a long list; the swipes were then repeated by hand and the pin state checked in the ledger each time.

**Left on the Moto G:** Camera is now **Backup** (it cannot be set back to Sync from the app, by design), 24 aged files `IMG_CT_*`/`VID_CT_*` and `IMG_CU_*`/`VID_CU_*` in DCIM/Camera and OneDrive (13 of them optimised, `IMG_CT_b1.jpg` kept at full size, the rest too new for the ages chosen), and both Settings switches on with Manual as before.

**Open.** Whether the install wizard's "optimise everything" choices (2 and 3) should also skip Camera: Ian said he would consider it, and nothing here touches the wizard. An age is read from the modified time, so a photo edited yesterday is "1 day" old whatever it was taken.

**Same day, after Ian used it - changes to the Camera control.** Heading now reads *Only list Photos/Videos older than*. **Cancel** sits beside the age box (shown once an age is chosen) instead of a "Don't optimise" menu item; Choose and Cancel are wider. **All** is the last age (zero wait, so it lists a file modified a moment ago; it is the user's choice, made with the list in front of them and a swipe to leave anything out, and the guide says so). **The Camera list is empty until an age is chosen**, with a line saying so (Ian offered "list nothing" or "default to All"; I took the first because a default of All would put a one-tap "optimise everything" bar on screen the moment the album opens). The Sort row is hidden until then. The **Optimise N files** button moved out of the header to a full-width bar at the foot of the screen, the same as Restore's, shown when something is listed or a run is going, disabled and reading "Optimising... N left" during a run. **The (?) beside the bottom button is gone on both this bar and the Restore tab's**; the guide topic for Restore's bar therefore became guide-only (`ui=False`), because `HowToGuideConsistencyTest` requires every pop-up topic to be offered by a screen. Ian also reported swiping "non-functional"; it was the plain list, which does not swipe (before this change it still showed files). Checked on the Moto G in light and dark themes; Cancel returns to the empty state and the bar button optimised the two listed files.

**Same day - one selecting gesture everywhere (Ian): swipe right to select, swipe left to deselect.** The Restore folder list already did it; the Restore **file** list (inside a folder) only took taps, and Ian reported it as not swiping. It now uses `SwipeChoiceBox`, as do the deleted-files window's cards (a tick still removes nothing by itself: the confirmation dialog stands between a swipe and OneDrive) and, already, Archive and Camera. Taps still work wherever they did. The hint text is one wording in every list (`select_swipe_hint`, "Swipe right to select / left to deselect"), Archive and Camera add "A deselected file stays ...", and screen-reader actions read Select / Deselect. Checked on the Moto G: Restore file list (right selects and brings the Restore bar up, left deselects and removes it, nothing restored) and the deleted-files window (3 test files deleted from the phone; right ticked one and showed "Remove 1 from OneDrive", left unticked it; finished with Keep all, nothing removed). **Left as they were:** the install wizard's folder checkboxes (a separate surface). Also made `SwipeChoiceBox` read its callbacks through `rememberUpdatedState`, so a list that reorders can never leave a swipe acting on the file that used to be in that slot; I could not make that fail on the phone, so it is hardening rather than a fix.

**Decision, 20 Sept 2026 (Ian): the wizard is left as it is, Camera included.** The open question above (should choices #2 and #3 skip the Camera album, or apply an age floor, or should the wizard's optimise options be removed altogether) is closed with no change. Reasoning as Ian gave it: an optimise made by mistake is recoverable, because **Restore brings the full-size original back from OneDrive** and pins it so it is not shrunk again. Two facts worth keeping from the discussion, since the first was misstated at one point: **#3 optimises only what the run itself uploads** (what was on the phone and not yet in OneDrive), so a reinstalled phone with an existing cloud backup gets very little from it, and what it does touch skews to the newest photos; and **#2 optimises the whole library**. Not built, deliberately: a Camera skip (it would nearly empty #3), an age floor, and removing the wizard's optimise options.

### 20 Sept 2026 (late) - the hard-kill duplicate: fixed, and the cause was not what this file said

**Correction first.** The night entry above called finding 3 "a hard kill mid-upload can leave one duplicate" and explained it as: OneDrive finishes the in-flight upload after the app has died, the next folder listing does not show it yet, and the file is sent again and filed as " 1". **That explanation was a guess and it was wrong.** A repeated-kill test with the first version of the fix (fail on a name clash, look at the item, treat the same size as arrived) still left a duplicate, and its log showed the real mechanism: **`createUploadSession` makes a zero-byte placeholder under the file's name at once.** Kill the app in the moment after the request and before the reply is read and saved, and the session is orphaned (the app never learned its URL, so it cannot resume it) while the empty item keeps the name. The retry is told the name is taken, by 0 bytes; the old code filed the photo as " 1", leaving an **empty file holding the real name and the real photo under the wrong one**, which is worse than a plain duplicate. Only the session route (files of 4 MiB and over) does this; a small file is one request and creates nothing until it finishes.

**What was built** (`ChunkedUploader`, `GraphUploadService`, `UploadDtos`):
1. Every upload first asks OneDrive to **fail** if the name exists (was `rename`, which is what made the " 1" copies).
2. On a 409 it reads the item at that exact path (a read of one item by path, not a folder listing, which can lag) and decides: **same size** means the file is already there, so it is recorded as uploaded and nothing is sent; **a different size, or nothing there**, means it is uploaded again with `rename`, exactly as before; **OneDrive cannot answer** (5xx, throttled) means the file stays pending and is tried next run, because a guess is what makes a duplicate.
3. **A zero-byte item under a big file's name is filled in place**, with `conflictBehavior=replace` and the item's eTag sent as `If-Match`, and only when the eTag was read. **This is the one place the app now sends `replace`**, and it is a deliberate exception to "never replace": replacing an empty item loses nothing, the app never uploads an empty file itself (`UploadOutcome.EmptySource`), and nothing with content in it is ever replaced (that is `rename`). Reverting it means an orphaned placeholder goes back to producing " 1" copies.

**Tests.** 14 new in `ChunkedUploaderTest` (563 to 567 in all): first attempt asks fail on both routes; same size not resent (small, large, and a clash only seen when the last chunk lands); different size, and nothing there, rename; an unanswered lookup uploads nothing; a non-clash failure is not looked into; the placeholder fill, its guard, no fill without an eTag, no fill for a small file, no fill of anything with content. Mutation-checked: 10 mutations, all caught.

**Hardware, Moto G.** The first run (30 files, 8 kills at random) reproduced it: 31 files in the folder, the extra being ` 1.jpg` beside an empty original. After the placeholder fix, 12 files over 4 MiB with the app killed **six times, each right after a session request**: 12 files in OneDrive, all under their own names, none empty, every size correct; the log shows the placeholder path taken twice and the resume path four times. The leftover `zzT14_kill` folder still holds that one bad pair (an empty file and its " 1" sibling); nothing deletes it.

### 20 Sept 2026 (later) - "Get full size" from the Share menu: probed, then shelved by Ian

Ian's original idea was that opening a file to edit it should fetch the full-size original. MILESTONES' platform constraints already say Android has no hook for that (nothing can intercept another app opening a media file; Samsung could only because it owns the viewer), so the closest route is a **Share / Open-with target**: the user shares the photo to Gallery Sync from inside the gallery and the app swaps the original back in. A throwaway debug-only probe on the Moto G showed **Google Photos does list it and hands over a URI that wraps the MediaStore URI (id parseable), with name, size and path, and the file reads as the smaller copy**, so matching to the ledger is reliable. It does not help editors that open photos from their own picker, and only Google Photos was tested. **Shelved for a future release; nothing built, the probe is removed.** Everything found, the limits, the related undecided edit-over-proxy fix and the DocumentsProvider alternative are in `.claude/tasks/TASK-024.md`.

### 20 Sept 2026 (evening) - the milestone checklist audited against the code

Ian asked for the checklist to be checked against what is actually built. Every unticked box was traced to
the code and to the log; the ticked ones were spot-checked (metered default `false`, Automatic sync on by
default, the content trigger and 6-hourly periodic worker, the wizard's steps, the Settings switches).
**Nothing was run on a device for this; it is a read of the code and of this file.**

**Ticked (built, and already verified on hardware in earlier entries):**
- v0.3 *Album modes in the UI*, *Sync scope toggles*, *Guided first run* (language excepted, below), and
  *Move to backup* (replaced by Archive).
- v0.4 *Restore replaces the proxy* (tagged v0.3.1, 27 Aug).
- v0.4 *Deletion sync*: the "no real deletion performed" sentence was stale and is corrected.

**Still open:**
- v0.2 **Retry failed items from the UI.** Not built, and a real gap: a file that fails five times is never
  selected again, `resetFailures()` has no caller, and there is no control (see the item).
- v0.3 **Running count of space saved, per album and in total.** Half built: the total exists under the
  Sync filter, the per-album figure is computed and not drawn, the "could free" forecast is not built.
- v0.5 **Billing, Google Photos, sync frequency.** Nothing built beyond account name, Sign out and the
  Delete Account Info page.

**Not on the checklist, found on the way:**
- **The language step is a placeholder.** The guided first run in TASK-014 lists language; Settings says
  "Multi-Language Support Coming Soon". Ian shelved it on 4 Sept, so it is recorded here and not counted as
  a defect.
- **`v0.2.0` was never tagged**, though the heading says so. Tags are `v0.1.0` and `v0.3.1` only; 168
  commits sit after `v0.3.1`, and `versionName` is still `0.3.0` (lower than the tag).
- **Dead code:** `BackupViewModel.buildMoveToBackupRequest` and `onMoveToBackupFinished` are called by nothing.
- **Stale comment:** `MediaSource.kt` says the Google Photos purchase check is in `BillingRepository` "in v0.3.0";
  it is v0.5 and the class does not exist.

**Release gate, on this reading:** v0.3 has one item open (per-album space saved) and v0.4 none; the checklist
no longer says otherwise. What still stands between the two milestones and a Play submission is not on the
checklist: the testing affordances to strip, the open items in TASK-023, and whatever Ian decides about
optimised photos that are edited (TASK-024).

### 20 Sept 2026 (night) - Settings re-laid out: Language last, a light line between settings, Albums reordered, folders ticked then removed

Ian sketched the changes as text mockups over several messages; this is what was built from the last version of each.

- **Language is the last line on the Settings tab**, below the policy pages (was in General, under the How To Guide card). The
  tour's Settings picture moves it too.
- **A light line between each sub-setting** (`SettingDivider`, the theme's own divider colour): in General, Backup, Albums and Sync.
  Indented options stay under their own switch. Read in both themes on the Moto G.
- **Albums reads in Ian's order:** *Default mode for new albums*, then *Folders to back up*, then *When you delete a photo/video*.
- **Folders to back up:** a box beside each folder, then **Add a folder** and **Remove** side by side. Remove is greyed until
  something is ticked and removes every ticked folder (`removeSource`, unchanged). The old per-row Remove button is gone. Seen
  ticking enable Remove on the phone; an actual removal was **not** exercised (it would drop the test phone's folder grant).
- **Backup shows OneDrive with its box at the left of the name**, ticked and greyed out, *At least one backup location must stay
  on.*, an **Account** label over the address (its (?) and Sign out kept) and the folder location. The rule is `BackupLocations.canSwitchOff`
  (5 tests; 572 in all, 0 failed): the last location on can never be switched off, so the box unlocks by itself the day a second
  location exists. **Google Photos and USB Drive were drawn for a moment as placeholders and removed at Ian's word**; the enum still
  names them, nothing shows them.
- Guide (`c6_settings.py`) and the tour's Settings mock follow; regenerated, `--check` clean.
- **Not done, and not asked for after Ian rewrote his message:** a "clear demarcation after the Archive heading", and "a user default
  for the Settings section". The first version of his message had both; the resent one did not.
- **During the screenshot run the harness's swipes appeared to tick a folder box and flip Optimise photos on the test phone.** A targeted
  probe (swipes starting on the folder row, three times) did not reproduce it, so it is unexplained rather than ruled out.

### 21 Sept 2026 - overnight on the Moto G: a 1,891-file Sync album, optimised unattended, and the Archive prompt after its Delay

Ian set the `Pictures` album (1,891 files, 6.3 GB) to **Sync** and `zzT10_pause` to Archive (having pressed *Delay, 1 hour* on the Archive question) and asked for both to be watched overnight. A read-only monitor (a PC script; it taps nothing and changes nothing) took a ledger snapshot every 3 minutes from 00:09 to 10:18, with the phone on the charger.

- **Backup half:** 1,613 pending rows were resolved in about 35 minutes by the skip-existing path (batches of 25, ~50 a minute): every file was already in OneDrive from the earlier wizard run, so **nothing was re-sent and no duplicate appeared**.
- **Optimise half:** ran on its own straight after, **32 batches of 60 photos, about a minute each, finished at 01:13**: 1,798 optimised, 93 left as not worth shrinking, 3 missing. **5,213 MB reclaimed**; the phone's free space rose from 95.0 to 100.1 GB, agreeing with it.
- **Nothing else:** no failed row, no crash-buffer entry, no stall, no error in the app's log, no change to any other album's mode or counts. Battery 100 % on the charger throughout.
- **Archive:** nothing was removed and no file changed on disk (`zzT10_pause` still 24 present). The 10:13 log reads `validate: 25 confirmed, 0 could not be archived` and the screen shows the question again, *Yes / No / Delay*, over all 25 files. That is the Delay working as designed: it silences the question for the chosen time, and when the time has run out the question comes back; it is not a timer that archives anything. Removal still waits for Yes and then Android's own dialog.
- **A correction to something said in chat the night before:** it was said that an Archive "minimum age" had never been built. The Delay (1 hour / 12 hours / 1 day) is built, in `ArchiveScreen`. What is not built is a rule that only archives files older than some age.
- Not exercised overnight, because it needs a tap: the Yes path. **That path is not untested.** Archive with Yes and Android's trash dialog has been run on the Moto G many times (eight videos on 28 Aug, found in the Files app's Trash; `zzT8_arch` still holds four .trashed files from it). What has never been seen is Yes on **API 37 / One UI 9**, and Samsung Gallery's Recycle Bin there; that is the Fold 8 plan's Phase 5.

**Same day - the Archive question's Delay is removed (Ian: "it is confusing").** The prompt is now **Yes / No**. Gone: the Delay button and its 1 hour / 12 hours / 1 day choices, the "Asked to wait" line, `ArchiveDelay`, `ArchiveUiState.delayedUntil`/`isDelayed()`, `BackupPreferences.archiveDelayedUntilEpochMillis` and its setter and key, and the delay argument to `ExitWarning.shouldWarn` (it now warns whenever files are waiting; back gesture only, as before). No file is ever removed or asked about differently: No still leaves everything on the phone and the check can be run again; Yes still goes to Android's own dialog. A Delay value already stored on a phone is simply never read. Guide (`c5_archive.py`) and strings follow; suite 565 (was 572: ExitWarning 6 to 2, the delay tests 5 to 2), 0 failed. Seen on the Moto G: Archive, *Check these files*, the question shows Yes and No only; crash buffer empty.

**Verified on the Moto G after the Delay came out (Yes tapped at about 11:07 on the new build).** `zzT10_pause`: all 24 files renamed in place to `.trashed-1792598838-…` (bytes kept, about 103 MB on disk, expiry 30 days out), 0 left in the folder; its ledger rows are kept (bookkeeping) and **its album mode was retired**, so it left the Albums tab and the Archive count went from 4 to 3, as the 18 Sept rule says. `Temp 5` and `Temp 9`, whose remaining files were opted out (*Not archiving*), were not touched. The Restore tab lists `zzT10_pause` as **30 files in OneDrive, 30 to download**, so the OneDrive copies are intact. Crash buffer empty. Not captured: the app's own log of the removal (it was cleared before the check was made), so the removal is evidenced by the disk, the ledger and OneDrive rather than by a log line.

### 21 Sept 2026 - the app icon: a GS loop in the app's greens (was still the default Android robot)

Ian took a G-and-S-in-a-sync-loop design generated by ChatGPT, asked for it in the app's colours, then for it darker and less neon, then for the shading inside the letters back. **Chosen: the "Punchy" GS loop.** It is raster artwork, recoloured with a hue map (bright pixels cyan to green, purple to sky blue; the navy tile to deep emerald) and then re-lit so the original's light and dark shading survives: a plain hue change had flattened the inside of the letters, because green is brighter than blue at the same HSV value. The source is in `design/icon/gallerysync-icon-source.png`.

- **Adaptive icon** (minSdk 26 makes this the only one that matters): background is a gradient in the tile's own colours (`drawable-nodpi/ic_launcher_background.png`); foreground is the **GS only, no lettering**, feathered into the background and sized to sit inside the 66 dp safe zone (`ic_launcher_foreground.png`); **`ic_launcher_monochrome.png` is a real silhouette** for Android 13+ themed icons (the default template pointed the monochrome layer at the coloured foreground, which would have drawn a blob). The two default vector drawables are gone. The lettering is left off because Android prints the app name under the icon, and at 48 px it is a smudge.
- Legacy `mipmap-*` webp (full tile, and a round crop) replaced; `design/icon/play-store-512.png` is the Play Store image (full bleed, with the "GallerySync" lettering).
- Seen on the Moto G after installing: the icon on the home screen and in the app drawer, crash buffer empty. Not seen: the Samsung launcher, a themed (monochrome) icon, a circular-mask launcher.
- **Not decided:** the app's launcher name is still `Gallery Sync` (two words, `app_name`), while Ian wants the name written `GallerySync`. Left as it is until he says.

**Same day - the product is written `GallerySync`, one word (Ian), everywhere a user reads it.** That settles the "not decided" line above. 90 occurrences changed: `app_name` (so the launcher label now reads GallerySync, seen on the Moto G) and 12 other strings, the guide sources `c2` to `c8` and the page titles in `build_guide.py`, the two hand-written web pages (`docs/privacy-policy.html`, `docs/delete-account.html`), and the regenerated guide (`--check` clean, suite 565, 0 failed). **Deliberately not changed:** history in this file and the task notes (they record what was said at the time), the design mock-ups under `design/`, `rootProject.name` in `settings.gradle.kts` and the IDE's own project name (build identity, not user-facing), and `c1_start.py`, which Ian is editing by hand; its four remaining "Gallery Sync" are his to change, and until he does the published guide still says it in those places. Samsung's own "gallery sync" feature is a different thing and was not touched.

### 21 Sept 2026 - the new welcome picture (Ian's design), and a mistake made while checking it

**The picture.** Ian supplied a new welcome image: the GS icon on a flat dark green with "Welcome to" above it and the Albums screen in a phone below, **cut off at the bottom so the phone runs off the edge rather than floating** (his intent). It replaces `welcome_screen.png` (2.6 MB) with `welcome_screen.webp` (100 KB). The card used to draw the picture at full width, centred, on the theme's background; with a picture this shape that leaves the phone ending in mid-air and a cream or near-black band under it. It now sits on the picture's own green (`SignalWelcomeGround`, `#003322`, sampled from its edges) and is pinned to the bottom, `ContentScale.Fit`: spare room is above it on a tall screen and at the sides on a wide one, and the phone still leaves by the bottom edge. Seen on the Moto G in light and dark, and at a squared-off 1500x1500 as a stand-in for the Fold unfolded (`wm size`, reset afterwards); not on the Fold itself.
- **Known, and left as Ian chose it:** the picture is a fixed image, so its wording cannot be translated or read by a screen reader; the phone in it has garbled AI-drawn numbers ("1116 files", "111e pending") and an older look of the Albums tab; the lime "Welcome to" script overlaps the top of the icon and is the loudest thing on the screen. All three were raised with Ian.
- The status bar and navigation bar keep the theme colour, so a strip of cream (light) or near-black (dark) shows above and below the green.

**What went wrong while checking it, and what was done.** The welcome card only shows when the stored `setup_complete` is false, and there is no button for that now, so to see it the flag was flipped in the app's DataStore file with adb, then put back. **The first attempt piped the file through `adb shell`, which goes through a terminal and mangled the bytes; the settings file was left truncated (465 of 665 bytes) and the app crashed on launch twice (`CorruptionException: Value not set`), for about two minutes.** The second method, `adb push` to a temp file then `run-as cp`, is binary-safe, and the file was restored **byte for byte** from a copy taken beforehand (checked with `cmp`); the app then opened normally. No test data was affected, only that one settings file for those minutes. Recorded so nobody pipes binary through `adb shell` again. Also: with Git Bash, a path like `/data/local/tmp` must be protected with `MSYS_NO_PATHCONV=1` or it becomes `C:/Program Files/Git/data/...`.

**Final welcome picture (Ian, 21 Sept 2026, after two of my own alternatives were rejected).** "Welcome to" and the icon are exactly as Ian sent them (the neon script overlapping the icon was deliberate, so it was put back after a cleaner typeface was tried and turned down). The two changes he approved: the phone is a **real screenshot of the current Albums tab from the Moto G** in a drawn frame (the AI-drawn phone had garbled numbers and an old look), and the icon has a **very faint glow**, not the glyph on its own. Built by keeping his top half, giving it an alpha channel from its distance to the ground colour, and compositing it over the ground with the glow and the new phone (`design/icon/welcome-screen-source.png`; exported as `welcome_screen.webp`, 111 KB). The "Sync now" in the screenshot is greyed because nothing was waiting to send; Ian judged that not worth fixing on a splash screen. Seen on the Moto G in light, dark and a squared-off screen; not on the Fold. One extra test photo was added to `DCIM/Camera` on the Moto G while trying to catch "Sync now" enabled.

**Welcome picture, second pass (Ian, same day): it sat too low on a tall phone.** The first version was 1209x2000 (0.60) and the Moto G's app area is about 720x1456 (0.49), so with the picture pinned to the bottom there was a wide empty band above "Welcome to". Rebuilt at **1209x2440 (0.4955)**, the shape of a tall phone's app area: the "Welcome to" and icon block (unchanged) is centred in the top half, and the phone is wider (920 px, was 800) and taller, showing four albums. The Albums screenshot was recaptured at **1.5x** (`wm size 1080x2406` with `wm density 372`, reset afterwards) so it stays sharp when scaled into the frame; at the first try (density 480) the layout was narrower than the real one and text wrapped, which is why a native-width density was used. Seen on the Moto G in light and a squared-off screen: the picture now fills the tall screen. `welcome_screen.webp` is 136 KB.

### 21 Sept 2026 - Retry failed items, and the endless re-run it turned up

Ian: "Retry failed items: build it." The last unticked v0.2 item, and the one the Fold 8 plan (TASK-025) wanted before the real backup.

**What was built.**
- An album with failed files that is being backed up (`RetryFailed.offered`: not Off, and at least one failed) shows **"N failed"** on its Albums row, after "pending" (a failed file is also pending: it is not in OneDrive). Open the album and the header gets a hint and a **Retry N failed** button (the hero-card outlined style; the plain outlined button was dark green on the dark green card and nearly invisible, seen and changed). Pressing it calls `BackupViewModel.retryFailed`: `BackupEntryDao.resetFailuresInAlbum` puts that album's failed rows back to pending with attempts at zero and the error cleared, then a run starts (`runBackupNow`). It only adds work; nothing is removed anywhere. `AlbumBackupCount.failed` carries the figure; no schema change.
- Guide: the "File marks and counts in an album" topic and the album row's status line explain it; regenerated, `--check` clean. Suite 570 (was 565), 0 failed: `RetryFailedTest` (3) and `RemainingWorkTest` (2).

**Found while testing it, and fixed: a permanently failed file made the worker re-run for ever.** To get a real failure on the Moto G, a JPEG named `AUX.jpg` (a reserved name) was put in `PauseTest`: OneDrive answered `400 invalidRequest`, the file reached five failed attempts within seconds and was marked FAILED, as designed. But the log then showed **"backup run finished ... 1 remaining, scheduling next batch" about once a second, indefinitely.** Cause: `countPendingInSelectedAlbums` and `countPendingAll` counted every row that was not uploaded, failed ones included, while `nextPending` skipped rows that had used their attempts. The two disagreed (the DAO's own comment says they must not), so `remaining` never reached zero, the worker chained another batch each time, and every run rescanned the library. It would also have kept the first-backup window from ever lifting and the "Sync now" control lit. Any single failed file on any phone would have caused it; on the Fold 8's first backup one was likely. **Fix:** both counts now take `maxAttempts` and count only rows the queue will still try (`BackupEngine.MAX_ATTEMPTS` at every caller). Verified on the phone: after the fix one run started in 18 seconds instead of dozens; the album row read "1 pending · 1 failed".

**Retry on the phone, end to end** (Moto G, real failure): row FAILED with 5 attempts, press **Retry 1 failed**, row PENDING with 0, a run, FAILED with 5 again (OneDrive still refuses the name), and **five runs started in all, then quiet**, which is the retry costing exactly the five attempts and then stopping. Both themes read correctly; crash buffer empty. **Not exercised:** a retry that then succeeds. There was no cheap way to make a healthy file fail without editing the ledger by hand, and the step after the reset is the ordinary upload path, which is exercised constantly. The test files were removed from the phone afterwards.
- Left as it was: `BackupEntryDao.resetFailures()` (all albums) is still unused. A per-album button was enough for now; a global one is easy to add if wanted.

**Retry's success path, checked 21 Sept 2026 (Moto G).** The five-attempt failure had only been seen failing again. To see it succeed, the 10 pending files of `Temp 4` were marked FAILED with 5 attempts in the ledger (database edited with the app stopped, put back with `adb push` and `run-as cp`), and the album set to Backup: it sat at "10 pending · 10 failed" and nothing was sent, as it should. **Retry 10 failed** then took all ten to UPLOADED with 0 attempts in about seven seconds; the hint, the button and the "failed" marks disappeared and the header read "10 backed up". No crash. The log had no PUT or POST, because those ten already had matching copies in OneDrive and were recognised rather than re-sent, so it was repeated with a file OneDrive had never seen: a unique 3.7 MB JPEG in a new album `zzT21_retry`, marked FAILED (5 attempts) while the album was Off, then the album set to Backup (nothing sent, again). **Retry 1 failed** took it to PENDING, then UPLOADED in eleven seconds, with one `PUT .../Samsung Gallery/DCIM/zzT21_retry/RETRY_1790029148.jpg:/content?conflictBehavior=fail` in the log and the remote size equal to the local size (3,721,428). So both halves are watched now: reset, then a genuine upload. `Temp 4` and `zzT21_retry` were left at Backup, all uploaded.

### 22 Sept 2026 - Confirmation dialog for removing a Settings backup folder

Ian: on Settings, removing a ticked folder from **Folders to back up** happened the instant **Remove**
was tapped, with no confirmation. Added one, matching every other confirm dialog in the app
(`TitleWithHelp` + a guide-driven `(?)` help topic): **Stop watching this folder?**, naming the count
when more than one is ticked, with **Cancel** (does nothing) and **Remove** (does what the old button
did). The body states what `ScopedDirectories.remove` actually does — stops watching, releases the
folder's permission, deletes no ledger row and no album mode, and re-adding restores everything — so
it does not read like a warning about loss it does not cause.

New guide topic `dialog-remove-folder` in `c6_settings.py`, linked from `settings-folders`.
`build_guide.py --check` clean; `HowToGuideConsistencyTest` and the full unit suite pass.

**Checked on the Moto G, both themes.** Ticked Pictures, pressed Remove: dialog appeared, folder list
unchanged. Cancel: dialog closed, both folders still there. The `(?)` help button opened the right
topic. Removed for real: Pictures left the list, DCIM stayed. Re-added Pictures through the folder
picker to leave the phone as found. Repeated the tick-and-Remove step in dark mode
(`cmd uimode night yes`): title, body and both buttons read clearly against the dark surface. No
crashes from com.gallery.sync at any point (one unrelated system crash from com.google.android.as
during launch, logged and ignored).

### 22 Sept 2026 - Retry failed items stress-tested: killed mid-run, airplane mode mid-run, and on an Archive album

Three cases from the "what's left to test" review, all on the Moto G, all against real ledger rows with
real files (a database edit while the app was stopped, put back byte-safe, then a real Retry press).

**Killed 1.2 seconds into a retry, before any network activity.** `Temp 6`, 10 files force-failed, set to
Backup, `Retry 10 failed` pressed, then `am force-stop` at +1.2 s. Right after the kill the ledger already
read PENDING/0 for all ten — `resetFailuresInAlbum` commits before the upload starts, so the reset itself
is safe from a kill. Reopening the app (no other action) let WorkManager resume the queued work on its
own: all ten reached UPLOADED within 5 seconds of reopening. No crash.

**Airplane mode turned on 1 second into a retry.** `Temp 7`, same setup. `cmd connectivity airplane-mode
enable` cut the network — and, being reached over wireless debugging, cut adb's own connection to the
phone at the same moment, so nothing could be observed or reversed remotely until Ian toggled airplane
mode off on the phone by hand. By the time adb reconnected, all ten files were already UPLOADED — the
app had recovered entirely on its own while unobserved, with no relaunch. No crash. **Caveat:** the
window between enabling airplane mode and Ian's fix was unobserved, so exactly when the retry recovered
inside that window isn't known — only that it did, unattended.

**Retry on an Archive-mode album**, not just Backup. `Temp 8`, 10 files force-failed while Off, then set
to **Archive** (through the real confirmation dialog). The album screen read "Archive · 10 files / 10
failed" with the same hint and **Retry 10 failed** button as a Backup album — `RetryFailed.offered` only
checks the mode isn't Off, so this was expected but had never been seen on Archive specifically. Pressed
it: all ten reached UPLOADED in 12 seconds, no crash, and **all ten files were still on the phone
afterwards** (`ls` on the folder) — Retry only re-sent them, it archived nothing, which is correct: Yes on
Archive's own check is a separate, explicit step this never touched.

**One thing learned about the test rig, not the app:** the Moto G is reached only over wireless
debugging, so any test that disables Wi-Fi (airplane mode, a Wi-Fi toggle) is also a test that cuts the
test harness's own access — recoverable only by hand, on the phone. Worth remembering before the next
airplane-mode test.

### 22 Sept 2026 - Archive gets real Settings: an age filter (with a Settings default) and an opt-in "come of age" notification

Ian, after the Archive Settings band sat at "Coming soon" since 18 Sept: proposed an age filter for the
Archive tab plus a notification, asked what effect `POST_NOTIFICATIONS` has on the Play listing (answered:
one manifest line, not on Google's restricted-permission list, no Data Safety change, no extra review —
unlike background location or Accessibility), then approved both with the trigger and default discussed.

**The age filter.** `ArchiveAge` (1 hour / 1 day / 1 week / 1 month / 1 year / All — Ian's list, a shorter
first step than Camera's own age control since Archive removes a file outright rather than shrinking it).
A control on the Archive tab, mirroring Camera's own pattern: files younger than the filter are held out of
`plan` into a new `hiddenByAge` list — the same standing as the opt-out list, so `validate()`,
`nextRemovalRequest()` and everything else that acts on `plan.entries` needed no change. The control stays
visible even when the filter empties the list (`state.archiveAlbums.isNotEmpty()`, not `!plan.isEmpty`), so
there is always a way back to a wider view — checked deliberately, since gating it on the file list itself
would have been a dead end. A held-back row still swipes: swiping it left pins it permanently
(`FilePin`), a stronger version of the same choice. Settings → Archive gets a default (**All** out of the
box, so nothing changes until chosen otherwise) that only sets where the tab's filter starts each visit;
changing the filter on the tab itself is a session choice and does not write the default back.

**The notification.** Off by default. `POST_NOTIFICATIONS` requested at runtime (13+) only when the switch
is turned on; the switch reflects the OS permission's real state on every recomposition rather than the
stored preference, so a permission denied or later revoked in the phone's own Settings shows as off with
an explanatory line, never as a lie. `ArchiveReadyNotice.shouldNotify(lastSeen, current)` fires only on
**growth** past the last count it was asked about — never merely because the count is still above zero —
reusing the exact signal `ExitWarning`'s readyCount already uses (`BackupEngine.redundantLocalCopies`, a
local ledger read, no extra network cost). Checked at the end of every complete `BackupWorker` run, the
same point the photo/video optimisers are queued, wrapped in `runCatching` so it can never fail the backup.
Tapping the notification opens `MainActivity` straight onto the Archive tab (`EXTRA_OPEN_ARCHIVE`, standard
launch mode so `onCreate` always sees the Intent fresh). Deliberately **additive**, never a replacement for
the Albums tab summons or the exit-warning dialog — see `ArchiveReadyNotifier`'s own doc comment for why a
permission-gated channel can never be the only way the user finds out.

New: `ArchiveAge.kt`, `ArchiveReadyNotice.kt`, `ArchiveNotifyPermission.kt`, `ArchiveReadyNotifier.kt`.
Guide: `archive-age-filter`, `settings-archive-default-age`, `settings-archive-notify`; `archive-file-list`
and `settings-section-archive` updated. Suite 581 (was 570) before this session's other work; +11 here
(`ArchiveAgeTest`, `ArchiveReadyNoticeTest`), 0 failed.

**Checked on the Moto G, both themes.** Age filter: dropdown shows all six options in order; picked 1 year
against files test-pushed today (so all read as "modified" within the last year, not by EXIF date, which
is correct — the filter reads modification time, same as Camera's) — count dropped to 0, hint read "21
files are younger than your filter…", **Check these files** correctly disappeared (nothing to check),
the control itself stayed put. Swiped a held-back row left: it moved to "Not archiving" and the ledger
showed it pinned (`modeOverride='BACKUP'`); swiped back, unpinned. Widened back to 1 hour: count returned
to 21. `(?)` help opened the right topic. Dark mode: selector and rows all legible.

Notification: turned the Settings switch on, Android's own permission dialog appeared, granted — channel
`archive_ready` confirmed registered (`dumpsys notification`, importance DEFAULT). Pushed one genuinely new
file into an Archive-mode album, let the ordinary sync path upload and verify it (no manual "Check" step
needed — `redundantLocalCopies()` reads the ledger, which the ordinary upload path already updates), and a
real `BackupWorker` run completed: a notification posted, title "Files ready to archive", body "22 files
are verified in OneDrive and ready to leave this phone." (21 existing + 1 new), correctly omitting the
single-album name suffix since three albums were involved. Tapped it: app opened directly on the Archive
tab reading 22, notification cleared (auto-cancel). A second complete run with nothing new correctly sent
no second notification — the "grown, not merely nonzero" rule holding in practice, not just in the unit
test. Revoked the OS permission by hand (`pm revoke`) while the Settings preference stayed on: the switch
correctly read off, with the blocked-message line underneath; tapped it, got the system dialog again,
declined it this time — switch stayed off, no crash. Re-granted the permission and left the switch off
(the same resting state a fresh install would show). Crash buffer empty throughout.

**One thing found about the test rig, not the app**, worth recording so it isn't rediscovered the hard way:
`Logger.formatTag` truncates a tag to 23 characters, and `"BackupWorker"` and `"ArchiveReadyNotifier"`
both overrun that — `"GallerySync/BackupWorke"` (missing the final "r") and `"GallerySync/ArchiveRead"`.
A `logcat` grep for the untruncated name never matches and reads as total silence, which burned a long
stretch of this session's testing before the cause was found. Grep a prefix that survives truncation
(`"GallerySync/BackupWork"`) instead.

### 22 Sept 2026 - the welcome screen no longer blinks past itself on launch

Ian: *"sometimes when you open the app the Welcome screen blinks on"*. Cause: the whole welcome step is
`Modifier.clickable(onClick = onNext)`, tap-anywhere-to-continue, and a tap that bleeds through from
opening the app — a double-tap on the launcher icon, or the very tap that launched it landing on the
first frame — reaches that same listener and dismisses the screen before anyone has seen it.

**Fix:** a fixed 3-second floor (`WelcomeMinimumVisibleMillis`) timestamped when the step is first shown
(`remember { System.currentTimeMillis() }`); the tap handler is a no-op until that much time has actually
passed, then works exactly as before. Nothing about the normal interaction changes past the first three
seconds. Guide (`setup-welcome-tour`) updated to say so. Suite and compile clean.

**Checked on the Moto G.** Flipped `setup_complete` false from a freshly pulled copy of the phone's own
DataStore file (not a stale snapshot — the earlier `ds_backup_original.pb` from 21 Sept predates several
settings added since and would have been the wrong thing to restore from), launched, and tapped the
welcome screen immediately: it stayed up. Tapped again after 3.5 seconds: advanced to the tour's second
step normally. Restored the real DataStore byte-exact (`cmp` equivalent: read-back comparison in Python,
identical) and relaunched: no crash, straight to the Albums tab as before.

### 24 Sept 2026 — Restore shows progress while it runs (Ian)
The Restore tab had no sign a run was under way beyond the button reading "Stop restoring". `RestoreBar` now carries a progress bar and "N of M restored · P%" under the button while `running`. Byte-weighted (`RestoreUiState.progress`), a failed file counts as passed. Verified on the Moto G: Car Show (34 files, 105 MB) moved aside, restored from OneDrive, bar and count advanced, folder back to 105 MB. The originals were parked in `/sdcard/Download/CarShow_hold` (duplicates now, can be removed).

### 24 Sept 2026 — setup tour draws the real tabs; Cloud Storage is one checklist (Ian)
The tour backdrops for Albums, Restore, Archive and Settings were separate hand-drawn copies and had fallen behind the real tabs. They are now `AlbumsTabPreview`, `RestoreTabPreview`, `ArchiveTabPreview` and `SettingsTabPreview`, built from the real header cards, rows and section bands with sample data, under a touch-swallowing layer (`PhoneScreenBackdrop`). Wizard step 4 is one list of clouds with a box each, nothing ticked to start, the first ticked being the free cloud (`main`) and the rest the Pro/trial ones; unticking a signed-in cloud signs it out. Verified on the Moto G (all four backdrops and the step; Google Photos ticked, became main (free), Connect shown). The How To Guide text for step 4 still describes the old radio-plus-extras layout and is Ian's to update.

### 24 Sept 2026 — Backblaze B2 proven on a real account (Ian)
First non-OneDrive, non-Google cloud run against a real account. Bucket `iandev-gallerysync-test`, endpoint `s3.us-east-005.backblazeb2.com`, a bucket-restricted Read and Write key (never the master key). `Movies` paired with Backblaze B2, album `blaze-test` at Backup: 10 files uploaded, then 5 more moved in were picked up with no button press. Ledger and bucket agree: 15 files, 50.5 MB, under `GallerySync/blaze-test/`, no retries. Rows have no cloud-side size, as designed (backup-only). The "rejects those keys" wall was two typos (a dropped digit in the key ID, then j for J in the secret); after 401/403 the check now asks with a one-item listing and names the box at fault (`S3Client.refusalCode`, `S3CompatibleCloud.rejectionMessage`). IDrive e2 shares the code path and is still unrun; Drive, Dropbox and pCloud still need registrations.

### 24 Sept 2026 — Google Drive and Dropbox proven on real accounts (Ian)
Google Drive (12 files, `camera roll`, 76.5 MB) and Dropbox (46 files, `dropbox`, 187 MB) uploaded from the Moto G with no retries and no errors; Ian confirmed the Dropbox files in his account. Drive reuses the Google Photos client ID (`drive.file` scope, same redirect), so it needed only the Drive API enabled and the scope added under Data Access in the Google Cloud Console. Dropbox needed `files.content.write`, the redirect `com.gallery.sync:/dropbox`, and **Enable additional users** because the app was created under a different account from the one signed in on the phone. Dropbox refused the wrong account with "This app has reached its user limit". pCloud is submitted and waiting on pCloud's manual approval; IDrive e2 is unrun. Albums that went wholly to another cloud no longer report "verified in OneDrive" (`AlbumRow.showsOneDriveClause`).

### 24 Sept 2026 — Restore works for Dropbox, verified on the Moto G (Ian)
First non-OneDrive Restore. New `CloudDownloader` (implemented by `DropboxCloud.openStream`: `files/download` by the `id:` recorded at upload); `DownloadMissingFile` picks the source from the row's `location`; `BackupEntryDao.fetchableElsewhere` feeds `BackupEngine.filesNotOnThePhone`; `CloudCapabilities` gives Dropbox `restore = true` only. A restore is checked against the size the phone recorded at upload, so the row stays unverified (`remoteSizeBytes` NULL) and Archive/Sync stay off: verified sizes are what those need, and making Dropbox rows look verified would have let the wizard optimise plans act on files whose way back is not built. Device test: 5 Dropbox files moved out of `/dropbox`, listed on the Restore tab as 5 to download, restored with the progress bar, byte sizes identical to the originals. The first attempt failed cleanly with Dropbox 400 `files.content.read` missing: the app only requested the write scope. Now both are requested, and a sign-in without read reports "sign in to that cloud again". **Landing folder:** MediaStore only allows photos under DCIM/Pictures and videos under DCIM/Movies, so the album's own folder is used when it is one of those, and otherwise `DCIM/<album>` (Ian's `/dropbox` at the top of storage is not one). **Not done:** the Restore tab still lists OneDrive's drive and says "Checking OneDrive"; a Dropbox-only user would see a OneDrive warning.

### 24 Sept 2026 — Restore for Google Drive, Backblaze B2 and IDrive e2; Restore tab no longer assumes OneDrive (Ian)
`GoogleDriveCloud.openStream` (`files/{id}?alt=media`, covered by the existing `drive.file` scope) and `S3CompatibleCloud.openStream` (signed GET on the object key recorded at upload, via `S3Client.getObject`) join `DropboxCloud` as `CloudDownloader`s. All four get `CloudCapabilities.RESTORE_ONLY`; Google Photos and pCloud stay backup-only (pCloud download not built). Device test on the Moto G: 3 Drive files and 3 Backblaze files moved out and restored together, byte sizes identical to the originals, progress bar shown, "6 back on this phone". IDrive e2 shares the S3 path and is untested on a real account. Files under `Movies/` that are photos land in `DCIM/<album>` (Android only allows photos under DCIM/Pictures). Restore tab: with OneDrive not connected the drive listing and its OneDrive warning are skipped (`RestoreViewModel`, ledger rows only); the empty-list wording and a failure reason say "cloud", and the guide sentence was updated. That skip is unit-untested and device-untested (would need OneDrive signed out); the OneDrive-connected path was re-checked on the phone. Archive and Sync still need verified sizes per cloud, and are the next stage.

### 24 Sept 2026 — Archive for Dropbox, Google Drive, Backblaze B2 and IDrive e2, stage 1 of TASK-027 (Ian)
Built and unit tested; **not yet run on the phone** (album modes are only set by the user, so Ian sets an album to Archive for the test). `CloudVerifier.sizeOf(remoteItemId)` on the four adapters (Dropbox: a one-byte ranged download whose `Dropbox-API-Result` header carries the size, so no new scope; Drive: `files.get?fields=size,trashed`, trashed = gone; S3: signed HEAD, `Content-Length`). `BackupEngine.confirmStillInCloud` asks the file's own cloud, live, for rows that are not OneDrive; `ElsewhereVerdict` decides, and only present-at-exactly-the-phone's-size confirms (unknown = do not remove). OneDrive rows and files with no row take the old listing path unchanged. **Prune fix:** `forgetAlbumsNotOnDevice` would have erased the rows of an album Archive emptied on these clouds (their size is NULL by design), and with them the ids Restore needs; uploaded rows holding an id are now kept for every cloud (androidTest `LedgerPruningTest`, 8 pass on the Moto G, run by hand with `am instrument` because `connectedAndroidTest` uninstalls the app). `CloudCapabilities.RESTORE_AND_ARCHIVE` for the four; Sync stays off (stage 2). CLAUDE.md's Deletion rule now states the same bar for these clouds. Archive tab wording says "your cloud". Not done: the ready-to-archive notification still counts only OneDrive-verified rows.

**Archive stage 1 verified on the Moto G, 25 Sept 2026 (Google Drive).** Album `camera roll` set to Archive by Ian. Check: `confirmStillInCloud: 9 confirmed, 0 no longer in OneDrive, 0 could not be checked` (the log line still says OneDrive; the counts are the ones that matter), each answered live by Drive. Ian's three restored files were correctly left alone (Restore pins what it brings back). Android's trash dialog "Allow GallerySync to move 9 photos to trash?" raised once (batch 1 of 1); the 9 files were renamed `.trashed-<expiry>-<name>` in place, as on every earlier trash test. The Restore tab then offered exactly those 9 ("9 files · 56 MB, 9 to download") and restored them from Drive; all 9 byte sizes equal their trashed originals. Not exercised on the device: the prune after an album empties completely (the pinned files kept `camera roll` on the scan); that is covered by the androidTest. Backblaze, Dropbox and IDrive share the verifier interface but their live Archive checks have only run against local servers. The trashed originals stay in the phone's Trash until Ian empties it.

### 25 Sept 2026 — Sync for Dropbox, Google Drive, Backblaze B2 and IDrive e2, stage 2 of TASK-027 (Ian)
Built and tested; **device test with Ian pending** (he sets the Optimise switches and an album to Sync). See TASK-027 for the design: a live per-file check (`CloudOriginalCheck`) stands in front of every in-place overwrite, because Sync leaves the cloud as the only holder of the original. Verified so far: unit tests (gate, held/expiry, capability list matches the SQL list, Archive of an optimised file by MediaStore id) and 11 androidTests on the Moto G run by hand (`SyncCandidatesTest`, `LedgerPruningTest`). Not on a real cloud yet.

**Sync verified on the Moto G with Dropbox, 25 Sept 2026.** Ian set `dropbox` to Sync with Optimise photos on. `OptimiseWorker: sync photos: proxying 41 of 41` then `ProxyApplier: proxied 41 files, reclaimed 138312679 bytes, 0 not worth proxying`; 187 MB became 31 MB, each file gated by the live Dropbox check first. A pulled proxy is a valid 2048x1152 JPEG with its EXIF and GPS kept. The 5 pinned files were skipped. Restore in place: 4 files restored from Dropbox, each at exactly its recorded original size, no staging leftovers, and they were pinned (`modeOverride = BACKUP`) so Sync does not shrink them again. Archive of an optimised file and Sync on Drive / Backblaze are still to test.

### 25 Sept 2026 — Signing out of a cloud no longer re-pairs its folders (Ian)
`CloudProvidersViewModel.disconnect` used to move every folder paired with that cloud, and its unsent files, to the main cloud. Ian signed out of Dropbox to change account and his `dropbox` folder silently became OneDrive; new files in it (a copy, `dropbox (1)`) then went to OneDrive. Ian: this must be the user's decision, since a sign-out may be a mistake or the files may be meant to stay. Now `disconnect` only signs out. A folder stays paired and its unsent files wait: `BackupEngine.uploadPending` already skips a cloud that is not connected without failing rows. Moving a folder is done from its own menu in Settings. The main cloud is no longer re-pointed either. `setMain` (the wizard's choice of free cloud) is unchanged.

**The sign-out prompt, built 25 Sept 2026 (Ian).** Tapping Sign out on a cloud in Settings now opens (1) a warning (backing up to that cloud stops; nothing is deleted; folders paired with it wait), then (2) if that cloud holds files that are not at full size on the phone, a choice: *Leave them there* (signs out, changes nothing) or *Restore them to the phone first* (`RestoreEverythingFrom`: the Restore tab's two verbs over the whole cloud, each download checked against its recorded size, progress with a Stop that stays signed in; it signs out only when every file is back, and if any failed it stays signed in and offers *Sign out anyway* / *Stay signed in*). With nothing to bring back it signs out straight after the warning. Ian dropped the third idea (move the files to another cloud): a user can restore to the phone and back up again if they choose. Unit tested (`RestoreEverythingFromTest`); **not yet seen on the phone** (signing out costs a re-login, so Ian tries it). The wizard's untick of a cloud still signs out directly.

### 25 Sept 2026 — An album in two top-level folders now counts in both (Ian)
*Where each folder goes* listed no Movies row although 12 files sat in `Movies/blaze-test`. An album is a folder's leaf name, so `DCIM/blaze-test` (3 restored files) and `Movies/blaze-test` (12) were one album, and `MediaAlbum.topLevelFolder` named only the first, DCIM. The folder list, and `SetFolderDestination`'s re-pointing of unsent rows, now use `MediaAlbum.inFolders` (per-folder counts) through `sharesByFolder()`. Seen on the Moto G: a Movies row, 12 files, Backblaze B2. Uploads were never affected: `BackupEngine` routes each file by its own path. **Known limit:** the album row's own "sent to" line and its routing still speak for the album's first folder, and re-pointing a folder retargets unsent rows by album name, so an album that lives in two folders retargets both folders' unsent files together. Ian's `blaze-test` is the only case; nothing more is done about it until it matters.

### 25 Sept 2026 — "OneDrive" removed from wording that applies to every cloud (Ian)
Ian saw "Checking OneDrive for more…" under the Restore header while restoring from Dropbox. About 40 strings that describe cloud-agnostic behaviour now say "your cloud": Restore's checking and unreachable lines, the Albums Rescan label (now just "Checking…", the longer text clipped in its button), Archive's check and refusal messages, the ready-to-archive notice and exit warning, sync and optimise explainers, the wizard and abort wording, upload status and stop reasons, the error messages, and the in-app topics on what the app does, modes, Archive, the promise, optimising, deleting and the recycle bin. **Kept on purpose because they are true of OneDrive only:** sign-in and the OneDrive folder picker, the deletion window (moves OneDrive copies to OneDrive's recycle bin), the per-album "verified in OneDrive" line (shown only for OneDrive albums), first-run reconciliation, and the Camera manual optimise. **Not touched:** `help_topics.xml` (the (?) help pages, ~40 mentions, several describing the old Settings layout, e.g. the removed "Current folder location") and Ian's guide in `tools/guide`; both need a content pass of their own. `BrowseScreen` and `RetrieveScreen` hold more OneDrive text but nothing navigates to them.

### 25 Sept 2026 — Sync no longer tries files that are gone from the phone; Sync verified with Drive, Backblaze and IDrive e2 (Ian)
**Bug found on the IDrive test.** `e2test` uploaded, but Sync stopped at once: `stopped at 20180628_180020.jpg (RecoverableSecurityException)`. The album `Temp0` is set to Sync and held 16 ledger rows for files Archive had removed earlier (trashed, `cloudDecision = ARCHIVED`). They were pinned until Ian's clean-up of pins, then became candidates; the queue is largest-first, so the trashed 8 MB file led it, failed three times and stopped the run, and nothing behind it was optimised. The four candidate queries (`proxyCandidates`, `proxyCandidatesAll`, `videoOptimiseCandidates`, `videoOptimiseCandidatesAll`) now also require `localMissingSinceEpochMillis IS NULL` and `cloudDecision` not `ARCHIVED`. androidTest `SyncCandidatesTest.rowsWhoseFileHasGoneAreNeverCandidates` (4 pass on the Moto G). After the fix `e2test` shrank 10 of 10 (49.0 MB to 9.6 MB). **Still open:** one file that fails with a permissions error still stops the whole run behind it; a per-file hold would be safer.

**Device results, 25 Sept 2026.** Sync (upload, live check, shrink), Restore in place and Restore of archived files were run on the Moto G against real accounts: Dropbox (41 shrunk, 4 restored in place, 36 archived and restored), Google Drive (10 shrunk, 5 restored), Backblaze B2 (12 shrunk, 4 restored in place, 16 archived and 10 restored) and IDrive e2 (10 uploaded and shrunk). Every restored file matched its recorded original size. Archive's live check refused 10 Dropbox files while Dropbox was signed out ("could not be checked": nothing removed) and confirmed the B2 files. Restoring from another cloud lands photos under `DCIM/<album>` when the album folder is not a photo root.

**One failing file no longer stops a Sync run, 25 Sept 2026 (Ian).** `ProxyApplier.apply` used to stop at the first file that failed three attempts, so one file ahead of the rest (largest first) blocked every photo behind it. Now that file is held back for six hours in memory (`CloudOriginalCheck.holdFailed`) and the run goes on; three failures in a row still stop it (`FailureStreak`), since that points at the run (no space, revoked grant) and not the file. Nothing is recorded as shrunk unless it was, so "which photos are still full quality" stays answerable from `isProxied`. Unit tested (`FailureStreakTest`); the hold-and-continue path itself is unexercised on the phone, because the trashed-file case that caused it is now filtered out earlier.

### 25 Sept 2026 — How To Guide rewritten for the multi-cloud app (Ian: "you can start rewriting")
The guide (`tools/guide/content`, one source for the published pages, the setup page and the (?) pop-ups) was brought up to date with the app. **First-time setup:** one list of clouds with nothing ticked, the first ticked is free, one Connect card per cloud finished before the next, no skip, and a **Goes to** button that starts blank. **Albums:** the New tag and *Show new Albums*, the cloud line for OneDrive against "N sent to <cloud>" for the others, and what a cloud that cannot do a mode says. **Restore:** every cloud, where restored files land (DCIM/<album> when the album's folder is not a photo root), the progress bar. **Archive:** the live per-file check for Dropbox, Google Drive, Backblaze B2 and IDrive e2, the original's size after Sync, and that being unable to ask removes nothing. **Settings:** Clouds and Sign out (warning, then leave or restore first), *Where each folder goes* replacing *Current folder location*, the deletion choice worded as Cloud, and the removed default-mode setting. **Concepts and FAQ:** how verification works per cloud, where data lives, four new questions (signed-out folders wait, rejected keys, could not check, reconnect that cloud). Everything that is true of OneDrive only keeps its OneDrive wording (the OneDrive folder chooser, whose two topics say it is not on Settings in this version, the deletion window, the OneDrive album line, first-run reconciliation, the Camera manual optimise). The pages were regenerated (`docs/how-to-guide*.html`, `docs/setup-guide.html`, `help_topics.xml`) and the guide consistency test passes. **Not covered:** the Delete Account Info and Privacy Policy pages in `docs/` are separate hand-written pages and still describe OneDrive and a Microsoft account.

### 25 Sept 2026: release build for Play Internal Testing (versionCode 3, 0.3.2)

Bumped `versionCode` 2 to 3 and `versionName` 0.3.0 to 0.3.2 and built the signed bundle (`:app:bundleRelease`, 17.7 MB, `app/build/outputs/bundle/release/app-release.aab`). It carries the multi-cloud work (Dropbox, Google Drive, Backblaze B2, IDrive e2), the sign-out prompt and the rewritten How To Guide. The upload key SHA-1 is `F4:C5:2D:C2:A1:E5:40:A1:72:AB:ED:0D:CD:26:92:3B:83:D0:62:84`; Play re-signs with its own app-signing key, which is a different fingerprint. **Open, unverified:** OneDrive (`msal_config.json` and the manifest carry the hash of the *debug* key) and the Google Android OAuth client (registered with the debug SHA-1) may reject a Play-signed install until the app-signing key is registered. Nothing signed by Play has ever run a sign-in. Upload to Play Console is Ian's.

### 25 Sept 2026: Privacy Policy and Delete Account rewritten; Cloud capitalised; tick becomes select

`docs/privacy-policy.html` and `docs/delete-account.html` rewritten for the multi-cloud app (all seven clouds, what each asks for, keys held encrypted, the optional notification, Google Play as the only purchase channel, the 30-day trial wording Ian gave, and how to withdraw access at each provider). The provider steps for Google, Dropbox and pCloud are written from general knowledge and have not been checked on those sites. pCloud is named although its approval is pending. Ian's rule, same day: **"Cloud" is capitalised in every document and on the app's screens** (`strings.xml` values, the Restore failure reasons in `DownloadMissingFile.kt` and `RestoreProxyInPlace.kt`, the guide, the two pages); icon descriptions ("cloud icon", "cloud badge", "cloud with a tick"), ids and the pCloud name stay as they were. **"Tick" becomes "select"** where it is an action or a state ("deselect", "checkbox"); "tick" stays where it names the drawn mark (green tick, tick or red cross). The guide pages and `help_topics.xml` were regenerated and the unit tests pass. Not installed on the Moto: a debug build there would block the Play install of version 3, which is signed with a different key.

### 25 Sept 2026: the Play build on a fresh Moto G, and Restore writes back into the folder it left

**Fresh install from Play Internal Testing (versionCode 3, 0.3.2), Moto G, real accounts.** The Play-signed build's key is `A9:85:65:5D:F5:C5:A9:12:62:23:D4:8D:78:73:23:81:FF:AE:24:24` (read off the installed APK with `apksigner`); the `deployment_cert.der` in Play's *Download certificates* zip matches it. **OneDrive, Google Photos, Dropbox and IDrive e2 all signed in on it** with no registration of that key anywhere, so neither the MSAL hash nor the Google Android client needed a release-only config. The trial started and let the three extra clouds through. First backup (276 files, four clouds) finished; a Camera album made from 7 photos and a video appeared as a new album at Off, was set to Backup by Ian, and uploaded to OneDrive. Photo Sync shrank Temp01 and Temp02 (33 MB to 6 MB). **Optimise video is off by default**, so PauseTest's videos did not shrink until Ian turned it on (Manual, Straight away): the 107.7 MB clip became 20.8 MB, played in full, and Restore in place returned it to exactly 107,719,409 bytes; five smaller clips were left alone. **Archive then Restore on IDrive e2, twice:** 20 photos archived, Trash emptied by Ian, restored from IDrive e2 alone to exactly 68,684 KB, the album came back at Off (never Archive) with all 20 kept at full size; after opting the files out of the pin the second Archive ran and the album's mode retired again. The Play build's database cannot be read (`run-as` refuses a non-debuggable package), so results came from the screen, the disk and the network counters.

**Found: a restored top-level folder split into two.** Restore creates files through MediaStore, which only allows photos under DCIM or Pictures, so `/iDrive` came back into `DCIM/iDrive`. Routing is by top-level folder, so those files would then follow DCIM's cloud. **Fix, built the same day, not yet run on a device:** `RestoreFolder.pick` chooses the granted folder named for the album when MediaStore could not write there; `SafMediaWriter.restoreFolderFor` and `createFile` write into it through the tree grant (download to a cache file, check the size, then create the document, rescan and wait for the MediaStore row; nothing here deletes). `DownloadMissingFile` tries it first and falls back to the MediaStore route when the grant cannot be used before anything is read. **No Room migration was needed:** the folder is found from the granted trees rather than recorded per row, so a file archived before this change is covered too. Ian had approved recording the folder; the lookup made that unnecessary, and it stays an option if two folders ever share an album name. Unit tests: `RestoreFolderTest` (8). Also the same day: the 3-minute delay chip is gone (six chips, two rows of three); the Backup Progress card lists how many files are pending per cloud, not x of y; Backup Plan is capitalised; Cloud is capitalised on the app's screens and in every document; tick became select except for the drawn mark, which is now a check mark. The Privacy Policy and Delete Account pages were rewritten for every cloud.
