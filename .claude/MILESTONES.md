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
- **"Old" is a user setting, decided by Ian 19 Aug 2026.** Never / 30 days / 90 days / 6 months /
  1 year, defaulting to 1 year, with 30 days enforced as the minimum. "Old" is not a fact about
  anyone's footage — client work gets edited for months, family video never gets opened again — so
  the same reasoning that made the storage floor a setting applies. **It gates downscaling only and
  never uploading**: a clip is uploaded immediately whatever its age, because a threshold that held
  new video out of OneDrive would rebuild the founding failure while wearing the name of the fix.
  Measured against `dateModifiedEpochSeconds`, which needs no schema change and errs toward leaving
  video alone. See TASK-011.
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
- [ ] **Start time for the first backup.** The initial whole-library upload is the heaviest thing the
      app ever does. User-set, default overnight, charging required for that first run.

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
- [ ] **Video proxies for old clips**, with the age threshold a user setting — see the video
      section above. Gated on v0.4 retrieval and on a transcode cost measured against real 8K footage.
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

## Hardware verification log — Galaxy Z Fold 4 (SM-F936U)

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
- **`POST_NOTIFICATIONS`** — needed for the floor notification, and it appears on the Play listing.
- **Language dropdown** — wire the per-app locale mechanism now and ship English only, or defer.

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
