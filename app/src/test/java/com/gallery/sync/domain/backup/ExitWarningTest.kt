package com.gallery.sync.domain.backup

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class ExitWarningTest {

    private val now: Instant = Instant.parse("2026-08-28T20:00:00Z")

    @Test
    fun `nothing waiting means no warning`() {
        assertFalse(ExitWarning.shouldWarn(readyCount = 0, delayedUntilEpochMillis = 0L, now = now))
    }

    @Test
    fun `files waiting and no delay set warns`() {
        assertTrue(ExitWarning.shouldWarn(readyCount = 12, delayedUntilEpochMillis = 0L, now = now))
    }

    @Test
    fun `a live delay silences the warning`() {
        val inAnHour = now.plusSeconds(3600).toEpochMilli()
        assertFalse(ExitWarning.shouldWarn(readyCount = 12, delayedUntilEpochMillis = inAnHour, now = now))
    }

    @Test
    fun `an expired delay warns again`() {
        val anHourAgo = now.minusSeconds(3600).toEpochMilli()
        assertTrue(ExitWarning.shouldWarn(readyCount = 12, delayedUntilEpochMillis = anHourAgo, now = now))
    }

    @Test
    fun `a delay expiring exactly now warns`() {
        assertTrue(ExitWarning.shouldWarn(readyCount = 1, delayedUntilEpochMillis = now.toEpochMilli(), now = now))
    }

    @Test
    fun `a delay cannot resurrect a warning with nothing to say`() {
        val anHourAgo = now.minusSeconds(3600).toEpochMilli()
        assertFalse(ExitWarning.shouldWarn(readyCount = 0, delayedUntilEpochMillis = anHourAgo, now = now))
    }
}
