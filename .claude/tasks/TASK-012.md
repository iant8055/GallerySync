# TASK-012 — Per-album modes, settings, and the shape of the app

Milestone: v0.3.0 / v0.4.0 — information architecture
Requested by: Ian, 18 Aug 2026
Depends on: TASK-011 (storage budget) for the auto-optimise toggle to control something

## What Ian asked for
1. Per-album **Off / Sync / Backup** dropdown, replacing the on/off toggle
2. A **default mode** for new albums, set in Settings
3. **Auto Optimise Photos** as a Settings toggle
4. **Storage** becomes where backup options live, including a schedule
5. **Remove the OneDrive tab**, unless thumbnails can be shown per file
6. Rename the tab — first to **Sync**, then to **Album Modes** on reflection. *Done, 93bf7d4 and this commit*
7. A **sleeker, more modern** look across the whole UI
8. A fourth mode that **moves** an album off the phone into OneDrive — added 18 Aug 2026

## 1. Album modes — the centrepiece

Four modes, forming a ladder: how much of the album stays on the phone.

| Mode | Uploaded? | Local state | Space freed | Still in the gallery? |
|---|---|---|---|---|
| **Off** | no | untouched, full size | none | yes |
| **Backup** | yes | untouched, full size | none | yes |
| **Sync** | yes | photos proxied | ~90% of photos | yes |
| **Archive** | yes | **removed** once older than *N* | all of it, as files age in | **no**, once they go |

Off/Backup/Sync map onto the wording test adopted today. Archive is Ian's fourth, added 18 Aug 2026:
*"moves the album off the local Gallery into OneDrive — not making a copy but moving it to a secure
location."*

**It subsumes an existing milestone item.** v0.3's "per-album keep originals on device for albums
actively edited from" *is* Backup mode. Close that row rather than building it separately.

**Mixed albums resolve themselves under Sync.** Camera holds photos and video together; photos are
proxied and video is left alone, because video is never proxied. No special casing.

**Mixed albums are also where the sync scope setting bites.** Ian added a universal Photos / Video /
Both scope on 19 Aug 2026, specified in TASK-011. Under **Photos only** the video in a Sync album is
not uploaded at all, so Camera set to Sync stops protecting the clips in it. The mode dropdown is
where that has to be visible — TASK-011 requires the affected rows to say so — because the mode is
what the user thinks they are choosing.

**Each row carries what its mode is worth in bytes**, also specified in TASK-011: space already
freed, and what the selected mode could still free, updating as the dropdown changes. Two figures,
never merged. Note the hazard it introduces on this screen in particular — Archive's number is the
largest one in every row, and the layout must not let the biggest number read as the best choice.

### The labels, and one ordering question

Ian, 19 Aug 2026, naming the dropdown items: **None · Sync · Backup · Archive**.

**"None" is the label; `OFF` stays the enum value.** They are different things and the difference is
load-bearing — the stored string is what `AlbumModeConverter` reads back, and its own comment
explains why a value change there could reinterpret existing rows. Rename freely in `strings.xml`;
never rename the constant.

**The order is worth a decision rather than an accident.** The enum is a ladder, ordered by how much
of the album stays on the phone:

| | Off | Backup | Sync | Archive |
|---|---|---|---|---|
| stays on the phone | all | all | shrunk | none |

Listing them **None · Sync · Backup · Archive** breaks that progression by putting the mode that
shrinks files above the one that leaves them alone. Ladder order makes the list readable as a single
axis — *how much of this album do I want to keep on my phone* — with the destructive end furthest
from the safe one, which is also where you want it in a dropdown someone is scrolling quickly.

Recommended: **None · Backup · Sync · Archive**, matching the enum. If Ian's order was deliberate —
Sync first because it is the mode most people should pick — then a "recommended" marker on Sync does
that job without scrambling the axis. Worth confirming before it is built.

### Archive is the dangerous one, and it is the failure Ian lived through
Under Archive the files leave the gallery entirely. Photos get no proxy — unlike Sync there is no
stand-in. This is what Samsung did to him: *"I couldn't find the video I had just shot because
Gallery had moved it to OneDrive."*

What makes it defensible is that **it is a per-album choice, never a blanket behaviour.** Samsung's
"free up phone space" was all-or-nothing with no per-folder control. Designating one album as an
archive is a different act from a policy that quietly reaches everything.

Four guards, none optional:

1. **Never a default**, and not offered as the Settings default for new albums.
2. **An archive age the user sets**, decided by Ian 19 Aug 2026. Nothing recent is swept up unless
   the user has asked for exactly that. Immediate / 30 days / 90 days / 6 months / 1 year,
   defaulting to **30 days**. Immediate is never a default and never reached by accident.
3. **Verified in OneDrive first** — Graph confirmed, byte size matched — the same bar as every other
   removal, and `createTrashRequest` rather than a delete, per the deletion policy.
4. **An explicit confirmation when the mode is set.** Requested by Ian, 19 Aug 2026, and it is *the*
   consent moment for everything the mode goes on to do — per CLAUDE.md there is no second one.
   - It says the files **leave the gallery** and move to OneDrive, in those words.
   - It says that files added to the album later are covered by the same choice. The mode is a
     standing instruction, and this is the only place that is ever made plain.
   - It states what has to be true before anything moves: verified in OneDrive, and older than the
     minimum age.
   - **It must not promise recoverability.** CLAUDE.md's rule holds — a local trash request is not a
     guarantee, as the Fold 4 demonstrated. The verified cloud copy is the only guarantee on offer,
     and the only one the dialog may state.
   - Cancel is the default action.
   - It appears when switching **to** Archive, never when switching away — un-consenting is safe and
     should be frictionless.

   After it is accepted the app does not ask again. Android's own trash dialog still appears per
   batch; it is neither a substitute for this confirmation nor a reason to skip it, because it says
   nothing about what the mode means.

### Archive is set per album but evaluated per file

Asked by Ian, 19 Aug 2026, and the answer follows from guard 2 rather than adding to it: **yes**. The
mode is a property of the album; what it does is decided file by file. A file leaves when it is
verified in OneDrive *and* older than the minimum age. Everything newer stays in the gallery, inside
an album that is set to Archive, until it reaches that age and is taken on a later run.

So an archived album **drains gradually — it does not empty at once.** Three consequences, and each
one has to be visible in the UI or the mode will read as broken:

- **The name promises something the mode does not do.** "Archive" sounds like a one-time move, and
  Ian's own description was *"moves the album off the local Gallery into OneDrive"*. The age guard
  makes it a standing filter instead. The confirmation and the mode's description have to say that
  files older than *N* leave and newer ones stay until they reach that age — otherwise the user
  archives an album, watches it visibly fail to empty, and concludes the app is not working.
- **A photo added later stays, and then goes.** It is present for the whole age window and then
  disappears with no further prompt, covered by the standing consent given when the mode was set.
  This is the sharpest edge in the mode, and the confirmation dialog is the only place it can
  honestly be explained.
- **The count of what Archive would free grows on its own**, as files age past the threshold. That
  is correct behaviour, and TASK-011's projection filters on the same age so the number does not
  promise to remove recent files it will leave alone.

The guard is **media-agnostic**: photos and video alike. Guard 2's wording — "a clip shot this
morning" — is illustrative, not a scoping.

**Which date.** The ledger holds `dateModifiedEpochSeconds`, not creation time. MediaStore's
`DATE_TAKEN` is the better semantic and is not on `backup_entries`, so using it means a nullable
column, schema 6 and a migration. The same reasoning as TASK-011's video age applies, including the
direction the mtime proxy errs in: a file copied onto the phone looks new, so it is *not* removed,
which is the safe way to be wrong.

### The archive age is the user's, and Immediate is one of the choices

Decided by Ian, 19 Aug 2026, resolving the open question this section used to hold. Trickle
archiving is the right default and the wrong mandate: someone who wants an album off the phone now
should be able to say so.

> **Immediately · 1 week · 1 month · 1 year**, defaulting to **1 month**.

Set by Ian, 19 Aug 2026, and shared with the Sync age in TASK-011 so there is one vocabulary of
ages across the app rather than two. Whether removal waits for the nightly pass is a **separate**
choice, not an entry in this list — see the scheduling section below. Age answers which files; the
pass answers when.


**There is no enforced minimum any more, and that is the actual change.** Guard 2 previously worked
as a floor the user could not go under. It now works as a default they must deliberately leave. The
protection moves from *the app refuses* to *the user chose, in a named option, behind a
confirmation* — which is the same trade the consent rule in CLAUDE.md already makes everywhere else.

### What Immediate means, and what it still does not bypass

Immediate means **no age wait**. It does not mean no wait.

- **Verified in OneDrive is untouched by it.** Graph confirmed *and* the byte size matched, before
  anything local goes. This is CLAUDE.md's rule and no setting reaches it. A file that has not
  finished uploading stays exactly where it is, however the age is set.
- `createTrashRequest` rather than a delete, per the deletion policy, exactly as at every other age.
- The mode confirmation still gates the whole thing.

So the sequence under either zero-age option is: file appears -> backed up -> verified -> **ready to
remove** -> the user approves the batch. The upload has to finish, and the trash dialog has to be
tapped; the setting removes the age wait and neither of those.

### It reproduces one third of the founding failure, deliberately

Worth stating plainly rather than discovering later. Camera set to Archive with Immediate will take
a clip shot this morning, once it is safely uploaded. That is the shape of the thing that started
this project.

What made Samsung's version a failure was three properties, and Immediate restores only one:

| | Samsung | Archive + Immediate |
|---|---|---|
| Silent, never announced | yes | **no** — a confirmation names it before it applies |
| All-or-nothing, no per-folder control | yes | **no** — one album, chosen |
| Removal coupled to backup | yes | **yes** — this is what the user asked for |

The coupling was never the harm on its own; being surprised by it was. A user who picks Archive,
confirms a dialog saying the files leave the gallery, and then picks Immediate has said the same
thing three times.

### The confirmation says more when Immediate is chosen

Guard 4's dialog covers the mode. Immediate is the most destructive configuration available in the
app, so selecting it changes what that dialog says: name it, and say that photos and videos taken
**today** will leave this phone as soon as they finish backing up. Not a second dialog — the same
one, telling the truth about the setting actually selected.

Changing an existing Archive album *to* Immediate re-raises the confirmation. It widens what was
consented to, so the previous consent does not cover it.

### It needs no special-casing anywhere

Neither zero-age option is a separate code path. Both set the cutoff at the present moment; what
differs between them is when the pass runs, not which files it finds:

```kotlin
val archiveCutoffEpochSeconds = when (age) {
    Immediately, AtNextPass -> nowEpochSeconds
    else -> nowEpochSeconds - age.seconds
}
```

The eligibility query, the worker and TASK-011's `archivableBytes` projection are all unchanged —
they already take the cutoff as a parameter. Resist branching on the age value inside the removal
code; a second path through the one operation that cannot be undone is how it acquires an untested
variant. The difference belongs in the scheduler.

### The Sync age uses the same four values — revised 19 Aug 2026

An earlier draft here argued that **Immediately** must not transfer to TASK-011's video downscale
age, on the grounds that Archive removes a file the user consented to remove while downscaling
degrades a clip in place. Ian's call is that both settings offer the same four values, so the app
has one vocabulary of ages rather than two.

The distinction the earlier draft was protecting has not gone away; it has moved to where the user
can see it. TASK-011 carries the flag: *recent video is never touched* is recorded in MILESTONES as
a requirement, and Immediately reaches exactly the clip that requirement was written for. The clip
stays in the gallery and stays playable, which the founding failure did not — but an export from it
is capped until the original is fetched back.

So: same values in both, and the Sync age carries a warning the Archive age does not need.

### When the archiving happens — a nightly pass, and two ways to opt out of waiting for it

Asked by Ian, 19 Aug 2026: can archiving run only at night, so a mistake has time to be caught? And
then refined — rather than redefining Immediate, offer **both** an immediate option and an "at the
next archiving pass" one.

Yes, and the refinement is the better shape. **An age minimum guards against the choice; a delay
guards against the mistake.** Setting the wrong album to Archive is a mistake, and no age setting
catches it — a 30-day floor still removes the wrong album's files, just later. A window between
deciding and acting is what actually helps, and it helps at every age.

So archive removal becomes a **scheduled nightly pass**: nothing goes at the moment it qualifies, it
goes at the next pass. And the option list carries the escape from that wait at the top:

> **Immediately · 1 week · 1 month · 1 year**, defaulting to **1 month** — plus a separate
> "wait for the nightly pass" choice, since when work happens is not an age.

An earlier draft here proposed renaming Immediate to "No age limit" because one word was carrying
both *which files* and *when*. Ian's version solves it better and with fewer concepts: the two
timing answers become two entries, and the aged options do not need the distinction at all. A file
that has sat for ninety days does not care whether it goes now or at 3am, so only the zero-age end
has a real choice to make. One dropdown, no second control.

### Neither option removes anything without a tap

This is the part the schedule does not get to decide. `LocalCopyRemover` uses
`MediaStore.createTrashRequest(...).intentSender`, which **launches only from an Activity** — the
same constraint TASK-011 records for `createWriteRequest`, with the same 2000-URI cap. No background
worker trashes files at 3am, and none trashes them the instant an upload verifies either.

So both options mean *becomes ready for removal*, not *is removed*:

| Setting | A photo taken today becomes ready | It actually goes |
|---|---|---|
| **Immediately** | as soon as it is verified in OneDrive | next time the user is in the app |
| **At the next archiving pass** | at the next nightly pass | next time the user is in the app after that |

If the user is rarely in the app the two converge. If they look daily, Immediately clears today's
photos today and the pass option clears them tomorrow. That is a smaller difference than the names
suggest, and the UI should not oversell it.

**The dialog is never raised unprompted.** Not while the user is doing something else, and not
because a background pass finished. The app surfaces a pending batch — "12 files ready to archive
from Camera" — and the user taps it. An unrequested system trash dialog interrupting someone in
Settings is both alarming and the wrong moment to be making that decision. This matches TASK-011's
recommendation for the proxy grants; there is one mechanism here, not two.

**Consequence for the mode's description:** Archive cannot promise that an album empties on its own.
It empties as batches are approved. Say that where the mode is chosen, next to the fact that it
drains by age rather than all at once.

### The app does run continuously — the tap is a separate thing

Ian, 19 Aug 2026, on an earlier draft's phrase "next time the app is opened": the app should be
running 24/7 unless the phone is off or offline. That is correct, and the wording was wrong. Worth
writing down precisely, because the distinction decides how this feature behaves.

**Everything except the approval already runs with the app closed.** Scanning, uploading, verifying
against Graph, working out what is eligible, the nightly pass itself — all of it is WorkManager,
content-triggered on `MediaStore` plus the periodic net, exactly as backup works today. The app does
not need to be open, and it does not need a foreground service either; a persistent service would
burn battery and add a permanent notification for nothing, since WorkManager already wakes the app.

**What cannot be background is not the app, it is the tap.** `createTrashRequest` returns an
`IntentSender` that must be launched from an Activity, and the dialog it raises is drawn by
MediaProvider for a human to answer. A live process does not help: at 3am nobody is looking at the
screen, so nothing gets approved and nothing moves. "App running" and "user present" are different
conditions, and this one requires the second.

So the accurate phrasing is **next time the user is in the app**, not next time it is opened — and
the reason is the human, not the process.

### The tap does not have to happen at removal time

This is the part that makes overnight application possible, and it is TASK-011's option 2 rather
than a new idea. `JobInfo.Builder#setClipData` carries granted URI permissions into background work,
so a grant obtained while the user *is* present outlives the Activity that obtained it. The user
approves a batch at 2pm; the pass at 3am applies it with no dialog, because consent already
travelled with the job.

That would deliver what was asked for — archiving that happens overnight — at the cost TASK-011
records: it sits outside WorkManager, Hilt injection into a `JobService` is manual, and **whether a
`ClipData` URI grant survives a reboot is unverified**. If it does not, every restart empties the
pool and re-prompts. Worth testing on hardware before building on it.

Without that path, the shape is: the pass prepares the batch overnight, and it is applied the next
time the user is in the app, in one tap.

### How often that tap actually happens

Once per batch, and a batch is up to **2000 URIs** — the same cap as everywhere else in this app.

- **First archive of a large album:** a few taps, once. 6,000 files is three dialogs.
- **Steady state:** whatever accumulates between passes, which for one album is a handful of files.
  Nobody adds 2000 photos to an archived album overnight.

So the honest description is not "the app waits for you to open it" but "the work happens
continuously and you confirm a batch occasionally" — which is the same sentence TASK-011 arrives at
for proxy grants, for the same platform reason.

### What the grace window is actually worth

Under **At the next archiving pass** there are two catches: overnight before anything is queued, and
the approval prompt afterwards. Under **Immediately** there is one — the prompt. That is the whole
trade, and it is a fair one to offer, because the prompt names the album and the count before
anything moves.

It is also why the default stays at 30 days rather than at either of the new options. The default
should be the setting that is hardest to regret.

### Backup should not be night-only, and this is the one part to push back on

The same restriction applied to uploading would be a mistake, and it runs against the decision made
earlier the same day.

- **There is nothing to undo.** A backup that should not have happened leaves a file in OneDrive
  that can be removed there. Nothing local is lost, nothing leaves the gallery. The grace window
  protects against destruction, and uploading destroys nothing.
- **It costs real protection.** Holding uploads until night means a clip shot at 9am has one copy
  for fourteen hours. The founding failure of this project was a video that went missing; a phone
  lost or broken during that window loses the footage outright.
- **It contradicts the property just established** — auto-syncing an album is unattended and
  immediate, and it is the one thing the platform lets the app do without asking.

The overnight case that *is* worth building already exists as a milestone item: a start time for the
**first** whole-library backup, where the cost is hours of transfer and battery rather than a delay
to safety. That one is scheduled; steady-state backup is not.

Proxying sits in between and is not settled here. It rewrites files, so a window has some value, but
it never removes anything from the gallery — worth deciding with TASK-011's worker rather than
assumed either way.

### How the pass gets scheduled

The mechanism already has a precedent in this codebase and in the milestones: WorkManager
`setInitialDelay` to the next occurrence of the chosen hour, then periodic daily —
`BackupScheduling.enable` already builds a periodic request with constraints and can carry a second
unique job. Add `setRequiresCharging(true)`; the phone is usually on a charger overnight, and it
sidesteps Doze deferring work on an unplugged idle device.

The hour should be the same user setting as the first-backup start time rather than a second one.
One "overnight" hour that both use is easier to explain than two, and nobody wants them different.

### Open — should the window be guaranteed rather than incidental?

An album set to Archive at 2am gets an hour before the 3am pass. Set at 2pm it gets thirteen. If the
window is the safety mechanism, it should not depend on when the user happened to tap.

The fix is to record when the mode was set and require the pass to be at least *N* hours later —
twelve, say — so the first pass after a change is skipped when it falls too close. It costs a column
on `album_preferences` recording the timestamp, which is a schema change and therefore an escalation
per CLAUDE.md. The cheaper alternative is to keep the timestamps in `BackupSettings` keyed by album
name, avoiding the migration at the cost of splitting album state across two stores.

Recommended: build the nightly pass first without it. The approval prompt is already a second
catch, and a guaranteed window can be added later without changing anything else. Ian's call.

### Two age settings, both visible

Decided by Ian, 19 Aug 2026: the user can tell a syncing age from an archiving age. So they are two
separate controls, both live in Settings, and neither is hidden inside a dialog to spare the user
the distinction. The earlier recommendation here — keep Archive's age inside the confirmation — is
withdrawn.

What the concern actually reduces to is **labelling**. Two fields both called "age" would be the
confusing thing; two settings are not. Name each by its consequence rather than by its mode:

| Setting | Reads as | Governs |
|---|---|---|
| Sync | *Downscale videos older than…* | a clip shrunk in place, still in the gallery |
| Archive | *Remove files older than…* | photos and video leaving the phone |

Written that way they are plainly two different actions that happen to take a duration each, rather
than two flavours of one idea. The other differences fall out of the same labels: only Archive
offers **Immediate**, and only Sync is limited to video. If a label needs a sentence underneath to
explain which is which, the label is wrong.

### Archive should not ship before v0.4
Once an album is archived the only route back is retrieval. Without it, Archive means "gone until you
go and use the OneDrive app", which is worse than not offering the mode. Build it; gate it behind
retrieval landing.

### Naming — "Backup" is the collision, not "Archive"
Ian reads backup as *moving to a secure location*. Common usage runs the other way: Time Machine,
OneDrive backup and Google Photos backup all copy and leave the original alone, and today's wording
test says the same. Two readings of one word, with opposite consequences for someone's files, is the
worst possible label for the most destructive mode.

So the destructive mode is **Archive**, which carries "moved to long-term storage, not here any more"
without ambiguity. If that still reads too softly, **Cloud only** states the end result outright and
is harder to misread than either.

The copy-and-leave-alone mode keeps **Backup**, which matches how the rest of the industry uses the
word — and Archive now occupies the meaning Ian wanted a name for.

### Room migration — escalation, per CLAUDE.md
`AlbumPreferenceEntity.isEnabled: Boolean` becomes a four-valued mode. Schema change, so it needs a
migration and Ian's sign-off.

**Map `true` to Backup**, and nothing to Archive ever. Today an enabled album uploads and touches
nothing local; optimising happens only on a tap. Mapping to Sync would switch on space management
nobody chose; mapping to Archive would empty their gallery. `false` maps to Off.

### Default for new albums
`DEFAULT_ENABLED = true` exists so the safe failure is "uploaded something you did not need" rather
than "lost something you did". The equivalent under four modes is **Backup**. Configurable in
Settings, offering Off, Backup and Sync — **Archive is not selectable as a default.**

## Optimise is not a feature — it is what Sync mode does

Ian, 19 Aug 2026: *"The Optimise is the actual SYNC function that needs to happen automatically
behind the scenes."*

Correct, and it collapses two things this spec was treating separately. In the mode ladder, **SYNC
already means upload and optimise** — that is the whole difference between it and BACKUP. So
"Optimise" should stop being a user-facing concept with its own section and its own switch; setting
an album to Sync *is* asking for its photos to be optimised.

### What that changes
- **The Settings "Optimise automatically" toggle is a stopgap.** It exists because the mode dropdown
  is not built yet and a global switch was the only place to put the preference. Once modes ship it
  is redundant, and worse than redundant — a global "off" and a per-album "Sync" would contradict
  each other, and there is no good answer for which wins.
- **That section should become the default-mode selector** described above, not a switch. "What
  should new albums do?" is the question actually left over once modes exist.
- **The word "Optimise" survives only as an explanation**, in the copy that says what Sync does to a
  photo. Not as a button, not as a section, not as a setting.

### How automatic it can actually be
Everything except the last step already runs without the user:

| Step | Needs the user? |
|---|---|
| Notice a photo is verified and eligible | no |
| Choose what to optimise, largest first | no |
| Generate the proxy — decode, downscale, badge, copy EXIF | no, it writes to app cache |
| **Write the proxy over the original** | **yes — one dialog per batch, from an Activity** |
| Update the ledger | no |

Only the write needs a tap, because `MediaStore.createWriteRequest` is how Android lets one app
modify another's media and it cannot be raised from the background. Samsung did it silently because
Samsung Gallery *is* the system gallery — the same privilege that let it keep a private cloud index,
and equally unavailable here.

**With sync running automatically the batches are small**, a handful of new photos between runs, so
the realistic shape is: a notification when there is something to do, one tap, done. That is as
close to behind-the-scenes as the platform allows a third-party app, and the UI should describe it
that way rather than promising invisibility it cannot deliver.


## 2. Auto Optimise Photos — a stopgap, superseded by Sync mode
Built 19 Aug 2026 as a Settings toggle, because the mode dropdown does not exist yet. See above:
once modes ship this becomes the default-mode selector rather than a switch. While it exists it is
the on/off for TASK-011's worker, and it does not replace the free-space floor — the two answer
different questions:

- **The toggle** — may the app optimise photos without being asked each time?
- **The floor** — at what point is it worth doing?

Without the floor, an "on" toggle would proxy every eligible photo the moment it qualified, degrading
images on a phone with plenty of space for no benefit. That is what TASK-011's hard rule 4 forbids.
Keep both, and put them together so the relationship is visible.

## Target Settings screen, once modes ship

Ian, 19 Aug 2026, asking whether Storage and Optimise photos both disappear. Optimise photos does.
Storage does not — but **nothing currently in it survives**, which the spec had not followed through.

| Section | Fate |
|---|---|
| Appearance | stays as built |
| Account | stays as built |
| Your cloud files | stays as built |
| Automatic sync | stays; still sync-on-change plus the metered choice |
| **Optimise photos** | **gone.** Becomes per-album Sync mode, plus a default-mode selector |
| **Storage** | stays as a section; every control in it is replaced |

### Why Optimise photos goes entirely
Setting an album to Sync *is* asking for its photos to be optimised. A global switch beside it would
contradict a per-album choice with no good answer for which wins. What is genuinely left over is
"what should new albums do?", so that is what the section becomes — and it is a default-mode
selector, not a switch, which means it belongs beside the mode UI rather than under a heading named
after one mode's side effect.

### Why Storage stays but empties out
It holds two things today. The verified count is informational and survives. **The "Remove from this
phone" button does not**: removing local copies becomes Archive mode, per album, with a minimum age.

That is not merely a relocation. The current button removes across every album at once with no way
to choose — which is precisely the all-or-nothing behaviour criticised in Samsung's "free up phone
space" in MILESTONES. Keeping it beside a per-album Archive mode would leave the app offering both
the careful version and the blunt one.

What Storage gains instead:

- the **free-space floor** from TASK-011
- the **sync schedule**, including the overnight start time for the first run
- the space figures — verified count, what is reclaimable, what has been reclaimed

Leaving it as "how much of this phone is in use, and when does anything change".

**Open, minor:** whether the schedule truly belongs here or under Automatic sync, which is otherwise
where "when does syncing happen" lives. Ian asked for Storage; recorded as his call, worth a second
look when the screen is actually rebuilt.

## 3. Storage section — where scheduling lives
See the target-state table above for what this section keeps and loses. Absorbs the start-time item
added to v0.2 today: when the first whole-library upload runs, defaulting
to overnight and requiring charging. Storage already holds the verified count, the floor, and the
optimise controls, so scheduling belongs beside them — all four answer "how much of this phone is in
use, and when does anything change".

## 4. The OneDrive tab — remove it, or repurpose it

**Do not add thumbnails.** The design principle in CLAUDE.md and MILESTONES rules out "no photo grid,
no thumbnail browser" in as many words, and that framing is Ian's own. Graph returns thumbnails
readily, which is exactly why the principle exists — to stop the easy addition that turns this into a
worse gallery than the one already on the phone.

**Better than removing it: make it the retrieval list.** v0.4 needs "a plain retrieval list — not a
photo browser", and `BrowseScreen` is already a plain list with breadcrumbs and sorting. Repurposing
it costs less than deleting it and then building its replacement, and it keeps a job the app
genuinely needs.

So: strip it of general browsing, point it at what is *not* on the phone, and let a row be fetched
back. If v0.4 slips, removing the tab in the meantime is reasonable — but the code should not be
deleted.

## 5. Visual refresh — do it after the structure, not with it
The IA changes above alter what is on each screen. Restyling screens that are about to change shape
means doing the work twice and verifying neither properly.

Direction, once the structure settles: Material 3 with dynamic colour from the wallpaper, a more
generous type scale, and fewer boxed rows in favour of grouped list sections.

### What Ian asked for, 19 Aug 2026

Stated after the SAF session, with the explicit framing that it comes **after the functionality
works** — recorded here so it is not lost, not scheduled here.

> *"The entire UI needs an overhaul — it looks very dated. But I figured we could tackle that after
> all the functionality is working."*

1. **Settings moves off the tab bar into a hamburger drawer.** The tab row is carrying navigation it
   was never suited to, and it gets worse as Album Modes and Storage grow. A drawer also gives the
   Help menu somewhere to live.
2. **A robust Help menu**, with clickable links from key terms, and **plain-language explanations of
   exactly what each of the four album settings does**. Ian, 19 Aug 2026.

   An earlier draft of this section claimed the app invents vocabulary the user has never met. Ian's
   correction: most people know what archive, optimise, sync and verified mean. That is right, and it
   sharpens what Help is actually for.

   **The risk is not an unknown word, it is a wrong assumption about the mechanism.** Someone who
   knows perfectly well what "archive" means may still assume it is reversible from the gallery, or
   that it behaves the way Samsung's did — where deleting on the phone deleted the cloud copy too.
   Those are expectations, not definitions, and a glossary would not touch them. Per-mode
   explanations would.

   So the four mode entries are the centre of Help, not an appendix to it. Each says what happens to
   the cloud copy, what happens to the file on the phone, whether space is freed, and how to get back
   to the original. The sentences that carry the most weight are the counter-intuitive ones:

   | Mode | The sentence that has to land |
   |---|---|
   | **Off** | nothing is copied and nothing on the phone changes |
   | **Backup** | your files are copied and **no space is freed** — backup is safety, not space |
   | **Sync** | the photo stays in your gallery and still opens everywhere, but it is now a smaller version; editing at full quality means fetching the original back |
   | **Archive** | the files **leave your gallery**; the only way back is fetching them in this app, not from your gallery app |

   Two rules on the wording, both from CLAUDE.md:

   - **Never say a local removal is recoverable.** The guarantee available is the verified cloud
     copy — remote confirmation plus a matching byte size — and that is the only one Help may state.
   - **Help and the Archive confirmation must not drift.** They describe the same operation in two
     places, so the dialog should draw on the same strings rather than paraphrasing them, or the two
     will disagree after some later edit and the disagreement will be invisible.

   **These are the strings that must be translated well.** Ian's first-run flow lets the user pick a
   language, and a mistranslated mode explanation is the one that costs somebody their photos. If
   translation quality is ever uncertain for a locale, this is the text to be conservative with —
   not the button labels.
3. **A first-run flow**, covering language, permissions, cloud service (OneDrive / Google Photos /
   Amazon Photos), and default settings. See below.

### First run — moved to TASK-014

Ian expanded this on 19 Aug 2026 into a guided setup with conversation-bubble explanations, plus two
gates the engine cannot start without: **which directories to pull from**, and **what to do with the
library already on the phone**. It outgrew a bullet here and now has its own spec.

Two things from it that reach back into this task:

- **Archive is never a bulk first-run choice.** TASK-012 already gates Archive behind v0.4
  retrieval; TASK-014 adds that it is not offered across every album in a wizard either. It stays a
  per-album decision with its own confirmation.
- **The mode explanations are shared strings**, not two descriptions of the same operation. The
  bubbles in the wizard and the Help entries above draw on one set, or they drift.

### Known: the XML theme is hardcoded Light
Found 19 Aug 2026, deferred by Ian to this task rather than fixed then.

`res/values/themes.xml` sets `android:Theme.Material.Light.NoActionBar` unconditionally, and there
is no `values-night` variant. Compose is unaffected — its colours come from `MaterialTheme.colorScheme`
and dark mode renders correctly — but `android:windowBackground` comes from this theme and paints
before Compose draws its first frame, so a cold launch in dark mode starts light.

It also interacts with the Appearance setting: the `-night` resource qualifier follows the *system*
setting, not the app's own choice, so forcing Dark on a light phone leaves the launch background
light every time. Not observed misbehaving — a screenshot cannot reliably catch a flash — so this
is a mechanism worth closing rather than a reported defect.

Fix is either a `values-night/themes.xml` with a dark parent, or `android:windowBackground` pointing
at a colour resource that has a night variant. Worth doing here because this file gets touched
anyway, and because it is the hardcoded-colour rule showing up somewhere that is not Kotlin and is
therefore easy to miss.

**This is the highest-risk change in the app for the dark-mode rule.** A restyle is exactly when
someone reaches for a specific colour, and CLAUDE.md is absolute: nothing hardcoded outside
`ui/theme/`, colours from `MaterialTheme.colorScheme`, text inheriting `LocalContentColor`, and both
themes checked on a device before it is called done. The teleprompter shipped unreadable in dark mode
this way.

## Acceptance
- An album can be set to Off, Backup, Sync or Archive, and the choice persists
- Backup mode never optimises or removes a local file, verified by observation not by reading code
- Existing enabled albums land in Backup after migration — never Sync, never Archive
- Archive removes nothing until the item is verified in OneDrive and older than the archive age
- Archive is unavailable as the default for new albums
- The default for new albums is configurable and starts at Backup
- Auto-optimise is a Settings toggle governing TASK-011's worker
- Scheduling lives in Storage with the other space controls
- The OneDrive tab either fetches things back or is gone; it does not grow thumbnails
- Verified on hardware in both themes, per CLAUDE.md
- Switching an album to Archive raises a confirmation that names what leaves the gallery; cancelling
  leaves the mode unchanged, and cancel is the default
- That confirmation never claims a local removal is recoverable
- Switching an album away from Archive raises nothing
- Nothing in the app asks for Archive approval a second time once the mode is set
- The archive age offers Immediately / 1 week / 1 month / 1 year, persists, defaults to 1 month,
  and Immediately is never reached without choosing it
- **Under Immediate, nothing is removed until it is verified in OneDrive** — the option removes the
  age wait and nothing else, verified by observation on a file mid-upload
- Selecting Immediate changes what the confirmation says, naming that media taken today will leave
  the phone once backed up
- Switching an existing Archive album to Immediate re-raises the confirmation
- The Sync age and the Archive age are separate controls, each labelled by what it does
- Under an aged setting or "At the next archiving pass", nothing becomes ready for removal until
  the pass runs; under Immediately it becomes ready as soon as the file is verified
- Neither zero-age option removes anything without the user approving the batch, and the trash
  dialog is never raised unprompted
- Backup is **not** restricted to that pass; uploading stays continuous and content-triggered
- The pass prepares a batch unattended and applies it when the user approves, naming the album and
  the file count before anything goes

## Notes
- Tabs are wired in `MainActivity.kt` around lines 88-106; the browse screen is `ui/browse/`.
- The album row and its `Switch` are in `BackupScreen.kt` around line 215.
- String *values* were updated to sync wording in 93bf7d4; the `backup_` string *names* are internal
  and were deliberately left, so do not treat them as stale.
