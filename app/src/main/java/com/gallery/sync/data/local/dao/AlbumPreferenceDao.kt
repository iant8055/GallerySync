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
     * The second matters more than it looks: albums default to enabled, so without a way to switch
     * them all off, anyone wanting to back up a single album would have to toggle a hundred others
     * by hand — and would likely just run it and upload their whole library by accident.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun setPreferences(preferences: List<AlbumPreferenceEntity>)

    @Query("SELECT * FROM album_preferences")
    fun observeAll(): Flow<List<AlbumPreferenceEntity>>

    @Query("SELECT * FROM album_preferences")
    suspend fun all(): List<AlbumPreferenceEntity>

    /** Albums the user switched off. Absence from this list means the album is uploaded. */
    @Query("SELECT albumName FROM album_preferences WHERE mode = 'OFF'")
    suspend fun disabledAlbums(): List<String>

    /** Albums in a given mode, for the space-management work that only applies to some of them. */
    @Query("SELECT albumName FROM album_preferences WHERE mode = :mode")
    suspend fun albumsInMode(mode: AlbumMode): List<String>

    /** The album's chosen mode, or null when the user has never touched it. */
    @Query("SELECT mode FROM album_preferences WHERE albumName = :albumName")
    suspend fun modeOrNull(albumName: String): AlbumMode?
}
