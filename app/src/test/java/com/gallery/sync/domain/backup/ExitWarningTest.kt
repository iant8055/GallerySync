package com.gallery.sync.domain.backup

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExitWarningTest {

    @Test
    fun `nothing waiting means no warning`() {
        assertFalse(ExitWarning.shouldWarn(readyCount = 0))
    }

    @Test
    fun `files waiting warns`() {
        assertTrue(ExitWarning.shouldWarn(readyCount = 12))
        assertTrue(ExitWarning.shouldWarn(readyCount = 1))
    }
}
