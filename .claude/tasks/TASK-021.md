# TASK-021 — New photos do not sync until the app is opened

Milestone: after the wizard defects are closed — Ian's explicit ordering, 6 Sept 2026
Requested by: Ian, 6 Sept 2026
Depends on: nothing. **Do not start before the Step 7 estimate and the trigger-guard work are
verified and closed** — Ian: *"this needs to be looked into AFTER all the Wizard issues are fixed."*

## What Ian observed, in his own sequence

Moto G, 6 Sept 2026:

1. Took several photos and videos. The **Camera folder appeared on the Albums tab**, mode `Off`,
   which is the configured default and correct.
2. **He set Camera to Sync himself.** Photos and videos then backed up and optimised as designed.
3. **Verified in the Files app and in OneDrive — everything correct.** This half works.
4. Took several more photos and videos.
5. **Did not open the app.** Waited several minutes.
6. Checked via the Files app: **nothing backed up, nothing optimised.**

His conclusion: *"it looks like when the app is running it does not auto sync/backup files without
opening the app."*

This strikes at the product's core claim. CLAUDE.md: *"Set up and mostly forget. The user chooses
once and the worker maintains it."* And: *"Ongoing sync needs no service at all; the content trigger
and the periodic net are uncapped JobScheduler work."* If step 6 is the steady state, that sentence
is wrong and the app only works while someone is watching it.

## Two candidate causes. Rule out the second one first — it is cheap and it is ours

### 1. App-standby dispatch batching, already measured

The 5 Sept afternoon MILESTONES entry recorded a user-scheduled job **ready and undispatched for 26
minutes 42 seconds**, every constraint satisfied, in bucket RARE — and dispatched *within one second*
of Ian opening the app. Three times in one session. The demotion lands within seconds of the app
having nothing left to run, which is exactly the gap a content trigger lives in.

**"Several minutes" sits well inside that window**, so this observation may be the known problem
rather than a new one. What separates them: whether the work eventually ran on its own. A job held by
batching still runs later; a trigger that was never armed never runs at all.

**Check first:** `adb shell dumpsys jobscheduler | grep -A 30 com.gallery.sync` for a ready,
undispatched job, and `dumpsys usagestats | grep STANDBY_BUCKET_CHANGED package=com.gallery.sync`
for the bucket and the reason code. If a job is sitting ready, this is #1 and the routes are the ones
in the 5 Sept evening entry — `setAndAllowWhileIdle` for delays, user-initiated data transfer jobs —
not a foreground service, which is closed off in CLAUDE.md.

### 2. The 6 Sept trigger guard — our own change, made the same day

`BackupWorker` now declines a content-triggered run when `BackupScheduling.optimiseChainLive()` is
true, to stop the app scanning the library because it rewrote it. **If an optimise chain can sit
`ENQUEUED` indefinitely** — blocked on a constraint, or waiting on a write request that never comes —
then every content trigger is declined for as long as it does, and new photos are ignored exactly as
described.

The guard re-arms the trigger before returning, so the watch is not lost, and the six-hourly periodic
pass ignores the guard entirely. That bounds the damage but would not stop the symptom Ian saw over
"several minutes".

**Check:** `WorkInfo` state for `gallery-sync-optimise` at the moment new photos are ignored. If it is
`ENQUEUED` or `BLOCKED` rather than `RUNNING`, the guard is too broad and should test for `RUNNING`
only, or carry a deadline.

Note the ordering: Ian's step 2 succeeded, and the optimise chain from it would have been live around
then. If that chain never reached a finished state, step 6 follows directly.

## What "fixed" looks like

A photo taken with the app closed reaches OneDrive without anyone opening the app, on a phone that has
been idle long enough to be in a restrictive bucket. Measured on hardware with the logcat buffer
raised first (`adb logcat -G 16M` — the stock 256 KiB holds about 97 seconds and will not survive the
wait), and with the app never brought to the foreground during the test, since doing so is what
dispatches the job and destroys the measurement.
