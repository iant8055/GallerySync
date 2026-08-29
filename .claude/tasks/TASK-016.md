# TASK-016 — The Archive tab: validate, then confirm, then remove

Milestone: v0.3 — space management
Requested by: Ian, 26 Aug 2026
Replaces: the Archive summons on the Albums tab, added 25 Aug
Depends on: `redundantLocalCopies`, `confirmStillInCloud`, `createTrashRequest`

## What Ian asked for

> *"I don't like the Removal being on the Album Tab. When a user sets an Album to be Archived, I want
> the user directed to the Cloud Check tab (which we will repurpose as the Archive Tab) where they
> will see the folder name and a list of all the files in that folder. The User will see each file
> being validated — each file from the Gallery will be listed and a bar indicating the progress of
> that file's verification. Its entire contents must be Sync'd with OneDrive, optimized photos, then
> the images/videos moved to Recycling Bin."*

Then the prompt, once validation finishes:

> **All files VALIDATED — All files are confirmed in OneDrive**
> **Archiving them will free up XXX GB on your phone**
> **Do you want to continue?**
> **YES   NO   DELAY (1hr / 12hr / 1day)**

> *"Clicking YES — the user will see the same list of files except instead of validating it will
> indicate Moving to Recycle Bin."*

## Why this is better than what it replaces

The current summons is a two-line prompt on the Albums screen, above a list of other albums. It states
a count and a size and offers a button. It is the largest irreversible action in the app presented in
the smallest space available, competing for attention with unrelated rows.

The new design gives removal its own screen and its own time. Three properties it gains:

1. **The user sees the names.** Not a count — the files. That is the difference between authorising a
   number and authorising a list, and this is the one action in the app where that distinction is
   worth a whole screen.
2. **Validation is visible while it happens**, so the guarantee stops being a claim in a sentence and
   becomes something watched. The app's promise is "verified in OneDrive"; this shows the verifying.
3. **The two phases are separated.** Validating and removing look the same on screen and mean opposite
   things, so running them as two passes over the same list — with a confirmation between — makes the
   moment of no return unmistakable.

## Consent — this does not add a second one

CLAUDE.md is explicit: *"The album mode is the consent, confirmed once when the mode is set… After
that the mode stands until it is changed: no per-file approval and no repeat prompting."*

Nothing here breaks that. The mode remains the consent. What this screen adds is the **summons** —
which already exists and exists only because `createTrashRequest` cannot launch without an Activity —
made legible. It is one prompt per archive operation, never one per file.

**DELAY is the exception worth noting, and it is the user's own choice**, so it is not repeat
prompting in the sense the rule forbids. It is the user saying "ask me in an hour".

## Scope

### The tab

Repurpose **Cloud check** as **Archive**. Setting an album to Archive navigates the user there.

**Decided 26 Aug — Cloud check is dismantled, not relocated.** Ian gave each piece a destination, and
nothing is left over:

| What is on Cloud check today | Where it goes |
|---|---|
| **Checking OneDrive** — the reconciliation readout | the first-run wizard (TASK-014) |
| **Photos on Phone** — the local scan counts | the first-run wizard (TASK-014) |
| **Where backups go** — the destination root and its change dialog | **Settings**, as a default |
| Source folders | already moved to Settings, 26 Aug |
| Gate 2 — what to do with the existing library | already a wizard step in TASK-014 |

That leaves the tab free to become Archive rather than sharing it.

**Why this is right rather than merely convenient.** Reconciliation answers "how much of my library is
already safe?", which is a question asked once, at the moment of setting up — it is the first honest
number, and TASK-014 is built around it. Keeping a permanent tab for a one-time answer is what made
Cloud check feel like a screen looking for a purpose. The destination is a setting by the same test:
chosen once, changed rarely, consequential when changed.

**Consequence for TASK-014:** the wizard now owns the reconciliation UI outright rather than linking
to a tab that will no longer exist. Its Gate 2 already depends on those numbers, so this removes an
indirection rather than adding work.

### Phase 1 — validation

- Folder name, then every file in that folder, by name
- Per-row state as validation reaches it, and progress while it does
- Nothing is removed, nothing is written, and leaving the screen costs nothing

**Decided — a green check mark per file**, not a bar. Ian, 26 Aug. Verification is one Graph listing
per album rather than a request per file, so a bar filling per row would be animation rather than
information. A row resolves to a tick when its file is confirmed.

### The prompt

Ian's wording, with the numbers filled in. Three answers: **Yes**, **No**, **Delay** (1 hour, 12
hours, 1 day).

**A file not found in OneDrive is uploaded, not failed.** Ian, 26 Aug: *"If a file can not be found
the local copy will be backed up."* So validation is not a read-only inspection — it is "make this
album true", and the missing category becomes work rather than an error. Only after that upload
completes and verifies does the file get its tick.

**Validating an optimised photo means checking the full version is in OneDrive.** Ian, 26 Aug:
*"part of the validation is if the file is Optimized that a full version is sitting in OneDrive."*
Not any copy — the original. The local file is a 2048px rewrite, so the remote must be compared
against the size the ledger remembers, never against the file on disk.

**This was broken and is now fixed.** `confirmStillInCloud` compared the remote size against the
*local* size, so every optimised photo would have been reported "no longer in OneDrive" — false, and
the most alarming thing the screen can say. It could not fire before 26 Aug because Archive could not
see proxied files at all; the fix that let it see them exposed this. Corrected the same day: the
expected remote size now comes from the ledger for proxied rows.

**Partial validation — decided 26 Aug.** Ian: *"if a file could not be checked then the file would
give it a big red X rather than a Green check — then we would give the user the option to Archive all
Green checked files or do nothing."*

That satisfies CLAUDE.md's *"if we could not ask, we do not remove"* **by construction** rather than
by a check that has to be remembered: a red file is simply not in the set being archived. The rule
becomes a property of what the button acts on.

The three categories map onto two marks, because the upload rule collapses one of them:

| `confirmStillInCloud` | Mark | Why |
|---|---|---|
| confirmed | **green tick** | the full version is in OneDrive |
| missing from OneDrive | upload it, then **green tick** | Ian's rule: a file not found is backed up, not failed |
| could not be checked | **red X** | we could not ask, so it stays |
| upload failed | **red X** | same answer, same reason |

So the prompt has two shapes. All green: Ian's wording verbatim. Some red: the count and size describe
**only the green set**, and the button says so — *archive the confirmed ones, or do nothing*.

**A red file is never a dead end.** The album stays in Archive mode, so the next run retries it. What
could not be checked today is usually a network answer rather than a permanent one.

**If every file is red there is nothing to offer**, and the screen must say that rather than present a
button that archives an empty set — the rule from TASK-014: never offer an action that cannot
succeed.

### Phase 2 — removal

- The same list, same order, now indicating **Moving to Recycle Bin**
- Per-row state as each is trashed

**Android's cap is 2000 URIs per `createTrashRequest`**, and the request is per batch. An album larger
than that needs several system dialogs, and the screen has to make that read as one operation
progressing rather than as the app asking again because something went wrong.

## The optimise question — answered 26 Aug

Already-optimised photos are archivable, and the proxy is what gets removed. An album set straight to
Archive never needs proxies made, because the photos are leaving; the point of the check is that the
*full* version is safe in OneDrive before the local 2048px copy goes.

Works as of 26 Aug: a proxied file is matched by MediaStore id, so Archive can see it. Before that fix
it could not, which is why an album taken Sync then Archive offered 2 of 13 files.

## Acceptance

- Setting an album to Archive navigates to the Archive tab
- Every file is listed by name, never only counted
- Validation state is a per-row tick, honest about what work is actually per file
- A file missing from OneDrive is uploaded and then ticked, never merely reported
- An optimised photo is validated against the **original's** size, never the proxy's
- Nothing is written or removed during validation
- The prompt states the freed size and offers Yes, No and Delay
- Partial validation is presented as itself, never as "All files VALIDATED"
- A file that could not be checked, or whose upload failed, shows a red X and is excluded from the
  archived set — the guarantee holds because of what the button acts on, not because of a check
- The count and size in the prompt describe only the green set
- An all-red album says so instead of offering a button that archives nothing
- A file that could not be checked is never removed
- Removal reuses the same list, so the user sees the same names they authorised
- An album over 2000 files reads as one operation across several system dialogs
- The local removal never claims recoverability — the trash caveat travels with it
- Verified on hardware in both themes, per CLAUDE.md

## What this removes

The `ArchiveReadyPrompt` on the Albums screen, and the held-back reporting moved there on 26 Aug.
Both are superseded: the summons becomes navigation, and the held-back explanation belongs in the
prompt where the decision is made.

---

## The exit warning — a third surface for the summons

Added 28 Aug 2026, at Ian's request, and it belongs to this task because the summons does.

> *"We can warn the user when they go to close the app if there are files still in Archive that haven't
> been attended to. Give them a little caution pop-up."*

### What it is, and what it is not

It is the summons, again, at the moment someone walks away from a job that is checked, verified and
waiting on one tap. It is **not** a second consent: the album mode is the consent, given once when the
mode was set, and CLAUDE.md forbids re-asking. This says only that the job is unfinished — a statement
about state, not a question about intent.

It replaces the notification. `POST_NOTIFICATIONS` had two uses left after the SAF finding: saying free
space is low, which duplicates Android, and summoning the user to a batch, which is this. A dialog needs
no permission, cannot be denied, and cannot be silently switched off.

### It cannot catch most exits

Android has no general "app is closing" event. Only the back gesture from the root screen is
interceptable; Home and a swipe from Recents are not, and on gesture navigation Home is the common way
out. So this is a net, not a guarantee, and **the Albums tab summons stays the surface that is always
there.** Nothing may become reachable only from here — the same rule TASK-011 recorded about
notifications, for the same reason.

### The snooze has to be persisted, and was not

`ArchiveViewModel.delayedUntil` was in-memory, so a Delay died when the app closed — which is exactly
when this dialog fires. Someone who chose "1 hour" and then left would have been warned anyway, by the
act the snooze existed to cover. Now `archive_delayed_until` in `BackupSettings`.

### Where the decision lives

`ExitWarning.shouldWarn(readyCount, delayedUntilEpochMillis, now)` — pure, unit-tested, six cases.
`BackupUiState` carries both inputs so the root can decide without touching `ArchiveViewModel`, which
scans the device on creation and would run that on every launch to arm a dialog usually not needed.

### Acceptance

- [x] Back with files ready shows the warning; back with none does not
- [x] A live delay silences it; an expired one does not
- [x] The delay survives the app being closed
- [x] "Archive now" lands on the Archive tab; "Leave" closes the app
- [x] Buttons name their actions rather than OK/Cancel
- [x] Verified on the Fold 4 cover screen in both themes, no crash
