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
    fun `Immediately reaches a file modified seconds ago`() {
        assertTrue(MediaAge.Immediately.hasElapsedFor(now.minusSeconds(5), now))
    }

    /**
     * The boundary, and it is inclusive.
     *
     * A file exactly at the threshold is old enough. The alternative leaves a clip sitting one
     * second short for another whole run, which reads as the setting not working.
     */
    @Test
    fun `a file exactly at the threshold qualifies`() {
        assertTrue(MediaAge.OneHour.hasElapsedFor(hoursAgo(1), now))
        assertTrue(MediaAge.TwelveHours.hasElapsedFor(hoursAgo(12), now))
        assertTrue(MediaAge.OneDay.hasElapsedFor(hoursAgo(24), now))
    }

    @Test
    fun `a file just short of the threshold does not qualify`() {
        assertFalse(MediaAge.OneHour.hasElapsedFor(now.minusSeconds(3599), now))
        assertFalse(MediaAge.TwelveHours.hasElapsedFor(hoursAgo(11), now))
        assertFalse(MediaAge.OneDay.hasElapsedFor(hoursAgo(23), now))
    }

    /**
     * A file modified in the future is not old.
     *
     * Not hypothetical: a wrong device clock, a restored backup, or a camera with the date unset all
     * produce them, and the arithmetic must not wrap round into "very old indeed".
     */
    @Test
    fun `a file dated in the future never qualifies except Immediately`() {
        val tomorrow = now.plus(Duration.ofDays(1))

        assertFalse(MediaAge.OneHour.hasElapsedFor(tomorrow, now))
        assertFalse(MediaAge.OneDay.hasElapsedFor(tomorrow, now))
        assertFalse(MediaAge.Immediately.hasElapsedFor(tomorrow, now))
    }

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

    /**
     * The test is per file, so one setting sorts a mixed album in a single pass - which is the
     * behaviour behind "everything old in a Sync album becomes eligible at once". The album scoping
     * itself lives in `proxyCandidates`, not here.
     */
    @Test
    fun `one threshold sorts files of different ages independently`() {
        val age = MediaAge.OneDay
        val lastYear = now.minus(Duration.ofDays(365))
        val anHourAgo = hoursAgo(1)

        assertTrue(age.hasElapsedFor(lastYear, now))
        assertFalse(age.hasElapsedFor(anHourAgo, now))
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
