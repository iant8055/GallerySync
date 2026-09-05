package com.gallery.sync.domain.backup

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * When a drained upload chain may start the install wizard's one-time optimise on its own.
 *
 * The interesting cases are all refusals. Saying yes too readily means files get rewritten during an
 * ordinary backup months after install, with the original left only in OneDrive — so every gate is
 * tested for the case it exists to stop, not just for the happy path.
 */
class WizardBulkOptimiseTest {

    @Test
    fun theTwoOptimisingChoicesHandOff() {
        assertTrue(
            WizardBulkOptimise.shouldHandOff(
                setupComplete = false,
                allAlbums = true,
                choice = LibraryChoice.BACK_UP_AND_FREE_SPACE
            )
        )
        assertTrue(
            WizardBulkOptimise.shouldHandOff(
                setupComplete = false,
                allAlbums = true,
                choice = LibraryChoice.BACK_UP_AND_OPTIMISE_NEW
            )
        )
    }

    @Test
    fun backingUpWithoutOptimisingDoesNot() {
        assertFalse(
            WizardBulkOptimise.shouldHandOff(
                setupComplete = false,
                allAlbums = true,
                choice = LibraryChoice.BACK_UP_EVERYTHING
            )
        )
    }

    @Test
    fun choosingPerAlbumDoesNot() {
        // Mode is null for this one, so the gate has to survive the absent mode rather than assume
        // one — this is the choice whose entire purpose is that nothing happens to the library.
        assertFalse(
            WizardBulkOptimise.shouldHandOff(
                setupComplete = false,
                allAlbums = true,
                choice = LibraryChoice.CHOOSE_PER_ALBUM
            )
        )
    }

    @Test
    fun aFinishedSetupNeverHandsOff() {
        // The install pass is one-time. Without this, every later backup that drained would start a
        // bulk optimise over the whole library.
        assertFalse(
            WizardBulkOptimise.shouldHandOff(
                setupComplete = true,
                allAlbums = true,
                choice = LibraryChoice.BACK_UP_AND_FREE_SPACE
            )
        )
    }

    @Test
    fun anOrdinaryRunNeverHandsOff() {
        // "Sync now" is manual too, so manual alone cannot tell the wizard's run apart from a user's.
        // allAlbums is what distinguishes them: only the wizard routes past album-mode filtering.
        assertFalse(
            WizardBulkOptimise.shouldHandOff(
                setupComplete = false,
                allAlbums = false,
                choice = LibraryChoice.BACK_UP_AND_FREE_SPACE
            )
        )
    }

    @Test
    fun videoFollowsPhotosOnTheSameChoices() {
        assertTrue(
            WizardBulkOptimise.shouldContinueToVideo(
                setupComplete = false,
                choice = LibraryChoice.BACK_UP_AND_OPTIMISE_NEW
            )
        )
        assertFalse(
            WizardBulkOptimise.shouldContinueToVideo(
                setupComplete = false,
                choice = LibraryChoice.BACK_UP_EVERYTHING
            )
        )
        assertFalse(
            WizardBulkOptimise.shouldContinueToVideo(
                setupComplete = true,
                choice = LibraryChoice.BACK_UP_AND_OPTIMISE_NEW
            )
        )
    }
}
