# TASK-025 — First run on the real phone: the Galaxy Z Fold 8

Milestone: before any release; the personal deadline is Samsung's sync ending on **30 Sept 2026**
Raised by: Ian, 20 Sept 2026
Status: **PLAN, not started.** Nothing here has been done. Nothing has been installed on the Fold 8.

## The facts that shape the plan

**The Fold 8 was backed up, but not recently** (Ian, 20 Sept 2026, correcting a first reading of "I haven't backed it
up"). The library (148 GB in earlier notes, most of it video) is mostly in OneDrive already; **the last several weeks
of photos and videos are not.** It is his real phone, and the app has only ever been run against the Moto G's test
data (a few thousand files at most, on a test OneDrive account). So:

- **Two populations.** The old library is in OneDrive already (by Samsung's sync, presumably); the recent weeks exist
  **only on the phone**. The first population tests the app's skip-existing path at real scale. The second is
  what needs protecting, and it is small.
- **The reconcile has to find the old backup, or the app will upload the whole library again.** It matches by name
  and size inside the album's folder under the destination root (default `Samsung Gallery/DCIM`). If Samsung's
  backup sits somewhere else or under different names, the app sees nothing there and re-uploads everything.
  Working this out is the first thing in Phase 0, and the reconcile numbers in Phase 2 are how it is confirmed.
- **Two accounts, kept apart.** The Fold 8 will sign in with Ian's **personal OneDrive account**, which is separate from
  the Moto G's test account. Nothing done on the Moto G touches it. The flip side is that this is the first
  time the app meets a real drive full of Ian's other files: its permission (`Files.ReadWrite`) covers the whole
  drive, not just the backup folder, so the care rules below matter more here than they ever did on the test account.
- Backup is the only mode that is safe to run here, because it only ever *adds* files to OneDrive and never changes
  or removes anything on the phone.
- **Sync (optimise) and Archive stay off on every real album until the backup is complete and checked.** Optimise
  rewrites the phone's copy (the original then lives only in OneDrive); Archive moves the phone's copy to the
  trash. Both are for later, and both are exercised first on throwaway copies (Phase 5).
- The app is not trusted with the only copy of anything yet. Phase 0 makes a second copy of the recent, unbacked
  files that the app cannot touch.

## What is known and what is not

| | |
|---|---|
| Known | Backup, resume after a kill, skip-existing, restore, the deleted-files window and Archive were all verified on the Moto G (Android 16). A 1.9 GB video, 2,079-file wizard run and 6 hard kills were among them. |
| Not known | Anything on **API 37 / One UI 9**, the only place targetSdk 37 behaviour can be seen. Samsung Gallery's Recycle Bin on this build (confirmed on the Fold 4, API 36, twice). The scale: tens of thousands of files, ~90 albums, 100+ GB. How the phone behaves folded and unfolded mid-run. |
| Not known either | **Whether the app recognises a Samsung-made backup.** Skip-existing was verified on the Moto G against the app's own uploads and against files put there by hand, not against Samsung's sync. Samsung may lay folders out or name files differently, and it is the thing Phase 2 measures. |
| Known gaps | **Retry failed items** is not built: a file that fails 5 times is never picked again and there is no button (`resetFailures()` has no caller). **An edit saved over an optimised photo is never uploaded** (TASK-024). Neither matters for a Backup-only first run *except the first*, see step 1.1. |

## Phase 0 — before the app goes near the phone (Ian, about 30 minutes)

1. **Make a second copy of what is not backed up, that the app cannot touch.** The files from the last several
   weeks exist only on the phone. Copy them to the PC over USB (sort `DCIM/Camera` by date and take everything
   since the last backup), or use Smart Switch. This is the most important step and it is cheap: the recent
   weeks are a small fraction of the library. Do it before installing anything. Copying the whole of `DCIM` is
   better still, if there is room, since the old backup is a Samsung-made one this app has not yet verified.
2. **Look at the old backup in OneDrive on the web.** Which folder is it in, and how is it laid out (for example
   `Samsung Gallery/DCIM/Camera/...`)? Roughly how many files, and when does it stop? That answers "where does
   the backup end", which is the boundary between the two populations, and whether the app's default
   destination (`Samsung Gallery/DCIM`) points at it. **If the backup is somewhere else, the destination is
   set to it in the wizard, or the app will not find it.**
3. **OneDrive space, and the 1 TB that will end.** Read from the account on 20 Sept 2026: the personal account
   (the same kind as the Moto G's test account) has **Microsoft 365 Personal, 1 TB, from an order on Aug 27, 2026
   paid with a redeemed code ($0.00)**, and 8.5 GB used. Its end date is **not known** (the Manage buttons did
   nothing in Chrome, for Ian or for Claude). OneDrive's storage page lists only that plan and the free 5 GB, so
   **when the 1 TB ends the account falls back to 5 GB**, below what is already in it. The $1.99 Play
   subscription (Microsoft 365 Basic) is the main account's, not this one's. Before the Fold 8 backup is relied
   on, decide where it will live: renew the plan, or move it. Check the same account holds the old backup.
4. **Is Samsung's sync still running?** It stops 30 Sept. Running both at once is safe in principle (the app skips
   what is already there) but worth knowing about.
5. **Upload speed.** Run a speed test on the Wi-Fi to be used. It sets how long the gap takes: the Moto G measured
   about 3 MB/s, so 20 GB of recent video is under two hours, and at 1 MB/s about six. (It only matters for
   the whole library if the old backup turns out not to be found.)
6. **Charger and Wi-Fi.** Keep the phone plugged in and on the home Wi-Fi for the upload.

## Phase 1 — get the build ready (Claude, before Ian starts)

1. **Build the "Retry failed items" control** (small): a button on the Albums tab, or on an album's file list,
   that calls `resetFailures()`. On a library this size a few files failing is likely (a huge 8K video, a
   file that changes under the upload), and today they would sit unsent with no way to retry.
2. Full unit suite green; build the debug APK from the same debug keystore as the Moto G. MSAL's redirect
   depends on the signing hash, so the same keystore should sign in on the Fold 8 with no Azure change.
   If it does not, that is an Azure app-registration edit and is Ian's to do.
3. Decide how the build gets onto the phone: **wireless debugging** (Developer options; pair, then
   `adb mdns services` because the port changes each time; **always `adb -s <serial>`**) is preferred because it
   gives logs and the crash buffer. Sideloading the APK by hand works if not.
4. **A debug build and a later Play build are signed differently.** Installing one over the other needs an
   uninstall, which loses the ledger and, importantly, `album_preferences` (the one table that cannot be
   rebuilt: album modes would have to be set again). Uploaded files are re-found by name and size, so nothing
   is re-sent. Just know it before choosing to run a debug build for weeks.

## Phase 2 — install and look, changing nothing

1. Install, launch, and read `adb logcat -b crash -d` (a screenshot of a crashed app looks like a launcher).
2. Sign in **with the personal account** and grant the media permission. The app's sign-in is set to accept both
   work/school and personal Microsoft accounts (`msal_config.json`: `AzureADandPersonalMicrosoftAccount`, tenant
   `common`), but that is only the app's side. **The Azure app registration must also allow personal accounts.**
   The Moto G's test account may be a different kind (work or personal), so this is not yet proven for the
   personal one. If sign-in is refused, that is an Azure registration setting and is Ian's to change; it is the
   first thing to find out, before anything else in this phase. The app holds one account at a time
   (`account_mode: SINGLE`), so the Fold 8 and the Moto G do not interfere.
3. In the wizard choose **#4 — check cloud storage but do not back up any new files.** No uploads, no
   optimising, all albums Off. It only asks OneDrive what is there and counts what is on the phone.
4. Pick the folders (this is also the write grant): `DCIM` first, then the others deliberately.
5. Read the numbers it reports against what Ian expects: files on the phone per album, files "already in
   OneDrive". **This is the real test of the old backup:** nearly all of the old library should read as already
   in OneDrive, and only the recent weeks as not. If the reconcile says most of it is missing, the destination
   or the layout is not what the app expects: **stop here, do not press Sync now**, and look at the OneDrive
   folder names against what the app is searching. If it disagrees with what Ian knows to be true, stop and
   investigate.
6. Look at both themes and both screens (folded, unfolded) once. Unfold and fold mid-screen and check the
   app does not lose its place or crash.

**Stop if:** a crash, a wrong count Ian cannot explain, most of the old library reading as "not in OneDrive", or
anything that looks like the app touching files.

## Phase 3 — a small backup, watched

1. Set **one small album** to **Backup** (Screenshots is a good one). Leave the Default mode at **Off**.
2. Press **Sync now** and watch it upload. Check in OneDrive (web) that the count and sizes match, and that
   there is no `name 1.jpg` beside anything.
3. Force-stop the app mid-upload once, start it again, confirm it resumes and there is still no duplicate
   (the 20 Sept fix, verified only on the Moto G's test account). **This matters more here:** the fix depends on how
   OneDrive treats an interrupted upload session (a zero-byte placeholder filled in with `If-Match`), which was
   only ever seen on the test account. A personal account and a work account may not behave identically.
4. Check a few files by eye in OneDrive: they open, and they are the right size.

**Stop if:** a duplicate, an empty file in OneDrive, or a count that does not match.

## Phase 4 — the whole library, Backup only

1. Choose the bulk route: **Select all** on the Albums tab. **With the default mode left at Off, Select all
   writes Backup to every album** (Camera included). Do **not** change the default to Sync first, or Select all
   will write Sync everywhere except Camera.
2. **Sync now** while the phone is charging, on Wi-Fi, app open. Sync now is never held by the first-backup
   window (the window only gates the automatic runs). Leave the phone plugged in. Leave the app with Home or
   Back, **never swipe it out of Recents**: a swipe kills the process and the run stops until the app is next
   opened, whereas closing it the ordinary way keeps the process and the run carries on (measured on the
   Moto G, 151 files in five minutes with the app closed; see "Do not add a foreground service" in CLAUDE.md).
3. The automatic path is the backstop: default is 1am, six hours, charging. At 3 MB/s that is about 65 GB a
   night, at 1 MB/s about 21 GB. If only the recent weeks are uploading, one night is likely enough; if the
   old backup was not found and the whole library is going up, **count the nights against 30 Sept.**
4. Watch for "N failed" on the Albums tab. Use the Retry control from Phase 1. A file that still fails is
   noted by name, not ignored; it is on the PC copy from Phase 0.
5. Samsung One UI may put the app to sleep. It was not needed on the Moto G (TASK-021), but watch the first
   night; if uploads stop overnight, the "sleeping apps" list is the first thing to check.

**Done when:** every album card reads "N verified in OneDrive" for all N, OneDrive's folder counts match the
phone's per album, **no `name 1.jpg` sits beside an old file** (the sign that the skip missed something), and a
sample of files (a few photos, a large video, some from the Samsung-made backup and some the app uploaded)
downloaded from OneDrive match the phone byte for byte.

## Phase 5 — the risky features, on copies only

Only after Phase 4 is done and checked. **Create a new folder, `DCIM/GS_Test`, and put copies of a handful of
photos and videos in it.** Every risky feature is exercised there, never on a real album. The copies land in
OneDrive too; delete them there by hand afterwards (the app never deletes).

1. **Sync (optimise):** set `GS_Test` to Sync with the Settings switches on. Open an optimised photo in
   **Samsung Gallery**: does it show, does it carry the cloud badge, does the size read right?
2. **Edit an optimised copy in Samsung Gallery and save.** This is the TASK-024 question, on the phone it
   applies to. Find out what Samsung's Save does (overwrite or save a copy) before it matters. Ian to decide
   the fix (upload an edit as a new file) once the answer is known.
3. **Restore** an optimised copy back to full size; the deleted-files window (Ask policy) with a deleted copy.
4. **Archive** `GS_Test`: confirm Samsung Gallery's **Recycle Bin** holds the trashed files on API 37, and that
   the files are recoverable from it. This is the one check that has only ever been made on API 36.
5. **Camera album's manual optimise** (age list, swipe to keep): the only part never seen on a Samsung.
6. The Android write dialog for a file outside a granted folder (the Camera album's optimise), untested anywhere.

Only if all of that is clean does Ian consider setting a real album to Sync or Archive, one album at a time.

## Phase 6 — running alongside, then cutting over

- Samsung's sync stops on **30 Sept**. Until then Ian's Samsung sync (if it is running) and the app can run in
  parallel, which is what the project's cutover rule asks for (two weeks alongside). There are ten days left,
  so it will not be a full two weeks; the PC copy from Phase 0 is what stands in for the missing time.
- Daily glance: Albums tab counts, "N failed", the Recents card, the crash buffer if adb is connected.

## Rollback

- **Uninstall the app.** Nothing on the phone was changed by Backup mode, and nothing in OneDrive was removed
  (the app never permanently deletes anything, and Phase 2 to 4 delete nothing anywhere).
- Album modes and the ledger are lost with the uninstall; the uploaded files are found again by name and size.
- Anything Archive trashed (Phase 5 only, on copies) is in the phone's Recycle Bin for 30 days.

## Rules that stay in force on this phone

- **Leave the deletion policy at Leave** (Settings, *When you delete a photo/video from this phone*) for the whole
  trial. It is the default. Under *Ask* the app can move a OneDrive copy to the recycle bin (only after a tick
  and a confirmation, and only for a file deleted from the phone), and on a personal drive that also holds
  Ian's other things there is no reason to have that switched on while the app is still being proven.
- Nothing from Phase 5 goes near a real album. Its copies land in the personal OneDrive under `GS_Test`; they
  are cleaned up by hand in OneDrive afterwards (the app never deletes them).
- **Never experiment on the Fold 8.** New behaviour is tried on the Moto G first, then here.
- Always pin adb with `-s <serial>` (two phones are usually attached).
- Nothing that removes a local file (Archive) or shrinks one (Sync) is switched on for a real album until
  Phase 4 is checked and Phase 5 is clean.
- Every failure or oddity is recorded in `.claude/MILESTONES.md` with the device named (Fold 8, API 37).

## Open questions for Ian

1. Where is the old backup in OneDrive (folder and layout), how was it made (Samsung's sync?), and roughly when
   does it stop? Is Samsung's sync still on?
2. Which OneDrive account is it in, and does it have room for the recent weeks?
3. Wireless debugging on the Fold 8, or sideload by hand?
4. Will the recent, unbacked weeks be copied to the PC first? (Recommended, and quick.)
5. Is the Moto G's test account a personal or a work/school account? (Decides whether the personal account is
   the same kind the app has been proven on.) Does the Azure registration allow personal accounts?
