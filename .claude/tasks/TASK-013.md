# TASK-013 — Video transcode for old clips

Milestone: v0.3.0 — the last space lever, and the one that reaches the biggest files
Requested by: Ian, 19 Aug 2026
Depends on: TASK-011 (the age setting), the SAF finding of 19 Aug 2026 (how the bytes get written)
Gated on: v0.4 retrieval, and a transcode cost measured on real 8K footage

## Goal
Downscale **old** video in Sync albums, full length, so the phone reclaims space from the largest
files on it while the clip stays in the gallery and stays watchable.

Photos already have this through proxies. Video is where the bytes actually are — Ian's own clips
run 103, 163 and 178 MB apiece against a 4 MB photo — so a storage floor that cannot touch video
will often fail to reach its target on a video-heavy phone. TASK-011 records that as the expected
outcome. This is what changes it.

## What is already decided, and by whom

- **Old video may be downscaled full-length**, marked, on charge, Sync albums only — MILESTONES.
- **Truncating to a stub is rejected.** It destroys the one thing old video is for.
- **How old is the user's setting** — Ian, 19 Aug 2026. Immediately / 1 week / 1 month / 1 year.
- **"Never proxy video silently" was an agent's note, not Ian's rule.** Revising it is a normal
  design decision.

## The blocker is a measurement, not a design

Nothing here should be built until a transcode has been timed on real footage from this phone.

Transcoding is the most expensive thing this app would ever do — orders of magnitude past uploading,
which is I/O-bound and cheap in CPU. What has to be known first:

| Measure | Why it decides something |
|---|---|
| Wall-clock seconds per minute of 8K footage | Whether a clip can finish inside a background window at all |
| Battery drain across one clip | Whether "on charge" is sufficient or the job needs to be rarer |
| Peak temperature, and whether the device throttles | A throttled transcode may never finish, and thermal pressure is user-visible |
| Output size against input, at the chosen resolution | Whether the reclaim is worth the cost. If it is not ~5x, reconsider |
| Whether Transformer falls back to software encoding | Software encode on 8K is likely disqualifying, and it is silent |

**Write this down in MILESTONES' hardware log before writing production code.** If a two-minute 8K
clip takes twenty minutes and cooks the phone, the honest answer is that this feature is for
1080p/4K footage only, or is not worth building — and that is a real possible outcome.

## Hard rules

1. **Never transcode anything not verified in OneDrive.** Graph confirmed and byte size matched,
   the same bar as every other destructive operation — `BackupEntryDao.verifiedInCloud()`.
2. **Validate the output before replacing the input.** Transcode to app cache, then open the result
   and confirm: it decodes, duration matches the source within tolerance, it has both tracks, and
   rotation survived. Only then overwrite. A truncated or half-written transcode must never replace
   a real clip. This is TASK-010's rule 2 and it matters more here — a broken video fails silently
   inside an editor and is discovered in the exported result.
3. **Never truncate, never clip, never drop audio.** Full length, both tracks. The output is a
   smaller version of the same clip and nothing else.
4. **Sync albums only**, and only for clips older than the user's age setting.
5. **On charge, and on unmetered if anything is uploaded as a result.** Not a background job that
   ambushes someone at 20% battery.
6. **Rescan MediaStore after the write.** Verified 19 Aug 2026: the index keeps the old size until
   `MediaScannerConnection.scanFile` runs, and the ledger keys on size.

## How the bytes get written — settled by the SAF finding

The consent problem this task would have had is already solved. A persisted SAF tree grant writes
and shortens media this app does not own, with no dialog, and survives a reboot — verified on the
Fold 4, 19 Aug 2026.

So the transcode runs unattended end to end: pick a candidate, transcode to cache, validate,
overwrite through the tree grant, rescan, update the ledger. No Activity, no URI cap, no tap.

**This is what makes the feature reasonable at all.** Under the MediaStore path each transcoded clip
would need a batch approval, and a job that takes minutes per file cannot sensibly be paired with a
consent dialog per batch.

## Resolution and codec — proposed, to be confirmed by the measurement

- **Target 1080p long edge**, H.264 or HEVC, preserving aspect and rotation.
- **Audio passthrough** where the container allows it. Re-encoding audio costs time and quality for
  no meaningful space saving; the video track is the whole cost.
- **Media3 `Transformer`** with `VideoEncoderSettings`, and hardware encoding required — see the
  measurement table.
- **Skip anything already at or under the target.** Same lesson as schema 5 for photos: a clip that
  cannot shrink must stop being offered, or the candidate count never reaches zero.

1080p rather than 4K because the footage this targets is footage people *watch*. A clip worth
editing is either recent, and therefore untouched, or retrievable at full quality from OneDrive.
Retrieval is what makes the choice safe, which is why this waits on v0.4.

## Ledger and schema

`BackupEntryEntity` already carries `isProxied` and `localProxySizeBytes`, and neither is
photo-specific — a transcoded clip is a proxy in exactly the same sense. Reuse them.

`isProxySkipped` likewise records "examined, cannot shrink", which the resolution check needs.

What needs adding:

- `proxyCandidates()` filters `isVideo = 0`. It needs a video counterpart with the age predicate and
  the Sync-album join, not a widening of the existing query — proxying photos and transcoding video
  have different costs and different schedules, and one query returning both would hide that.
- **No schema change is expected.** If one turns out to be needed it is an escalation per CLAUDE.md.

### The re-upload trap applies unchanged
Transcoding changes the file's size, so a rescan computes a different `backupKeyOf` and the scanner
would treat the transcoded clip as a new file to upload — landing a 200 MB transcode in OneDrive
beside the 1.5 GB original. The existing defence is `mediaStoreId` on proxied rows, which the
scanner skips. Confirm it covers video before running anything against a real library.

## Interaction with the rest of v0.3

- **Sync scope** (TASK-011) gates this: under `PHOTOS_ONLY` no video is transcoded, because no video
  is uploaded. Under `VIDEO_ONLY` this becomes the only space lever in a Sync album, and the
  projection stops reading zero — see the running-count section.
- **The storage budget** (TASK-011) gains a second lever. Ordering matters: proxy photos first,
  since they are cheap, local and instant, and only transcode if the floor is still unmet. Largest
  first within that.
- **Archive** is unaffected. It removes files rather than shrinking them, and it keeps its tap.

## Acceptance
- A clip older than the age setting, in a Sync album, verified in OneDrive, is transcoded without
  the user present and without any dialog
- The output plays, full length, with audio and correct rotation, in Samsung Gallery and in CapCut
- Nothing under the target resolution is transcoded, and it stops being offered
- MediaStore reports the new size after the run, and the gallery shows the clip normally
- The ledger marks the clip proxied, and a following scan does not re-upload it
- A failed or interrupted transcode leaves the original untouched
- Battery, thermal and duration figures recorded in MILESTONES against real 8K footage
- Verified on hardware in both themes, per CLAUDE.md

## Not in scope
- **Recent video.** Protected by the age setting, and the whole point of it.
- **Removing video.** That is Archive, with its own consent and its own tap.
- **Re-transcoding.** A clip is transcoded once. The original lives in OneDrive and retrieval is the
  route back to it.
