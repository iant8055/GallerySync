package com.gallery.sync.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.gallery.sync.data.local.entity.AlbumMode
import com.gallery.sync.data.local.entity.BackupEntryEntity
import com.gallery.sync.data.local.entity.BackupState
import com.gallery.sync.data.local.media.RestoredAlbum
import kotlinx.coroutines.flow.Flow

/**
 * How much of one album has reached OneDrive.
 *
 * Lets the UI distinguish "switched off because it is finished and safe" from "switched off and
 * not backed up" — two very different situations that a bare toggle renders identically.
 */
/**
 * Just enough of a backed-up row to decide whether its file is still on the phone.
 *
 * Carries name and size as well as the content key, because those answer different questions. The
 * key says "this exact file, in this album, last modified then"; name and size say "this content,
 * anywhere". A restored file matches the second and not the first — it lands in a different folder
 * with a fresh timestamp — and it is the second that decides whether to keep offering it back.
 */
data class UploadedKey(
    val id: String,
    val displayName: String,
    val sizeBytes: Long,
    /**
     * Survives a proxy rewrite when [sizeBytes] does not.
     *
     * Proxying replaces the file in place, so its MediaStore row keeps its id while its size and
     * mtime both change. That makes the id the only stable way to ask "is this file still on the
     * phone?" for a proxied row — every content-derived key is computed from a size the file no
     * longer has.
     */
    val mediaStoreId: Long,
    val isProxied: Boolean
) {
    /** How the same content is recognised wherever it now sits. See [RestoredAlbum]. */
    val contentSignature: String get() = RestoredAlbum.contentSignature(displayName, sizeBytes)
}

data class AlbumBackupCount(
    val album: String,
    val total: Int,
    val backedUp: Int,
    val proxied: Int,
    /**
     * What optimising actually reclaimed: the original's size less the proxy now on disk.
     *
     * Summed from the ledger rather than measured, because the original is no longer on the phone
     * to measure. `sizeBytes` is what was uploaded and `localProxySizeBytes` is what replaced it,
     * so the difference is the space that came back — and it is the only honest way to state it.
     */
    val savedBytes: Long,
    /**
     * Rows this album has ever had uploaded, including files no longer on the phone.
     *
     * [backedUp] deliberately counts only what is still here, because it is rendered beside a file
     * count taken from the device and the two must describe one population. This one exists for the
     * single question that genuinely spans both: has this album ever had anything backed up? An
     * Archive album that ran to completion has [backedUp] of zero and a non-zero value here, and
     * that is exactly how it is told apart from an Archive album that was always empty.
     */
    val everBackedUp: Int,

    /**
     * What those uploaded rows occupy in OneDrive.
     *
     * The size the Archive filter reports, because an archived album holds nothing locally and
     * `totalBytes` is therefore zero — which is how that view came to describe a finished archive as
     * "0 Images · 0 Videos". Ian, 27 Aug 2026.
     */
    val everBackedUpBytes: Long
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
     * The worker's queue: what still needs uploading, from albums the user chose to back up.
     *
     * **Opt-in, and the direction is the whole point.** This asks which albums are in a mode other
     * than Off, rather than excluding the ones marked Off. The two differ only for an album with no
     * row at all — and that is exactly the case that went wrong. Under the old
     * `NOT IN (… mode = 'OFF')`, an album nobody had chosen was eligible, because `NOT IN` over an
     * empty set is true for everything.
     *
     * Observed 24 Aug 2026 on a fresh install: a content-triggered run fired before any preference
     * had been written and uploaded 23 files from five albums the user had never seen, one of them
     * a 75 MB video. Nothing was deleted — local removal needs an Activity and a tap — but files
     * left the phone that nobody had chosen to send.
     *
     * Consent has to be something granted, not something left un-revoked. With this direction the
     * failure mode is "backs up too little", which is visible and recoverable; the old one was
     * "backs up what you did not ask for", which is neither.
     *
     * Newest first, matching the scanner — an interrupted run should already have protected the
     * most recent photos.
     */
    @Query(
        """
        SELECT * FROM backup_entries
        WHERE state != :uploaded
          AND album IN (
              SELECT albumName FROM album_preferences WHERE mode != 'OFF'
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

    /**
     * Remembers a resumable upload session so the next run can continue it.
     *
     * Written as soon as Graph issues the session, not when the upload finishes — the whole point
     * is to survive a run that never reaches the end.
     */
    @Query(
        """
        UPDATE backup_entries
        SET uploadSessionUrl = :url,
            uploadSessionExpiresAtEpochMillis = :expiresAt
        WHERE id = :id
        """
    )
    suspend fun rememberUploadSession(id: String, url: String, expiresAt: Long?)

    /**
     * Drops a session that is finished, expired, or rejected.
     *
     * Leaving a dead URL behind is worse than having none: the next run would spend a request
     * discovering it is gone before starting the upload it could have started immediately.
     */
    @Query(
        """
        UPDATE backup_entries
        SET uploadSessionUrl = NULL,
            uploadSessionExpiresAtEpochMillis = NULL
        WHERE id = :id
        """
    )
    suspend fun forgetUploadSession(id: String)

    /**
     * Rows still holding a session, so a pause can release them.
     *
     * There is normally at most one — a run uploads a file at a time — but a crash between chunks
     * can leave an older one behind, and releasing those costs nothing.
     */
    @Query("SELECT * FROM backup_entries WHERE uploadSessionUrl IS NOT NULL")
    suspend fun entriesWithUploadSession(): List<BackupEntryEntity>

    /**
     * Every key the ledger holds for a file it believes is in OneDrive.
     *
     * The caller diffs this against a scan in memory rather than asking SQLite to. A
     * `NOT IN (:sixThousandKeys)` binds one variable per file and blows past SQLite's parameter
     * limit on a real library — and it cannot be chunked, because a file in the second chunk would
     * be marked missing by the first.
     */
    @Query(
        "SELECT id, displayName, sizeBytes, mediaStoreId, isProxied FROM backup_entries WHERE state = :uploaded"
    )
    suspend fun uploadedKeys(uploaded: BackupState = BackupState.UPLOADED): List<UploadedKey>

    /**
     * Every key the ledger holds for a file it still intends to upload.
     *
     * The counterpart to [uploadedKeys], and needed for a different reason. A row that has been
     * uploaded and whose file has gone is a *question* — should the cloud copy follow? — so it is
     * flagged and kept. A row that was never uploaded and whose file has gone is simply work that
     * no longer exists, and keeping it means the engine queues a file it cannot open.
     */
    @Query(
        "SELECT id, displayName, sizeBytes, mediaStoreId, isProxied FROM backup_entries WHERE state != :uploaded"
    )
    suspend fun pendingKeys(uploaded: BackupState = BackupState.UPLOADED): List<UploadedKey>

    /**
     * Forgets rows for files that were never uploaded and are no longer on the device.
     *
     * A delete rather than a flag, because there is nothing to decide: nothing was sent, so nothing
     * in OneDrive depends on the row. If the file reappears the scan seeds it again.
     */
    @Query("DELETE FROM backup_entries WHERE id IN (:ids) AND state != :uploaded")
    suspend fun forgetPending(ids: List<String>, uploaded: BackupState = BackupState.UPLOADED): Int

    /**
     * Marks rows whose file has left the phone.
     *
     * Only sets the timestamp where it is currently null, so the date reflects when the file first
     * went rather than the last time a scan noticed. Safe to call in chunks.
     */
    @Query(
        """
        UPDATE backup_entries
        SET localMissingSinceEpochMillis = :now
        WHERE localMissingSinceEpochMillis IS NULL
          AND id IN (:ids)
        """
    )
    suspend fun markLocalMissing(ids: List<String>, now: Long): Int

    /** Clears the flag for anything back on the phone — a restore, or a file that reappeared. */
    @Query(
        """
        UPDATE backup_entries
        SET localMissingSinceEpochMillis = NULL
        WHERE localMissingSinceEpochMillis IS NOT NULL
          AND id IN (:ids)
        """
    )
    suspend fun clearLocalMissing(ids: List<String>): Int

    /**
     * What can be fetched back: verified in OneDrive, and no longer on the phone.
     *
     * The same `remoteSizeBytes = sizeBytes` bar every other safe operation uses. Offering a file
     * whose cloud copy was never confirmed whole would mean handing someone a truncated photo and
     * calling it a restore.
     *
     * Newest first, because the most recently lost file is the one most likely to be wanted.
     */
    @Query(
        """
        SELECT * FROM backup_entries
        WHERE state = :uploaded
          AND localMissingSinceEpochMillis IS NOT NULL
          AND remoteItemId IS NOT NULL
          AND remoteItemId != ''
          AND remoteSizeBytes IS NOT NULL
          AND remoteSizeBytes = sizeBytes
        ORDER BY dateModifiedEpochSeconds DESC
        """
    )
    fun observeRetrievable(uploaded: BackupState = BackupState.UPLOADED): Flow<List<BackupEntryEntity>>

    /**
     * Files whose cloud copy could be offered for deletion.
     *
     * Every condition is a guard, and none is redundant:
     *
     * - `localMissingSinceEpochMillis <= :missingBefore` is the grace period. Absence observed once
     *   is not evidence of a deletion; absence that persists is.
     * - a usable `remoteItemId`, because without one there is nothing safe to delete, and matching
     *   by name would be a way to remove the wrong photo.
     * - `remoteSizeBytes = sizeBytes`, the same verification bar as everywhere else.
     * - **`isProxied = 0`.** A proxied photo carries a cloud badge burned into its pixels, and that
     *   badge is a standing promise: the full-quality original is in OneDrive. Offering that
     *   original for deletion would make the promise false while the badge went on asserting it —
     *   and every other badged photo would become indistinguishable from a broken one. The two
     *   facts have to be wired together rather than merely checked, which is what this line does.
     *
     * Oldest absence first, so the least ambiguous cases are presented at the top.
     *
     * **Why the proxy guard is not redundant with correct classification.** On 26 Aug 2026 proxying
     * changed a file's size and mtime, so `refreshLedger` could no longer match it and marked six
     * photos on disk as deleted from the phone. The classification bug is fixed separately; this
     * line is what makes the same mistake harmless if it is ever reintroduced.
     */
    @Query(
        """
        SELECT * FROM backup_entries
        WHERE state = :uploaded
          AND localMissingSinceEpochMillis IS NOT NULL
          AND localMissingSinceEpochMillis <= :missingBefore
          AND remoteItemId IS NOT NULL
          AND remoteItemId != ''
          AND remoteSizeBytes IS NOT NULL
          AND remoteSizeBytes = sizeBytes
          AND isProxied = 0
        ORDER BY localMissingSinceEpochMillis ASC
        """
    )
    suspend fun cloudDeletionCandidates(
        missingBefore: Long,
        uploaded: BackupState = BackupState.UPLOADED
    ): List<BackupEntryEntity>

    /**
     * Returns rows to pending so the uploader will send them again.
     *
     * For files the ledger records as `UPLOADED` that OneDrive turns out not to have. Archive's
     * validation backs up anything it cannot find — Ian, 26 Aug 2026 — but `nextPending` selects on
     * `state != UPLOADED`, so a file in exactly this state was invisible to the run sent to fix it.
     * Observed on the Moto G, 28 Aug 2026: eight `UPLOADED` rows with matching remote sizes, one of
     * them absent from the drive, and a manual run that finished in 600 ms having selected nothing.
     *
     * `remoteItemId` and `remoteSizeBytes` are cleared with the state. Leaving them would keep the
     * row asserting a cloud copy that has just been shown not to exist, and `verifiedInCloud()` —
     * the gate every removal passes through — reads exactly those columns.
     *
     * This is bookkeeping. It removes nothing anywhere; its only effect is to cause an upload.
     */
    @Query(
        """
        UPDATE backup_entries
        SET state = :pending,
            attemptCount = 0,
            lastError = NULL,
            remoteItemId = '',
            remoteSizeBytes = NULL
        WHERE mediaStoreId IN (:mediaStoreIds)
        """
    )
    suspend fun requeueForUpload(
        mediaStoreIds: List<Long>,
        pending: BackupState = BackupState.PENDING
    ): Int

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
     *
     * Must use the same opt-in test as [nextPending] — if these two disagree the UI promises work
     * the worker will not do, or reports nothing outstanding while it uploads.
     */
    @Query(
        """
        SELECT COUNT(*) FROM backup_entries
        WHERE state != :uploaded
          AND album IN (
              SELECT albumName FROM album_preferences WHERE mode != 'OFF'
          )
        """
    )
    suspend fun countPendingInSelectedAlbums(uploaded: BackupState = BackupState.UPLOADED): Int

    @Query("SELECT COUNT(*) FROM backup_entries")
    fun observeTotal(): Flow<Int>

    @Query("SELECT COALESCE(SUM(sizeBytes), 0) FROM backup_entries WHERE state != :uploaded")
    fun observePendingBytes(uploaded: BackupState = BackupState.UPLOADED): Flow<Long>

    /**
     * Bytes already in OneDrive, and bytes still waiting in albums the user selected.
     *
     * Sizes rather than counts because the UI shows a proportion, and a proportion of file *counts*
     * is a different and more flattering number than a proportion of bytes — 900 thumbnails backed
     * up and one 2 GB video outstanding is 99% by count and about 30% by size. The bar has to mean
     * the one the user is waiting on.
     *
     * Scoped the same way [countPendingInSelectedAlbums] is: an album switched off is not work.
     */
    @Query(
        """
        SELECT COALESCE(SUM(sizeBytes), 0) FROM backup_entries
        WHERE state = :uploaded
          AND album IN (
              SELECT albumName FROM album_preferences WHERE mode != 'OFF'
          )
        """
    )
    suspend fun uploadedBytesInSelectedAlbums(uploaded: BackupState = BackupState.UPLOADED): Long

    @Query(
        """
        SELECT COALESCE(SUM(sizeBytes), 0) FROM backup_entries
        WHERE state != :uploaded
          AND album IN (
              SELECT albumName FROM album_preferences WHERE mode != 'OFF'
          )
        """
    )
    suspend fun pendingBytesInSelectedAlbums(uploaded: BackupState = BackupState.UPLOADED): Long

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
     *
     * ### A row verified in OneDrive is never forgotten
     *
     * Added 25 Aug 2026, after watching this delete the only record of a file that had just been
     * backed up. Removing an album's last file makes the whole album absent from the scan, so the
     * prune fired and erased the row — which is precisely the row retrieval is built from.
     *
     * That is the Archive path exactly: take the files off the phone, the album empties, and the app
     * forgets everything it ever backed up from it. The user cannot get any of it back, and nothing
     * says why. Anything still verified in the cloud is therefore exempt — it is not a stale row, it
     * is the record of a file that can still be fetched.
     */
    @Query(
        """
        DELETE FROM backup_entries
        WHERE album NOT IN (:albumsOnDevice)
          AND NOT (
              state = :uploaded
              AND remoteItemId IS NOT NULL
              AND remoteItemId != ''
              AND remoteSizeBytes IS NOT NULL
              AND remoteSizeBytes = sizeBytes
          )
        """
    )
    suspend fun forgetAlbumsNotOnDevice(
        albumsOnDevice: List<String>,
        uploaded: BackupState = BackupState.UPLOADED
    ): Int

    /** Rows kept back from the prune because they can still be fetched. For logging the exemption. */
    @Query(
        """
        SELECT COUNT(*) FROM backup_entries
        WHERE album NOT IN (:albumsOnDevice)
          AND state = :uploaded
          AND remoteItemId IS NOT NULL
          AND remoteItemId != ''
          AND remoteSizeBytes IS NOT NULL
          AND remoteSizeBytes = sizeBytes
        """
    )
    suspend fun countRetrievableOutsideDevice(
        albumsOnDevice: List<String>,
        uploaded: BackupState = BackupState.UPLOADED
    ): Int

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
     * Every proxy that could be restored: ours, still here, and with a cloud original to fetch.
     *
     * `remoteItemId` and `remoteSizeBytes` are both required because a row missing either cannot be
     * restored — one says which OneDrive item to fetch and the other is the byte count the download
     * is checked against before anything is overwritten. Offering a row without them would be a
     * promise the app cannot keep.
     *
     * Measured against the live ledger on 27 Aug 2026: all 26 proxied rows on the Fold 4 carried
     * both, and no duplicate `album + displayName` existed anywhere in 147 rows. See TASK-018.
     */
    @Query(
        """
        SELECT * FROM backup_entries
        WHERE isProxied = 1
          AND remoteItemId IS NOT NULL
          AND remoteSizeBytes IS NOT NULL
          AND localMissingSinceEpochMillis IS NULL
        ORDER BY album, displayName
        """
    )
    suspend fun restorableProxies(): List<BackupEntryEntity>

    /**
     * Files this phone no longer has, which OneDrive still does.
     *
     * The download half of the Restore tab. Where [restorableProxies] answers *what have I shrunk*,
     * this answers *what has left this phone* — chiefly an Archive album, whose whole point is that
     * the local copies are gone.
     *
     * **Scope, deliberately: what this app took, not everything in the drive.** A row exists here
     * only because this device uploaded the file and later noticed it gone. A photo put in OneDrive
     * from a PC has no row and is not offered — Ian, 27 Aug 2026: *"if the user wants a straight
     * download they can use OneDrive."*
     *
     * `remoteItemId` is required for the same reason it is in [restorableProxies]: without it there
     * is nothing to fetch, and offering the row would be a promise the app cannot keep.
     *
     * ### It no longer asks whether the file is on the phone
     *
     * That clause was `localMissingSinceEpochMillis IS NOT NULL`, and it made this list depend on a
     * column whose real job is guarding cloud deletion. The two questions are not the same one:
     * `localMissing` is set by a **content** test that ignores which folder a file is in, so an
     * album archived while byte-identical copies sit in another folder was never marked — and the
     * Restore tab offered nothing. Observed on the Moto G, 28 Aug 2026, on eight files Ian had just
     * archived.
     *
     * Making that column stricter would have been the obvious fix and the wrong one: it is what
     * `cloudDeletionCandidates` keys on, so a looser reading of "gone" would put files in line to be
     * removed from OneDrive. Deletion keeps the cautious, album-blind answer; Restore now asks its
     * own question, per folder, in `BackupEngine.filesNotOnThePhone`. Ian, 28 Aug 2026, choosing
     * per-folder for Restore: a copy in an unrelated album is not an answer to "get that album
     * back".
     */
    @Query(
        """
        SELECT * FROM backup_entries
        WHERE state = :uploaded
          AND isProxied = 0
          AND remoteItemId IS NOT NULL
          AND remoteItemId != ''
          AND remoteSizeBytes IS NOT NULL
        ORDER BY album, displayName
        """
    )
    suspend fun fetchableFromCloud(uploaded: BackupState = BackupState.UPLOADED): List<BackupEntryEntity>

    /**
     * Moves a row onto the file that has just replaced it, in one statement.
     *
     * **The id changes, and it has to.** A row's identity is
     * `backupKeyOf(album, name, size, mtime)`, and a restore necessarily rewrites the mtime. Left
     * alone, the next scan computes a key the ledger has never seen and inserts a second, PENDING
     * row — so a file already sitting in OneDrive is uploaded again. Ian, 27 Aug 2026: *"a restored
     * file should not trigger a sync, only if the file is moved or saved."* Updating the key here is
     * what tells those two apart, because the app knows which one it just did and the mtime does not.
     *
     * Everything else on the row survives, `remoteItemId` above all — this is an UPDATE precisely so
     * the pointer to the cloud original is not lost the way a delete-and-reinsert would lose it.
     *
     * [modeOverride] is set to `BACKUP` by the caller, so the optimiser does not shrink back what the
     * user just pulled down.
     */
    @Query(
        """
        UPDATE backup_entries
        SET id = :newId,
            dateModifiedEpochSeconds = :dateModifiedEpochSeconds,
            sizeBytes = :sizeBytes,
            mediaStoreId = :mediaStoreId,
            contentUri = :contentUri,
            isProxied = 0,
            isProxySkipped = 0,
            localProxySizeBytes = NULL,
            localMissingSinceEpochMillis = NULL,
            modeOverride = :modeOverride
        WHERE id = :oldId
        """
    )
    suspend fun markRestored(
        oldId: String,
        newId: String,
        dateModifiedEpochSeconds: Long,
        sizeBytes: Long,
        mediaStoreId: Long,
        contentUri: String,
        modeOverride: AlbumMode = AlbumMode.BACKUP
    )

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
          -- The file's own mode wins over its album's. A restored photo is pinned to BACKUP so the
          -- optimiser does not shrink back what the user just pulled down; every other row is null
          -- here and follows its album exactly as before. See TASK-018.
          AND (
              modeOverride = :syncMode
              OR (
                  modeOverride IS NULL
                  AND album IN (
                      SELECT albumName FROM album_preferences WHERE mode = :syncMode
                  )
              )
          )
        ORDER BY sizeBytes DESC
        """
    )
    suspend fun proxyCandidates(
        uploaded: BackupState = BackupState.UPLOADED,
        syncMode: AlbumMode = AlbumMode.SYNC
    ): List<BackupEntryEntity>

    /**
     * Per-album totals, so each row can say whether it is completely safe.
     *
     * **Counted over files still on the phone.** The card puts these beside a file count taken from
     * a device scan, and until 27 Aug 2026 they were counted over every ledger row — so `Weird Al`
     * read "17 files" above "28 backed up", which is not arithmetic anyone can follow. Both numbers
     * were true and they counted different populations: 17 on the device, 28 in OneDrive, 11 of
     * those no longer here. Ian asked what the discrepancy was, which is the only reasonable
     * response to a card that says that.
     */
    @Query(
        """
        SELECT album AS album,
               COUNT(*) AS total,
               SUM(CASE WHEN state = :uploaded AND localMissingSinceEpochMillis IS NULL
                        THEN 1 ELSE 0 END) AS backedUp,
               SUM(CASE WHEN isProxied = 1 AND localMissingSinceEpochMillis IS NULL
                        THEN 1 ELSE 0 END) AS proxied,
               COALESCE(SUM(
                   CASE WHEN isProxied = 1 AND localProxySizeBytes IS NOT NULL
                             AND localMissingSinceEpochMillis IS NULL
                        THEN sizeBytes - localProxySizeBytes ELSE 0 END
               ), 0) AS savedBytes,
               SUM(CASE WHEN state = :uploaded THEN 1 ELSE 0 END) AS everBackedUp,
               COALESCE(SUM(
                   CASE WHEN state = :uploaded
                        THEN COALESCE(remoteSizeBytes, sizeBytes) ELSE 0 END
               ), 0) AS everBackedUpBytes
        FROM backup_entries
        GROUP BY album
        """
    )
    suspend fun albumCounts(uploaded: BackupState = BackupState.UPLOADED): List<AlbumBackupCount>

    @Query(
        """
        SELECT * FROM backup_entries
        WHERE album = :album
        ORDER BY displayName ASC
        """
    )
    suspend fun entriesForAlbum(album: String): List<BackupEntryEntity>

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
