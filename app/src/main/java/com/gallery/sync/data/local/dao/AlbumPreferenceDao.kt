package com.gallery.sync.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.gallery.sync.data.local.entity.AlbumMode
import com.gallery.sync.data.local.entity.AlbumPreferenceEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AlbumPreferenceDao {

    /** `REPLACE` here is correct: this table holds exactly the user's latest choice per album. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun setPreference(preference: AlbumPreferenceEntity)

    /**
     * Sets many at once, for "back up everything" / "back up nothing".
     *
     * `REPLACE`, so this overwrites whatever the user chose before — which is right for an explicit
     * bulk action and wrong for anything else. To record an album without touching a choice already
     * made, use [insertIfNew].
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun setPreferences(preferences: List<AlbumPreferenceEntity>)

    /**
     * Records albums the scanner found, without disturbing any choice already made.
     *
     * `IGNORE` is the entire point: this runs on every scan, and `REPLACE` here would reset the
     * user's modes to the default each time — silently switching off albums they had turned on, or
     * re-arming ones they had turned off.
     *
     * Called by the engine rather than the UI. The upload gate is opt-in, so an album only becomes
     * eligible once it has a row in a mode other than Off; seeding here means a headless run can
     * tell "an album nobody has chosen" from "an album that does not exist yet" without waiting for
     * someone to open the album screen.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfNew(preferences: List<AlbumPreferenceEntity>)

    @Query("SELECT * FROM album_preferences")
    fun observeAll(): Flow<List<AlbumPreferenceEntity>>

    @Query("SELECT * FROM album_preferences")
    suspend fun all(): List<AlbumPreferenceEntity>

    /**
     * Albums the user switched off.
     *
     * Absence from this list does **not** mean the album is backed up — an album with no row has
     * not been chosen either way, and is not eligible. See [BackupEntryDao.nextPending] for the gate
     * that decides.
     */
    @Query("SELECT albumName FROM album_preferences WHERE mode = 'OFF'")
    suspend fun disabledAlbums(): List<String>

    /** Albums in a given mode, for the space-management work that only applies to some of them. */
    @Query("SELECT albumName FROM album_preferences WHERE mode = :mode")
    suspend fun albumsInMode(mode: AlbumMode): List<String>

    /** The album's chosen mode, or null when the user has never touched it. */
    @Query("SELECT mode FROM album_preferences WHERE albumName = :albumName")
    suspend fun modeOrNull(albumName: String): AlbumMode?
}
