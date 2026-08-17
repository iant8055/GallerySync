# TASK-004 — OneDrive adapter (Microsoft Graph — browse folder structure)

Milestone: v0.1.0 Foundation — "OneDrive adapter (Microsoft Graph API — browse folder structure)"
Depends on: TASK-001, TASK-002
Blocks: v0.2.0 on-demand download

## Goal
Browse the user's OneDrive folder tree through Microsoft Graph. Read-only listing. No
downloading, no uploading, no deleting.

## ESCALATED — do not work around this
Azure app registration (client ID, redirect URI, admin consent) **has been escalated to Ian**
per CLAUDE.md ("OAuth app registration is needed"). Until Ian returns a client ID:

- **Do NOT invent, guess, or hardcode a client ID, tenant ID, client secret, or access token.**
- **Do NOT implement an interactive OAuth/MSAL sign-in flow.** That is a separate task once the
  registration exists.
- Everything below must be complete and fully testable against an **injected token source**.
- Mark the single integration seam with exactly this comment so it is greppable:
  `// TODO(TASK-004-AUTH): requires Azure app registration — see escalation in TASK-004.md`

The whole point: when Ian hands over a client ID, the only new work is an implementation of
`OneDriveTokenProvider`. Nothing else should need to change.

## What to build

### 1. Domain model — `domain/model/`
No Android imports in `domain/` (CLAUDE.md architecture rule). Pure Kotlin.

`RemoteMediaNode.kt`:
```
sealed interface RemoteMediaNode {
    val id: String
    val name: String
    val modifiedAtUtc: Long

    data class Folder(
        override val id: String,
        override val name: String,
        override val modifiedAtUtc: Long,
        val childCount: Int,
        val parentPath: String?
    ) : RemoteMediaNode

    data class File(
        override val id: String,
        override val name: String,
        override val modifiedAtUtc: Long,
        val mimeType: String,
        val sizeBytes: Long,
        val widthPx: Int?,
        val heightPx: Int?,
        val eTag: String?
    ) : RemoteMediaNode
}
```

`FolderPage.kt` — `data class FolderPage(val nodes: List<RemoteMediaNode>, val nextPageToken: String?)`

`DataResult.kt`:
```
sealed interface DataResult<out T> {
    data class Success<T>(val value: T) : DataResult<T>
    data class Failure(val error: RemoteError) : DataResult<Nothing>
}

sealed interface RemoteError {
    data object Unauthorized : RemoteError          // 401 — token missing/expired
    data object NoToken : RemoteError               // no token available at all
    data object Network : RemoteError               // IOException
    data class Http(val code: Int, val body: String?) : RemoteError
    data class Unknown(val cause: Throwable) : RemoteError
}
```

### 2. Token seam — `data/remote/auth/OneDriveTokenProvider.kt`
```
interface OneDriveTokenProvider {
    suspend fun getAccessToken(): String?   // null = not signed in
    suspend fun invalidateAccessToken()
}
```
Coroutines, not callbacks (CLAUDE.md).

### 3. Token storage — `data/remote/auth/EncryptedTokenStore.kt`
CLAUDE.md hard rule: **"Never store OAuth tokens in SharedPreferences — use
EncryptedSharedPreferences."**
- Back it with `androidx.security:security-crypto` `EncryptedSharedPreferences` using a
  `MasterKey` with `KeyScheme.AES256_GCM`, file name `"gallery_sync_secure_prefs"`.
- API: `suspend fun readAccessToken(): String?`, `suspend fun writeAccessToken(token: String?)`,
  `suspend fun clear()`. All disk I/O on `Dispatchers.IO`.
- Never log a token value. Log presence/absence only (`Logger.d(TAG, "token present: ${t != null}")`).
- Implement `OneDriveTokenProvider` on top of it as `StoredOneDriveTokenProvider`
  (`invalidateAccessToken()` clears the stored token). It returns null until real sign-in
  exists — that is correct and expected for v0.1.0. Put the TODO(TASK-004-AUTH) marker here.
- If `androidx.security:security-crypto` turns out to be deprecated at the version you pull,
  still use it (CLAUDE.md mandates it) but **report the deprecation to the Lead Agent**.

### 4. Retrofit layer — `data/remote/onedrive/`

`GraphApiService.kt` — base URL `https://graph.microsoft.com/v1.0/`:
```
interface GraphApiService {
    @GET("me/drive/root/children")
    suspend fun listRootChildren(
        @Query("\$top") top: Int = 100,
        @Query("\$select") select: String = DEFAULT_SELECT
    ): Response<GraphChildrenResponseDto>

    @GET("me/drive/items/{itemId}/children")
    suspend fun listChildren(
        @Path("itemId") itemId: String,
        @Query("\$top") top: Int = 100,
        @Query("\$select") select: String = DEFAULT_SELECT
    ): Response<GraphChildrenResponseDto>

    @GET
    suspend fun listNextPage(@Url nextLink: String): Response<GraphChildrenResponseDto>
}
```
`DEFAULT_SELECT = "id,name,size,eTag,lastModifiedDateTime,createdDateTime,file,folder,image,photo,parentReference"`
Return `Response<T>` (not the bare body) so HTTP codes are mappable to `RemoteError`.

DTOs in `data/remote/onedrive/dto/`, all `@Serializable`, all fields nullable-safe because
Graph omits absent facets:
- `GraphChildrenResponseDto(value: List<GraphDriveItemDto> = emptyList(), @SerialName("@odata.nextLink") nextLink: String? = null)`
- `GraphDriveItemDto(id, name, size: Long? = null, eTag: String? = null, lastModifiedDateTime: String? = null, createdDateTime: String? = null, file: GraphFileFacetDto? = null, folder: GraphFolderFacetDto? = null, image: GraphImageFacetDto? = null, parentReference: GraphParentReferenceDto? = null)`
- `GraphFileFacetDto(mimeType: String? = null)`
- `GraphFolderFacetDto(childCount: Int? = null)`
- `GraphImageFacetDto(width: Int? = null, height: Int? = null)`
- `GraphParentReferenceDto(id: String? = null, path: String? = null)`

Configure the Json instance with `ignoreUnknownKeys = true` — Graph adds fields constantly.

### 5. Auth interceptor — `data/remote/onedrive/GraphAuthInterceptor.kt`
OkHttp `Interceptor` adding `Authorization: Bearer <token>`.
OkHttp's interceptor API is synchronous, so bridge with `runBlocking { tokenProvider.getAccessToken() }`.
This is the one sanctioned `runBlocking` in the codebase — it runs on OkHttp's own background
thread, never the main thread. Add a comment saying exactly that so a future reader does not
"fix" it. If the token is null, proceed **without** the header and let the 401 path handle it
(do not throw from the interceptor).

### 6. Mapper — `data/remote/onedrive/GraphDriveItemMapper.kt`
Pure Kotlin, no Android, no Retrofit. `fun GraphDriveItemDto.toRemoteMediaNode(): RemoteMediaNode?`
- `folder != null` -> `RemoteMediaNode.Folder` (childCount defaults 0)
- `file != null` -> `RemoteMediaNode.File`, mimeType defaults to `"application/octet-stream"`,
  sizeBytes defaults 0
- neither facet, or `id`/`name` null -> return **null** (caller filters it out). Graph returns
  package/bundle items that are neither; dropping them is correct.
- ISO-8601 `lastModifiedDateTime` -> epoch millis. `java.time.Instant.parse` is API 26+, which
  matches minSdk 26, so use it — no desugaring needed. Unparseable/missing -> `0L`.
**This file must be trivially unit-testable with zero mocks. Keep it a pure function.**

### 7. Repository — the ONLY place network calls happen
`domain/repository/OneDriveRepository.kt` (interface, pure Kotlin):
```
interface OneDriveRepository {
    suspend fun listRoot(): DataResult<FolderPage>
    suspend fun listFolder(folderId: String): DataResult<FolderPage>
    suspend fun listNextPage(nextPageToken: String): DataResult<FolderPage>
}
```
`data/repository/OneDriveRepositoryImpl.kt`:
- `@Inject constructor(private val api: GraphApiService, private val tokenProvider: OneDriveTokenProvider, @IoDispatcher private val dispatcher: CoroutineDispatcher)`
- Every method: `withContext(dispatcher) { ... }`
- Short-circuit to `DataResult.Failure(RemoteError.NoToken)` if `getAccessToken()` is null —
  do not burn a network call.
- Map: 401 -> call `tokenProvider.invalidateAccessToken()` then `RemoteError.Unauthorized`;
  other non-2xx -> `RemoteError.Http(code, errorBody)`; `IOException` -> `RemoteError.Network`;
  anything else -> `RemoteError.Unknown`.
- Filter out nulls from the mapper. Pass `@odata.nextLink` through as `nextPageToken`.
- Log via `Logger`, never `Log.d`. Never log the Authorization header or token.

### 8. DI — `di/NetworkModule.kt` and `di/DispatcherModule.kt`
- `@Qualifier annotation class IoDispatcher`, provided as `Dispatchers.IO`. This exists so
  tests can inject `UnconfinedTestDispatcher` — do not skip it.
- Provide `Json`, `OkHttpClient` (with `GraphAuthInterceptor` + `HttpLoggingInterceptor` at
  `BODY` **only when `BuildConfig.DEBUG`**, otherwise `NONE` — a BODY-level logger in release
  would print bearer tokens to logcat, which is a security defect), `Retrofit`, `GraphApiService`.
- Bind `OneDriveRepositoryImpl` to `OneDriveRepository` and `StoredOneDriveTokenProvider` to
  `OneDriveTokenProvider` via an `@Binds` module.

## Test requirements — `app/src/test/java/com/gallery/sync/`
Mockito + `kotlinx-coroutines-test` + `runTest`. Mock `GraphApiService` and `OneDriveTokenProvider`.
- Mapper: folder DTO -> Folder; file DTO -> File; DTO with neither facet -> null; missing
  mimeType -> `application/octet-stream`; ISO date -> correct epoch millis; malformed date -> 0L.
- Repository happy path: `listRoot` returns Success with mapped nodes.
- **No token** -> `Failure(NoToken)` and `verify(api, never())` was called. This one matters.
- 401 -> `Failure(Unauthorized)` AND `invalidateAccessToken()` was called.
- 500 -> `Failure(Http(500, ...))`.
- `IOException` thrown by the service -> `Failure(Network)`.
- Empty `value` array -> `Success` with an empty node list (not a failure).
- `@odata.nextLink` present -> surfaced as `nextPageToken`; absent -> null.
- JSON deserialization test: feed a realistic Graph children payload string through the
  configured `Json` and assert the DTO parses, including an unknown extra field being ignored.

## Acceptance criteria
1. Zero hardcoded client IDs, secrets, tenant IDs, or tokens anywhere.
2. `grep -r "TODO(TASK-004-AUTH)" app/src/main` returns the auth seam(s) only.
3. No `android.util.Log` usage.
4. No network call outside the repository layer.
5. All unit tests pass; `./gradlew assembleDebug testDebugUnitTest` green.

## Out of scope
- MSAL / interactive sign-in (blocked on Ian's Azure registration)
- Downloading file content, thumbnails, or `@microsoft.graph.downloadUrl` handling (v0.2.0)
- Writing anything into Room (v0.2.0 sync job)
- Any UI
- Google Photos (v0.3.0, Pro-gated)

## Report back
- Confirmation nothing was hardcoded
- Exactly what Ian must do in the Azure portal, in a form he can act on
- Test count and result
