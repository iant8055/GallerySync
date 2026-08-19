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
Pro tier:   Google Photos sync (unlocked by IAP)

IAP product ID: pro_unlock
Billing library: com.android.billingclient:billing-ktx (latest stable)

Rules:
- Gate Google Photos features behind a BillingRepository.isPurchased() check
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
  that the files leave the gallery and that files added to that album later are covered by the same
  choice. After that the mode stands until it is changed: no per-file approval and no repeat
  prompting, which would make the mode unbuildable and is not what this rule says.

  **"Safely" is not a judgement call.** Graph confirmed the file *and* the byte size it reported
  equals the local size — `BackupEntryDao.verifiedInCloud()`, the same bar as every other removal.
  Nothing weaker qualifies. This is the check that has to hold, because it is the only guarantee the
  UI is allowed to make.

  What the rule forbids is removal the user did not choose: uploading, backing up, syncing,
  proxying, a storage budget, or any worker deciding on its own that a file should go. Removal
  follows from a mode the user set, and from nothing else. Note the standing-instruction property —
  a file added to an Archive album later is covered by the mode already set, so the mode's wording
  has to make that plain before it is applied.

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
- Locally this means `MediaStore.createTrashRequest()` (API 30+), never a plain delete.
  Below API 30 Android has no media trash, so local deletion is **not offered at all** on
  those versions rather than silently deleting permanently.
- **A trash request is not a guarantee of recoverability.** Observed on a Galaxy Z Fold 4:
  the files were removed outright. Samsung routes the request through Gallery's Recycle Bin,
  and with that setting off the request becomes a delete. The platform gives no way to
  detect this in advance.
  Therefore: **never tell the user a local removal is recoverable.** The guarantee that
  actually holds is the verified cloud copy — remote confirmation plus a matching byte size —
  and that is what the UI may promise. Nothing else.
- Remotely this means Graph `DELETE /me/drive/items/{id}`, which moves to the recycle bin.
  Never anything that bypasses it.
- Removing a row from the local ledger or index is bookkeeping and is not a deletion — but
  it must never cause a file to be removed anywhere.
- Deleting a photo from the phone does not delete its backup unless the user explicitly
  confirms that specific action.

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
