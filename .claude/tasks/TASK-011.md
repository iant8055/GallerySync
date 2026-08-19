# TASK-011 — Storage budget

Milestone: v0.3.0 — "the phone stops filling up on its own"
Depends on: TASK-010 (photo proxies), verified on hardware 18 Aug 2026
Blocks: nothing, but it is what makes v0.3 a product rather than a button

## Goal
The user sets a ceiling once. A background worker keeps the phone under it by proxying the
largest verified photos, without being asked again.

Everything works today only because someone taps **Optimise**. That is a demo, not the
"set up and forget" behaviour the design principle promises.

## Scope — photos only in this pass

Reclaiming space for a photo is **entirely local work**: the original is already in OneDrive, so
the worker downscales the local copy and nothing leaves the device. No network, no deletion, and
the photo stays in the gallery. That makes this a safe feature to run unattended.

**Video eviction is explicitly out of scope**, and belongs with the rolling-window item. A video
cannot be proxied, so the only lever is removing the local file — which makes it disappear from
the gallery, and runs straight into the trash rules in CLAUDE.md and the Samsung behaviour that
deleted files outright. Different risk, different task, different decision.

## Decision — made by Ian, 18 Aug 2026

**A free-space floor, user-selectable, defaulting to 20 GB free.**

Not a cap on GallerySync's own footprint. "My phone is full" is the problem people actually
have, and it is felt against total free space, not against what any one app occupies.

The value is a user setting rather than a constant. 20 GB is a sensible default on a modern
phone, but it is not a fact about anyone's device — on a smaller phone it is a large fraction
of the whole, and on a big one it is barely a reserve.

Two consequences that follow from a floor and must be built in from the start:

- **The target is a deficit, not a total.** Work to do is `floor - currentFreeSpace`, so the
  worker usually does nothing. Only when free space drops below the floor does it act, and only
  by enough to get back over it. Hard rule 4 already says stop at the ceiling, not past it.
- **The trigger can be something GallerySync did not cause.** Another app growing, or a few 8K
  videos, can push free space under the floor and cause photos to be optimised. The user will
  experience that as unprompted. The UI has to be able to say *why* a run happened, or it reads
  as arbitrary — which is exactly the objection that was weighed against the floor.

### The floor may be unreachable, and that is a normal outcome
Videos are out of scope here and are the largest files on the device — Ian's own are 103, 163
and 178 MB apiece. Proxying photos cannot always close a deficit that video created. Hard rule 2
already forbids deleting to reach the number, so the honest behaviour is to proxy what it can,
stop, and say plainly that the target was not met and what is holding it. Do not treat this as an
error state; on a video-heavy phone it is the expected one.

## Hard rules

1. **Never proxy anything not verified in OneDrive.** Same definition as TASK-010: Graph confirmed
   it and the byte size matches. `BackupEntryDao.proxyCandidates()` already expresses this.
2. **Nothing is deleted.** This task only ever downscales. If the budget cannot be met by
   proxying alone, it stops and says so — it does not start removing files to reach the number.
3. **Largest first.** Most space reclaimed for the fewest photos touched, which is also the
   fewest chances to get something wrong.
4. **Stop at the ceiling, not past it.** Over-optimising to build headroom degrades photos nobody
   asked to degrade.
5. **Respect per-album "keep originals"** once that exists. Until then, no album is exempt, and
   that limitation is worth saying out loud in the UI.

## Where it goes
Settings → Storage, beneath the existing verified count and "Move to backup". All three answer
the same question: how much of this phone is in use, and what is safe to reclaim.

## Acceptance
- A floor can be set and persists across restarts; the default is 20 GB free
- The worker runs on its own and brings free space back above the floor using proxies only
- It reports what it did, and says plainly when it cannot reach the target
- Nothing unverified is ever touched; no file is ever deleted
- The user is notified when free space drops below the floor, in each of the three states below
- Repeated runs finding the same situation do not re-notify
- With notifications denied, Settings still shows the same state and the feature remains usable
- Verified on hardware in both themes, per CLAUDE.md

## Notification — required, and it is also the consent mechanism

The user is told when free space drops below their floor. Requested by Ian, 18 Aug 2026.

This is not a separate feature bolted on. Because a background worker **cannot obtain write
consent by itself** (see below), the notification is the only way the app can ask for the next
batch. Design them as one thing, not two.

### Three states, three different messages
The worker checks free space against the floor and lands in exactly one of these:

1. **Below the floor, grants in hand.** It proxies until back above the floor, then reports what
   it did — how many photos, how much reclaimed. Low priority: this is the case where the feature
   worked, and it should not interrupt anyone.
2. **Below the floor, grant pool empty.** The actionable one. "Your phone is below *N* GB free.
   Approve the next batch of photos to optimise." Tapping opens the Activity and launches
   `createWriteRequest`. Without this the feature simply stops, silently, the moment the pool runs
   dry — which on a large library is soon.
3. **Below the floor, nothing left to proxy.** Everything eligible is already optimised and the
   floor is still unmet. Say so plainly, and say what is holding it — on a video-heavy phone that
   is video, which this task deliberately will not touch. **Expect this state on Ian's own device.**

### Do not nag
Free space hovers around a threshold; a naive check notifies every run.

- Re-notify only when the situation *changes* state, not on every pass that finds the same thing.
- Apply hysteresis: having crossed below the floor, do not re-notify until free space has risen
  meaningfully above it and fallen again. A single margin constant, not a second user setting.
- State 3 must not repeat until something could plausibly have changed — new photos backed up, or
  the floor itself edited.

### The notification cannot be the only channel
`POST_NOTIFICATIONS` is a runtime permission on API 33+, and the user can refuse it or turn the
channel off later. If that happens, and the grant pool empties, **automatic space management dies
silently** — the app would be waiting on an approval it has no way to ask for.

So the same three states must be visible in Settings → Storage as ordinary UI. The notification is
a prompt for something already legible in the app, never the sole carrier of it. Check what the
screen says with notifications disabled, not only with them working.

### Permission — flagged for Ian
`POST_NOTIFICATIONS` is a new manifest permission and appears on the Play listing, which CLAUDE.md
lists as an escalation. Raised here rather than assumed: the request implies it, but adding a
permission to the listing is Ian's call to make explicitly.

Note it is likely needed either way — a foreground service, which is one of the two paths for the
applying step below, shows a notification while it runs.

## Consent — settled shape, and the constraint it puts on everything else

`MediaStore.createWriteRequest` can only be launched from an Activity, so **a background worker
cannot obtain consent on its own.** The platform's documented way through is to hand the granted
URIs to background work via `ClipData`, carrying `FLAG_GRANT_WRITE_URI_PERMISSION`, so the grant
outlives the Activity that obtained it.

That works, but the 2000-URI cap means a single grant cannot cover a large library forever. The
honest description of the feature is therefore:

> **"Approve a batch occasionally", not "never asked again".**

This is a platform limit, not a design choice, and it is better absorbed now than discovered
halfway through building. It changes three things:

- **The UI must not promise set-and-forget.** Say up front that the phone will ask again as the
  library grows. A prompt the user was told to expect is maintenance; the same prompt unannounced
  reads as the app being broken.
- **A grant pool becomes a real object**, not an implementation detail. Ask in the Activity for a
  batch, largest-first; persist those URIs; let the worker consume them. When the pool empties and
  the floor is still unmet, the outcome is "needs approval", which is a distinct state from "done"
  and from "cannot reach the target".
- **The pool goes stale.** Largest-first is computed at grant time, and new photos arrive after it.
  Re-asking periodically is the design, so build for it rather than treating it as an edge case.

### Where the applying step runs — still open

Ian, 18 Aug 2026: *"applying steps should be running in the background."* Taken as read. It does
not settle the question on its own, because **every option below runs in the background.** None
requires the app to be open or the user to be watching.

"Foreground service" is Android's own misleading name. A foreground service has no UI and runs
while the app is closed; "foreground" refers to its scheduling priority and the persistent
notification it must show, not to the user being present. What genuinely cannot be backgrounded is
the **consent dialog** — that is the platform constraint recorded above, and it holds whichever
option is chosen.

`BackupWorker` is a `CoroutineWorker` scheduled through WorkManager, and **WorkManager exposes no
way to attach `ClipData` to the underlying `JobInfo`.** The two documented carriers are
`Context#startService` and `JobInfo.Builder#setClipData`, neither reachable through WorkManager.
So the applying step cannot simply live in the existing worker.

1. **WorkManager decides, a foreground service applies.** Keeps scheduling where the rest of the
   app has it. But **Android 12+ forbids starting a foreground service from the background**
   (`ForegroundServiceStartNotAllowedException`), which is exactly the situation here — a worker
   waking on its own and starting the service. `WorkManager.setForeground()` is the sanctioned way
   around that restriction, but it promotes execution priority; it still does not let the job carry
   a `ClipData` grant. This path looks blocked on both counts and should be confirmed dead before
   anything is built on it.
2. **Raw JobScheduler for this job only.** `JobInfo.Builder#setClipData` is the documented carrier
   and needs no service or notification. The genuine background path. Costs: it sits outside
   WorkManager, so this job does not share the app's constraints, backoff and observability, and
   Hilt injection into a `JobService` has to be wired by hand rather than via `@HiltWorker`.
   Unverified: whether a `ClipData` URI grant survives a reboot on a persisted job. If it does not,
   every restart empties the pool and re-prompts the user.
3. **Background detection, user-initiated applying.** The worker does what it can already do
   without consent — watch free space, compare against the floor, fire the notification. Applying
   happens when the user taps through, in the Activity that already has the grant. No pool, no
   `ClipData`, no second execution mechanism, no reboot question.

**Recommendation: option 3**, and it is worth weighing against the instruction rather than around
it. The pool-and-`ClipData` machinery exists solely to apply a batch the user approved *earlier*.
But the 2000-URI cap and the staleness of a largest-first ordering mean a stored grant cannot cover
future runs anyway — the user has to be asked again regardless. So the machinery buys deferral of
work by minutes, in exchange for the one genuinely uncertain mechanism in the whole design.

Under option 3 the part Ian asked for is still background: noticing the phone is filling up and
saying so happens with the app closed. Only the rewriting waits for the tap that was always going
to be required.

If background applying is wanted anyway, **option 2 is the path** — but verify the reboot
behaviour on hardware first. The documentation says the grant should carry; the Samsung trash
behaviour is a standing reminder that the documentation and a Galaxy device do not always agree.

## Notes for whoever picks this up
- `ProxyApplier.candidates()` returns eligible photos largest-first and already filters rows whose
  local file is gone. It caps at 2000 URIs per MediaStore's limit.
- `ProxyApplier.createWriteRequest()` already builds the IntentSender for a batch — the grant-pool
  work extends it rather than replacing it.
- `ProxyGenerator` skips anything already at or under 2048px, so the reclaimable total is smaller
  than the candidate byte count suggests. Do not size the deficit against `proxyCandidateBytes`.
- The ceiling is a user setting and belongs with `BackupSettings`, alongside the existing
  preferences, not in a new store.
