# TASK-027 — Archive and Sync for Dropbox, Google Drive, Backblaze B2 and IDrive e2

Ian, 24 Sept 2026: *"arch - sync for drop drive and s3"*. Restore already works for these four (TASK-026, `CloudDownloader`).
This task adds the two features that remove or replace a file on the phone. Staged, because the two carry different risk.

## The rule that governs both (CLAUDE.md, Deletion)

A local removal needs the cloud copy confirmed **and its byte size equal to the local size**, checked live. OneDrive
does this against a Graph listing (`BackupEngine.confirmStillInCloud`). For the other clouds the same bar is met by asking
the cloud about the one object the ledger recorded (`CloudVerifier.sizeOf(remoteItemId)`):

- **Present at the expected size** — confirmed.
- **Present at another size, deleted, or (Drive) trashed** — not confirmed; the file stays on the phone.
- **Could not ask** — not confirmed. *If we could not ask, we do not remove.*

Nothing weaker qualifies. The rows of these clouds keep `remoteSizeBytes = NULL` on purpose (see
`CloudUploader`): making them look verified would have let every OneDrive-shaped consumer act on them (the cloud-deletion
window would send a Dropbox id to Graph). Verification is live, at the moment of action, never remembered.

## Stage 1 — Archive (this stage)

1. `CloudVerifier` / `CloudVerifiers` (`domain/repository`), `RemoteCheck { Present(size), Gone, Unknown }`.
2. Adapters: Dropbox (a one-byte ranged download; the size is in the `Dropbox-API-Result` header, so no new scope),
   Drive (`files.get?fields=size,trashed`), S3 (`HEAD` object, `Content-Length`).
3. `BackupEngine.confirmStillInCloud` partitions items by their ledger row's cloud. OneDrive rows and rows with no
   ledger row take the existing listing path unchanged. Other clouds go through the verifier; a row not yet uploaded is
   "missing", which the Archive tab already answers by backing the file up and re-checking.
4. **Prune exemption.** `forgetAlbumsNotOnDevice` forgets the rows of an album that has left the scan, except rows
   verified in OneDrive. When Archive empties an album this would erase the ids Restore needs for every other cloud.
   Rows of a cloud that can restore, uploaded and holding an id, are exempt too.
5. `CloudCapabilities`: `archive = true` for the four. `sync` stays false.
6. Wording that says "OneDrive" on the Archive tab becomes "your cloud". CLAUDE.md's rule text says "Graph confirmed";
   it is generalised to "the cloud confirmed", with the same size bar. Not weakened.

Not in stage 1: the ready-to-archive notification (`redundantLocalCopies`) still counts only OneDrive-verified rows, so
an album on another cloud gets no notification. It works from the Archive tab.

## Stage 2 — Sync (built and device-tested 25 Sept 2026)

Sync replaces the file on the phone with a smaller copy **in place, with no trash step**, so from then on the cloud holds
the only original. Built:

1. **`CloudOriginalCheck`** — the gate before every overwrite (`ProxyApplier.proxyOnce`, `VideoOptimiser.optimise`,
   `VideoOptimiser.optimiseForWizard`). OneDrive rows pass on the size Graph recorded, as before. Any other cloud is asked
   live through its `CloudVerifier`, and the original must be present at exactly its size (`ElsewhereVerdict`). Anything
   else, including "could not ask", leaves the file untouched.
2. **Held, not failed.** A file that fails the gate is skipped by the candidate lists for six hours, in memory only, and
   nothing is written to its row. Without that it would be first in every batch and the re-queueing chain would never end.
3. **Candidate queries** (`proxyCandidates`, `proxyCandidatesAll`, `videoOptimiseCandidates`, `videoOptimiseCandidatesAll`)
   also return uploaded rows holding a cloud id for the clouds in `SyncLocations` (a SQL list; `SyncLocationsTest` keeps it
   equal to the capability table). Google Photos and pCloud never appear.
4. **`RestoreProxyInPlace`** downloads through `CloudDownloader` for these rows and checks the finished download against
   the original's recorded size before the proxy is touched. `restorableProxies` lists them.
5. **Archive of an optimised file**: `confirmStillInCloud` finds the row by MediaStore id (the rewrite changed the key) and
   checks the cloud against the ORIGINAL's size, not the proxy's.
6. `CloudCapabilities.FULL` for Dropbox, Google Drive, Backblaze B2 and IDrive e2.

Left as it was: the Camera album's manual optimise (`CameraOptimise`) still needs a remembered OneDrive size, so it works
for OneDrive only. The ready-to-archive notification still counts only OneDrive-verified rows.

## Test plan

Unit: verifier adapters against a local server; the decision from a `RemoteCheck` to confirmed / wrong size / missing /
unconfirmed; the prune query (androidTest, Room). Device (Moto G, Ian sets the mode, since album modes are only ever
set by the user): a Dropbox album set to Archive, Check, Android's trash dialog, files in the trash, Restore back.

## Result — device tests, 25 Sept 2026 (Moto G, real accounts)

Every step below ran against the real service, and every restored file matched the size recorded at upload.

| Cloud | Upload | Sync (shrink) | Restore in place | Archive (live check, trash) | Restore archived |
|---|---|---|---|---|---|
| Dropbox | yes | yes, 41 files | yes | yes, 36 files | yes |
| Google Drive | yes | yes, 10 files | yes | yes, 9 files (stage 1) | yes |
| Backblaze B2 | yes | yes, 12 files | yes | yes, 16 files | yes |
| IDrive e2 | yes | yes, 10 files | yes | yes, 6 files | yes |
| pCloud | waiting on pCloud's approval; no download or verifier built | | | | |

Seen on the way, and fixed: signing out of a cloud re-paired its folders to the main cloud (now the user decides; see the sign-out prompt), an album spread over two top-level folders was missing from the folder list, the Sync queue stopped behind a row whose file was gone, and one failing file stopped the whole run (now held, and three in a row still stop it). The Archive check refused 10 Dropbox files while Dropbox was signed out, which is the rule: if the cloud could not be asked, nothing is removed.

Not done: the ready-to-archive notification and the Camera album's manual optimise are OneDrive-only; the (?) help pages and Ian's guide still describe the old layout in places.
