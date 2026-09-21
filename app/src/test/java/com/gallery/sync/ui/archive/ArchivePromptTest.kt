package com.gallery.sync.ui.archive

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * When the Archive question is asked. It used to be silenced for a time by a Delay button; that was
 * removed on 21 Sept 2026 (Ian: it was confusing), so the rule is now only that the check is done.
 */
class ArchivePromptTest {

    @Test
    fun `the question is asked once the check is done`() {
        assertTrue(ArchiveUiState(phase = ArchivePhase.READY).showPrompt())
    }

    @Test
    fun `it is never asked before the check has finished`() {
        for (phase in listOf(ArchivePhase.IDLE, ArchivePhase.VALIDATING, ArchivePhase.REMOVING, ArchivePhase.DONE)) {
            assertFalse("$phase", ArchiveUiState(phase = phase).showPrompt())
        }
    }
}
