# Guide Sibling handoff: one swipe for every file not backed up

Ian, 23 Nov 2026: *"we already have the 'XXX files not backed up' in the album ... can we use that system for ALL files
that haven't been backed up?"*, then *"yes - build it that way"*. App side committed with this file.

## What changed in the app

**Album page (every album except the camera folder's optimize list):** one gesture for every file.

- **Swipe left: keep this file out of the backup.** Greyed, marked *kept out of the backup · your choice*. Never
  uploaded to any Cloud, never optimized, never offered for Archive. A copy already in a Cloud stays there.
- **Swipe right: back it up.**
  - On a file kept out: puts it back in the backup.
  - On a file gone from its Cloud: sends it again straight away (the Cloud is asked first).
  - On a file that failed five times: tries that one again.
- Files gone from their Cloud start greyed and are never re-sent on their own (unchanged).
- **Select all / Clear / Resend N are gone.** The header has **Back up all N** for the files gone from the Cloud.
- Header counts add *N not backed up by choice*.
- The camera folder (when *Special settings for Camera* is on) keeps its own swipe, which selects files to optimize.

**Albums tab card:**

- Red *N files not backed up* now counts every file that needs a decision: gone from its Cloud, failed (not in an Off
  album), or unable to finish uploading. New files waiting for the next run are not counted.
- New grey line *N files not backed up by choice*.
- The breakdown line no longer says *N failed* (it is in the red count).

**Uploads that keep being interrupted:** a file whose upload is interrupted twice without getting further goes to the end
of the queue and its card says *couldn't finish uploading · trying after the rest* in red.

## What the Guide needs

- `album-resend` (title *Files not backed up*): rewrite for the swipe (left keeps out, right backs up / sends again /
  retries), **Back up all**, and no Select all / Clear / Resend. All seven languages, `en_hash` recomputed.
- The album file-status topic (`album-file-status` or its current id): add *kept out of the backup · your choice* and
  *couldn't finish uploading · trying after the rest*; the header's *N not backed up by choice*.
- The Albums-tab card topic: the red line's new meaning and the grey *not backed up by choice* line; *failed* no
  longer in the breakdown.
- Bump `LAST_UPDATED` and all 7 `LAST_UPDATED_BY_LOCALE`, run `build_guide.py`, run `./gradlew test`, push, and
  delete this file.
