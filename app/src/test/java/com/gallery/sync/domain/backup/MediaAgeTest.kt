package com.gallery.sync.domain.backup

import org.junit.Assert.assertEquals
import org.junit.Test

/** Sync's video *Older than*: the Archive tab's two menus, with the old fixed list still read back. */
class MediaAgeTest {

    @Test
    fun `an unknown or absent stored value falls back to a day`() {
        assertEquals(ArchiveAge(1, ArchiveAgeUnit.Day), MediaAge.DEFAULT)
        assertEquals(MediaAge.DEFAULT, MediaAge.fromNameOrDefault(null))
        assertEquals(MediaAge.DEFAULT, MediaAge.fromNameOrDefault("Fortnight"))
        assertEquals(MediaAge.DEFAULT, MediaAge.fromNameOrDefault("12 Hour"))
    }

    @Test
    fun `a value saved by the old fixed list keeps its meaning`() {
        assertEquals(ArchiveAge.All, MediaAge.fromNameOrDefault("Immediately"))
        assertEquals(ArchiveAge(1, ArchiveAgeUnit.Hour), MediaAge.fromNameOrDefault("OneHour"))
        assertEquals(ArchiveAge(1, ArchiveAgeUnit.Day), MediaAge.fromNameOrDefault("OneDay"))
        assertEquals(ArchiveAge(1, ArchiveAgeUnit.Week), MediaAge.fromNameOrDefault("OneWeek"))
    }

    @Test
    fun `12 hours has no place on a 1 to 9 scale and reads as the longer wait`() {
        val read = MediaAge.fromNameOrDefault("TwelveHours")
        assertEquals(ArchiveAge(1, ArchiveAgeUnit.Day), read)
    }

    @Test
    fun `every choice round-trips through its stored name`() {
        val all = ArchiveAge.COUNTS.flatMap { n ->
            ArchiveAgeUnit.entries.filter { it != ArchiveAgeUnit.All }.map { ArchiveAge(n, it) }
        } + ArchiveAge.All
        all.forEach { assertEquals(it, MediaAge.fromNameOrDefault(it.storedName)) }
    }

    @Test
    fun `Straight away is the zero entry`() {
        assertEquals(ArchiveAge.All, MediaAge.Immediately)
        assertEquals(0L, MediaAge.Immediately.duration.seconds)
    }
}
