# GallerySync Milestones

**Samsung turns off Gallery Sync on 30 September 2026.**

## Release gate — decided by Ian

**Nothing is published to Google Play until v0.3.0 and v0.4.0 are built and tested.**

Shipping v0.2.0 alone would deliver "back up your photos, and they disappear from your
gallery" — the space is freed but nothing keeps the photos visible, because that is what
v0.3 (photo proxies) and v0.4 (retrieval) are for. That is a broken product no matter how
well the backup works.

Two consequences:

- **The Sept 30 date is a personal deadline, not a release deadline.** Ian's own library
  still needs protecting when Samsung's sync stops. Backup alone covers that: run it, and
  simply do not use Move to backup until the gallery side exists. OneDrive's own camera
  backup is a reasonable second net in the meantime.
- **v0.3 can be built properly rather than rushed**, since no store listing depends on it.

## Naming
Do not call anything "Sync" in the UI until v0.3 and v0.4 land. Samsung's Gallery Sync kept
photos visible while freeing space; ours does not yet. Using the word early promises the
behaviour users are migrating from and would be wrong. "Move to backup" until then.

## Design principle — GallerySync is invisible

**It is not a gallery app and must never become one.**

Its only job is making files *present*. Everything a person actually does with photos —
viewing, search, face grouping, editing, sharing, albums, stories — the phone's existing
gallery already does, with years of work behind it. Rebuilding any of that would produce
something worse than what the user already has.

Consequences that bind every future task:

- **Feed the existing gallery, do not replace it.** A file with local bytes appears in Samsung
  Gallery, CapCut and everything else automatically, because it is an ordinary file. No
  integration is needed or possible.
- **GallerySync's own UI stays minimal**: setup, album selection, a storage budget, and a plain
  list for retrieving something that is not on the phone. No photo grid, no thumbnails
  browser, no search, no editing. If a task starts to look like building a gallery, it is the
  wrong task.
- **Set up and forget.** The user sets a budget once. The background worker maintains it.

## Platform constraints — established by experiment, do not re-litigate

Verified on a Galaxy Z Fold 4 (Android 16) on 2026-08-17:

- **A file with no local bytes cannot appear in any gallery app.** MediaStore rows must point
  at a real file, and the system opens that file directly — there is no hydration hook. Android
  has no placeholder-that-downloads-on-open mechanism for third-party apps.
- **Samsung Gallery's cloud albums came from Samsung's own private index**, not MediaStore.
  That is why third-party apps could never see them, and it is exactly what is being switched
  off. It cannot be replicated by a third-party app.
- **A trash request is not a guarantee of recoverability.** See the deletion rule in CLAUDE.md.
- Therefore: **storage can be reduced, never eliminated.** Any plan that plans on zero local
  storage while remaining visible in the gallery is impossible, not merely hard.

## v0.1.0 — Foundation ✅ TAGGED
Scaffold, Logger, Room, OneDrive Graph adapter, MSAL sign-in, browse UI.
Verified on hardware: sign-in completes and the real drive lists.

## v0.2.0 — Backup ✅ WORKING (not yet tagged)
- [x] `Files.ReadWrite` scope
- [x] Upload ledger keyed on content, not MediaStore ids
- [x] Media scanner, partial-access aware
- [x] Resumable Graph upload — verified byte-identical on hardware
- [x] Per-album include/exclude
- [x] Skip files already present in OneDrive
- [x] Backup UI with a manual run
- [x] Move redundant local copies out, once the cloud copy is verified
- [ ] Schedule the periodic worker (currently manual only, deliberately)
- [ ] Metered-network preference (currently unmetered-only, hardcoded)
- [ ] Retry failed items from the UI

Cutover rule: run alongside Samsung's sync for at least two weeks before trusting this alone.

## v0.3.0 — Space management
The milestone that delivers the actual product: the phone stops filling up, and the existing
gallery keeps working.

- [ ] **Photo proxies.** Downscale photos (~2048px, EXIF preserved) and keep the proxy in
      MediaStore permanently. Roughly 10x smaller, and every photo stays visible, searchable and
      editable in the phone's own gallery. This is not a storage trick — it is what keeps the
      existing gallery whole.
- [ ] **Never proxy video silently.** A degraded clip handed to an editor fails quietly, and the
      user only discovers it in the exported result. Videos are kept whole or kept in the cloud.
- [ ] **Storage budget.** User sets a ceiling; the worker maintains it — newest kept, oldest
      evicted, nothing evicted until its cloud copy is verified.
- [ ] **Rolling window for video**, so recent clips stay usable in the gallery.
- [ ] Per-album "keep originals on device" for albums actively edited from.
- [ ] Clear marker showing which items are optimised versus whole.

## v0.4.0 — Retrieval and deletion sync
- [ ] Fetch a cloud-only item back on demand, registering it in MediaStore so every app sees it
- [ ] Plain retrieval list — **not** a photo browser
- [ ] Deletion sync, opt-in and batched. Highest-risk feature in the product; it only follows a
      backup engine that has been watched working. Never infers deletion from absence alone —
      a card unmounting or a permission being revoked must not be read as intent.

## v0.5.0 — Google Photos + Billing
- [ ] Google Play Billing (`pro_unlock`)
- [ ] Google Photos adapter (requires OAuth — Ian)
- [ ] Settings: sync frequency, account management
