package com.gallery.sync.domain.backup

import android.content.Context
import com.gallery.sync.data.local.dao.AlbumPreferenceDao
import com.gallery.sync.data.local.dao.BackupEntryDao
import com.gallery.sync.data.local.entity.AlbumMode
import com.gallery.sync.data.local.entity.AlbumPreferenceEntity
import com.gallery.sync.data.local.entity.BackupEntryEntity
import com.gallery.sync.data.local.entity.BackupState
import com.gallery.sync.data.local.entity.backupKeyOf
import com.gallery.sync.data.local.media.LocalMediaItem
import com.gallery.sync.data.local.media.MediaAccess
import com.gallery.sync.data.local.media.MediaScanner
import com.gallery.sync.data.local.media.ProxyMarker
import com.gallery.sync.data.local.media.RestoredAlbum
import com.gallery.sync.data.local.settings.BackupSettings
import com.gallery.sync.data.remote.onedrive.ContentUriUploadSource
import com.gallery.sync.data.remote.onedrive.ResumableSession
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
    private val albumDao: AlbumPreferenceDao,
    private val settings: BackupSettings,
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
        albumDao.insertIfNew(albumsOnDevice.map { AlbumPreferenceEntity(it, defaultMode) })

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
        entryDao.countPendingInSelectedAlbums()
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
            val remoteByAlbum = mutableMapOf<String, Map<String, RemoteFileRef>?>()
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
                val match = alreadyThere[entry.displayName]
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
                        uploadedAt = System.currentTimeMillis()
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
                    existingSession = entry.uploadSessionUrl?.let {
                        ResumableSession(it, entry.uploadSessionExpiresAtEpochMillis)
                    },
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
    suspend fun filesInArchiveAlbums(): List<LocalMediaItem> = withContext(dispatcher) {
        if (scanner.access() == MediaAccess.NONE) return@withContext emptyList()

        val archived = albumDao.albumsInMode(AlbumMode.ARCHIVE).toSet()
        if (archived.isEmpty()) {
            Logger.d(TAG, "filesInArchiveAlbums: no album is set to Archive")
            return@withContext emptyList()
        }

        scanner.scanAll()
            .filter { it.album in archived }
            .sortedWith(compareBy({ it.album }, { it.displayName }))
            .also { Logger.d(TAG, "filesInArchiveAlbums: ${it.size} files in ${archived.size} albums") }
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

        scanner.scanAll().filter { item ->
            if (item.album !in archived) return@filter false
            val key = backupKeyOf(
                album = item.album,
                displayName = item.displayName,
                sizeBytes = item.sizeBytes,
                dateModifiedEpochSeconds = item.dateModifiedEpochSeconds
            )
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

            when {
                index == null -> unconfirmed += item
                index[item.displayName]?.sizeBytes == expected -> confirmed += item
                else -> missing += item
            }
        }

        Logger.i(
            TAG,
            "confirmStillInCloud: ${confirmed.size} confirmed, ${missing.size} no longer in " +
                "OneDrive, ${unconfirmed.size} could not be checked"
        )
        CloudConfirmation(confirmed, missing, unconfirmed)
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
            RestorableFile(
                remoteItemId = ref.id,
                displayName = name,
                mimeType = ref.mimeType,
                sizeBytes = ref.sizeBytes,
                alreadyOnDevice = RestoredAlbum.contentSignature(name, ref.sizeBytes) in onDevice
            )
        }.sortedBy { it.displayName.lowercase() }
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
    internal suspend fun remoteIndexFor(album: String): Map<String, RemoteFileRef>? {
        val merged = mutableMapOf<String, RemoteFileRef>()

        for (root in RemoteRoots.searchOrder(destinationRoot())) {
            // One unreachable root makes the whole answer unknown. Merging what did list would
            // under-report what is backed up, and under-reporting here means re-uploading files the
            // user already has — the same "failing to ask is not evidence of absence" rule that the
            // per-album null exists for, applied across roots.
            val one = indexForPath("$root/$album", album) ?: return null
            // First root wins on a duplicate name, so the destination's copy is preferred.
            for ((name, ref) in one) merged.putIfAbsent(name, ref)
        }
        return merged
    }

    /** Every file at one remote path, by name and size, or null if it could not be listed. */
    private suspend fun indexForPath(path: String, album: String): Map<String, RemoteFileRef>? {
        val index = mutableMapOf<String, RemoteFileRef>()

        var page = when (val result = repository.listFolderByPath(path)) {
            is DataResult.Success -> result.value
            is DataResult.Failure -> {
                Logger.w(TAG, "could not list $album remotely (${result.error}); deferring")
                return null
            }
        }
        index += page.nodes.filterIsInstance<RemoteMediaNode.File>()
            .associate { it.name to RemoteFileRef(it.id, it.sizeBytes, it.mimeType) }

        var pages = 1
        while (page.nextPageToken != null && pages < MAX_REMOTE_PAGES) {
            page = when (val result = repository.listNextPage(page.nextPageToken!!)) {
                is DataResult.Success -> result.value
                is DataResult.Failure -> {
                    Logger.w(TAG, "page ${pages + 1} of $album failed (${result.error}); using $pages")
                    return index
                }
            }
            index += page.nodes.filterIsInstance<RemoteMediaNode.File>()
            .associate { it.name to RemoteFileRef(it.id, it.sizeBytes, it.mimeType) }
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
        is RemoteError.Http,
        is RemoteError.Unknown -> null
    }

    companion object {
        private const val TAG = "BackupEngine"

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
        const val SQL_BATCH = 500

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
