package com.gallery.sync.data.remote.cloud

import android.app.Activity
import com.gallery.sync.data.remote.auth.SignInResult
import com.gallery.sync.data.remote.googlephotos.toStreamingRequestBody
import com.gallery.sync.data.remote.onedrive.UploadSource
import com.gallery.sync.di.IoDispatcher
import com.gallery.sync.domain.backup.BackupLocation
import com.gallery.sync.domain.model.DataResult
import com.gallery.sync.domain.model.RemoteError
import com.gallery.sync.domain.model.UploadedItem
import com.gallery.sync.domain.repository.CloudDownloader
import com.gallery.sync.domain.repository.CloudUploader
import com.gallery.sync.domain.repository.CloudVerifier
import com.gallery.sync.domain.repository.RemoteCheck
import com.gallery.sync.util.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Google Drive: files land in `GallerySync/<album>/<name>` in the user's own Drive.
 *
 * Uses the **`drive.file`** scope only — the app can see and touch only what it created itself, never
 * the rest of the user's Drive. That is also why no Drive listing is ever needed to find the user's own
 * files: a folder lookup can only ever match a folder this app made.
 *
 * Not offered until a Google client id is filled in `cloud_oauth_config.json` (the same Android client
 * Google Photos uses works, with the Drive API enabled on the project) — see [CloudOAuthConfigs].
 * Backup-only, like every cloud but OneDrive.
 */
@Singleton
class GoogleDriveCloud @Inject constructor(
    private val configs: CloudOAuthConfigs,
    private val signInFlow: AppAuthCloudSignIn,
    private val tokens: AppAuthCloudTokens,
    @param:CloudUploadClient private val client: OkHttpClient,
    @param:IoDispatcher private val dispatcher: CoroutineDispatcher
) : CloudConnection, CloudUploader, CloudDownloader, CloudVerifier {

    override val location = BackupLocation.GOOGLE_DRIVE

    override val kind = ConnectionKind.OAUTH

    override val isOfferedInThisBuild: Boolean get() = configs.clientFor(PROVIDER_ID) != null

    /** Overridden in tests to point at a local server. */
    internal var apiBase = "https://www.googleapis.com"

    private val folderIds = ConcurrentHashMap<String, String>()

    private val provider = OAuthProvider(
        location = location,
        authEndpoint = "https://accounts.google.com/o/oauth2/v2/auth",
        tokenEndpoint = "https://oauth2.googleapis.com/token",
        scopes = listOf("https://www.googleapis.com/auth/drive.file"),
        // Google only issues a refresh token on the first consent unless both are set; without them a
        // reconnect would quietly yield an access token that dies in an hour.
        extraParams = mapOf("access_type" to "offline"),
        prompt = "consent"
    )

    /**
     * Opens a file for Restore by the file id recorded at upload (`files/{id}?alt=media`). The `drive.file`
     * scope covers reading what this app created, so no new permission is asked for. A file deleted from Drive
     * answers 404, which Restore reports as gone from the cloud.
     */
    override suspend fun openStream(remoteItemId: String): DataResult<InputStream> = withContext(dispatcher) {
        val token = tokens.accessToken(location) ?: return@withContext DataResult.Failure(RemoteError.NoToken)
        val url = "$apiBase/drive/v3/files".toHttpUrl().newBuilder()
            .addPathSegment(remoteItemId)
            .addQueryParameter("alt", "media")
            .build()
        val request = Request.Builder().url(url).header("Authorization", "Bearer $token").get().build()
        try {
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                val body = response.body?.string()
                response.close()
                if (response.code == 401) tokens.invalidate(location)
                return@withContext CloudHttp.failureFor(response, body)
            }
            val stream = response.body?.byteStream()
                ?: run {
                    response.close()
                    return@withContext DataResult.Failure(RemoteError.Unknown(IOException("Drive sent no file")))
                }
            DataResult.Success(stream)
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            Logger.w(TAG, "download: network failure: ${e.message}")
            DataResult.Failure(RemoteError.Network)
        }
    }

    /**
     * Asks Drive how big a stored file is, for Archive (`files/{id}?fields=size,trashed`). A file in Drive's trash
     * still answers with a size, so `trashed` is read too: a trashed file is not a copy anyone can rely on, and
     * counts as [RemoteCheck.Gone]. A definite 404 is Gone; everything else that is not a clear answer is
     * [RemoteCheck.Unknown], which Archive reads as "do not remove".
     */
    override suspend fun sizeOf(remoteItemId: String): RemoteCheck = withContext(dispatcher) {
        val token = tokens.accessToken(location) ?: return@withContext RemoteCheck.Unknown
        val url = "$apiBase/drive/v3/files".toHttpUrl().newBuilder()
            .addPathSegment(remoteItemId)
            .addQueryParameter("fields", "size,trashed")
            .build()
        val request = Request.Builder().url(url).header("Authorization", "Bearer $token").get().build()
        try {
            client.newCall(request).execute().use { response ->
                val body = response.body?.string()
                when {
                    response.isSuccessful -> {
                        val meta = CloudHttp.objectOf(body)
                        val size = meta?.longOrNull("size")
                        when {
                            meta == null || size == null -> RemoteCheck.Unknown
                            meta.stringOrNull("trashed") == "true" -> RemoteCheck.Gone
                            else -> RemoteCheck.Present(size)
                        }
                    }
                    response.code == 404 -> RemoteCheck.Gone
                    else -> {
                        if (response.code == 401) tokens.invalidate(location)
                        RemoteCheck.Unknown
                    }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            Logger.w(TAG, "check: network failure: ${e.message}")
            RemoteCheck.Unknown
        }
    }

    override suspend fun accountLabel(): String? = if (tokens.isSignedIn(location)) LABEL else null

    override suspend fun isConnected(): Boolean = tokens.isSignedIn(location)

    override suspend fun signIn(activity: Activity): SignInResult {
        val clientConfig = configs.clientFor(PROVIDER_ID) ?: return SignInResult.Failed("not_configured")
        return signInFlow.signIn(activity, provider, clientConfig)
    }

    override suspend fun signOut() {
        tokens.signOut(location)
        folderIds.clear()
    }

    override suspend fun upload(
        source: UploadSource,
        album: String,
        onProgress: (bytesSent: Long, total: Long) -> Unit
    ): DataResult<UploadedItem> = withContext(dispatcher) {
        val token = tokens.accessToken(location) ?: return@withContext DataResult.Failure(RemoteError.NoToken)
        if (source.sizeBytes <= 0L) {
            Logger.w(TAG, "upload: ${source.displayName} reads as zero bytes, refusing to send")
            return@withContext DataResult.Failure(RemoteError.EmptyLocalFile)
        }

        try {
            val root = ensureFolder(token, "GallerySync", "root")
                ?: return@withContext failureAfterLookup()
            val parent = ensureFolder(token, album.trim('/'), root)
                ?: return@withContext failureAfterLookup()
            uploadFile(token, parent, source, onProgress)
        } catch (e: FileNotFoundException) {
            DataResult.Failure(RemoteError.LocalFileMissing)
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            Logger.w(TAG, "upload: network failure: ${e.message}")
            DataResult.Failure(RemoteError.Network)
        }
    }

    /** A folder lookup or create failed: the token is the likeliest cause, and it stops this provider. */
    private fun failureAfterLookup(): DataResult.Failure {
        tokens.invalidate(location)
        return DataResult.Failure(RemoteError.Unauthorized)
    }

    /** The id of the folder [name] under [parentId], creating it if it is not there. Null on failure. */
    private fun ensureFolder(token: String, name: String, parentId: String): String? {
        val cacheKey = "$parentId/$name"
        folderIds[cacheKey]?.let { return it }

        val q = "name='${escapeQuery(name)}' and mimeType='$FOLDER_MIME' and '$parentId' in parents and trashed=false"
        val listUrl = "$apiBase/drive/v3/files".toHttpUrl().newBuilder()
            .addQueryParameter("q", q)
            .addQueryParameter("fields", "files(id)")
            .addQueryParameter("pageSize", "1")
            .build()
        client.newCall(Request.Builder().url(listUrl).header("Authorization", "Bearer $token").build())
            .execute().use { response ->
                val body = response.body?.string()
                if (!response.isSuccessful) return null
                val id = CloudHttp.objectOf(body)?.get("files")?.let {
                    (it as? kotlinx.serialization.json.JsonArray)?.firstOrNull()
                        ?.let { f -> (f as? kotlinx.serialization.json.JsonObject)?.stringOrNull("id") }
                }
                if (id != null) {
                    folderIds[cacheKey] = id
                    return id
                }
            }

        val meta = """{"name":${CloudHttp.asciiJsonString(name)},"mimeType":"$FOLDER_MIME","parents":["$parentId"]}"""
        val create = Request.Builder()
            .url("$apiBase/drive/v3/files?fields=id")
            .header("Authorization", "Bearer $token")
            .post(meta.toRequestBody(JSON))
            .build()
        client.newCall(create).execute().use { response ->
            val body = response.body?.string()
            if (!response.isSuccessful) return null
            val id = CloudHttp.objectOf(body)?.stringOrNull("id") ?: return null
            folderIds[cacheKey] = id
            return id
        }
    }

    private fun uploadFile(
        token: String,
        parentId: String,
        source: UploadSource,
        onProgress: (Long, Long) -> Unit
    ): DataResult<UploadedItem> {
        val mime = CloudMime.of(source.displayName)
        val meta = """{"name":${CloudHttp.asciiJsonString(source.displayName)},"parents":["$parentId"]}"""

        // Step one: open a resumable session. Nothing is stored until the bytes arrive.
        val open = Request.Builder()
            .url("$apiBase/upload/drive/v3/files?uploadType=resumable&fields=id,size")
            .header("Authorization", "Bearer $token")
            .header("X-Upload-Content-Type", mime)
            .header("X-Upload-Content-Length", source.sizeBytes.toString())
            .post(meta.toRequestBody(JSON))
            .build()
        val sessionUrl = client.newCall(open).execute().use { response ->
            if (!response.isSuccessful) {
                val body = response.body?.string()
                return handleFailure(response, body)
            }
            response.header("Location") ?: return DataResult.Failure(
                RemoteError.Unknown(IOException("Drive gave no upload location"))
            )
        }

        // Step two: the bytes, in one request. The session URL carries its own authorisation.
        val put = Request.Builder()
            .url(sessionUrl)
            .put(source.toStreamingRequestBody(mime.toMediaType(), onProgress))
            .build()
        client.newCall(put).execute().use { response ->
            val body = response.body?.string()
            if (!response.isSuccessful) return handleFailure(response, body)
            val id = CloudHttp.objectOf(body)?.stringOrNull("id")
                ?: return DataResult.Failure(RemoteError.Unknown(IOException("Drive returned no file id")))
            onProgress(source.sizeBytes, source.sizeBytes)
            // The size is the LOCAL one: nothing here is used to prove the copy, and the engine records
            // the row without a verified remote size.
            return DataResult.Success(UploadedItem(id, source.displayName, source.sizeBytes, null))
        }
    }

    private fun handleFailure(response: okhttp3.Response, body: String?): DataResult.Failure {
        if (response.code == 401) tokens.invalidate(location)
        // A folder that was deleted in Drive since it was cached would fail every file after it.
        if (response.code == 404) folderIds.clear()
        return CloudHttp.failureFor(response, body, quotaMarkers = listOf("storageQuotaExceeded"))
    }

    private fun escapeQuery(value: String) = value.replace("\\", "\\\\").replace("'", "\\'")

    internal companion object {
        private const val TAG = "GoogleDrive"
        const val PROVIDER_ID = "google_drive"
        const val LABEL = "Google Drive"
        const val FOLDER_MIME = "application/vnd.google-apps.folder"
        private val JSON = "application/json; charset=utf-8".toMediaType()
    }
}
