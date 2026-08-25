package com.gallery.sync.domain.backup

import com.gallery.sync.data.local.media.LocalMediaItem

/**
 * One file as OneDrive reports it: what it is called is the key, this is the rest.
 *
 * [id] is the part that used to be thrown away. The skip-existing check matched on name and size and
 * then recorded `remoteItemId = ""`, which is enough to say "already backed up" and not enough to
 * ever fetch it back — and on a real library that path covers almost everything: 6,278 of 6,371
 * files on the Fold 4. Without the id, retrieval could offer none of them.
 */
data class RemoteFileRef(val id: String, val sizeBytes: Long)

/**
 * The answer to "are these still in OneDrive?", asked of the drive rather than of the ledger.
 *
 * Only [confirmed] may have its local copy removed. [missing] means the ledger was wrong and the
 * file is not backed up at all — the user needs telling, not a quiet exclusion. [unconfirmed] means
 * the question could not be asked, which is not an answer and must never be treated as one.
 */
data class CloudConfirmation(
    val confirmed: List<LocalMediaItem> = emptyList(),
    val missing: List<LocalMediaItem> = emptyList(),
    val unconfirmed: List<LocalMediaItem> = emptyList()
) {
    /** True when every file asked about came back confirmed. */
    val isComplete: Boolean get() = missing.isEmpty() && unconfirmed.isEmpty()

    val heldBack: Int get() = missing.size + unconfirmed.size
}

/** A count of files and what they occupy. */
data class MediaTally(val files: Int = 0, val bytes: Long = 0) {

    operator fun plus(other: MediaTally) = MediaTally(files + other.files, bytes + other.bytes)

    companion object {
        val EMPTY = MediaTally()
    }
}

/**
 * How much of the library OneDrive already holds.
 *
 * ### Three outcomes, not two
 *
 * The category that matters is [unchecked]. An album whose listing failed is **not** an album with
 * nothing in it, and collapsing the two is a mistake this project has already made: on 19 Aug 2026 a
 * run reported 8,177 files "not in OneDrive" when the truth was that 81 albums never got listed at
 * all, because connectivity dropped part-way.
 *
 * In a debug screen that is a wrong number. In the first-run flow it is worse than wrong — someone
 * told their library is unprotected will choose to upload all of it, paying hours of transfer and
 * duplicate quota for files that were already safe. **Failing to ask is not evidence of absence**,
 * and this type exists to keep those two states apart all the way to the screen.
 */
data class CloudReconciliation(
    val photosBackedUp: MediaTally = MediaTally.EMPTY,
    val photosOutstanding: MediaTally = MediaTally.EMPTY,
    val videosBackedUp: MediaTally = MediaTally.EMPTY,
    val videosOutstanding: MediaTally = MediaTally.EMPTY,
    /** Files in albums that could not be listed. Neither backed up nor known to be missing. */
    val unchecked: MediaTally = MediaTally.EMPTY,
    val albumsChecked: Int = 0,
    val albumsUnchecked: Int = 0
) {
    val backedUp: MediaTally get() = photosBackedUp + videosBackedUp

    /** Every photo in scope, backed up or not — what proxying could act on. */
    val photos: MediaTally get() = photosBackedUp + photosOutstanding

    /** Every video in scope. Sync leaves these whole, which is why they are counted apart. */
    val videos: MediaTally get() = videosBackedUp + videosOutstanding

    /** What a first run would actually send. The number that decides "back up everything". */
    val outstanding: MediaTally get() = photosOutstanding + videosOutstanding

    /**
     * Whether every album was successfully checked.
     *
     * False means the figures above are a floor, not a total, and the screen must say so rather
     * than presenting them as complete.
     */
    val isComplete: Boolean get() = albumsUnchecked == 0

    operator fun plus(other: CloudReconciliation) = CloudReconciliation(
        photosBackedUp = photosBackedUp + other.photosBackedUp,
        photosOutstanding = photosOutstanding + other.photosOutstanding,
        videosBackedUp = videosBackedUp + other.videosBackedUp,
        videosOutstanding = videosOutstanding + other.videosOutstanding,
        unchecked = unchecked + other.unchecked,
        albumsChecked = albumsChecked + other.albumsChecked,
        albumsUnchecked = albumsUnchecked + other.albumsUnchecked
    )
}

/**
 * The matching decisions, as pure functions.
 *
 * Extracted so they can be tested with no device and no network, the same way [MediaScanRules]
 * handles the scanner's judgement calls.
 */
internal object ReconciliationRules {

    /**
     * Tallies one album against what OneDrive reports for it.
     *
     * [remoteIndex] is file name to byte size, or **null when the album could not be listed** — a
     * distinction the caller must preserve rather than substituting an empty map. An empty map says
     * "the album is there and holds nothing"; null says "we do not know", and only one of those is
     * a reason to upload.
     *
     * A file counts as backed up on **name and matching byte size**, the same bar
     * `BackupEntryDao.verifiedInCloud` uses. A name match alone is also true of a truncated upload,
     * and a truncated photo is a lost photo.
     */
    fun tallyAlbum(
        local: List<LocalMediaItem>,
        remoteIndex: Map<String, RemoteFileRef>?
    ): CloudReconciliation {
        if (remoteIndex == null) {
            return CloudReconciliation(
                unchecked = MediaTally(local.size, local.sumOf { it.sizeBytes }),
                albumsUnchecked = 1
            )
        }

        var photosBackedUp = MediaTally.EMPTY
        var photosOutstanding = MediaTally.EMPTY
        var videosBackedUp = MediaTally.EMPTY
        var videosOutstanding = MediaTally.EMPTY

        for (item in local) {
            val one = MediaTally(1, item.sizeBytes)
            val isBackedUp = remoteIndex[item.displayName]?.sizeBytes == item.sizeBytes

            when {
                item.isVideo && isBackedUp -> videosBackedUp += one
                item.isVideo -> videosOutstanding += one
                isBackedUp -> photosBackedUp += one
                else -> photosOutstanding += one
            }
        }

        return CloudReconciliation(
            photosBackedUp = photosBackedUp,
            photosOutstanding = photosOutstanding,
            videosBackedUp = videosBackedUp,
            videosOutstanding = videosOutstanding,
            albumsChecked = 1
        )
    }
}
