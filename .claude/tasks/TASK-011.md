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

**Video eviction is explicitly out of scope**, and belongs with the rolling-window item. Removing a
video's local file makes it disappear from the gallery, and runs straight into the trash rules in
CLAUDE.md and the Samsung behaviour that deleted files outright. Different risk, different task,
different decision.

That is eviction, and it is not the only lever. **Downscaling old video is a decided v0.3 item**,
not a rejected one — see the video section of MILESTONES. When it lands it frees space without
removing anything, which makes it the safer of the two rather than a variant of eviction. It is out
of *this pass* because it needs Media3 Transformer and a transcode cost measured on real 8K footage,
not because video cannot be shrunk.

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
- Each album row shows space already freed and what its selected mode could free, and the two are
  never merged into one figure
- The projection changes as a mode is picked, before anything is applied
- Off and Backup read "no space freed" rather than showing a blank
- Sync's estimate uses the ratio measured from this device's own proxies once enough exist, and a
  documented fallback before that; rows that can never shrink are excluded
- The list total and the Settings → Storage total are the same number
- Sync scope persists, defaults to Both, and applies to every album marked Sync and to no other
- Choosing Photos only states how many videos it leaves unprotected before it is applied, and the
  affected albums keep saying so afterwards
- Video only yields a zero projection on every Sync album and proxies nothing
- The video age offers Immediately / 1 week / 1 month / 1 year, persists, and defaults to 1 year
- Choosing Immediately states what it costs — clips downscaled as soon as they are backed up,
  including ones shot today, and full-quality editing means fetching the original back
- **Uploading is never delayed by the threshold** — a clip shot minutes ago is uploaded on the next
  run whatever the age setting says, verified by observation rather than by reading code
- Changing the threshold moves the Sync projection on the album rows, before anything is applied

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

### Permission — what is actually being decided, revised 19 Aug 2026

**The SAF finding demoted this.** Before it, the notification was load-bearing: a background worker
could not obtain write consent, so the notification was the only way to ask for the next batch, and
without it automatic space management died silently. That is no longer true for photos — proxying
now runs unattended through the tree grant and never needs to ask.

So what the permission is worth now:

| Use | Still needed? |
|---|---|
| Asking for the next proxy-consent batch | **gone** — SAF needs no batch |
| Telling the user a batch is ready to **archive** | yes — Archive keeps its tap |
| Saying the floor was breached and could not be met | yes, informational |
| Reporting what a run did | yes, informational |

**Nothing breaks if it is denied.** TASK-011 already requires the same states to be visible in
Settings as ordinary UI, precisely so the notification is never the sole carrier. Denied, Archive
batches accumulate until the user next opens the app, and the floor state is a screen they have to
go and look at. Worse, not broken.

**The Play cost is close to zero.** `POST_NOTIFICATIONS` is the most common runtime permission on
Android, needs no declaration form and no review, and attracts none of the scrutiny
`MANAGE_EXTERNAL_STORAGE` would have. It appears in the listing's permission list and nowhere
prominent. It is flagged here only because CLAUDE.md makes any listing-visible permission an
escalation, not because it is a close call.

**So the decision is one thing: when to ask.** Ian's first-run design (19 Aug 2026) puts permissions
approval in the launch wizard alongside language and cloud choice. That is right for the media
permissions, which gate everything — but asking for notifications before the user has a storage
floor or an Archive album is asking before there is anything to notify about, and a permission the
user cannot see the point of is the one they deny permanently. Android only ever prompts once.

Recommended split: **media permissions and cloud choice at first run; notifications at the moment
the user first sets a floor or sets an album to Archive**, where the prompt explains itself. Ian's
call, and either way the feature works.

## Consent — settled shape, and the constraint it puts on everything else

> **Superseded in part, 19 Aug 2026 — verified on hardware.** A persisted SAF tree grant performs
> the proxy write (4.4 MB photo owned by `com.sec.android.app.camera`, shortened to 4 KB) with no
> consent dialog and no URI cap. If the grant survives a reboot, everything below — the grant pool,
> the `ClipData` hand-off, the WorkManager-versus-JobScheduler fork, options 1/2/3 — is unnecessary
> for proxying, and the applying step becomes ordinary background work. It is kept because it is
> still the design if the SAF route fails the reboot test, and because Archive still needs a delete
> path that is not yet verified. One requirement carries over regardless: **a MediaStore rescan must
> follow every write**, since the index stays stale until it happens. See the SAF entry in the
> hardware log in MILESTONES.

`MediaStore.createWriteRequest` can only be launched from an Activity, so **a background worker
cannot obtain consent on its own.** The platform's documented way through is to hand the granted
URIs to background work via `ClipData`, carrying `FLAG_GRANT_WRITE_URI_PERMISSION`, so the grant
outlives the Activity that obtained it.

That works, but the 2000-URI cap means a single grant cannot cover a whole library in one go. The
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

### What the 2000 cap actually is — checked 18 Aug 2026

Verified against the platform docs rather than assumed. An earlier commit message called it
"MediaStore's documented limit", which was right but incomplete in a way that matters:

- The cap is **2000 URIs per request**, and exceeding it throws `IllegalArgumentException`. The
  same cap applies to `createDeleteRequest`, `createTrashRequest` and `createFavoriteRequest`.
- It applies to **apps targeting Android 15 (API 35) and above**. This app targets 37, so it
  applies. It is not a limit older targets hit.
- No official statement of intent is published. The forces behind it are legible, though: the URIs
  cross a Binder boundary inside a `ClipData`, and the shared transaction buffer is about 1 MB, so
  an oversized list used to fail as `TransactionTooLargeException` — an obscure, size-dependent
  crash. A round number well inside the buffer turns that into a documented, deterministic error.
  The consent dialog also has to render and count the affected items, and "modify 47,000 photos?"
  is not informed consent in any case.

**It is a cap per request, not a lifetime quota and not a ceiling on how much can be optimised.**
Nothing limits how many grants are requested over time.

### Consequence: this is a first-run problem, not a steady-state one
The device holds 6,289 images, and only a subset is eligible — verified in OneDrive, over 2048px,
not already proxied, not video. So a full catch-up is **at most four dialogs, once**.

After that, the batch is however many new eligible photos accumulate between runs. Nobody takes
2000 photos between passes over a storage budget. The steady state is one prompt, rarely, covering
a handful of files — or no prompt at all.

This materially weakens the case for a grant pool. The pool exists to avoid re-prompting, but the
re-prompting it avoids is a few dialogs during initial catch-up and almost nothing thereafter. It
is not the recurring nag it looked like when the cap was first written down.

### The backup schedule keeps batches small — with three conditions

Ian, 18 Aug 2026: with sync running automatically, few eligible photos accumulate between runs.
Confirmed against the scheduling code, and it is a stronger claim than it first appears: backup is
**content-triggered** on `MediaStore.Images` and `MediaStore.Video`, plus a 6-hourly safety net. So
a photo is verified in OneDrive shortly after it is taken, and eligibility accrues at roughly the
rate photos are taken. In steady state that is a handful of files per run, not thousands.

Three things gate it, and each is worth knowing before relying on the claim.

**1. Automatic backup is off by default.** `BackupPreferences.isAutomaticEnabled` defaults to
`false`, deliberately, so installing a build never starts uploading a library on its own. Until the
user turns it on there is no steady state at all — only the catch-up case. The budget feature
should not assume automatic backup is running; it should notice when it is not, because a floor
that can never be met because nothing is being backed up is a confusing thing to stare at.

**2. Unmetered-only by default.** Two weeks off Wi-Fi is exactly when a lot of photos get taken and
none of them get verified. They arrive as one batch on returning home. Still comfortably inside a
single 2000-URI grant, but it is the one realistic case where the batch is large — and it coincides
with the phone being fullest.

**3. `setRequiresStorageNotLow(true)` — the one with teeth.** The backup worker will not run when
Android considers storage low. Backup is what makes a photo eligible for proxying, so if that
constraint ever bites, new photos stop becoming eligible precisely when space is most needed.

The floor is what keeps this safe. Android's low-storage threshold is on the order of a few hundred
megabytes, so a 20 GB floor trips long before the constraint does — the budget worker acts while
backup is still running normally. That is a real argument for the default, not just a comfortable
number.

But it means **the floor must stay well clear of the system threshold**, and a floor set very low
would converge on it: backup stalls, nothing new is verified, the worker exhausts the already-
verified backlog, and the phone stays full with no way out. Enforce a sensible minimum on the
setting rather than accepting any number the user types.

### What this does to the applying-step decision
If a steady-state batch is a handful of photos, applying it takes seconds. Backgrounding a
two-second job — at the cost of a grant pool, a `ClipData` hand-off and a second execution
mechanism — is most of the complexity in this task for none of the benefit. Option 3 gets stronger
the more closely the two schedules are examined.

### First run — scheduling it, and why no dialogs are involved

Ian, 18 Aug 2026, raised the first activation backing up the entire gallery, and asked whether it
could run overnight and have the consent dialogs answered by a script.

**Backing up needs no consent dialogs at all.** Uploading reads local files and POSTs them to
Graph. Reading needs `READ_MEDIA_IMAGES` / `READ_MEDIA_VIDEO`, granted once at setup — there is no
per-file or per-batch dialog on the read path. The first whole-gallery backup is a long transfer,
not a sequence of prompts.

Dialogs appear in exactly two places, both of which *modify* local files, and both user-initiated:

| Operation | Dialog | When |
|---|---|---|
| Backup / upload | none | — |
| Move redundant local copies (`createTrashRequest`) | yes, 2000 cap | user taps "Move to backup" |
| Optimise (`createWriteRequest`) | yes, 2000 cap | user taps "Optimise" |

So the thing worth scheduling overnight and the thing that needs the user present are different
operations, and they separate cleanly.

**Answering the dialogs programmatically is not available, and is not a gap to engineer around.**
The dialog is drawn by MediaProvider in another process; cross-app input injection needs
`INJECT_EVENTS`, which is signature-level and system-only. The one mechanism that could reach it is
an `AccessibilityService`, which the user must enable by hand and which Play policy restricts to
genuine accessibility use — auto-confirming permission dialogs is an explicit violation and gets
apps removed. It also runs directly against this project's own rule that nothing is rewritten or
trashed without the user confirming that specific action. The dialog is the refusal opportunity;
answering it on the user's behalf removes the only thing it is for.

It is also not needed. The first optimise pass over 6,289 images is **at most four dialogs, once**
— fewer in practice, since anything already under 2048px is skipped.

### Is there a way around the tap at all? — asked by Ian, 19 Aug 2026

The paragraph above answers a narrower question: whether the dialog can be *answered* for the user.
It cannot. This one is different — whether the dialog has to *exist*. There are two routes that
avoid raising it, and one of them is real.

**Note first that removing the dialog does not break this project's consent rule.** Per CLAUDE.md
the consent is the album mode, given once and confirmed when it is set. The platform dialog was
never where the authorisation came from; it is Android's own check, layered on top. So eliminating
it is consistent with the design rather than a way round it — which is exactly why it is worth
evaluating rather than dismissing.

#### 1. `MANAGE_EXTERNAL_STORAGE` — works, and costs the most

All files access lets an app modify and delete media in shared storage without per-batch MediaStore
consent. It is the mechanism file managers use, and it would remove the tap entirely — proxying and
archiving alike, at any hour, with no Activity involved.

The cost is the Play listing:

- It is a **high-scrutiny permission** requiring a declaration and review, and Play restricts it to
  apps whose core purpose genuinely needs broad file access. **Backup apps and file managers are
  among the permitted categories**, and GallerySync is defensibly the former — but "defensibly" is
  doing real work in that sentence, and the reviewer decides, not us.
- It appears prominently to the user at install and in the listing.
- A rejected declaration can hold up a submission, and the release gate already puts the first
  submission after v0.3 and v0.4.

Per CLAUDE.md this is an escalation twice over: a new permission that affects the Play listing, and
two architecturally distinct paths with long-term implications. **Ian's call, and not a decision to
drift into.** Worth noting the asymmetry: adopting it later is easy, while removing it after launch
means users who granted it and a listing that changes.

#### 2. A persisted SAF tree grant — cheaper, and unverified

`ACTION_OPEN_DOCUMENT_TREE` plus `takePersistableUriPermission` gives durable write access to
everything under a chosen directory, with no per-file dialog afterwards. One folder pick at setup,
covering DCIM or Pictures, would in principle let a background pass modify and trash files under it
indefinitely.

Unverified, and the parts to establish on hardware before believing it:

- **Whether the directories that matter can still be picked.** Android 11 blocked selecting the
  external storage root and `Download`, and blocks `Android/data` and `Android/obb`. DCIM is
  believed selectable; confirm it on the Fold 4 at targetSdk 37 rather than trusting that.
- **Whether writing through the tree grant actually bypasses the MediaStore consent** for media the
  app does not own, on this API level, rather than throwing `RecoverableSecurityException` anyway.
- **Whether MediaStore stays consistent** afterwards, since a SAF write does not itself update the
  index.

If it holds it is much the cheaper option: no Play declaration, no listing change, one folder
picker at setup. If it does not, route 1 is the only one left.

#### 3. What definitely does not work

- **Input injection** — `INJECT_EVENTS` is signature-level and system-only.
- **AccessibilityService** — Play policy violation, gets apps removed, and the user must enable it
  by hand anyway.
- **Being the system gallery** — how Samsung did it, unavailable to a third-party app, and the
  mechanism is being switched off regardless.
- **Owning the files instead.** An app may modify media it created without consent, but ownership
  sits with whatever wrote the file and does not transfer. Deleting the user's original to
  re-create it under this app's ownership needs the same consent first, and destroys the original
  to save a dialog. Not a route.

#### Recommendation

**Verify route 2 before considering route 1.** It costs an afternoon on the Fold 4 and, if it
works, removes the tap without touching the Play listing at all. Route 1 works but spends listing
scrutiny that this project has not had to spend yet, and it is easier to add later than to withdraw.

Either way the tap is not needed *often* — 2000 URIs per batch means the first pass over a large
library is a few dialogs once, and steady state is a handful of files. The case for removing it is
about unattended overnight operation, not about volume.

### The two halves fit together
This is the natural shape of first run, and it happens to be exactly option 3:

- **Overnight, unattended:** the whole-gallery upload. Hours of transfer, battery and heat, and no
  user input possible or required. Schedule it.
- **Next time the app is opened:** the optimise pass, where the user taps through a few grants with
  a progress indicator between them. They are present because the platform requires it, and the
  work is fast because the uploads already happened.

### Scheduling the first backup — worth building
Not for the dialogs, but because a whole-library upload is the one genuinely heavy thing this app
ever does.

- WorkManager `setInitialDelay` to the next occurrence of a user-chosen time; the existing
  `BackupScheduling.enable` already takes the constraints.
- Add `setRequiresCharging(true)` for this first run. Overnight and charging also sidesteps Doze,
  which defers work on an unplugged idle device.
- Keep the existing unmetered constraint.
- Generalises later into a backup window preference, but the narrow version — a start time for the
  first run — is what is being asked for and is enough.

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

## Space saved — a running count per album

Requested by Ian, 19 Aug 2026. The mode dropdown asks the user to choose between four options whose
whole point is space, and then tells them nothing about space. Each album row carries the number its
choice is worth, and the list carries the total.

This belongs here rather than in TASK-012 because it is the **same arithmetic the floor needs**. The
budget worker asks "how many bytes can I still reclaim, and from where"; the album list asks "what
is this choice worth". One set of aggregates answers both, and building them twice would let the
screen and the worker disagree about the same phone.

### Two numbers, and they must never be confused

- **Freed** — what has actually been reclaimed. Exact, already true, and does not change when the
  user touches a dropdown. `sizeBytes - localProxySizeBytes` over proxied rows.
- **Could free** — what the currently-selected mode would reclaim if fully applied. An estimate, and
  must be rendered as one.

A single blended figure would be the wrong call: the user is making a decision about the future
while looking at a record of the past, and a number that silently mixes the two reads as the app
losing count.

### What each mode is worth

| Mode | Could free | Exact? |
|---|---|---|
| **Off** | nothing | yes |
| **Backup** | nothing | yes |
| **Sync** | proxyable photo bytes, less what the proxies will occupy | estimated |
| **Archive** | every local byte the album currently occupies | yes |

**Backup showing zero is the honest answer, and it is worth showing rather than blanking.** Backup's
value is that the photos are safe, not that the phone is emptier; a blank cell invites the reading
that the number is missing. Say "no space freed" beside "every file copied to OneDrive" and the
trade is legible.

### The estimate

A photo that has not been proxied yet has no known proxy size — the ledger records
`localProxySizeBytes` only after the fact. So Sync's projection needs a ratio, and there is a real
one available: measure it from this device's own proxied rows rather than asserting 10x.

```sql
SELECT COALESCE(SUM(localProxySizeBytes), 0), COALESCE(SUM(sizeBytes), 0)
FROM backup_entries WHERE isProxied = 1 AND localProxySizeBytes IS NOT NULL
```

Fall back to 0.1 until enough rows exist to measure — say 20 — because a ratio taken from two photos
is noise. The 18 Aug hardware run is the sanity check: 11 photos, 40,283,338 bytes reclaimed, a
348 KB proxy against a multi-megabyte original.

**Exclude `isProxySkipped = 1`.** This is the same trap the notes below already record for the
deficit: a photo already under 2048px can never shrink, and counting it inflates the projection with
bytes that will never arrive. Schema 5 exists precisely so this figure can be honest.

### The aggregate

Mode-independent by design. The dropdown selection is applied in the ViewModel, not in SQL —
otherwise every flick of a dropdown re-queries the database, and a list of forty albums becomes
forty round trips. It does take the archive age as a parameter, which is a setting rather than a
mode: it changes rarely, so re-querying when it moves is fine.

```sql
SELECT album,
       SUM(CASE WHEN isProxied = 1
                THEN sizeBytes - COALESCE(localProxySizeBytes, sizeBytes)
                ELSE 0 END)                                    AS freedBytes,
       SUM(CASE WHEN isProxied = 0 AND isProxySkipped = 0 AND isVideo = 0
                THEN sizeBytes ELSE 0 END)                     AS proxyableBytes,
       SUM(CASE WHEN state = :uploaded
                     AND remoteSizeBytes IS NOT NULL
                     AND remoteSizeBytes = sizeBytes
                     AND dateModifiedEpochSeconds < :archiveCutoffEpochSeconds
                THEN COALESCE(localProxySizeBytes, sizeBytes)
                ELSE 0 END)                                    AS archivableBytes
FROM backup_entries
GROUP BY album
```

- `freedBytes` → **Freed**, for every mode.
- `proxyableBytes` → **Sync**'s projection, times `(1 - ratio)`.
- `archivableBytes` → **Archive**'s projection. Note the `COALESCE`: an already-proxied photo now
  occupies only its proxy, so archiving it frees the proxy's bytes and not the original's. The
  consequence is worth surfacing — **once Sync has run, Archive's additional gain is small**, which
  is exactly the argument the UI should be making.

Two filters on `archivableBytes`, and dropping either one turns the figure into a promise the app
will not keep:

- **Verified in the cloud**, per hard rule 1. An album whose backup is half finished must not
  advertise the whole album's bytes as available; that would be a promise to remove files that have
  nowhere to go back to.
- **Older than the archive age.** Archive is evaluated per file, not per album — recent files stay
  put inside an archived album (TASK-012 guard 2). An unfiltered total would count every recent
  photo as space about to be freed, and then not free it. Ian, 19 Aug 2026: this filter was missing
  from the first draft of this section.

A consequence worth expecting rather than debugging: **Archive's projection grows on its own** as
files age past the threshold, with nobody touching anything. That is correct, and it is another
reason the number needs the word "could" rather than a promise attached to it.

### The hazard this introduces, and it is not small

**Archive will always show the biggest number.** It is exact, it includes video, and it is several
times whatever Sync can claim. A column of numbers with the largest one beside the only mode that
empties the gallery is an argument for the mode that caused Ian's original problem.

So the count is never presented as a ranking, and Archive's figure never appears without its cost in
the same breath — the files leave the gallery, and getting one back is a deliberate fetch that v0.4
has not built yet. Per TASK-012, Archive must not ship before retrieval exists; until then its
projection may be shown greyed with the reason, but the mode cannot be selected.

### Where it goes

Per-album, on the row itself in the Album Modes list — TASK-012 owns that screen. A total across all
albums at the top of that list, and the same total in Settings → Storage beside the floor, so the
budget screen and the album screen never quote different figures.

## Sync scope — photos, video, or both

Requested by Ian, 19 Aug 2026: a single setting deciding what Sync acts on, applying universally to
every album marked Sync rather than per album.

Universal is the right shape. Per-album it would be a second dropdown beside the first, multiplying
four modes by three scopes into twelve states to reason about, for a preference nobody holds
differently for one album than another.

### What it does

| Setting | Photos in a Sync album | Video in a Sync album |
|---|---|---|
| **Photos only** | uploaded, then optimised | uploaded, left at full size |
| **Video only** | uploaded, left at full size | uploaded, then optimised |
| **Both** *(default)* | uploaded, then optimised | uploaded, then optimised |

> **Revised by Ian, 29 Aug 2026.** The three "not uploaded, untouched" cells above are struck: they
> said this setting could stop a medium being backed up, and it cannot. Uploading follows the album
> mode — Backup, Sync and Archive all upload; only Off does not — so a setting scoping *Sync* scopes
> the optimising half and nothing else. *"Turning off syncing/optimising doesn't mean it can't still
> be backed up."*
>
> The old reading was also unsafe in a way nobody had noticed: everything downstream needs a verified
> cloud copy, so excluding video from upload would have silently disabled optimising, archiving and
> restore for it, and left the largest files on the phone with no cloud copy at all. The shape is now
> two toggles rather than a tri-state, and the worst a toggle can do is leave a file at full size.

Only `SYNC` albums are affected — Ian's wording, and it is also the coherent line. `BACKUP` means
"copy everything and change nothing", and a type filter silently narrowing it would make the safe
mode not-quite-safe.

### The reading this assumes, stated plainly

The setting scopes **what Sync includes at all**, not merely what it optimises. That is the only
reading that produces three distinct buildable behaviours: Sync's space-saving mechanism is the
photo proxy, video is never proxied, so a setting that scoped only the optimising step would make
"Photos only" identical to today's behaviour and leave the other two waiting on video proxies that
do not exist. If the narrower reading was intended, this section is wrong and the feature waits on
the video-proxy item — worth a word before it is built.

### Video only — what it saves depends on a feature that is decided but not built

**Today it saves nothing.** Video is uploaded and left whole, so Sync does exactly what Backup does,
at the cost of excluding the photos.

That is a statement about the current build, not about video. **Old-video downscaling is a decided
v0.3 item, not a rejected one**: MILESTONES has it as a full-length transcode, marked, on charge,
**Sync albums only** — pending Media3 Transformer and a transcode cost measured on real 8K footage.
"Sync albums only" is this setting's territory exactly, so the two features are coupled rather than
adjacent.

| | Today | Once old-video downscale lands |
|---|---|---|
| **Photos only** | photos proxied, video excluded | unchanged |
| **Video only** | video uploaded, nothing freed | old clips downscaled, recent clips untouched |
| **Both** | photos proxied, video whole | photos proxied, old video downscaled |

Two things follow, and both are cheaper to build now than to retrofit:

- **Do not hardcode Video only's projection to zero.** It is zero because no video is currently
  proxyable, which the aggregate already expresses through `isVideo = 0` on `proxyableBytes`. Leave
  the arithmetic general so video bytes can enter it when they become eligible, rather than writing
  a special case that has to be found and removed later.
- **"Old" has no definition anywhere yet.** *Recent video is never touched* is the load-bearing half
  of the requirement and is settled; the age boundary that separates recent from old is not, and it
  is Ian's to set. Under **Video only** that boundary is effectively the entire feature — it decides
  whether the setting frees a lot or nothing at all. Flagged rather than assumed.

The running count still earns its place either way: with **Video only** selected today, every Sync
album's projection reads zero on the same screen where the choice was made, which is the honest
answer until the transcode exists.

### The trap: a mixed album with photos-only

Camera holds photos and video together. Set Camera to **Sync** with **Photos only**, and the videos
in it are not backed up at all — the founding failure of this project wearing a different hat.

It stays possible, because someone who does not want 8K clips consuming their OneDrive quota is
making a legitimate choice. But it is never silent:

- Changing the scope shows what it will exclude, counted from the ledger — "this leaves 212 videos
  in Sync albums unprotected" — before it is applied, not after.
- The affected albums say so on their own row, permanently, not only at the moment of the change.
- Setting those albums to **Backup** is offered as the fix in the same place, since Backup covers
  everything regardless of scope.

`AlbumMode.DEFAULT`'s reasoning is the standard here: the failure mode should be "uploaded something
you did not need", never "lost something you did".

### Where it lives

`BackupSettings`, beside the existing preferences, per the note below — not a new store.

```kotlin
enum class SyncScope { PHOTOS_ONLY, VIDEO_ONLY, BOTH }
```

Default `BOTH`, matching what the app does today, so an upgrade changes nothing until the user
chooses otherwise. `BackupPreferences` gains the field; the stored key is a string, and an
unrecognised value falls back to `BOTH` rather than throwing — a future rename must not brick the
setting.

### What it changes in the queries

`nextPending` currently excludes only `mode = 'OFF'` albums. It has to become mode-aware for
TASK-012 regardless, so scope rides along on the same change:

> exclude a row when its album's mode is `SYNC` and `isVideo` does not match the scope.

`proxyCandidates` needs `VIDEO_ONLY` to return nothing for Sync albums; it already filters
`isVideo = 0`, so the scope check is the only addition. Getting this wrong in the permissive
direction proxies photos the user asked the app to leave alone — an overwrite of an original — so it
wants a unit test per scope value rather than a glance.

## Video age — the user sets it

Decided by Ian, 19 Aug 2026, closing the boundary this spec had flagged as open. *Recent video is
never touched* stays the requirement; **how recent is a user setting**, not a constant chosen here.

That is the right call for the same reason the storage floor is a setting rather than 20 GB hard-
coded: "old" is not a fact about anyone's footage. Someone shooting client work edits clips for
months; someone filming their kids will never open most of it again.

### It gates downscaling, never uploading

**Read this before building anything.** The setting decides when a video becomes eligible to be
**shrunk**. It must never delay, gate or defer the *upload*.

Uploading stays immediate for every video regardless of age, and it stays **unattended**. Two
separate properties, and this task may spend neither:

- **It is never delayed.** A clip that has not been uploaded is a clip with one copy, and the window
  in which it has only one copy should be as short as the network allows. The founding failure was a
  clip going missing ten minutes after it was shot, so a threshold that held new video out of
  OneDrive would reproduce the original problem while wearing the name of the fix. This is a
  backup-coverage argument and nothing to do with consent.
- **It never asks the user to approve anything.** Ian, 19 Aug 2026: the point of auto-syncing albums
  is that the user does not have to intervene. Uploading needs no consent dialog at all — reading
  local files runs on `READ_MEDIA_*`, granted once at setup — so unattended upload is a property the
  platform genuinely allows, unlike unattended rewriting.

That asymmetry is the shape of the whole app, and it lines up exactly with the consent rule in
CLAUDE.md:

> **Uploads never wait for the user. Anything that removes or rewrites always does.**

Consent attaches precisely where a file leaves or changes the gallery, and nowhere else — and per
CLAUDE.md it is the **album mode** that carries it, not a per-file prompt. It is also why the age
threshold gates only the downscale: that is the half that changes the file, and the half where the
platform will interpose regardless.

The "waits on the user" column below is Android's own dialog, not the app's consent model. It
appears per batch whether or not the mode already authorised the work, and the app neither supplies
it nor adds a per-batch confirmation of its own — the app's single confirmation happens when the
mode is set, per CLAUDE.md.

| | Governed by the age setting? | Waits on the user? |
|---|---|---|
| Video uploaded to OneDrive | **no** — always immediate | **no** — no dialog exists on this path |
| Video downscaled in place | **yes** — only once older than the threshold | **no** — SAF tree grant, verified 19 Aug 2026 |
| Video's local copy removed | no — that is Archive, and its own explicit choice | yes, per batch, unavoidably |

### The shape of the setting

Presets rather than a free-text number of days, so the value is always sane and always legible, and
the **same four the Archive age uses** — one vocabulary of ages across the app rather than two:

> **Immediately · 1 week · 1 month · 1 year**

Set by Ian, 19 Aug 2026, replacing an earlier `Never / 30 days / 90 days / 6 months / 1 year` with a
30-day floor.

- **Default: 1 year.** Deliberately conservative, matching every other default in this app —
  automatic optimising off, metered off, `ARCHIVE` never a default. The cost of a cautious default
  is that the feature does little until the user lowers it, which is recoverable in one tap. The
  cost of an eager one is a degraded clip somebody still wanted.
- **"Never" is gone, and nothing is lost.** `SyncScope.PHOTOS_ONLY` already expresses "leave my
  video alone" and expresses it better, since it also keeps the clips out of OneDrive.
- **There is no enforced minimum any more.** Immediately is below any floor by definition. The
  protection is the same one the Archive age relies on: a named option the user picks deliberately,
  not a limit the app imposes.

### Immediately, for video, contradicts a stated requirement — flagged, not resolved

**Worth a moment before it is built.** MILESTONES records *recent video is never touched* as the
requirement rather than a default, and it comes from the founding use case: a clip shot ten minutes
ago that could not be edited. Setting this to **Immediately** downscales exactly that clip.

Two things are genuinely different from the original failure, and one is not:

- The clip **does not disappear.** It stays in the gallery, playable, at 1080p. Nothing vanishes,
  which was the actual harm.
- The user **chose it**, in a named option, on an album they set to Sync. That is the same standard
  CLAUDE.md's consent rule applies everywhere else.
- But a CapCut export from it **is capped at the transcoded resolution**, and that is precisely the
  complaint recorded against full-length downscale in the first place. Retrieval (v0.4) is the way
  back, and it is a deliberate fetch rather than something an editor does for you.

So the option is built as instructed, and the UI has to say plainly what it costs: *clips are
downscaled as soon as they are backed up, including ones you shoot today; editing at full quality
means fetching the original back.* If that reads as too sharp an edge once it is on screen, the
answer is to change the default or drop the option — not to soften the wording.

### What "old" is measured against

Use `dateModifiedEpochSeconds`, which the ledger already carries. **No schema change, so no
migration and no escalation** — worth stating, because the obvious alternative does need one.

The obvious alternative is MediaStore's `DATE_TAKEN`, which is the better semantic: it is when the
footage was shot, not when the file last changed. It is not on `backup_entries`, so using it means a
nullable column, schema 6 and a migration — and CLAUDE.md makes that an escalation. Not worth it
here, because the error `dateModified` introduces runs in the safe direction:

- A clip copied onto the phone gets a fresh mtime, so genuinely old footage looks new. It is
  therefore **not** downscaled. Erring toward leaving video alone is the failure this feature can
  afford.
- Re-saving or trimming an old clip resets its mtime, so a video the user is actively working on
  stops being eligible. That is not a defect of the proxy for date-taken; it is the behaviour you
  would want anyway.

If date-taken is wanted later it is an additive migration, and the fallback rule should be
`max(dateTaken, dateModified)` rather than date-taken alone — otherwise the second property above
is lost and a 2019 clip edited yesterday becomes eligible again.

### It changes the projection, which is the point

The Sync projection for video is a function of this threshold: lower it and more clips become
eligible, so the number on each album row moves as the setting moves. That closes the loop the
running count was built for — the user sees what the choice is worth **before** committing to it,
on the same screen.

It also gives the notification's third state something to say. "Below the floor, nothing left to
proxy" currently ends at *video is holding it and this app will not touch video*. Once downscaling
exists, that message can name the setting and the number: lowering the video age to 90 days would
free a further *N* GB. A dead end becomes an action, without the app deciding anything on its own.

### It does not ship before retrieval

Same gate as `ARCHIVE`, for the same reason. Downscaling a video means the full-quality original
exists only in OneDrive, and until v0.4 there is no route back to it from inside the app. Photos
survived this gap because a 2048px proxy is still a usable photo; a downscaled clip handed to CapCut
caps the export, which is the whole objection recorded against full-length downscale in the first
place.

So the setting may be built and shown, but the transcode that consumes it waits on v0.4 — and on
the transcode cost measured against real 8K footage, which is still the thing standing between this
being decided and being buildable.

### How the age limit and the album mode combine

Asked by Ian, 19 Aug 2026. The short answer is that nothing checks videos one at a time, and no
state is written to a row when it crosses the threshold.

**Eligibility is a query, never a stamp.** A video may be downscaled when all of these hold, and
they are evaluated together at the moment the worker asks:

1. its album's mode is `SYNC` — not Off, not Backup, and not Archive, which removes rather than
   shrinks;
2. the sync scope includes video (`VIDEO_ONLY` or `BOTH`);
3. it is verified in OneDrive — Graph confirmed and the byte size matches;
4. it is older than the age threshold;
5. it has not already been downscaled or examined and declined.

```sql
SELECT * FROM backup_entries
WHERE isVideo = 1
  AND dateModifiedEpochSeconds < :cutoffEpochSeconds
  AND album IN (SELECT albumName FROM album_preferences WHERE mode = 'SYNC')
  AND state = :uploaded
  AND remoteSizeBytes IS NOT NULL
  AND remoteSizeBytes = sizeBytes
  AND isProxied = 0
  AND isProxySkipped = 0
ORDER BY sizeBytes DESC
```

Scope is applied before the query runs rather than inside it: under `PHOTOS_ONLY` this query is not
issued at all.

Note `album IN (… mode = 'SYNC')` rather than the `NOT IN (… mode = 'OFF')` form used by
`nextPending`. An album with no preference row takes `AlbumMode.DEFAULT`, which is `BACKUP`, so
absent rows are correctly excluded by the positive test. Copying the negative form would sweep in
every album the user has never touched.

### No, it does not check every video

The threshold is not compared against each file. **One cutoff timestamp is computed in Kotlin** —
`now - threshold` — and the database does a single range comparison. The cost is one query over a
few thousand rows with no file I/O, no decode and no MediaStore round trip, which SQLite answers in
low single-digit milliseconds. There is no per-video work until a file is actually chosen to be
transcoded.

`backup_entries` carries indices on `state`, `album` and `(album, state)`, and none on
`dateModifiedEpochSeconds` or `isVideo`. That is fine at this table's size — adding one is a schema
change, so measure before paying for it rather than adding it speculatively.

### Nothing has to run at the moment a video ages

A video becomes eligible by the passage of time, which sounds like it needs a timer and does not.
Eligibility only matters when the worker next asks, and the existing schedule already asks often
enough: content-triggered on `MediaStore.Video`, plus the 6-hourly safety net in
`BackupScheduling`. A clip that crosses the threshold at 3am is simply included in the next run. No
alarms, no per-file scheduling, nothing to reconcile after a reboot.

### Why this shape matters — the settings stay free to change

Because eligibility is computed rather than stored, **changing a setting costs nothing**. Switch an
album from Sync to Backup and its videos stop being eligible on the next query, with no sweep and
no per-file state to unwind. Raise the age threshold and clips that were eligible quietly are not.
Change the scope and a whole media type drops out.

The alternative — writing an `isEligible` flag onto each row — is precisely what would create the
problem this question is about: every settings change would then need a pass over the whole table
to recompute it, and any missed pass would leave rows lying about their own status. Do not
materialise eligibility.

The same property is what lets the running count above update live as a dropdown moves: the
projection and the worker's queue are the same query with a different aggregate over it.

### Created versus modified

The threshold reads `dateModifiedEpochSeconds`, which the ledger already holds. Creation time —
MediaStore's `DATE_TAKEN` — is the better semantic and is not on the table, so it would mean a
nullable column, schema 6 and a migration. See the section above for why the mtime proxy is
acceptable and which direction its error runs in.

### Two age settings — decided, they stay separate and both are visible

Ian, 19 Aug 2026. There are two ages in the app and they are not variants of each other:

| Setting | Reads as | Governs | Immediate? | Minimum |
|---|---|---|---|---|
| **Sync age** — this task | *Downscale videos older than…* | a clip shrunk in place, still in the gallery | yes | none |
| **Archive age** — TASK-012 | *Remove files older than…* | photos and video leaving the phone | **yes** | none |

Both live in Settings. An earlier draft of this section proposed hiding the Archive one inside its
confirmation dialog to avoid two age fields sitting together; Ian's call is that the user can tell
the two apart, so that is withdrawn. The real requirement is that each label names its
**consequence** rather than its mode — two fields both called "age" would be the confusing thing,
two settings are not.

Both take the same four values and neither has a floor — Ian, 19 Aug 2026. One difference survives
and must not be tidied away: **only the Sync age is limited to video.** Photos are proxied whatever
their age, because a 2048px proxy leaves the photo in the gallery and costs an edit nothing until
the export. There is no photo age setting and none is wanted.

The Sync age gaining **Immediately** is a real change of stance, not a harmonisation — see the flag
under "The shape of the setting" above. It puts downscaling a clip shot this morning within reach of
a user who asks for it.


## Notes for whoever picks this up
- `ProxyApplier.candidates()` returns eligible photos largest-first and already filters rows whose
  local file is gone. It caps at 2000 URIs per MediaStore's limit.
- `ProxyApplier.createWriteRequest()` already builds the IntentSender for a batch — the grant-pool
  work extends it rather than replacing it.
- `ProxyGenerator` skips anything already at or under 2048px, so the reclaimable total is smaller
  than the candidate byte count suggests. Do not size the deficit against `proxyCandidateBytes`.
- The ceiling is a user setting and belongs with `BackupSettings`, alongside the existing
  preferences, not in a new store.
