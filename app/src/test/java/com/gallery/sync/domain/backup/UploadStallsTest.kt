package com.gallery.sync.domain.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UploadStallsTest {

    @Test
    fun `an interruption that gained ground is not a stall`() {
        val after = UploadStalls.afterInterruption(mapOf("a" to 1), "a", gainedGround = true)
        assertTrue("a" !in after)
    }

    @Test
    fun `two interruptions that gained nothing stall the file`() {
        var counts = emptyMap<String, Int>()
        counts = UploadStalls.afterInterruption(counts, "a", gainedGround = false)
        assertTrue(UploadStalls.stalled(counts).isEmpty())
        counts = UploadStalls.afterInterruption(counts, "a", gainedGround = false)
        assertEquals(setOf("a"), UploadStalls.stalled(counts))
    }

    @Test
    fun `progress after a stall clears it`() {
        val counts = UploadStalls.afterInterruption(mapOf("a" to 3), "a", gainedGround = true)
        assertTrue(UploadStalls.stalled(counts).isEmpty())
    }

    @Test
    fun `a finished upload clears it`() {
        assertTrue(UploadStalls.cleared(mapOf("a" to 5), "a").isEmpty())
    }

    @Test
    fun `stalled files go to the end, the rest keep their order`() {
        val out = UploadStalls.lastIfStalled(listOf("x", "a", "y", "b", "z"), setOf("a", "b")) { it }
        assertEquals(listOf("x", "y", "z", "a", "b"), out)
    }

    @Test
    fun `round trips through storage, ids with odd characters included`() {
        val counts = mapOf("Iwa/clip\twith tab.mp4" to 2, "b" to 1)
        assertEquals(counts, UploadStalls.decode(UploadStalls.encode(counts)))
    }

    @Test
    fun `unreadable lines are ignored`() {
        assertEquals(mapOf("a" to 2), UploadStalls.decode(setOf("a\t2", "garbage", "b\tx")))
    }
}
