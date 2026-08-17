package com.gallery.sync.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.gallery.sync.data.local.entity.BackupEntryEntity
import com.gallery.sync.data.local.entity.BackupState
import kotlinx.coroutines.flow.Flow

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
}
