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
 * **Display only.** It writes [AlbumElsewhereStatusEntity] and nothing else: no ledger state, no mode, no file. Archive
 * still asks the Cloud live about each file before anything leaves the phone.
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

    suspend fun run() = withContext(dispatcher) {
        val prefs = settings.current()
        val groups = entryDao.sentElsewhere().groupBy { it.album to it.location }
        val written = HashSet<Pair<String, BackupLocation>>()

        for ((key, rows) in groups) {
            coroutineContext.ensureActive()
            val (album, location) = key
            val verifier = verifiers.of(location) ?: continue // Google Photos: nothing to ask.
            val listing = verifier.listAlbum(album, prefs.folderFor(location))
            val status = if (listing == null) {
                Logger.w(TAG, "check: could not list $album in $location")
                AlbumElsewhereStatusEntity(album, location, System.currentTimeMillis(), 0, 0, 0, couldNotCheck = true)
            } else {
                val tally = ElsewhereTally.of(rows, listing) { verifier.sizeOf(it) }
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

    suspend fun of(
        rows: List<SentElsewhereRow>,
        listing: Map<String, Long>,
        lookUp: suspend (remoteItemId: String) -> RemoteCheck
    ): Outcome {
        var verified = 0
        var missing = 0
        var unchecked = 0
        var lookups = 0
        for (row in rows) {
            val listed = listing[row.remoteItemId]
            if (listed != null) {
                if (listed == row.sizeBytes) verified++ else missing++
                continue
            }
            if (lookups >= MAX_LOOKUPS) {
                unchecked++
                continue
            }
            lookups++
            when (val answer = lookUp(row.remoteItemId)) {
                is RemoteCheck.Present -> if (answer.sizeBytes == row.sizeBytes) verified++ else missing++
                RemoteCheck.Gone -> missing++
                RemoteCheck.Unknown -> unchecked++
            }
        }
        return Outcome(verified, missing, unchecked)
    }
}
