# TASK-019 — Pause and resume a run, and a button that tells the truth

Milestone: v0.3 — the Albums hero
Requested by: Ian, 28 Aug 2026 (early hours)
Depends on: `BackupScheduling`, `BackupWorker`, FIX-001's stop path
Blocks: nothing, but it closes a control gap found during the 28 Aug scale test

## What Ian asked for

> *"while a backup or Sync is in progress can you change the Sync Now button into a 'Syncing' and
> around the outside of the button a overall progress indicator - or make the button read 'XX%
> Synced' and during Syncing change the Rescan button to a Pause Sync button"*

Then, on what Pause means:

> *"a Pause button the user can press to pause the Syncing/Backup - which then turns into a resume
> button"*

Then, on the timing:

> *"Have the Pause initialize only after the file thats currently uploading finishes"*

And then, having thought about it, the question that overturned it:

> *"On the Pausing - do we want to complete the file or rollback? rollback would give the user an
> INSTANT pause and not have to wait"*

Rollback, on the evidence below. The reason the first answer looked right was a false premise about
what interrupting an upload costs.

## Why this is not cosmetic

Two defects sit behind the request, and both were found on hardware on 28 Aug rather than by reading
the code.

### 1. An automatic run cannot be stopped at all

`isRunning` is set only from the manual work chain — `MANUAL_WORK`, observed in `BackupViewModel`.
`observeBackgroundWork()` watches the three automatic chains (`CONTENT_TRIGGER_WORK`,
`CONTINUATION_WORK`, `PERIODIC_WORK`) and updates the *status text* but never `isRunning`.

The Sync now button relabels itself to "Stop sync" only when `isRunning` is true. So:

- during an automatic run the button reads **"Sync now"** throughout — observed on the Moto G while
  21 GB uploaded
- `canRunBackup` is `isRunning || pendingCount > 0`, so it is also **enabled**, and pressing it
  queues a *manual* run alongside the automatic one
- automatic sync is **on by default**, so the runs a user most wants to interrupt — overnight, on
  mobile data — are exactly the ones with no control attached

### 2. Progress is reported per batch, not per job

The status reads "x of 25" because `DEFAULT_BATCH` is 25. At 3,327 files that is roughly 133 batches,
and the user sees a counter that resets over and over with no sense of the whole. It was adequate at
149 files. It is misleading at library scale.

## The design

### The controls

| State | Left | Right |
|---|---|---|
| Idle | **Sync now** | **Rescan** |
| Running | **"Syncing 37%"**, fill sweeping behind the label | **Pause** · **Stop** |
| Paused | **"Paused at 37%"** | **Resume** · **Stop** |

Three controls while a run is live, not two. An earlier draft treated the existing two-button row as
a constraint and spent two sections arguing Pause and Stop into one control; Ian pointed out on
28 Aug that the row is a choice, not a given. Rescan is a once-in-a-blue-moon control and stands
down while a run is live.

**The left button stops being a control.** Today it is `Sync now` and relabels to `Stop sync`, which
is why Stop appears to have nowhere to live. Under this design it reports and nothing more.

### The controls shrink to icons when the row cannot hold words

Measured 28 Aug 2026:

| Screen | Pixels | Density | Width |
|---|---|---|---|
| Moto G 2026 | 720 × 1604 | 1.625 | **443 dp** |
| Fold 4 — inner | 1812 × 2176 | 2.625 | **690 dp** |
| Fold 4 — **cover** | 904 × 2316 | 2.625 | **344 dp** |

The Moto is comfortable: roughly 370 dp of row after the card's padding, about 118 dp per pill, which
holds "Syncing 37%", "Pause" and "Stop" without strain. **The cover screen is the constraint** —
100 dp narrower, leaving about 85 dp per pill against a `ModePillMinWidth` of 96 dp that exists so
"Archive" survives a large font scale. Three labelled pills do not fit there.

So the labels drop to icons:

| Available width | Right-hand controls |
|---|---|
| Comfortable | **Pause** · **Stop** as labelled pills |
| Compact | two icon buttons |

Three requirements on that, none optional:

- **Use `isCompactWidth()`**, which already keys on font scale as well as width. Its own comment
  records why: a 400 dp screen at 2× has the same problem as a 320 dp screen at 1.6×, and keying on
  width alone calls the second one comfortable.
- **Draw the icons, do not type them.** Add `Pause` and `Stop` to `SignalIcons` as stroked vectors
  beside `Albums` and `Check`. Glyph characters scale with font size rather than layout and depend on
  the font carrying them; every other icon in this app is a vector for that reason.
- **`contentDescription` is the same string the wide layout prints**, so the two cannot drift and
  TalkBack still announces "Pause" and "Stop".

**Verify folded.** MILESTONES already carries a 24 Aug 2026 entry titled "the layout breaks folded",
so the cover screen has caught this app out once. It is also the device leaving soonest.

### Percent is of bytes, not files

Decided on the 28 Aug measurements. At one point the Moto had uploaded 164 of 3,327 files — **5% by
count, 37% by bytes** — because eighteen of those files were video. By file count the bar crawls for
two hours and then leaps. Bytes track what is actually happening.

`SUM(sizeBytes) WHERE state='UPLOADED'` over `SUM(sizeBytes)` for albums in scope. The ledger already
holds both.

### Pause is instant, and rolls back the file in flight

**Decided by Ian, 28 Aug 2026, after the first draft got this wrong.**

The first draft had Pause wait for the current file to finish, on the grounds that interrupting
mid-file would abandon the upload session and re-send the whole file on resume. **That is false.**
`ChunkedUploader.resumeOffsetOf` asks Graph for the ranges already accepted and resumes from that
offset — proven on the Fold 4, 26 Aug 2026, where a 1,938 MB video force-stopped at ~50% resumed at
byte 1,069,547,520 rather than at zero.

So interrupting costs nothing that survives a resume, and waiting costs up to seven minutes of
transfer the user explicitly asked to stop — landing hardest in the cases Pause is actually reached
for: a data cap about to break, Wi‑Fi about to be left, a battery about to die.

**The one real cost** is a pause outliving the upload session, which expires roughly fifteen minutes
after its last chunk. Then that single file restarts from zero on resume. One file, not the batch,
and only after a long hold. Not worth surfacing to the user.

Files under `SMALL_FILE_THRESHOLD_BYTES` (4 MB) go as a single PUT with no session at all, so a pause
during one either completes or is discarded. Sub-second at any realistic rate.

**This removes the "Pausing…" state entirely**, along with the dead button and the
second-tap-to-abort escape hatch the earlier draft needed. Pause means paused, at once.

### Pause and Stop are different questions about the *next* run

Both end the current run immediately. The difference is what happens afterwards, and it is the whole
reason they are two controls:

| | Ends the current run | Automatic runs after it |
|---|---|---|
| **Stop** | yes | **stay armed** — the next trigger picks up: new media, the six-hourly net, next launch |
| **Pause** | yes | **suppressed** until Resume is pressed |

Two real intents. *"Not right now, I am walking out of the door"* is Stop, and the user expects it to
carry on later unasked. *"Not until I say so, I am abroad and data is expensive"* is Pause. Offering
only one forces everyone into whichever was built — and if that is Pause, a library quietly stops
protecting itself while looking exactly like one that is up to date.

**Stop means the same thing in both states.** Pressed while paused it also clears the paused flag,
because the hold is precisely what it is undoing. So Stop is always "end this run, return to normal
automatic behaviour", and Resume is always "start again now".

That also softens the expiry question below: a pause no longer strands anyone, because the way out
sits next to Resume rather than having to be remembered.

### Paused is a persisted preference, not a cancellation

The load-bearing constraint. Backup has three automatic triggers — armed on every launch
(`GallerySyncApplication.armAutomaticSync`), content-triggered on new media, and a six-hourly safety
net. **If Pause merely cancels the running chain, the next trigger restarts it and the pause lasts
minutes.**

So:

- `BackupPreferences.isPaused`, persisted in DataStore. Set by Pause, cleared by **both** Resume and
  Stop — Stop undoes the hold as well as ending the run
- `BackupWorker.doWork()` returns early when it is set, before doing any transfer
- `BackupScheduling` does not need to tear down the triggers; the worker declining is enough, and it
  keeps the schedule intact for Resume
- Resume clears the flag and kicks a run immediately rather than waiting for the next trigger

### Pausing mid-file, mechanically

Setting the flag cancels the running work. `ChunkedUploader` stops between chunks, the persisted
`uploadSessionUrl` and its expiry stay on the ledger row, and the next run resumes from the accepted
offset. This is the same path a killed process already takes, which is why it is proven rather than
new.

The interrupted row stays `PENDING` with its session intact. Nothing marks it failed, and
`attemptCount` must not be incremented — a user pausing is not an error, and counting it as one
would eventually push the file into the backoff a real failure earns.

## Acceptance

- During any run — automatic or manual — the Sync now button shows overall progress as a percentage
  of bytes, not of files, and not a per-batch count
- The percentage is monotonic across the whole job and does not reset between batches
- Pause is offered during automatic runs. The regression test is an automatic run started by the
  content trigger, paused from the UI without the user ever pressing Sync now
- Pressing Pause stops transfer immediately and the pair becomes Resume and Stop. There is no
  intermediate state and no waiting
- Pressing Stop ends the run and leaves automatic sync armed: the regression test is Stop followed by
  new media appearing, which must start a run without the user asking
- Pressing Stop while paused clears the hold, so the next trigger runs normally
- A file interrupted by Pause resumes from its accepted byte offset, not from zero, provided the
  session has not expired. The regression test is pausing partway through a large video and checking
  the resume log reports a non-zero offset
- An interrupted file stays `PENDING` with its session intact, is not marked failed, and does not
  have `attemptCount` incremented
- A paused app stays paused across a process restart, an app launch, new media appearing, and the
  six-hourly trigger — all three arming paths must respect it
- Resume starts a run immediately rather than waiting for the next trigger
- Pressing the progress button during a run does not queue a second run
- `WHEN_THINGS_HAPPEN` in `SetupTopic` gains a sentence saying a paused run stays paused until
  resumed — no trigger restarts it. It cannot be written honestly until this ships
- The labelled pills collapse to icons when the row is compact by width **or** by font scale, and the
  icons carry the same strings as content descriptions
- Verified on hardware in both themes, per CLAUDE.md, and on the Fold's cover screen at an increased
  font scale — the narrowest surface this app runs on

## Open, for Ian

- **Does Pause expire?** Less pressing now that Stop sits beside Resume as a visible way out, but a
  user who pauses and closes the app can still forget. TASK-016's DELAY control (1hr / 12hr / 1day)
  is a precedent for a timed hold if an indefinite one still feels too sharp.
- **Where does Rescan go** while a run is live — hidden, or demoted into a menu.
- **Does the paused state survive a sign-out**, or is it cleared with the rest of the session.
