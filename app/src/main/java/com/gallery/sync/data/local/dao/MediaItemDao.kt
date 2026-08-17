package com.gallery.sync.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.gallery.sync.data.local.entity.MediaItemEntity
import com.gallery.sync.data.local.entity.MediaSource
import kotlinx.coroutines.flow.Flow

/** Reads on the `media_items` index. Reads return [Flow]; writes are `suspend`. */
@Dao
interface MediaItemDao {

    /**
     * Observes the items directly inside [parentFolderId], or the source roots when it is `null`.
     *
     * The `IS NULL` branch is required: SQL `parentFolderId = NULL` is never true, so a plain
     * equality comparison would silently return zero root rows.
     */
    @Query(
        """
        SELECT * FROM media_items
        WHERE (:parentFolderId IS NULL AND parentFolderId IS NULL)
           OR parentFolderId = :parentFolderId
        ORDER BY name
        """
    )
    fun observeByParent(parentFolderId: String?): Flow<List<MediaItemEntity>>

    @Query("SELECT * FROM media_items ORDER BY name")
    fun observeAll(): Flow<List<MediaItemEntity>>

    @Query("SELECT * FROM media_items WHERE id = :id")
    suspend fun getById(id: String): MediaItemEntity?

    @Upsert
    suspend fun upsertAll(items: List<MediaItemEntity>)

    /** Records that [id] was just read, so v0.2.0 LRU eviction can rank it. */
    @Query("UPDATE media_items SET lastAccessedUtc = :timestampUtc WHERE id = :id")
    suspend fun touchLastAccessed(id: String, timestampUtc: Long)

    /**
     * Drops the *local cache reference* for [id]: the row stays, and the remote file is untouched.
     *
     * Named for what it does. This is cache management, never a delete.
     */
    @Query("UPDATE media_items SET localCachePath = NULL, cachedAtUtc = NULL WHERE id = :id")
    suspend fun clearLocalCacheRef(id: String)

    /**
     * Removes local *index rows* for [source]. This never touches cloud content — it only discards
     * the cached listing, e.g. when an account is disconnected.
     */
    @Query("DELETE FROM media_items WHERE source = :source")
    suspend fun deleteIndexRowsBySource(source: MediaSource)
}
