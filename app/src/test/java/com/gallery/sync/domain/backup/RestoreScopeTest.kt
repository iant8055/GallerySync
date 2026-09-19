package com.gallery.sync.domain.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RestoreScopeTest {

    private data class Row(val album: String, val name: String, val size: Long)

    private val sig: (Row) -> String = { RestoreScope.signature(it.album, it.name, it.size) }

    private val archived = Row("PauseTest", "clip.mp4", 117_668_262L)
    private val stillHere = Row("BudgetVideo", "holiday.mp4", 500L)

    @Test
    fun `a file still in its own folder is not offered`() {
        val present = setOf(sig(stillHere))
        assertTrue(RestoreScope.notOnTheDevice(listOf(stillHere), present, sig).isEmpty())
    }

    /** The case Ian hit: gone from its album, byte-identical copy in another. */
    @Test
    fun `a copy in a different album does not count as present`() {
        val duplicateElsewhere = RestoreScope.signature("BudgetVideo", "clip.mp4", 117_668_262L)
        val offered = RestoreScope.notOnTheDevice(
            candidates = listOf(archived),
            presentOnDevice = setOf(duplicateElsewhere),
            signatureOf = sig
        )
        assertEquals(listOf(archived), offered)
    }

    @Test
    fun `the same name at a different size is a different file`() {
        val present = setOf(RestoreScope.signature("PauseTest", "clip.mp4", 999L))
        assertEquals(listOf(archived), RestoreScope.notOnTheDevice(listOf(archived), present, sig))
    }

    /** A failed scan must never be read as "the phone is empty". */
    @Test
    fun `an empty device scan offers nothing rather than everything`() {
        val offered = RestoreScope.notOnTheDevice(listOf(archived, stillHere), emptySet(), sig)
        assertTrue(offered.isEmpty())
    }

    // ── Optimised files: the size on the phone is the proxy's, not the original's ──

    @Test
    fun `an optimised file is judged at the size it has on the phone`() {
        assertEquals(
            410_879L,
            RestoreScope.onDiskSizeBytes(isProxied = true, localProxySizeBytes = 410_879L, sizeBytes = 2_423_443L)
        )
    }

    @Test
    fun `an ordinary file is judged at its own size`() {
        assertEquals(
            500L,
            RestoreScope.onDiskSizeBytes(isProxied = false, localProxySizeBytes = null, sizeBytes = 500L)
        )
    }

    @Test
    fun `an optimised row with no recorded proxy size falls back to the original`() {
        assertEquals(
            2_423_443L,
            RestoreScope.onDiskSizeBytes(isProxied = true, localProxySizeBytes = null, sizeBytes = 2_423_443L)
        )
    }

    /** The Test 4 / Test 5 case, 18 Sept 2026: an optimised photo whose album was then archived. */
    @Test
    fun `an archived optimised photo is offered, and one still in its folder is not`() {
        data class Optimised(val album: String, val name: String, val original: Long, val proxy: Long)

        val archivedProxy = Optimised("test 4", "a.jpg", original = 3_000_000L, proxy = 500_000L)
        val presentProxy = Optimised("test 2", "b.jpg", original = 3_842_069L, proxy = 747_517L)
        val onPhone = setOf(RestoreScope.signature(presentProxy.album, presentProxy.name, presentProxy.proxy))

        val offered = RestoreScope.notOnTheDevice(
            candidates = listOf(archivedProxy, presentProxy),
            presentOnDevice = onPhone,
            signatureOf = {
                RestoreScope.signature(
                    it.album,
                    it.name,
                    RestoreScope.onDiskSizeBytes(true, it.proxy, it.original)
                )
            }
        )

        assertEquals(listOf(archivedProxy), offered)
    }

    @Test
    fun `several albums are judged independently`() {
        val present = setOf(sig(stillHere))
        assertEquals(
            listOf(archived),
            RestoreScope.notOnTheDevice(listOf(archived, stillHere), present, sig)
        )
    }
}
