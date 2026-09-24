# GallerySync — Agent Rules

## Project
Android app that makes cloud-hosted photos and videos (OneDrive, Google Photos)
accessible to any third-party app (e.g. CapCut) without requiring local storage.
Core mechanism: Android ContentProvider + on-demand download from cloud APIs.

Built for Samsung Galaxy primarily; tested on LG and Moto as well.

Stack: Kotlin, Jetpack Compose, Room (SQLite), Hilt (DI), WorkManager (background sync),
Retrofit + OkHttp (cloud APIs), JUnit + Mockito (unit tests), Espresso (UI tests).

## Design principle — GallerySync is invisible
**It is not a gallery app and must never become one.**

Its only job is making files present. Viewing, search, face grouping, editing, sharing,
albums — the phone's existing gallery already does all of that, and rebuilding any of it
would be worse than what the user already has.

- Feed the existing gallery, do not replace it. A file with local bytes shows up in Samsung
  Gallery and every other app automatically, because it is an ordinary file.
- GallerySync's own UI stays minimal: setup, album selection, storage budget, and a plain
  list for retrieving what is not on the phone. No photo grid, no thumbnail browser, no
  search, no editing.
- If a task starts to look like building a gallery, it is the wrong task.

See `.claude/MILESTONES.md` for the platform constraints this rests on — chiefly that a file
with no local bytes cannot appear in any gallery app, which is a platform limit rather than a
design choice.

## Monetization
Free app on Google Play with a single one-time in-app purchase to unlock Pro.

Free tier:  OneDrive sync, ContentProvider access, core sync engine
Pro tier:   every cloud beyond OneDrive (Google Photos, Google Drive, Dropbox, pCloud, IDrive e2, Backblaze B2),
            unlocked by the IAP, after an optional 30-day trial that is a hard gate and never an auto-charge

IAP product ID: pro_unlock
Billing library: com.android.billingclient:billing-ktx (latest stable)

Rules:
- Gate every second cloud behind `MultiCloudEntitlement` (purchased or trial running), which asks
  BillingRepository.isPurchased() for the purchase half. Every non-OneDrive cloud is backup-only
- Never gate OneDrive or the ContentProvider — those are always free
- BillingRepository is the single source of truth for purchase state
- Never hardcode purchase state — always query BillingRepository
- Test with Google Play test accounts and test product IDs during development

## Hard Rules — all agents must follow

### Deletion — GallerySync never permanently deletes anything
This is absolute and applies to every file, in every location, without exception.

- **Nothing leaves the gallery unless the user chose that for that album.** Stated by Ian,
  19 Aug 2026. This governs *whether* a removal happens; every rule below governs *how* one must
  happen once it does.

  **The album mode is the consent, confirmed once when the mode is set.** Setting an album to
  Archive *is* the user saying "take this album off the phone once it is safely in OneDrive".
  Switching an album to Archive must raise an explicit confirmation before it takes effect, saying
  what Archive does: it checks the files are in the cloud, and the local copies then leave the
  gallery for the phone's trash. After that the mode stands until it is changed: no per-file
  approval and no repeat prompting, which would make the mode unbuildable and is not what this rule
  says.

  **The dialog no longer has to spell out that files added later are covered.** Removed by Ian,
  28 Aug 2026, when he rewrote that copy. The requirement had been to state the standing-instruction
  property in the dialog itself; his judgement is that the mode's name and the album row that shows
  it carry that, and that the dialog reads better saying what Archive does than enumerating its
  consequences. **The property itself is unchanged and still governs the code** — see below.

  **"Safely" is not a judgement call.** Graph confirmed the file *and* the byte size it reported
  equals the local size — `BackupEntryDao.verifiedInCloud()`, the same bar as every other removal.
  Nothing weaker qualifies. This is the check that has to hold, because it is the only guarantee the
  UI is allowed to make.

  What the rule forbids is removal the user did not choose: uploading, backing up, syncing,
  proxying, a storage budget, or any worker deciding on its own that a file should go. Removal
  follows from a mode the user set, and from nothing else. Note the standing-instruction property —
  a file added to an Archive album later is covered by the mode already set, and is removed without
  anyone being asked again. That is how the mode is built and it is not in question; what changed on
  28 Aug 2026 is only whether the confirmation dialog has to say it out loud. It does not.

  The property still binds the code: anything that widens what an Archive album contains widens what
  will be removed under a choice made earlier, so **the album's membership is not a free variable**.
  **Emptying an album retires its mode. Ian, 18 Sept 2026, superseding 27 Aug 2026** (when the rule was
  that the mode outlives the files, because a camera, a download or a file manager can refill the
  folder). When an Archive run removes the last file, the app forgets the album's mode: the preference
  row is deleted and nothing is written. The album leaves the Albums tab, and if Restore or anything
  else refills the folder it comes back as a *new* album at the default mode for new albums, which can
  never be Archive (`AlbumMode.canBeDefault`). That closes the loop of archive, restore, archive again,
  and it is the safe direction: a refilled folder is no longer covered by a choice made about the files
  that used to be in it. It fires only right after an Archive run has removed files, never from a plain
  rescan, because a partial scan would otherwise read as "every Archive album is empty". The folder
  stays on disk, empty; the ledger rows and the OneDrive copies are untouched.

  The standing-instruction property above therefore holds **while the album still holds files**. Treat
  a change that lets files enter an Archive album by some new route while it holds files as touching
  this rule. A file the user has ticked *Keep at full size* (`FilePin`, Restore's flag) is never
  offered for archiving, whatever its album's mode.

  **The user can also opt any file out of Archive, on the Archive tab. Ian, 19 Sept 2026.** Swiping a
  file left greys it and pins it (the same `FilePin`, so no new column); swiping it right clears the pin.
  The tab lists every file in an Archive album, archivable or not, so a file added later can be opted
  out before anything is archived. The pin can only make the app do less, so this adds no removal, and
  the invariant to protect is that **an opted-out file is never in the plan the check and the removal
  act on**: `BackupEngine.filesInArchiveAlbums()` never returns a pinned file, opted-out files live in
  `ArchiveUiState.optedOut` and never in `ArchivePlan`, and `nextRemovalRequest` drops any that were
  opted out after the check. An album that still holds an opted-out file is not empty, so it keeps its
  mode.

  Android shows its own dialog for a trash request, per batch, capped at 2000 URIs. That is the
  platform's and not ours: it is not where the consent comes from, and it is not to be mirrored by
  an app-level prompt.

  Do not restate this rule as "uploading must never remove anything". That was the mechanism of the
  original failure, not the rule, and scoping it to the upload path leaves every other trigger out.

- A deletion **always** moves the item to a trash the user can recover from: OneDrive's
  recycle bin remotely, and Android's media trash locally.
- **Emptying trash is never done by this app.** The user empties it themselves, in OneDrive
  or in their gallery app. GallerySync must never call an empty-trash or permanent-delete
  API, and must never offer a control that does.
- **`DocumentsContract.deleteDocument` is a permanent delete and is forbidden**, along with any
  other SAF removal. Observed on the Fold 4, 19 Aug 2026: a file removed through a persisted SAF
  tree grant left **nothing in Samsung Gallery's Recycle Bin**. Ian checked; it is not an inference
  from the API name.
  It is tempting precisely because it needs no consent dialog, which makes it the shortest path to
  unattended archiving. Take the tap instead: local removal uses `MediaStore.createTrashRequest`.
  The difference is that the trash request reaches a trash where `deleteDocument` never can — see
  the entry below, confirmed twice on this device.
  **The SAF tree grant is still the right route for proxying**, which shortens a file and removes
  nothing. The prohibition is on deleting through it, not on using it.
- Locally this means `MediaStore.createTrashRequest()` (API 30+), never a plain delete.
  Below API 30 Android has no media trash, so local deletion is **not offered at all** on
  those versions rather than silently deleting permanently.
- **A trash request reaches Android's media trash.** Confirmed twice on a Galaxy Z Fold 4 —
  25 Aug 2026 (one file, 461 MB) and 27 Aug 2026 (51 files, album `Anne`). The file is renamed in
  place to `.trashed-<expiry>-<name>`, keeps its bytes, expires after 30 days, and **is visible in
  Samsung Gallery's Recycle Bin**. Ian checked the Recycle Bin on both occasions.

  **The earlier "removed outright" claim is withdrawn.** It appeared only here and never in
  MILESTONES, and was almost certainly a conflation with `DocumentsContract.deleteDocument` — a
  permanent SAF delete, which genuinely does leave nothing in the Recycle Bin and remains forbidden
  above.

  The outcome cannot be known *before* the request, but it is trivially checkable *after*: the
  rename sits on disk in the same folder. So the app may state what actually happened rather than
  warning about the worst case unconditionally. Note that `is_trashed` queries over adb return
  nothing, because trashed rows are owner-scoped — the disk is the reliable check.

  **A trashed file still occupies its bytes.** Measured 27 Aug 2026: `du` reported 18.6 MB in
  `DCIM/Anne` before 51 files were trashed and 18.6 MB after. Space returns when the user empties
  the Recycle Bin or the 30 days elapse, never on the tap. Any UI saying a removal "frees up X" is
  describing what happens after the bin is emptied, and must say so.

  **Confirmed on a second vendor, 28 Aug 2026.** Moto G 2026, stock Android 16: eight videos archived,
  all eight renamed in place, byte sizes unchanged, `du` still reporting 1.0G, expiry 28 Sept —
  31 days. Ian found all eight in the **Files** app's Trash. Two vendors and two skins give the
  identical answer, so this is the platform's behaviour rather than Samsung's.

  **The trash has a different name on each vendor** — Samsung Gallery's *Recycle Bin*, the Files
  app's *Trash*. UI copy naming one of them is wrong on the other.

  Two devices is still not every device, and the guarantee that always holds is the verified cloud
  copy — remote confirmation plus a matching byte size — which remains what the UI may promise
  unconditionally.
- Remotely this means Graph `DELETE /me/drive/items/{id}`, which moves to the recycle bin.
  Never anything that bypasses it.
- Removing a row from the local ledger or index is bookkeeping and is not a deletion — but
  it must never cause a file to be removed anywhere.
- Deleting a photo from the phone does not delete its backup unless the user explicitly
  confirms that specific action.

  **How that consent is taken, built 19 Sept 2026 (Ian).** Only under the Settings choice *Ask*, and
  only in the window that opens with the app (`ui/deleted/DeletedFilesGate`), never in Settings and
  never in the wizard. It lists files that have left the phone and are undecided, **nothing ticked to
  start with**; the user ticks the files whose OneDrive copy should go and then confirms in a
  dialog naming the count and size, and only then does `SyncDeletionsToCloud.delete` move them to the
  OneDrive recycle bin (never emptied by this app). **Showing the window is not consent**, and back,
  *Decide later* and dismissing the dialog all remove nothing. Unticked files are left alone for good
  (`CloudCopyDecision.KEPT`) until they come back to the phone and are deleted again. Files Archive
  removed on purpose (`CloudCopyDecision.ARCHIVED`, written when an Archive removal completes) are
  never offered, because their OneDrive copy is the one the user asked to keep. A photo edited in place
  (its name is still in its folder) is not a deletion, and a scan that loses more than half the library
  at once is treated as a bad scan (`MassAbsence`) and offers nothing.

  **It covers every deleted file, not only the ones this app backed up, and asks two questions. Ian,
  19 Sept 2026, later the same day.** Files with a copy in OneDrive, *whether this app put it there or
  not* (found by name and size in the album's own OneDrive folder, `BackupEngine.cloudCopiesOf`), are the
  first window: keep the copy or delete it, and deleting is still the only removal and still needs the
  tick and the confirmation dialog above. Files with **no** copy are the second window, and they are in
  the phone's trash: **Remain in Trash** or **Back up to Cloud** (`BackupEngine.backUpFromTrash`, which
  reads the trashed file through its MediaStore URI, measured readable on the Moto G). Backing up **adds a
  copy and removes nothing anywhere**, and leaves the file in the trash, so it needs the tick and no
  dialog. Nothing is ticked to start with in either, and an unticked file gets the passive answer.

  A file the app never sent is kept as a row in `unsent_departures`, written **before** the ledger forgets
  its pending row (that forgetting stands: a kept pending row makes the upload queue chase a file it
  cannot open). The window is Ask-only as a whole, backing up included. **A file that could not be placed
  because OneDrive could not be asked is left out and the window is not counted as seen**: failing to ask
  is not evidence of absence. The app is held on a checking screen until the first look is done, so the
  window really does come before the Albums tab, with a *Skip for now* that decides nothing.

### UI must be readable in dark mode
Learned the hard way on the Teleprompter app, where dark-mode users could not read the
text at all. That is a shipped-to-users bug, not a cosmetic one.

- **Never hardcode a colour in UI code.** No `Color(0xFF…)`, no `Color.Black`, no
  `Color.White` outside `ui/theme/`. Take colours from `MaterialTheme.colorScheme`, and let
  text inherit `LocalContentColor` rather than setting it.
- A colour that reads correctly on a white background is exactly the kind that vanishes on
  a dark one. If a composable needs to set a colour explicitly, it needs a theme token.
- Check both themes on a device before calling UI work done:
  `adb shell "cmd uimode night yes"` and `… night no`. Compiling proves nothing here.
- The same applies to anything drawn rather than composed — icons, custom canvas, overlays.

### The wizard and the Settings tab are independent — in both directions

## **SETTINGS HAS NO EFFECT ON THE WIZARD**

## **THE WIZARD HAS NO EFFECT ON THE SETTINGS**

Stated by Ian, 7 Sept 2026, after a report treated the wizard's cards as though they were supposed to
mirror the Settings tab and flagged the differences as defects.

Two separate surfaces. The wizard collects its own answers and is not a view onto Settings; Settings
is not a record of what the wizard was told. Neither reads the other and neither writes the other.

Not a bug, in the Settings → wizard direction:

- **"Choose folders to back up" opening with nothing checked**, even when Settings lists a folder
  under *Folders to back up* and the SAF tree grant is already held.
- **A wizard card naming a different backup destination from the one the Settings tab shows.**
- **A wizard toggle sitting differently from its Settings counterpart.**

Not a bug, in the wizard → Settings direction:

- **Settings unchanged after the wizard has been answered.** The wizard's four modes do not write the
  Settings optimise tree — the rule immediately below has always said so, and this is the same fact
  stated from the other side.
- **A wizard answer that leaves no trace in Settings at all.**

Do not file any of it, do not fix it, and do not report it as an inconsistency to reconcile. If an
explanation you are building requires one of these surfaces to write the other, the explanation is
wrong — discard it rather than checking it.


**One deliberate exception, Ian, 24 Sept 2026: which cloud each top-level folder goes to.** The wizard
asks it (step 5, only when a second cloud is actually connected) and writes the `folder_preferences`
table, the same table Settings lists. That is not the wizard writing Settings: the table is the
routing itself, not a Settings value, and the wizard still reads none of it. It writes no album modes
and no optimise settings, and none of the rules above changed. See TASK-026.

**Settings → *Run setup again* is a testing affordance and will not ship** (Ian, 7 Sept 2026). It is
there so the wizard can be re-entered without wiping app data while the wizard is still being
iterated on, and *"once we finally get the Wizard set that setting will go away"*. Do not build on it,
test against it, or correct its copy, and never let user-facing behaviour depend on the wizard being
re-enterable.

### Album modes are set only by the user — nothing else writes them
Ian has corrected this more than once, most recently 4 Sept 2026. It is here, in the file that always
loads, because a memory file recording the same rule did not stop it recurring.

Three areas, fully independent:

1. **The install wizard's four modes.** One-time, at install. Acts only on files already on the
   device. Does **not** set album modes and does **not** write the Settings optimise tree.
2. **The ongoing optimise settings.** Global, not per-album. Applies only to Sync albums — Backup and
   Archive never optimise. Does **not** set album modes. **When it runs (Ian, 19 Sept 2026):**
   *Automatic* is as soon as a file reaches a Sync album (after OneDrive has confirmed it) or an album is
   switched to Sync; *Manual* is through **Sync now** on the Albums tab, which is enabled for it even
   with nothing to send. There is no optimise button or status line in Settings.
3. **Album modes** (Off / Backup / Sync / Archive). Set **only** by the user, per album.

**The Camera album has no Sync, and has its own manual optimise. Ian, 20 Sept 2026.** *"I don't want a user
to take a picture/video and then BAM it's optimized already."* The Camera album (`CameraAlbum`, by name) offers
Off, Backup and Archive; Sync is not on its menu, `setAlbumMode` refuses it, and seeding a new Camera album
or Select all writes Backup where every other album would get Sync. **An album already at Sync is left alone**,
because rewriting that would be the app setting a mode. In its place, the album's file list has *Only list Photos/Videos
older than* 1 day / 1 week / 1 month / 6 months / 1 year / All (`CameraOptimisePlan`): a **one-shot, manual
optimise of one folder, not a mode and not a standing rule**. Nothing is remembered but the files the user swiped
out (`FilePin`, so no new column), nothing runs until the button is pressed, and it writes no album mode, so it is
none of the three areas above and touches none of them. It obeys the Settings switches, measures age from the
file's modified time, and only ever takes a file OneDrive has confirmed at its full size. **The invariant to
protect: a swiped-out (pinned) file is never in `CameraOptimisePlan.eligible`**, which is the only list the worker
acts on; the cutoff is fixed when the button is pressed so the files done are the files shown.

**One exception, made by Ian, 16 Sept 2026 — a folder/album conflict sets the mode to `Off`.** When the
app finds one folder under two album identities (TASK-023: MediaStore keeps each writer's spelling, so
`camera` and `Camera` read as two albums over one directory) and they are merged, the merged album's mode
is set to `Off` and the user is warned that a discrepancy was found and that the mode changed. This is the
only thing other than the user that may write a mode, and it may only ever write `Off`, which cannot
remove a file. It does not open the door to anything else setting modes, and the wizard still sets none.

**Albums reading `Off` after an initial backup is correct**, not a bug — the first backup runs with
`allAlbums = true` and deliberately ignores modes. Do not "fix" it.

**The test that saves the most time: any explanation requiring the install choice to write album
modes is wrong by construction.** If a theory needs two of these areas to touch, discard the theory
rather than checking it. That shape has been the error every time.

### Do not add a foreground service
Tried and removed the same evening, 4 Sept 2026. **Do not propose it again without new evidence that
defeats the measurement below.** It is an obvious-looking answer to "the backup stops when the user
leaves", which is exactly why it needs to be closed off in writing.

What was built: `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_DATA_SYNC`, a `dataSync` type on
WorkManager's `SystemForegroundService`, and `setForeground()` from `BackupWorker` and
`OptimiseWorker`, scoped to the wizard's first backup.

Why it was removed — measured on the Moto G, not reasoned:

- **It covers one batch.** The first run gets it, because the app is visible and `uidState: TOP`
  makes the start legal. Both workers then re-enqueue a continuation, and those start in the
  background, where Android refuses:
  `ForegroundServiceStartNotAllowedException: ... due to mAllowStartForeground false`, logged
  21:19:42. A first backup is dozens of batches, so it is unprotected for nearly all of it.
- **A swipe with the service down kills the run exactly as before.** Predicted, then confirmed at
  21:56:58: `Killing 20310 (setSvc -10000): remove task`, process gone, optimising frozen mid-pass,
  no restart in the three minutes after.
- **The cost is real.** Two manifest permissions and a Play Console foreground-service declaration
  on a first submission, in exchange for a few percent of the window.
- **Android 15 caps `dataSync` at six hours per twenty-four**, so it can never be the answer for
  continuous sync. Ian: *"This app needs to be running 24/7 - not 6 hours out of 24."*

**What actually works is `finish()`** — see the Close handler in `SetupTour`. It keeps the process,
and the WorkManager chain carries on: 151 files in five minutes with the app closed, twice measured.
Ongoing sync needs no service at all; the content trigger and the periodic net are uncapped
JobScheduler work.

The unsolved case is a **swipe out of Recents**, which kills the process and stops the app's jobs
being dispatched until it is next opened — a delayed start armed before a swipe never fires. A
foreground service does not fix that either, because a pending delay has no run in flight to hold up.
If that case must be solved, the routes worth investigating are `setExpedited` on the continuations
(quota-limited) or a battery-optimisation exemption — not this.

### Other hard rules
- All file operations on device are cache management, never source-of-truth writes
- Never store OAuth tokens in SharedPreferences — use EncryptedSharedPreferences
- Minimum Android SDK: 26 (Android 8.0). Target SDK: 37 (Android 17)
  Was 35 here while the build file said 37. 35 is the stale one: from 31 Aug 2026 Google Play
  requires **new apps to target API 36 or higher**, and GallerySync will be a new submission. See
  the targetSdk section in `.claude/MILESTONES.md` for what 37 pulls in.
- Kotlin only — no Java files
- No Log.d/Log.e in production code — use the Logger utility (app/src/main/.../util/Logger.kt)
- Coroutines for all async work — no callbacks, no RxJava
- All network calls go through the repository layer — never call APIs from ViewModels directly
- Unit tests live in app/src/test/, instrumented tests in app/src/androidTest/

## Hardware — the devices this is tested on

**Always pin adb with `-s <serial>`.** Two phones are usually attached, and on 28 Aug 2026 an
unpinned command read one device's MediaStore and attributed it to the other — which produced a
confident, wrong report that Ian's library was at risk and cost about an hour.

| Device | Serial | What it is |
|---|---|---|
| ~~Galaxy Z Fold 4~~ | ~~`RFCT71H7RSW`~~ | **GONE — shipped out 30 Aug 2026, never available again.** Do not propose testing on it. Left in the table because its serial appears throughout MILESTONES and those observations are still valid; what is no longer valid is treating it as a rig. See below for what went with it. |
| Moto G 2026 | `ZT422CTZQV` | Stock Android, **Google Photos rather than Samsung Gallery** — so it is where non-Samsung behaviour gets checked. Destination root `MotoG/Gallery`. |
| Galaxy Z Fold 8 | — | **Ian's real phone.** Never experiment on it. |

**The Moto G's OneDrive account is a test account. So is everything on the phone.**

Stated by Ian, 7 Sept 2026. **The account the Moto G uploads to exists only for testing, and every
photo and video on that device was put there to be test data.** Nothing in either place is real, and
nothing in either place needs protecting.

So a test that uploads is cheap. Propose one freely, run large backups, fill the account — there is
no cleanup cost to weigh and no need to ask permission before writing to that account. The care
belongs to the **Galaxy Z Fold 8**, Ian's real phone, which is never experimented on.

**This corrects what this file said until 7 Sept 2026** — that the only disposable account left with
the Fold 4 and that Moto G uploads therefore cost Ian cleanup. That was wrong, and it made every
upload test read as expensive when none of them are. Do not reinstate it.

**One capability did leave with the Fold 4, and it has no replacement.**

- **The 344dp cover screen.** It was the narrowest surface this app runs on and the only place the
  compact layout could be proven on hardware. There is no device left that can. Compact-width work
  is now verifiable only in a Compose preview or an emulator, which is weaker evidence, and any
  claim that a layout works at 344dp must say which of those it rests on.

Wireless debugging assigns a **new port every time it is toggled**, so a remembered address goes
stale; rediscover with `adb mdns services`. An address in `100.64.0.0/10` means the phone is on
mobile data or a VPN and is not reachable from this machine.

## Working practices — learned the hard way

- **Verify on hardware before calling anything done.** Nearly every real defect in this project was
  found by eye on a device, not by reading code — including several that passed review and compiled
  cleanly.
- **After every install, launch the app and run `adb logcat -b crash -d`.** On 28 Aug 2026 a crash
  that killed the app on every launch of the Albums tab shipped to both phones, because "install
  succeeded" plus a screenshot looked like proof it worked. A screenshot of a crashed app looks like
  a screenshot of a launcher.
- **Read the Room database with its `-wal` and `-shm` files together**, or checkpoint first. A
  single-file copy is not a snapshot: recent writes live in the WAL, and reading without it once
  produced a defect report for a bug that did not exist.
- **Prefer a screen recording to screenshots for anything that changes over time**, and diff the
  frames. On 28 Aug a recording diffed with ffmpeg located every change to one label in seconds and
  revealed two interleaving values that stills had made look frozen.
- **When an instrument disagrees with the evidence, doubt the instrument.** `content query` returned
  three different row counts for one table in a single session; `ls` and `du` were stable throughout.
- **Update `.claude/MILESTONES.md` before staging**, per `.claude/agents/backup-agent.md`. It is the
  project's memory, and a correction that lands in one file and not the other is how a withdrawn
  claim comes back weeks later and gets acted on.

## Escalate to Ian — Lead Agent only, when:
- OAuth app registration is needed (Google Cloud Console or Azure app registration)
- A new Android permission is required that affects the Play Store listing
- A breaking change to the Room database schema requires a migration
- Feature scope has two architecturally distinct paths with long-term implications
- A security issue is found (token storage, data exposure, permission misuse)
- The debug loop has cycled 3+ times without resolving a test failure

## Autonomous — no escalation needed for:
- Adding repository methods, use cases, utility functions
- Writing or updating unit tests
- Bug fixes with clear root cause
- UI layout and composable changes
- Refactoring within a single module or layer

## Architecture — Clean Architecture layers
ui/          ← Jetpack Compose screens and ViewModels
domain/      ← Use cases and domain models (no Android dependencies)
data/        ← Repositories, Room DAOs, API services, cloud adapters
util/        ← Logger, extensions, helpers
provider/    ← ContentProvider implementation (exposes files to other apps)
worker/      ← WorkManager workers (background sync)

## File Structure
app/src/main/java/com/gallery/sync/
  ui/
  domain/
  data/
  util/
  provider/
  worker/
app/src/test/java/com/gallery/sync/       ← JUnit + Mockito unit tests
app/src/androidTest/java/com/gallery/sync/ ← Espresso instrumented tests
.claude/
  agents/      ← Agent definition files (auto-loaded by Claude Code)
  tasks/       ← Active task specs (TASK-NNN.md) and fix specs (FIX-NNN.md)
  MILESTONES.md
