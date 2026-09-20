package com.gallery.sync.ui.archive

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/**
 * When the Archive question is asked. Ian, 19 Sept 2026: he set Temp 5 to Archive, checked the files,
 * and *"NOTHING HAPPENED - the Tab just sat there"*. A Delay pressed earlier that day was still stored,
 * and the screen read a stored time as "delayed" for ever.
 */
class ArchiveDelayTest {

    private val now = Instant.parse("2026-09-19T22:00:00Z")

    private fun ready(delayedUntil: Instant?) =
        ArchiveUiState(phase = ArchivePhase.READY, delayedUntil = delayedUntil)

    @Test
    fun `the question is asked once the check is done and no delay was ever set`() {
        assertTrue(ready(null).showPrompt(now))
    }

    @Test
    fun `a delay that is still running keeps the question quiet`() {
        val state = ready(now.plusSeconds(3600))

        assertFalse(state.showPrompt(now))
        assertTrue(state.isDelayed(now))
    }

    @Test
    fun `a delay that has run out no longer keeps the question quiet`() {
        val state = ready(now.minusSeconds(6 * 3600))

        assertTrue("the wait is over, so it is asked", state.showPrompt(now))
        assertFalse(state.isDelayed(now))
    }

    @Test
    fun `the question is asked at the very moment a delay ends`() {
        assertTrue(ready(now).showPrompt(now))
    }

    @Test
    fun `it is never asked before the check has finished, whatever the delay`() {
        for (phase in listOf(ArchivePhase.IDLE, ArchivePhase.VALIDATING, ArchivePhase.REMOVING, ArchivePhase.DONE)) {
            assertFalse("$phase", ArchiveUiState(phase = phase, delayedUntil = null).showPrompt(now))
            assertFalse("$phase", ArchiveUiState(phase = phase, delayedUntil = now.minusSeconds(60)).showPrompt(now))
        }
    }
}
