# GallerySync Milestones

**Hard deadline: 30 September 2026.** Samsung turns off Gallery Sync that day. From
1 October, if GallerySync is not backing up, new photos on the phone are unprotected.
Backup is therefore the critical path and everything else yields to it.

## v0.1.0 — Foundation ✅ TAGGED
- [x] Android project scaffold (Kotlin, Compose, Hilt, Room, Retrofit)
- [x] Logger utility
- [x] Room database schema for cached media index
- [x] OneDrive adapter (Microsoft Graph API — browse folder structure)
- [x] ContentProvider skeleton (registers with Android, returns empty cursor)
- [x] MSAL sign-in (public client + PKCE, no secret)
- [x] Browse UI proving the stack end to end

Verified on a Galaxy Z Fold 4 (Android 16): sign-in completes and the root listing
returns the real drive.

## v0.2.0 — Backup (MUST SHIP BEFORE SEPT 30)
Replaces what Samsung is switching off. Phone → OneDrive.

- [x] `Files.ReadWrite` scope (granted in Azure, requested by the app)
- [x] Upload ledger in Room: content-derived key, size, mtime, state, remote id
- [x] Local media scanner: enumerate albums via MediaStore, partial-access aware
- [x] Graph upload — resumable upload sessions, verified on hardware against the
      real account: 12 MB chunked upload stored byte-identical
- [ ] WorkManager backup worker: network + battery constraints, retry with backoff
- [ ] Per-album include/exclude (mirrors the model Samsung already taught users)
- [ ] Backup status UI: what is pending, what failed, manual "back up now"
- [ ] **Verification**: prove a file actually landed, rather than assuming it did

Rule for cutover: run GallerySync and Samsung's sync **in parallel for at least two
weeks** before trusting GallerySync alone. Duplicate uploads are harmless. A silent
backup failure while GallerySync is the only thing running is not recoverable.

## v0.3.0 — Deletion sync (opt-in) and access bridge

### Deletion sync
Deliberately not in v0.2.0. It is the highest-risk feature in the product — the one where a
bug destroys data rather than merely failing to save it — so it is built only on top of a
backup engine that has been watched working for weeks.

Nothing here ever deletes permanently; see the deletion rule in CLAUDE.md.

- [ ] Detect locally-deleted files (the ledger already knows which rows vanished)
- [ ] Prompt in **batches**, never per file: "47 photos were removed from your phone.
      Remove them from OneDrive too?", with the list reviewable before confirming
- [ ] Prompt on next app open, not the instant a deletion lands — deletions come in bursts
      while the app is backgrounded
- [ ] **Off by default, explicit opt-in**
- [ ] Never infer deletion from absence alone. A file can vanish from MediaStore because a
      card was unmounted, a permission was revoked, or scoped storage hid it. If a large
      fraction disappears at once, treat it as a fault and prompt nothing — that pattern is
      how a sync tool destroys a library
- [ ] Remote deletion goes to OneDrive's recycle bin; local deletion uses
      `MediaStore.createTrashRequest()` and is not offered below API 30

### Access bridge
The differentiating feature: cloud media usable by any third-party app.

Established by experiment on 2026-08-17: Samsung Gallery renders cloud albums from a
private index with no local files and no MediaStore rows, which is exactly why CapCut
cannot see them. Bridging cloud media into MediaStore requires real bytes on disk.

- [ ] On-demand download when an item is requested
- [ ] MediaStore registration so every app sees the file normally
- [ ] Cache manager: size ceiling, LRU eviction
- [ ] CapCut verification on hardware

## v0.4.0 — Google Photos + Billing
- [ ] Google Play Billing (BillingRepository, pro_unlock IAP)
- [ ] Pro upgrade screen
- [ ] Google Photos adapter (requires OAuth — Ian)
- [ ] Settings: cache size, sync frequency, account management
