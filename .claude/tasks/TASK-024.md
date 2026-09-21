# TASK-024 — "Get full size": bring the original back from the gallery's Share menu

Milestone: future release (not v0.3 / v0.4)
Raised by: Ian, 20 Sept 2026
Status: **SHELVED by Ian, 20 Sept 2026.** Nothing is built. A feasibility probe was run and removed; its
findings are below so the work does not have to be rediscovered.

## The problem

An optimised photo or video is a smaller copy in the gallery, with the full-size original in OneDrive. An
edit started from it starts from the smaller copy. Ian's original idea for the app was that **opening a file
to edit it should fetch the full-size version automatically.** MILESTONES ("Platform constraints") records
that Android has no hook for that: an app cannot intercept another app opening a media file, and Samsung could
do it only because Samsung Gallery is both the viewer and the index. Today the only route back is the Restore
tab, which the user has to remember to visit first.

## The idea: a Share / Open-with target

Register Gallery Sync as a target for `ACTION_SEND` (and `SEND_MULTIPLE`, `VIEW`) on `image/*` and `video/*`.
The user shares the photo from the gallery to **"Gallery Sync — Get full size"**; the app finds the file in its
ledger, downloads the original from OneDrive, swaps it in place, and says "open it again to edit". A deliberate
tap, from where the user already is, with no change needed in the editor.

## What the probe found (Moto G, Google Photos 7.93, 20 Sept 2026)

A temporary debug-only activity with the three intent filters above appeared in Google Photos' share sheet as
"Gallery Sync — GS probe". Sharing an optimised photo (`IMG_CT_c1.jpg`, 622,788 bytes on the phone, 4,465,318
in OneDrive) handed over:

- **Caller:** `activity.referrer` = `android-app://com.google.android.apps.photos`.
- **URI:** `content://com.google.android.apps.photos.contentprovider/-1/1/content%3A%2F%2Fmedia%2Fexternal%2Fimages%2Fmedia%2F2521/REQUIRE_ORIGINAL/NONE/image%2Fjpeg/…` — Photos' own provider **wrapping the
  real MediaStore URI, so the MediaStore id (2521) can be parsed out of the path.**
- **Columns on that URI:** `_id`, `_display_name`, `_size`, `mime_type`, `_data`
  (`/storage/emulated/0/DCIM/Camera/IMG_CT_c1.jpg`), `bucket_display_name`, and others.
- **Readable:** `openFileDescriptor` worked, `statSize` = 622,788, i.e. the **smaller copy**, equal to the
  ledger's `localProxySizeBytes`.

So a file can be matched to its ledger row by MediaStore id (parsed), or by name + size against
`localProxySizeBytes`, or by path. The activity is in the foreground, so it can also raise
`MediaStore.createWriteRequest` itself for files outside a granted SAF tree, which a worker cannot.

## What was not tested, and the limits

- **Only Google Photos.** Samsung Gallery, CapCut, Lightroom, Snapseed and Files by Google were not on the Moto G.
  Other apps may share a temporary copy or a `FileProvider` URI instead of a MediaStore one, in which case
  matching falls back to name + size.
- **Does not help an editor that opens files from its own picker** (CapCut, Lightroom import, Instagram, the
  Android Photo Picker). There is no share sheet in that flow, so the user must fetch the full size first, by
  this route or through Restore.
- **The download and the swap were not built.** `RestoreProxyInPlace` and the Restore machinery are the likely
  base: SAF tree write where covered, Android's write dialog otherwise.

## Related, still undecided

- **An edit saved over an already-optimised photo is never uploaded.** `BackupEngine.refreshLedger` skips any file
  whose MediaStore id is in the proxied set, before it looks at date or size, so an editor that overwrites in
  place (Samsung's plain Save, probably) leaves the edit with no cloud copy; an editor that saves a copy is fine.
  Proposed fix, no schema change: treat a proxied file whose current size no longer equals `localProxySizeBytes`
  as edited, stop skipping it, and let it upload as a new file (it lands as `name 1.jpg`, as edited photos in
  Backup albums do). Restore already refuses to write over a file whose size has changed. Ian has not decided.
- **A DocumentsProvider** registered in the system file picker is the only route that works inside an editor's own
  SAF picker (the provider's `openDocument` is called at the moment of use, so the original can be fetched
  then). It covers only editors that use that picker, and is a much larger piece of work. Not tried.

## If it is picked up

1. Add the intent filters and a small Activity (Compose, no gallery UI: a progress line and a result).
2. Resolve the file: parse the MediaStore id out of the shared URI, else name + size against the ledger.
3. Refuse anything not verified in OneDrive at full size (`verifiedInCloud`), the same bar as every rewrite.
4. Download the original, write it in place, clear `isProxied`, and pin the file (`FilePin`), exactly as Restore does.
5. Test with Samsung Gallery on Ian's real device only if he chooses to; the Moto G has Google Photos only.
