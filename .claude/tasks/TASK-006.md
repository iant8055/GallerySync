# TASK-006 — Graph upload with resumable sessions

Milestone: v0.2.0 Backup — "Graph upload, resumable upload sessions"
Depends on: TASK-004 (Graph API service, auth interceptor)
Blocks: the backup worker, the upload ledger, everything else in v0.2.0

## Why this is first
Every other backup task is downstream of being able to put one file into OneDrive
reliably. Build and prove this in isolation before any scanning, scheduling, or
ledger work, so that when the worker fails later we already know the transport is sound.

## Prerequisite — DONE
`Files.ReadWrite` is granted on the Azure app registration, and `MsalClientProvider.SCOPES`
now requests it in place of `Files.Read`. The user will see a fresh consent prompt on the
next interactive sign-in; a silent acquisition against the old consent may fail first, in
which case signing out and back in resolves it.

Do not widen the scope further. `Files.ReadWrite.All` grants access to files shared with
the user and is not needed to back up this device's own photos.

## What to build

### 1. `data/remote/onedrive/GraphUploadService.kt`
Retrofit service for the upload endpoints. Separate from `GraphApiService` because the
chunk PUTs go to an absolute `uploadUrl` returned by Graph rather than to the Graph base
URL, and mixing the two on one interface invites a wrong-base-URL bug.

- `createUploadSession(path, body)` → `POST /me/drive/root:/{path}:/createUploadSession`
  Body sets `@microsoft.graph.conflictBehavior`. Use **`rename`**, never `replace`:
  CLAUDE.md forbids destroying a user's cloud file, and a hash collision or a
  same-named photo from another device must not silently overwrite what is already there.
- `uploadChunk(@Url uploadUrl, contentRange, body)` → `PUT` to the absolute session URL.
  Must be `@Url`; do not route through the Graph base URL.
- The upload session URL is **pre-authorised**. Do not attach the bearer token to chunk
  PUTs — sending it is a documented cause of 401s on otherwise valid sessions. This means
  the chunk calls need an OkHttp client *without* `GraphAuthInterceptor`, provided as a
  distinct qualified dependency in `NetworkModule`.

### 2. `data/remote/onedrive/dto/` — request/response DTOs
`CreateUploadSessionRequestDto`, `UploadSessionDto` (`uploadUrl`, `expirationDateTime`),
`UploadedItemDto` (`id`, `name`, `size`, `eTag`). Keep them `@Serializable`, matching the
existing DTO style.

### 3. `data/remote/onedrive/ChunkedUploader.kt`
The transport logic, and the part that carries real risk.

- Chunk size **must be a multiple of 320 KiB** (327,680 bytes) — Graph rejects otherwise.
  Use 5 MiB (`5 * 327680 * 16`), a reasonable balance for mobile.
- `Content-Range: bytes {start}-{end}/{total}`, where `end` is **inclusive**. An
  off-by-one here produces a corrupt file that Graph still accepts — the single most
  dangerous bug in this task, so unit test the header string directly.
- Final chunk's response is `200`/`201` with the created item; intermediate chunks return
  `202` with `nextExpectedRanges`. Treat any other code as failure.
- On resume, trust `nextExpectedRanges` from the server rather than local assumptions
  about how far we got.
- Support cancellation: honour coroutine cancellation between chunks so a worker being
  stopped does not leave a thread pushing bytes.
- Files **smaller than 4 MiB** should use a simple `PUT .../content` instead — creating a
  session for a 200 KB thumbnail is wasteful. Route this decision here.

### 4. `domain/repository/` — extend the repository contract
Add to a new `OneDriveUploadRepository` (do not widen `OneDriveRepository`, which is
documented as read-only):
```
suspend fun upload(
    localFile: File,
    remoteFolderPath: String,
    onProgress: (bytesSent: Long, total: Long) -> Unit = {}
): DataResult<UploadedItem>
```
Returns the existing `DataResult` so callers handle failure as data, per the established
pattern. Map errors to the existing `RemoteError` types; add `RemoteError.InsufficientStorage`
for Graph's quota response (507 / `quotaLimitReached`), because "your OneDrive is full" is a
user-actionable message and must not be flattened into a generic HTTP error.

## Test requirements
Unit tests in `app/src/test/`, using MockWebServer for the transport:
- `Content-Range` header is exactly right for first, middle, and final chunk, including
  a file whose size is not a clean multiple of the chunk size
- a file of exactly the chunk size produces one chunk, not two
- a 202 with `nextExpectedRanges` resumes from the server's offset, not a local guess
- a 507 maps to `RemoteError.InsufficientStorage`
- a file under 4 MiB takes the simple-PUT path and never calls `createUploadSession`
- cancellation mid-upload stops issuing chunks
- an empty (0-byte) file does not hang or produce a malformed range header

## Acceptance criteria
1. A file larger than 4 MiB uploads via a session and appears in OneDrive intact —
   verify the size matches byte-for-byte, not just that a file exists.
2. An interrupted upload resumes without restarting from zero.
3. Nothing existing in OneDrive is ever overwritten (`conflictBehavior = rename`).
4. `./gradlew assembleDebug testDebugUnitTest` green.

## Out of scope
- Scanning for what to upload (TASK-007)
- The ledger and dedup (TASK-008)
- Scheduling (TASK-009)
- Deleting or moving anything, locally or remotely — this task only ever adds files

## Report back
Files created, the exact scope string now requested, and confirmation that chunk PUTs do
not carry the bearer token.
