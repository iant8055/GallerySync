package com.gallery.sync.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.gallery.sync.data.local.entity.FolderPreferenceEntity
import kotlinx.coroutines.flow.Flow

/** Mirrors [AlbumPreferenceDao]'s shape exactly, one level up — see [FolderPreferenceEntity]. */
@Dao
interface FolderPreferenceDao {

    /** `REPLACE` here is correct: this table holds exactly the user's latest choice per folder. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun setPreference(preference: FolderPreferenceEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun setPreferences(preferences: List<FolderPreferenceEntity>)

    /**
     * Records folders the scanner found, without disturbing any choice already made.
     *
     * `IGNORE` is the entire point, same reason as [AlbumPreferenceDao.insertIfNew]: this runs on
     * every scan, and `REPLACE` here would reset an explicitly-chosen destination back to the
     * app-wide default the next time the folder was seen.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfNew(preferences: List<FolderPreferenceEntity>)

    @Query("SELECT * FROM folder_preferences")
    fun observeAll(): Flow<List<FolderPreferenceEntity>>

    @Query("SELECT * FROM folder_preferences")
    suspend fun all(): List<FolderPreferenceEntity>
}
