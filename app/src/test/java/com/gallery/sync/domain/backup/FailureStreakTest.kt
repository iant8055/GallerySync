package com.gallery.sync.domain.backup

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FailureStreakTest {

    @Test
    fun `stops only after the limit in a row`() {
        val streak = FailureStreak(limit = 3)

        assertFalse(streak.failed())
        assertFalse(streak.failed())
        assertTrue(streak.failed())
    }

    @Test
    fun `a success in between starts the count again`() {
        val streak = FailureStreak(limit = 3)

        assertFalse(streak.failed())
        assertFalse(streak.failed())
        streak.succeeded()
        assertFalse(streak.failed())
        assertFalse(streak.failed())
        assertTrue(streak.failed())
    }

    @Test
    fun `one bad file does not stop a run`() {
        val streak = FailureStreak()

        assertFalse(streak.failed())
        streak.succeeded()
        streak.succeeded()
        assertFalse(streak.failed())
    }
}
