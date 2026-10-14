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
     * Whether choosing this runs the wizard's one-time bulk optimise — Gate 2's #2 and #3.
     *
     * Named here rather than spelled `mode?.proxiesPhotos == true` at each call site, because the
     * screen that *estimates* the saving and the worker that *performs* it must not be able to
     * disagree about which choices optimise. They did until 6 Sept 2026: Step 7 quoted the
     * outstanding tallies — the population only #3 was ever described against — while the worker
     * optimised everything eligible, so a #2 install promised 60 MB and reclaimed 2.26 GB.
     */
    val optimisesAtInstall: Boolean get() = mode?.proxiesPhotos == true

    /**
     * Whether the install pass acts on the **whole** library rather than only what it just uploaded.
     *
     * True for [BACK_UP_AND_FREE_SPACE] alone. Ian, 6 Sept 2026, spelling out the difference: #2
     * *"optimizes ALL files on phone"*; #3 *"ONLY optimizes files that were actually backed up in
     * the previous step"*.
     *
     * What the *worker* does about it is [cutoffFor]; this exists so a screen estimating the saving
     * picks the same population the worker will act on — the whole library for #2, and only what is
     * outstanding for #3.
     */
    val optimisesWholeLibrary: Boolean get() = this == BACK_UP_AND_FREE_SPACE

    /**
     * The moment before which already-uploaded files are left alone.
     *
     * Only [BACK_UP_AND_OPTIMISE_NEW] sets one; every other choice either optimises everything
     * eligible or optimises nothing, and neither needs a cutoff.
     */
    fun cutoffFor(now: Long): Long =
        if (this == BACK_UP_AND_OPTIMISE_NEW) now else OptimiseCutoff.EVERYTHING

    companion object {

        /** What an install has chosen until the user says otherwise. */
        val DEFAULT = BACK_UP_EVERYTHING

        /**
         * Read back by name, never by ordinal, so inserting a choice later cannot silently
         * reinterpret one somebody already made. Same reasoning as [VideoQuality].
         */
        fun fromNameOrDefault(name: String?): LibraryChoice =
            entries.firstOrNull { it.name == name } ?: DEFAULT
    }
}
