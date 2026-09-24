package com.gallery.sync.domain.backup

import android.content.Context
import com.gallery.sync.data.local.dao.AlbumPreferenceDao
import com.gallery.sync.data.local.dao.FolderPreferenceDao
import com.gallery.sync.data.local.dao.BackupEntryDao
import com.gallery.sync.data.local.dao.UnsentDepartureDao
import com.gallery.sync.data.local.entity.AlbumMode
import com.gallery.sync.data.local.entity.AlbumPreferenceEntity
import com.gallery.sync.data.local.entity.FolderPreferenceEntity
import com.gallery.sync.data.local.entity.BackupEntryEntity
import com.gallery.sync.data.local.entity.BackupState
import com.gallery.sync.data.local.entity.CloudCopyDecision
import com.gallery.sync.data.local.entity.UnsentDepartureEntity
import com.gallery.sync.data.local.entity.backupKeyOf
import com.gallery.sync.data.local.media.LocalMediaItem
import com.gallery.sync.data.local.media.MediaAccess
import com.gallery.sync.data.local.media.MediaScanner
import com.gallery.sync.data.local.media.MediaScanRules
import com.gallery.sync.data.local.media.ProxyMarker
import com.gallery.sync.data.local.media.RestoredAlbum
import com.gallery.sync.data.local.settings.BackupSettings
import com.gallery.sync.data.remote.onedrive.ContentUriUploadSource
import com.gallery.sync.data.remote.onedrive.ResumableSession
import com.gallery.sync.data.remote.onedrive.UploadSource
import com.gallery.sync.di.IoDispatcher
import com.gallery.sync.domain.model.DataResult
import com.gallery.sync.domain.model.RemoteError
import com.gallery.sync.domain.model.RemoteMediaNode
import com.gallery.sync.domain.billing.MultiCloudEntitlement
import com.gallery.sync.domain.repository.CloudUploaders
import com.gallery.sync.domain.repository.OneDriveRepository
import com.gallery.sync.domain.repository.OneDriveUploadRepository
import com.gallery.sync.util.Logger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
    private val albumDao: AlbumPreferenceDao,
    private val folderDao: FolderPreferenceDao,
    private val unsentDao: UnsentDepartureDao,
    private val settings: BackupSettings,
    private val repository: OneDriveRepository,
    private val uploadRepository: OneDriveUploadRepository,
    private val uploaders: CloudUploaders,
    private val entitlement: MultiCloudEntitlement,
    private val proxyMarker: ProxyMarker,
    private val albumIdentity: AlbumIdentityReconciler,
    @ApplicationContext private val context: Context,
    @param:IoDispatcher private val dispatcher: CoroutineDispatcher
) {

    /**
     * Releases any upload session left in flight, so a paused file starts clean.
     *
     * Called when the user pauses or stops, and again before a run begins if the interruption is
     * older than [STALE_SESSION_AFTER_MILLIS].
     */
    suspend fun discardInFlightUploads() = withContext(dispatcher) {
        entryDao.entriesWithUploadSession().forEach { entry ->
            entry.uploadSessionUrl?.let { uploadRepository.cancelUploadSession(it) }
            entryDao.forgetUploadSession(entry.id)
            Logger.i(TAG, "discarded in-flight upload of ${entry.displayName}")
        }
    }

    /**
     * Opens, carries or closes the denominator for run progress.
     *
     * Called at **both ends** of every worker invocation, and the second call is the one that keeps
     * it honest. Raising-only at the start left a finished run's baseline in place: the queue
     * drained, nothing cleared it, and the next run — smaller than the last — neither raised it nor
     * reset it. A 62-file album opened at **79%** against an 884 MB denominator from an hour
     * earlier. Fold 4, 28 Aug 2026.
     *
     * At the start of a chain it sets the baseline to what is outstanding; later invocations in the
     * same chain leave it alone, which is what makes progress span the whole run rather than
     * resetting each batch.
     *
     * Raised, never lowered, while a run is live: files arriving midway make the reported progress
     * slow down rather than leap backwards, which is the honest rendering of "there is now more to
     * do than when we started".
     *
     * Cleared when nothing is outstanding, so the next run opens at zero rather than inheriting a
     * finished run's denominator.
     */
    suspend fun updateRunBaseline() = withContext(dispatcher) {
        val outstanding = entryDao.pendingBytesInSelectedAlbums()
        val current = settings.current().runBaselineBytes

        when {
            outstanding <= 0L -> if (current != 0L) settings.setRunBaselineBytes(0L)
            outstanding > current -> settings.setRunBaselineBytes(outstanding)
        }
    }

    /**
     * Drops a held session that is too old to trust, leaving a fresh one to be opened.
     *
     * The compromise Ian settled on, 28 Aug 2026, after two reversals in the other direction.
     * Suspending an upload is nearly free — resume continues from the offset Graph reports
     * accepted, verified three times at three offsets, each on an exact 5 MB chunk boundary. But
     * resuming reads the **current** local file at a stored offset, so a file changed while paused
     * could be spliced from two versions into something Graph accepts and marks complete.
     *
     * Holding the session for a short window keeps the common pause cheap — take a call, step away
     * — while a long one starts clean. Ten minutes sits inside Graph's roughly fifteen-minute
     * session window, so the shortcut is only ever kept while it is still usable; past that the
     * session would be dead anyway and the restart merely becomes deliberate rather than emergent.
     *
     * **Decided at the moment it matters rather than by a timer.** A scheduled job would have to
     * survive process death, reboot and Doze to fire correctly, which is a great deal of machinery
     * for a question answerable when the next run starts.
     *
     * The clock stops on either button: Resume and Stop both clear the stamp, because both are the
     * user attending to the run. Only walking away leaves it running.
     */
    suspend fun discardStaleUploadSessions() = withContext(dispatcher) {
        val interruptedAt = settings.current().uploadInterruptedAtEpochMillis
        if (interruptedAt <= 0L) return@withContext

        val age = System.currentTimeMillis() - interruptedAt
        if (age >= STALE_SESSION_AFTER_MILLIS) {
            Logger.i(TAG, "held upload session is ${age / 60_000}m old, discarding")
            discardInFlightUploads()
        }
    }

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

        // Before anything is inserted or seeded: a row written now under a spelling the merge is
        // about to retire would split the album again straight away. TASK-023.
        albumIdentity.reconcile()

        // Proxied files are skipped by MediaStore id, because proxying changed their size and so
        // their content key. Without this every proxy is seen as a new file and uploaded beside
        // the original it replaced — the single most important line in this method.
        val proxied = entryDao.proxiedMediaStoreIds().toSet()

        // Where uploads go — per top-level folder (DCIM, Pictures, Movies...), not one app-wide
        // setting; see TASK-026, 24 Sept 2026. Read once here rather than per item: a row's
        // `location` is fixed at creation and never re-read afterwards (BackupEntryEntity.location),
        // so this only ever matters for rows made in this pass. A later change to a folder's
        // destination is picked up by the next scan, not retroactively.
        val folderLocations = folderDao.all().orEmpty().associate { it.folderName to it.backupLocation }
        // The app-wide setting is still real — it's the fallback for a folder with no row of its
        // own: never explicitly chosen, or a `RELATIVE_PATH`-less item on API < 29.
        val defaultLocation = settings.current().backupLocation

        val items = scanner.scanAll().filterNot { it.mediaStoreId in proxied }
        val entries = items.map { item ->
            val folder = MediaScanRules.topLevelFolderOf(item.relativePath)
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
                state = BackupState.PENDING,
                location = FolderDestination.resolve(folder, folderLocations, defaultLocation)
            )
        }

        // IGNORE on conflict, so a rescan never resets an uploaded row back to pending.
        entryDao.insertIfNew(entries)

        // Give every discovered folder a row too, same reasoning and same IGNORE-only-if-absent
        // shape as seeding album_preferences below — a folder already chosen is never touched, and
        // a headless run needs the row to exist so the next one can find it.
        val foldersOnDevice = items.mapNotNull { MediaScanRules.topLevelFolderOf(it.relativePath) }.distinct()
        folderDao.insertIfNew(foldersOnDevice.map { FolderPreferenceEntity(it, defaultLocation) })

        val albumsOnDevice = items.map { it.album }.distinct()

        // Give every album the scan found a row. IGNORE means a choice already made is never
        // touched, so this is safe to run on every scan.
        //
        // This lives here, not in the UI, because the upload gate is opt-in and headless runs
        // happen. Seeding from a ViewModel meant a content-triggered run before the user ever
        // opened the album screen saw an empty preference table — which under the old opt-out gate
        // made the whole library eligible.
        //
        // The mode is the user's configured default for new albums, not [AlbumMode.DEFAULT]:
        // hardcoding it here would silently disable that setting, since the row would already exist
        // by the time the screen looked. `canBeDefault` keeps Archive out of it, so seeding can
        // never arm a mode that removes files.
        val defaultMode = settings.current().defaultAlbumMode
        //
        // Except that the Camera album never starts at Sync (Ian, 20 Sept 2026): a photo taken this
        // minute must not be shrunk this minute. See [CameraAlbum].
        albumDao.insertIfNew(albumsOnDevice.map { AlbumPreferenceEntity(it, CameraAlbum.seeded(it, defaultMode)) })

        // Unscoped, deliberately, and used for two things. Pruning asks "does this album still
        // exist on the phone?", and marking asks "is this file still here?" — both are questions
        // about the device, not about what the user currently wants watched. Driving either from a
        // scoped scan would treat a narrowed folder as a deletion.
        val everything = scanner.scanEverything()

        markWhatIsNoLongerOnTheDevice(everything)
        pruneAlbumsNoLongerOnDevice(everything.map { it.album }.distinct())

        Logger.i(TAG, "refreshLedger: ${entries.size} files seen")
        entries.size
    }

    /**
     * Records which backed-up files have left the phone, and which have come back.
     *
     * This is what the retrieval list is built from. It covers every way a file can go — Archive
     * removing it, the user deleting it in their gallery, or a photo being proxied, whose
     * full-quality original genuinely is no longer here.
     *
     * Guarded like the prune: an empty scan is never evidence that everything was deleted.
     */
    private suspend fun markWhatIsNoLongerOnTheDevice(everything: List<LocalMediaItem>) {
        if (scanner.access() != MediaAccess.FULL) {
            Logger.d(TAG, "not marking missing files: media access is not full")
            return
        }
        if (everything.isEmpty()) {
            Logger.w(TAG, "not marking missing files: the scan returned nothing at all")
            return
        }

        val present = everything.mapTo(HashSet()) { item ->
            backupKeyOf(
                album = item.album,
                displayName = item.displayName,
                sizeBytes = item.sizeBytes,
                dateModifiedEpochSeconds = item.dateModifiedEpochSeconds
            )
        }

        // A second index, by name and size rather than by content key. A file fetched back from
        // OneDrive lands in the Restored folder with a fresh timestamp, so its content key can never
        // match the row that describes where it used to live — and without this the ledger would go
        // on offering to fetch a file the user is already looking at.
        //
        // Name and size is the same bar `verifiedInCloud` uses to call a copy safe, so it is a fair
        // test of "this content is on the phone somewhere".
        //
        // Built through RestoredAlbum.contentSignature, which strips the `_restored` a fetch adds.
        // Comparing the raw name would mean a file the user has just fetched back never clears its
        // flag, and so stays on course to have its cloud copy offered for deletion.
        val presentContent = everything.mapTo(HashSet()) {
            RestoredAlbum.contentSignature(it.displayName, it.sizeBytes)
        }

        // Diffed here rather than in SQL. A `NOT IN` over six thousand keys binds one variable per
        // file and exceeds SQLite's parameter limit, and it cannot be chunked because a file in the
        // second chunk would be marked missing by the first.
        // Every MediaStore id the device still has. A proxied file keeps its id through the
        // rewrite while its size and mtime both change, so this is the only question that stays
        // answerable about it — see UploadedKey.mediaStoreId.
        val presentIds = everything.mapTo(HashSet()) { it.mediaStoreId }

        val known = entryDao.uploadedKeys()
        val back = known
            .filter {
                // A proxied row is judged on its id alone. Judging it on content would compare a
                // remembered original size against the proxy on disk, never match, and conclude the
                // user had deleted a photo that is sitting in their gallery — which on 26 Aug 2026
                // it did, to six of them.
                if (it.isProxied) it.mediaStoreId in presentIds
                else it.contentSignature in presentContent
            }
            .map { it.id }
        // Back wins over gone: a restored file is absent by key and present by content, and the
        // second reading is the one the user would recognise.
        val backIds = back.toHashSet()
        val gone = known
            .filterNot { it.id in backIds }
            .filterNot { it.id in present }
            .map { it.id }

        val now = System.currentTimeMillis()
        // Cleared first, so a file restored moments ago is never marked missing on the way through.
        var returned = 0
        back.chunked(SQL_BATCH).forEach { returned += entryDao.clearLocalMissing(it) }
        var marked = 0
        gone.chunked(SQL_BATCH).forEach { marked += entryDao.markLocalMissing(it, now) }

        if (marked > 0 || returned > 0) {
            Logger.i(TAG, "$marked files no longer on the device, $returned back")
        }

        forgetDeparturesThatCameBack(present, presentContent, presentIds)
        forgetPendingFilesThatAreGone(present, presentContent, presentIds)
    }

    /**
     * Drops rows for files that were never uploaded and are no longer on the device.
     *
     * The marking above deliberately looks only at uploaded rows, because the flag it sets drives
     * the cloud-deletion question and that question only exists for a file with a cloud copy. The
     * consequence was that a **pending** row whose file had gone was reconciled by nothing at all
     * and stayed in the ledger for good — fourteen of them on the Fold 4, 28 Aug 2026, for an
     * album whose folder was empty.
     *
     * Harmless while that album is `OFF`, and not harmless afterwards: give it a mode and the
     * engine queues uploads for files it cannot open, burning a batch slot on each.
     *
     * Deleted rather than flagged, because there is nothing to decide. Nothing was sent, so nothing
     * in OneDrive depends on the row, and if the file comes back the scan seeds it again. That is
     * also why this is safe where [pruneAlbumsNoLongerOnDevice] has to be so careful: losing a
     * pending row costs a re-scan, losing an uploaded one costs the record of what is safe.
     *
     * Guarded by the same conditions as the marking above — it runs only on a full-access,
     * non-empty scan, so a revoked permission cannot read as "every file was deleted".
     */
    private suspend fun forgetPendingFilesThatAreGone(
        present: Set<String>,
        presentContent: Set<String>,
        presentIds: Set<Long>
    ) {
        val gone = entryDao.pendingKeys()
            .filterNot { it.id in present }
            .filterNot { it.contentSignature in presentContent }
            .filterNot { it.mediaStoreId in presentIds }
            .map { it.id }

        if (gone.isEmpty()) return

        // Written down before the rows are forgotten, so forgetting one no longer means forgetting the
        // file. Ian, 19 Sept 2026: the window that opens with the app covers ALL deleted files, and a
        // file this app never sent is one of them. See [UnsentDepartureEntity].
        recordDepartures(gone)

        var forgotten = 0
        gone.chunked(SQL_BATCH).forEach { forgotten += entryDao.forgetPending(it) }
        Logger.i(TAG, "forgot $forgotten pending rows whose files are no longer on the device")
    }

    /** Keeps what is needed to ask about files that left the phone unsent. */
    private suspend fun recordDepartures(ids: List<String>) {
        val now = System.currentTimeMillis()
        ids.chunked(SQL_BATCH).forEach { chunk ->
            unsentDao.insertIfNew(entryDao.entriesByIds(chunk).map { departureOf(it, now) })
        }
    }

    private fun departureOf(entry: BackupEntryEntity, now: Long) = UnsentDepartureEntity(
        id = entry.id,
        mediaStoreId = entry.mediaStoreId,
        contentUri = entry.contentUri,
        displayName = entry.displayName,
        album = entry.album,
        sizeBytes = entry.sizeBytes,
        dateModifiedEpochSeconds = entry.dateModifiedEpochSeconds,
        mimeType = entry.mimeType,
        isVideo = entry.isVideo,
        goneSinceEpochMillis = now
    )

    /**
     * Drops the record of a departure whose file is on the phone again, by the same three readings the
     * ledger uses to say a file is here: its key, its content, its MediaStore id. A photo restored from
     * the trash is no longer something to ask about. Judged before new departures are recorded, so a
     * file is never both.
     */
    private suspend fun forgetDeparturesThatCameBack(
        present: Set<String>,
        presentContent: Set<String>,
        presentIds: Set<Long>
    ) {
        val back = unsentDao.all().filter {
            it.id in present ||
                RestoredAlbum.contentSignature(it.displayName, it.sizeBytes) in presentContent ||
                it.mediaStoreId in presentIds
        }.map { it.id }
        if (back.isEmpty()) return
        back.chunked(SQL_BATCH).forEach { unsentDao.forget(it) }
        Logger.i(TAG, "${back.size} departed files are back on the phone")
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

        val kept = entryDao.countRetrievableOutsideDevice(albumsOnDevice)
        val removed = entryDao.forgetAlbumsNotOnDevice(albumsOnDevice)
        if (kept > 0) {
            Logger.i(TAG, "kept $kept rows for files still in OneDrive but not on the device")
        }
        if (removed > 0) {
            Logger.i(TAG, "forgot $removed rows for albums no longer on the device")
        }
    }

    /**
     * Files still waiting in albums the user selected.
     *
     * Exists so a run that never reaches the engine can still tell whether there is anything left
     * to do. The first-backup window returns early, and until 26 Aug 2026 the only code that lifted
     * that window sat downstream of the return — so while the gate was up nothing was capable of
     * noticing the gate was no longer needed. See FIX-001.
     */
    suspend fun outstandingCount(): Int = withContext(dispatcher) {
        entryDao.countPendingInSelectedAlbums(MAX_ATTEMPTS)
    }

    suspend fun outstandingCountAll(): Int = withContext(dispatcher) {
        entryDao.countPendingAll(MAX_ATTEMPTS)
    }

    /** Files actually sent to OneDrive since [sinceMillis]. See `BackupEntryDao.countUploadedSince`. */
    suspend fun uploadedSince(sinceMillis: Long): Int = withContext(dispatcher) {
        entryDao.countUploadedSince(sinceMillis)
    }

    /**
     * Compares every UPLOADED ledger entry against what OneDrive actually holds, and requeues
     * anything that is missing.
     *
     * This is the setup wizard's cloud check: it trusts the drive, not the ledger, because the
     * ledger records what was once sent and the drive records what is there now. A folder deleted
     * from OneDrive by hand leaves ledger rows insisting the files are safe — this is the only
     * path that corrects them.
     *
     * Returns the number of entries requeued.
     */
    suspend fun reconcileAndRequeue(): Int = withContext(dispatcher) {
        val uploadedEntries = entryDao.uploadedEntries()
        if (uploadedEntries.isEmpty()) return@withContext 0

        val albumGroups = uploadedEntries.groupBy { it.album }
        val toRequeue = mutableListOf<Long>()

        for ((album, entries) in albumGroups) {
            val remoteIndex = remoteIndexFor(album) ?: continue

            for (entry in entries) {
                val expected = if (entry.isProxied && entry.remoteSizeBytes != null) {
                    entry.remoteSizeBytes
                } else {
                    entry.sizeBytes
                }
                val ref = remoteIndex[entry.displayName]
                if (ref == null || ref.sizeBytes != expected) {
                    toRequeue += entry.mediaStoreId
                }
            }
        }

        if (toRequeue.isNotEmpty()) {
            toRequeue.chunked(SQL_BATCH).forEach { entryDao.requeueForUpload(it) }
            Logger.i(TAG, "reconcileAndRequeue: ${toRequeue.size} files requeued for upload")
        }
        toRequeue.size
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
        allAlbums: Boolean = false,
        onProgress: (BackupProgress) -> Unit = {}
    ): BackupRunResult = uploadMutex.withLock {
        uploadPendingWhileHolding(limit, maxBytes, allAlbums, onProgress)
    }

    /**
     * One upload run at a time, however many workers ask.
     *
     * **Found 20 Sept 2026 on the Moto G, and it duplicated files in OneDrive.** Pressing *Resume* after
     * *Pause* started the automatic chain and the manual chain within 15 ms of each other. Both read the
     * same pending rows and uploaded every one of them side by side, so 24 photos became 30 files in
     * OneDrive, six of them renamed copies (` 1.jpg`) that nothing else would ever remove. Nothing in
     * the ledger could see it: the second copy of each upload simply succeeded.
     *
     * A run that has to wait here starts afterwards and reads the ledger as the first left it, so what
     * the first uploaded is no longer pending and there is nothing for the second to do twice. Held by
     * the engine, which is a singleton, and so shared by every worker in the process.
     */
    private val uploadMutex = Mutex()

    private suspend fun uploadPendingWhileHolding(
        limit: Int,
        maxBytes: Long,
        allAlbums: Boolean,
        onProgress: (BackupProgress) -> Unit
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

            val pending = if (allAlbums) {
                entryDao.nextPendingAll(limit = limit, maxAttempts = MAX_ATTEMPTS)
            } else {
                // The upload gate reads modes. See AlbumIdentityReconciler for why that waits on it.
                albumIdentity.reconcile()
                entryDao.nextPending(limit = limit, maxAttempts = MAX_ATTEMPTS)
            }.let { candidates -> withinByteBudget(candidates, maxBytes) }

            // Split by destination — see TASK-026. The two loops below are independent on purpose:
            // Ian, 23 Sept 2026, "a failed Google run (or iDrive or OneDrive) should never stop
            // another backup." Neither loop's own stop condition may prevent the other from running
            // in this same pass, and only OneDrive's may ever fail the whole worker run — see
            // stoppedBecause on the BackupRunResult this function returns, and BackupWorker's mapping
            // of it. `pending.size` is still what both loops report progress against, so a run
            // spanning both providers shows one continuous count rather than resetting partway.
            val (oneDrivePending, otherPending) = pending.partition {
                it.location == BackupLocation.ONEDRIVE
            }

            var uploaded = 0
            var failed = 0
            var skipped = 0
            var pruned = 0
            var deferred = 0

            // One remote listing per album, reused across every file in it. Asking per file would
            // cost a request each; asking once costs one and answers for all of them.
            //
            // `null` means the listing failed, which is **not** the same as the album being empty.
            // Until 19 Aug 2026 a failure returned an empty map and every file in the album was
            // uploaded — so one bad moment on the network re-uploaded whole albums as renamed
            // duplicates. Observed: 81 of 87 albums failed to list in a single run when
            // connectivity dropped. Failing to ask is not evidence of absence.
            val remoteByAlbum = mutableMapOf<String, Map<String, RemoteFileRef>?>()

            // Only OneDrive's own stop reason may ever fail the whole worker run (see below and
            // BackupWorker). Kept local to this loop rather than returned early, so a OneDrive stop
            // no longer skips the Google Photos loop that follows it.
            var oneDriveStop: StopReason? = null

            for (entry in oneDrivePending) {
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
                // `match.sizeBytes` null means the listing did not report one. That is not a
                // mismatch and must not be read as one: falling through would upload a second copy
                // beside a file that may well already be there, which is the renamed-duplicate
                // failure this check exists to prevent. Defer instead and ask again next run.
                val match = alreadyThere[entry.displayName]
                if (match != null && match.sizeBytes == null) {
                    Logger.w(
                        TAG,
                        "OneDrive reported no size for ${entry.displayName}; deferring rather " +
                            "than risking a duplicate"
                    )
                    deferred++
                    continue
                }
                if (match?.sizeBytes == entry.sizeBytes) {
                    Logger.d(TAG, "already in OneDrive, not re-uploading: ${entry.displayName}")
                    entryDao.markUploaded(
                        id = entry.id,
                        // The listing's item id, not an empty string. Recording "" here said the
                        // file was safe while making it impossible to fetch back — and this is the
                        // path most of a real library takes, so it would have left retrieval able
                        // to offer almost nothing.
                        remoteItemId = match.id,
                        remoteSizeBytes = entry.sizeBytes,
                        // When OneDrive got it, not when we noticed. Stamping *now* here made every
                        // file already in OneDrive read as uploaded by this run, so Gate 2 #3 —
                        // "optimise only what this run backed up", which is `uploadedAt >= cutoff` —
                        // optimised the whole library. Moto G, 15 Sept 2026. `0` when Graph gave no
                        // date: an unknown arrival is treated as old, so #3 leaves it alone.
                        uploadedAt = match.createdAtEpochMillis
                    )
                    skipped++
                    continue
                }

                // A proxy is smaller than the original it came from, so the size test above cannot
                // see that it is already backed up. Ask the file instead: if it carries the proxy
                // marker and OneDrive holds a larger file of the same name, that larger file is
                // the original. Uploading would file a 2048px copy beside it.
                val remoteMatch = alreadyThere[entry.displayName]
                val remoteSize = remoteMatch?.sizeBytes
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
                        // The listing's id, as the exact-size skip above records. This said "" and
                        // it is the path a phone that was already optimised takes after a reinstall,
                        // so every such row claimed a cloud copy it could not fetch, and was
                        // forgotten outright when its album was archived: the row's protection from
                        // the prune is a real item id. Moto G, 18 Sept 2026, Test 4 and Test 5.
                        remoteItemId = remoteMatch?.id.orEmpty(),
                        // The original's arrival in OneDrive, for the same reason as the skip above.
                        uploadedAt = remoteMatch?.createdAtEpochMillis ?: 0L
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
                    remoteFolderPath = remotePathFor(entry.album),
                    onProgress = { sent, total ->
                        onProgress(
                            BackupProgress(
                                completed = uploaded + skipped + pruned,
                                total = pending.size,
                                currentFile = entry.displayName,
                                currentBytesSent = sent,
                                currentBytesTotal = total
                            )
                        )
                    },
                    // Anything this row was part-way through last time. Expiry and whether the
                    // server still honours it are decided further down; here it is just handed over.
                    //
                    // Withheld when the file on disk no longer matches the row it was opened for.
                    // Resuming reads the *current* bytes at a stored offset, so a file rewritten
                    // while a session was held would be spliced from two versions into something
                    // Graph accepts and marks complete. The ten-minute rule narrows that window;
                    // this closes it, for the price of one size check.
                    existingSession = entry.uploadSessionUrl
                        ?.takeIf { source.sizeBytes == entry.sizeBytes }
                        ?.let { ResumableSession(it, entry.uploadSessionExpiresAtEpochMillis) },
                    // Stored before the first byte leaves. The run that dies is the one whose
                    // session matters, so waiting for success would record nothing useful.
                    onSessionCreated = { session ->
                        entryDao.rememberUploadSession(
                            id = entry.id,
                            url = session.uploadUrl,
                            expiresAt = session.expiresAtEpochMillis
                        )
                    }
                )

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
                            entryDao.forgetUploadSession(entry.id)
                            uploaded++
                        } else {
                            entryDao.markFailed(
                                entry.id,
                                "size mismatch: sent ${entry.sizeBytes}, stored ${item.sizeBytes}"
                            )
                            // The session ran to completion and produced the wrong bytes, so it is
                            // spent. Resuming it would re-confirm the same bad file; only a fresh
                            // session can put this right.
                            entryDao.forgetUploadSession(entry.id)
                            failed++
                        }
                    }

                    is DataResult.Failure -> {
                        // The file is gone — deleted, moved, or on an unmounted card. Forget the
                        // row rather than failing it: retrying cannot bring the file back, and a
                        // kept row would exhaust its attempts and then sit as a permanent failure
                        // inflating the count for good.
                        if (result.error == RemoteError.LocalFileMissing) {
                            recordDepartures(listOf(entry.id))
                            entryDao.forget(entry.id)
                            pruned++
                            continue
                        }

                        // Read as zero bytes. Deferred rather than failed: no attempt is spent, the
                        // row is kept, and the next run tries again — which is right because the
                        // cause is nearly always a file caught mid-write or mid-proxy. What must
                        // not happen is the upload proceeding; see RemoteError.EmptyLocalFile.
                        if (result.error == RemoteError.EmptyLocalFile) {
                            deferred++
                            continue
                        }

                        val stop = stopReasonFor(result.error)
                        if (stop != null) {
                            Logger.w(TAG, "uploadPending: stopping run — $stop")
                            oneDriveStop = stop
                            break
                        }
                        entryDao.markFailed(entry.id, result.error.toString())
                        failed++
                    }
                }
            }

            // ---------- Every other cloud ----------
            //
            // Google Drive, Dropbox, pCloud, IDrive e2, Backblaze B2 and Google Photos, one loop per
            // provider, each independent of OneDrive above and of each other — Ian, 23 Sept 2026: a
            // failed run on one provider must never stop another backup. A provider's own stop (an
            // expired token, its own quota) ends *its* loop only and is never written into the
            // BackupRunResult below, so it can neither fail the worker run nor be reported as though
            // OneDrive were the reason. Its rows simply stay PENDING/FAILED for the next run.
            //
            // No remote-index precheck the way the OneDrive loop has: that exists so a lost ledger
            // does not produce a renamed duplicate, and an equivalent for these providers is not built
            // yet (TASK-026). A lost ledger risks a duplicate upload for them, not data loss.
            //
            // The entitlement is checked once, here, at the boundary that actually spends the user's
            // upload rather than trusted from a stored setting — a refund or a lapsed trial can
            // outlive the choice it depended on. Not entitled means this pass sends nothing to any
            // of them; OneDrive is never gated.
            val othersAllowed = otherPending.isEmpty() || entitlement.isEntitled()
            if (!othersAllowed) {
                Logger.w(
                    TAG,
                    "uploadPending: ${otherPending.size} row(s) routed to a second cloud, " +
                        "but Pro is not unlocked and no trial is running — skipping this pass"
                )
            }

            val otherByLocation = if (othersAllowed) otherPending.groupBy { it.location } else emptyMap()
            for ((location, rows) in otherByLocation) {
                val uploader = uploaders.of(location)
                if (uploader == null || !uploader.isConnected()) {
                    Logger.w(TAG, "uploadPending: ${rows.size} row(s) routed to $location, which is not connected")
                    continue
                }

                for (entry in rows) {
                    onProgress(
                        BackupProgress(
                            completed = uploaded + skipped + pruned,
                            total = pending.size,
                            currentFile = entry.displayName,
                            currentBytesSent = 0,
                            currentBytesTotal = entry.sizeBytes
                        )
                    )

                    val source = ContentUriUploadSource(
                        resolver = context.contentResolver,
                        uri = android.net.Uri.parse(entry.contentUri),
                        displayName = entry.displayName,
                        sizeBytes = entry.sizeBytes
                    )

                    val result = uploader.upload(
                        source = source,
                        album = entry.album,
                        onProgress = { sent, total ->
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
                    )

                    when (result) {
                        is DataResult.Success -> {
                            // Never byte-verified: none of these is proven by this app. Written
                            // through markUploadedWithoutSizeVerification, which always leaves
                            // remoteSizeBytes NULL, so the row can never satisfy verifiedInCloud() and
                            // can never become Archive/Sync/Restore eligible. See that method's doc.
                            entryDao.markUploadedWithoutSizeVerification(
                                id = entry.id,
                                remoteItemId = result.value.id,
                                uploadedAt = System.currentTimeMillis()
                            )
                            uploaded++
                        }

                        is DataResult.Failure -> {
                            if (result.error == RemoteError.LocalFileMissing) {
                                recordDepartures(listOf(entry.id))
                                entryDao.forget(entry.id)
                                pruned++
                                continue
                            }

                            if (result.error == RemoteError.EmptyLocalFile) {
                                deferred++
                                continue
                            }

                            val stop = stopReasonFor(result.error)
                            if (stop != null) {
                                Logger.w(TAG, "uploadPending ($location): stopping this provider's loop — $stop")
                                break
                            }
                            entryDao.markFailed(entry.id, result.error.toString())
                            failed++
                        }
                    }
                }
            }

            BackupRunResult(
                uploaded = uploaded,
                failed = failed,
                // A real count. This previously reused nextPending with a limit of 1, so it could
                // only ever report 0 or 1 — "1 still to go" actually meant "at least one".
                remaining = if (allAlbums) entryDao.countPendingAll(MAX_ATTEMPTS) else entryDao.countPendingInSelectedAlbums(MAX_ATTEMPTS),
                skipped = skipped,
                deferred = deferred,
                pruned = pruned,
                // OneDrive's stop reason only. See where the two lists were split, and BackupWorker's
                // mapping of this field to a WorkManager Result — a Google Photos stop must never
                // turn a run that otherwise finished into a failed worker outcome for both providers.
                stoppedBecause = oneDriveStop
            )
        }


    /**
     * Every file sitting in an album the user set to Archive, whatever its backup state.
     *
     * Deliberately **not** [redundantLocalCopies], which returns only what the ledger already calls
     * verified. The Archive screen has to show the user the whole folder, because Ian's rule is that
     * a file not found in OneDrive is uploaded rather than reported — so a file the ledger knows
     * nothing about is work to be done, not a file to be hidden. Listing only the verified ones would
     * quietly drop exactly the files that still need attention, and the screen would claim an album
     * was ready when part of it had never been backed up at all.
     *
     * Scoped to Archive albums for the same reason everything else is: CLAUDE.md's rule that removal
     * follows from a mode the user set and from nothing else.
     */
    /**
     * What OneDrive holds that is not in the folder it belongs to on this phone.
     *
     * The download half of the Restore tab. Asked per folder rather than by content alone — see
     * [RestoreScope] for why that is a different question from the one the deletion guard asks, and
     * why the two must not share an answer.
     *
     * Scoped to what this app uploaded: a row exists only because this device sent the file. A photo
     * put in OneDrive from a PC is not offered, which is Ian's rule from 27 Aug 2026 — *"if the user
     * wants a straight download they can use OneDrive"* — and what keeps this tab from becoming the
     * cloud file browser the design principle rules out.
     *
     * Computed from a live scan on every call rather than from a stored flag. It costs about half a
     * second against 3,335 files, and the alternative is a second persisted notion of "gone" sitting
     * next to the one that guards cloud deletion, free to drift from it.
     */
    suspend fun filesNotOnThePhone(): List<BackupEntryEntity> = withContext(dispatcher) {
        if (scanner.access() == MediaAccess.NONE) {
            Logger.w(TAG, "filesNotOnThePhone: no media access")
            return@withContext emptyList()
        }

        val present = scanner.scanEverything().mapTo(HashSet()) {
            RestoreScope.signature(it.album, it.displayName, it.sizeBytes)
        }

        RestoreScope.notOnTheDevice(
            candidates = entryDao.fetchableFromCloud(),
            presentOnDevice = present,
            // The size the file has on the phone, so an optimised photo still in its folder matches
            // its own proxy and is not offered as a download. See RestoreScope.onDiskSizeBytes.
            signatureOf = {
                RestoreScope.signature(
                    it.album,
                    it.displayName,
                    RestoreScope.onDiskSizeBytes(it.isProxied, it.localProxySizeBytes, it.sizeBytes)
                )
            }
        ).also {
            Logger.d(TAG, "filesNotOnThePhone: ${it.size} files are in OneDrive but not in their folder")
        }
    }

    /**
     * Lists every OneDrive backup folder, once: the slow half of [driveRestoreFiles], which the Restore
     * tab holds on to so that leaving the tab and coming back does not read the drive again.
     *
     * A folder that cannot be read sets [DriveListing.couldNotList] and is left out, rather than
     * being treated as empty.
     */
    suspend fun listDriveFolders(): DriveListing = withContext(dispatcher) {
        val folders = cloudFolders()
            ?: return@withContext DriveListing(emptyMap(), couldNotList = true)

        val byFolder = LinkedHashMap<String, Map<String, RemoteFileRef>>()
        var couldNotList = false
        for (folder in folders) {
            if (folder.isEmpty) continue
            val index = remoteIndexFor(folder.name)
            if (index == null) couldNotList = true else byFolder[folder.name] = index
        }
        DriveListing(byFolder, couldNotList)
    }

    /**
     * Everything OneDrive holds in the folders this app backs up into that the Restore tab should
     * know about beyond the ledger's own lists, and the ledger's missing ids filled in on the way.
     *
     * Takes a [DriveListing] from [listDriveFolders], so the drive is read once and the comparison with
     * the phone, which is cheap and must be current, can be repeated.
     *
     * Ian, 18 Sept 2026: Restore should offer any file OneDrive holds and put it back in the album it
     * came from, not only what this app uploaded. It was the rule on 25 Aug (`RestorableFile`), was
     * narrowed to the ledger on 27 Aug, and is this again. What broke it in practice was the ledger
     * itself: an archive that forgot its rows left files in OneDrive that Restore could not see.
     *
     * Reads the drive, so it needs the network and can be slow; the Restore tab shows the ledger's
     * answer first and this one when it arrives. A folder that cannot be listed sets
     * [DriveRestoreFiles.couldNotList] and is left out rather than treated as empty. A phone without
     * full media access returns nothing, because "is it here?" cannot be answered.
     *
     * Side effect, bookkeeping only: a ledger row that says uploaded but never recorded an id gets it
     * from the listing when name and size match. That is what makes restoring in place work for those
     * rows, and it removes nothing.
     */
    suspend fun driveRestoreFiles(listing: DriveListing): DriveRestoreFiles = withContext(dispatcher) {
        if (scanner.access() != MediaAccess.FULL) return@withContext DriveRestoreFiles.NONE

        val onDevice = scanner.scanEverything()
        // An empty scan proves nothing about the phone; see RestoreScope.notOnTheDevice.
        if (onDevice.isEmpty()) return@withContext DriveRestoreFiles.NONE
        val presentSignatures = onDevice.mapTo(HashSet()) {
            RestoreScope.presenceSignature(it.album, it.displayName, it.sizeBytes)
        }
        val presentNames = onDevice.mapTo(HashSet()) { RestoreScope.presenceName(it.album, it.displayName) }

        val missing = mutableListOf<BackupEntryEntity>()
        val here = mutableListOf<BackupEntryEntity>()
        val couldNotList = listing.couldNotList

        for ((folderName, index) in listing.folders) {
            val known = entryDao.entriesForAlbum(folderName)
            known.filter { it.state == BackupState.UPLOADED && it.remoteItemId.isNullOrEmpty() }
                .forEach { row ->
                    val ref = index[row.displayName]
                    if (ref != null && ref.id.isNotEmpty() && ref.sizeBytes == row.sizeBytes) {
                        entryDao.fillMissingRemoteItemIdByKey(row.id, ref.id)
                    }
                }
            val knownNames = known.mapTo(HashSet()) { it.displayName }

            for ((name, ref) in index) {
                val size = ref.sizeBytes ?: continue
                if (!RestoreScope.isMedia(ref.mimeType, name)) continue

                when (
                    RestoreScope.classifyDriveFile(
                        album = folderName,
                        displayName = name,
                        remoteSizeBytes = size,
                        presentSignatures = presentSignatures,
                        presentNames = presentNames,
                        ledgerNamesInAlbum = knownNames
                    )
                ) {
                    RestoreScope.DriveFileState.HERE -> here += driveEntity(folderName, name, size, ref)
                    RestoreScope.DriveFileState.MISSING -> missing += driveEntity(folderName, name, size, ref)
                    RestoreScope.DriveFileState.LEDGER_HANDLES,
                    RestoreScope.DriveFileState.SAME_NAME_OTHER_SIZE -> Unit
                }
            }
        }

        Logger.d(
            TAG,
            "driveRestoreFiles: ${missing.size} to download, ${here.size} already here" +
                if (couldNotList) ", some folders could not be listed" else ""
        )
        DriveRestoreFiles(missing, here, couldNotList)
    }

    /**
     * A description of one OneDrive file in the shape the Restore code already understands. Not stored.
     *
     * The id is prefixed so it can never collide with a ledger key, and `DownloadMissingFile` treats a
     * row it cannot find as "write one when the file arrives".
     */
    private fun driveEntity(album: String, name: String, size: Long, ref: RemoteFileRef) = BackupEntryEntity(
        id = DRIVE_ID_PREFIX + ref.id,
        mediaStoreId = 0L,
        contentUri = "",
        displayName = name,
        album = album,
        sizeBytes = size,
        dateModifiedEpochSeconds = 0L,
        mimeType = ref.mimeType,
        isVideo = ref.mimeType.startsWith("video/") ||
            name.substringAfterLast('.', "").lowercase() in setOf("mp4", "mov", "m4v", "3gp", "mkv", "webm", "avi"),
        state = BackupState.UPLOADED,
        remoteItemId = ref.id,
        remoteSizeBytes = size,
        uploadedAtEpochMillis = ref.createdAtEpochMillis
    )

    /**
     * Forgets the mode of an Archive album an Archive run has left empty.
     *
     * Ian, 18 Sept 2026: an archived album that is empty should simply go, and a restore should bring
     * it back as a new album with the default mode. That is what this does, with nothing written: the
     * album's preference row is removed, so the album drops off the Albums tab like any emptied album,
     * and when files return the scanner finds it new and the ordinary new-album path gives it the
     * default. That default can never be Archive (`AlbumMode.canBeDefault`), which is what closes the
     * loop of archive, restore, archive again.
     *
     * Replaces 27 Aug's ruling that an emptied Archive album keeps its mode as a standing instruction.
     * The ledger rows and the OneDrive copies are untouched; the folder stays on disk, empty.
     *
     * Called only right after an Archive run has removed files, never from a plain rescan: a partial
     * scan would otherwise read as "every Archive album is empty" and wipe them all. Guarded the same
     * way as the prune. Returns the names forgotten.
     */
    suspend fun forgetEmptiedArchiveAlbums(): List<String> = withContext(dispatcher) {
        if (scanner.access() != MediaAccess.FULL) return@withContext emptyList()
        val everything = scanner.scanEverything()
        if (everything.isEmpty()) return@withContext emptyList()

        val present = everything.mapTo(HashSet()) { it.album }
        val emptied = albumDao.albumsInMode(AlbumMode.ARCHIVE).filter { it !in present }
        if (emptied.isNotEmpty()) {
            albumDao.deleteAlbums(emptied)
            Logger.i(TAG, "forgot the Archive mode of ${emptied.size} emptied albums: $emptied")
        }
        emptied
    }

    /**
     * Marks files for upload again, for ones the drive turns out not to have.
     *
     * Archive's validation treats "not in OneDrive" as work rather than as a verdict, and backs the
     * file up before deciding. That only functions if the uploader can see the file: `nextPending`
     * selects `state != UPLOADED`, and a file the ledger believes it already sent is excluded by
     * exactly that clause. So the run enqueued to fix the problem had nothing to select, and the
     * recheck afterwards could only reach the same answer as before.
     *
     * Bookkeeping only — see `BackupEntryDao.requeueForUpload`. Nothing is removed anywhere.
     */
    suspend fun requeueMissingFromCloud(items: List<LocalMediaItem>): Int =
        withContext(dispatcher) {
            if (items.isEmpty()) return@withContext 0
            val count = entryDao.requeueForUpload(items.map { it.mediaStoreId })
            Logger.i(TAG, "requeueMissingFromCloud: $count rows returned to pending for re-upload")
            count
        }

    /**
     * Album names the user has set to Archive, whether or not anything is left in them.
     *
     * Distinct from [filesInArchiveAlbums] returning nothing, and the difference is what the screen
     * says: no Archive album at all means "nothing here removes anything", while an Archive album
     * holding no files means the mode ran to completion — and is still standing.
     */
    suspend fun archiveAlbumNames(): List<String> = withContext(dispatcher) {
        albumIdentity.reconcile()
        albumDao.albumsInMode(AlbumMode.ARCHIVE).sorted()
    }

    suspend fun archiveFiles(): ArchiveFiles = withContext(dispatcher) {
        if (scanner.access() == MediaAccess.NONE) return@withContext ArchiveFiles()

        // Archive removes files. Its album membership must be settled before it is read, or a file
        // named for one spelling is caught by the mode of the other. TASK-023.
        albumIdentity.reconcile()

        val archived = albumDao.albumsInMode(AlbumMode.ARCHIVE).toSet()
        if (archived.isEmpty()) {
            Logger.d(TAG, "archiveFiles: no album is set to Archive")
            return@withContext ArchiveFiles()
        }

        // Files the user has kept at full size are not Archive's to offer. See FilePin: Restore
        // pins what it brings back, so a restored album is not immediately taken off the phone again,
        // and the Archive tab pins a file the user swipes out of Archive.
        val pinned = entryDao.pinnedKeys()
        val (notPinned, optedOut) = FilePin.split(
            items = scanner.scanAll().filter { it.album in archived },
            pinnedIds = pinned.mapTo(HashSet()) { it.id },
            pinnedMediaStoreIds = pinned.mapTo(HashSet()) { it.mediaStoreId },
            idOf = { backupKeyOf(it.album, it.displayName, it.sizeBytes, it.dateModifiedEpochSeconds) },
            mediaStoreIdOf = { it.mediaStoreId }
        )

        val order = compareBy<LocalMediaItem>({ it.album }, { it.displayName })
        ArchiveFiles(toArchive = notPinned.sortedWith(order), optedOut = optedOut.sortedWith(order)).also {
            Logger.d(
                TAG,
                "archiveFiles: ${it.toArchive.size} to archive, ${it.optedOut.size} opted out, " +
                    "in ${archived.size} albums"
            )
        }
    }

    /**
     * Records that Archive took these files off the phone on purpose, so their OneDrive copies are
     * never offered for removal by the "files deleted from this phone" window. See
     * [CloudCopyDecision.ARCHIVED].
     *
     * Marked by content key and by MediaStore id, because a row's key can have drifted from the file's
     * (a restore rewrites the modification time) while its id has not. Bookkeeping only; called after
     * a removal completes and before the ledger is refreshed.
     */
    suspend fun markRemovedByArchive(items: List<LocalMediaItem>) = withContext(dispatcher) {
        if (items.isEmpty()) return@withContext
        items.map { backupKeyOf(it.album, it.displayName, it.sizeBytes, it.dateModifiedEpochSeconds) }
            .chunked(DECISION_CHUNK)
            .forEach { entryDao.setCloudDecision(it, CloudCopyDecision.ARCHIVED) }
        items.map { it.mediaStoreId }
            .chunked(DECISION_CHUNK)
            .forEach { entryDao.setCloudDecisionByMediaStoreId(it, CloudCopyDecision.ARCHIVED) }
        Logger.i(TAG, "markRemovedByArchive: ${items.size} files marked as archived on purpose")
    }

    /** The files that may be archived. Unchanged in meaning: opted-out files are never in it. */
    suspend fun filesInArchiveAlbums(): List<LocalMediaItem> = archiveFiles().toArchive

    /**
     * Opts one file out of Archive, or puts it back in. Ian, 19 Sept 2026.
     *
     * The choice is the file's pin (see [FilePin]), stored on its ledger row, so it survives the app
     * being closed and is there to be reversed the next time the Archive tab is opened. Bookkeeping
     * only: nothing is removed, sent or changed on the phone or the drive, and the direction it
     * writes can only make the app do less.
     *
     * A file put in an Archive album a moment ago may have no ledger row yet, so the ledger is
     * refreshed first in that case. Returns false when there is still no row to write to, in which
     * case nothing was saved and the caller must not show the file as opted out.
     *
     * Putting a file back also clears any other row pinned under the same MediaStore id: a restore
     * rewrites a file's modification time, so its row and its key can have drifted apart, and the
     * pin is matched either way when the list is read.
     */
    suspend fun setArchiveOptOut(item: LocalMediaItem, optedOut: Boolean): Boolean =
        withContext(dispatcher) {
            val key = backupKeyOf(item.album, item.displayName, item.sizeBytes, item.dateModifiedEpochSeconds)

            if (optedOut) {
                if (entryDao.find(key) == null) refreshLedger()
                if (entryDao.find(key) == null) {
                    Logger.w(TAG, "setArchiveOptOut: no ledger row for ${item.displayName}, nothing saved")
                    return@withContext false
                }
                entryDao.setModeOverride(key, FilePin.overrideFor(true))
            } else {
                val pinned = entryDao.pinnedKeys()
                val ids = pinned.filter { it.id == key || it.mediaStoreId == item.mediaStoreId }.map { it.id }
                (ids + key).distinct().forEach { entryDao.setModeOverride(it, FilePin.overrideFor(false)) }
            }
            Logger.i(TAG, "setArchiveOptOut: ${item.displayName} ${if (optedOut) "kept on the phone" else "back in Archive"}")
            true
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

        // See filesInArchiveAlbums: membership is settled before Archive is read. TASK-023.
        albumIdentity.reconcile()

        // Scoped to albums the user set to Archive. Until 25 Aug 2026 this returned every verified
        // file regardless of mode, so Settings offered to remove files from Backup albums — while
        // Backup's own description promises "nothing on your phone changes and no space is freed".
        // Observed on the Fold 4: a 440 MB video was removed from an album set to Backup.
        //
        // CLAUDE.md settles which of the two gives way: "Nothing leaves the gallery unless the user
        // chose that for that album... Removal follows from a mode the user set, and from nothing
        // else." Archive is that mode; no other route may offer a file up.
        val archived = albumDao.albumsInMode(AlbumMode.ARCHIVE).toSet()
        if (archived.isEmpty()) {
            Logger.d(TAG, "redundantLocalCopies: no album is set to Archive, so nothing is offered")
            return@withContext emptyList()
        }

        val verifiedEntries = entryDao.verifiedInCloud()
        val verified = verifiedEntries.map { it.id }.toSet()

        // Proxied rows are matched by MediaStore id instead. Their content key was computed from
        // the original's size, and the file on disk is now a 2048px rewrite — so the key can never
        // match and Archive would silently skip every photo it had already optimised. Observed
        // 26 Aug 2026: an album switched Sync then Archive offered 2 of 13 files, and the 11 it
        // could not see were the ones it had shrunk itself.
        val verifiedProxiedIds = verifiedEntries.filter { it.isProxied }.mapTo(HashSet()) { it.mediaStoreId }

        // Left out before anything is matched: a file the user kept at full size is never offered.
        val pinned = entryDao.pinnedKeys()
        val pinnedIds = pinned.mapTo(HashSet()) { it.id }
        val pinnedMediaStoreIds = pinned.mapTo(HashSet()) { it.mediaStoreId }

        scanner.scanAll().filter { item ->
            if (item.album !in archived) return@filter false
            val key = backupKeyOf(
                album = item.album,
                displayName = item.displayName,
                sizeBytes = item.sizeBytes,
                dateModifiedEpochSeconds = item.dateModifiedEpochSeconds
            )
            if (key in pinnedIds || item.mediaStoreId in pinnedMediaStoreIds) return@filter false
            key in verified || item.mediaStoreId in verifiedProxiedIds
        }.also {
            Logger.d(
                TAG,
                "redundantLocalCopies: ${it.size} files in Archive albums are safely in OneDrive"
            )
        }
    }

    /**
     * Asks OneDrive, right now, whether these files are still there.
     *
     * ### Why the ledger is not enough
     *
     * `verifiedInCloud` reads a **remembered** byte size. It says a copy was confirmed once, which is
     * a different claim from "there is a copy now" — and removal is the one operation where only the
     * second claim will do. Nothing else in the app re-checks: a file deleted from OneDrive by hand
     * leaves a row insisting it is safe forever. Demonstrated 25 Aug 2026 by deleting a test file
     * from the drive and watching the ledger go on asserting it was backed up.
     *
     * ### Three outcomes, and only one of them permits removal
     *
     * [CloudConfirmation.unconfirmed] is the category that matters. An album whose listing failed is
     * not an album whose files are gone, and it is equally not an album whose files are safe. The
     * cautious reading is the only acceptable one here: **if we could not ask, we do not remove.**
     * The same rule the reconciliation follows, applied where being wrong costs a photo.
     */
    suspend fun confirmStillInCloud(
        items: List<LocalMediaItem>
    ): CloudConfirmation = withContext(dispatcher) {
        if (items.isEmpty()) return@withContext CloudConfirmation()

        val confirmed = mutableListOf<LocalMediaItem>()
        val missing = mutableListOf<LocalMediaItem>()
        val unconfirmed = mutableListOf<LocalMediaItem>()
        val presentAtWrongSize = mutableSetOf<Long>()

        // One listing per album, reused across its files, exactly as the upload path does.
        val byAlbum = mutableMapOf<String, Map<String, RemoteFileRef>?>()

        // What size the cloud copy *should* be, for files whose local copy is no longer that size.
        //
        // An optimised photo is a 2048px rewrite; OneDrive holds the full original. Comparing the
        // remote against the local file would compare the original against the proxy, never match,
        // and report a photo as no longer in OneDrive — false, and the most alarming thing this
        // screen can say. The ledger remembers what was uploaded, so ask it.
        //
        // This is the check Ian asked for on 26 Aug: "if the file is Optimized, that a full version
        // is sitting in OneDrive". It could not fire before that day's fix, because Archive could
        // not see proxied files at all.
        val expectedRemoteSize = entryDao.uploadedKeys()
            .filter { it.isProxied }
            .associate { it.mediaStoreId to it.sizeBytes }

        for (item in items) {
            val index = if (byAlbum.containsKey(item.album)) {
                byAlbum[item.album]
            } else {
                remoteIndexFor(item.album).also { byAlbum[item.album] = it }
            }

            // The proxy's own size for an ordinary file; the remembered original for an
            // optimised one.
            val expected = expectedRemoteSize[item.mediaStoreId] ?: item.sizeBytes

            val ref = index?.get(item.displayName)

            when {
                // Could not list the album at all.
                index == null -> unconfirmed += item

                // Listed, and the drive reports the size we expect. The only confirming case.
                ref?.sizeBytes == expected -> {
                    confirmed += item
                    // The listing has just handed us this file's OneDrive id. A row that never
                    // recorded one is about to lose its file from the phone, and a row without an
                    // id is what the prune forgets when the album empties and what Restore cannot
                    // fetch. Bookkeeping only: nothing is removed here, and a row that already has
                    // an id is left alone. See BackupEntryDao.fillMissingRemoteItemId.
                    if (ref?.id?.isNotEmpty() == true) {
                        entryDao.fillMissingRemoteItemId(item.mediaStoreId, ref.id)
                    }
                }

                // Listed, the name is there, and the drive did not say how big it is. **Not**
                // evidence the file is gone — it is the absence of evidence either way, and it
                // belongs with "could not check" rather than with "no longer in OneDrive". Told
                // apart since 28 Aug 2026: the mapper used to render a missing size as 0, which
                // made this indistinguishable from a genuinely empty file and put it in `missing`.
                // The user then read "Not in OneDrive" about a file that was sitting in OneDrive.
                ref != null && ref.sizeBytes == null -> unconfirmed += item

                // Either the name is absent, or it is there at a size that is not the file's.
                // Both stay in `missing` — a wrong-sized copy protects nothing — but they are
                // recorded apart so the screen can say which one happened.
                else -> {
                    missing += item
                    if (ref != null) presentAtWrongSize += item.mediaStoreId
                }
            }
        }

        Logger.i(
            TAG,
            "confirmStillInCloud: ${confirmed.size} confirmed, ${missing.size} no longer in " +
                "OneDrive, ${unconfirmed.size} could not be checked"
        )

        // Name the ones that failed, and say what the listing held instead.
        //
        // "1 no longer in OneDrive" is not a diagnosable statement: it cannot distinguish a file
        // that is genuinely absent from one whose name or size does not match what the listing
        // returned, and those want opposite fixes. Cost is one line per failure, and only when
        // there is a failure.
        for (item in missing) {
            val index = byAlbum[item.album]
            Logger.w(
                TAG,
                "confirmStillInCloud: '${item.displayName}' (${item.sizeBytes} B) not matched in " +
                    "${item.album} — listing held ${index?.size ?: 0} names, " +
                    "same name present: ${index?.containsKey(item.displayName)}, " +
                    "its size there: ${index?.get(item.displayName)?.sizeBytes ?: "not reported"}"
            )
        }
        CloudConfirmation(confirmed, missing, unconfirmed, presentAtWrongSize)
    }

    /**
     * Folder names OneDrive holds under the roots this app backs up into.
     *
     * The entry point for retrieval: pick a folder, then see what is in it. Reads the drive rather
     * than the album table, because the folders worth fetching from include ones this phone has
     * never had. On a new handset the album table and the ledger are both empty and OneDrive is
     * full, which is precisely when someone goes looking for a restore.
     *
     * **Confined to [RemoteRoots.searchOrder].** Ian, 25 Aug 2026: only the roots for now. That
     * keeps this a restore screen rather than a file manager — a full drive walk would be the cloud
     * browser the design principle rules out, and the Open OneDrive button in Settings already
     * covers real browsing. Worth revisiting when other cloud services arrive, since a second
     * provider will not lay its files out under a Samsung path.
     *
     * `null` when a root could not be listed, which the caller must not render as "you have no
     * backups". Failing to ask is not evidence of absence.
     */
    suspend fun cloudFolders(): List<RestorableFolder>? = withContext(dispatcher) {
        val found = mutableMapOf<String, RemoteMediaNode.Folder>()

        for (root in RemoteRoots.searchOrder(destinationRoot())) {
            var page = when (val result = repository.listFolderByPath(root)) {
                is DataResult.Success -> result.value
                is DataResult.Failure -> {
                    Logger.w(TAG, "cloudFolders: could not list $root (${result.error})")
                    return@withContext null
                }
            }
            page.nodes.filterIsInstance<RemoteMediaNode.Folder>()
                .forEach { found.putIfAbsent(it.name, it) }

            var pages = 1
            while (page.nextPageToken != null && pages < MAX_REMOTE_PAGES) {
                page = when (val result = repository.listNextPage(page.nextPageToken!!)) {
                    is DataResult.Success -> result.value
                    // A partial list of folders is still usable: every name in it is real and the
                    // user can act on it. Unlike the index below, a short answer here cannot cause a
                    // wrong decision, only a missing row.
                    is DataResult.Failure -> break
                }
                page.nodes.filterIsInstance<RemoteMediaNode.Folder>()
                    .forEach { found.putIfAbsent(it.name, it) }
                pages++
            }
        }

        // One scan, grouped by album name, rather than one listing per folder. See [RestorableFolder]
        // for why these counts are deliberately not an identity claim.
        val hereByAlbum = if (scanner.access() == MediaAccess.FULL) {
            scanner.scanEverything().groupingBy { it.album }.eachCount()
        } else {
            emptyMap()
        }

        Logger.d(TAG, "cloudFolders: ${found.size} folders across the search roots")
        found.values
            .map { folder ->
                RestorableFolder(
                    name = folder.name,
                    fileCount = folder.childCount,
                    sizeBytes = folder.sizeBytes,
                    onDeviceCount = hereByAlbum[folder.name] ?: 0
                )
            }
            .sortedBy { it.name.lowercase() }
    }

    /**
     * Where new uploads go, as a path the user can read.
     *
     * For the breadcrumb on the restore screen. The destination is shown rather than the whole
     * search set: [remoteIndexFor] also looks in `Samsung Gallery/DCIM`, so a folder present in both
     * has two true paths and only one of them is where the next upload would land. Showing the
     * destination is the one that stays true as the drive changes.
     */
    suspend fun destinationPath(): String = destinationRoot()

    /**
     * Everything OneDrive holds for one album, each marked with whether the phone still has it.
     *
     * Reuses [remoteIndexFor] rather than walking the pages again. That walk is the one this
     * codebase has already paid for: reading a single page made 5,523 files look absent on a real
     * library, and a second copy of the logic is a second chance to reintroduce it.
     *
     * Every file is returned, including ones already on the device — see [RestorableFile]. `null`
     * means the folder could not be listed, which is not the same as it being empty.
     */
    suspend fun restorableFilesIn(album: String): List<RestorableFile>? = withContext(dispatcher) {
        val index = remoteIndexFor(album) ?: return@withContext null

        // Only a trustworthy scan may say a file is here. Without full access the honest answer is
        // "we do not know", and the safe rendering of that is to mark nothing — an unmarked file is
        // simply offered, which costs a duplicate at worst. Claiming a file is already on the phone
        // when we cannot see it would talk the user out of a retrieval they need.
        val onDevice = if (scanner.access() == MediaAccess.FULL) {
            scanner.scanEverything().mapTo(HashSet()) {
                RestoredAlbum.contentSignature(it.displayName, it.sizeBytes)
            }
        } else {
            emptySet()
        }

        index.map { (name, ref) ->
            // An unreported size shows as 0 in the list and never matches an on-device signature.
            // Both are the safe direction here: the row still offers the download, and "already on
            // this phone" stays a claim we can only make when we actually know the size.
            RestorableFile(
                remoteItemId = ref.id,
                displayName = name,
                mimeType = ref.mimeType,
                sizeBytes = ref.sizeBytes ?: 0L,
                alreadyOnDevice = ref.sizeBytes != null &&
                    RestoredAlbum.contentSignature(name, ref.sizeBytes) in onDevice
            )
        }.sortedBy { it.displayName.lowercase() }
    }

    private class CachedIndex(val index: Map<String, RemoteFileRef>, val atMillis: Long)

    private val indexCache = HashMap<String, CachedIndex>()

    /** The clock the cache reads, so a test can move time. */
    internal var clock: () -> Long = System::currentTimeMillis

    /**
     * [remoteIndexFor], remembered for a few minutes, **for the deleted-files window only**.
     *
     * The window looks in OneDrive to decide where a file belongs, and *Back up* then needs the same
     * listing to be sure it is not sending a second copy. For a large album that is many requests
     * (about 20 s for a 1,888-file folder), and the second walk answered a question the first had
     * just answered. Ian asked for the cache on 19 Sept 2026.
     *
     * Deliberately not used by the upload queue, Restore or the cloud check, which want the drive as
     * it is now. Kept short, and dropped whenever this app changes the drive ([forgetCachedRemoteIndex]).
     *
     * **A listing that came back short is never kept.** A partial index says "no copy" for files it
     * simply did not reach, and a cache would repeat that for minutes. Failures are not kept either.
     */
    private suspend fun cachedRemoteIndexFor(album: String): Map<String, RemoteFileRef>? {
        val now = clock()
        synchronized(indexCache) {
            indexCache[album]?.takeIf { now - it.atMillis < INDEX_CACHE_MILLIS }?.let { return it.index }
        }

        var partial = false
        val fresh = remoteIndexFor(album) { partial = true } ?: return null
        if (!partial) synchronized(indexCache) { indexCache[album] = CachedIndex(fresh, now) }
        return fresh
    }

    /**
     * Drops what is remembered about the drive, for one album or all of them. Called whenever this app
     * has just added to it or removed from it, so the next question is answered from the drive as it is.
     */
    fun forgetCachedRemoteIndex(album: String? = null) {
        synchronized(indexCache) { if (album == null) indexCache.clear() else indexCache.remove(album) }
    }

    /**
     * Which of these deleted files OneDrive already holds, **whether this app put them there or not**.
     * Ian, 19 Sept 2026.
     *
     * Looks in each album's own OneDrive folder, by name and size, and nowhere else: he chose that over
     * searching the whole drive. A same-named file of a different size is different content, so it is
     * not a copy. Reuses [remoteIndexFor], the walk that already reads every page.
     *
     * A file that cannot be placed is reported as unknown rather than absent. **Failing to ask is not
     * evidence of absence**, and treating an unlistable folder as empty would tell the user nothing
     * was backed up and invite them to send a second copy.
     */
    suspend fun cloudCopiesOf(files: List<DeletedFile>): CloudLookup = withContext(dispatcher) {
        val found = HashMap<String, String>()
        val unknown = HashSet<String>()

        for ((album, group) in files.groupBy { it.album }) {
            val index = cachedRemoteIndexFor(album)
            if (index == null) {
                unknown += group.map { it.id }
                continue
            }
            for (file in group) {
                val ref = index[file.displayName] ?: continue
                val size = ref.sizeBytes
                when {
                    size == null -> unknown += file.id
                    size == file.sizeBytes -> found[file.id] = ref.id
                    // Same name, other size: another version, not this file's copy.
                }
            }
        }
        CloudLookup(found = found, unknown = unknown)
    }

    /**
     * Sends files that were deleted before they were ever backed up to OneDrive, reading them from the
     * phone's trash, and leaves them where they are. Ian, 19 Sept 2026: *"Remain in Trash / Back up to
     * Cloud"*.
     *
     * Adds a copy and removes nothing anywhere, which is why it needs no confirmation of its own. The
     * user has ticked these files and pressed the button; not ticking one leaves it in the trash.
     *
     * ### What "readable" means
     *
     * A trashed photo keeps its MediaStore id and its bytes, and this app can open it through the
     * URI the ledger row recorded (measured on the Moto G, 19 Sept 2026). If the trash has since been
     * emptied the open fails with `LocalFileMissing`: there is nothing left to save or to decide, so
     * the record is dropped and the file is counted as [TrashBackupOutcome.unreadable].
     *
     * ### What is written when one lands
     *
     * A ledger row, already uploaded and already marked as gone from the phone, with the decision set
     * so the deletion window does not immediately offer to remove the copy that was just made. That
     * row is also what lets Restore fetch the file back.
     *
     * A failure that will repeat for every file stops the run, as [uploadPending] does. Files not
     * reached, and files that failed, keep their record and are offered again.
     */
    suspend fun backUpFromTrash(
        files: List<DeletedFile>,
        onProgress: (done: Int, total: Int, current: String) -> Unit = { _, _, _ -> }
    ): TrashBackupOutcome = withContext(dispatcher) {
        var uploaded = 0
        var alreadyThere = 0
        var unreadable = 0
        var failed = 0
        var done = 0

        // Same rule as the upload queue: a listing that failed stays failed for the whole run.
        val remoteByAlbum = mutableMapOf<String, Map<String, RemoteFileRef>?>()

        for (file in files) {
            onProgress(done, files.size, file.displayName)

            val index = if (remoteByAlbum.containsKey(file.album)) {
                remoteByAlbum[file.album]
            } else {
                cachedRemoteIndexFor(file.album).also { remoteByAlbum[file.album] = it }
            }
            if (index == null) {
                failed++
                done++
                continue
            }

            val there = index[file.displayName]
            if (there != null && there.sizeBytes == file.sizeBytes) {
                recordBackedUp(file, there.id, there.sizeBytes ?: file.sizeBytes)
                alreadyThere++
                done++
                continue
            }

            val result = uploadRepository.upload(
                source = sourceFor(file),
                remoteFolderPath = remotePathFor(file.album),
                onProgress = { _, _ -> }
            )

            when (result) {
                is DataResult.Success -> {
                    // Size equality is the proof, as for every upload: "a file appeared" is also true
                    // of a truncated one.
                    // The drive has changed, so nothing remembered about this album is true any more.
                    forgetCachedRemoteIndex(file.album)
                    if (result.value.sizeBytes == file.sizeBytes) {
                        recordBackedUp(file, result.value.id, result.value.sizeBytes)
                        uploaded++
                    } else {
                        Logger.w(TAG, "trash backup of ${file.displayName}: sent ${file.sizeBytes}, stored ${result.value.sizeBytes}")
                        failed++
                    }
                }

                is DataResult.Failure -> {
                    if (result.error == RemoteError.LocalFileMissing) {
                        // Trash emptied since the row was written. Nothing left to save or to ask.
                        unsentDao.forget(listOf(file.id))
                        unreadable++
                    } else if (stopReasonFor(result.error) != null) {
                        Logger.w(TAG, "backUpFromTrash: stopping run — ${stopReasonFor(result.error)}")
                        return@withContext TrashBackupOutcome(
                            uploaded = uploaded,
                            alreadyThere = alreadyThere,
                            unreadable = unreadable,
                            failed = failed + (files.size - done),
                            stoppedBecause = stopReasonFor(result.error)
                        )
                    } else {
                        failed++
                    }
                }
            }
            done++
        }

        onProgress(done, files.size, "")
        Logger.i(TAG, "backUpFromTrash: $uploaded sent, $alreadyThere already there, $unreadable unreadable, $failed left")
        TrashBackupOutcome(uploaded, alreadyThere, unreadable, failed)
    }

    /**
     * How a departed file's bytes are opened: through the URI its ledger row recorded, which a trashed
     * item keeps. A seam for tests only, because `Uri.parse` does not run off a device.
     */
    internal var sourceFor: (DeletedFile) -> UploadSource = { file ->
        ContentUriUploadSource(
            resolver = context.contentResolver,
            uri = android.net.Uri.parse(file.contentUri),
            displayName = file.displayName,
            sizeBytes = file.sizeBytes
        )
    }

    /** Records that a departed file is now in OneDrive, and drops it from the unsent list. */
    private suspend fun recordBackedUp(file: DeletedFile, remoteItemId: String, remoteSize: Long) {
        val now = System.currentTimeMillis()
        entryDao.insertIfNew(
            listOf(
                BackupEntryEntity(
                    id = file.id,
                    mediaStoreId = file.mediaStoreId,
                    contentUri = file.contentUri,
                    displayName = file.displayName,
                    album = file.album,
                    sizeBytes = file.sizeBytes,
                    dateModifiedEpochSeconds = file.dateModifiedEpochSeconds,
                    mimeType = file.mimeType,
                    isVideo = file.isVideo,
                    // Written as uploaded in one go, so the upload queue can never see it pending.
                    state = BackupState.UPLOADED,
                    remoteItemId = remoteItemId,
                    remoteSizeBytes = remoteSize,
                    uploadedAtEpochMillis = now,
                    localMissingSinceEpochMillis = file.departedAtEpochMillis,
                    cloudDecision = CloudCopyDecision.KEPT
                )
            )
        )
        // Stated again for a row that already existed, which the insert above leaves as it was.
        entryDao.markUploaded(
            id = file.id,
            remoteItemId = remoteItemId,
            remoteSizeBytes = remoteSize,
            uploadedAt = now
        )
        // Left alone from now on: the user chose to save this file, so its copy is not a candidate for
        // the very next question.
        entryDao.setCloudDecision(listOf(file.id), CloudCopyDecision.KEPT)
        unsentDao.forget(listOf(file.id))
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
    internal suspend fun remoteIndexFor(
        album: String,
        onPartial: () -> Unit = {}
    ): Map<String, RemoteFileRef>? {
        val merged = mutableMapOf<String, RemoteFileRef>()

        for (root in RemoteRoots.searchOrder(destinationRoot())) {
            // One unreachable root makes the whole answer unknown. Merging what did list would
            // under-report what is backed up, and under-reporting here means re-uploading files the
            // user already has — the same "failing to ask is not evidence of absence" rule that the
            // per-album null exists for, applied across roots.
            val one = indexForPath("$root/$album", album, onPartial) ?: return null
            // First root wins on a duplicate name, so the destination's copy is preferred.
            for ((name, ref) in one) merged.putIfAbsent(name, ref)
        }
        return merged
    }

    /** Every file at one remote path, by name and size, or null if it could not be listed. */
    private suspend fun indexForPath(
        path: String,
        album: String,
        onPartial: () -> Unit
    ): Map<String, RemoteFileRef>? {
        val index = mutableMapOf<String, RemoteFileRef>()

        var page = when (val result = repository.listFolderByPath(path)) {
            is DataResult.Success -> result.value
            is DataResult.Failure -> {
                Logger.w(TAG, "could not list $album remotely (${result.error}); deferring")
                return null
            }
        }
        index += page.nodes.filterIsInstance<RemoteMediaNode.File>()
            .associate { it.name to RemoteFileRef(it.id, it.sizeBytes, it.mimeType, it.createdAtUtc) }

        var pages = 1
        while (page.nextPageToken != null && pages < MAX_REMOTE_PAGES) {
            page = when (val result = repository.listNextPage(page.nextPageToken!!)) {
                is DataResult.Success -> result.value
                is DataResult.Failure -> {
                    Logger.w(TAG, "page ${pages + 1} of $album failed (${result.error}); using $pages")
                    onPartial()
                    return index
                }
            }
            index += page.nodes.filterIsInstance<RemoteMediaNode.File>()
            .associate { it.name to RemoteFileRef(it.id, it.sizeBytes, it.mimeType, it.createdAtUtc) }
            pages++
        }

        // Stopped by the page cap with more still to read: the index is short.
        if (page.nextPageToken != null) onPartial()

        if (pages > 1) Logger.d(TAG, "$album: ${index.size} remote files across $pages pages")
        // Name the path. Two roots are searched for every album — the destination and the legacy
        // Samsung one — and until this line the log said only that *a* listing returned N items,
        // which is not enough to tell "your files are in OneDrive" from "your files are in a
        // OneDrive folder you were not looking in".
        Logger.i(TAG, "listed '$path': ${index.size} files")
        return index
    }

    /**
     * Maps a device album onto its place in OneDrive.
     *
     * Mirrors the layout Samsung already created — `Samsung Gallery/DCIM/<album>` — so that after
     * Samsung's sync stops, new photos keep landing beside the ones already there instead of
     * starting a second parallel structure the user then has to reconcile.
     */
    private suspend fun remotePathFor(album: String): String = "${destinationRoot()}/$album"

    /**
     * Where new uploads go. User-settable; defaults to the layout Samsung created.
     *
     * Read per use rather than cached, so a change takes effect on the next file instead of the
     * next process. The cost is a DataStore read, which is already in memory after the first.
     */
    private suspend fun destinationRoot(): String = settings.current().destinationRoot

    private fun stopReasonFor(error: RemoteError): StopReason? = when (error) {
        RemoteError.NoToken -> StopReason.NO_TOKEN
        RemoteError.Unauthorized -> StopReason.UNAUTHORIZED
        RemoteError.InsufficientStorage -> StopReason.DRIVE_FULL
        RemoteError.Network -> StopReason.NETWORK

        // These affect one file, not the run. A missing local file especially: a ledger row can
        // outlive the file it describes, and letting that halt everything means one deleted photo
        // silently stops the rest of a library being backed up.
        RemoteError.LocalFileMissing,
        RemoteError.EmptyLocalFile,
        is RemoteError.Http,
        is RemoteError.Unknown -> null
    }

    companion object {
        private const val TAG = "BackupEngine"

        /** Marks a description of a OneDrive file that has no ledger row. See [driveRestoreFiles]. */
        const val DRIVE_ID_PREFIX = "drive:"

        /**
         * Retired in favour of [RemoteRoots]. The destination is now a user setting and the search
         * set is more than one folder, so a single constant can no longer describe either.
         */
        @Deprecated(
            "The destination is a setting; use RemoteRoots",
            ReplaceWith("RemoteRoots.SAMSUNG_GALLERY")
        )
        const val REMOTE_ROOT = RemoteRoots.SAMSUNG_GALLERY

        /**
         * Bound variables per statement.
         *
         * Comfortably under SQLite's historic 999 limit, which is what older Android versions
         * enforce even though newer ones allow far more.
         */
        /**
         * How long a held upload session stays usable after the user interrupted it.
         *
         * Inside Graph's own window, deliberately, so a session is never kept past the point it
         * would work.
         */
        const val STALE_SESSION_AFTER_MILLIS = 10 * 60 * 1000L

        /** SQLite binds one variable per id and stops at 999, so lists of ids are written in chunks. */
        const val DECISION_CHUNK = 500

        const val SQL_BATCH = 500

        /**
         * Ceiling on the page walk in [remoteIndexFor], so a paging loop that never terminates
         * cannot hang a backup run. At 100 items a page this covers 20,000 files in one album,
         * comfortably past the 8,482 in the whole library it was measured against.
         */
        const val MAX_REMOTE_PAGES = 200

        /** How long the deleted-files window remembers what a OneDrive folder held. */
        const val INDEX_CACHE_MILLIS = 3L * 60 * 1000

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
