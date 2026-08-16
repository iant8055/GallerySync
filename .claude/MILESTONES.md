# GallerySync Milestones

## v0.1.0 — Sync Engine Foundation
- [ ] Local folder watcher (add / modify / delete events, debounced)
- [ ] SQLite metadata store (file hashes, paths, timestamps)
- [ ] One-way sync: source → destination
- [ ] Duplicate detection via SHA-256 hash

## v0.2.0 — Usable Desktop App
- [ ] Conflict resolution: newer-wins and manual modes
- [ ] Electron tray app with sync status indicator
- [ ] Real-time sync log UI panel

## v0.3.0 — Organization & Cloud
- [ ] EXIF-based auto-organization by date and camera model
- [ ] OneDrive source adapter (via local sync folder)

## v0.4.0 — Full Cloud Support
- [ ] Google Drive source adapter (OAuth — requires Ian approval)
- [ ] Scheduled sync jobs with configurable intervals
- [ ] Preview thumbnail generation
