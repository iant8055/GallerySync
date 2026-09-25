package com.gallery.sync.data.remote.s3

import android.app.Activity
import androidx.annotation.StringRes
import com.gallery.sync.R
import com.gallery.sync.data.remote.auth.SignInResult
import com.gallery.sync.data.remote.cloud.CloudConnection
import com.gallery.sync.data.remote.cloud.CloudMime
import com.gallery.sync.data.remote.cloud.ConnectionKind
import com.gallery.sync.data.remote.cloud.EncryptedCloudSecretsStore
import com.gallery.sync.data.remote.cloud.KeyField
import com.gallery.sync.data.remote.onedrive.UploadSource
import com.gallery.sync.domain.backup.BackupLocation
import com.gallery.sync.domain.model.DataResult
import com.gallery.sync.domain.model.RemoteError
import com.gallery.sync.domain.model.UploadedItem
import com.gallery.sync.domain.repository.CloudUploader
import com.gallery.sync.util.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.FileNotFoundException
import java.io.IOException

/**
 * Connection and uploading for one S3-compatible store: the user pastes the endpoint, region, bucket
 * and the access/secret key pair the provider issued. IDrive e2 and Backblaze B2 are two instances;
 * nothing else differs, and any other S3-compatible store would be a third line, not a third adapter.
 *
 * **No app registration is needed** — the keys are the user's own, which is why these two work
 * without anything from a developer console. They are stored encrypted ([EncryptedCloudSecretsStore])
 * and only after a signed `HEAD` on the bucket succeeds, so a wrong key fails in front of the user.
 */
abstract class S3CompatibleCloud(
    override val location: BackupLocation,
    @StringRes endpointHint: Int,
    @StringRes regionHint: Int,
    private val secrets: EncryptedCloudSecretsStore,
    private val s3: S3Client,
    private val dispatcher: CoroutineDispatcher
) : CloudConnection, CloudUploader {

    override val kind = ConnectionKind.ACCESS_KEYS

    /** Always offered: nothing to register. */
    override val isOfferedInThisBuild = true

    override val keyFields = listOf(
        KeyField(FIELD_ENDPOINT, R.string.cloud_field_endpoint, hintRes = endpointHint),
        KeyField(FIELD_REGION, R.string.cloud_field_region, hintRes = regionHint),
        KeyField(FIELD_BUCKET, R.string.cloud_field_bucket),
        KeyField(FIELD_ACCESS_KEY, R.string.cloud_field_access_key),
        KeyField(FIELD_SECRET_KEY, R.string.cloud_field_secret_key, isSecret = true)
    )

    private val prefix get() = "${location.name}."

    override suspend fun accountLabel(): String? =
        loadConfig()?.let { "${it.bucket} · ${hostLabel(it.endpoint)}" }

    override suspend fun isConnected(): Boolean = loadConfig() != null

    override suspend fun signOut() {
        secrets.clearPrefix(prefix)
    }

    override suspend fun signIn(activity: Activity): SignInResult = SignInResult.Failed("not_oauth")

    override suspend fun connectWithKeys(values: Map<String, String>): SignInResult = withContext(dispatcher) {
        val config = configFrom(values) ?: return@withContext SignInResult.Failed(MSG_INCOMPLETE)
        try {
            s3.headBucket(config).use { response ->
                when {
                    response.isSuccessful -> {
                        save(config)
                        SignInResult.Success(accountLabel() ?: config.bucket)
                    }
                    response.code == 403 || response.code == 401 -> {
                        // The HEAD said no but not why. Ask again in a way that explains itself, so the
                        // message can name the box that is wrong (Ian, 24 Sept 2026: a bare "rejected"
                        // left him guessing between five fields).
                        val code = try {
                            s3.refusalCode(config)
                        } catch (e: IOException) {
                            null
                        }
                        Logger.w(TAG, "connect: refused, store code $code")
                        SignInResult.Failed(rejectionMessage(code))
                    }
                    response.code == 404 -> SignInResult.Failed(MSG_BUCKET_NOT_FOUND)
                    else -> SignInResult.Failed("The store answered with error ${response.code}")
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            Logger.w(TAG, "connect: could not reach the endpoint")
            SignInResult.Failed(MSG_UNREACHABLE)
        }
    }

    override suspend fun upload(
        source: UploadSource,
        album: String,
        onProgress: (bytesSent: Long, total: Long) -> Unit
    ): DataResult<UploadedItem> = withContext(dispatcher) {
        val config = loadConfig() ?: return@withContext DataResult.Failure(RemoteError.NoToken)
        if (source.sizeBytes <= 0L) {
            Logger.w(TAG, "upload: ${source.displayName} reads as zero bytes, refusing to send")
            return@withContext DataResult.Failure(RemoteError.EmptyLocalFile)
        }

        val key = objectKey(album, source.displayName)
        try {
            s3.putObject(config, key, source, mimeTypeFor(source.displayName), onProgress).use { response ->
                when {
                    response.isSuccessful -> {
                        onProgress(source.sizeBytes, source.sizeBytes)
                        // sizeBytes is the LOCAL size: this store's answer proves nothing here, and the
                        // engine records the row without a verified remote size.
                        DataResult.Success(
                            UploadedItem(key, source.displayName, source.sizeBytes, response.header("ETag"))
                        )
                    }
                    response.code == 401 || response.code == 403 -> DataResult.Failure(RemoteError.Unauthorized)
                    response.code == 507 -> DataResult.Failure(RemoteError.InsufficientStorage)
                    else -> DataResult.Failure(RemoteError.Http(response.code, response.body?.string()))
                }
            }
        } catch (e: FileNotFoundException) {
            DataResult.Failure(RemoteError.LocalFileMissing)
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            Logger.w(TAG, "upload: network or size failure: ${e.message}")
            // A file over the single-upload limit is one file's problem, not a lost connection, so it
            // must not stop the whole provider's run.
            if (e.message?.contains("5 GB") == true) DataResult.Failure(RemoteError.Unknown(e))
            else DataResult.Failure(RemoteError.Network)
        }
    }

    private suspend fun save(config: S3Config) {
        secrets.write(prefix + FIELD_ENDPOINT, config.endpoint)
        secrets.write(prefix + FIELD_REGION, config.region)
        secrets.write(prefix + FIELD_BUCKET, config.bucket)
        secrets.write(prefix + FIELD_ACCESS_KEY, config.accessKey)
        secrets.write(prefix + FIELD_SECRET_KEY, config.secretKey)
    }

    private suspend fun loadConfig(): S3Config? {
        val endpoint = secrets.read(prefix + FIELD_ENDPOINT) ?: return null
        val region = secrets.read(prefix + FIELD_REGION) ?: return null
        val bucket = secrets.read(prefix + FIELD_BUCKET) ?: return null
        val access = secrets.read(prefix + FIELD_ACCESS_KEY) ?: return null
        val secret = secrets.read(prefix + FIELD_SECRET_KEY) ?: return null
        return S3Config(endpoint, region, bucket, access, secret)
    }

    internal companion object {
        private const val TAG = "S3Cloud"
        const val FIELD_ENDPOINT = "endpoint"
        const val FIELD_REGION = "region"
        const val FIELD_BUCKET = "bucket"
        const val FIELD_ACCESS_KEY = "access_key"
        const val FIELD_SECRET_KEY = "secret_key"

        const val MSG_INCOMPLETE = "Fill in every box, and use an https:// endpoint."
        const val MSG_KEYS_REJECTED = "The store rejected those keys."
        const val MSG_BUCKET_NOT_FOUND = "That bucket was not found."
        const val MSG_UNREACHABLE = "Could not reach that endpoint."

        /** What to tell the user for the error code the store gave, naming the box to check. */
        fun rejectionMessage(code: String?): String = when (code) {
            "InvalidAccessKeyId" ->
                "The store does not recognise that access key ID. Use the full key ID."
            "SignatureDoesNotMatch" ->
                "The secret key or the region does not match. Check both."
            "AuthorizationHeaderMalformed", "InvalidRegionName" ->
                "The region does not match this endpoint. Copy it from the endpoint."
            "AccessDenied" ->
                "These keys are not allowed to use that bucket."
            null -> MSG_KEYS_REJECTED
            else -> "$MSG_KEYS_REJECTED ($code)"
        }

        /** `GallerySync/<album>/<name>` — the same shape for every provider that keeps folders. */
        fun objectKey(album: String, displayName: String): String =
            "GallerySync/${album.trim('/')}/$displayName"

        /**
         * Turns what the user typed into a config, or null if anything is missing or the endpoint is
         * not https (credentials never travel in the clear). A bare host gets `https://`.
         */
        fun configFrom(values: Map<String, String>): S3Config? {
            fun v(id: String) = values[id]?.trim().orEmpty()
            var endpoint = v(FIELD_ENDPOINT).trimEnd('/')
            if (endpoint.isEmpty()) return null
            if (!endpoint.contains("://")) endpoint = "https://$endpoint"
            if (!endpoint.startsWith("https://")) return null
            // Left blank, the region is read from the endpoint where the endpoint carries it, which
            // Backblaze's does (`s3.us-east-005.backblazeb2.com`): one box fewer to get wrong.
            val region = v(FIELD_REGION).ifEmpty { regionFromEndpoint(endpoint).orEmpty() }
            val bucket = v(FIELD_BUCKET).trim('/')
            val access = v(FIELD_ACCESS_KEY)
            val secret = v(FIELD_SECRET_KEY)
            if (region.isEmpty() || bucket.isEmpty() || access.isEmpty() || secret.isEmpty()) return null
            return S3Config(endpoint, region, bucket, access, secret)
        }

        fun hostLabel(endpoint: String): String = endpoint.removePrefix("https://")

        private val REGION_IN_HOST = Regex("""^https://s3\.([a-z0-9-]+)\.(backblazeb2|amazonaws)\.com$""")

        /**
         * The signing region an endpoint names, or null when it does not. Only for hosts whose second
         * label really is the region (Backblaze B2, Amazon S3); IDrive e2's host is not, so its region
         * is still asked for.
         */
        fun regionFromEndpoint(endpoint: String): String? =
            REGION_IN_HOST.matchEntire(endpoint.trimEnd('/'))?.groupValues?.get(1)

        fun mimeTypeFor(name: String): String = CloudMime.of(name)
    }
}
