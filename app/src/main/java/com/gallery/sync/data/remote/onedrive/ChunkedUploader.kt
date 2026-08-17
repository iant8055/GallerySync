package com.gallery.sync.data.remote.onedrive

import com.gallery.sync.data.remote.onedrive.dto.ChunkAcceptedDto
import com.gallery.sync.data.remote.onedrive.dto.UploadedItemDto
import com.gallery.sync.util.Logger
import kotlinx.coroutines.ensureActive
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.ResponseBody
import retrofit2.Response
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.coroutineContext

/** Outcome of one upload attempt. Network failures surface as [java.io.IOException] to the caller. */
sealed interface UploadOutcome {

    data class Success(val item: UploadedItemDto) : UploadOutcome

    data class HttpFailure(val code: Int, val body: String?) : UploadOutcome
}

/**
 * Uploads a single local file to OneDrive, resuming rather than restarting after interruption.
 *
 * Small files go up in one request; anything larger uses a resumable upload session so that a
 * 100 MB video interrupted at 90 MB on mobile data does not begin again from zero.
 *
 * This class only ever **adds** files. It does not delete, move, or overwrite anything.
 */
@Singleton
class ChunkedUploader @Inject constructor(
    private val uploadApi: GraphUploadService,
    private val chunkApi: UploadChunkService,
    private val json: Json
) {

    /**
     * Uploads [file] into the drive folder at [remoteFolderPath] (e.g. `Samsung Gallery/DCIM/Camera`).
     *
     * [onProgress] reports bytes confirmed by the server, not bytes handed to the socket.
     */
    suspend fun upload(
        source: UploadSource,
        remoteFolderPath: String,
        onProgress: (bytesSent: Long, total: Long) -> Unit = { _, _ -> }
    ): UploadOutcome {
        val total = source.sizeBytes
        val remotePath = buildRemotePath(remoteFolderPath, source.displayName)

        return if (total < SMALL_FILE_THRESHOLD_BYTES) {
            uploadSmall(source, remotePath, total, onProgress)
        } else {
            uploadChunked(source, remotePath, total, onProgress)
        }
    }

    /** Convenience for callers already holding a [File], such as the debug upload test. */
    suspend fun upload(
        file: File,
        remoteFolderPath: String,
        onProgress: (bytesSent: Long, total: Long) -> Unit = { _, _ -> }
    ): UploadOutcome = upload(FileUploadSource(file), remoteFolderPath, onProgress)

    private suspend fun uploadSmall(
        source: UploadSource,
        remotePath: String,
        total: Long,
        onProgress: (Long, Long) -> Unit
    ): UploadOutcome {
        Logger.d(TAG, "uploading ${source.displayName} as a single request ($total bytes)")

        val bytes = ByteArray(total.toInt())
        if (total > 0) source.open().use { it.readFully(0, bytes, total.toInt()) }

        val response = uploadApi.uploadSmallFile(remotePath, bytes.toRequestBody(OCTET_STREAM))

        return if (response.isSuccessful) {
            onProgress(total, total)
            UploadOutcome.Success(response.body() ?: UploadedItemDto())
        } else {
            UploadOutcome.HttpFailure(response.code(), response.errorBody()?.string())
        }
    }

    private suspend fun uploadChunked(
        source: UploadSource,
        remotePath: String,
        total: Long,
        onProgress: (Long, Long) -> Unit
    ): UploadOutcome {
        val sessionResponse = uploadApi.createUploadSession(remotePath)
        if (!sessionResponse.isSuccessful) {
            return UploadOutcome.HttpFailure(
                sessionResponse.code(),
                sessionResponse.errorBody()?.string()
            )
        }
        val uploadUrl = sessionResponse.body()?.uploadUrl
            ?: return UploadOutcome.HttpFailure(sessionResponse.code(), "no uploadUrl in session")

        Logger.d(TAG, "uploading ${source.displayName} in chunks ($total bytes)")

        var offset = 0L
        source.open().use { reader ->
            while (offset < total) {
                // Cooperative cancellation: a stopped worker must not keep pushing bytes.
                coroutineContext.ensureActive()

                val size = minOf(CHUNK_SIZE_BYTES.toLong(), total - offset).toInt()
                val buffer = ByteArray(size)
                reader.readFully(offset, buffer, size)

                val range = contentRange(offset, offset + size - 1, total)
                val response = chunkApi.uploadChunk(
                    uploadUrl = uploadUrl,
                    contentRange = range,
                    body = buffer.toRequestBody(OCTET_STREAM)
                )

                when {
                    response.code() == HTTP_ACCEPTED -> {
                        // Trust the server's view of what it has, not our own arithmetic: a chunk
                        // can be partially received, and resuming from a local guess would leave
                        // a hole in the file that Graph would happily assemble anyway.
                        val accepted = decode<ChunkAcceptedDto>(response)
                        offset = nextOffsetFrom(accepted?.nextExpectedRanges) ?: (offset + size)
                        onProgress(offset, total)
                    }

                    response.isSuccessful -> {
                        onProgress(total, total)
                        return UploadOutcome.Success(decode<UploadedItemDto>(response) ?: UploadedItemDto())
                    }

                    else -> return UploadOutcome.HttpFailure(
                        response.code(),
                        response.errorBody()?.string()
                    )
                }
            }
        }

        // Every byte was accepted but Graph never returned the finished item.
        return UploadOutcome.HttpFailure(HTTP_ACCEPTED, "upload ended without a completed item")
    }

    private inline fun <reified T> decode(response: Response<ResponseBody>): T? =
        response.body()?.string()?.takeIf { it.isNotBlank() }?.let {
            runCatching { json.decodeFromString<T>(it) }.getOrNull()
        }

    companion object {

        private const val TAG = "ChunkedUpload"

        private val OCTET_STREAM = "application/octet-stream".toMediaType()

        private const val HTTP_ACCEPTED = 202

        /**
         * Graph requires every chunk except the last to be a multiple of 320 KiB and rejects
         * anything else, so this is not a tunable number — only the multiplier is.
         */
        const val CHUNK_ALIGNMENT_BYTES = 327_680

        /** 5 MiB: 16 alignment units. Large enough to be efficient, small enough to retry cheaply. */
        const val CHUNK_SIZE_BYTES = CHUNK_ALIGNMENT_BYTES * 16

        /** Graph's ceiling for a single-request upload. */
        const val SMALL_FILE_THRESHOLD_BYTES = 4L * 1024 * 1024

        /**
         * Builds `bytes {start}-{end}/{total}` where **end is inclusive**.
         *
         * An off-by-one here is the most dangerous bug in this class: Graph accepts the upload and
         * assembles a file that is silently corrupt, so nothing fails loudly and the user discovers
         * it only when a video will not play. Pure and public so it is directly unit tested.
         */
        fun contentRange(start: Long, endInclusive: Long, total: Long): String =
            "bytes $start-$endInclusive/$total"

        /**
         * Reads the resume offset out of Graph's `nextExpectedRanges` (e.g. `"26214400-"`).
         *
         * Returns null when absent or unparseable, leaving the caller to fall back to its own
         * offset. Pure and public so it is directly unit tested.
         */
        fun nextOffsetFrom(ranges: List<String>?): Long? =
            ranges?.firstOrNull()?.substringBefore('-')?.trim()?.toLongOrNull()

        private fun buildRemotePath(folderPath: String, fileName: String): String =
            folderPath.trim('/').let { if (it.isEmpty()) fileName else "$it/$fileName" }
    }
}
