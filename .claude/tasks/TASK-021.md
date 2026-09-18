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

## One more observation, same day, and it points the same way

Ian watched the Camera album's verified count climb live on the Albums tab — *"1 to 8 verified to 8 of
8 verified"* — as the photos backed up. **The app was open the whole time**, because watching it is
what he was doing.

That is a data point on the "works while open" side of the question, from the same device and the same
batch of photos. Taken with step 6 above, the pair is close to a controlled comparison: identical
album, identical album mode, identical file types, differing only in whether anyone was looking at the
app. Worth reproducing deliberately — same photos, once with the app foregrounded and once without —
before spending time in the code.

Note also what it proves *works*: the reconcile, the per-album tally and the live UI update are all
correct and prompt under those conditions. Whatever is wrong is upstream of them, in what wakes the
worker.

## Root cause, found 18 Sept 2026

Two factory-reset repro runs on the Moto G, the second with continuous `adb logcat` capture (the first
lost the critical window to buffer rotation). Full detail in `.claude/MILESTONES.md`, 18 Sept entries.

**What actually happens:** backgrounding the app with Home (no swipe) is enough for something on this
device — confirmed to be an automatic `remove task` kill, the same AOSP path a Recents swipe goes
through, firing on its own within ~70–90 seconds — to kill the process. The next time a content-trigger
job needs to run (a new photo arrives), it cold-starts into a fresh process, and WorkManager's own
`ForceStopRunnable` — which runs on every process start to decide whether the app was force-stopped —
intermittently (not always; confirmed inconsistent across four cold starts in one session) concludes it
was, and cancels the job it just started rather than running it. The replacement it schedules only
watches for the *next* change; it does not retroactively act on files already outstanding. This is why
the bug looks intermittent and self-healing — a *later* trigger, or the 6-hour periodic net, eventually
sweeps up whatever an earlier one dropped — and why the manual "Right now"/"Sync now" paths have never
failed: they enqueue their work after the process is already up, outside the window where
`ForceStopRunnable`'s startup check can cancel it.

**Diagnostically confirmed, not shippable as a fix:** adding the app to the platform's Doze/battery
whitelist (`dumpsys deviceidle whitelist +com.gallery.sync`) stopped the kill entirely across a 3-minute
observation — the process still froze (`moto_freezer`) but never escalated to `remove task`, and a
subsequent batch uploaded cleanly with zero `ForceStopRunnable` false positives. This confirms staying
off the kill path is what matters, but this Moto G has no per-app "Unrestricted" battery screen (only a
device-wide "Adaptive Battery" switch), and the only app-side route onto that list is the
`REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` permission Ian ruled out 5 Sept as Play-review risk for an
unqualifying use case. Not pursued further.

Also considered and ruled out: user-initiated data transfer jobs (`setUserInitiated(true)`, API 34+) —
these can only be scheduled while the app is visible or can launch an Activity from the background,
which is exactly the condition that doesn't hold when a photo arrives with the app closed. They fit the
manual paths, which are not the ones that are broken.

## The fix — spec, approved by Ian, 18 Sept 2026

**Make every cold start self-healing, not just the ones whose own WorkSpec survives.**
`GallerySyncApplication.armAutomaticSync()` already runs before any other component in the process, on
every single process start, for any reason — confirmed directly, since it precedes `MediaProvider`'s
own `onCreate` in every capture. Today it only re-arms the *future* watch
(`BackupScheduling.enable()` → `enqueueContentTriggered()`). It never checks what's already
outstanding.

Add a call to `BackupScheduling.enqueueContinuation(workManager, preferences.allowMeteredNetwork)`
alongside the existing `enable()` call, gated the same way (`preferences.isAutomaticEnabled`). This
enqueues a `BackupWorker` run under `CONTINUATION_WORK` — a fresh enqueue, made *after* this process's
own `ForceStopRunnable` pass has already completed, so it is not subject to the cancellation the
content-trigger `WorkSpec` intermittently suffers. `doWork()` always runs a full `refreshLedger()` +
`uploadPending()` regardless of entry path, so this catches anything outstanding — a photo whose own
content trigger got silently cancelled included — the same day, on the very next time the app process
is touched for any reason, rather than waiting for a later trigger or the 6-hour net.

**Not a targeted fix for the cancellation itself** — `ForceStopRunnable`'s internal mechanism on
WorkManager 2.11.2 was never pinned down (the AlarmManager-canary theory was checked directly and
withdrawn; see MILESTONES). This works around it structurally: it doesn't matter whether the
content-trigger `WorkSpec` survives, because every process start now gets its own independent chance to
catch up.

**Known cost, accepted rather than engineered around:** this adds a real backup check (ledger refresh +
cloud reconcile) to every cold start, including ones triggered for unrelated reasons — another app
querying GallerySync's `ContentProvider`, for instance. Cheap when nothing is pending (observed under a
second in every capture today), not free. No throttling added: `enqueueContinuation` already collapses
concurrent calls via `ExistingWorkPolicy.REPLACE` on a unique work name, and the codebase has no
existing pattern of cooldown-gating this class of check — matching "Sync now" costing the same each time
it's tapped.
