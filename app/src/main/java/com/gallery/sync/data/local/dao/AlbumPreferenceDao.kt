package com.gallery.sync.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.gallery.sync.data.local.entity.AlbumPreferenceEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AlbumPreferenceDao {

    /** `REPLACE` here is correct: this table holds exactly the user's latest choice per album. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun setPreference(preference: AlbumPreferenceEntity)

    @Query("SELECT * FROM album_preferences")
    fun observeAll(): Flow<List<AlbumPreferenceEntity>>

    @Query("SELECT * FROM album_preferences")
    suspend fun all(): List<AlbumPreferenceEntity>

    /** Albums the user switched off. Absence from this list means enabled. */
    @Query("SELECT albumName FROM album_preferences WHERE isEnabled = 0")
    suspend fun disabledAlbums(): List<String>

    @Query("SELECT isEnabled FROM album_preferences WHERE albumName = :albumName")
    suspend fun isEnabledOrNull(albumName: String): Boolean?
}
