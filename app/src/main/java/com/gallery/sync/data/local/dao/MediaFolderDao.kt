package com.gallery.sync.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.gallery.sync.data.local.entity.MediaFolderEntity
import com.gallery.sync.data.local.entity.MediaSource
import kotlinx.coroutines.flow.Flow

/** Reads on the `media_folders` index. Reads return [Flow]; writes are `suspend`. */
@Dao
interface MediaFolderDao {

    /**
     * Observes the folders directly inside [parentFolderId], or the source roots when it is `null`.
     *
     * The `IS NULL` branch is required: SQL `parentFolderId = NULL` is never true, so a plain
     * equality comparison would silently return zero root rows.
     */
    @Query(
        """
        SELECT * FROM media_folders
        WHERE (:parentFolderId IS NULL AND parentFolderId IS NULL)
           OR parentFolderId = :parentFolderId
        ORDER BY name
        """
    )
    fun observeChildren(parentFolderId: String?): Flow<List<MediaFolderEntity>>

    @Query("SELECT * FROM media_folders WHERE id = :id")
    suspend fun getById(id: String): MediaFolderEntity?

    @Upsert
    suspend fun upsertAll(folders: List<MediaFolderEntity>)

    /**
     * Removes local *index rows* for [source]. This never touches cloud content — it only discards
     * the cached listing, e.g. when an account is disconnected.
     */
    @Query("DELETE FROM media_folders WHERE source = :source")
    suspend fun deleteIndexRowsBySource(source: MediaSource)
}
