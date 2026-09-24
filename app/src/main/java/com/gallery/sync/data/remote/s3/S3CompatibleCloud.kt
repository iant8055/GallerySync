package com.gallery.sync.data.remote.s3

import android.app.Activity
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
    private val secrets: EncryptedCloudSecretsStore,
    private val s3: S3Client,
    private val dispatcher: CoroutineDispatcher
) : CloudConnection, CloudUploader {

    override val kind = ConnectionKind.ACCESS_KEYS

    /** Always offered: nothing to register. */
    override val isOfferedInThisBuild = true

    override val keyFields = listOf(
        KeyField(FIELD_ENDPOINT, R.string.cloud_field_endpoint),
        KeyField(FIELD_REGION, R.string.cloud_field_region),
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
                    response.code == 403 || response.code == 401 -> SignInResult.Failed(MSG_KEYS_REJECTED)
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
            val region = v(FIELD_REGION)
            val bucket = v(FIELD_BUCKET).trim('/')
            val access = v(FIELD_ACCESS_KEY)
            val secret = v(FIELD_SECRET_KEY)
            if (region.isEmpty() || bucket.isEmpty() || access.isEmpty() || secret.isEmpty()) return null
            return S3Config(endpoint, region, bucket, access, secret)
        }

        fun hostLabel(endpoint: String): String = endpoint.removePrefix("https://")

        fun mimeTypeFor(name: String): String = CloudMime.of(name)
    }
}
