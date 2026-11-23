package com.gallery.sync.domain.backup

import com.gallery.sync.data.local.dao.AlbumPreferenceDao
import com.gallery.sync.data.local.dao.BackupEntryDao
import com.gallery.sync.data.local.dao.FolderPreferenceDao
import com.gallery.sync.data.local.dao.ReverifyRow
import com.gallery.sync.data.local.settings.BackupSettings
import com.gallery.sync.data.local.settings.folderFor
import com.gallery.sync.domain.repository.CloudVerifiers
import com.gallery.sync.util.Logger
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Files recorded as sent that a Cloud has been asked about and **definitely** no longer holds (Ian, 22 Nov 2026).
 *
 * **Flagged, never re-sent on its own.** Ian: *"They may have deleted or Restored those files purposefully and may not
 * want them backed up automatically"*. So the Albums tab's check only counts them ([missingFromOneDrive],
 * [missingElsewhere]) and the album says so; sending them again is the user's choice, made with **Resend** on the
 * album's own page ([resend]), which asks the Clouds afresh at that moment and only then writes anything.
 *
 * Resending only adds work: it puts a copy back and removes nothing anywhere. Its limits keep it to files whose
 * absence is proven and whose re-sending is honest:
 *
 * - **A complete answer only.** A failed or partial listing, an unknown size, or a lookup past the cap is not evidence
 *   of absence, and leaves the file as it was.
 * - **Still on the phone, never optimized.** An optimized file's full-size original existed only in the Cloud; sending
 *   the shrunk copy would record it as backed up with the original gone. It is not counted and cannot be resent; its
 *   album line keeps saying it is not verified. The query and the update both refuse such rows.
 * - **Only albums that upload** (Backup, Sync, Archive) can resend; an Off album sends nothing, as with Retry failed.
 *
 * A resent file goes where its folder goes now (`FolderDestination`), like any new upload.
 */
@Singleton
class RequeueMissingFromCloud @Inject constructor(
    private val entryDao: BackupEntryDao,
    private val albumDao: AlbumPreferenceDao,
    private val folderDao: FolderPreferenceDao,
    private val settings: BackupSettings,
    private val engine: BackupEngine,
    private val verifiers: CloudVerifiers
) {

    /** OneDrive's album listing, as the check got it: the sent files it proves gone. Writes nothing. */
    suspend fun missingFromOneDrive(album: String, remoteIndex: Map<String, RemoteFileRef>?, complete: Boolean): List<ReverifyRow> {
        if (remoteIndex == null || !complete) return emptyList()
        val rows = entryDao.uploadedOnPhone(album).filter { it.location == BackupLocation.ONEDRIVE }
        return MissingFromCloud.inOneDrive(rows, remoteIndex)
    }

    /** The rows another Cloud's check counted missing, kept to those that could be resent. Writes nothing. */
    suspend fun missingElsewhere(album: String, rowIds: Collection<String>): List<ReverifyRow> {
        if (rowIds.isEmpty()) return emptyList()
        val wanted = rowIds.toSet()
        return entryDao.uploadedOnPhone(album).filter { it.id in wanted && it.location != BackupLocation.ONEDRIVE }
    }

    /**
     * **Resend**, pressed on the album's page with [selected] files: asks every Cloud this album sent to again, now, and
     * puts back in the queue those of them that are definitely gone. Returns how many. A selected file the Cloud turns
     * out to hold is left alone, and so is everything for an album that does not upload.
     */
    suspend fun resend(album: String, selected: Set<String>): Int {
        if (selected.isEmpty()) return 0
        if (albumDao.modeOrNull(album)?.uploads != true) return 0
        var partial = false
        val missing = missingFromOneDrive(album, engine.remoteIndexFor(album) { partial = true }, !partial).toMutableList()

        val prefs = settings.current()
        entryDao.sentElsewhere().filter { it.album == album }.groupBy { it.location }.forEach { (location, rows) ->
            val verifier = verifiers.of(location) ?: return@forEach // Google Photos cannot be asked.
            val listing = verifier.listAlbum(album, prefs.folderFor(location)) ?: return@forEach
            val (_, ids) = ElsewhereTally.withMissing(rows, listing) { verifier.sizeOf(it) }
            missing += missingElsewhere(album, ids)
        }
        return requeue(album, missing.filter { it.id in selected })
    }

    private suspend fun requeue(album: String, rows: List<ReverifyRow>): Int {
        if (rows.isEmpty()) return 0
        val chosen = folderDao.all().orEmpty().associate { it.folderName to it.backupLocation }
        val fallback = settings.current().backupLocation
        var n = 0
        rows.distinctBy { it.id }
            .groupBy { FolderDestination.resolve(it.relativePath, chosen, fallback) }
            .forEach { (location, inLocation) ->
                inLocation.map { it.id }.chunked(500).forEach { n += entryDao.requeueMissingFromCloud(it, location) }
            }
        Logger.i(TAG, "resend: $n file(s) in $album no longer in the Cloud, queued to send again")
        return n
    }

    private companion object {
        const val TAG = "RequeueMissing"
    }
}

/** The OneDrive comparison, kept apart so it can be tested without a drive. */
object MissingFromCloud {

    /**
     * Rows OneDrive's listing proves absent: no entry with the id recorded at upload, and none with the file's name, at
     * the recorded size. An entry the listing gave no size for is unknown, and unknown is not missing.
     */
    fun inOneDrive(rows: List<ReverifyRow>, remoteIndex: Map<String, RemoteFileRef>): List<ReverifyRow> {
        val byId = remoteIndex.values.associateBy { it.id }
        return rows.filter { row ->
            val candidates = listOfNotNull(row.remoteItemId?.let { byId[it] }, remoteIndex[row.displayName])
            candidates.none { it.sizeBytes == row.sizeBytes || it.sizeBytes == null }
        }
    }
}
