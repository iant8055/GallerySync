package com.gallery.sync.domain.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoQualityTest {

    @Test
    fun `an unknown or absent stored value falls back to the default`() {
        assertEquals(VideoQuality.DEFAULT, VideoQuality.fromNameOrDefault(null))
        assertEquals(VideoQuality.DEFAULT, VideoQuality.fromNameOrDefault(""))
        assertEquals(VideoQuality.DEFAULT, VideoQuality.fromNameOrDefault("Ludicrous"))
    }

    @Test
    fun `every level round-trips through its stored name`() {
        VideoQuality.entries.forEach {
            assertEquals(it, VideoQuality.fromNameOrDefault(it.name))
        }
    }

    /**
     * The direction has to hold, because the labels lean on it: High is the smallest file and the
     * largest saving. If a level were ever reordered or re-valued, "High - saves about 90%" would
     * quietly start lying.
     */
    @Test
    fun `higher shrinking means a smaller target and a larger saving`() {
        val ordered = listOf(VideoQuality.High, VideoQuality.Medium, VideoQuality.Low)

        ordered.zipWithNext().forEach { (more, less) ->
            assertTrue(
                "${more.name} should target a smaller short side than ${less.name}",
                more.targetShortSide < less.targetShortSide
            )
            assertTrue(
                "${more.name} should save more than ${less.name}",
                more.approximateSavingPercent > less.approximateSavingPercent
            )
        }
    }

    /** Low keeps full 1080p resolution - it is a re-encode, and the copy says so. */
    @Test
    fun `the gentlest level still keeps 1080p`() {
        assertEquals(1080, VideoQuality.Low.targetShortSide)
    }

    @Test
    fun `the default is the one the evidence supported`() {
        assertEquals(VideoQuality.High, VideoQuality.DEFAULT)
    }
}
