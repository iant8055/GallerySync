package com.gallery.sync.domain.backup

import com.gallery.sync.data.local.dao.BackupEntryDao
import com.gallery.sync.data.local.entity.BackupEntryEntity
import com.gallery.sync.data.local.media.MediaAccess
import com.gallery.sync.data.local.media.MediaScanner
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
 * Removes OneDrive copies of files the user deleted from their phone — and only ever on a yes.
 *
 * **The highest-risk feature in the product.** Everything else here either adds a file or removes
 * one it can prove is safe elsewhere. This removes the drive's copy, and the local copy is already
 * gone by definition — there is nothing left to fall back to except the recycle bin.
 *
 * ### Four guards, each load-bearing
 *
 * 1. **The policy must be [CloudDeletionPolicy.ASK].** The default is LEAVE and there is no
 *    automatic mode; [CloudDeletionPolicy] records why one cannot be made safe.
 * 2. **The grace period**, applied in the query. "Never infers deletion from absence alone" —
 *    absence that persists for days is a different claim from absence noticed once.
 * 3. **A fresh scan immediately before deleting.** A file that has come back is dropped from the
 *    batch however recently the list was drawn. This is the mirror of the removal re-check: the
 *    ledger records what was true once, and the operation is when that gets tested.
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
    private val scanner: MediaScanner,
    private val settings: BackupSettings,
    private val deletionRepository: OneDriveDeletionRepository,
    @param:IoDispatcher private val dispatcher: CoroutineDispatcher
) {

    /**
     * Files whose cloud copy may be offered for deletion, or empty when the policy forbids it.
     *
     * Returns nothing at all under [CloudDeletionPolicy.LEAVE] — not a list the UI then has to
     * remember to hide. The safest reading of the setting is the one that produces no candidates.
     */
    suspend fun candidates(): List<BackupEntryEntity> = withContext(dispatcher) {
        val prefs = settings.current()
        if (prefs.cloudDeletionPolicy != CloudDeletionPolicy.ASK) return@withContext emptyList()

        val cutoff = System.currentTimeMillis() - prefs.cloudDeletionGraceDays * MILLIS_PER_DAY

        entryDao.cloudDeletionCandidates(missingBefore = cutoff)
            .also { Logger.d(TAG, "${it.size} cloud copies could be offered for deletion") }
    }

    /**
     * Deletes the cloud copies of [approved], having re-checked that each is still gone.
     *
     * [approved] must be a list a person has just confirmed. Nothing here re-derives what to delete;
     * being handed the list is what makes the consent specific to these files rather than to the
     * idea of deletion.
     */
    suspend fun delete(approved: List<BackupEntryEntity>): DeletionOutcome =
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
            val presentContent = everything.mapTo(HashSet()) { "${it.displayName}|${it.sizeBytes}" }

            var deleted = 0
            var failed = 0
            var cameBack = 0

            for (entry in approved) {
                if ("${entry.displayName}|${entry.sizeBytes}" in presentContent) {
                    Logger.i(TAG, "not deleting ${entry.displayName}: it is back on the phone")
                    cameBack++
                    continue
                }

                when (deletionRepository.moveToRecycleBin(entry.remoteItemId.orEmpty())) {
                    is DataResult.Success -> {
                        // Now on neither the phone nor the drive, so the row describes nothing.
                        // Forgetting it is bookkeeping and removes no file anywhere.
                        entryDao.forget(entry.id)
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
        const val MILLIS_PER_DAY = 24L * 60 * 60 * 1000
    }
}
