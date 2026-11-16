package com.gallery.sync.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.gallery.sync.data.local.entity.AlbumElsewhereStatusEntity
import kotlinx.coroutines.flow.Flow

/** What each Cloud other than OneDrive last said about each album. See [AlbumElsewhereStatusEntity]. */
@Dao
interface AlbumElsewhereStatusDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(status: AlbumElsewhereStatusEntity)

    @Query("SELECT * FROM album_elsewhere_status")
    suspend fun all(): List<AlbumElsewhereStatusEntity>

    /** The same rows as they change, so each album's line fills in as its answer arrives. */
    @Query("SELECT * FROM album_elsewhere_status")
    fun observeAll(): Flow<List<AlbumElsewhereStatusEntity>>

    @Delete
    suspend fun delete(rows: List<AlbumElsewhereStatusEntity>)
}
