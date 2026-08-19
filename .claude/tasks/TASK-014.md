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
sit under a granted tree, or proxying silently cannot touch it. A user who grants DCIM and then sets
a Pictures album to Sync gets an album that never optimises and no explanation why. Either

- the album list is restricted to what was granted, or
- selecting Sync on an ungranted album prompts for that tree there and then.

The second is friendlier and is recommended. Whichever, **the failure must never be silent** — a
mode that quietly does nothing is worse than a mode that cannot be selected.

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

A shown-explanations set, keyed per topic, in `BackupSettings` alongside the other preferences. No
schema change — this is DataStore, not Room.

What it buys, in order of usefulness:

- **A destructive mode cannot be chosen before its explanation has been displayed.** Selecting
  Archive with no record of the Archive explanation shows the explanation first, then the
  confirmation. Not a second consent step — the sequence a careful person would want anyway.
- **The tour becomes skippable without losing anything.** Skipping is fine, because the explanation
  reappears at the point of first use. Just-in-time is better teaching than a wizard nobody
  remembers, and the record makes the two routes equivalent.
- **The record is per topic, not per tour**, so adding a fifth mode later does not require re-running
  setup, and a user who saw the Archive explanation in the wizard is not shown it again.

### One honest limit on the record

It proves the text was **displayed**, never that it was read or understood. Worth stating because a
record like this invites being treated as proof of comprehension, and it is not — it should not
appear in any wording that implies the user agreed to something by having seen a screen. Its value
is that the app can never take a destructive action the user was given no opportunity to understand,
which is a real guarantee and a narrower one.

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
- A destructive mode cannot be selected before its explanation has been displayed, by either route
- Skipping the tour is allowed and loses nothing — explanations reappear at first use
- The shown-explanations record is per topic and survives a restart
- Re-running setup is possible from Settings without reinstalling
- Verified on hardware in both themes, per CLAUDE.md

## Open
- **Does the scan scope follow the granted trees, or stay MediaStore-wide with the trees only used
  for writing?** Following the grants is simpler to explain and matches what the user picked;
  MediaStore-wide keeps albums visible that the user could still choose to back up. Recommended:
  follow the grants, since an album the app cannot write to cannot be fully managed anyway.
- **Whether the wizard can be re-entered per gate**, or only as a whole.
