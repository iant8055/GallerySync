package com.gallery.sync.domain.backup

import androidx.room.withTransaction
import com.gallery.sync.data.local.GallerySyncDatabase
import com.gallery.sync.data.local.dao.AlbumCloudStatusDao
import com.gallery.sync.data.local.dao.AlbumPreferenceDao
import com.gallery.sync.data.local.dao.BackupEntryDao
import com.gallery.sync.data.local.entity.AlbumMode
import com.gallery.sync.data.local.entity.AlbumPreferenceEntity
import com.gallery.sync.data.local.media.MediaAccess
import com.gallery.sync.data.local.media.MediaScanRules
import com.gallery.sync.data.local.media.MediaScanner
import com.gallery.sync.data.local.settings.BackupSettings
import com.gallery.sync.di.IoDispatcher
import com.gallery.sync.util.Logger
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Merges an album the app has stored under more than one spelling of one folder. TASK-023.
 *
 * The scanner already gives every item in a folder one name. This handles what was stored before:
 * preference rows, cloud answers and ledger rows written under a spelling the folder no longer has,
 * or under a second spelling beside the first. See [AlbumIdentityRules] for what a merge does and
 * why it always ends Off.
 *
 * ### Call it before reading a mode
 *
 * Anything that acts on album modes calls [reconcile] first: uploading, optimising and Archive.
 * The order matters. The scanner renames items as soon as it runs, so between a scan and a merge a
 * new camera shot can already carry a name whose stored mode is Archive, set for the other spelling.
 * Acting in that window removes a file under a choice made for a different name. A guard at each
 * reader closes the window without relying on call order. It is cheap when nothing needs merging:
 * one scan and three small reads.
 */
@Singleton
class AlbumIdentityReconciler @Inject constructor(
    private val database: GallerySyncDatabase,
    private val scanner: MediaScanner,
    private val albumDao: AlbumPreferenceDao,
    private val cloudStatusDao: AlbumCloudStatusDao,
    private val entryDao: BackupEntryDao,
    private val settings: BackupSettings,
    @param:IoDispatcher private val dispatcher: CoroutineDispatcher
) {

    /** Workers and screens call this concurrently. Two merges of the same rows must not interleave. */
    private val lock = Mutex()

    /** Returns how many albums were merged. */
    suspend fun reconcile(): Int = withContext(dispatcher) {
        lock.withLock {
            // No scan means no idea what the folders are called now, so nothing is merged on a guess.
            // Stored spellings on their own can still be merged below, because that needs no scan.
            val deviceAlbums = if (scanner.access() == MediaAccess.NONE) {
                emptyList()
            } else {
                scanner.scanEverything()
                    .map { DeviceAlbum(it.album, MediaScanRules.folderKeyOf(it.relativePath, it.album)) }
                    .distinct()
            }

            val preferences = albumDao.all()
            val cloudNames = cloudStatusDao.albumNames()
            val ledgerNames = entryDao.distinctAlbums()

            // Only the ledger rows of albums that could be in a merge are loaded, so a large library
            // with nothing to merge does not read every row on every call.
            val allNames = preferences.map { it.albumName } + cloudNames + ledgerNames +
                deviceAlbums.map { it.name }
            val clashing = allNames.distinct()
                .groupBy(AlbumIdentityRules::foldCase)
                .filterValues { it.size > 1 }
                .keys
            val deviceNames = deviceAlbums.associateBy({ AlbumIdentityRules.foldCase(it.name) }, { it.name })
            val renamed = (preferences.map { it.albumName } + cloudNames + ledgerNames)
                .filter { name ->
                    val onDevice = deviceNames[AlbumIdentityRules.foldCase(name)]
                    onDevice != null && onDevice != name
                }
            val candidates = allNames.filter {
                AlbumIdentityRules.foldCase(it) in clashing || it in renamed
            }.distinct()
            if (candidates.isEmpty()) return@withLock 0

            val merges = AlbumIdentityRules.plan(
                deviceAlbums = deviceAlbums,
                preferences = preferences,
                cloudStatusNames = cloudNames,
                entries = entryDao.entriesForAlbums(ledgerNames.filter { it in candidates })
            )
            if (merges.isEmpty()) return@withLock 0

            database.withTransaction {
                for (merge in merges) {
                    albumDao.deleteAlbums(merge.spellings)
                    albumDao.setPreference(AlbumPreferenceEntity(merge.target, AlbumMode.OFF))
                    cloudStatusDao.deleteAlbums(merge.spellings)
                    entryDao.deleteForAlbums(merge.spellings)
                    merge.entries.chunked(BackupEngine.SQL_BATCH).forEach { entryDao.replaceAll(it) }
                }
            }

            // After the transaction: a warning for a merge that rolled back would describe something
            // that did not happen.
            val now = System.currentTimeMillis()
            settings.addAlbumMergeWarnings(merges.map { AlbumIdentityRules.warningFor(it, now) })

            merges.forEach { merge ->
                Logger.i(
                    TAG,
                    "merged ${merge.spellings} into '${merge.target}': modes were " +
                        "${merge.previousModes.ifEmpty { "none" }}, now OFF; " +
                        "${merge.entries.size} ledger rows"
                )
            }
            merges.size
        }
    }

    private companion object {
        const val TAG = "AlbumIdentity"
    }
}
