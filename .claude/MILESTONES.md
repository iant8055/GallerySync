# GallerySync Milestones

## v0.1.0 — Foundation (must ship before Samsung sunset)
- [ ] Android project scaffold (Kotlin, Compose, Hilt, Room, Retrofit)
- [ ] Logger utility
- [ ] Room database schema for cached media index
- [ ] OneDrive adapter (Microsoft Graph API — browse folder structure)
- [ ] ContentProvider skeleton (registers with Android, returns empty cursor)

## v0.2.0 — Core Sync (must ship before Samsung sunset)
- [ ] On-demand download: file fetched from cloud when accessed via ContentProvider
- [ ] Media index sync: background WorkManager job updates Room from cloud
- [ ] ContentProvider serves real media to third-party apps (CapCut test)
- [ ] Local cache management (size limit, LRU eviction)

## v0.3.0 — Google Photos + UI
- [ ] Google Photos adapter (Google Photos Library API — requires OAuth)
- [ ] Unified media browser UI (Compose) showing both sources
- [ ] Folder structure preserved and browsable in-app
- [ ] Settings screen (cache size, sync frequency, account management)

## v0.4.0 — Samsung Galaxy Polish
- [ ] Samsung Gallery integration (appears as album source)
- [ ] Notification for sync status
- [ ] Offline mode (graceful degradation when no network)
- [ ] Widget (optional)
