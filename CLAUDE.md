# GallerySync — Agent Rules

## Project
Android app that makes cloud-hosted photos and videos (OneDrive, Google Photos)
accessible to any third-party app (e.g. CapCut) without requiring local storage.
Core mechanism: Android ContentProvider + on-demand download from cloud APIs.

Built for Samsung Galaxy primarily; tested on LG and Moto as well.

Stack: Kotlin, Jetpack Compose, Room (SQLite), Hilt (DI), WorkManager (background sync),
Retrofit + OkHttp (cloud APIs), JUnit + Mockito (unit tests), Espresso (UI tests).

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

- A deletion **always** moves the item to a trash the user can recover from: OneDrive's
  recycle bin remotely, and Android's media trash locally.
- **Emptying trash is never done by this app.** The user empties it themselves, in OneDrive
  or in their gallery app. GallerySync must never call an empty-trash or permanent-delete
  API, and must never offer a control that does.
- Locally this means `MediaStore.createTrashRequest()` (API 30+), never a plain delete.
  Below API 30 Android has no media trash, so local deletion is **not offered at all** on
  those versions rather than silently deleting permanently.
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
- Minimum Android SDK: 26 (Android 8.0). Target SDK: 35
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
