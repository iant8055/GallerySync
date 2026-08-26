# TASK-015 — Backing up to a second location, on purpose

Milestone: v0.4.x — destination handling
Requested by: Ian, 25 Aug 2026
Depends on: `RemoteRoots`, the skip-existing check in `BackupEngine.remoteIndexFor`

## Deferred — Ian, 26 Aug 2026

**Not being built now. Revisit in a later build.**

The diagnosis below stands and is the reason to keep this file: the destination setting was never
broken, and anyone who reads "backup didn't work" in a log later should find the explanation here
rather than re-derive it.

What is deferred is the *duplication feature* — the deliberate second copy. Costed 26 Aug and shelved,
because the consequences turn out to be larger than the wording suggests.

### Why, in one fact

**The ledger holds one row per file with one `remoteItemId`.** A second copy has no row, so it has no
identity the app can act on. Everything below follows from that:

| | |
|---|---|
| Archive's safety guarantee | **unaffected** — `verifiedInCloud` then `confirmStillInCloud` both act on the tracked copy |
| Deletion sync | deletes by `remoteItemId`, so it removes one copy and reports "the OneDrive copy" — true of one, false as stated, and the space is not freed |
| Restore | `cloudFolders` dedups by folder name with `putIfAbsent`; a folder under two roots is listed once, first root wins, so the second copy is invisible |
| Divergence | edit a file and the new version goes to the destination while the untracked copy stays at the old one — same name, different content, one tracked |
| Orphans | never verified, never updated, never restorable, never removed; they only consume quota, and quota exhaustion stops backup entirely |

Duplicates cost money and clarity, not data. Nothing here endangers a file, which is why this is a
deferral and not a prohibition.

### The fork this was always hiding

The "Open" question below — one-off copy or standing choice — is not a detail, it is two different
features:

- **One-off copy.** Fire-and-forget. Cheap, no schema change. But the app then owes the user a plain
  statement that the copy is *not tracked* and will not be verified, updated or removed. An untracked
  copy the user believes is a backup is worse than no copy at all.
- **Standing choice.** Every future photo goes to both places. Then they are not orphans, they are a
  second backup, and a second backup has to be tracked: one row per file **per location**. That is a
  schema change and a migration, which CLAUDE.md makes an escalation.

Recommended when it returns: the one-off, with the untracked wording made explicit. The standing
version is a genuinely larger feature and deserves costing on its own rather than arriving by
accident through this one.

## What Ian asked for

> *"If I want to backup some files to a different location (even if it means the files are
> duplicated) then I should be allowed to. Maybe a pop-up window indicating these files are backed up
> in a different location — are you sure you want to back up here."*

And, ruling out the alternative that was on the table:

> *"Moving files inside OneDrive can be accomplished with the OneDrive app itself — we don't need to
> accommodate that."*

Correct, and it is the same instinct as the Open OneDrive button: building a worse version of
something the user already has is not this app's job.

## How it was found

Ian changed the destination to `test/test` and reported that backup "didn't work". It had not
failed. Traced on the Fold 4, 25 Aug 2026:

- `destination_root` was stored correctly as `test/test`, and the Cloud check screen displayed it.
- A manual Sync now resolved 25 of 26 pending files, taking `UPLOADED` from 38 to 63.
- **Every one of them logged `already in OneDrive, not re-uploading`.** Not a byte was sent.

`RemoteRoots.searchOrder` keeps `Samsung Gallery/DCIM` permanently searchable, so every file already
there is recognised and recorded as backed up without being uploaded to the new destination. Working
exactly as designed — and indistinguishable, from the outside, from the setting being ignored.

The person who wrote that copy expected files to appear in the new folder. That is decent evidence
the behaviour needs stating rather than the reader needing to try harder.

## The obstacle is the ledger, not the search

Stopping the search at the destination is not enough. `nextPending` selects rows where
`state != UPLOADED`, so the ledger itself refuses to send a file it believes is already safe.
Forcing a second copy means resetting the affected rows to pending.

**That is safe now, and was not this morning.** Until 25 Aug the retrieval list read
`observeRetrievable()`, which requires `state = UPLOADED` — resetting rows would have pulled
already-archived files out of the restore list mid-operation, which is the one thing this app must
never do. The Restore screen now reads the drive directly, so row state no longer gates getting a
file back.

What resetting still affects, all in the safe direction:

| Affected | Effect while rows are pending |
|---|---|
| `redundantLocalCopies` → Archive removal | stops offering those files until they re-verify |
| `cloudDeletionCandidates` | stops offering them |
| The Albums hero count | dips, then recovers |

Nothing is lost; things pause. No schema change, so no migration.

## Scope

1. **Per album, not the whole library.** Wanting one album in a second place is a common want;
   duplicating 148 GB is not. Whole-library duplication may follow, but the album is the unit.
2. Changing the destination — or choosing a second location for an album — offers the duplicate
   explicitly. Declining leaves today's behaviour exactly as it is.
3. On acceptance: reset that album's uploaded rows to pending, and have the skip check consult only
   the destination for that pass.
4. The old root stays permanently searchable regardless. Nothing is ever stranded by this.

## The dialog must carry the cost in numbers

"These files are backed up in a different location, are you sure?" understates it. The regret here
is a storage bill, so the figure belongs in the sentence:

> **8,482 files are already backed up in Samsung Gallery/DCIM.**
> Copying them here as well uploads all of them again and uses a further **148 GB** of your OneDrive.
> Nothing already backed up is removed or moved.

Three facts, in the order someone needs them: what exists, what it will cost, and what is *not* at
risk. The last line matters — "back up again" sounds like it might disturb the first copy, and it
does not.

## Acceptance

- Declining leaves the current behaviour byte-for-byte unchanged
- The confirmation names the file count and the size, not just the fact
- Resetting rows to pending never removes a `remoteItemId` before its replacement is verified
- Archive offers nothing from an album mid-duplication, and resumes when it re-verifies
- The old root stays in the search set throughout, so a failed or abandoned duplication strands
  nothing
- Verified on hardware in both themes, per CLAUDE.md

## Open

- **Whether the duplicate is a standing choice or a one-off.** A standing "this album backs up to two
  places" means every future photo goes to both, and that is a different feature from copying what
  exists today. Ian's wording — "backup some files to a different location" — reads as the one-off.
- What the Albums screen says about an album that lives in two places. Today a mode is one word and
  the row has no room for a second destination.
