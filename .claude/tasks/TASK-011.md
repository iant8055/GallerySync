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

## Open decision — Ian
What the ceiling is measured against. This changes the design, not just the wording:

- **"Use at most N GB"** — a cap on what GallerySync's managed files occupy. Predictable, and
  unaffected by whatever else fills the phone. But it does not solve "my phone is full" if
  something else is eating the space.
- **"Keep at least N GB free"** — a floor on device free space. Matches how people actually
  experience a full phone. But other apps growing can trigger the user's photos being optimised,
  which can feel arbitrary and unexplained.

Recommendation: the free-space floor, because "my phone is full" is the real problem. Needs Ian's
call before building.

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

## Notes for whoever picks this up
- `ProxyApplier.candidates()` returns eligible photos largest-first and already filters rows whose
  local file is gone. It caps at 2000 URIs per MediaStore's limit.
- Applying proxies needs user consent via `MediaStore.createWriteRequest`, and the dialog can only
  be launched from an Activity. **A background worker cannot obtain that consent by itself**, which
  is the first design problem to solve, not an implementation detail.

  There is a documented way through, from the `createWriteRequest` docs in the platform source:

  > Permissions granted through this mechanism are tied to the lifecycle of the Activity that
  > requests them. If you need to retain longer-term access for background actions, you can place
  > items into a ClipData or Intent which can then be passed to `Context#startService` or
  > `JobInfo.Builder#setClipData`. Be sure to include any relevant access modes you want to
  > retain, such as `FLAG_GRANT_WRITE_URI_PERMISSION`.

  So the shape is: ask once in the Activity for a batch of URIs, then hand those URIs to the
  background work via ClipData so the grant survives. Note the 2000-URI cap on a single request —
  a large library needs more than one grant, so the budget cannot be a one-time question answered
  forever. Design for re-asking periodically rather than assuming a single consent covers the life
  of the app.
