package com.gallery.sync.domain.backup

import com.gallery.sync.data.local.dao.AlbumElsewhereStatusDao
import com.gallery.sync.data.local.dao.BackupEntryDao
import com.gallery.sync.data.local.dao.SentElsewhereRow
import com.gallery.sync.data.local.entity.AlbumElsewhereStatusEntity
import com.gallery.sync.data.local.settings.BackupSettings
import com.gallery.sync.data.local.settings.folderFor
import com.gallery.sync.di.IoDispatcher
import com.gallery.sync.domain.repository.CloudVerifiers
import com.gallery.sync.domain.repository.RemoteCheck
import com.gallery.sync.util.Logger
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.coroutineContext

/**
 * Asks every Cloud other than OneDrive that can answer whether it still holds what this phone sent it, album by album,
 * and keeps the answer for the Albums tab (Ian, 16 Nov 2026: *"every Cloud that can be checked - per folder like
 * OneDrive"*). Google Photos cannot be asked and keeps its "sent to" line.
 *
 * It writes [AlbumElsewhereStatusEntity] and nothing else: no ledger state, no mode, no file. From Rescan it also hands
 * each album's definitely-missing files to `onMissing` (Ian, 22 Nov 2026, `RequeueMissingFromCloud`), which can only
 * queue them to be sent again. Archive still asks the Cloud live about each file before anything leaves the phone.
 *
 * Not part of the wizard and not part of `ReconcileWithCloud`, which the wizard shares.
 */
@Singleton
class CheckOtherClouds @Inject constructor(
    private val entryDao: BackupEntryDao,
    private val statusDao: AlbumElsewhereStatusDao,
    private val verifiers: CloudVerifiers,
    private val settings: BackupSettings,
    @param:IoDispatcher private val dispatcher: CoroutineDispatcher
) {

    suspend fun run(
        onMissing: suspend (album: String, rowIds: List<String>) -> Unit = { _, _ -> }
    ) = withContext(dispatcher) {
        val prefs = settings.current()
        val groups = entryDao.sentElsewhere().groupBy { it.album to it.location }
        val written = HashSet<Pair<String, BackupLocation>>()

        for ((key, rows) in groups) {
            coroutineContext.ensureActive()
            val (album, location) = key
            val verifier = verifiers.of(location) ?: continue // Google Photos: nothing to ask.
            val listing = verifier.listAlbum(album, prefs.folderFor(location))
            var missingIds = emptyList<String>()
            val status = if (listing == null) {
                Logger.w(TAG, "check: could not list $album in $location")
                AlbumElsewhereStatusEntity(album, location, System.currentTimeMillis(), 0, 0, 0, couldNotCheck = true)
            } else {
                val (tally, missing) = ElsewhereTally.withMissing(rows, listing) { verifier.sizeOf(it) }
                missingIds = missing
                AlbumElsewhereStatusEntity(
                    albumName = album,
                    location = location,
                    checkedAtEpochMillis = System.currentTimeMillis(),
                    verifiedFiles = tally.verified,
                    missingFiles = tally.missing,
                    uncheckedFiles = tally.unchecked,
                    couldNotCheck = false
                )
            }
            statusDao.upsert(status)
            written += key
            if (missingIds.isNotEmpty()) onMissing(album, missingIds)
        }

        // An answer about files no longer sent there (the album emptied, or the Cloud signed out and forgotten) says
        // nothing true any more.
        statusDao.delete(statusDao.all().filter { (it.albumName to it.location) !in written })
    }

    private companion object {
        const val TAG = "CheckOtherClouds"
    }
}

/**
 * The comparison, kept apart so it can be tested without a Cloud. A file is verified when the Cloud holds **the object
 * recorded at upload at exactly the local size** — the bar Archive uses. A file the listing does not show is asked
 * about by id, live, because the folder may have been changed since it was sent (files already sent stay where they
 * went); at most [MAX_LOOKUPS] per album, so a folder deleted by hand cannot turn one check into thousands of requests.
 * A file not answered for is unchecked, never missing: failing to ask is not evidence of absence.
 */
object ElsewhereTally {

    const val MAX_LOOKUPS = 25

    data class Outcome(val verified: Int, val missing: Int, val unchecked: Int)

    private enum class Answer { VERIFIED, MISSING, UNCHECKED }

    suspend fun of(
        rows: List<SentElsewhereRow>,
        listing: Map<String, Long>,
        lookUp: suspend (remoteItemId: String) -> RemoteCheck
    ): Outcome = outcomeOf(answer(rows, listing, lookUp).values)

    /** [of], plus the ledger ids of the rows it counted missing: what Rescan may queue again. Never an unchecked one. */
    suspend fun withMissing(
        rows: List<SentElsewhereRow>,
        listing: Map<String, Long>,
        lookUp: suspend (remoteItemId: String) -> RemoteCheck
    ): Pair<Outcome, List<String>> {
        val answers = answer(rows, listing, lookUp)
        return outcomeOf(answers.values) to answers.filterValues { it == Answer.MISSING }.keys.map { it.id }
    }

    private fun outcomeOf(answers: Collection<Answer>) = Outcome(
        verified = answers.count { it == Answer.VERIFIED },
        missing = answers.count { it == Answer.MISSING },
        unchecked = answers.count { it == Answer.UNCHECKED }
    )

    private suspend fun answer(
        rows: List<SentElsewhereRow>,
        listing: Map<String, Long>,
        lookUp: suspend (remoteItemId: String) -> RemoteCheck
    ): Map<SentElsewhereRow, Answer> {
        var lookups = 0
        val out = LinkedHashMap<SentElsewhereRow, Answer>()
        for (row in rows) {
            val listed = listing[row.remoteItemId]
            out[row] = when {
                listed != null -> if (listed == row.sizeBytes) Answer.VERIFIED else Answer.MISSING
                lookups >= MAX_LOOKUPS -> Answer.UNCHECKED
                else -> {
                    lookups++
                    when (val answer = lookUp(row.remoteItemId)) {
                        is RemoteCheck.Present -> if (answer.sizeBytes == row.sizeBytes) Answer.VERIFIED else Answer.MISSING
                        RemoteCheck.Gone -> Answer.MISSING
                        RemoteCheck.Unknown -> Answer.UNCHECKED
                    }
                }
            }
        }
        return out
    }
}
