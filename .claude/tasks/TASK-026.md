# TASK-026 — Multi-cloud: Google Photos as a second destination, behind a Pro unlock

Milestone: v0.5.0 (Google Photos + Billing), pulled forward at Ian's request, 22 Sept 2026
Raised by: Ian, 22 Sept 2026 — competitive research on multi-cloud support, market pricing research, then
"lets plan out for the multi-platform Pro add on option... $2.49 price point works"
Status: **IN PROGRESS.** OAuth registered. Room schema migrated (v11 → v12). Provider abstraction and
Google Photos plumbing being built.

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

## Open, for Ian when there's a moment

- Nothing blocking right now. Will flag here if something needs a decision only he can make.
- Next up, no decision needed to start: the AppAuth-based Google sign-in flow (PKCE against the Android
  client), then the Google Photos wire client (raw upload + `batchCreate`), then DI wiring and
  `BackupEngine`'s dispatch. UI (destination picker, Sync/Archive mode restriction, Pro-unlock gating)
  comes after that.
