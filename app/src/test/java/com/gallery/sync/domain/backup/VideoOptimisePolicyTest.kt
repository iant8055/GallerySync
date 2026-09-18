package com.gallery.sync.domain.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The rules for the ongoing video optimiser, which is what Settings' Optimise video controls drive.
 *
 * Two questions carry the risk. Whether it may start on its own: a wrong "yes" transcodes someone's
 * clips without their having asked, and a wrong "no" is the defect this feature was written to fix,
 * settings that are saved and change nothing. And whether a chain of batches ends: a chain that does
 * not is a phone re-encoding, on charge, for ever.
 */
class VideoOptimisePolicyTest {

    private fun automatic(
        setupComplete: Boolean = true,
        optimiseEnabled: Boolean = true,
        optimiseVideo: Boolean = true,
        mode: OptimiseMode = OptimiseMode.Auto
    ) = VideoOptimisePolicy.runsAutomatically(setupComplete, optimiseEnabled, optimiseVideo, mode)

    @Test
    fun runsOnItsOwnWhenEverySwitchSaysSo() {
        assertTrue(automatic())
    }

    @Test
    fun neverRunsBeforeSetupIsFinished() {
        // The wizard's own pass owns video until then, and two chains would transcode the same clips.
        assertFalse(automatic(setupComplete = false))
    }

    @Test
    fun theMasterSwitchAndTheVideoSwitchAreBothRequired() {
        assertFalse(automatic(optimiseEnabled = false))
        assertFalse(automatic(optimiseVideo = false))
    }

    @Test
    fun manualModeDoesNotStartOnItsOwn() {
        // Manual means "I will press the button", which is a different thing from Off.
        assertFalse(automatic(mode = OptimiseMode.Manual))
    }

    @Test
    fun aBatchThatDidNothingDoesNotQueueAnother() {
        assertFalse(VideoOptimisePolicy.shouldContinue(attempted = 0, moreReady = 5, excludedCount = 0))
    }

    @Test
    fun aBatchWithNothingLeftBehindItEnds() {
        assertFalse(VideoOptimisePolicy.shouldContinue(attempted = 3, moreReady = 0, excludedCount = 0))
    }

    @Test
    fun aBatchThatMadeProgressWithMoreToDoContinues() {
        assertTrue(VideoOptimisePolicy.shouldContinue(attempted = 3, moreReady = 7, excludedCount = 0))
    }

    @Test
    fun aBatchOfOnlyFailuresStillContinuesBecauseTheFailedClipsAreSteppedOver() {
        // A failure adds the clip to the chain's exclusions, so the list shrinks even though nothing
        // was optimised, and the clips behind the failing one are reached.
        assertTrue(VideoOptimisePolicy.shouldContinue(attempted = 3, moreReady = 4, excludedCount = 3))
    }

    @Test
    fun aChainThatHasFailedOnTooManyClipsStopsRatherThanForgetting() {
        val limit = VideoOptimisePolicy.MAX_EXCLUDED
        assertTrue(VideoOptimisePolicy.shouldContinue(attempted = 1, moreReady = 1, excludedCount = limit))
        assertFalse(VideoOptimisePolicy.shouldContinue(attempted = 1, moreReady = 1, excludedCount = limit + 1))
    }

    @Test
    fun theExclusionsFitInWorkManagersInputData() {
        // Input data is capped at 10 KB. A ledger id is a path, a name and two numbers.
        val longestPlausibleId = 160
        assertTrue(VideoOptimisePolicy.MAX_EXCLUDED * longestPlausibleId < 10_240)
    }

    @Test
    fun aBatchIsSmallEnoughToFinishInsideTheWorkersWindow() {
        // 20 to 27 seconds a clip was measured at 1080p on the Moto G; WorkManager allows ten minutes.
        assertEquals(3, VideoOptimisePolicy.BATCH)
    }
}
