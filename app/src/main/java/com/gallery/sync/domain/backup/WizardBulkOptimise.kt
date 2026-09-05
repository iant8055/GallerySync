package com.gallery.sync.domain.backup

/**
 * Whether a drained upload chain should start the install wizard's one-time optimise by itself.
 *
 * ### Why the wizard card cannot be the thing that starts it
 *
 * The handoff from uploading to optimising used to live in a `LaunchedEffect` on the progress card,
 * so it only ran while a composition was alive to run it. Measured on the Moto G, 5 Sept 2026: a
 * four-hour delayed start fired on time and uploaded 230 files between 02:47:19 and 02:55:48, then
 * nothing was optimised for nine hours. Every proxy on disk carries a 12:04 mtime — the minute the
 * phone was woken. The upload half honoured "set it and walk away" and the optimise half could not,
 * which is the same class of defect as optimising dying with `viewModelScope` on 4 Sept: work that
 * outlives the screen must be owned by something that also does.
 *
 * ### Why the gate is this narrow
 *
 * Optimising rewrites files on the phone, and from then on the full-quality original exists only in
 * OneDrive. This must fire for the one-time install pass the user explicitly chose, and never become
 * a standing behaviour that acts on a later ordinary backup:
 *
 *  - **[setupComplete] is false** — the guided first run is still in progress. Area 1 happens once,
 *    at install, and this flag is what says the install is still happening.
 *  - **[allAlbums]** — only the wizard's own runs route past album-mode filtering. A later "Sync
 *    now" is manual too, so manual alone would not distinguish them, and a bulk optimise must not
 *    ride along behind an ordinary run.
 *  - **the choice optimises** — Gate 2's #2 and #3 do; #1 and #4 do not.
 *
 * Note what is deliberately absent. This **reads** the install choice and never writes an album
 * mode: the two areas are independent, album modes are set only by the user, and an album reading
 * `Off` after the first backup is correct rather than a bug.
 *
 * Consent is checked separately and later, by the worker that would do the writing: files inside a
 * granted SAF tree need no dialog, and files outside one need a tap this app cannot raise from the
 * background. Answering that here would mean asking MediaStore a question in a pure decision.
 */
object WizardBulkOptimise {

    fun shouldHandOff(
        setupComplete: Boolean,
        allAlbums: Boolean,
        choice: LibraryChoice
    ): Boolean = !setupComplete && allAlbums && choice.mode?.proxiesPhotos == true

    /**
     * Whether a drained photo pass should be followed by the video one.
     *
     * No `allAlbums` here, and its absence is the point: that flag exists to tell the wizard's own
     * upload runs apart from an ordinary "Sync now", and by this point we are already inside the
     * optimise chain, which nothing but the wizard starts. What still has to hold is that the
     * install is unfinished and that the choice asked for optimising at all.
     */
    fun shouldContinueToVideo(
        setupComplete: Boolean,
        choice: LibraryChoice
    ): Boolean = !setupComplete && choice.mode?.proxiesPhotos == true
}
