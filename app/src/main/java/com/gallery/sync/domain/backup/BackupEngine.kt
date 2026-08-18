package com.gallery.sync.domain.backup

import android.content.Context
import com.gallery.sync.data.local.dao.BackupEntryDao
import com.gallery.sync.data.local.entity.BackupEntryEntity
import com.gallery.sync.data.local.entity.BackupState
import com.gallery.sync.data.local.entity.backupKeyOf
import com.gallery.sync.data.local.media.LocalMediaItem
import com.gallery.sync.data.local.media.MediaAccess
import com.gallery.sync.data.local.media.MediaScanner
import com.gallery.sync.data.remote.onedrive.ContentUriUploadSource
import com.gallery.sync.di.IoDispatcher
import com.gallery.sync.domain.model.DataResult
import com.gallery.sync.domain.model.RemoteError
import com.gallery.sync.domain.model.RemoteMediaNode
import com.gallery.sync.domain.repository.OneDriveRepository
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
    /** Already present in OneDrive, so recorded as backed up without being sent again. */
    val skipped: Int = 0,
    /** Ledger rows forgotten because the file is no longer on the device. Not a failure. */
    val pruned: Int = 0,
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
    private val repository: OneDriveRepository,
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

        // Proxied files are skipped by MediaStore id, because proxying changed their size and so
        // their content key. Without this every proxy is seen as a new file and uploaded beside
        // the original it replaced — the single most important line in this method.
        val proxied = entryDao.proxiedMediaStoreIds().toSet()

        val items = scanner.scanAll().filterNot { it.mediaStoreId in proxied }
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

        pruneAlbumsNoLongerOnDevice(items.map { it.album }.distinct())

        Logger.i(TAG, "refreshLedger: ${entries.size} files seen")
        entries.size
    }

    /**
     * Forgets rows for albums the device no longer has.
     *
     * Guarded hard. An empty or partial scan must never reach the delete: a revoked permission or
     * an unmounted card would look identical to "the user deleted everything", and acting on that
     * wipes the record of what is already safely backed up. When in doubt this does nothing, which
     * costs only a stale row.
     */
    private suspend fun pruneAlbumsNoLongerOnDevice(albumsOnDevice: List<String>) {
        if (scanner.access() != MediaAccess.FULL) {
            Logger.d(TAG, "not pruning: media access is not full")
            return
        }
        if (albumsOnDevice.isEmpty()) {
            Logger.w(TAG, "not pruning: the scan returned no albums at all")
            return
        }

        val removed = entryDao.forgetAlbumsNotOnDevice(albumsOnDevice)
        if (removed > 0) {
            Logger.i(TAG, "forgot $removed rows for albums no longer on the device")
        }
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
                return@withContext BackupRunResult(
                    uploaded = 0,
                    failed = 0,
                    remaining = 0,
                    stoppedBecause = StopReason.NO_MEDIA_ACCESS
                )
            }

            val pending = entryDao.nextPending(limit = limit, maxAttempts = MAX_ATTEMPTS)
            var uploaded = 0
            var failed = 0
            var skipped = 0
            var pruned = 0

            // One remote listing per album, reused across every file in it. Asking per file would
            // cost a request each; asking once costs one and answers for all of them.
            val remoteByAlbum = mutableMapOf<String, Map<String, Long>>()

            for (entry in pending) {
                val alreadyThere = remoteByAlbum.getOrPut(entry.album) {
                    remoteIndexFor(entry.album)
                }

                // Same name and same size means the file is already backed up — by Samsung's own
                // sync while both run in parallel, or by this app before a reinstall lost the
                // ledger. Uploading anyway produces a renamed duplicate, which is what the user
                // saw before this check existed.
                if (alreadyThere[entry.displayName] == entry.sizeBytes) {
                    Logger.d(TAG, "already in OneDrive, not re-uploading: ${entry.displayName}")
                    entryDao.markUploaded(
                        id = entry.id,
                        remoteItemId = "",
                        remoteSizeBytes = entry.sizeBytes,
                        uploadedAt = System.currentTimeMillis()
                    )
                    skipped++
                    continue
                }

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
                        // The file is gone — deleted, moved, or on an unmounted card. Forget the
                        // row rather than failing it: retrying cannot bring the file back, and a
                        // kept row would exhaust its attempts and then sit as a permanent failure
                        // inflating the count for good.
                        if (result.error == RemoteError.LocalFileMissing) {
                            entryDao.forget(entry.id)
                            pruned++
                            continue
                        }

                        val stop = stopReasonFor(result.error)
                        if (stop != null) {
                            Logger.w(TAG, "uploadPending: stopping run — $stop")
                            return@withContext BackupRunResult(
                                uploaded = uploaded,
                                failed = failed,
                                remaining = entryDao.countPendingInSelectedAlbums(),
                                skipped = skipped,
                                pruned = pruned,
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
                // A real count. This previously reused nextPending with a limit of 1, so it could
                // only ever report 0 or 1 — "1 still to go" actually meant "at least one".
                remaining = entryDao.countPendingInSelectedAlbums(),
                skipped = skipped,
                pruned = pruned
            )
        }

    /**
     * Local files whose cloud copy is confirmed, so the phone's copy is redundant.
     *
     * Matched against a fresh scan rather than trusted from the ledger alone: a ledger row can
     * outlive the file it describes, and building a delete request from stale rows is how a backup
     * tool removes the wrong thing.
     */
    suspend fun redundantLocalCopies(): List<LocalMediaItem> = withContext(dispatcher) {
        if (scanner.access() == MediaAccess.NONE) return@withContext emptyList()

        val verified = entryDao.verifiedInCloud().map { it.id }.toSet()

        scanner.scanAll().filter { item ->
            val key = backupKeyOf(
                album = item.album,
                displayName = item.displayName,
                sizeBytes = item.sizeBytes,
                dateModifiedEpochSeconds = item.dateModifiedEpochSeconds
            )
            key in verified
        }.also { Logger.d(TAG, "redundantLocalCopies: ${it.size} files are safely in OneDrive") }
    }

    /**
     * What is already in an album's OneDrive folder, as name to size.
     *
     * Name **and** size together: a same-named file of a different size is genuinely different
     * content — an edited photo, or a different shot that happened to reuse a camera filename —
     * and skipping it would silently leave the newer version unbacked.
     *
     * A folder that does not exist yet, or a listing that fails, yields an empty map. Erring
     * towards uploading is right: a duplicate is untidy, a missing backup is a lost photo.
     */
    private suspend fun remoteIndexFor(album: String): Map<String, Long> =
        when (val result = repository.listFolderByPath(remotePathFor(album))) {
            is DataResult.Success ->
                result.value.nodes
                    .filterIsInstance<RemoteMediaNode.File>()
                    .associate { it.name to it.sizeBytes }

            is DataResult.Failure -> {
                Logger.w(TAG, "could not list $album remotely (${result.error}); will upload")
                emptyMap()
            }
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

        // These affect one file, not the run. A missing local file especially: a ledger row can
        // outlive the file it describes, and letting that halt everything means one deleted photo
        // silently stops the rest of a library being backed up.
        RemoteError.LocalFileMissing,
        is RemoteError.Http,
        is RemoteError.Unknown -> null
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
