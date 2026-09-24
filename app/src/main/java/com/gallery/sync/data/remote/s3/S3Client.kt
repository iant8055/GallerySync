package com.gallery.sync.data.remote.s3

import com.gallery.sync.data.remote.cloud.CloudUploadClient
import com.gallery.sync.data.remote.googlephotos.toStreamingRequestBody
import com.gallery.sync.data.remote.onedrive.UploadSource
import com.gallery.sync.util.Logger
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.Response
import java.io.IOException
import java.security.MessageDigest
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton

/** What an S3-compatible store needs to be talked to. */
data class S3Config(
    /** `https://host[:port]`, no trailing slash, no bucket. */
    val endpoint: String,
    val region: String,
    val bucket: String,
    val accessKey: String,
    val secretKey: String
)

/**
 * A minimal S3-compatible client: check a bucket, put an object. Path-style addressing, which IDrive
 * e2 and Backblaze B2 both accept, so one client serves both.
 *
 * No Retrofit: SigV4 needs the exact bytes of the request line and headers, and a hand-built OkHttp
 * request is the honest way to control them. Nothing here logs a key, and the shared client has no
 * logging interceptor at all, so a body can never end up in the log.
 */
@Singleton
class S3Client @Inject constructor(
    @param:CloudUploadClient private val client: OkHttpClient
) {

    /** `HEAD /bucket` — 200 means the endpoint, region, bucket and keys all work together. */
    fun headBucket(config: S3Config): Response {
        val url = urlFor(config, key = null)
        val headers = SigV4.sign(
            method = "HEAD",
            host = hostOf(url),
            path = "/${config.bucket}",
            query = "",
            payloadSha256Hex = EMPTY_SHA256,
            region = config.region,
            accessKey = config.accessKey,
            secretKey = config.secretKey,
            now = Date()
        ).headers
        val request = Request.Builder().url(url).head().apply { headers.forEach { (k, v) -> header(k, v) } }.build()
        return client.newCall(request).execute()
    }

    /**
     * `PUT /bucket/key`. The payload's SHA-256 is computed first, by reading the file once, and signed
     * into the request: every S3-compatible store accepts a real hash, where "unsigned payload" is one
     * more thing a provider might not support. Local reads are cheap next to the upload.
     *
     * Returns the [Response]; the caller maps the status. One PUT carries at most 5 GB, the S3 limit;
     * anything larger is refused before it starts rather than failing half way.
     */
    fun putObject(
        config: S3Config,
        key: String,
        source: UploadSource,
        mimeType: String,
        onProgress: (Long, Long) -> Unit
    ): Response {
        if (source.sizeBytes > MAX_SINGLE_PUT_BYTES) {
            throw IOException("file is larger than the 5 GB a single upload carries")
        }
        val sha = sha256Of(source)
        val url = urlFor(config, key)
        val headers = SigV4.sign(
            method = "PUT",
            host = hostOf(url),
            path = "/${config.bucket}/${SigV4.encodePath(key)}",
            query = "",
            payloadSha256Hex = sha,
            region = config.region,
            accessKey = config.accessKey,
            secretKey = config.secretKey,
            now = Date()
        ).headers
        val body: RequestBody = source.toStreamingRequestBody(mimeType.toMediaType(), onProgress)
        val request = Request.Builder().url(url).put(body).apply { headers.forEach { (k, v) -> header(k, v) } }.build()
        return client.newCall(request).execute()
    }

    private fun sha256Of(source: UploadSource): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(BUFFER_BYTES)
        source.open().use { reader ->
            var offset = 0L
            while (offset < source.sizeBytes) {
                val size = minOf(BUFFER_BYTES.toLong(), source.sizeBytes - offset).toInt()
                reader.readFully(offset, buffer, size)
                digest.update(buffer, 0, size)
                offset += size
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun urlFor(config: S3Config, key: String?): okhttp3.HttpUrl {
        val path = if (key == null) "/${config.bucket}" else "/${config.bucket}/${SigV4.encodePath(key)}"
        return (config.endpoint + path).toHttpUrlOrNull()
            ?: run {
                Logger.w(TAG, "endpoint is not a valid URL")
                throw IOException("the endpoint is not a valid address")
            }
    }

    /** The `Host` header as OkHttp will send it: the port only when it is not the scheme's default. */
    private fun hostOf(url: okhttp3.HttpUrl): String =
        if (url.port == okhttp3.HttpUrl.defaultPort(url.scheme)) url.host else "${url.host}:${url.port}"

    private companion object {
        const val TAG = "S3Client"
        const val EMPTY_SHA256 = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"
        const val MAX_SINGLE_PUT_BYTES = 5L * 1024 * 1024 * 1024
        const val BUFFER_BYTES = 1024 * 1024
    }
}
