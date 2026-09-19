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

    // ── Files OneDrive holds that the ledger has no row for ────────────────

    private fun classify(
        album: String = "test 4",
        name: String = "a.jpg",
        size: Long = 3_000_000L,
        present: Set<String> = emptySet(),
        presentNames: Set<String> = emptySet(),
        ledger: Set<String> = emptySet()
    ) = RestoreScope.classifyDriveFile(album, name, size, present, presentNames, ledger)

    /** Test 4 and Test 5, 18 Sept 2026: in OneDrive, gone from the phone, no ledger row. */
    @Test
    fun `a file OneDrive holds that the phone lacks and the ledger never heard of is a download`() {
        assertEquals(RestoreScope.DriveFileState.MISSING, classify())
    }

    @Test
    fun `a file the phone has at the same size is here whatever the ledger says`() {
        val present = setOf(RestoreScope.presenceSignature("test 4", "a.jpg", 3_000_000L))
        assertEquals(RestoreScope.DriveFileState.HERE, classify(present = present))
        assertEquals(RestoreScope.DriveFileState.HERE, classify(present = present, ledger = setOf("a.jpg")))
    }

    @Test
    fun `a file the ledger has a row for is left to the ledger's own lists`() {
        assertEquals(RestoreScope.DriveFileState.LEDGER_HANDLES, classify(ledger = setOf("a.jpg")))
    }

    /** Overwriting an edit is the one thing Restore must never do, so it is neither offered nor greyed. */
    @Test
    fun `the same name at a different size is not offered`() {
        val names = setOf(RestoreScope.presenceName("test 4", "a.jpg"))
        assertEquals(RestoreScope.DriveFileState.SAME_NAME_OTHER_SIZE, classify(presentNames = names))
    }

    @Test
    fun `presence is judged without regard to the case of the folder or the name`() {
        val present = setOf(RestoreScope.presenceSignature("Camera", "IMG_1.JPG", 10L))
        assertEquals(
            RestoreScope.DriveFileState.HERE,
            classify(album = "camera", name = "img_1.jpg", size = 10L, present = present)
        )
    }

    @Test
    fun `only photos and videos are offered`() {
        assertTrue(RestoreScope.isMedia("image/jpeg", "a.jpg"))
        assertTrue(RestoreScope.isMedia("video/mp4", "a.mp4"))
        assertTrue(!RestoreScope.isMedia("application/pdf", "notes.pdf"))
        // No useful type from the listing: fall back to the extension.
        assertTrue(RestoreScope.isMedia("application/octet-stream", "IMG_0001.HEIC"))
        assertTrue(!RestoreScope.isMedia("application/octet-stream", "notes.txt"))
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
