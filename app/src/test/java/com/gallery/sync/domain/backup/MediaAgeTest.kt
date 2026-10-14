package com.gallery.sync.domain.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant

class MediaAgeTest {

    private val now: Instant = Instant.parse("2026-08-28T22:00:00Z")

    private fun hoursAgo(hours: Long): Instant = now.minus(Duration.ofHours(hours))

    @Test
    fun `the thresholds increase in order`() {
        val ordered = listOf(
            MediaAge.Immediately,
            MediaAge.OneHour,
            MediaAge.TwelveHours,
            MediaAge.OneDay,
            MediaAge.OneWeek
        )
        ordered.zipWithNext().forEach { (shorter, longer) ->
            assertTrue(
                "${shorter.name} should be shorter than ${longer.name}",
                shorter.duration < longer.duration
            )
        }
    }

    @Test
    fun `an unknown or absent stored value falls back to the cautious default`() {
        assertEquals(MediaAge.OneDay, MediaAge.DEFAULT)
        assertEquals(MediaAge.DEFAULT, MediaAge.fromNameOrDefault(null))
        assertEquals(MediaAge.DEFAULT, MediaAge.fromNameOrDefault("Fortnight"))
    }

    @Test
    fun `a week is the longest wait offered`() {
        assertEquals(MediaAge.OneWeek, MediaAge.entries.maxByOrNull { it.duration })
    }

    @Test
    fun `every value round-trips through its stored name`() {
        MediaAge.entries.forEach { assertEquals(it, MediaAge.fromNameOrDefault(it.name)) }
    }
}
