# TASK-015 — Backing up to a second location, on purpose

Milestone: v0.4.x — destination handling
Requested by: Ian, 25 Aug 2026
Depends on: `RemoteRoots`, the skip-existing check in `BackupEngine.remoteIndexFor`

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
