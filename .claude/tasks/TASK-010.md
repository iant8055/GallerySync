# TASK-010 — Photo proxies

Milestone: v0.3.0 — "Photo proxies keep every photo visible in the phone's own gallery"
Depends on: v0.2.0 backup (verified cloud copies)
Blocks: the storage budget, which is meaningless without something to shrink

## Goal
Replace a photo's local file with a downscaled version while the original stays in OneDrive.
The photo remains in Samsung Gallery — searchable, face-grouped, editable, shareable — at roughly
a tenth of the space.

This is the milestone that delivers the product. It is also the most dangerous code in it.

## Why this is dangerous, stated plainly
**It overwrites the user's original photo.** After proxying, the full-resolution image exists only
in OneDrive. A bug here does not fail loudly — it silently degrades a library, and the user finds
out years later when they try to print something.

Every rule below exists because of that.

## Hard rules for this task

1. **Never proxy a photo that is not verified in OneDrive.** Verified means Graph confirmed it AND
   the reported byte size equals the local size. `BackupEntryDao.verifiedInCloud()` already
   expresses this. No other definition is acceptable.
2. **Generate and validate the proxy before touching the original.** Write it to app cache, decode
   it back, confirm dimensions and that EXIF survived. Only then overwrite. A half-written proxy
   must never replace a real photo.
3. **Videos are never proxied by this task.** A degraded clip fails silently inside an editor and is
   discovered in the exported result, so nothing on the proxy path here touches video.

   *Amended 19 Aug 2026.* This rule originally ended "out of scope, permanently". That was this
   spec's word rather than Ian's, and MILESTONES has since decided the opposite for one case:
   **old** clips may be downscaled full-length, marked, on charge, in Sync albums only, pending a
   transcode cost measured on real 8K footage. **Recent video is never touched** — that half is the
   requirement and it stands. Nothing changes for TASK-010 itself, whose proxy path stays photos
   only; the word "permanently" was the part that was wrong.
4. **The user consents.** Modifying media this app did not create requires
   `MediaStore.createWriteRequest()` on API 30+, which shows a system dialog. That is a feature,
   not an obstacle: the user sees what is about to change.
5. **Below API 30, do not offer proxying at all** rather than finding a way around the consent
   requirement.

## The re-upload trap — read this before writing code
The ledger keys on `album + name + size + mtime`. **Proxying changes the size**, so a rescan
computes a different key, finds no match, and treats the proxy as a new file — uploading a 400 KB
proxy into OneDrive beside the 4 MB original. `conflictBehavior=rename` means it lands as a
duplicate rather than destroying anything, but the result is a library full of junk.

Fix: proxied rows are additionally identified by `mediaStoreId`, which survives a content
rewrite. The scan must skip any local item whose `mediaStoreId` matches a proxied ledger row,
regardless of the computed key.

Add to `BackupEntryEntity`:
- `isProxied: Boolean = false`
- `localProxySizeBytes: Long? = null` — what the local file now occupies, for honest reporting

Schema 2 → 3, additive, with a written migration and an instrumented test. `sizeBytes` keeps
meaning **the original's** size, because that is what is in the cloud.

## What to build

### 1. `data/local/media/ProxyGenerator.kt`
Pure-ish image work, no MediaStore writes:
- Decode with `BitmapFactory` using `inSampleSize` so a 50 MP image never lands in memory whole —
  OOM on a large photo is a crash on someone's phone, not a theoretical concern.
- Scale so the **long edge is 2048px**. Never upscale: an image already smaller is left alone and
  reported as not worth proxying.
- Encode JPEG at quality 90.
- **Copy EXIF across with `ExifInterface`** — date taken, orientation, GPS, camera. Without it the
  gallery loses date grouping and map placement, which users notice immediately. Orientation
  especially: dropping it turns portraits sideways.
- Return the proxy in app cache plus its size, or null when proxying would not help.

### 2. `data/local/media/ProxyApplier.kt`
The destructive half, kept separate so the generator stays testable:
- Batch `MediaStore.createWriteRequest()` for the URIs being replaced.
- After consent, for each: open the cache proxy, verify it decodes and carries EXIF, then write
  over the original through `ContentResolver.openOutputStream(uri, "wt")`.
- Update the ledger row: `isProxied = true`, `localProxySizeBytes = <new size>`.
- If any single file fails, leave the rest alone and report it. Never continue blindly after a
  write error — a partial write is the one outcome that loses a photo.

### 3. Scanner change
`BackupEngine.refreshLedger()` must skip local items whose `mediaStoreId` matches a proxied row.
Otherwise every proxied photo is re-uploaded on the next run. This is the single most important
line of the task.

### 4. UI
Settings gains a "Optimise photos" action showing what would be reclaimed. Album rows show which
photos are optimised versus whole. Nothing automatic in this task — the storage budget is
TASK-011, and automatic destructive work should not ship in the same change as the mechanism.

## Test requirements
Unit (`ProxyGenerator` is the testable half):
- long edge lands at 2048 for landscape, portrait, and square inputs
- an image already under 2048 returns null rather than being upscaled
- EXIF date, orientation and GPS survive
- a corrupt/undecodable input returns null instead of throwing
- `inSampleSize` chosen such that a very large image does not decode at full size

Instrumented:
- schema 2 → 3 migration validates
- a proxied row is skipped by the scanner and not re-uploaded

## Acceptance criteria
1. A proxied photo still appears in Samsung Gallery with its correct date, orientation and place.
2. The local file is materially smaller; the OneDrive original is untouched.
3. A rescan does not re-upload the proxy.
4. Nothing is proxied that is not verified in the cloud.
5. `./gradlew assembleDebug testDebugUnitTest connectedDebugAndroidTest` green.

## Out of scope
- Video (permanently)
- Automatic budget-driven proxying (TASK-011)
- Restoring the original back to the phone (that is v0.4 retrieval)

## Report back
Files created, the migration, and confirmation that a proxied photo survives a rescan without
being re-uploaded.
