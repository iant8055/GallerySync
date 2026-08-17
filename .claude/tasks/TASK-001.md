# TASK-001 — Build infrastructure & Hilt DI foundation

Milestone: v0.1.0 Foundation — "Android project scaffold (Kotlin, Compose, Hilt, Room, Retrofit)"
Depends on: nothing
Blocks: TASK-002, TASK-003, TASK-004, TASK-005

## Goal
Wire the dependency stack CLAUDE.md mandates (Hilt, Room, Retrofit/OkHttp, coroutines,
EncryptedSharedPreferences, Mockito, coroutines-test) into the version catalog and the
app build file, and stand up the Hilt object graph. No feature code in this task.

## Critical build context — read before editing
- AGP is **9.3.1**, Kotlin **2.2.10**, Gradle **9.5.0**, Compose BOM **2026.02.01**.
- `app/build.gradle.kts` uses the **AGP 9 DSL**: `compileSdk { version = release(37) }` and
  `buildTypes.release.optimization { enable = false }`. **Keep that style.** Do not rewrite it
  back to the AGP 8 `compileSdk = 37` form.
- The build currently applies only `android.application` + `kotlin.compose`. There is no
  explicit `org.jetbrains.kotlin.android` plugin — AGP 9 provides built-in Kotlin support.
  **Do not add `kotlin.android` unless the build fails without it**; if you must, report it.
- `org.gradle.configuration-cache=true` is on. Anything you add must be configuration-cache safe.
- The baseline build is GREEN. `./gradlew assembleDebug testDebugUnitTest` passes today.
  If it goes red, it went red because of your change — fix it, don't leave it.

## SDK level — DO NOT CHANGE
`compileSdk`/`targetSdk` stay at **37**, `minSdk` stays at **26**.
There is a known discrepancy with CLAUDE.md ("Target SDK: 35"); the Lead Agent has escalated
it to Ian. **Do not edit CLAUDE.md and do not change any SDK number in this task.**
(Also relevant: only `android-37.0` is installed in the local SDK; `android-35` is not.)

## What to build

### 1. `gradle/libs.versions.toml`
Add versions/libraries/plugins for the stack below. Suggested starting versions — if a
coordinate fails to resolve or is plugin-incompatible, pick the nearest working stable
version and **report exactly what you changed and why**:

- KSP plugin `com.google.devtools.ksp` — must match Kotlin 2.2.10 (try `2.2.10-2.0.2`)
- Hilt `com.google.dagger:hilt-android` + `hilt-android-compiler`, plugin
  `com.google.dagger.hilt.android` (try `2.57.2`)
- `androidx.hilt:hilt-navigation-compose` (try `1.3.0`)
- Room `androidx.room:room-runtime`, `room-ktx`, `room-compiler` (try `2.8.1`)
- Retrofit `com.squareup.retrofit2:retrofit` (try `3.0.0`)
- `com.squareup.okhttp3:okhttp` + `logging-interceptor` (try `5.2.1`) — use the OkHttp BOM if
  the version you pick publishes one
- kotlinx.serialization: plugin `org.jetbrains.kotlin.plugin.serialization` (version.ref kotlin),
  `org.jetbrains.kotlinx:kotlinx-serialization-json` (try `1.9.0`), and
  `com.squareup.retrofit2:converter-kotlinx-serialization` (try `3.0.0`)
- `org.jetbrains.kotlinx:kotlinx-coroutines-android` + `kotlinx-coroutines-test` (try `1.10.2`)
- `androidx.security:security-crypto` (try `1.1.0`) — for EncryptedSharedPreferences
- `org.mockito:mockito-core` (try `5.20.0`) and `org.mockito.kotlin:mockito-kotlin` (try `6.1.0`)
- `app.cash.turbine:turbine` (try `1.2.1`) — Flow testing
- `androidx.room:room-testing` (try same as room)

**JSON: use kotlinx.serialization, not Gson and not Moshi.** Rationale: Graph API returns
fields like `@odata.nextLink` that need `@SerialName`, and it avoids a second annotation
processor. Apply the serialization plugin in `app/build.gradle.kts`.

### 2. `app/build.gradle.kts`
- Apply plugins: ksp, hilt, kotlin serialization (plus the existing two).
- Add the dependencies above in the right configurations (`implementation`, `ksp`,
  `testImplementation`, `androidTestImplementation`).
- Add to the `android { }` block:
  ```
  testOptions {
      unitTests {
          isReturnDefaultValues = true
      }
  }
  ```
  (Needed so unit tests touching android.jar stubs like `Uri`/`UriMatcher` fail loudly on
  logic errors rather than on "not mocked". Use the AGP 9 spelling if this one is deprecated.)
- Add `packaging { resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" } }` only if a
  duplicate-resource merge error actually occurs.

### 3. `app/src/main/java/com/gallery/sync/GallerySyncApplication.kt`
```
@HiltAndroidApp
class GallerySyncApplication : Application()
```

### 4. `app/src/main/AndroidManifest.xml`
- Add `android:name=".GallerySyncApplication"` to `<application>`.
- Add `<uses-permission android:name="android.permission.INTERNET" />` and
  `<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />`.
  These two are normal (non-dangerous) permissions and do not require a Play Store
  data-safety disclosure on their own, so they are in scope here.
- **Do not add any other permission.** Storage/media permissions affect the Play listing and
  are an escalation to Ian — stop and report instead.

### 5. `MainActivity`
Annotate with `@AndroidEntryPoint`. Change nothing else about it.

### 6. DI skeleton — `app/src/main/java/com/gallery/sync/di/`
Create `AppModule.kt`:
```
@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides @Singleton
    fun provideApplicationContext(@ApplicationContext context: Context): Context = context
}
```
Only what is needed to prove the graph compiles. Room/Retrofit modules come in TASK-003/004
and are explicitly out of scope here.

## Acceptance criteria
1. `./gradlew assembleDebug` succeeds.
2. `./gradlew testDebugUnitTest` succeeds (the existing `ExampleUnitTest` still runs).
3. Hilt's annotation processor actually runs — verify `app/build/generated/` contains
   generated Hilt/Dagger sources. A build that "passes" because Hilt was never applied is a
   FAILED task.
4. Configuration cache still works (no `--no-configuration-cache` needed).
5. No Java files anywhere.

## Out of scope
- Any Room entity, DAO, or database class
- Any Retrofit service interface or DTO
- Any ContentProvider
- WorkManager / Billing (v0.2.0 / v0.3.0 — do not add the dependencies)
- The Logger utility (TASK-002)
- Any UI change beyond the `@AndroidEntryPoint` annotation

## Report back
- Every version you had to change from the suggestions above, and the error that forced it
- Whether `kotlin.android` had to be applied explicitly
- Generated-Hilt-sources proof (a path under `app/build/generated/`)
- Full final output of `assembleDebug testDebugUnitTest`
