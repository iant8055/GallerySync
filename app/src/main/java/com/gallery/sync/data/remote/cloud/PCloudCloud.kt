package com.gallery.sync.data.remote.cloud

import android.app.Activity
import android.content.Intent
import android.net.Uri
import com.gallery.sync.data.remote.auth.GoogleSignInResultBridge
import com.gallery.sync.data.remote.auth.SignInResult
import com.gallery.sync.data.remote.googlephotos.toStreamingRequestBody
import com.gallery.sync.data.remote.onedrive.UploadSource
import com.gallery.sync.di.IoDispatcher
import com.gallery.sync.domain.backup.BackupLocation
import com.gallery.sync.domain.model.DataResult
import com.gallery.sync.domain.model.RemoteError
import com.gallery.sync.domain.model.UploadedItem
import com.gallery.sync.domain.repository.CloudUploader
import com.gallery.sync.util.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.FileNotFoundException
import java.io.IOException
import java.net.URLDecoder
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * pCloud: files land in `/GallerySync/<album>/<name>`.
 *
 * pCloud's authorisation-code flow needs a client **secret**, which cannot live in an app. Its
 * *token* flow (`response_type=token`) returns the access token in the redirect and needs none, so that
 * is what is used — a plain browser round trip caught by [PCloudRedirectActivity]. The token is
 * long-lived, so there is no refresh. Not offered until an app id is filled in `cloud_oauth_config.json`
 * and the redirect (`com.gallery.sync.pcloud:/callback`) is registered with pCloud. Backup-only.
 *
 * pCloud keeps users in a US or a European data centre and expects API calls at the right host; the
 * host the sign-in reports is stored and used.
 */
@Singleton
class PCloudCloud @Inject constructor(
    private val configs: CloudOAuthConfigs,
    private val secrets: EncryptedCloudSecretsStore,
    private val bridge: GoogleSignInResultBridge,
    @param:CloudUploadClient private val client: OkHttpClient,
    @param:IoDispatcher private val dispatcher: CoroutineDispatcher
) : CloudConnection, CloudUploader {

    override val location = BackupLocation.PCLOUD

    override val kind = ConnectionKind.OAUTH

    override val isOfferedInThisBuild: Boolean get() = configs.clientFor(PROVIDER_ID) != null

    /** Overridden in tests: a full base URL, scheme included, in place of `https://<host>`. */
    internal var baseOverride: String? = null

    private val ensured = ConcurrentHashMap.newKeySet<String>()

    override suspend fun accountLabel(): String? =
        if (secrets.read(KEY_TOKEN) != null) LABEL else null

    override suspend fun isConnected(): Boolean = secrets.read(KEY_TOKEN) != null

    override suspend fun signIn(activity: Activity): SignInResult {
        val clientConfig = configs.clientFor(PROVIDER_ID) ?: return SignInResult.Failed("not_configured")
        val state = randomState()
        secrets.write(KEY_PENDING_STATE, state)
        val deferred = bridge.beginWait()

        val url = Uri.parse("https://my.pcloud.com/oauth2/authorize").buildUpon()
            .appendQueryParameter("client_id", clientConfig.clientId)
            .appendQueryParameter("response_type", "token")
            .appendQueryParameter("redirect_uri", clientConfig.redirectUri)
            .appendQueryParameter("state", state)
            .build()
        Logger.i(TAG, "launching sign-in")
        activity.startActivity(Intent(Intent.ACTION_VIEW, url))
        return deferred.await()
    }

    override suspend fun signOut() {
        secrets.clearPrefix("${location.name}.")
        ensured.clear()
    }

    override suspend fun upload(
        source: UploadSource,
        album: String,
        onProgress: (bytesSent: Long, total: Long) -> Unit
    ): DataResult<UploadedItem> = withContext(dispatcher) {
        val token = secrets.read(KEY_TOKEN) ?: return@withContext DataResult.Failure(RemoteError.NoToken)
        if (source.sizeBytes <= 0L) {
            Logger.w(TAG, "upload: ${source.displayName} reads as zero bytes, refusing to send")
            return@withContext DataResult.Failure(RemoteError.EmptyLocalFile)
        }
        val base = baseOverride ?: "https://${secrets.read(KEY_HOST) ?: DEFAULT_HOST}"

        try {
            val folder = "/GallerySync/${album.trim('/')}"
            for (path in listOf("/GallerySync", folder)) {
                if (path in ensured) continue
                val result = call(base, "createfolderifnotexists", token, "path" to path)
                if (result != null) return@withContext result
                ensured.add(path)
            }

            val url = "$base/uploadfile".toHttpUrl().newBuilder()
                .addQueryParameter("path", folder)
                .addQueryParameter("filename", source.displayName)
                .addQueryParameter("nopartial", "1")
                .addQueryParameter("access_token", token)
                .build()
            val multipart = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart(
                    "file",
                    source.displayName,
                    source.toStreamingRequestBody(CloudMime.of(source.displayName).toMediaType(), onProgress)
                )
                .build()
            client.newCall(Request.Builder().url(url).post(multipart).build()).execute().use { response ->
                val body = response.body?.string()
                if (!response.isSuccessful) return@use CloudHttp.failureFor(response, body)
                val json = CloudHttp.objectOf(body)
                val code = json?.get("result")?.toString()?.toIntOrNull()
                if (code != 0) return@use failureForResult(code, body)
                val id = (json["fileids"] as? kotlinx.serialization.json.JsonArray)?.firstOrNull()?.toString()
                    ?: source.displayName
                onProgress(source.sizeBytes, source.sizeBytes)
                // The LOCAL size: nothing here proves the copy, and the engine records the row unverified.
                DataResult.Success(UploadedItem(id, source.displayName, source.sizeBytes, null))
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

    /** One simple GET. Null means it worked; otherwise the failure to return. */
    private fun call(base: String, method: String, token: String, vararg params: Pair<String, String>): DataResult.Failure? {
        val url = "$base/$method".toHttpUrl().newBuilder().apply {
            params.forEach { (k, v) -> addQueryParameter(k, v) }
            addQueryParameter("access_token", token)
        }.build()
        client.newCall(Request.Builder().url(url).build()).execute().use { response ->
            val body = response.body?.string()
            if (!response.isSuccessful) return CloudHttp.failureFor(response, body)
            val code = CloudHttp.objectOf(body)?.get("result")?.toString()?.toIntOrNull()
            return if (code == 0) null else failureForResult(code, body)
        }
    }

    /** pCloud answers 200 with a `result` code; 0 is success. */
    private fun failureForResult(code: Int?, body: String?): DataResult.Failure = when (code) {
        1000, 2000, 2094, 2095 -> DataResult.Failure(RemoteError.Unauthorized) // log in required / bad token
        2008 -> DataResult.Failure(RemoteError.InsufficientStorage) // over quota
        else -> DataResult.Failure(RemoteError.Http(200, body))
    }

    private fun randomState(): String {
        val bytes = ByteArray(16).also { SecureRandom().nextBytes(it) }
        return bytes.joinToString("") { "%02x".format(it) }
    }

    internal companion object {
        private const val TAG = "PCloud"
        const val PROVIDER_ID = "pcloud"
        const val LABEL = "pCloud"
        const val DEFAULT_HOST = "api.pcloud.com"
        const val KEY_TOKEN = "PCLOUD.access_token"
        const val KEY_HOST = "PCLOUD.hostname"
        const val KEY_PENDING_STATE = "PCLOUD.pending_state"

        /** Parses `a=b&c=d` (a redirect's fragment or query) into a map, percent-decoded. */
        fun parseParams(raw: String?): Map<String, String> =
            raw.orEmpty().split('&').filter { it.contains('=') }.associate {
                URLDecoder.decode(it.substringBefore('='), "UTF-8") to URLDecoder.decode(it.substringAfter('='), "UTF-8")
            }
    }
}
