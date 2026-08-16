# GallerySync — Agent Rules

## Project
Android app that makes cloud-hosted photos and videos (OneDrive, Google Photos)
accessible to any third-party app (e.g. CapCut) without requiring local storage.
Core mechanism: Android ContentProvider + on-demand download from cloud APIs.

Built for Samsung Galaxy primarily; tested on LG and Moto as well.

Stack: Kotlin, Jetpack Compose, Room (SQLite), Hilt (DI), WorkManager (background sync),
Retrofit + OkHttp (cloud APIs), JUnit + Mockito (unit tests), Espresso (UI tests).

## Hard Rules — all agents must follow
- Never permanently delete a user's cloud file — local cache only
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
app/src/main/java/com/iant/gallerysync/
  ui/
  domain/
  data/
  util/
  provider/
  worker/
app/src/test/java/com/iant/gallerysync/       ← JUnit + Mockito unit tests
app/src/androidTest/java/com/iant/gallerysync/ ← Espresso instrumented tests
.claude/
  agents/      ← Agent definition files (auto-loaded by Claude Code)
  tasks/       ← Active task specs (TASK-NNN.md) and fix specs (FIX-NNN.md)
  MILESTONES.md
