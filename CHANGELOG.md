# GallerySync — Changelog

Every change since version 26 (0.3.25), the last build the Closed Testing group had. The short notes Play shows to users
(500 characters per language) are in `release-notes/`; this file is the full record.

## Version 41 (0.3.40) — 23 Nov 2026, uploaded

**Large uploads**
- A big video no longer starts again from zero. Android stops every backup after about ten minutes; the next one now always continues the OneDrive upload from where it stopped. Before, about twenty minutes in, a still-good upload was treated as expired and begun again, so a file over about 2.5 GB could never finish. Seen on the Moto G: an 8.4 GB video resumed at 3.0 GB and finished whole.
- A backup that Android stops now carries on straight away. One started by new photos used to wait for the next new photo, and the six-hourly one for six hours.
- A file that keeps being interrupted without getting further goes to the end of the queue, so the other files go first, and its album page says *couldn't finish uploading · trying after the rest*.

**Album page: one swipe for every file**
- Swipe left keeps a file out of the backup: greyed, *kept out of the backup · your choice*, never uploaded, optimized or archived. A copy already in a Cloud stays.
- Swipe right backs it up: puts a kept-out file back, sends a file gone from its Cloud again (asking the Cloud first), or retries a failed one.
- *Select all*, *Clear* and *Resend* are replaced by **Back up all** for files gone from the Cloud.
- The camera folder keeps its own swipe, which selects files to optimize.

**Albums tab**
- The red *N files not backed up* counts files that need a decision: gone from their Cloud, failed, or unable to finish. New files waiting for the next backup are not counted.
- New grey line *N files not backed up by choice*.

## Version 40 (0.3.39) — 23 Nov 2026, uploaded

**Restore tab**
- Lists only what a Cloud still holds. Files deleted from OneDrive, Dropbox, pCloud or any other Cloud that can be asked are no longer offered for download; a Cloud that cannot be reached removes nothing.
- Folders backed up to every Cloud appear, with their files that are still on the phone shown greyed as *already on this phone*, as OneDrive's always were.
- Each folder's card names its Cloud: *Pictures · Dropbox*, or several joined (*Pictures · Dropbox, pCloud*).


**Wording**
- The Sync mode is now called **Optimize**, and *Sync now* is **Back Up Now**. Every on-screen "optimize" is spelt "optimize". In Settings the *Sync* section is **Optimization** and *Camera Sync* is **Camera Folder**, whose rows now read **Select Camera Folder**, **Only Optimize Photos/Videos older than**, **Optimize Photos** and **Optimize Videos**. Nothing behaves differently.

**Settings**
- A folder inside a top-level folder can choose its own Cloud. Adding *DCIM / Funny stuff* now asks about that folder and lists it under DCIM in *Where each folder goes*; before, the answer moved the whole of DCIM. Every folder added under *Folders to back up* has its own row, following its parent's Cloud until one is chosen.

**Albums tab**
- An album whose Cloud no longer holds some of the files it sent says so in red ("25 files not backed up"), found by the tab's check and **Rescan**. Nothing is sent again on its own.
- On the album's page those files are greyed and marked "not in Dropbox". Select them as on the Restore tab (swipe right, or **Select all**) and press **Resend** to send them again, to where their folder goes now. A new (?) explains it. Optimized files are never offered: their full-size original was only in the Cloud.
- *Everything here is backed up* no longer shows while an album below says otherwise: every Cloud an album names must have confirmed what was sent to it.
- The album's page follows uploads as they finish. Before, files resent or newly added went on reading "pending" after they had reached the Cloud, until the page was left and opened again.

**Settings reset**
- The stored names of the optimize settings were respelt, so on an existing install these start again from their defaults: Settings → *Optimize photos* and *Optimize video* (and their modes and ages), and the Camera Folder's *Optimize Photos* default. Choose them again after updating. Nothing is optimized because of the reset: the defaults are off.

## Version 39 (0.3.38) — 16 Nov 2026, uploaded, installed on the Fold

**Albums tab**
- The upload line now names the album after the count, and shows the file's size sent of its total ("Uploading 3 of 40 · Camera · clip.mp4 · 94 MB of 249 MB") instead of a percentage. The two sizes never split across lines.
- The upload line and its bar moved inside the header card, so **Hide Details** folds them away too. *Syncing…*, Pause and Stop stay; a finished run's result stays below the card.
- Albums backed up to Dropbox, Google Drive, pCloud, Backblaze B2 or IDrive e2 now say "N verified in Dropbox" (or "N of M verified"), like OneDrive: the Albums tab's check asks that Cloud about each album's folder and counts a file only when the Cloud holds it at the same size. Google Photos cannot be asked and still says "sent to". Nothing is changed by the check.

## Version 38 (0.3.37) — 15 Nov 2026, uploaded

**Sync**
- Settings → Sync → *Optimize video* → *Older than* is now the same two menus as Archive's: a number from 1 to 9, and hour, day, week, month, year or Straight away. The number is greyed while Straight away is chosen. A choice saved earlier keeps its meaning, except 12 hours, which now reads as 1 day.
- **Every age in the app now has ½.** The number menu reads ½, 1 to 9, on the Archive tab, Settings → Archive, Settings → Sync → *Older than*, and the Camera folder.
- **The Camera folder's age is now the same two menus**, on its screen and in Settings → Camera Sync. A choice saved earlier keeps its meaning; 6 months is now 6 × 30 days (180, was 182).

## Version 37 (0.3.36) — 14 Nov 2026, uploaded

**Opening a file (TASK-028)**
- Long-press a file on the Albums, Restore or Archive tab to open it in the phone's own photo or video app. Tap and swipe are unchanged; on Restore a long-press never selects, and a greyed-out file already on the phone can be opened.
- A file that is not on the phone follows Settings → General → *Files not on the phone*: **Opens in Cloud App or Browser** (default) or **Download temporarily**. OneDrive's link ends in the browser (no link opens a file in the OneDrive app). Backblaze B2, IDrive e2 and pCloud have no link, so they always download. Google Photos cannot be downloaded.
- A temporary copy lives only in the app's cache, never in the gallery, follows *Use mobile data*, and on mobile data asks first above 750 MB.
- New (?) on the setting, and How To Guide topics, in all seven languages.

## Version 36 (0.3.35) — 2 Nov 2026, uploaded

**Archive**
- *Only show files older than* is now two menus, on the Archive tab and in Settings: a number from 1 to 9, and hour, day, week, month, year or All. The number is greyed while All is chosen. A default saved earlier keeps its meaning.

## Version 35 (0.3.34) — 1 Nov 2026, uploaded

**Dates**
- Files now sort and age by the date they were taken, not the date they were last modified. Optimizing a file rewrites its modified date, so an optimized album read as all new and Archive's *older than* held all of it back. Covers Archive's *older than*, the Camera folder's age, *Sort by Date* in an album, *Newest first* on Restore, and the date shown on each file.

## Version 34 (0.3.33) — built 1 Nov 2026, never uploaded; these changes first reach users in version 35

**Settings**
- **Pro unlocked. You can use every Cloud.** moved from under the Clouds heading into the green Backup band.
- The Backup, Albums, Sync, Restore and Archive section titles are now translated. The tabs named Albums, Restore and Archive stay English.

## Version 33 (0.3.32) — 1 Nov 2026, Production

**Settings → Clouds**
- A light line between each Cloud.
- A Cloud that is not connected has its Connect or Enter keys button on the same line as its name, at any text size.
- With Pro bought, the section now says **Pro unlocked. You can use every Cloud.** Before, nothing said so.
- A connected Cloud shows two columns under the headings **Clouds** and **Backup folder**: its name, account and Sign out on
  the left, its folder and Change on the right.

**Adding a folder**
- Add a folder in Settings now rescans by itself, then asks which Cloud the folder should go to, and shows where in that Cloud,
  with **Change**. **Decide later** changes nothing. Nothing is uploaded until an album has a mode.
- A folder you add is always listed under **Where each folder goes**, even with no photos or videos in it yet, and leaves
  the list as soon as you remove it.

**Settings layout**
- **Where each folder goes** is now a main title of its own, like Backup and Albums.

## Version 32 (0.3.31) — 25 Oct 2026, Production

The same code as the final version 31 build. Version 31 (0.3.30) was uploaded but Play's automatic app-string translation
failed for French, so it was not released; Ian turned automatic translation off (the app carries its own translations, and
the Albums, Restore and Archive tabs stay English on purpose), and Play needs a new version number.

**Large text on narrow screens** (seen on the Galaxy Z Fold 8's cover screen)
- The Archive tab's question: its Yes and No can always be scrolled into reach, and the age choice stays visible.
- Labels on buttons and filters shrink to fit on one line instead of being cut off ("Back", "Arch", "Checkin").
- Sizes keep the number and unit together ("936 MB" no longer splits across lines).
- The coloured card at the top of each tab lets its text grow only up to 1.3 times. Everything else still follows the
  phone's text size in full.

**Smaller headers**
- The coloured card at the top of Albums, Restore and Archive has a **▲ Hide Details ▲** line. Tapped, the card folds down
  to its title, number and main button, and the line becomes **▼ Show Details ▼**. Each tab remembers its choice.
- Results, progress and anything that needs an answer stay in view when folded.
- Albums keeps its four mode buttons when folded; only the mode count folds.
- Albums: "Tap to filter by mode" removed; its (?) explains it.

**Settings**
- A setting whose value is long (Compression, "High compression — 480p") puts its button under the label when both do not
  fit, instead of squeezing the label to one letter per line.

**Font**
- The app draws its text in its own font, Roboto (the standard Android font), so it looks the same on every phone
  whatever the phone's font style. (Noto Sans was tried first and looked too large and too heavy with bold text.)
- **Settings → Font** switches between **App font** and **Phone's font**. Text size follows the phone either way.

## Version 30 (0.3.29) — 20 Oct 2026, Closed Testing

The same code as version 29, under a new version number. Play would not add the version 29 bundle, which is in production
review, to the Closed Testing track. It gets the Welcome screen fix to the testers and gives Google's pre-launch tests a
build to run.

## Version 29 (0.3.28) — 20 Oct 2026, Production

**Welcome screen (testers could not get past it in version 28)**
- The one-line rolling language strip at the bottom of the Welcome screen is gone. Its first line was the prompt, which did
  nothing when tapped, and nothing showed that it had to be swiped.
- A small **Tap to continue** pill sits near the bottom of the Welcome picture, in every language.
- Tapping the picture opens a **language pop-up**: the heading, the seven languages in their own names, and **OK**.
- Tapping a language only selects it: the check moves, and the heading and button switch to that language at once, so a
  wrong tap is easy to see and undo. **OK** continues. Tapping outside the pop-up does nothing; Back leaves the app.

## Version 28 (0.3.27) — built 15–17 Oct 2026, not released (rejected in production review)

**Seven languages**
- Choose the language on the Welcome screen: English, Español, Português (Brasil), Français, Deutsch, 日本語, हिन्दी.
- The **setup wizard**, the **Settings tab**, the **(?) help cards** and the **How To Guide** follow that choice.
  The Albums, Restore and Archive tabs stay in English.
- Anything that names a screen or a mode elsewhere in the app stays in English in every language: tab names, the album
  modes Backup, Sync, Archive and Off, and the Archive and camera-folder filters.
- **Settings → Language** shows and changes the same choice. It now sits just below the How To Guide card, above Appearance.
- The How To Guide is published in all seven languages, with a language row on every page; "Read more" on a (?) card opens
  the guide in the chosen language at that topic.
- Every install carries all seven languages (Play's per-language download splitting is turned off), so a language chosen
  on the Welcome screen works even when it is not the phone's language.

**Pro and the free trial**
- The Pro unlock is shown as **$9.99** (one-time, no subscription) in the trial offer, the days-left line and the
  trial-ended line. The price charged is set in Play Console.
- **Trial reminders:** a notification 3 days before the free Pro trial ends, and another on the day it ends. Sent once each,
  only when Pro is not bought and a folder actually goes to a Cloud other than the free one. Nothing is changed or removed.
- The **Trial ended** line in Settings no longer names Google Photos for every Cloud.
- Purchase tested end to end with a real payment on a Play install: bought, verified against the licensing key,
  acknowledged, Pro unlocked, an extra Cloud backed up, Pro kept after a reinstall, and removed by a refund.

**Ratings**
- After the first backup is checked and finished, pressing **Finish** asks Google Play for its rating sheet, once. It waits
  until the New Albums pop-up is cleared. Play decides whether it actually appears; nothing depends on the answer.

**The How To Guide and pop-ups**
- The guide and the policy pages follow the app's **Appearance** setting (System, Light or Dark) instead of always showing
  their light colours.
- The Appearance (?) card says so.

**Setup wizard wording**
- The finish line reads "N file(s) checked … N file(s) sent in all", correct for any count.
- The start-time card says **"You have selected nothing to back up"** instead of "0 B" when everything chosen is already in
  the Cloud.
- The folder list's "15 folders, 2212 photos, 42 videos" line is translated (it was built from English words).
- Sizes use each language's decimal mark.
- The German video-quality label is shortened so it fits its button.

**Under the hood**
- Code cleanup after Closed Testing: the original ContentProvider and its two database tables are removed (database
  version 16, with a tested migration), along with test-only code and the old folder screen.
- Translation sources for the wizard and Settings live in `tools/wizard/`, generated into `values-<language>/strings.xml`.

## Version 27 (0.3.26) — 14 Oct 2026, built, not uploaded

- The Albums status line clears when an automatic backup ends, instead of staying on "Uploading … 100%".
- The finish summary says the check is OneDrive's and that the sent figure covers every Cloud.
- The folder screen says **Select** instead of Check.
