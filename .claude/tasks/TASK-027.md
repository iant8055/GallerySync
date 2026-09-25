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

## Stage 2 — Sync (separate, after Stage 1 is proven on the phone)

Sync replaces a file with a smaller copy, then relies on the cloud original to restore it. The candidate queries
(`remoteSizeBytes = sizeBytes`), `RestoreProxyInPlace`, the wizard's optimise plans and the Camera optimise all assume a
remembered, OneDrive-shaped proof. Each needs a live check or an adapter. Designed once Stage 1 has shown the verifier
approach holds.

## Test plan

Unit: verifier adapters against a local server; the decision from a `RemoteCheck` to confirmed / wrong size / missing /
unconfirmed; the prune query (androidTest, Room). Device (Moto G, Ian sets the mode, since album modes are only ever
set by the user): a Dropbox album set to Archive, Check, Android's trash dialog, files in the trash, Restore back.
