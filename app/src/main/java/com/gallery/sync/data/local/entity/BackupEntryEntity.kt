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
    val lastError: String? = null,

    /**
     * The local file has been replaced by a downscaled proxy; the original lives only in OneDrive.
     *
     * Load-bearing for correctness, not just display. Proxying changes the local file's size, so
     * a rescan computes a different [id] and would treat the proxy as a new file to upload. The
     * scan skips any local item whose [mediaStoreId] matches a proxied row — which is why that id
     * is kept even though it is unfit for identity.
     */
    val isProxied: Boolean = false,

    /**
     * The generator looked at this file and decided no proxy is worth making — it is already at or
     * under the target size, or it is already a proxy.
     *
     * Distinct from [isProxied]: nothing was rewritten and nothing was reclaimed. It exists so a
     * file that can never shrink stops being offered. Without it the candidate count never reaches
     * zero and the button becomes a no-op the user keeps pressing.
     *
     * Only set for permanent reasons. A file that failed to decode or write is left alone and stays
     * a candidate, because that may have been transient.
     */
    val isProxySkipped: Boolean = false,

    /** What the local file occupies now. [sizeBytes] still means the original, which is in the cloud. */
    val localProxySizeBytes: Long? = null,

    /**
     * A resumable upload session that outlived the run that created it.
     *
     * Graph hands back a pre-authorised URL that stays valid for roughly five hours. Without
     * storing it, a run killed part-way starts the next attempt from byte zero: observed on the
     * Fold 4, force-stopped at 96% of a 164 MB video, next run opened a fresh session reporting
     * `nextExpectedRanges: ["0-"]`.
     *
     * That is not merely wasteful — it is a hard ceiling. A file that cannot finish inside one
     * background window can never finish at all, and the window is measured in bytes of upstream,
     * not in file size. Null whenever no session is outstanding.
     */
    val uploadSessionUrl: String? = null,

    /**
     * When [uploadSessionUrl] stops being valid, epoch millis.
     *
     * Stored rather than inferred: Graph decides the lifetime and says so, and a session resumed
     * past its expiry fails in a way that looks like a network error.
     */
    val uploadSessionExpiresAtEpochMillis: Long? = null,

    /**
     * When this file stopped being on the phone, epoch millis. Null while it is still here.
     *
     * Set by comparing the ledger against an **unscoped** scan, so it means "gone from the device"
     * and never "in a folder the user narrowed away". Getting that wrong would offer someone their
     * whole library back the moment they removed a directory from Gate 1.
     *
     * Covers more than Archive: a photo deleted in the gallery app is equally gone, and equally
     * worth offering back from a cloud copy that is still there. It also covers a proxied photo,
     * whose full-quality original genuinely is no longer on the phone — retrieval is the only route
     * back to it.
     */
    val localMissingSinceEpochMillis: Long? = null
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
