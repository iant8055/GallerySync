package com.gallery.sync.domain.backup

/**
 * When the ongoing video optimiser may start, and when its chain may go on.
 *
 * This is Area 2 (CLAUDE.md's three areas): the Settings tree, applied to Sync albums after install.
 * It is deliberately a different thing from [WizardBulkOptimise], which is Area 1 and acts once, at
 * setup, on the library choice alone. Neither may read the other's gates, so the two have their own
 * rules in their own files.
 *
 * Pure, so the rules are pinned by tests and not by watching a phone.
 */
object VideoOptimisePolicy {

    /**
     * Whether video may be optimised without anyone asking.
     *
     * Every one of these has to hold, and the first is the reason setup is not something this can
     * run around: until setup completes the wizard's own pass owns video, and a second chain
     * transcoding the same clips would only double the work.
     *
     * [OptimiseMode.Manual] is not a way to switch this off, it is the user saying they will press the
     * button themselves, so it answers false here and the button still works.
     */
    fun runsAutomatically(
        setupComplete: Boolean,
        optimiseEnabled: Boolean,
        optimiseVideo: Boolean,
        mode: OptimiseMode
    ): Boolean = setupComplete && optimiseEnabled && optimiseVideo && mode == OptimiseMode.Auto

    /**
     * Whether pressing **Sync now** should also optimise video.
     *
     * Ian, 19 Sept 2026: Manual means *"through the Sync Now button on the Albums tab"*. So it is the
     * mode that answers here, the mirror of [runsAutomatically]: a Manual clip waits for that button and
     * an Automatic one never needs it.
     */
    fun runsOnSyncNow(
        setupComplete: Boolean,
        optimiseEnabled: Boolean,
        optimiseVideo: Boolean,
        mode: OptimiseMode
    ): Boolean = setupComplete && optimiseEnabled && optimiseVideo && mode == OptimiseMode.Manual

    /**
     * Whether a batch that just finished should be followed by another.
     *
     * Two things have to be true, and between them they are what makes the chain end.
     *
     * - **The batch took a real look at something** ([attempted] is optimised, skipped or failed).
     *   A batch that found nothing to do is finished; queueing another would spin for ever.
     * - **Something is left that this chain has not already given up on** ([moreReady] is counted
     *   with the chain's failures excluded).
     *
     * Each pass either removes a clip from the candidates (optimised, or marked as one that cannot
     * shrink) or adds it to the chain's exclusions (failed), so the list only ever gets shorter and
     * the chain always ends. The exclusions ride in WorkManager's input data, which is capped, so
     * past [MAX_EXCLUDED] the chain stops rather than losing track and retrying what failed.
     */
    fun shouldContinue(attempted: Int, moreReady: Int, excludedCount: Int): Boolean =
        attempted > 0 && moreReady > 0 && excludedCount <= MAX_EXCLUDED

    /**
     * How many failed clips one chain remembers.
     *
     * A ledger id is a path and a name and two numbers, around a hundred characters, and WorkManager
     * refuses input data over ten kilobytes.
     */
    const val MAX_EXCLUDED = 40

    /**
     * Clips per batch. A transcode is tens of seconds — 20 to 27 measured on the Moto G at 1080p —
     * and WorkManager stops a worker that runs too long, so a batch is few enough to finish well
     * inside the window and the chain does the rest.
     */
    const val BATCH = 3
}
