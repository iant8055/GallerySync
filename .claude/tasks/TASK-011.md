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
- A ceiling can be set and persists across restarts
- The worker runs on its own and brings usage under the ceiling using proxies only
- It reports what it did, and says plainly when it cannot reach the target
- Nothing unverified is ever touched; no file is ever deleted
- Verified on hardware in both themes, per CLAUDE.md

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

### Open — WorkManager cannot carry the grant
`BackupWorker` is a `CoroutineWorker` scheduled through WorkManager, and **WorkManager exposes no
way to attach `ClipData` to the underlying `JobInfo`.** The two documented carriers are
`Context#startService` and `JobInfo.Builder#setClipData`, and neither is reachable through the
WorkManager API. So the *applying* step probably cannot live in a WorkManager worker at all.

Two architecturally distinct paths, with long-term consequences either way — **needs Ian's call
before building**, per the escalation rule in CLAUDE.md:

1. **WorkManager decides, a foreground service applies.** Keep scheduling where the rest of the
   app already has it; when a run is warranted, start a foreground service with an Intent carrying
   the ClipData grant. Consistent with existing scheduling, but adds a second execution mechanism
   and a user-visible notification while it runs.
2. **Raw JobScheduler for this job only.** `JobInfo.Builder#setClipData` is the documented path and
   needs no service or notification. But it sits outside WorkManager, so this one job does not
   share the app's existing constraints, backoff and observability.

Verify the grant actually survives on hardware before committing to either. The documentation says
it should; the Samsung trash behaviour is a standing reminder that the documentation and a Galaxy
device do not always agree.

## Notes for whoever picks this up
- `ProxyApplier.candidates()` returns eligible photos largest-first and already filters rows whose
  local file is gone. It caps at 2000 URIs per MediaStore's limit.
- `ProxyApplier.createWriteRequest()` already builds the IntentSender for a batch — the grant-pool
  work extends it rather than replacing it.
- `ProxyGenerator` skips anything already at or under 2048px, so the reclaimable total is smaller
  than the candidate byte count suggests. Do not size the deficit against `proxyCandidateBytes`.
- The ceiling is a user setting and belongs with `BackupSettings`, alongside the existing
  preferences, not in a new store.
