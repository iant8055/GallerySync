# GallerySync Milestones

## v0.1.0 — Foundation (must ship before Samsung sunset)
- [x] Android project scaffold (Kotlin, Compose, Hilt, Room, Retrofit)
- [x] Logger utility
- [x] Room database schema for cached media index
- [x] OneDrive adapter (Microsoft Graph API — browse folder structure)
- [x] ContentProvider skeleton (registers with Android, returns empty cursor)

- [x] MSAL sign-in (Azure registration landed; public client + PKCE, no secret)

- [x] Browse UI proving the stack end to end

**Tagged v0.1.0.** Verified on a Galaxy Z Fold 4 (Android 16): interactive sign-in completes,
and the root listing returns the real drive — 13 items received, 12 mapped, the unmapped one
being a Graph package item the mapper drops by design.

Carried into v0.2.0 as the blocking question:
- **The provider ships `exported="false"`, and that is not the real problem.** A plain
  ContentProvider on a custom authority is invisible to CapCut no matter what `exported` says,
  because no third-party app knows to query `com.gallery.sync.provider`. The supported mechanism
  is a DocumentsProvider (Storage Access Framework) — and that only helps if the target app uses
  `ACTION_OPEN_DOCUMENT` rather than its own MediaStore-backed gallery. Settle this before
  building anything further on the provider.

## v0.2.0 — Core Sync (must ship before Samsung sunset)
- [ ] On-demand download: file fetched from cloud when accessed via ContentProvider
- [ ] Media index sync: background WorkManager job updates Room from cloud
- [ ] ContentProvider serves real media to third-party apps (CapCut test)
- [ ] Local cache management (size limit, LRU eviction)

## v0.3.0 — Google Photos + Billing + UI
- [ ] Google Play Billing integration (BillingRepository, pro_unlock IAP)
- [ ] Pro upgrade screen (Compose)
- [ ] Google Photos adapter (Google Photos Library API — requires OAuth, Pro only)
- [ ] Unified media browser UI (Compose) showing both sources
- [ ] Folder structure preserved and browsable in-app
- [ ] Settings screen (cache size, sync frequency, account management)

## v0.4.0 — Samsung Galaxy Polish
- [ ] Samsung Gallery integration (appears as album source)
- [ ] Notification for sync status
- [ ] Offline mode (graceful degradation when no network)
- [ ] Widget (optional)
