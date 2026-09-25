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
import java.io.InputStream
import com.gallery.sync.domain.repository.CloudDownloader
import com.gallery.sync.domain.repository.CloudUploader
import com.gallery.sync.util.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.FileNotFoundException
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Dropbox: files land in `/GallerySync/<album>/<name>` in the app's own area of the user's Dropbox
 * (an "app folder" app) or the root, depending on how the app is registered.
 *
 * Sign-in is standard PKCE with no client secret. Not offered until an app key is filled in
 * `cloud_oauth_config.json`, and the redirect URI there (`com.gallery.sync:/dropbox`) must be added to
 * the Dropbox app's settings exactly. Backup-only, like every cloud but OneDrive.
 *
 * Files up to [SINGLE_REQUEST_LIMIT_BYTES] go in one request; anything larger goes through an upload
 * session in [CHUNK_BYTES] pieces, because Dropbox refuses a single request above 150 MB and videos
 * routinely exceed that.
 */
@Singleton
class DropboxCloud @Inject constructor(
    private val configs: CloudOAuthConfigs,
    private val signInFlow: AppAuthCloudSignIn,
    private val tokens: AppAuthCloudTokens,
    @param:CloudUploadClient private val client: OkHttpClient,
    @param:IoDispatcher private val dispatcher: CoroutineDispatcher
) : CloudConnection, CloudUploader, CloudDownloader {

    override val location = BackupLocation.DROPBOX

    override val kind = ConnectionKind.OAUTH

    override val isOfferedInThisBuild: Boolean get() = configs.clientFor(PROVIDER_ID) != null

    /** Overridden in tests to point at a local server. */
    internal var contentBase = "https://content.dropboxapi.com"

    /** Lowered in tests so the session path can be exercised with a small file. */
    internal var singleRequestLimit = SINGLE_REQUEST_LIMIT_BYTES
    internal var chunkBytes = CHUNK_BYTES

    private val provider = OAuthProvider(
        location = location,
        authEndpoint = "https://www.dropbox.com/oauth2/authorize",
        tokenEndpoint = "https://api.dropboxapi.com/oauth2/token",
        scopes = listOf("files.content.write"),
        // Without offline access Dropbox hands out a token that dies in hours and no refresh token.
        extraParams = mapOf("token_access_type" to "offline")
    )

    override suspend fun accountLabel(): String? = if (tokens.isSignedIn(location)) LABEL else null

    override suspend fun isConnected(): Boolean = tokens.isSignedIn(location)

    override suspend fun signIn(activity: Activity): SignInResult {
        val clientConfig = configs.clientFor(PROVIDER_ID) ?: return SignInResult.Failed("not_configured")
        return signInFlow.signIn(activity, provider, clientConfig)
    }

    override suspend fun signOut() {
        tokens.signOut(location)
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

        val path = "/GallerySync/${album.trim('/')}/${source.displayName}"
        try {
            if (source.sizeBytes <= singleRequestLimit) {
                uploadSingle(token, path, source, onProgress)
            } else {
                uploadInSession(token, path, source, onProgress)
            }
        } catch (e: FileNotFoundException) {
            DataResult.Failure(RemoteError.LocalFileMissing)
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            Logger.w(TAG, "upload: network failure: ${e.message}")
            DataResult.Failure(RemoteError.Network)
        }
    }

    /**
     * Opens a file for Restore. `remoteItemId` is the `id:…` Dropbox returned at upload, which its download
     * endpoint accepts in place of a path, so the file is found even if it was moved or renamed in Dropbox.
     * A file that is no longer there answers 409 `path/not_found`, reported as a 404 so Restore says it is gone.
     */
    override suspend fun openStream(remoteItemId: String): DataResult<InputStream> = withContext(dispatcher) {
        val token = tokens.accessToken(location) ?: return@withContext DataResult.Failure(RemoteError.NoToken)
        val request = Request.Builder()
            .url("$contentBase/2/files/download")
            .header("Authorization", "Bearer $token")
            .header("Dropbox-API-Arg", """{"path":${CloudHttp.asciiJsonString(remoteItemId)}}""")
            .post(ByteArray(0).toRequestBody(null))
            .build()
        try {
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                val body = response.body?.string()
                response.close()
                return@withContext if (response.code == 409 && body?.contains("not_found") == true) {
                    DataResult.Failure(RemoteError.Http(404, body))
                } else {
                    failure(response, body)
                }
            }
            val stream = response.body?.byteStream()
                ?: run {
                    response.close()
                    return@withContext DataResult.Failure(RemoteError.Unknown(IOException("Dropbox sent no file")))
                }
            DataResult.Success(stream)
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            Logger.w(TAG, "download: network failure: ${e.message}")
            DataResult.Failure(RemoteError.Network)
        }
    }

    private fun commitArg(path: String) =
        """{"path":${CloudHttp.asciiJsonString(path)},"mode":"overwrite","autorename":false,"mute":true}"""

    private fun uploadSingle(
        token: String,
        path: String,
        source: UploadSource,
        onProgress: (Long, Long) -> Unit
    ): DataResult<UploadedItem> {
        val request = Request.Builder()
            .url("$contentBase/2/files/upload")
            .header("Authorization", "Bearer $token")
            .header("Dropbox-API-Arg", commitArg(path))
            .post(source.toStreamingRequestBody(OCTET, onProgress))
            .build()
        return client.newCall(request).execute().use { response ->
            val body = response.body?.string()
            if (!response.isSuccessful) return@use failure(response, body)
            finished(source, body, onProgress)
        }
    }

    private fun uploadInSession(
        token: String,
        path: String,
        source: UploadSource,
        onProgress: (Long, Long) -> Unit
    ): DataResult<UploadedItem> {
        val start = Request.Builder()
            .url("$contentBase/2/files/upload_session/start")
            .header("Authorization", "Bearer $token")
            .header("Dropbox-API-Arg", """{"close":false}""")
            .post(ByteArray(0).toRequestBody(OCTET))
            .build()
        val sessionId = client.newCall(start).execute().use { response ->
            val body = response.body?.string()
            if (!response.isSuccessful) return failure(response, body)
            CloudHttp.objectOf(body)?.stringOrNull("session_id")
                ?: return DataResult.Failure(RemoteError.Unknown(IOException("Dropbox gave no session id")))
        }

        val total = source.sizeBytes
        val buffer = ByteArray(chunkBytes)
        var offset = 0L
        source.open().use { reader ->
            while (offset < total) {
                val size = minOf(chunkBytes.toLong(), total - offset).toInt()
                reader.readFully(offset, buffer, size)
                val chunk: RequestBody = buffer.copyOf(size).toRequestBody(OCTET)
                val append = Request.Builder()
                    .url("$contentBase/2/files/upload_session/append_v2")
                    .header("Authorization", "Bearer $token")
                    .header(
                        "Dropbox-API-Arg",
                        """{"cursor":{"session_id":"$sessionId","offset":$offset},"close":false}"""
                    )
                    .post(chunk)
                    .build()
                client.newCall(append).execute().use { response ->
                    if (!response.isSuccessful) return failure(response, response.body?.string())
                }
                offset += size
                onProgress(offset, total)
            }
        }

        val finish = Request.Builder()
            .url("$contentBase/2/files/upload_session/finish")
            .header("Authorization", "Bearer $token")
            .header(
                "Dropbox-API-Arg",
                """{"cursor":{"session_id":"$sessionId","offset":$total},"commit":${commitArg(path)}}"""
            )
            .post(ByteArray(0).toRequestBody(OCTET))
            .build()
        return client.newCall(finish).execute().use { response ->
            val body = response.body?.string()
            if (!response.isSuccessful) return@use failure(response, body)
            finished(source, body, onProgress)
        }
    }

    private fun finished(source: UploadSource, body: String?, onProgress: (Long, Long) -> Unit): DataResult<UploadedItem> {
        val id = CloudHttp.objectOf(body)?.stringOrNull("id")
            ?: return DataResult.Failure(RemoteError.Unknown(IOException("Dropbox returned no file id")))
        onProgress(source.sizeBytes, source.sizeBytes)
        // The LOCAL size: nothing here proves the copy, and the engine records the row unverified.
        return DataResult.Success(UploadedItem(id, source.displayName, source.sizeBytes, null))
    }

    private fun failure(response: okhttp3.Response, body: String?): DataResult.Failure {
        if (response.code == 401) tokens.invalidate(location)
        return CloudHttp.failureFor(response, body, quotaMarkers = listOf("insufficient_space"))
    }

    internal companion object {
        private const val TAG = "Dropbox"
        const val PROVIDER_ID = "dropbox"
        const val LABEL = "Dropbox"

        /** Dropbox refuses a single upload request above 150 MB; stay well under it. */
        const val SINGLE_REQUEST_LIMIT_BYTES = 100L * 1024 * 1024
        const val CHUNK_BYTES = 8 * 1024 * 1024
        private val OCTET = "application/octet-stream".toMediaType()
    }
}
