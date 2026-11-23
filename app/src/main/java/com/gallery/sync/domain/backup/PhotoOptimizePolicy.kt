package com.gallery.sync.domain.backup

/**
 * When photos in Sync albums are optimized, once setup is over. Area 2 (CLAUDE.md's three areas): the
 * Settings tree, applied to Sync albums. It has nothing to do with [WizardBulkOptimize], which acts once
 * at install on the library choice alone, and neither reads the other's gates.
 *
 * Ian, 19 Sept 2026, on what the two modes mean: **Automatic** is *"as soon as a file hits an Album
 * whose mode is SYNC or an Album mode is switched to SYNC"*; **Manual** is *"through the Sync Now
 * button on the Albums tab"*. Both are still bounded by everything else: Sync albums only, verified in
 * OneDrive only, and never a photo the user has pinned at full size.
 *
 * Pure, so the rules are pinned by tests and not by watching a phone.
 */
object PhotoOptimizePolicy {

    /**
     * Whether photos may be optimized without anyone asking.
     *
     * [OptimizeMode.Manual] is not a way to switch this off, it is the user saying they will press
     * Sync now themselves, so it answers false here and [runsOnSyncNow] answers true.
     */
    fun runsAutomatically(
        setupComplete: Boolean,
        optimizeEnabled: Boolean,
        optimizePhotos: Boolean,
        mode: OptimizeMode
    ): Boolean = setupComplete && optimizeEnabled && optimizePhotos && mode == OptimizeMode.Auto

    /** Whether pressing **Sync now** should also optimize photos. The mirror of [runsAutomatically]. */
    fun runsOnSyncNow(
        setupComplete: Boolean,
        optimizeEnabled: Boolean,
        optimizePhotos: Boolean,
        mode: OptimizeMode
    ): Boolean = setupComplete && optimizeEnabled && optimizePhotos && mode == OptimizeMode.Manual

    /** Whether a chain already under way may go on: the switches still say photos are wanted. */
    fun mayContinue(
        setupComplete: Boolean,
        optimizeEnabled: Boolean,
        optimizePhotos: Boolean
    ): Boolean = setupComplete && optimizeEnabled && optimizePhotos
}
