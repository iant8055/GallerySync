package com.gallery.sync.domain.backup

import com.gallery.sync.data.local.entity.AlbumMode

/**
 * Gate 2: what to do with the library already on the phone.
 *
 * The highest-regret moment in the app — one choice applied to thousands of files, made by someone
 * who has not yet watched the app do anything.
 *
 * **[ARCHIVE][AlbumMode.ARCHIVE] is deliberately absent and must stay absent.** Setting every album
 * to Archive in a wizard is the largest irreversible thing this product can do, chosen at the moment
 * the user knows least about it, and before v0.4 retrieval exists to undo it. Archive stays a
 * per-album decision with its own confirmation.
 */
enum class LibraryChoice(val mode: AlbumMode?) {

    /**
     * Nothing happens until the user visits Album Modes. The default, and the honest answer for
     * someone who has not seen the app work yet.
     */
    CHOOSE_PER_ALBUM(mode = null),

    /** Every album in scope to Backup. Nothing local changes and nothing is freed. */
    BACK_UP_EVERYTHING(mode = AlbumMode.BACKUP),

    /** Every album in scope to Sync. Photos get proxied; video is left whole. */
    BACK_UP_AND_FREE_SPACE(mode = AlbumMode.SYNC);

    /** Whether choosing this starts uploading. Both non-default choices do. */
    val uploads: Boolean get() = mode?.uploads == true
}

/**
 * What each choice would actually cost and save.
 *
 * ### Why the estimate exists at all
 *
 * "Back up and free space" sounds like it frees space in proportion to the library. On a real
 * library it does not, because **only photos shrink**. Measured on the Fold 8: 148 GB, of which
 * 130 GB is video. Proxying every photo there reclaims around 14 GB — roughly 11% — while the
 * wording invites someone to expect most of it back.
 *
 * Offering that without saying so is the kind of soft naming the milestones already warn about:
 * "Move to backup" was a soft name for a hard action, and the softening was the risk. The same
 * applies in reverse here — a hard-sounding promise over a soft result.
 */
object LibraryEstimate {

    /**
     * How much of a photo survives proxying, as a fraction.
     *
     * From the hardware measurement of 18 Aug 2026: 11 photos, 40,283,338 bytes reclaimed, with a
     * 348 KB proxy standing in for a multi-megabyte original — about a tenth of the size. Rounded
     * conservatively, because an estimate that overstates the saving is the one that misleads.
     */
    const val PROXY_FRACTION_REMAINING = 0.1

    /**
     * Bytes that proxying every photo would reclaim.
     *
     * Video is not included and never should be: Sync mode leaves it whole, and counting it here
     * would produce exactly the overstatement this function exists to prevent.
     */
    fun spaceFreedBySync(photoBytes: Long): Long =
        (photoBytes * (1.0 - PROXY_FRACTION_REMAINING)).toLong().coerceAtLeast(0)

    /**
     * Whether the saving is small enough that presenting it as "free space" would mislead.
     *
     * True when photos are a minor part of the library, which is the normal case for anyone who
     * records video. The screen uses this to lead with what *stays* rather than what goes.
     */
    fun isSavingMarginal(photoBytes: Long, videoBytes: Long): Boolean {
        val total = photoBytes + videoBytes
        if (total <= 0) return false
        return spaceFreedBySync(photoBytes).toDouble() / total < MARGINAL_FRACTION
    }

    /** Below a fifth of the library, "free space" is not a fair description of the outcome. */
    private const val MARGINAL_FRACTION = 0.2
}
