package com.gallery.sync.data.remote.onedrive

import com.gallery.sync.data.remote.onedrive.dto.ChunkAcceptedDto
import com.gallery.sync.data.remote.onedrive.dto.CreateUploadSessionRequestDto
import com.gallery.sync.data.remote.onedrive.dto.UploadablePropertiesDto
import com.gallery.sync.data.remote.onedrive.dto.UploadedItemDto
import com.gallery.sync.util.Logger
import kotlinx.coroutines.ensureActive
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.ResponseBody
import retrofit2.Response
import java.io.File
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.coroutineContext

/** Outcome of one upload attempt. Network failures surface as [java.io.IOException] to the caller. */
sealed interface UploadOutcome {

    data class Success(val item: UploadedItemDto) : UploadOutcome

    data class HttpFailure(val code: Int, val body: String?) : UploadOutcome

    /**
     * The local file read as zero bytes, so nothing was sent.
     *
     * Not a success and not a network failure — a refusal. Uploading it would have written an empty
     * file to the drive under this photo's name, and because `conflictBehavior` is `rename` (see
     * `UploadablePropertiesDto`, and it is right to be) that name could never afterwards be
     * corrected by retrying: every later attempt files a sibling beside the empty one.
     *
     * Worse than untidy. An empty upload reports its size as 0, the ledger records 0 against a local
     * file that also reads 0, `verifiedInCloud()` sees them match, and the file becomes eligible for
     * removal from the phone — a data-loss path whose every individual step looks correct.
     *
     * A zero-length read is nearly always transient: a content URI whose file is mid-write, being
     * proxied, or already trashed. Deferring costs one run; uploading costs the name forever.
     */
    data object EmptySource : UploadOutcome
}

/**
 * Uploads a single local file to OneDrive, resuming rather than restarting after interruption.
 *
 * Small files go up in one request; anything larger uses a resumable upload session so that a
 * 100 MB video interrupted at 90 MB on mobile data does not begin again from zero.
 *
 * This class only ever **adds** files. It does not delete, move, or overwrite anything.
 *
 * ### A name that is already taken (20 Sept 2026, late)
 *
 * Every upload first asks OneDrive to **fail** if the name exists. A 409 is then looked into rather
 * than papered over: what is at that path, and is it the same size? If so this very file has already
 * arrived, which is what a hard kill mid-upload leaves behind (OneDrive finishes the upload after the app
 * has gone, and the next folder listing may not show it yet), and the answer is [UploadOutcome.Success]
 * with that item, sending nothing. If it is a different size, or not there after all, the file goes up
 * again asking for **rename**, so it lands beside the other.
 *
 * **One exception to "never replace", found by a repeated-kill test on the Moto G.** A resumable session
 * makes a zero-byte placeholder under the file's name the moment it is created. Kill the app right then
 * and the placeholder is orphaned, and the retry finds the name held by an empty file. That, and not a
 * finished upload the listing had not caught up with (which is what this comment first assumed), is what
 * produced the " 1" duplicates. An item that is read and found to hold **zero bytes**, with its eTag, is
 * filled in place (`replace`, guarded by `If-Match`): nothing of anyone's is lost by replacing nothing.
 * Anything with content in it is never replaced.
 *
 * Same name and same size is the app's own definition of the same file everywhere else too (the
 * folder listing it skips uploads by, and `verifiedInCloud`), so this adds no new way to be wrong.
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
        onProgress: (bytesSent: Long, total: Long) -> Unit = { _, _ -> },
        existingSession: ResumableSession? = null,
        onSessionCreated: suspend (ResumableSession) -> Unit = {}
    ): UploadOutcome {
        val total = source.sizeBytes
        val remotePath = buildRemotePath(remoteFolderPath, source.displayName)

        // Nothing to send, so send nothing. See UploadOutcome.EmptySource for why this is a refusal
        // rather than a trivially successful upload of no bytes.
        if (total <= 0L) {
            Logger.w(TAG, "not uploading ${source.displayName}: it reads as zero bytes")
            return UploadOutcome.EmptySource
        }

        val first = attempt(
            source, remotePath, total, onProgress, existingSession, onSessionCreated, NameClash.FAIL
        )
        if (first !is UploadOutcome.HttpFailure || first.code != HTTP_CONFLICT) return first

        return whenTheNameIsTaken(source, remotePath, total, onProgress, onSessionCreated)
    }

    /**
     * What to do if the name is already in the folder. [FILL_EMPTY] is only ever chosen for an item that
     * was read and found to hold no bytes.
     */
    private enum class NameClash { FAIL, RENAME, FILL_EMPTY }

    private suspend fun attempt(
        source: UploadSource,
        remotePath: String,
        total: Long,
        onProgress: (Long, Long) -> Unit,
        existingSession: ResumableSession?,
        onSessionCreated: suspend (ResumableSession) -> Unit,
        clash: NameClash,
        expectedETag: String? = null
    ): UploadOutcome =
        if (total < SMALL_FILE_THRESHOLD_BYTES) {
            // Small files go in one request, so there is no session to resume and nothing an
            // interruption could leave half-done.
            uploadSmall(source, remotePath, total, onProgress, clash)
        } else {
            uploadChunked(
                source, remotePath, total, onProgress, existingSession, onSessionCreated, clash, expectedETag
            )
        }

    /**
     * OneDrive said the name is taken. Find out by what.
     *
     * The same size means this file is already there, so it is reported as uploaded and nothing more is
     * sent. If a different file has the name, or nothing does after all (the file went between the two
     * calls), it is uploaded again beside it. If OneDrive cannot say what is there, or the connection
     * drops, the file stays pending and is tried on the next run: a retry costs nothing, a guess can
     * make a duplicate.
     */
    private suspend fun whenTheNameIsTaken(
        source: UploadSource,
        remotePath: String,
        total: Long,
        onProgress: (Long, Long) -> Unit,
        onSessionCreated: suspend (ResumableSession) -> Unit
    ): UploadOutcome {
        val lookup = uploadApi.itemAtPath(remotePath)
        val existing = lookup.body()?.takeIf { lookup.isSuccessful }

        if (existing != null && existing.size == total) {
            Logger.i(TAG, "${source.displayName} is already in OneDrive at the same size; not sending it again")
            onProgress(total, total)
            return UploadOutcome.Success(existing)
        }

        // Only "a different file is there" or "nothing is there" may lead to a second upload. If OneDrive
        // could not answer (a 5xx, a throttle), the honest position is that we do not know, and guessing
        // wrong here is exactly what makes a duplicate. The file stays pending and is tried again.
        if (!lookup.isSuccessful && lookup.code() != HTTP_NOT_FOUND) {
            Logger.w(TAG, "${source.displayName}: the name is taken and OneDrive would not say by what (HTTP ${lookup.code()}); leaving it for the next run")
            return UploadOutcome.HttpFailure(HTTP_CONFLICT, "name taken; lookup answered ${lookup.code()}")
        }

        // An empty item under a big file's name is what an interrupted upload session leaves behind: the
        // session makes a zero-byte placeholder at once and the app can be killed before it has read the
        // session back, so it can never resume it. Fill it, guarded by its eTag, rather than file the photo
        // beside it and leave the empty one holding the real name. Small files never open a session, so
        // they never leave one; an empty item under a small file's name is not ours to fill.
        if (existing != null && existing.size == 0L && existing.eTag != null &&
            total >= SMALL_FILE_THRESHOLD_BYTES
        ) {
            Logger.i(TAG, "${source.displayName}: the name holds an empty placeholder from an interrupted upload; filling it")
            return attempt(
                source, remotePath, total, onProgress, null, onSessionCreated, NameClash.FILL_EMPTY, existing.eTag
            )
        }

        Logger.i(
            TAG,
            "${source.displayName}: the name is taken by a different file " +
                "(${existing?.size ?: "unreadable"} bytes there, $total here); uploading beside it"
        )
        return attempt(source, remotePath, total, onProgress, null, onSessionCreated, NameClash.RENAME)
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
        onProgress: (Long, Long) -> Unit,
        clash: NameClash
    ): UploadOutcome {
        Logger.d(TAG, "uploading ${source.displayName} as a single request ($total bytes)")

        val bytes = ByteArray(total.toInt())
        source.open().use { it.readFully(0, bytes, total.toInt()) }

        val body = bytes.toRequestBody(OCTET_STREAM)
        val response = when (clash) {
            NameClash.FAIL -> uploadApi.uploadSmallFile(remotePath, body)
            NameClash.RENAME -> uploadApi.uploadSmallFileRenaming(remotePath, body)
            // Never chosen for a small file (see whenTheNameIsTaken); if it ever were, rename is the safe reading.
            NameClash.FILL_EMPTY -> uploadApi.uploadSmallFileRenaming(remotePath, body)
        }

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
        onProgress: (Long, Long) -> Unit,
        existingSession: ResumableSession?,
        onSessionCreated: suspend (ResumableSession) -> Unit,
        clash: NameClash,
        expectedETag: String?
    ): UploadOutcome {
        // Continue a session left over from an interrupted run, when there is one and the server
        // still recognises it. Anything unusable falls through to opening a fresh session, which is
        // exactly the old behaviour.
        val resumed = existingSession
            ?.takeIf { !it.hasExpired() }
            ?.let { session -> resumeOffsetOf(session, total)?.let { session.uploadUrl to it } }

        val uploadUrl: String
        var offset: Long

        if (resumed != null) {
            uploadUrl = resumed.first
            offset = resumed.second
            Logger.i(
                TAG,
                "resuming ${source.displayName} at $offset of $total bytes " +
                    "(${100 * offset / total.coerceAtLeast(1)}% already accepted)"
            )
            onProgress(offset, total)
        } else {
            val sessionResponse = uploadApi.createUploadSession(
                remotePath,
                CreateUploadSessionRequestDto(
                    item = UploadablePropertiesDto(
                        conflictBehavior = when (clash) {
                            NameClash.FAIL -> UploadablePropertiesDto.CONFLICT_BEHAVIOUR_FAIL
                            NameClash.RENAME -> UploadablePropertiesDto.CONFLICT_BEHAVIOUR_RENAME
                            NameClash.FILL_EMPTY -> UploadablePropertiesDto.CONFLICT_BEHAVIOUR_REPLACE
                        }
                    )
                ),
                ifMatch = expectedETag
            )
            if (!sessionResponse.isSuccessful) {
                return UploadOutcome.HttpFailure(
                    sessionResponse.code(),
                    sessionResponse.errorBody()?.string()
                )
            }
            val created = sessionResponse.body()?.uploadUrl
                ?: return UploadOutcome.HttpFailure(sessionResponse.code(), "no uploadUrl in session")

            uploadUrl = created
            offset = 0L

            // Handed over before a single byte goes out. Persisting on success would be useless —
            // the run that fails is precisely the one whose session needs to survive.
            onSessionCreated(
                ResumableSession(created, expiryMillisOf(sessionResponse.body()?.expirationDateTime))
            )
            Logger.d(TAG, "uploading ${source.displayName} in chunks ($total bytes)")
        }
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

    /**
     * How many bytes Graph already holds for [session], or null if it cannot be continued.
     *
     * Null covers every unusable case — the session is gone, the response is unreadable, or the
     * server claims everything has arrived while never having returned a finished item. All of them
     * mean the same thing to the caller: open a new session and start over.
     */
    /**
     * Abandons a session so Graph releases the chunks it is holding.
     *
     * Best effort by design. The caller has already cleared the local row, the session expires on
     * its own within about fifteen minutes, and nothing partial is ever visible in the drive — so a
     * failure here changes nothing a user could observe.
     */
    suspend fun cancelSession(uploadUrl: String) {
        runCatching { chunkApi.cancelSession(uploadUrl) }
            .onSuccess { Logger.d(TAG, "released upload session") }
            .onFailure { Logger.d(TAG, "upload session release failed, letting it expire: ${it.message}") }
    }

    private suspend fun resumeOffsetOf(session: ResumableSession, total: Long): Long? {
        val response = runCatching { chunkApi.querySession(session.uploadUrl) }.getOrNull()
            ?: return null

        if (!response.isSuccessful) {
            Logger.d(TAG, "stored session is no longer usable (HTTP ${response.code()})")
            return null
        }

        val offset = nextOffsetFrom(decode<ChunkAcceptedDto>(response)?.nextExpectedRanges)
        // At or past the end there is no chunk left to send, and the loop below would exit without
        // ever receiving the completed item. A fresh session is the only way out of that.
        return offset?.takeIf { it in 0 until total }
    }

    /**
     * Graph's `expirationDateTime` as epoch millis, or null when absent or unparseable.
     *
     * Null means "unknown", and the session is then trusted until the server says otherwise.
     * Discarding a session because its timestamp was in an unexpected shape would reintroduce the
     * very restart this exists to prevent.
     */
    private fun expiryMillisOf(expirationDateTime: String?): Long? =
        expirationDateTime?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() }

    private inline fun <reified T> decode(response: Response<ResponseBody>): T? =
        response.body()?.string()?.takeIf { it.isNotBlank() }?.let {
            runCatching { json.decodeFromString<T>(it) }.getOrNull()
        }

    companion object {

        private const val TAG = "ChunkedUpload"

        private val OCTET_STREAM = "application/octet-stream".toMediaType()

        private const val HTTP_ACCEPTED = 202
        private const val HTTP_CONFLICT = 409
        private const val HTTP_NOT_FOUND = 404

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
