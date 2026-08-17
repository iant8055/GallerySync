package com.gallery.sync.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Where a local file has got to on its way into OneDrive. */
enum class BackupState {

    /** Known about, not yet stored in the cloud. */
    PENDING,

    /** OneDrive confirmed it, and the size it reported matched. */
    UPLOADED,

    /** The last attempt failed. [BackupEntryEntity.attemptCount] says how many have. */
    FAILED
}

/**
 * The ledger: one row per local photo or video, recording whether it is safely in OneDrive.
 *
 * This is what makes backup resumable. Without it every run would re-upload the entire library,
 * and an interrupted run would have no idea where it got to.
 */
@Entity(
    tableName = "backup_entries",
    indices = [
        Index(value = ["state"]),
        Index(value = ["album"]),
        // The worker's hot query is "pending items in this album, oldest first".
        Index(value = ["album", "state"])
    ]
)
data class BackupEntryEntity(

    /**
     * A content-derived key, not the MediaStore id.
     *
     * MediaStore reuses ids after a row is deleted, so keying on one risks a newly-taken photo
     * inheriting the id of a deleted-and-already-uploaded file and being marked done without ever
     * being sent — a silent gap in someone's backup, the exact failure this app exists to prevent.
     *
     * See [backupKeyOf].
     */
    @PrimaryKey val id: String,

    /** Kept for reads, but never for identity, for the reason above. */
    val mediaStoreId: Long,
    val contentUri: String,
    val displayName: String,
    val album: String,
    val sizeBytes: Long,
    val dateModifiedEpochSeconds: Long,
    val mimeType: String,
    val isVideo: Boolean,

    val state: BackupState,

    /** Graph's id for the stored file, once it exists. */
    val remoteItemId: String? = null,

    /** Size OneDrive reported back. Compared against [sizeBytes] to prove the upload was whole. */
    val remoteSizeBytes: Long? = null,

    val uploadedAtEpochMillis: Long? = null,

    /** Attempts so far, so the worker can stop retrying something that will never succeed. */
    val attemptCount: Int = 0,

    /** Last failure, for display and diagnosis. Never contains a token or a URL with one. */
    val lastError: String? = null
)

/**
 * Builds the stable identity for a local file.
 *
 * Name, album, size and modification time together: the same combination `rsync` and most backup
 * tools rely on. Re-saving or editing a photo changes size or mtime, which produces a new key and
 * so a fresh upload — correct, because the bytes genuinely differ.
 *
 * A content hash would be stronger, but hashing every video on the device costs a full read of
 * every byte on every scan. That is not worth it here, and the collision risk for real photos is
 * negligible.
 */
fun backupKeyOf(
    album: String,
    displayName: String,
    sizeBytes: Long,
    dateModifiedEpochSeconds: Long
): String = "$album/$displayName|$sizeBytes|$dateModifiedEpochSeconds"
