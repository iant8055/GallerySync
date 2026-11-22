package com.gallery.sync.domain.backup

import com.gallery.sync.data.local.dao.BackupEntryDao
import com.gallery.sync.data.local.dao.FolderPreferenceDao
import com.gallery.sync.data.local.entity.FolderPreferenceEntity
import com.gallery.sync.data.local.media.MediaScanner
import com.gallery.sync.data.local.media.mainPath
import javax.inject.Inject

/**
 * Records where a folder's new uploads go (TASK-026), from wherever the user makes the choice — the Settings list or
 * the setup wizard. A folder is a top-level one (`DCIM`) or, from Settings, one inside it (`DCIM/Funny stuff`); the
 * most specific choice wins, see [FolderDestination].
 *
 * Also re-points the not-yet-sent rows that the changed choices now govern. A file's `location` is frozen when its row
 * is created, and rows are created as soon as the app scans, which is normally *before* the user has answered
 * anything; without this the files already queued would go to the old destination and the choice would only ever
 * apply to files found later. Only rows whose governing folder is one of the folders just chosen move: choosing for
 * `DCIM` leaves `DCIM/Funny stuff`'s rows alone when that has a choice of its own. Nothing already uploaded is
 * touched, so no file's recorded history changes and nothing is re-uploaded.
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

        val keys = folderDao.all().orEmpty().map { it.folderName }.toSet() + choices.keys
        // A row made before RELATIVE_PATH was recorded goes by where its album is on the phone now.
        var pathOfAlbum: Map<String, String?>? = null

        val moves = HashMap<BackupLocation, MutableList<String>>()
        for (row in entryDao.unsentRoutes()) {
            val path = row.relativePath ?: run {
                val known = pathOfAlbum ?: scanner.scanAlbums().associate { it.name to it.mainPath() }
                    .also { pathOfAlbum = it }
                known[row.album]
            }
            val key = FolderDestination.routeOf(path, keys) ?: continue
            val location = choices[key]
                ?: choices.entries.firstOrNull { it.key.equals(key, ignoreCase = true) }?.value
                ?: continue
            if (location != row.location) moves.getOrPut(location) { mutableListOf() } += row.id
        }
        moves.forEach { (location, ids) -> ids.chunked(500).forEach { entryDao.retargetUnsentRows(it, location) } }
    }
}
