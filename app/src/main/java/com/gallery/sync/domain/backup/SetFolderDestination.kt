package com.gallery.sync.domain.backup

import com.gallery.sync.data.local.dao.BackupEntryDao
import com.gallery.sync.data.local.dao.FolderPreferenceDao
import com.gallery.sync.data.local.entity.FolderPreferenceEntity
import com.gallery.sync.data.local.media.MediaScanner
import javax.inject.Inject

/**
 * Records where a top-level folder's new uploads go (TASK-026), from wherever the user makes the
 * choice — the Settings list or the setup wizard.
 *
 * Also re-points the folder's not-yet-sent rows. A file's `location` is frozen when its row is
 * created, and rows are created as soon as the app scans, which is normally *before* the user has
 * answered anything; without this the files already queued would go to the old destination and the
 * choice would only ever apply to files found later. Nothing already uploaded is touched, so no
 * file's recorded history changes and nothing is re-uploaded.
 */
class SetFolderDestination @Inject constructor(
    private val scanner: MediaScanner,
    private val folderDao: FolderPreferenceDao,
    private val entryDao: BackupEntryDao
) {

    suspend operator fun invoke(folder: String, location: BackupLocation) =
        invoke(mapOf(folder to location))

    /** Several folders in one pass, so the device is scanned once rather than once per folder. */
    suspend operator fun invoke(choices: Map<String, BackupLocation>) {
        if (choices.isEmpty()) return
        folderDao.setPreferences(choices.map { (folder, location) -> FolderPreferenceEntity(folder, location) })

        val albumsByFolder = scanner.scanAlbums()
            .filter { it.topLevelFolder != null }
            .groupBy({ it.topLevelFolder!! }, { it.name })
        choices.forEach { (folder, location) ->
            val albums = albumsByFolder[folder].orEmpty()
            if (albums.isNotEmpty()) entryDao.retargetUnsent(albums, location)
        }
    }
}
