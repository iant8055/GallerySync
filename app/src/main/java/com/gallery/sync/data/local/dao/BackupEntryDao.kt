package com.gallery.sync.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.gallery.sync.data.local.entity.BackupEntryEntity
import com.gallery.sync.data.local.entity.BackupState
import kotlinx.coroutines.flow.Flow

/**
 * How much of one album has reached OneDrive.
 *
 * Lets the UI distinguish "switched off because it is finished and safe" from "switched off and
 * not backed up" — two very different situations that a bare toggle renders identically.
 */
data class AlbumBackupCount(
    val album: String,
    val total: Int,
    val backedUp: Int
)

@Dao
interface BackupEntryDao {

    /**
     * Records newly-scanned files without disturbing what is already known.
     *
     * `IGNORE`, emphatically not `REPLACE`: a rescan sees every file again, and replacing would
     * reset an already-UPLOADED row back to PENDING and re-upload the user's entire library on
     * every run. Rows only change through the explicit mark* calls below.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfNew(entries: List<BackupEntryEntity>)

    /**
     * The worker's queue: what still needs uploading, from albums the user kept enabled.
     *
     * Newest first, matching the scanner — an interrupted run should already have protected the
     * most recent photos.
     */
    @Query(
        """
        SELECT * FROM backup_entries
        WHERE state != :uploaded
          AND album NOT IN (
              SELECT albumName FROM album_preferences WHERE isEnabled = 0
          )
          AND attemptCount < :maxAttempts
        ORDER BY dateModifiedEpochSeconds DESC
        LIMIT :limit
        """
    )
    suspend fun nextPending(
        limit: Int,
        maxAttempts: Int,
        uploaded: BackupState = BackupState.UPLOADED
    ): List<BackupEntryEntity>

    @Query(
        """
        UPDATE backup_entries
        SET state = :state,
            remoteItemId = :remoteItemId,
            remoteSizeBytes = :remoteSizeBytes,
            uploadedAtEpochMillis = :uploadedAt,
            lastError = NULL
        WHERE id = :id
        """
    )
    suspend fun markUploaded(
        id: String,
        remoteItemId: String,
        remoteSizeBytes: Long,
        uploadedAt: Long,
        state: BackupState = BackupState.UPLOADED
    )

    @Query(
        """
        UPDATE backup_entries
        SET state = :state,
            attemptCount = attemptCount + 1,
            lastError = :error
        WHERE id = :id
        """
    )
    suspend fun markFailed(id: String, error: String, state: BackupState = BackupState.FAILED)

    /** Clears the failure count so the user can retry something that has given up. */
    @Query("UPDATE backup_entries SET state = :pending, attemptCount = 0, lastError = NULL WHERE state = :failed")
    suspend fun resetFailures(
        pending: BackupState = BackupState.PENDING,
        failed: BackupState = BackupState.FAILED
    )

    @Query("SELECT COUNT(*) FROM backup_entries WHERE state = :state")
    fun observeCount(state: BackupState): Flow<Int>

    @Query("SELECT COUNT(*) FROM backup_entries WHERE state = :state")
    suspend fun countInState(state: BackupState): Int

    @Query("SELECT COUNT(*) FROM backup_entries WHERE state != :state")
    suspend fun countNotInState(state: BackupState): Int

    /**
     * Outstanding files in albums the user actually selected.
     *
     * The whole-table count is misleading in the UI: someone backing up one album does not care
     * that 8,000 files sit in albums they deliberately switched off, and showing that number
     * alongside a run that correctly does nothing makes the app look broken.
     */
    @Query(
        """
        SELECT COUNT(*) FROM backup_entries
        WHERE state != :uploaded
          AND album NOT IN (
              SELECT albumName FROM album_preferences WHERE isEnabled = 0
          )
        """
    )
    suspend fun countPendingInSelectedAlbums(uploaded: BackupState = BackupState.UPLOADED): Int

    @Query("SELECT COUNT(*) FROM backup_entries")
    fun observeTotal(): Flow<Int>

    @Query("SELECT COALESCE(SUM(sizeBytes), 0) FROM backup_entries WHERE state != :uploaded")
    fun observePendingBytes(uploaded: BackupState = BackupState.UPLOADED): Flow<Long>

    /**
     * Rows for files no longer on the device.
     *
     * Removing these keeps the ledger honest. It never touches the copy already in OneDrive —
     * deleting a photo from the phone must not delete the backup, which is the whole point of
     * having one.
     */
    @Query("DELETE FROM backup_entries WHERE id NOT IN (:presentIds)")
    suspend fun deleteMissing(presentIds: List<String>)

    @Query("SELECT * FROM backup_entries WHERE id = :id")
    suspend fun find(id: String): BackupEntryEntity?

    /**
     * MediaStore ids of files replaced by a local proxy.
     *
     * By id, not by the content key: proxying changes the file's size, so the key no longer
     * matches. Without this the scanner sees each proxy as a brand-new file and uploads it beside
     * the original it was made from.
     */
    @Query("SELECT mediaStoreId FROM backup_entries WHERE isProxied = 1")
    suspend fun proxiedMediaStoreIds(): List<Long>

    @Query(
        """
        UPDATE backup_entries
        SET isProxied = 1, localProxySizeBytes = :proxySizeBytes
        WHERE id = :id
        """
    )
    suspend fun markProxied(id: String, proxySizeBytes: Long)

    /**
     * Photos whose local copy can safely be replaced by a proxy.
     *
     * Verified in the cloud, not already proxied, and **never video** — a degraded clip fails
     * silently inside an editor and is only discovered in the exported result.
     */
    @Query(
        """
        SELECT * FROM backup_entries
        WHERE state = :uploaded
          AND remoteSizeBytes IS NOT NULL
          AND remoteSizeBytes = sizeBytes
          AND isProxied = 0
          AND isVideo = 0
        ORDER BY sizeBytes DESC
        """
    )
    suspend fun proxyCandidates(uploaded: BackupState = BackupState.UPLOADED): List<BackupEntryEntity>

    /** Per-album totals, so each row can say whether it is completely safe. */
    @Query(
        """
        SELECT album AS album,
               COUNT(*) AS total,
               SUM(CASE WHEN state = :uploaded THEN 1 ELSE 0 END) AS backedUp
        FROM backup_entries
        GROUP BY album
        """
    )
    suspend fun albumCounts(uploaded: BackupState = BackupState.UPLOADED): List<AlbumBackupCount>

    /**
     * Entries safe to remove the local copy of.
     *
     * Requires both that OneDrive confirmed the file **and** that the size it reported equals the
     * local size. "We think we uploaded it" is not a good enough basis for deleting someone's only
     * other copy — the size check is the evidence the bytes actually arrived whole.
     */
    @Query(
        """
        SELECT * FROM backup_entries
        WHERE state = :uploaded
          AND remoteSizeBytes IS NOT NULL
          AND remoteSizeBytes = sizeBytes
        """
    )
    suspend fun verifiedInCloud(uploaded: BackupState = BackupState.UPLOADED): List<BackupEntryEntity>
}
