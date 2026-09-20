package com.gallery.sync.domain.backup

import com.gallery.sync.data.local.entity.BackupEntryEntity
import com.gallery.sync.data.local.entity.UnsentDepartureEntity

/** Where the record of a departed file lives, which decides how it is settled. */
enum class DepartureOrigin {

    /** A ledger row: this app sent the file, so the row carries the OneDrive item's id. */
    SENT,

    /** An [UnsentDepartureEntity]: the app never sent it, so any OneDrive copy was found by looking. */
    NEVER_SENT
}

/**
 * One file that has left the phone, as the window that opens with the app shows it. Ian, 19 Sept 2026.
 *
 * Two kinds of file arrive here, and the window does not care which: the ones this app backed up, and
 * the ones it never did. What matters to the window is only whether a copy is in OneDrive.
 */
data class DeletedFile(
    /** The ledger key the file had. Unique across both origins. */
    val id: String,
    val origin: DepartureOrigin,
    val displayName: String,
    val album: String,
    val sizeBytes: Long,
    val mediaStoreId: Long,
    val contentUri: String,
    val mimeType: String,
    val isVideo: Boolean,
    val dateModifiedEpochSeconds: Long,
    /** When a scan first found it gone. */
    val departedAtEpochMillis: Long,
    /**
     * Graph's id for the OneDrive copy, or null when none is known. Set from the ledger for a file
     * this app sent, and from looking in the album's folder for one it did not.
     */
    val remoteItemId: String?
) {
    companion object {
        fun of(entry: BackupEntryEntity) = DeletedFile(
            id = entry.id,
            origin = DepartureOrigin.SENT,
            displayName = entry.displayName,
            album = entry.album,
            sizeBytes = entry.sizeBytes,
            mediaStoreId = entry.mediaStoreId,
            contentUri = entry.contentUri,
            mimeType = entry.mimeType,
            isVideo = entry.isVideo,
            dateModifiedEpochSeconds = entry.dateModifiedEpochSeconds,
            departedAtEpochMillis = entry.localMissingSinceEpochMillis ?: 0L,
            remoteItemId = entry.remoteItemId
        )

        fun of(departure: UnsentDepartureEntity, remoteItemId: String? = null) = DeletedFile(
            id = departure.id,
            origin = DepartureOrigin.NEVER_SENT,
            displayName = departure.displayName,
            album = departure.album,
            sizeBytes = departure.sizeBytes,
            mediaStoreId = departure.mediaStoreId,
            contentUri = departure.contentUri,
            mimeType = departure.mimeType,
            isVideo = departure.isVideo,
            dateModifiedEpochSeconds = departure.dateModifiedEpochSeconds,
            departedAtEpochMillis = departure.goneSinceEpochMillis,
            remoteItemId = remoteItemId
        )
    }
}

/**
 * What the window has to ask, in the two parts Ian gave it.
 *
 * - [inCloud]: deleted from the phone but **a copy is in OneDrive**, whether this app put it there or
 *   not. Question: what should happen to that copy? Keep it or delete it.
 * - [notInCloud]: deleted from the phone, in the phone's trash, and **no copy could be found**.
 *   Question: what should happen to the file? Stay in the trash or be backed up.
 *
 * [complete] is false when some file could not be placed in either part because OneDrive could not be
 * asked. Those files are left out rather than guessed at, and the window is not counted as seen, so
 * they are offered again the next time it can be.
 */
data class DeletedFilesOffer(
    val inCloud: List<DeletedFile> = emptyList(),
    val notInCloud: List<DeletedFile> = emptyList(),
    val complete: Boolean = true
) {
    val isEmpty: Boolean get() = inCloud.isEmpty() && notInCloud.isEmpty()
    val newestDeparture: Long
        get() = (inCloud + notInCloud).maxOfOrNull { it.departedAtEpochMillis } ?: 0L
}

/** What looking in OneDrive found for a group of files. */
data class CloudLookup(
    /** File id to the OneDrive item id of the copy. Same name and same size as the file. */
    val found: Map<String, String>,
    /** File ids that could not be placed: the album could not be listed, or the copy reported no size. */
    val unknown: Set<String>
)

/** What backing up from the trash did. */
data class TrashBackupOutcome(
    val uploaded: Int = 0,
    /** Found in OneDrive when it came to send them, so recorded without sending. */
    val alreadyThere: Int = 0,
    /** Could not be read any more, most likely because the trash was emptied. Nothing left to decide. */
    val unreadable: Int = 0,
    /** Left undecided, to be offered again: no network, or the send did not complete. */
    val failed: Int = 0,
    val stoppedBecause: StopReason? = null
) {
    val backedUp: Int get() = uploaded + alreadyThere
}
