# TASK-028 — Long-press a file to open it in the phone's app or the Cloud's

Status: **SPEC, not started.** Decided by Ian, 11 Nov 2026, in a cloud session with no Android SDK or adb, so nothing
here has been built, compiled or seen on a device. To be built on Ian's machine.

## What Ian asked for

*"Give the user the ability to click on any file in the albums, restore and archive tabs and have that open that file in
their image/video application of choice."* Then, for a file that is no longer on the phone: *"is it possible to open it
in the cloud app"*, and *"give the user the option, if the file isn't on the phone whether to open it on the cloud or
download the file temporarily. This is only an option for files that are not on the phone. Not optimized files."*

## Why this does not break "GallerySync is invisible"

Nothing is drawn by this app. A long-press hands the file to the phone's own photo/video app, or to the Cloud's app, and
GallerySync is out of the picture. That is feeding the existing gallery, not replacing it. **No in-app viewer, no
preview, no thumbnail of the file in a new place.** If the build starts to need one, it is the wrong build.

## Decisions (Ian, 11 Nov 2026)

| # | Question | Answer |
|---|---|---|
| 1 | Gesture | **Long-press opens**, the same on the Albums (album file list), Restore and Archive tabs. Tap and swipe keep exactly what they do today: on Restore a tap still selects (Ian, 20 Sept 2026). |
| 2 | File on the phone | Opens in the **phone's default** photo/video app. Android's chooser appears only if no default is set. No forced chooser. |
| 3 | Optimised (Sync) file | It is on the phone, so it opens the **optimised copy on the phone**. The setting below does not apply to it. |
| 4 | File not on the phone | A new setting, **Settings → General, below *Use mobile data***, two values: **Open in the Cloud** (default) / **Download temporarily**. |
| 5 | Open in the Cloud, but the Cloud has no link | Fall back to the temporary download. Backblaze B2 and IDrive e2, which have no link at all. A Cloud that has links but could not give one this time is row 8, not this. |
| 6 | Temporary download and mobile data | **Obeys the existing mobile-data switch** (`allowMeteredNetwork`). Off and on mobile data: no download; say why (Wi-Fi needed, and where the switch is). |
| 7 | Size warning | Only **on mobile data** (so only when the switch allows it), and only for files **over 750 MB**: ask first, stating the size. On Wi-Fi never ask. |
| 8 | Link could not be fetched | Tell the user and offer **Cancel** or **Download**. |

## Which files are "not on the phone"

Not only archived files. Ian's first assumption was that only Archive leaves a file off the phone; corrected in the same
session and accepted:

- Archived files (Albums tab rows, and Restore's *Download* rows).
- Files the user deleted themselves, including ones left in the trash by the deleted-files window.
- Files in the backup folder that came from another phone or an earlier install (Restore lists them since 18 Sept).

Decide "on the phone" by **asking MediaStore/the file**, never the ledger (MILESTONES v0.4.0: *never trust the ledger for
what is on the phone*). Ledger `mediaStoreId`s go stale: version 35 found 7 videos in Movies whose id no longer matched.
A row whose id does not resolve to a readable file is "not on the phone".

## What each tab has today (read 11 Nov 2026)

- **Archive** — `ArchiveScreen.kt`, `ArchiveListItem` → `SwipeChoiceBox` → `ArchiveRow` / `OptedOutRow` /
  `HiddenByAgeRow`. Every row is a `LocalMediaItem` with a `contentUri`. All on the phone. No tap today.
- **Albums** — `AlbumDetailScreen.kt`, `FileCard(entry: BackupEntryEntity, …)`. The ledger row has `mediaStoreId`,
  `mimeType`, `isVideo`, `location`, `remoteItemId`. No tap today; the camera folder wraps it in `SwipeChoiceBox`. The
  list comes from `entryDao.entriesForAlbum`, so **check whether it includes rows whose file has left the phone**; those
  are the rows the setting applies to here.
- **Restore** — `RestoreScreen.kt`, `FileCard(row: RestoreRow, …)` inside `SwipeChoiceBox`; `Surface(onClick = onToggle)`
  selects. `RowKind.Restore` = proxy on the phone, `Download` = not on the phone, `Here` = full size on the phone.
  **`Here` cards are `enabled = false`**, so they need to take a long-press while still refusing selection.

`SwipeChoiceBox` (`ui/common`) uses a drag detector. A long-press must not fire during a swipe and a swipe must not be
eaten by the long-press: check by thumb on the Moto, not by reading.

## Build

### 1. Opening a file on the phone — `ui/common/OpenFile.kt` (new)

- `Intent(ACTION_VIEW).setDataAndType(contentUri, mimeType)` + `FLAG_GRANT_READ_URI_PERMISSION`. A MediaStore content URI
  for ledger rows: `ContentUris.withAppendedId(Images|Video EXTERNAL_CONTENT_URI, mediaStoreId)`.
- No `createChooser` (decision 2). `runCatching`; `ActivityNotFoundException` → a short message, never a crash
  (the pattern in `OneDriveLauncher`).
- No new permission, and no `<queries>` entry, as long as nothing calls `resolveActivity` first. Do not add one.

### 2. A link to the file in its Cloud — `CloudFileLink` (new, `domain/repository`, one per Cloud like `CloudDownloader`)

Returns a URL or "no link". Network calls go through the repository layer (CLAUDE.md), never the ViewModel.
**Researched 11 Nov 2026; nothing below has been tried on a phone:**

| Cloud (`BackupLocation`) | Link | Known |
|---|---|---|
| `ONEDRIVE` | Graph `GET /me/drive/items/{remoteItemId}?select=webUrl` at tap time | Microsoft says these URLs are opaque, so never build one. Documented to open in a browser; whether the OneDrive app takes it is **unknown**. |
| `GOOGLE_PHOTOS` | `mediaItems.get(remoteItemId)` → `productUrl` | The app can read only items it created, which these are. Opens for a signed-in user; app or browser **not tested**. |
| `GOOGLE_DRIVE` | `https://drive.google.com/file/d/{remoteItemId}/view` (or `webViewLink` from `files.get`) | That it opens the Drive app is a forum claim, not documented. Which Google account it opens under is unanswered. |
| `DROPBOX` | **Find out.** Probably the web path of the file | Unverified |
| `PCLOUD` | **Find out.** Web file manager link | Probably the browser |
| `IDRIVE_E2`, `BACKBLAZE_B2` | None | No consumer app; the consoles need a login. Always the temporary download (decision 5). **Do not use a presigned URL**: it hands a working credential to another app (a CLAUDE.md escalation: data exposure). |
| `USB_DRIVE` | None | Not a Cloud; leave out. |

Open the URL with `ACTION_VIEW`. **No link exists for that Cloud at all** → temporary download, with no question.
**The link could not be fetched** (no network, 404, token): tell the user and offer **Cancel** or **Download** (Ian,
11 Nov 2026). Cancel does nothing. Download takes the temporary download, under the same mobile-data and 750 MB rules.
Never download without that answer, because the user chose the Cloud.

### 3. Temporary download — reuse `CloudDownloaders.of(location).openStream(remoteItemId)`

- Into `cacheDir/open/`, served by a **FileProvider** (new manifest `<provider>`, `exported="false"`,
  `grantUriPermissions="true"`, a `res/xml` path for `cache/open/`). Not a permission; does not touch the Play listing.
  Then step 1 with the FileProvider URI.
- **This is a cache, not a file of the user's.** Never written to MediaStore, never in a gallery, never in an album. Clear
  it on the next open and on app start (or by age). CLAUDE.md: on-device file operations are cache management. Not a
  deletion under the deletion rules, but keep it confined to `cacheDir/open/` so nothing else can be reached by it.
- Check free space first (the app already does this for restores).
- Mobile data: obey `allowMeteredNetwork` (decision 6). Over 750 MB on mobile data: ask, with the size (decision 7).
  `750 MB` as one named constant, decimal or binary to match how `Formatting.kt` prints sizes, so the dialog's number and
  the threshold agree.
- Progress with **Cancel**; Cancel deletes the partial file in `cacheDir/open/`.
- Size check after download: bytes received must equal the row's size, or do not open (a truncated video looks like a
  broken file in someone else's app).

### 4. The setting — Settings → General

- `BackupPreferences`: `openNotOnPhone` (`CLOUD` default, `DOWNLOAD`), a new DataStore key. **No Room change.**
- `SettingDropdown` in General with a (?) (`HelpTopic`), **directly below the *Use mobile data* switch**
  (`backup_allow_metered`, `SettingsScreen.kt` ~line 176), which is the last item in General (Ian, 11 Nov 2026).
- Mirror it in the Settings mock the setup tour draws (`SettingsScreen.kt`, around line 939: *the Settings mockup mirrors
  the Settings tab*). The wizard does not ask it and does not write it.
- `.claude/DEFAULTS.md`: add the setting and what its default does.

### 5. Strings

- Settings strings are translated: add to `tools/wizard/wizard_en.json` and every `tools/wizard/<lang>.py`, then
  `python tools/wizard/gen.py es pt-BR fr de ja hi`.
- Messages shown on the Albums, Restore and Archive tabs (no app, Wi-Fi needed, the 750 MB question, progress) follow the
  rule that those tabs stay English. Confirm with Ian if unsure.
- Trash naming differs per vendor; do not name one in any new copy.

### 6. How To Guide

New topics for the long-press on each tab and for the setting, in all seven languages, through the guide tooling
(`tools/guide`). The guide is generated: do not hand-edit `docs/*.html`.

## Tests

- Unit: the decision of what a long-press does, as a pure function — (on phone?, optimised?, setting, Cloud has a link?,
  network, mobile-data switch, size) → open local / open link / download / ask / refuse with reason. Every row of the
  decisions table above is a case. Include: B2 with *Open in the Cloud* downloads; optimised opens local whatever the
  setting; mobile data with the switch off refuses; 750 MB exactly does not ask, one byte more does.
- Unit: a failed link request asks Cancel/Download and never downloads unanswered; Cancel does nothing.
- Unit: the cache path never leaves `cacheDir/open/`.

## On the Moto G (`ZT422CTZQV`), test account, so uploads and downloads cost nothing

- Long-press on each tab: photo, video, optimised photo, Restore `Here` row. Opens in Google Photos (the Moto's gallery).
- A swipe on every swipeable list still works and never opens a file; a long-press never selects.
- *Open in the Cloud* for each Cloud connected: **record where each link lands** (app or browser) in MILESTONES.
- *Download temporarily*: a photo, a video over 750 MB on Wi-Fi (no question) and on mobile data (question; and refused
  with the switch off). Cancel mid-download leaves nothing in `cacheDir/open/`.
- Light and dark (`adb -s ZT422CTZQV shell "cmd uimode night yes"` / `no`), large text, and `adb logcat -b crash -d` after.
- Not the Fold 8: Ian's real phone. Look only, after a Play install.

## Open for Ian

Nothing. Both points were answered on 11 Nov 2026 (rows 4 and 8 above).
