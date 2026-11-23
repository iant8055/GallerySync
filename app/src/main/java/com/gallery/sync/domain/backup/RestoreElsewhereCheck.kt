package com.gallery.sync.domain.backup

import com.gallery.sync.data.local.dao.SentElsewhereRow
import com.gallery.sync.data.local.entity.BackupEntryEntity
import com.gallery.sync.data.local.settings.BackupSettings
import com.gallery.sync.data.local.settings.folderFor
import com.gallery.sync.di.IoDispatcher
import com.gallery.sync.domain.repository.CloudVerifiers
import com.gallery.sync.util.Logger
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.coroutineContext

/**
 * The Restore tab's question to every Cloud but OneDrive: which of the files it offers are definitely no longer there
 * (Ian, 23 Nov 2026: *"yes - check Dropbox and pCloud too"*, after the tab listed folders those accounts no longer held).
 * [RestoreStillInOneDrive] answers the same question for OneDrive.
 *
 * Each album's folder is listed once, by the same [com.gallery.sync.domain.repository.CloudVerifier] the Albums tab
 * uses, and judged by [ElsewhereTally]: the object recorded at upload at exactly the local size, a file the listing does
 * not show asked about by id. Its cap is higher here, [MAX_LOOKUPS] per album: nothing from this check is stored, so
 * the Albums tab's 25 would ask about the same 25 files on every Refresh and never reach the rest.
 *
 * Returns ledger ids that a Cloud answered for and does not hold. A Cloud that could not be asked, a file past the cap,
 * a file with no recorded id, and Google Photos (which cannot be asked) are all kept: failing to ask is not evidence of
 * absence. Nothing is written.
 */
@Singleton
class RestoreElsewhereCheck @Inject constructor(
    private val verifiers: CloudVerifiers,
    private val settings: BackupSettings,
    @param:IoDispatcher private val dispatcher: CoroutineDispatcher
) {

    suspend fun gone(entries: List<BackupEntryEntity>): Set<String> = withContext(dispatcher) {
        val prefs = settings.current()
        val gone = HashSet<String>()
        val groups = entries
            .filter { it.location != BackupLocation.ONEDRIVE && !it.remoteItemId.isNullOrEmpty() }
            .groupBy { it.album to it.location }
        for ((key, rows) in groups) {
            coroutineContext.ensureActive()
            val (album, location) = key
            val verifier = verifiers.of(location) ?: continue
            val listing = verifier.listAlbum(album, prefs.folderFor(location))
            if (listing == null) {
                Logger.w(TAG, "restore check: could not list $album in $location")
                continue
            }
            val sent = rows.map { SentElsewhereRow(it.album, it.location, it.remoteItemId!!, it.sizeBytes, it.id) }
            val (_, missing) = ElsewhereTally.withMissing(sent, listing, MAX_LOOKUPS) { verifier.sizeOf(it) }
            gone += missing
            Logger.i(TAG, "restore check: $album in $location, ${rows.size} offered, ${missing.size} no longer there")
        }
        gone
    }

    companion object {
        const val TAG = "RestoreElsewhereCheck"
        const val MAX_LOOKUPS = 500
    }
}
