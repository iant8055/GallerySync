package com.gallery.sync.domain.backup

import com.gallery.sync.data.local.entity.AlbumMode

/**
 * Gate 2: what to do with the library already on the phone.
 *
 * The highest-regret moment in the app — one choice applied to thousands of files, made by someone
 * who has not yet watched the app do anything.
 *
 * **Ordered as Ian wrote them, 28 Aug 2026**, and the order is the argument: a baseline first, then
 * the two things that can be added to it, then the opt-out. Each option is the previous one plus
 * something, so the screen reads as a single decision with a scale rather than four unrelated
 * buttons.
 *
 * Every option except the last does the same backup. **Optimising is the only variable**, which is
 * why the heading states the backup once and the options carry only the part that differs — an
 * earlier draft repeated "Back up everything" in three of four labels and buried the actual choice.
 *
 * **[ARCHIVE][AlbumMode.ARCHIVE] is deliberately absent and must stay absent.** Setting every album
 * to Archive in a wizard is the largest irreversible thing this product can do, chosen at the moment
 * the user knows least about it, and before v0.4 retrieval exists to undo it. Archive stays a
 * per-album decision with its own confirmation.
 */
enum class LibraryChoice(val mode: AlbumMode?) {

    /**
     * Reconcile, then upload what OneDrive does not already have. Nothing local changes.
     *
     * The baseline every other choice is expressed against, and Ian's #1: *"check Cloud Storage and
     * back up everything that isn't already backed up."*
     */
    BACK_UP_EVERYTHING(mode = AlbumMode.BACKUP),

    /**
     * All of [BACK_UP_EVERYTHING], plus a smaller local copy of everything eligible.
     *
     * Ian's #2 — maximum space saved.
     */
    BACK_UP_AND_FREE_SPACE(mode = AlbumMode.SYNC),

    /**
     * All of [BACK_UP_EVERYTHING], plus smaller copies of **only what this run uploads**.
     *
     * Ian's #3 — moderate space saved, and his phrasing for it was *"we back them up first and leave
     * an opt file behind."* The albums all go to Sync so new files are handled from here on, and
     * [OptimiseCutoff] keeps what was already in OneDrive at full size on the phone.
     *
     * The cautious choice, for somebody unwilling to have a library they already own rewritten in a
     * single overnight pass. **It frees little on a typical install**, because most of a real library
     * is already in the cloud — which is why the screen shows both counts rather than a promise.
     */
    BACK_UP_AND_OPTIMISE_NEW(mode = AlbumMode.SYNC),

    /**
     * Reconcile, and stop. The default.
     *
     * Ian's #4: *"check Cloud Storage but do not back up any new files."* Note that the check still
     * happens — it is what produced the scan report the user has just read — so this is not "nothing
     * happens", and an earlier draft of the copy saying so was wrong.
     */
    CHOOSE_PER_ALBUM(mode = null);

    /** Whether choosing this starts uploading. Every non-default choice does. */
    val uploads: Boolean get() = mode?.uploads == true

    /**
     * The moment before which already-uploaded files are left alone.
     *
     * Only [BACK_UP_AND_OPTIMISE_NEW] sets one; every other choice either optimises everything
     * eligible or optimises nothing, and neither needs a cutoff.
     */
    fun cutoffFor(now: Long): Long =
        if (this == BACK_UP_AND_OPTIMISE_NEW) now else OptimiseCutoff.EVERYTHING
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
