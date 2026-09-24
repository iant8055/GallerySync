# TASK-026 — Multi-cloud: Google Photos as a second destination, behind a Pro unlock

Milestone: v0.5.0 (Google Photos + Billing), pulled forward at Ian's request, 22 Sept 2026
Raised by: Ian, 22 Sept 2026 — competitive research on multi-cloud support, market pricing research, then
"lets plan out for the multi-platform Pro add on option... $2.49 price point works"
Status: **IN PROGRESS.** OAuth registered. Play Console app + `pro_unlock` product registered. Room
schema migrated (v11 → v12). AppAuth sign-in flow, Google Photos wire client, `BackupEngine` dispatch,
and `BillingRepository` all built and verified on-device. Destination-picker UI next.

## What was decided, and why, before any code

**Price: $2.49, one-time, unlocks every second destination at once.** Not per-provider, not a subscription.
Anchored to PhotoSync's Cloud Add-On (touchbyte, 1M+ downloads, a decade in the market at that price for
bundling Dropbox/Drive/Google Photos/Flickr/OneDrive/SmugMug/Box/Zenfolio/PhotoPrism together) rather than to
storage-seller pricing (Google One, Microsoft 365, IDrive, G Cloud — $2–27/mo). GallerySync doesn't sell
storage, the user's own cloud account does; the IAP is for the capability of a second destination, which is
what PhotoSync's number actually prices. `pro_unlock`, per CLAUDE.md's monetization section, unchanged.

**The feature set for Google Photos is deliberately smaller than OneDrive's, and that is correct, not a
shortfall.** Confirmed two ways before building anything:

- **The API itself.** As of 1 April 2025, the Photos Library API only exposes content the calling app
  created (`photoslibrary.readonly.appcreateddata` scope) — never the user's pre-existing library, never what
  Google's own auto-backup put there. There has never been a `mediaItems.delete` endpoint, at any point in the
  API's history.
- **The market.** MetaCtrl builds a dedicated "Autosync for X" app for Drive, OneDrive, Dropbox, Box, MEGA — and
  has never built one for Google Photos, despite Drive (a generic file API, no such restriction) getting one.
  Their own listing draws the line in words: *"automatic photo upload... not... keeping photos on multiple
  devices synchronized."* PhotoSync, the one major app that does support Google Photos, treats it as a manual,
  user-triggered import/export target — never a reconciling background sync, for any provider. Nobody who has
  been doing this for years has built OneDrive-shaped sync against Google Photos, because the API doesn't
  support it.

**Consequence for GallerySync's Google Photos destination:**
- Upload — yes.
- Dedup against what *GallerySync itself* already sent — yes, and it survives a reinstall: `mediaItems.list`
  under `appcreateddata` is scoped to the account and the app (the OAuth client), not the device, so a fresh
  install can re-list what it created before and rebuild the ledger rather than re-upload everything. Confirm
  this empirically once the auth flow exists — the docs imply it but don't spell out the reinstall case.
- Skip-existing reconciliation against a library Google's own backup already put there — **no**. Cannot see it,
  full stop. A user with both running will get a second copy from GallerySync. Say so in the UI before they
  turn it on, the same honesty-first pattern the rest of Settings already uses (see e.g. `sources_remove_confirm_body`).
- Deletion sync (the OneDrive-copy half of "when you delete a photo/video from this phone") — **no**. No delete
  endpoint exists for any app. Google Photos backups are simply never offered in that window; Leave/Ask stays
  OneDrive-only.
- Archive (verify cloud copy, remove local) — **yes**, for files GallerySync itself uploaded, since
  `appcreateddata` covers reading them back to verify. Same 100%-size-match bar as OneDrive, nothing weaker.

## OAuth (Google Cloud Console) — done

Project `gallerysync-509502`. Photos Library API enabled (Picker and Ambient also enabled, harmless, unused).
Data Access scopes added: `photoslibrary.readonly.appcreateddata` (non-sensitive), `photoslibrary.appendonly`
(sensitive — neither landed in the *restricted* tier, better than expected; restricted would need the CASA
security assessment before a public launch, sensitive only needs Google's lighter standard verification).
Ian added as a test user under Audience, so nothing here needs Google's review to test against his own account.

Two OAuth clients exist:
- **Android** (`com.gallery.sync`, debug SHA-1 `13:B6:47:86:44:4D:19:C6:EA:E5:3F:A0:50:D0:93:92:BA:18:60:CA`):
  `400850912934-uspc27q5168r202m50gr0813pfcp89u1.apps.googleusercontent.com`. This is the one actually used —
  a public client, no secret, checked into `res/raw/google_photos_config.json` the same way `msal_config.json`
  already is.
- **Web application** (`400850912934-sr0tdnib6qjpiku8gipl8t20g801ifo7...`): created for `requestOfflineAccess`,
  turned out not to be needed. Google's own "OAuth 2.0 for native apps" flow (RFC 8252) issues a refresh token
  straight to a public installed client via PKCE — no backend, no secret, same shape as OneDrive's MSAL flow.
  Left registered, unused; costs nothing to leave alone, same reasoning as the two unused Photos APIs.

**A real near-miss, worth recording so it isn't repeated.** Google Cloud Console's own download for the Web
client is named `client_secret_<id>.json` and genuinely contains a client secret (`GOCSPX-...`) — unlike the
Android client's same-named download, which has none. Ian downloaded both straight into the repo root twice.
Neither was ever staged or committed — caught and deleted both times before `git add` — but `client_secret_*.json`
is now in `.gitignore` as a standing guard, since trusting "catch it every time" isn't a plan.

## Two more decisions, both Ian's, both settled before more code

**Archive stays OneDrive-only. Google Photos is Backup only, for now.** Found while designing the
verification step: unlike Graph, the Photos Library API returns **no file size anywhere** — not on
upload, not in `mediaItems.list`, not in `mediaMetadata`. CLAUDE.md's "safely" bar is absolute — a
verified byte-size match, nothing weaker — and Google's API simply has nothing to match against. A
workaround exists (a `HEAD` request against the upload response's `baseUrl`, valid for 60 minutes,
read `Content-Length` once, right after upload) but it was not built — Ian: *"Backup only for now."*
Revisit if Archive-for-Google-Photos is ever wanted; until then, Google-Photos-routed albums simply
should not offer Archive or Sync (optimise) as modes — see below for why that needs no special-casing.

**One destination per album, chosen by the user — not mirrored to every active location.** Ian, from
what he's seen in other apps: *"Internal Storage/DCIM → OneDrive/.../DCIM, Internal Storage/Pictures
→ Google Photos/Main, Internal Storage/Documents → IDrive/..."* — a folder-to-provider mapping, one
provider per folder. This is what `BackupEngine`'s dispatch is actually for: per album, decide which
one repository pair to call, not fan a file out to several. Confirms the original TASK-026 shape
("a thin dispatch point... decided by which `BackupLocation` an album's uploads are routed to") over
the mirror-to-everywhere alternative that was floated and rejected.

**What the Settings `BackupLocation` checkboxes ("OneDrive ✓", "at least one must stay on") mean under
this model:** which providers are signed in and available to choose from at all — not "send everything
everywhere." "At least one must stay on" still holds: switching off a signed-in provider that some
album is still routed to would leave that album with nowhere to go.

**The Archive/Sync restriction needs no new code.** Both already gate on `remoteSizeBytes ==
sizeBytes` (`verifiedInCloud`, `CameraOptimisePlan.isReady`, the general proxy-candidate queries).
Google Photos rows simply never populate `remoteSizeBytes` — Backup-only falls out of the *existing*,
already-tested eligibility checks for free. What does need building: the Albums UI must not *offer*
Archive or Sync on a Google-Photos-routed album in the first place (TASK-014's rule — never offer an
action that cannot succeed), the same way Camera's own mode menu already refuses Sync. Not yet built.

## What is being built

A **new, parallel** repository pair for Google Photos — `GooglePhotosRepository` / `GooglePhotosUploadRepository`
— shaped for what the API actually offers (list-what-we-created, upload, no folders, no delete), rather than
forcing OneDrive's folder-tree-shaped interfaces onto an API that has no folders. `OneDriveRepository` and
friends are untouched: they work, they're hardware-verified, and Google Photos' shape is different enough that
a single forced-common interface would mean stub methods throwing on one side or the other.

`BackupEngine` gains a thin dispatch point — which concrete repository pair to call is decided by which
`BackupLocation` an album's uploads are routed to — rather than a deep abstraction both providers are bent to
fit. `MediaSource`/`BackupLocation` already have the enum values for this (`GOOGLE_PHOTOS`), unused until now.

## Room schema — done (v11 → v12)

One destination per album, chosen by the user — see above. Two columns, both additive, both
`TEXT NOT NULL DEFAULT 'ONEDRIVE'`, following this project's established migration shape (name-based
converter with safe fallback, no `@ColumnInfo(defaultValue=...)` annotation, matching every prior
migration in `Migrations.kt`):

- `album_preferences.backupLocation` — where an album's uploads go from now on.
- `backup_entries.location` — where that specific row's upload actually targeted, set once at row
  creation from the album's setting at the time, never re-read afterwards. A row already `UPLOADED`
  keeps recording where the file genuinely went even if the album's destination changes later.

`'ONEDRIVE'` is not a placeholder default — every album and every ledger row that exists before this
column does genuinely target OneDrive, since nothing else has ever been usable.

`BackupLocationConverter` mirrors `AlbumModeConverter` (name-based, falls back to `BackupLocation.DEFAULT`
on a corrupt value). `GallerySyncDatabase` bumped to version 12, converter registered, schema exported
(`app/schemas/.../12.json`). `MigrationTest.kt` gained `migrate11To12_producesTheSchemaRoomExpects` and
`migrate11To12_defaultsExistingAlbumsAndRowsToOneDrive` — both run and passed on the Moto G
(`ZT422CTZQV`, connected wirelessly), 23 Sept 2026.

One incidental fix needed to get any instrumented build compiling again: `net.openid:appauth`'s own
manifest declares its redirect-receiver activity with an `${appAuthRedirectScheme}` placeholder that
nothing had supplied, which broke `processDebugMainManifest` for every build touching the manifest
(compileDebugKotlin alone doesn't merge manifests, so this had gone unnoticed). Added
`manifestPlaceholders["appAuthRedirectScheme"] = "com.gallery.sync"` to `app/build.gradle.kts`,
matching `google_photos_config.json`'s `redirect_uri` scheme. Unrelated to the schema work itself, but
blocking it, so fixed in the same pass.

## Sign-in — done

`GooglePhotosSignIn` / `GooglePhotosTokenProvider`, mirroring `OneDriveSignIn` / `OneDriveTokenProvider`'s
split and the reason for it (interactive sign-in needs a foreground `Activity`, silent token acquisition
doesn't, and the split is what makes the sign-in UI fake-able in tests).

Backed by `net.openid:appauth` — RFC 8252 native-app flow, PKCE, no client secret, no backend server —
rather than Credential Manager's `requestOfflineAccess`, which assumes a real backend to hold the offline
token. One extra piece of plumbing OneDrive's MSAL flow didn't need: AppAuth's browser redirect returns
through its own `RedirectUriReceiverActivity`, not through the calling screen's `onActivityResult`, so
completion is delivered via `PendingIntent`s to a small internal-only `GoogleAuthResultActivity`
(`exported="false"`, no intent-filter, never reachable except by our own `PendingIntent`s), which exchanges
the code for tokens and hands the outcome back through `GoogleSignInResultBridge`, a `CompletableDeferred`
coordinator.

Tokens live in their own `EncryptedSharedPreferences` file (`EncryptedGoogleAuthStore`), separate from
OneDrive's — MSAL keeps its own cache and never touches that store, so there's nothing to share, and
signing out of Google alone can't touch OneDrive's tokens.

`access_type=offline` + `prompt=consent` on the authorization request — otherwise Google only issues a
refresh token on the *first* consent for a client+account pair, and this would only have shown up on a
reinstall or a revoked-and-reconnected account, not on first use.

Verified: full app + test compile, unit tests pass (`GoogleSignInResultBridgeTest` — the one piece here
with no Android dependency), installs and launches clean on the Moto G, no crash-log entries. **Not yet
exercised end-to-end** — there is no UI entry point to trigger it yet, since the Settings destination
picker hasn't been built. Worth an actual sign-in round-trip against Ian's real Google account once that
picker exists.

## Wire client — done

`GooglePhotosRepositoryImpl` / `GooglePhotosUploadRepositoryImpl`, mirroring the OneDrive
repositories' shape: the network boundary lives here, no Retrofit or OkHttp type escapes the data
layer, every failure maps to a typed `RemoteError`, a 401 invalidates the stored token the same way
Graph's interceptor does. Own `OkHttpClient`/`Retrofit` in `NetworkModule` (own base URL, own
`GooglePhotosAuthInterceptor`, same debug-only body logging with `Authorization` redacted).

Upload is the two-call shape the interface already documented: raw bytes to `/v1/uploads` — streamed
in 1 MiB chunks via a real `RequestBody` rather than loaded whole into memory, since Google's
raw-upload endpoint has no small-file ceiling the way Graph's does and this project has already hit
an OOM once loading a whole response body (see CLAUDE.md) — then the returned token into
`mediaItems:batchCreate`. No chunked/resumable variant: an interruption between the two calls just
strands an unused token that expires server-side, nothing ever becomes visible in the library, so v1
simply retries the whole thing from the top rather than resuming.

Caught one real bug before it shipped: the first draft of the upload-token step used a mutable field
on the (singleton) repository to smuggle a failure result out of a helper method — a data race under
any two concurrent uploads. Replaced with a small sealed result type instead of shipping it.

Verified: full app + test compile, unit tests pass — including new coverage for the pure mapper and
the streaming request body (a real temp file in, a real okio `Buffer` catching what's written,
sized to force the multi-chunk loop rather than only the single-chunk case) — installs and launches
clean on the Moto G. **Not yet exercised against the real Photos Library API** — that needs the
sign-in UI, which doesn't exist yet, and `BackupEngine` actually calling any of this.

## BackupEngine dispatch — done

Read the whole of `uploadPendingWhileHolding` (`BackupEngine.kt`, the loop `BackupWorker` actually
runs) before touching it, since it's the one place CLAUDE.md's absolute deletion-safety bar and the
Camera-album "no instant optimise" rule both ultimately rest on. That reading surfaced two things
worth having settled before writing the dispatch itself, both now resolved.

**A real near-miss, caught in the wire client before it ever reached this file:** the naive dispatch —
"call `googlePhotosUploadRepository.upload` instead of OneDrive's, same downstream handling" —
would have been a genuine CLAUDE.md violation. `UploadedItem.sizeBytes` for Google Photos is always
the *local* size (Google never reports one), so recording a success through the existing
`entryDao.markUploaded(remoteSizeBytes = item.sizeBytes)` would have made the row's `remoteSizeBytes`
always equal `sizeBytes` — and `BackupEntryDao.verifiedInCloud()` (`remoteSizeBytes IS NOT NULL AND
remoteSizeBytes = sizeBytes`) would read every Google Photos row as byte-verified, forever, having
confirmed nothing. Restricting Archive/Sync to OneDrive in the UI does not stop that row from
satisfying `verifiedInCloud()` for any *other* code that trusts it — `CameraOptimisePlan`, the
deleted-files "kept vs gone" reconciliation, more. Fixed structurally: `BackupEntryDao
.markUploadedWithoutSizeVerification`, whose SQL always writes `remoteSizeBytes = NULL`, so a Google
Photos row can never satisfy `verifiedInCloud()` no matter what dispatch code does or later gets
changed to.

**The stop-reason question, settled by Ian, 23 Sept 2026:** *"agreed a failed Google run (or iDrive or
OneDrive) should never stop another backup."* `uploadPendingWhileHolding` now splits its batch by
each row's `location` into two independent loops. OneDrive's loop is byte-for-byte unchanged apart
from the exit mechanism — a stop used to `return@withContext` the whole function immediately; it now
just ends that loop with `break`, so the Google Photos loop that follows still runs in the same pass.
Only OneDrive's own stop reason is ever written into the `BackupRunResult` that `BackupWorker` maps to
a `Result`; a Google Photos-only failure (an expired token, its own quota) never produces the terminal
`Result.failure()` that stops the whole worker — it just leaves those rows `PENDING`/`FAILED`, picked
up by the next natural continuation, the same way a deferred OneDrive file already works today. No new
`StopReason` value, no new `BackupWorker` mapping needed for v1.

`refreshLedger` now stamps each new row's `location` from its album's `backupLocation` at creation
time. No remote-index precheck in the Google Photos loop the way OneDrive's has — the equivalent
(reconciling against this app's own items via `mediaItems.list`) isn't built yet, so a lost Google
Photos ledger risks a duplicate upload today, not a data-loss risk. Documented gap, not a blocker.

Verified: full app + test + androidTest compile, all 599 unit tests pass (five existing
`BackupEngine`-constructing tests needed the new constructor parameter; one genuine bug this surfaced
— `albumDao.all()` returning `null` under an unstubbed Mockito mock despite its non-null Kotlin type —
fixed with a defensive `.orEmpty()`, harmless in production since Room never actually returns null for
a `List` query), installs and launches clean on the Moto G, schema migration confirmed live on-device
(`PRAGMA user_version = 12`, `backupLocation` column present). No album can be routed to Google Photos
yet — no UI exists for it — so the new loop is provably a no-op in real-world use today; the OneDrive
path's actual runtime behaviour is unchanged by construction.

## Play Console + Billing — done (23 Sept 2026)

Play Console app entry created for GallerySync (`com.gallery.sync`), same account as Teleprompter.
Release signing set up from scratch — nothing existed before this (only a debug keystore, used for
the OAuth Android client's SHA-1). `gallerysync-release.jks`, Play App Signing, upload key generated
locally with a random password, kept in `keystore.properties` — both gitignored, never committed. A
clone without `keystore.properties` still builds a release variant, just unsigned.

Two uploads to Internal Testing were needed. The first (versionCode 1, no Billing dependency yet) hit
a real wall: Play Console refuses to let you create *any* one-time product until an uploaded build's
manifest declares `com.android.vending.BILLING` — which only appears once `com.android.billingclient
:billing-ktx` is actually a dependency. Added it (9.1.0 — CLAUDE.md says "latest stable"; Google
requires Billing Library 8+ for any new app/update from 31 Aug 2026), rebuilt as versionCode 2,
re-uploaded, confirmed the permission was present in the merged manifest before handing it over.

Along the way: Play restructured one-time products since CLAUDE.md's monetization section was
written — a product no longer has a price directly, it needs a separate **purchase option**
underneath it (Buy/Rent/pre-order, its own price and regional availability), and that purchase
option is what actually needs activating, not just the product. `pro_unlock` — Durable, $2.49, Buy —
is live with an active purchase option. License testing (both `iant8055@gmail.com` and the test
account) and the internal testers list both set.

**`BillingRepository`, done and pushed** (`domain/billing/BillingRepository.kt` /
`data/billing/PlayBillingRepository.kt`, see that commit for the full design notes): backed by Play
Billing directly, no backend server (same reasoning as the rest of this app), `isPurchased()` queries
Play's own cache fresh every call rather than persisting a flag of its own. Compiles, all tests pass,
installs and launches clean on the Moto G. Nothing calls it yet — no UI exists to trigger a purchase —
so it's inert in the running app today, same as the dispatch loop and wire client before it.

One thing worth Ian's attention whenever there's a moment, not blocking: Claude in Chrome (the
browser extension) was used directly against Ian's live, already-authenticated Play Console session
for parts of this — navigating pages, reading state — never entering credentials, never touching the
sign-in itself. Flagging it here simply because it's a new way this session touched an external
account, not because anything about it went wrong.

## Open, for Ian when there's a moment

- Nothing blocking. The test Google account (`iandev8055@gmail.com`) still needs adding as a test user
  under the OAuth consent screen's Audience tab (Google Cloud Console, not Play Console) before sign-in
  will work against it — separate from the Play Console tester/license-testing lists already done.
- Next: UI — the per-album destination picker, Sync/Archive mode restriction on Google-Photos-routed
  albums (TASK-014's "never offer an action that cannot succeed" rule, the same way Camera's own mode
  menu already refuses Sync), and real Pro-unlock gating via `BillingRepository.isPurchased()`. This is
  also where the sign-in flow, wire client, and billing flow all finally get a real, on-device,
  against-an-actual-account test — everything built so far has been provably inert in the running app
  until this lands.
