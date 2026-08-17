# GallerySync Milestones

## v0.1.0 — Foundation (must ship before Samsung sunset)
- [x] Android project scaffold (Kotlin, Compose, Hilt, Room, Retrofit)
- [x] Logger utility
- [x] Room database schema for cached media index
- [x] OneDrive adapter (Microsoft Graph API — browse folder structure)
- [x] ContentProvider skeleton (registers with Android, returns empty cursor)

- [x] MSAL sign-in (Azure registration landed; public client + PKCE, no secret)

Not tagged v0.1.0 yet. Two items are open:
- **Sign-in has never run on a real device.** It builds and the ViewModel is unit tested against
  a fake, but MSAL's own types cannot be exercised off-device. Until an actual Microsoft account
  signs in on hardware, "the OneDrive stack works" is unverified.
- **The provider ships `exported="false"`.** Nothing outside the app can see it, so CapCut
  cannot yet either. Flipping that is a security-relevant decision and is deliberately Ian's —
  and it is gated behind the open question of whether a plain ContentProvider is the right
  mechanism at all, versus a DocumentsProvider.

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
