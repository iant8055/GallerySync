package com.gallery.sync.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.gallery.sync.data.local.entity.AlbumMode
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
              SELECT albumName FROM album_preferences WHERE mode = 'OFF'
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
              SELECT albumName FROM album_preferences WHERE mode = 'OFF'
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
     * Forgets one entry, for a file that is no longer on the device.
     *
     * Bookkeeping, not deletion: it removes our record and never touches the copy in OneDrive.
     * Keeping the row instead would retry a file that cannot come back, then leave it as a
     * permanent failure inflating the count for good.
     */
    @Query("DELETE FROM backup_entries WHERE id = :id")
    suspend fun forget(id: String)

    /**
     * Forgets rows belonging to albums that are no longer on the device.
     *
     * The album list in the UI comes from the device; the ledger does not. A deleted album leaves
     * rows behind that still count as work — invisible, because there is no album to render, and
     * so impossible to deselect. That is how "0 files selected" and "147 still to go" appeared on
     * the same screen.
     *
     * Matched on album name rather than file id deliberately: there are tens of albums and
     * thousands of files, and binding thousands of parameters exceeds SQLite's limit.
     *
     * **Only ever call this with a trustworthy scan.** An empty or partial list — revoked
     * permission, unmounted card — would otherwise read as "every album vanished" and wipe the
     * record of what is already backed up.
     */
    @Query("DELETE FROM backup_entries WHERE album NOT IN (:albumsOnDevice)")
    suspend fun forgetAlbumsNotOnDevice(albumsOnDevice: List<String>): Int

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
     * Records that a file was examined and no proxy is worth making.
     *
     * Only for permanent reasons — already small enough, or already a proxy. A transient failure
     * must not land here, or one bad decode would exclude a photo forever.
     */
    @Query("UPDATE backup_entries SET isProxySkipped = 1 WHERE id = :id")
    suspend fun markProxySkipped(id: String)

    /**
     * Records a file that was already backed up and already proxied, when the ledger had no memory
     * of either — after an uninstall, or on a new phone.
     *
     * `sizeBytes` is rewritten to the original's size rather than the proxy's. Every other query
     * treats `sizeBytes` as "how big this photo really is", with `localProxySizeBytes` holding what
     * it currently occupies; leaving the proxy's size there would make the row claim the original
     * was tiny, and `remoteSizeBytes = sizeBytes` — the test for "verified in the cloud" — would
     * never hold again for this file.
     */
    @Query(
        """
        UPDATE backup_entries
        SET state = :state,
            sizeBytes = :originalSizeBytes,
            remoteSizeBytes = :originalSizeBytes,
            remoteItemId = :remoteItemId,
            uploadedAtEpochMillis = :uploadedAt,
            isProxied = 1,
            localProxySizeBytes = :proxySizeBytes,
            attemptCount = 0,
            lastError = NULL
        WHERE id = :id
        """
    )
    suspend fun markRecoveredAsProxied(
        id: String,
        originalSizeBytes: Long,
        proxySizeBytes: Long,
        remoteItemId: String,
        uploadedAt: Long,
        state: BackupState = BackupState.UPLOADED
    )

    /**
     * Photos whose local copy can safely be replaced by a proxy.
     *
     * Verified in the cloud, not already proxied, not already examined and declined, and **never
     * video** — a degraded clip fails silently inside an editor and is only discovered in the
     * exported result.
     *
     * The declined ones matter as much as the proxied ones here. A file already under the target
     * size can never shrink, so leaving it in this list means the count never reaches zero and the
     * user keeps consenting to work that cannot happen.
     */
    @Query(
        """
        SELECT * FROM backup_entries
        WHERE state = :uploaded
          AND remoteSizeBytes IS NOT NULL
          AND remoteSizeBytes = sizeBytes
          AND isProxied = 0
          AND isProxySkipped = 0
          AND isVideo = 0
          AND album IN (
              SELECT albumName FROM album_preferences WHERE mode = :syncMode
          )
        ORDER BY sizeBytes DESC
        """
    )
    suspend fun proxyCandidates(
        uploaded: BackupState = BackupState.UPLOADED,
        syncMode: AlbumMode = AlbumMode.SYNC
    ): List<BackupEntryEntity>

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
