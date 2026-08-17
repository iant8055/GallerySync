# TASK-002 — Logger utility

Milestone: v0.1.0 Foundation — "Logger utility"
Depends on: TASK-001
Blocks: TASK-003, TASK-004, TASK-005 (every one of them must log through this)

## Goal
The single logging entry point for the whole app. CLAUDE.md hard rule: "No Log.d/Log.e in
production code — use the Logger utility". This is that utility, and it is the ONLY place in
`app/src/main/` allowed to reference `android.util.Log`.

## What to build

### `app/src/main/java/com/gallery/sync/util/Logger.kt`
A Kotlin `object` (not injected — it must be callable from a ContentProvider, which Hilt
cannot field-inject before `onCreate`).

Required API:
```
object Logger {
    fun v(tag: String, message: String)
    fun d(tag: String, message: String)
    fun i(tag: String, message: String)
    fun w(tag: String, message: String, throwable: Throwable? = null)
    fun e(tag: String, message: String, throwable: Throwable? = null)
}
```

Behavior requirements:
- Prefix every tag with `"GallerySync/"` so the app is greppable in logcat, and **truncate the
  final tag to 23 chars** (pre-API-26 limit is gone but long tags are still hostile in logcat).
- `v`/`d` must be **no-ops in release builds**. Gate on `BuildConfig.DEBUG`.
  This requires `buildConfig = true` in the `buildFeatures` block of `app/build.gradle.kts` —
  add it if TASK-001 did not.
- `i`/`w`/`e` always emit.
- No string interpolation cost when a level is disabled — accept the message eagerly is fine
  for now, but do NOT add lambda overloads; keep it simple.

### Testability requirement (this is the part that matters)
`android.util.Log` is an android.jar stub, so the logic must be unit-testable without a device.
Extract the decision logic into internal, pure functions that tests can call directly:

```
internal fun formatTag(tag: String): String      // prefix + 23-char truncation
internal fun isLoggable(level: Level, isDebugBuild: Boolean): Boolean
```

`Logger` delegates to these. Keep them `internal` (not private) so `app/src/test/` can see them.
Do NOT build a full pluggable log-sink abstraction — that is over-engineering for v0.1.0.

## Test requirements — `app/src/test/java/com/gallery/sync/util/LoggerTest.kt`
The Test Agent writes these; Builder must leave the code shaped so they are possible.
- `formatTag` prefixes with `GallerySync/`
- `formatTag` truncates a long tag to exactly 23 chars
- `formatTag` leaves a short tag intact (beyond the prefix)
- `isLoggable(VERBOSE, isDebugBuild = false)` is false
- `isLoggable(DEBUG, isDebugBuild = false)` is false
- `isLoggable(INFO/WARN/ERROR, isDebugBuild = false)` is true
- all levels loggable when `isDebugBuild = true`

## Acceptance criteria
1. `app/src/main/java/com/gallery/sync/util/Logger.kt` exists and compiles.
2. It is the only file under `app/src/main/` that imports `android.util.Log`.
3. `./gradlew assembleDebug testDebugUnitTest` still green.

## Out of scope
- File logging, crash reporting, Timber, remote log upload
- Any DI wiring for Logger (it is an object, deliberately)
- Replacing logging in existing files (there is none)

## Report back
Files created/modified, and confirmation that `buildConfig = true` is set.
