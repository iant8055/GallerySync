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

## Conversation bubbles teach; they never consent

Coach-mark tours have one well-known failure: people tap through them. That is tolerable for a
tutorial and unacceptable for consent.

- The bubbles explain what the modes do and what the defaults are.
- **They never stand in for the Archive confirmation** in TASK-012 guard 4, which keeps its own
  dialog with Cancel as the default action.
- Nothing destructive is reachable by tapping "Next" repeatedly.
- The tour is skippable, and skipping it leaves the safe defaults in place rather than an
  unconfigured app.

The bubbles are also where the plain-language mode explanations first appear. Same strings as the
Help entries in TASK-012, not a paraphrase — two descriptions of one irreversible operation will
drift, and the drift is invisible.

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
- Re-running setup is possible from Settings without reinstalling
- Verified on hardware in both themes, per CLAUDE.md

## Open
- **Does the scan scope follow the granted trees, or stay MediaStore-wide with the trees only used
  for writing?** Following the grants is simpler to explain and matches what the user picked;
  MediaStore-wide keeps albums visible that the user could still choose to back up. Recommended:
  follow the grants, since an album the app cannot write to cannot be fully managed anyway.
- **Whether the wizard can be re-entered per gate**, or only as a whole.
