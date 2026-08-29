package com.gallery.sync.domain.backup

import com.gallery.sync.data.local.dao.AlbumPreferenceDao
import com.gallery.sync.data.local.settings.BackupSettings
import com.gallery.sync.data.local.entity.AlbumPreferenceEntity
import com.gallery.sync.data.local.media.MediaScanner
import com.gallery.sync.di.IoDispatcher
import com.gallery.sync.util.Logger
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Applies Gate 2 across every album currently in scope.
 *
 * This is a bulk version of what the Album Modes screen does one row at a time, and nothing more.
 * The alternative — a separate notion of "files that were already here" — would mean new state, a
 * migration, and two rules where users expect one. A mode is a standing instruction covering past
 * and future files alike, which is what CLAUDE.md's consent rule already relies on.
 *
 * ### Only albums the user can see
 *
 * Scoped, so a bulk choice never reaches a folder outside Gate 1. Someone who granted DCIM and chose
 * "back up everything" meant everything *they picked*, not every app cache on the device.
 *
 * ### It is an action, not a setting
 *
 * Running it again re-applies it, so nothing calls it twice on its own. Re-running setup offers the
 * choice again but must not silently repeat the last answer just because the user walked through
 * the flow — see the re-run rules in TASK-014.
 */
@Singleton
class ApplyLibraryChoice @Inject constructor(
    private val scanner: MediaScanner,
    private val albumDao: AlbumPreferenceDao,
    private val settings: BackupSettings,
    @param:IoDispatcher private val dispatcher: CoroutineDispatcher
) {

    /**
     * Sets every in-scope album to the mode [choice] implies.
     *
     * Returns how many albums were changed. [LibraryChoice.CHOOSE_PER_ALBUM] changes nothing and
     * returns zero — it is the absence of a bulk action, not a bulk action that sets Off, which
     * would overwrite choices a returning user had already made.
     */
    suspend fun apply(choice: LibraryChoice): Int = withContext(dispatcher) {
        val mode = choice.mode ?: run {
            Logger.i(TAG, "per-album chosen; leaving every album as it is")
            return@withContext 0
        }

        val albums = scanner.scanAlbums().map { it.name }
        if (albums.isEmpty()) {
            Logger.w(TAG, "nothing in scope; no modes to set")
            return@withContext 0
        }

        // REPLACE, which is correct here and only here: this is an explicit bulk instruction, so
        // overwriting an earlier per-album choice is what the user just asked for.
        albumDao.setPreferences(albums.map { AlbumPreferenceEntity(it, mode) })

        // The cutoff, which is what tells BACK_UP_AND_OPTIMISE_NEW apart from BACK_UP_AND_FREE_SPACE.
        // Both set every album to Sync; only the first leaves what is already in OneDrive at full
        // size, and it does that by recording the moment rather than by a mode - see OptimiseCutoff.
        //
        // Written for every choice, not only that one, so a user changing their mind gets the new
        // answer rather than the old cutoff quietly surviving.
        settings.setOptimiseCutoff(choice.cutoffFor(System.currentTimeMillis()))

        Logger.i(TAG, "set ${albums.size} albums to $mode")
        albums.size
    }

    private companion object {
        const val TAG = "LibraryChoice"
    }
}
