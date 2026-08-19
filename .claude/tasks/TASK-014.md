# TASK-014 — Guided first run

Milestone: v0.3.0 / v0.4.0 — the setup the whole app depends on
Requested by: Ian, 19 Aug 2026
Depends on: TASK-012 (album modes), TASK-011 (settings), the SAF finding of 19 Aug 2026

## What Ian asked for

> A guided first setup with conversation-bubble pop-ups that walk the user step by step through
> configuring backup, sync and Archive, and explain what the default settings do. Then, **before
> anything works**, the user decides what to do with the photos and videos already on their phone,
> and which directories to pull from — DCIM, Camera Roll, or both.

Two of those are not tutorial steps. They are **gates**: the engine has nothing correct to do until
they are answered, and answering them wrongly is expensive on a library this size. Ian's phone holds
**8,508 items across 90 albums**, which is the scale every decision here applies to at once.

## The two gates

### Gate 1 — which directories to pull from

Today `MediaScanner` scans everything MediaStore returns, which is how 90 albums appear. Most people
do not want WhatsApp thumbnails, Screenshots and every app's cache folder treated as their library.

**This is the same question as the SAF grant, and should be the same picker.** Today's finding
established that a persisted tree grant is what lets proxying run without a tap. The folders the
user chooses to pull *from* are exactly the folders the app later needs to write *into*. So use
`ACTION_OPEN_DOCUMENT_TREE` here, take persistable read+write on each, and let the result serve
both purposes: it scopes the scan and it carries the write access.

**The constraint that follows is load-bearing:** every album that can ever be set to **Sync** must
sit under a granted tree, or proxying silently cannot touch it. Ian settled this on 19 Aug 2026 by
scoping the scan to the grants — an ungranted album is never listed, so it can never be given a mode
the app cannot carry out. Assert the invariant anyway; a mode that quietly does nothing is worse
than a mode that cannot be selected, and this is the kind of guarantee that erodes when someone
later adds a second way to reach the album list.

*Note on naming:* "Camera Roll" is not an Android folder. Real candidates are `DCIM/Camera`,
`Pictures`, `Downloads`, and per-app folders. Present what the device actually has rather than a
fixed list, with DCIM preselected.

### Gate 2 — what to do with what is already there

The highest-regret moment in the app. One choice applies to 8,508 files.

Recommended options, and one deliberate omission:

| Offer | Effect |
|---|---|
| **Choose per album** *(default)* | nothing happens until the user visits Album Modes. Safest, and the honest answer for someone who has not seen the app work yet |
| **Back up everything** | every selected album to `BACKUP`. Nothing local changes, nothing is freed. Costs OneDrive quota and a long first upload |
| **Back up and free space** | every selected album to `SYNC`. Photos get proxied, video left whole |

**Archive is not offered here, and that is a recommendation with teeth.** Setting 90 albums to
Archive in a wizard — before the user has watched the app work, and before v0.4 retrieval exists —
is the largest irreversible action the product can perform, chosen at the moment the user knows
least about it. It stays a per-album decision made deliberately on the Album Modes screen, with its
own confirmation. TASK-012 already gates Archive behind retrieval landing; this adds that it is
never a bulk first-run choice either.

**Warn about the first upload.** "Back up everything" on this library is hours of transfer. The
milestones already carry a start-time setting for the first backup, defaulting overnight with
charging required — the wizard is where that gets offered, not a surprise the user discovers.

### The distinction hiding in "currently on their phone"

Ian's wording separates the **existing library** from what arrives later. Album modes do not make
that distinction: a mode is a standing instruction covering past and future files alike, which is
exactly what CLAUDE.md's consent rule relies on.

**Recommendation: the first-run choice sets modes and nothing more.** It is a bulk way of doing what
the Album Modes screen does one row at a time, so "what happens to existing photos" and "what
happens to new ones" have the same answer. The alternative — a separate notion of pre-existing files
— means new state, a new migration and two rules where users expect one. Not worth it.

## The bubbles are the *presentation* half of informed consent

Clarified by Ian, 19 Aug 2026, correcting an earlier draft of this section that argued against a
position he had not taken:

> *"I do not mean that the bubbles replace consent notifications. Just so that we can control
> (verify) that the user has seen the necessary information. They are not meant to replace Archiving
> approvals, just to explain step by step what each setting does."*

That splits informed consent into its two real parts, and the app should hold both:

| Part | Carried by |
|---|---|
| The information **was presented** | the bubbles, and a record that they were shown |
| The decision **was taken** | the Archive confirmation in TASK-012 guard 4, unchanged |

The second was never in question. The first is the new requirement, and it is more than a tutorial:
**the app keeps a record of which explanations have been displayed.**

### What "verify they have seen it" means in build terms

An acknowledged-explanations set, keyed per topic, in `BackupSettings` alongside the other
preferences. No
schema change — this is DataStore, not Room.

What it buys, in order of usefulness:

- **A destructive mode cannot be chosen before its explanation has been acknowledged.** Selecting
  Archive with no acknowledgement on file shows the explanation first, then the
  confirmation. Not a second consent step — the sequence a careful person would want anyway.
- **The tour becomes skippable without losing anything.** Skipping is fine, because the explanation
  reappears at the point of first use. Just-in-time is better teaching than a wizard nobody
  remembers, and the record makes the two routes equivalent.
- **The record is per topic, not per tour**, so adding a fifth mode later does not require re-running
  setup, and a user who saw the Archive explanation in the wizard is not shown it again.

### Advancing requires an explicit acknowledgement

Ian, 19 Aug 2026: a bubble is not dismissed by tapping Next — the user clicks **"I understand"** to
move on.

This upgrades what the record means. It stops being *displayed* and becomes *acknowledged*, which is
a materially stronger signal: a screen can be rendered and swiped past without a decision, but a
deliberately-placed button cannot be pressed by accident. Every use of the record below should read
"acknowledged" rather than "shown".

**Two things about how it is built, both of which protect the signal rather than the user:**

**1. Do not put it on every bubble.** A nine-step wizard where every step demands "I understand"
teaches people to click it without reading by about step four, and the ones that matter arrive after
that. Uniformity is what destroys the signal. So:

| Bubble | Advance control |
|---|---|
| Purely informational — what a storage floor is, where Settings moved | plain **Next** |
| The four album modes, and Archive above all | **"I understand"** |
| Anything describing a file leaving the phone or being rewritten | **"I understand"** |

The rest of this app already works this way: the Archive confirmation carries weight *because* it is
rare. A button that appears everywhere carries none.

**2. Name what is being understood.** A generic "I understand" is the fatigue pattern in miniature —
it can be pressed without the sentence above it entering the decision at all. Restate the
consequence in the button:

> **I understand — Archive takes these files off my phone**
> **I understand — optimised photos are smaller until I fetch the original**

Longer, harder to press reflexively, and it survives being the only thing the user actually reads.

**What it does not change:** the Archive confirmation in TASK-012 guard 4 still happens, separately,
at the moment the mode is set. Acknowledging an explanation during setup is not choosing Archive for
an album, and the two must never be collapsed — one is *I know what this does*, the other is *do it
to this album*.

**And the honest limit stands, slightly narrowed.** An acknowledgement proves a deliberate press,
not comprehension. It is a much better record than a render, and it is still not proof the user
understood — so no wording anywhere may imply they accepted a consequence by having pressed a
button in a tutorial.

**Leaving is not gated.** "I understand" gates moving *forward* through the tour, never exiting it.
Skip stays available at every step, and any topic left unacknowledged is presented again at the
point of first use — which is where the record earns its keep.

### Wording, shared not duplicated

The bubbles are where the plain-language mode explanations first appear, and they use the **same
strings** as the Help entries in TASK-012 and the Archive confirmation. Three descriptions of one
irreversible operation will drift apart, and the drift is invisible until someone reads two of them
side by side.

## Order of the flow

1. **Language** — first, because everything after it is text
2. **Cloud service** — OneDrive today; the picker is built for extension, see TASK-012
3. **Sign in** — nothing can be verified without it
4. **Media permissions** — `READ_MEDIA_IMAGES` / `READ_MEDIA_VIDEO`, which gate the scan
5. **Gate 1: source directories**, via the tree picker that also carries the write grant
6. **Scan and report** — "we found N photos and M videos in K albums", the first honest number
7. **Gate 2: what to do with them**
8. **Defaults explained** — storage floor, sync scope, ages, with the bubbles
9. **First backup start time**, if anything was set to upload

`POST_NOTIFICATIONS` is deliberately **not** in this list — see TASK-011. On a fresh install there is
nothing to notify about, and Android only prompts once.

## Acceptance
- Nothing is uploaded, proxied or removed before both gates are answered
- The tree picker's grants are persisted, and the granted set covers every album offered for Sync
- An album set to Sync that is not under a granted tree either cannot be chosen or prompts for the
  grant — never silently fails to optimise
- "Choose per album" is the default, and leaves the library untouched
- Archive is not offered as a bulk choice anywhere in the flow
- Skipping the tour leaves a safe, working configuration
- The mode explanations here and in Help come from the same strings
- A destructive mode cannot be selected before its explanation has been acknowledged, by either route
- Skipping the tour is allowed and loses nothing — explanations reappear at first use
- The acknowledgement record is per topic and survives a restart
- Consequential bubbles advance only on an explicit acknowledgement; informational ones use Next
- Acknowledgement buttons name the consequence rather than reading a bare "I understand"
- Skip and back-out stay available at every step — acknowledgement gates forward, never exit
- Removing a directory from the scope hides its albums and deletes no ledger or preference rows;
  re-adding it restores them with modes and history intact, and re-uploads nothing
- Adding a directory says how many albums it brings and that they begin backing up
- Re-running setup opens on current values and does not clear acknowledgements
- Verified on hardware in both themes, per CLAUDE.md

## Scope follows the grants — decided

Ian, 19 Aug 2026: the scan follows the granted trees. An album outside them is not scanned, not
listed, and not offered.

That simplifies the constraint recorded under Gate 1. The earlier draft offered two ways to stop a
Sync album sitting outside a granted tree — restrict the list, or prompt for the grant on selection.
**Restricting is now automatic**: an ungranted album never appears, so the case cannot arise and the
prompt is not needed. The rule survives as an invariant to assert rather than a flow to build.

### Narrowing the scope hides albums; it must never forget them

The important consequence, and it reaches existing code.

`BackupEntryDao.forgetAlbumsNotOnDevice` deletes ledger rows for albums the scan did not return. Its
own documentation already warns that a partial scan — revoked permission, unmounted card — would
read as "every album vanished" and wipe the record of what is backed up. **Grant-scoped scanning
adds a routine, intentional way for the scan to return fewer albums**, which is exactly the input
that function must never be handed.

So:

- **A scope change is a preference change, not evidence a file is gone.** Removing a directory from
  the scope must not call `forgetAlbumsNotOnDevice`, must not delete ledger rows, and must not
  delete `album_preferences` rows.
- **`album_preferences` especially.** CLAUDE.md records it as the one table that cannot be rebuilt
  from OneDrive or from the files. Losing a mode because a directory was briefly de-scoped is
  unrecoverable in a way nothing else here is.
- **Re-adding a directory restores its albums with their modes and their backup history intact**,
  and nothing re-uploads. That is the test that proves the rule held.

Only a genuine disappearance — the album gone from a directory that *is* still in scope — should
ever reach the forget path.

### Adding a directory later starts backing it up

A new tree brings new albums, and a new album takes `AlbumMode.DEFAULT`, which is `BACKUP`. So
widening the scope begins uploading, without a further prompt.

That is the right default for the reason the enum already gives — the failure mode should be
"uploaded something you did not need", never "lost something you did" — but it should be **stated in
the wizard at the moment the directory is added**, with the album count, rather than discovered from
a running upload.

## Re-running setup — decided

Ian, 19 Aug 2026: an option in Settings, or in the Help menu.

Three requirements on it, all of which follow from what setup can change:

- **It opens on current values, not defaults.** A wizard that resets the configuration it is meant
  to let you adjust is a trap, and the destructive settings are the ones it would reset.
- **It never re-applies a bulk mode change silently.** Gate 2's "back up everything" is a one-time
  choice; re-running setup offers it again but must not re-run the last answer just because the
  user walked through the flow.
- **Acknowledgements are not cleared by re-running it.** The record is per topic and already
  answered; making someone re-acknowledge Archive to change a directory devalues the
  acknowledgement, which is the whole point of keeping it rare.

Whether it lives in Settings or in Help is presentation. Help is the better home if the wizard is
mostly explanatory on a second run; Settings is better if it is mostly configuration. Both is fine
— one entry point, linked from two places.
