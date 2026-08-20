package com.gallery.sync.domain.backup

import android.content.Context
import com.gallery.sync.data.local.dao.BackupEntryDao
import com.gallery.sync.data.local.entity.BackupEntryEntity
import com.gallery.sync.data.local.entity.BackupState
import com.gallery.sync.data.local.entity.backupKeyOf
import com.gallery.sync.data.local.media.LocalMediaItem
import com.gallery.sync.data.local.media.MediaAccess
import com.gallery.sync.data.local.media.MediaScanner
import com.gallery.sync.data.local.media.ProxyMarker
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

/**
 * Live position within a run.
 *
 * Emitted as each file starts and as its bytes go out, so a long run says what it is doing. A
 * three-minute upload with no feedback reads as a hang, and the biggest files are exactly the ones
 * that take longest.
 */
data class BackupProgress(
    val completed: Int,
    val total: Int,
    val currentFile: String,
    val currentBytesSent: Long,
    val currentBytesTotal: Long
)

data class BackupRunResult(
    val uploaded: Int,
    val failed: Int,
    val remaining: Int,
    /** Already present in OneDrive, so recorded as backed up without being sent again. */
    val skipped: Int = 0,
    /**
     * Left for the next run because their album could not be listed remotely.
     *
     * Distinct from [failed]: nothing is wrong with these files and no attempt was spent on
     * them. Surfaced so a run that could check nothing does not read as a run that found
     * nothing to do — which is how the old behaviour hid itself.
     */
    val deferred: Int = 0,
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
    private val proxyMarker: ProxyMarker,
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
     * Uploads up to [limit] outstanding files, and no more than roughly [maxBytes] of them.
     *
     * The byte bound matters more than the count. Twenty-five photos is about 100 MB; twenty-five
     * videos can be four gigabytes, and a run that long is stopped by WorkManager partway — losing
     * whatever file was in flight, because the upload session is not persisted across runs. Sizing
     * by bytes keeps a run to something it can finish.
     *
     * A single file larger than the cap is still attempted on its own. Refusing it would mean the
     * largest files never upload at all, which is worse than a long run.
     *
     * Stops the whole run on a failure that will repeat for every remaining file — no token, a
     * rejected token, a full drive, a dropped network. Continuing would waste the user's battery
     * and data to collect an identical error on each of a thousand photos.
     */
    suspend fun uploadPending(
        limit: Int = DEFAULT_BATCH,
        maxBytes: Long = DEFAULT_BATCH_BYTES,
        onProgress: (BackupProgress) -> Unit = {}
    ): BackupRunResult =
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
                .let { candidates -> withinByteBudget(candidates, maxBytes) }
            var uploaded = 0
            var failed = 0
            var skipped = 0
            var pruned = 0

            // One remote listing per album, reused across every file in it. Asking per file would
            // cost a request each; asking once costs one and answers for all of them.
            //
            // `null` means the listing failed, which is **not** the same as the album being empty.
            // Until 19 Aug 2026 a failure returned an empty map and every file in the album was
            // uploaded — so one bad moment on the network re-uploaded whole albums as renamed
            // duplicates. Observed: 81 of 87 albums failed to list in a single run when
            // connectivity dropped. Failing to ask is not evidence of absence.
            val remoteByAlbum = mutableMapOf<String, Map<String, Long>?>()
            var deferred = 0

            for (entry in pending) {
                // `containsKey` rather than `getOrPut`: getOrPut re-runs its lambda whenever the
                // stored value is null, so a failed album would be listed again for every one of
                // its pending files — hundreds of requests in the exact network conditions that
                // made the first one fail. A remembered failure has to stay remembered.
                val alreadyThere = if (remoteByAlbum.containsKey(entry.album)) {
                    remoteByAlbum[entry.album]
                } else {
                    remoteIndexFor(entry.album).also { remoteByAlbum[entry.album] = it }
                }

                if (alreadyThere == null) {
                    // Leave the row PENDING and its attemptCount alone. Marking it failed would
                    // burn an attempt on a file that is fine, and a few network-troubled runs would
                    // then exhaust MAX_ATTEMPTS and give up on it permanently.
                    deferred++
                    continue
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

                // A proxy is smaller than the original it came from, so the size test above cannot
                // see that it is already backed up. Ask the file instead: if it carries the proxy
                // marker and OneDrive holds a larger file of the same name, that larger file is
                // the original. Uploading would file a 2048px copy beside it.
                val remoteSize = alreadyThere[entry.displayName]
                if (
                    LedgerRecovery.isBackedUpProxy(
                        localSizeBytes = entry.sizeBytes,
                        remoteSizeBytes = remoteSize,
                        carriesProxyMarker = proxyMarker.isProxy(
                            android.net.Uri.parse(entry.contentUri)
                        )
                    )
                ) {
                    Logger.i(TAG, "recovered proxy record for ${entry.displayName}")
                    entryDao.markRecoveredAsProxied(
                        id = entry.id,
                        originalSizeBytes = remoteSize!!,
                        proxySizeBytes = entry.sizeBytes,
                        remoteItemId = "",
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

                // Announce the file before sending a byte, so a large video shows its name
                // immediately rather than after the first chunk lands.
                onProgress(
                    BackupProgress(
                        completed = uploaded + skipped + pruned,
                        total = pending.size,
                        currentFile = entry.displayName,
                        currentBytesSent = 0,
                        currentBytesTotal = entry.sizeBytes
                    )
                )

                val result = uploadRepository.upload(
                    source = source,
                    remoteFolderPath = remotePathFor(entry.album)
                ) { sent, total ->
                    onProgress(
                        BackupProgress(
                            completed = uploaded + skipped + pruned,
                            total = pending.size,
                            currentFile = entry.displayName,
                            currentBytesSent = sent,
                            currentBytesTotal = total
                        )
                    )
                }

                when (result) {
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
                                deferred = deferred,
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
                deferred = deferred,
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
     * Every file OneDrive already holds for this album, by name and size.
     *
     * Name **and** size together: a same-named file of a different size is genuinely different
     * content — an edited photo, or a different shot that happened to reuse a camera filename —
     * and skipping it would silently leave the newer version unbacked.
     *
     * `null` means the album could not be listed at all, which the caller must not read as an
     * empty folder. A folder that genuinely does not exist yet still yields an empty map, and
     * uploading into it is right.
     *
     * **Walks every page.** Graph returns 100 items at a time, and reading only the first page was
     * the state of this function until 19 Aug 2026 — which meant that on any album larger than a
     * page, every file past the hundredth looked absent and was uploaded again. Measured on the
     * Fold 4 against a real library, running this function: 8,482 local files across 87 albums, of
     * which 8,276 were already in OneDrive — but only 2,753 were visible one page at a time.
     * **5,523 files would have been re-uploaded as renamed duplicates** — the exact failure the
     * skip check exists to prevent, and the one the user had already seen once before it was
     * written.
     *
     * The cost is one request per page per album, once per run: the caller memoises this by album,
     * so it is not paid per file.
     *
     * `internal` rather than `private` so the debug coverage probe can verify *this* function
     * rather than a copy of it. The bug it fixes was invisible to unit tests and only showed up
     * against a real drive, so the check that catches a regression has to run the real code.
     *
     * A failure mid-walk returns what was gathered so far rather than nothing. A partial index can
     * only cause a re-upload, while an empty one guarantees a whole album of them.
     */
    internal suspend fun remoteIndexFor(album: String): Map<String, Long>? {
        val path = remotePathFor(album)
        val index = mutableMapOf<String, Long>()

        var page = when (val result = repository.listFolderByPath(path)) {
            is DataResult.Success -> result.value
            is DataResult.Failure -> {
                Logger.w(TAG, "could not list $album remotely (${result.error}); deferring")
                return null
            }
        }
        index += page.nodes.filterIsInstance<RemoteMediaNode.File>().associate { it.name to it.sizeBytes }

        var pages = 1
        while (page.nextPageToken != null && pages < MAX_REMOTE_PAGES) {
            page = when (val result = repository.listNextPage(page.nextPageToken!!)) {
                is DataResult.Success -> result.value
                is DataResult.Failure -> {
                    Logger.w(TAG, "page ${pages + 1} of $album failed (${result.error}); using $pages")
                    return index
                }
            }
            index += page.nodes.filterIsInstance<RemoteMediaNode.File>().associate { it.name to it.sizeBytes }
            pages++
        }

        if (pages > 1) Logger.d(TAG, "$album: ${index.size} remote files across $pages pages")
        return index
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

        /**
         * Ceiling on the page walk in [remoteIndexFor], so a paging loop that never terminates
         * cannot hang a backup run. At 100 items a page this covers 20,000 files in one album,
         * comfortably past the 8,482 in the whole library it was measured against.
         */
        const val MAX_REMOTE_PAGES = 200

        /** Files per run. Small enough that a cancelled worker loses little work. */
        const val DEFAULT_BATCH = 25

        /**
         * Roughly how much one run should move.
         *
         * Chosen against the window a background run actually gets, not against a connection
         * speed: at 1 MB/s this is about nine minutes, which fits inside WorkManager's limit, and
         * on a fast connection it is a couple of minutes. Lower would mean more runs; higher
         * would mean runs that get killed, and a killed run throws away the file in flight.
         */
        const val DEFAULT_BATCH_BYTES = 512L * 1024 * 1024

        /**
         * Trims a batch to a byte budget, keeping order.
         *
         * Always keeps the first file however large it is: a file bigger than the whole budget
         * would otherwise be skipped on every run and never upload at all. Everything after it
         * has to fit.
         *
         * Pure, and in the companion so it can be tested without building an engine. The case that
         * matters is a lone oversized file being dropped, which stays invisible until someone owns
         * a big video.
         */
        fun withinByteBudget(
            candidates: List<BackupEntryEntity>,
            maxBytes: Long
        ): List<BackupEntryEntity> {
            if (candidates.isEmpty()) return candidates

            val kept = mutableListOf(candidates.first())
            var total = candidates.first().sizeBytes

            for (entry in candidates.drop(1)) {
                if (total + entry.sizeBytes > maxBytes) break
                kept += entry
                total += entry.sizeBytes
            }

            if (kept.size < candidates.size) {
                Logger.d(TAG, "byte budget trimmed ${candidates.size} candidates to ${kept.size}")
            }
            return kept
        }

        /** Give up on a file after this many failures rather than retrying it forever. */
        const val MAX_ATTEMPTS = 5
    }
}
