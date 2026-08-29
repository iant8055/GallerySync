package com.gallery.sync.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.gallery.sync.data.local.entity.AlbumCloudStatusEntity
import kotlinx.coroutines.flow.Flow

/** Reads and writes what the drive last said about each album. See [AlbumCloudStatusEntity]. */
@Dao
interface AlbumCloudStatusDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(status: AlbumCloudStatusEntity)

    @Query("SELECT * FROM album_cloud_status")
    suspend fun all(): List<AlbumCloudStatusEntity>

    /**
     * The same rows, as they change.
     *
     * The reconciliation writes one row per album as it walks the drive, and it runs at launch
     * alongside the Albums tab building its list. A one-shot read caught whichever albums happened
     * to be done first: observed on the Moto G, 28 Aug 2026, with `BudgetMixed` showing "1116
     * verified in OneDrive" while the five albums checked seconds later still read "Not checked
     * against OneDrive yet". Observing means the rows fill in as the answers arrive.
     */
    @Query("SELECT * FROM album_cloud_status")
    fun observeAll(): Flow<List<AlbumCloudStatusEntity>>

    @Query("SELECT * FROM album_cloud_status WHERE albumName = :albumName")
    suspend fun forAlbum(albumName: String): AlbumCloudStatusEntity?

    /**
     * Forgets what the drive said about an album.
     *
     * For a destination change: the answer was about a different folder, and a stale one would be
     * worse than none — it would report an album verified against a place the app no longer uploads
     * to.
     */
    @Query("DELETE FROM album_cloud_status")
    suspend fun clear()
}
