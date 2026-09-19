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
- [ ] Retry failed items from the UI
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
- [ ] **Album modes in the UI.** Schema 4 carries Off/Backup/Sync/Archive; the screen is still a
      switch. See TASK-012.
- [ ] **Running count of space saved, per album and in total.** Each album row says what has already
      been freed and what its selected mode could free, updating as the mode changes. Same
      aggregates the floor uses, so the two screens cannot disagree. Added by Ian 19 Aug 2026. See
      TASK-011.
- [ ] **Sync scope — two toggles, photos and video, and they gate optimising only.** Revised by Ian,
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
- [x] **Video transcode for old clips**, age a user setting (wired to Settings 18 Sept 2026, see the last entry) — see TASK-013. The write needs no tap
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
- [ ] **Restore replaces the proxy; it does not download a second copy.** Supersedes the
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

- **The v0.2–v0.4 checkboxes lag the log.** Still unticked: *Album modes in the UI*, *Guided first run*,
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
