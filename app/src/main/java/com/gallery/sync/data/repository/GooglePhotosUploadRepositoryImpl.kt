package com.gallery.sync.data.repository

import android.webkit.MimeTypeMap
import com.gallery.sync.data.remote.auth.GooglePhotosTokenProvider
import com.gallery.sync.data.remote.googlephotos.GooglePhotosApiService
import com.gallery.sync.data.remote.googlephotos.dto.BatchCreateRequestDto
import com.gallery.sync.data.remote.googlephotos.dto.NewMediaItemDto
import com.gallery.sync.data.remote.googlephotos.dto.SimpleMediaItemDto
import com.gallery.sync.data.remote.googlephotos.WriteRateLimiter
import com.gallery.sync.data.remote.googlephotos.toStreamingRequestBody
import com.gallery.sync.data.remote.onedrive.UploadSource
import com.gallery.sync.di.IoDispatcher
import com.gallery.sync.domain.model.DataResult
import com.gallery.sync.domain.model.RemoteError
import com.gallery.sync.domain.model.UploadedItem
import com.gallery.sync.domain.repository.GooglePhotosUploadRepository
import com.gallery.sync.util.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import java.io.EOFException
import java.io.FileNotFoundException
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Google Photos Library API implementation of [GooglePhotosUploadRepository].
 *
 * Mirrors [OneDriveUploadRepositoryImpl]'s shape: the network boundary lives here, no Retrofit or
 * OkHttp type escapes the data layer, nothing here logs a token. The upload itself is the two-call
 * shape the interface documents — raw bytes, then `batchCreate` — with no chunking or resume
 * between them; see the interface doc and TASK-026 for why that is deliberately not built.
 */
@Singleton
class GooglePhotosUploadRepositoryImpl @Inject constructor(
    private val api: GooglePhotosApiService,
    private val tokenProvider: GooglePhotosTokenProvider,
    private val rateLimiter: WriteRateLimiter,
    @param:IoDispatcher private val dispatcher: CoroutineDispatcher
) : GooglePhotosUploadRepository {

    override suspend fun upload(
        source: UploadSource,
        onProgress: (bytesSent: Long, total: Long) -> Unit
    ): DataResult<UploadedItem> = withContext(dispatcher) {

        if (tokenProvider.getAccessToken() == null) {
            Logger.w(TAG, "upload: no access token, skipping network call")
            return@withContext DataResult.Failure(RemoteError.NoToken)
        }

        val total = source.sizeBytes
        if (total <= 0L) {
            // Same refusal OneDrive's uploader makes, and for the same reason: an empty upload
            // would file a real library item under this photo's name with nothing worth having
            // in it, and there is no reconciliation step here to notice and correct that later —
            // see UploadOutcome.EmptySource.
            Logger.w(TAG, "upload: ${source.displayName} reads as zero bytes, refusing to send")
            return@withContext DataResult.Failure(RemoteError.EmptyLocalFile)
        }

        try {
            when (val outcome = uploadBytes(source, total, onProgress)) {
                is UploadTokenOutcome.Failed -> outcome.result
                is UploadTokenOutcome.Uploaded -> createMediaItem(source, outcome.token)
            }
        } catch (e: FileNotFoundException) {
            Logger.w(TAG, "upload: local file is gone, skipping this one")
            DataResult.Failure(RemoteError.LocalFileMissing)
        } catch (e: EOFException) {
            Logger.w(TAG, "upload: local file truncated, skipping: ${e.message}")
            DataResult.Failure(RemoteError.Unknown(e))
        } catch (e: IOException) {
            Logger.w(TAG, "upload: network failure", e)
            DataResult.Failure(RemoteError.Network)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            Logger.e(TAG, "upload: unexpected failure", e)
            DataResult.Failure(RemoteError.Unknown(e))
        }
    }

    /** Outcome of the raw-bytes step. Not a nullable String: this class is a singleton, and every
     *  in-flight upload needs its own failure to carry back rather than share one mutable field. */
    private sealed interface UploadTokenOutcome {
        data class Uploaded(val token: String) : UploadTokenOutcome
        data class Failed(val result: DataResult.Failure) : UploadTokenOutcome
    }

    /** Step one. Returns the opaque upload token, or the [DataResult.Failure] to report. */
    private suspend fun uploadBytes(
        source: UploadSource,
        total: Long,
        onProgress: (Long, Long) -> Unit
    ): UploadTokenOutcome {
        val mimeType = (guessMimeType(source.displayName) ?: OCTET_STREAM_MIME).toMediaType()
        val body = source.toStreamingRequestBody(mimeType, onProgress)

        val response = withQuota { api.uploadBytes(contentType = mimeType.toString(), body = body) }
        if (response.code() == HTTP_TOO_MANY_REQUESTS) return UploadTokenOutcome.Failed(quotaFailure())

        if (response.code() == HTTP_UNAUTHORIZED) {
            Logger.w(TAG, "upload: Library API returned 401 on the bytes step, invalidating stored token")
            tokenProvider.invalidateAccessToken()
            return UploadTokenOutcome.Failed(DataResult.Failure(RemoteError.Unauthorized))
        }
        if (!response.isSuccessful) {
            val code = response.code()
            Logger.w(TAG, "upload: Library API returned HTTP $code on the bytes step")
            return UploadTokenOutcome.Failed(
                DataResult.Failure(RemoteError.Http(code, response.errorBody()?.string()))
            )
        }

        val token = response.body()?.string()?.trim()
        if (token.isNullOrEmpty()) {
            Logger.w(TAG, "upload: bytes step succeeded but returned no upload token")
            return UploadTokenOutcome.Failed(
                DataResult.Failure(RemoteError.Unknown(IOException("empty upload token")))
            )
        }
        onProgress(total, total)
        return UploadTokenOutcome.Uploaded(token)
    }

    /** Step two: turns [uploadToken] into a real library item. */
    private suspend fun createMediaItem(
        source: UploadSource,
        uploadToken: String
    ): DataResult<UploadedItem> {
        val response = withQuota {
            api.batchCreate(
                BatchCreateRequestDto(
                    newMediaItems = listOf(
                        NewMediaItemDto(SimpleMediaItemDto(uploadToken = uploadToken, fileName = source.displayName))
                    )
                )
            )
        }
        if (response.code() == HTTP_TOO_MANY_REQUESTS) return quotaFailure()

        if (response.code() == HTTP_UNAUTHORIZED) {
            Logger.w(TAG, "upload: Library API returned 401 on batchCreate, invalidating stored token")
            tokenProvider.invalidateAccessToken()
            return DataResult.Failure(RemoteError.Unauthorized)
        }
        if (!response.isSuccessful) {
            val code = response.code()
            Logger.w(TAG, "upload: Library API returned HTTP $code on batchCreate")
            return DataResult.Failure(RemoteError.Http(code, response.errorBody()?.string()))
        }

        val result = response.body()?.newMediaItemResults?.firstOrNull()
        val statusCode = result?.status?.code
        val mediaItem = result?.mediaItem
        val itemId = mediaItem?.id

        // Absent or 0 is success, per the Library API's own convention for this field — see
        // BatchCreateStatusDto. Anything else, or a success with no item attached, is a real failure:
        // the bytes are already on Google's servers by this point but never became a library item,
        // so nothing is visible in the user's library and nothing here believes otherwise.
        if ((statusCode != null && statusCode != 0) || itemId == null) {
            val message = result?.status?.message ?: "batchCreate returned no item"
            Logger.w(TAG, "upload: batchCreate did not create an item for ${source.displayName}: $message")
            return DataResult.Failure(RemoteError.Unknown(IOException(message)))
        }

        Logger.i(TAG, "upload: created ${source.displayName} in Google Photos")
        return DataResult.Success(
            UploadedItem(
                id = itemId,
                name = mediaItem.filename ?: source.displayName,
                // Never Google-confirmed — see GooglePhotosUploadRepository's doc comment.
                sizeBytes = source.sizeBytes,
                eTag = null
            )
        )
    }

    /**
     * One write call, paced, and retried once after a stand-down if Google still answers 429.
     * Never marks a file failed for it: see [WriteRateLimiter].
     */
    private suspend fun <T> withQuota(call: suspend () -> retrofit2.Response<T>): retrofit2.Response<T> {
        rateLimiter.acquire()
        var response = call()
        if (response.code() == HTTP_TOO_MANY_REQUESTS) {
            Logger.w(TAG, "upload: Library API quota reached, standing down before one retry")
            delay(WriteRateLimiter.BACKOFF_MILLIS)
            rateLimiter.acquire()
            response = call()
        }
        return response
    }

    /**
     * Still over quota after standing down. Reported as a network-level stop, which ends only this
     * provider's pass and leaves the file PENDING with its attempts untouched, rather than as a failure of
     * the file — the file is fine, the quota is spent.
     */
    private fun quotaFailure(): DataResult.Failure {
        Logger.w(TAG, "upload: Library API quota still spent; leaving the file for the next pass")
        return DataResult.Failure(RemoteError.Network)
    }

    private companion object {
        const val TAG = "GooglePhotosUpload"
        const val HTTP_TOO_MANY_REQUESTS = 429
        const val HTTP_UNAUTHORIZED = 401
        const val OCTET_STREAM_MIME = "application/octet-stream"

        /**
         * Best-effort guess from the file extension. Sent as `X-Goog-Upload-Content-Type`, which
         * Google's own docs mark optional — an absent or wrong guess makes Google infer the type
         * from the bytes instead, so this is an accuracy improvement, not a correctness dependency.
         */
        fun guessMimeType(displayName: String): String? {
            val extension = displayName.substringAfterLast('.', missingDelimiterValue = "")
                .lowercase()
                .takeIf { it.isNotEmpty() }
                ?: return null
            return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
        }
    }
}
