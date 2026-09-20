package com.gallery.sync.domain.backup

import com.gallery.sync.data.local.dao.BackupEntryDao
import com.gallery.sync.data.local.dao.UnsentDepartureDao
import com.gallery.sync.data.local.entity.CloudCopyDecision
import com.gallery.sync.data.local.media.MediaAccess
import com.gallery.sync.data.local.media.MediaScanner
import com.gallery.sync.data.local.media.RestoredAlbum
import com.gallery.sync.data.local.settings.BackupSettings
import com.gallery.sync.di.IoDispatcher
import com.gallery.sync.domain.model.DataResult
import com.gallery.sync.domain.repository.OneDriveDeletionRepository
import com.gallery.sync.util.Logger
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/** What a deletion pass did. */
data class DeletionOutcome(
    val deleted: Int = 0,
    val failed: Int = 0,
    /** Skipped because the file turned out to be back on the phone after all. */
    val cameBack: Int = 0
)

/**
 * Works out what the "files deleted from phone" window has to ask, and settles the answers. It removes
 * OneDrive copies only ever on a yes.
 *
 * **The removal is the highest-risk feature in the product.** Everything else here either adds a file
 * or removes one it can prove is safe elsewhere. This removes the drive's copy, and the local copy is
 * already gone by definition — there is nothing left to fall back to except the recycle bin.
 *
 * ### What is offered, as Ian defined it (19 Sept 2026)
 *
 * **All** deleted files, not only the ones this app backed up. They fall in two parts, and each part
 * has its own question:
 *
 * - **A copy is in OneDrive**, whether this app put it there or not. What should happen to the copy:
 *   keep it, or delete it.
 * - **No copy could be found**, and the file is in the phone's trash. What should happen to the file:
 *   stay in the trash, or be backed up. (That is [BackupEngine.backUpFromTrash].)
 *
 * ### Guards on the removal, each load-bearing
 *
 * 1. **The policy must be [CloudDeletionPolicy.ASK].** The default is LEAVE and there is no
 *    automatic mode; [CloudDeletionPolicy] records why one cannot be made safe. The whole window is
 *    Ask-only, including the backing-up half.
 * 2. **Nothing already settled, and nothing Archive took off the phone on purpose** (the query's
 *    `cloudDecision IS NULL`), and **nothing when a mass of files went missing at once**
 *    ([MassAbsence]). There is no waiting period: the window only shows what is new since it last
 *    appeared.
 * 3. **A file whose name is still in its folder is not a deletion** ([EditedInPlace]), and **a fresh
 *    scan immediately before deleting.** A file that has come back is dropped from the batch however
 *    recently the list was drawn.
 * 4. **An explicit confirmation**, which lives in the UI — which is why [delete] takes an
 *    already-approved list rather than deciding for itself what to remove.
 *
 * ### What it leaves behind
 *
 * Graph's DELETE is a soft delete: the file lands in the OneDrive recycle bin, which the user empties
 * themselves. This app never empties it and offers no control that does.
 */
@Singleton
class SyncDeletionsToCloud @Inject constructor(
    private val entryDao: BackupEntryDao,
    private val unsentDao: UnsentDepartureDao,
    private val scanner: MediaScanner,
    private val settings: BackupSettings,
    private val deletionRepository: OneDriveDeletionRepository,
    private val engine: BackupEngine,
    @param:IoDispatcher private val dispatcher: CoroutineDispatcher
) {

    /**
     * What the window has to ask, or nothing at all when the policy forbids it or there is nothing new.
     *
     * Returns nothing under [CloudDeletionPolicy.LEAVE] — not a list the UI then has to remember to
     * hide. The safest reading of the setting is the one that produces no offer.
     *
     * [newerThan] is the newest departure the window has already shown. When nothing undecided is
     * newer than that, this returns before it asks OneDrive anything, so opening the app with nothing
     * new costs no network.
     *
     * [onAskingOneDrive] is called just before OneDrive is asked, which is the only slow part: listing
     * the folder of a large album is many requests. It lets the screen say what it is waiting for.
     */
    suspend fun offer(
        newerThan: Long = 0L,
        onAskingOneDrive: () -> Unit = {}
    ): DeletedFilesOffer = withContext(dispatcher) {
        val prefs = settings.current()
        if (prefs.cloudDeletionPolicy != CloudDeletionPolicy.ASK) return@withContext DeletedFilesOffer()

        // A scan that cannot be trusted is no answer, so it offers nothing rather than everything.
        if (scanner.access() != MediaAccess.FULL) return@withContext DeletedFilesOffer()
        val everything = scanner.scanEverything()
        if (everything.isEmpty()) return@withContext DeletedFilesOffer()

        val sent = entryDao.cloudDeletionCandidates()
        val unsent = unsentDao.all()
        if (sent.isEmpty() && unsent.isEmpty()) return@withContext DeletedFilesOffer()

        // A scan that came back short, not the user, is the likelier cause of a mass of missing files.
        // Measured against everything the app knows about, sent or not.
        val known = entryDao.totalCount() + unsent.size
        if (MassAbsence.looksLikeABadScan(sent.size + unsent.size, known)) {
            Logger.w(TAG, "${sent.size + unsent.size} files look missing at once, which reads as a bad scan: offering none")
            return@withContext DeletedFilesOffer()
        }

        // A file whose name is still in its folder was edited, not deleted (see [EditedInPlace]), and
        // only the phone can say what is still there.
        val here = EditedInPlace.keysOf(everything.map { it.album to it.displayName })
        val sentOffer = sent
            .filterNot { EditedInPlace.isStillHere(it.album, it.displayName, here) }
            .map { DeletedFile.of(it) }

        // Only a file in the phone's trash can still be saved, and only those are asked about. One
        // that is in neither the trash nor the phone was deleted outright: nothing to decide.
        val trashed = scanner.trashedIds()
        forgetStale(unsent, trashed)
        val inTrash = unsent
            .filterNot { EditedInPlace.isStillHere(it.album, it.displayName, here) }
            .filter { it.mediaStoreId in trashed }

        val newest = maxOf(
            sentOffer.maxOfOrNull { it.departedAtEpochMillis } ?: 0L,
            inTrash.maxOfOrNull { it.goneSinceEpochMillis } ?: 0L
        )
        if (newest <= newerThan) return@withContext DeletedFilesOffer()

        val lookup = if (inTrash.isEmpty()) {
            CloudLookup(emptyMap(), emptySet())
        } else {
            onAskingOneDrive()
            engine.cloudCopiesOf(inTrash.map { DeletedFile.of(it) })
        }

        val foundElsewhere = inTrash.filter { it.id in lookup.found }
            .map { DeletedFile.of(it, remoteItemId = lookup.found[it.id]) }
        val notThere = inTrash.filter { it.id !in lookup.found && it.id !in lookup.unknown }
            .map { DeletedFile.of(it) }

        Logger.d(
            TAG,
            "offer: ${sentOffer.size} sent, ${foundElsewhere.size} found in OneDrive, " +
                "${notThere.size} with no copy, ${lookup.unknown.size} could not be placed"
        )
        DeletedFilesOffer(
            inCloud = sentOffer + foundElsewhere,
            notInCloud = notThere,
            complete = lookup.unknown.isEmpty()
        )
    }

    /**
     * Drops records of files that are in neither the phone nor its trash and have been for a while:
     * nothing can be done about them. Only when the trash query returned something, because an empty
     * answer is also what a failed query gives and must not be read as "the trash is empty".
     */
    private suspend fun forgetStale(
        unsent: List<com.gallery.sync.data.local.entity.UnsentDepartureEntity>,
        trashed: Set<Long>
    ) {
        if (trashed.isEmpty()) return
        val cutoff = System.currentTimeMillis() - STALE_MILLIS
        val stale = unsent.filter { it.mediaStoreId !in trashed && it.goneSinceEpochMillis < cutoff }.map { it.id }
        stale.chunked(CHUNK).forEach { unsentDao.forget(it) }
    }

    /**
     * Settles these files as *left alone*: a file's OneDrive copy stays, or a file stays in the trash.
     * They are left out of the window from now on, until they are back on the phone and gone again.
     * Bookkeeping only: nothing is removed or changed anywhere.
     */
    suspend fun keep(files: List<DeletedFile>) = withContext(dispatcher) {
        files.filter { it.origin == DepartureOrigin.SENT }.map { it.id }.chunked(CHUNK)
            .forEach { entryDao.setCloudDecision(it, CloudCopyDecision.KEPT) }
        files.filter { it.origin == DepartureOrigin.NEVER_SENT }.map { it.id }.chunked(CHUNK)
            .forEach { unsentDao.forget(it) }
    }

    /**
     * Deletes the cloud copies of [approved], having re-checked that each is still gone.
     *
     * [approved] must be a list a person has just confirmed. Nothing here re-derives what to delete;
     * being handed the list is what makes the consent specific to these files rather than to the
     * idea of deletion.
     */
    suspend fun delete(approved: List<DeletedFile>): DeletionOutcome =
        withContext(dispatcher) {
            if (approved.isEmpty()) return@withContext DeletionOutcome()

            if (settings.current().cloudDeletionPolicy != CloudDeletionPolicy.ASK) {
                Logger.w(TAG, "refusing to delete: policy is not ASK")
                return@withContext DeletionOutcome()
            }

            // Only the device can say whether a file has come back, and a scan it cannot trust is
            // no answer. Both refusals below are deliberate dead ends rather than best guesses.
            if (scanner.access() != MediaAccess.FULL) {
                Logger.w(TAG, "refusing to delete: media access is not full, cannot confirm absence")
                return@withContext DeletionOutcome()
            }
            val everything = scanner.scanEverything()
            if (everything.isEmpty()) {
                Logger.w(TAG, "refusing to delete: the scan returned nothing at all")
                return@withContext DeletionOutcome()
            }
            // Through RestoredAlbum.contentSignature so a file the user fetched back — which carries
            // `_restored` in its name — is recognised as being here. This is the last check before a
            // cloud copy goes to the recycle bin, so the rename must not blind it.
            val presentContent = everything.mapTo(HashSet()) {
                RestoredAlbum.contentSignature(it.displayName, it.sizeBytes)
            }
            // And by folder and name alone, for a photo edited in place since the list was drawn:
            // its size changed, so the check above cannot see it, but it is not a deletion.
            val presentNames = EditedInPlace.keysOf(everything.map { it.album to it.displayName })

            var deleted = 0
            var failed = 0
            var cameBack = 0

            for (file in approved) {
                val signature = RestoredAlbum.contentSignature(file.displayName, file.sizeBytes)
                if (signature in presentContent ||
                    EditedInPlace.isStillHere(file.album, file.displayName, presentNames)
                ) {
                    Logger.i(TAG, "not deleting ${file.displayName}: it is back on the phone")
                    cameBack++
                    continue
                }

                // No id, no delete. An empty id is never sent to the drive.
                val remoteId = file.remoteItemId
                if (remoteId.isNullOrBlank()) {
                    Logger.w(TAG, "not deleting ${file.displayName}: no OneDrive item id is known")
                    failed++
                    continue
                }

                when (deletionRepository.moveToRecycleBin(remoteId)) {
                    is DataResult.Success -> {
                        // The drive has changed, so what the window remembered of this album is stale.
                        engine.forgetCachedRemoteIndex(file.album)
                        // Now on neither the phone nor the drive, so the record describes nothing.
                        // Forgetting it is bookkeeping and removes no file anywhere.
                        when (file.origin) {
                            DepartureOrigin.SENT -> entryDao.forget(file.id)
                            DepartureOrigin.NEVER_SENT -> unsentDao.forget(listOf(file.id))
                        }
                        deleted++
                    }

                    is DataResult.Failure -> failed++
                }
            }

            Logger.i(TAG, "deletion pass: $deleted removed, $failed failed, $cameBack came back")
            DeletionOutcome(deleted = deleted, failed = failed, cameBack = cameBack)
        }

    private companion object {
        const val TAG = "DeletionSync"

        /** SQLite binds one variable per id and stops at 999, so lists are written in chunks. */
        const val CHUNK = 500

        /** A departure that is in neither the phone nor its trash after this long is dropped. */
        const val STALE_MILLIS = 45L * 24 * 60 * 60 * 1000
    }
}
