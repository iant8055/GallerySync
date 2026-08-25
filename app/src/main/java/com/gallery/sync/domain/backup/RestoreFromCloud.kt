package com.gallery.sync.domain.backup

import com.gallery.sync.data.local.media.MediaStoreWriter
import com.gallery.sync.data.local.media.RestoredAlbum
import com.gallery.sync.data.local.media.WriteOutcome
import com.gallery.sync.di.IoDispatcher
import com.gallery.sync.domain.model.DataResult
import com.gallery.sync.domain.repository.OneDriveRepository
import com.gallery.sync.util.Logger
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/** What happened when a file was fetched back. */
sealed interface RestoreResult {

    data class Restored(val bytesWritten: Long) : RestoreResult

    /** Below API 29 there is no way to publish a new media file safely. */
    data object Unsupported : RestoreResult

    data class Failed(val reason: String) : RestoreResult
}

/**
 * Fetches one file back from OneDrive onto the phone.
 *
 * ### Non-destructive, and repeatable
 *
 * Ian, 24 Aug 2026: retrieved files stay in the cloud and can be fetched as often as the user likes.
 * Nothing here deletes, moves or marks anything remotely — a restore is a copy downwards, and the
 * cloud copy remains the thing the safety guarantees rest on.
 *
 * ### Why this matters more than it looks
 *
 * Proxying caps what an editor can import at 2048px, so without retrieval a photo optimised months
 * ago can never be recovered at full quality. The milestones call it load-bearing rather than a
 * nicety for that reason, and both the video transcode work and Archive mode are gated behind it.
 *
 * ### The one direction Android allows unattended
 *
 * Writing a *new* file needs no consent dialog, unlike every other media operation in this app. See
 * [MediaStoreWriter].
 */
@Singleton
class RestoreFromCloud @Inject constructor(
    private val repository: OneDriveRepository,
    private val writer: MediaStoreWriter,
    @param:IoDispatcher private val dispatcher: CoroutineDispatcher
) {

    /**
     * Downloads [remoteItemId] and publishes it into the hidden Restored album.
     *
     * [sizeBytes] is what OneDrive reports, and the write is rejected unless exactly that many bytes
     * arrive. A short read leaves nothing behind: this may be the only copy coming back, and a
     * truncated photo that looks whole is worse than a failure the user can retry.
     */
    suspend fun restore(
        remoteItemId: String,
        displayName: String,
        mimeType: String,
        isVideo: Boolean,
        sizeBytes: Long,
        onProgress: (bytesWritten: Long, total: Long) -> Unit = { _, _ -> }
    ): RestoreResult = withContext(dispatcher) {
        if (!writer.isSupported()) return@withContext RestoreResult.Unsupported

        Logger.i(TAG, "restoring $displayName ($sizeBytes bytes)")

        val stream = when (val opened = repository.openStream(remoteItemId)) {
            is DataResult.Success -> opened.value
            is DataResult.Failure -> {
                Logger.w(TAG, "could not open $displayName: ${opened.error}")
                return@withContext RestoreResult.Failed("could not reach OneDrive")
            }
        }

        val outcome = writer.write(
            displayName = displayName,
            mimeType = mimeType,
            relativePath = RestoredAlbum.RELATIVE_PATH,
            isVideo = isVideo,
            expectedBytes = sizeBytes,
            onProgress = { written -> onProgress(written, sizeBytes) },
            source = { stream }
        )

        when (outcome) {
            is WriteOutcome.Success -> RestoreResult.Restored(outcome.bytesWritten)
            is WriteOutcome.Unsupported -> RestoreResult.Unsupported
            is WriteOutcome.Failed -> RestoreResult.Failed(outcome.reason)
        }
    }

    private companion object {
        const val TAG = "Restore"
    }
}
