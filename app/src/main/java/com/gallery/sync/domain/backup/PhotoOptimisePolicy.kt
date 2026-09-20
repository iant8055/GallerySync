package com.gallery.sync.domain.backup

/**
 * When photos in Sync albums are optimised, once setup is over. Area 2 (CLAUDE.md's three areas): the
 * Settings tree, applied to Sync albums. It has nothing to do with [WizardBulkOptimise], which acts once
 * at install on the library choice alone, and neither reads the other's gates.
 *
 * Ian, 19 Sept 2026, on what the two modes mean: **Automatic** is *"as soon as a file hits an Album
 * whose mode is SYNC or an Album mode is switched to SYNC"*; **Manual** is *"through the Sync Now
 * button on the Albums tab"*. Both are still bounded by everything else: Sync albums only, verified in
 * OneDrive only, and never a photo the user has pinned at full size.
 *
 * Pure, so the rules are pinned by tests and not by watching a phone.
 */
object PhotoOptimisePolicy {

    /**
     * Whether photos may be optimised without anyone asking.
     *
     * [OptimiseMode.Manual] is not a way to switch this off, it is the user saying they will press
     * Sync now themselves, so it answers false here and [runsOnSyncNow] answers true.
     */
    fun runsAutomatically(
        setupComplete: Boolean,
        optimiseEnabled: Boolean,
        optimisePhotos: Boolean,
        mode: OptimiseMode
    ): Boolean = setupComplete && optimiseEnabled && optimisePhotos && mode == OptimiseMode.Auto

    /** Whether pressing **Sync now** should also optimise photos. The mirror of [runsAutomatically]. */
    fun runsOnSyncNow(
        setupComplete: Boolean,
        optimiseEnabled: Boolean,
        optimisePhotos: Boolean,
        mode: OptimiseMode
    ): Boolean = setupComplete && optimiseEnabled && optimisePhotos && mode == OptimiseMode.Manual

    /** Whether a chain already under way may go on: the switches still say photos are wanted. */
    fun mayContinue(
        setupComplete: Boolean,
        optimiseEnabled: Boolean,
        optimisePhotos: Boolean
    ): Boolean = setupComplete && optimiseEnabled && optimisePhotos
}
