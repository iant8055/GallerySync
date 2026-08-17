package com.gallery.sync.domain.backup

import android.content.Context
import com.gallery.sync.data.local.dao.BackupEntryDao
import com.gallery.sync.data.local.entity.BackupEntryEntity
import com.gallery.sync.data.local.entity.BackupState
import com.gallery.sync.data.local.entity.backupKeyOf
import com.gallery.sync.data.local.media.MediaAccess
import com.gallery.sync.data.local.media.MediaScanner
import com.gallery.sync.data.remote.onedrive.ContentUriUploadSource
import com.gallery.sync.di.IoDispatcher
import com.gallery.sync.domain.model.DataResult
import com.gallery.sync.domain.model.RemoteError
import com.gallery.sync.domain.repository.OneDriveUploadRepository
import com.gallery.sync.util.Logger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/** Why a backup run stopped before finishing its batch. */
enum class StopReason {

    /** Signed out. Nothing can be uploaded until the user signs in again. */
    NO_TOKEN,

    /** OneDrive rejected the token. Retrying every remaining file would just repeat the failure. */
    UNAUTHORIZED,

    /** The drive is full. The user has to free space; hammering it helps nobody. */
    DRIVE_FULL,

    /** Lost the network. WorkManager will retry the whole run later. */
    NETWORK,

    /** No permission to read the user's media. */
    NO_MEDIA_ACCESS
}

data class BackupRunResult(
    val uploaded: Int,
    val failed: Int,
    val remaining: Int,
    val stoppedBecause: StopReason? = null
) {
    val isComplete: Boolean get() = stoppedBecause == null && remaining == 0
}

/**
 * Backs the device's media up to OneDrive.
 *
 * Deliberately split into two phases. [refreshLedger] records what exists; [uploadPending] moves
 * what has not gone yet. Keeping them apart means the ledger is accurate even when the network is
 * unavailable, and a run that uploads nothing still leaves the app able to say what is outstanding.
 */
@Singleton
class BackupEngine @Inject constructor(
    private val scanner: MediaScanner,
    private val entryDao: BackupEntryDao,
    private val uploadRepository: OneDriveUploadRepository,
    @ApplicationContext private val context: Context,
    @param:IoDispatcher private val dispatcher: CoroutineDispatcher
) {

    /**
     * Records every readable file in the ledger. Existing rows are untouched, so already-uploaded
     * files stay uploaded.
     *
     * Returns how many rows were newly seen, or null when media cannot be read at all.
     */
    suspend fun refreshLedger(): Int? = withContext(dispatcher) {
        if (scanner.access() == MediaAccess.NONE) {
            Logger.w(TAG, "refreshLedger: no media access")
            return@withContext null
        }

        val items = scanner.scanAll()
        val entries = items.map { item ->
            BackupEntryEntity(
                id = backupKeyOf(
                    album = item.album,
                    displayName = item.displayName,
                    sizeBytes = item.sizeBytes,
                    dateModifiedEpochSeconds = item.dateModifiedEpochSeconds
                ),
                mediaStoreId = item.mediaStoreId,
                contentUri = item.contentUri.toString(),
                displayName = item.displayName,
                album = item.album,
                sizeBytes = item.sizeBytes,
                dateModifiedEpochSeconds = item.dateModifiedEpochSeconds,
                mimeType = item.mimeType,
                isVideo = item.isVideo,
                state = BackupState.PENDING
            )
        }

        // IGNORE on conflict, so a rescan never resets an uploaded row back to pending.
        entryDao.insertIfNew(entries)
        Logger.i(TAG, "refreshLedger: ${entries.size} files seen")
        entries.size
    }

    /**
     * Uploads up to [limit] outstanding files.
     *
     * Stops the whole run on a failure that will repeat for every remaining file — no token, a
     * rejected token, a full drive, a dropped network. Continuing would waste the user's battery
     * and data to collect an identical error on each of a thousand photos.
     */
    suspend fun uploadPending(limit: Int = DEFAULT_BATCH): BackupRunResult =
        withContext(dispatcher) {
            if (scanner.access() == MediaAccess.NONE) {
                return@withContext BackupRunResult(0, 0, 0, StopReason.NO_MEDIA_ACCESS)
            }

            val pending = entryDao.nextPending(limit = limit, maxAttempts = MAX_ATTEMPTS)
            var uploaded = 0
            var failed = 0

            for (entry in pending) {
                val source = ContentUriUploadSource(
                    resolver = context.contentResolver,
                    uri = android.net.Uri.parse(entry.contentUri),
                    displayName = entry.displayName,
                    sizeBytes = entry.sizeBytes
                )

                when (val result = uploadRepository.upload(source, remotePathFor(entry.album))) {
                    is DataResult.Success -> {
                        val item = result.value
                        // Size equality is the proof. "A file appeared" is also true of a
                        // truncated upload, and a truncated photo is a lost photo.
                        if (item.sizeBytes == entry.sizeBytes) {
                            entryDao.markUploaded(
                                id = entry.id,
                                remoteItemId = item.id,
                                remoteSizeBytes = item.sizeBytes,
                                uploadedAt = System.currentTimeMillis()
                            )
                            uploaded++
                        } else {
                            entryDao.markFailed(
                                entry.id,
                                "size mismatch: sent ${entry.sizeBytes}, stored ${item.sizeBytes}"
                            )
                            failed++
                        }
                    }

                    is DataResult.Failure -> {
                        val stop = stopReasonFor(result.error)
                        if (stop != null) {
                            Logger.w(TAG, "uploadPending: stopping run — $stop")
                            return@withContext BackupRunResult(
                                uploaded = uploaded,
                                failed = failed,
                                remaining = entryDao.nextPending(1, MAX_ATTEMPTS).size,
                                stoppedBecause = stop
                            )
                        }
                        entryDao.markFailed(entry.id, result.error.toString())
                        failed++
                    }
                }
            }

            BackupRunResult(
                uploaded = uploaded,
                failed = failed,
                remaining = entryDao.nextPending(1, MAX_ATTEMPTS).size
            )
        }

    /**
     * Maps a device album onto its place in OneDrive.
     *
     * Mirrors the layout Samsung already created — `Samsung Gallery/DCIM/<album>` — so that after
     * Samsung's sync stops, new photos keep landing beside the ones already there instead of
     * starting a second parallel structure the user then has to reconcile.
     */
    private fun remotePathFor(album: String): String = "$REMOTE_ROOT/$album"

    private fun stopReasonFor(error: RemoteError): StopReason? = when (error) {
        RemoteError.NoToken -> StopReason.NO_TOKEN
        RemoteError.Unauthorized -> StopReason.UNAUTHORIZED
        RemoteError.InsufficientStorage -> StopReason.DRIVE_FULL
        RemoteError.Network -> StopReason.NETWORK
        // A single file failing (an odd 4xx, a missing local file) should not stop the others.
        is RemoteError.Http, is RemoteError.Unknown -> null
    }

    companion object {
        private const val TAG = "BackupEngine"

        const val REMOTE_ROOT = "Samsung Gallery/DCIM"

        /** Files per run. Small enough that a cancelled worker loses little work. */
        const val DEFAULT_BATCH = 25

        /** Give up on a file after this many failures rather than retrying it forever. */
        const val MAX_ATTEMPTS = 5
    }
}
