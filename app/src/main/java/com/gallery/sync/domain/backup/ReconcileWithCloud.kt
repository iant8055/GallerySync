package com.gallery.sync.domain.backup

import com.gallery.sync.data.local.dao.AlbumCloudStatusDao
import com.gallery.sync.data.local.entity.AlbumCloudStatusEntity
import com.gallery.sync.data.local.media.MediaAccess
import com.gallery.sync.data.local.media.MediaScanner
import com.gallery.sync.di.IoDispatcher
import com.gallery.sync.util.Logger
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.coroutineContext

/**
 * Answers "how much of this is already safe?" without uploading a byte.
 *
 * This is the first honest number the user sees, and the one the whole first-run choice turns on.
 * Ian's assumption is that most people already have their photos in OneDrive because Samsung's own
 * sync put them there, so a first run is mostly reconciliation rather than a bulk transfer. Measured
 * on a real drive: 8,482 local files reduced to 206 actually needing upload.
 *
 * ### It calls the engine's own listing, deliberately
 *
 * [BackupEngine.remoteIndexFor] is reused rather than reimplemented, so the figures shown to the
 * user are produced by the same code that later decides what to send. A separate implementation
 * would be free to drift, and the first symptom would be a setup screen promising something the
 * engine then disagrees with.
 *
 * It also inherits that function's paging fix. Reading one page of a large folder makes every file
 * beyond the first page look absent — which on this library was thousands of files.
 *
 * ### Cost
 *
 * One listing request per album, plus one per extra page. Across ~90 albums that is slow and worth
 * showing progress for, which is what [onProgress] is for: it emits a running total after each
 * album, so the screen fills in rather than sitting blank.
 */
@Singleton
class ReconcileWithCloud @Inject constructor(
    private val scanner: MediaScanner,
    private val cloudStatusDao: AlbumCloudStatusDao,
    private val engine: BackupEngine,
    private val entryDao: com.gallery.sync.data.local.dao.BackupEntryDao,
    @param:IoDispatcher private val dispatcher: CoroutineDispatcher
) {

    /**
     * Walks every album on the device and compares it with OneDrive.
     *
     * Returns null when media cannot be read at all — distinct from a result of zero, which would
     * claim the library is empty.
     *
     * Albums that cannot be listed are counted as [CloudReconciliation.unchecked] rather than as
     * missing. A run that loses connectivity half way must report "we could not check 40 albums",
     * never "40 albums are not backed up".
     */
    suspend fun run(
        onProgress: (CloudReconciliation) -> Unit = {}
    ): CloudReconciliation? = withContext(dispatcher) {
        if (scanner.access() == MediaAccess.NONE) {
            Logger.w(TAG, "reconcile: no media access")
            return@withContext null
        }

        val albums = scanner.scanAlbums()
        Logger.i(TAG, "reconcile: checking ${albums.size} albums against OneDrive")

        var total = CloudReconciliation()

        for (album in albums) {
            // A user who backs out of setup must not leave a hundred listing requests in flight.
            coroutineContext.ensureActive()

            val local = scanner.scanAlbum(album.name)
            // null means the listing failed. Passed through as-is: substituting an empty map here
            // is exactly the mistake that reported 8,177 safe files as missing.
            val remoteIndex = engine.remoteIndexFor(album.name)

            // A proxied file is smaller on disk than the original OneDrive holds, so without its
            // pre-proxy size the size test fails and a verified file counts as missing — the Albums
            // tab said "2 of 5 verified in OneDrive" for an album whose five were all uploaded, and
            // the number fell further with every clip optimised.
            val proxiedOriginals = entryDao.proxiedOriginalSizes(album.name)
                .associate { it.displayName to it.sizeBytes }

            val forAlbum = ReconciliationRules.tallyAlbum(local, remoteIndex, proxiedOriginals)

            // Keep the per-album answer, not just its contribution to the total.
            //
            // This loop already asked the drive about every album and already computed exactly the
            // numbers the Albums tab needs; until 28 Aug 2026 it added them to a running total and
            // discarded the detail, so the one place in the app that knew what OneDrive actually
            // holds told only the setup wizard, and the album rows went on quoting the ledger.
            cloudStatusDao.upsert(
                AlbumCloudStatusEntity(
                    albumName = album.name,
                    checkedAtEpochMillis = System.currentTimeMillis(),
                    verifiedFiles = forAlbum.backedUp.files,
                    missingFiles = forAlbum.outstanding.files,
                    // A failed listing is recorded rather than skipped: "asked and could not reach
                    // the drive" is a different thing to show than "never asked".
                    couldNotCheck = remoteIndex == null
                )
            )

            total += forAlbum
            onProgress(total)
        }

        Logger.i(
            TAG,
            "reconcile: ${total.backedUp.files} already in OneDrive, " +
                "${total.outstanding.files} outstanding, " +
                "${total.unchecked.files} in ${total.albumsUnchecked} albums that could not be checked"
        )
        total
    }

    private companion object {
        const val TAG = "Reconcile"
    }
}
