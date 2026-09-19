package com.gallery.sync.domain.backup

import android.net.Uri
import com.gallery.sync.data.local.media.LocalMediaItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.mock

/**
 * Matching the Archive plan to the phone after a swipe or a new file. The property that matters is
 * that the plan only loses files or gains **unchecked** ones, so nothing unconfirmed can be removed:
 * the removal step acts on [ArchivePlan.confirmed] alone. See [reconciledWith].
 */
class ArchivePlanReconcileTest {

    private fun item(name: String, id: Long, album: String = "Temp 9", size: Long = 1_000L) = LocalMediaItem(
        mediaStoreId = id,
        contentUri = mock<Uri>(),
        displayName = name,
        album = album,
        sizeBytes = size,
        dateModifiedEpochSeconds = 1L,
        mimeType = "image/jpeg",
        isVideo = false,
        relativePath = "DCIM/$album/"
    )

    private val a = item("a.jpg", 1L)
    private val b = item("b.jpg", 2L)
    private val c = item("c.jpg", 3L)

    private fun checkedPlan(vararg items: LocalMediaItem) = ArchivePlan(
        entries = items.map { ArchiveEntry(it, mark = ArchiveMark.CONFIRMED) },
        validated = true
    )

    @Test
    fun `a file swiped out leaves the plan and the rest keep their marks`() {
        val result = checkedPlan(a, b, c).reconciledWith(
            ArchiveFiles(toArchive = listOf(a, c), optedOut = listOf(b)),
            checkFinished = true
        )

        assertEquals(listOf("a.jpg", "c.jpg"), result.plan.entries.map { it.name })
        assertTrue("the check still describes what is left", result.plan.entries.all { it.mark == ArchiveMark.CONFIRMED })
        assertTrue(result.plan.validated)
        assertFalse(result.needsRecheck)
    }

    @Test
    fun `a file put back joins unchecked and the finished check is dropped`() {
        val result = checkedPlan(a, c).reconciledWith(
            ArchiveFiles(toArchive = listOf(a, b, c)),
            checkFinished = true
        )

        assertEquals(listOf("a.jpg", "b.jpg", "c.jpg"), result.plan.entries.map { it.name })
        assertTrue("nothing is confirmed until it is checked again", result.plan.confirmed.isEmpty())
        assertTrue(result.plan.entries.all { it.mark == ArchiveMark.WAITING })
        assertFalse(result.plan.validated)
        assertTrue(result.needsRecheck)
    }

    @Test
    fun `a file that arrived joins unchecked`() {
        val fresh = item("new.jpg", 9L)
        val result = checkedPlan(a).reconciledWith(
            ArchiveFiles(toArchive = listOf(a, fresh)),
            checkFinished = true
        )

        assertEquals(ArchiveMark.WAITING, result.plan.entries.single { it.name == "new.jpg" }.mark)
        assertTrue(result.plan.confirmed.isEmpty())
        assertTrue(result.needsRecheck)
    }

    @Test
    fun `a file gone from the phone leaves the plan`() {
        val result = checkedPlan(a, b).reconciledWith(
            ArchiveFiles(toArchive = listOf(a)),
            checkFinished = true
        )

        assertEquals(listOf("a.jpg"), result.plan.entries.map { it.name })
        assertEquals(ArchiveMark.CONFIRMED, result.plan.entries.single().mark)
    }

    @Test
    fun `opting out every confirmed file leaves nothing to offer and asks for a new check`() {
        val result = checkedPlan(a).reconciledWith(
            ArchiveFiles(toArchive = emptyList(), optedOut = listOf(a)),
            checkFinished = true
        )

        assertTrue(result.plan.isEmpty)
        assertTrue("the prompt must not stay up offering an empty set", result.needsRecheck)
    }

    @Test
    fun `nothing unconfirmed can become confirmed by a merge`() {
        val plan = ArchivePlan(
            entries = listOf(
                ArchiveEntry(a, mark = ArchiveMark.CONFIRMED),
                ArchiveEntry(b, mark = ArchiveMark.FAILED, failure = ArchiveFailure.NOT_BACKED_UP)
            ),
            validated = true
        )

        val result = plan.reconciledWith(ArchiveFiles(toArchive = listOf(a, b, c)), checkFinished = false)

        assertEquals(setOf("a.jpg"), result.plan.confirmed.map { it.name }.toSet())
    }

    @Test
    fun `without a finished check the marks are left alone and only the validated flag drops`() {
        val plan = ArchivePlan(entries = listOf(ArchiveEntry(a)), validated = false)

        val result = plan.reconciledWith(ArchiveFiles(toArchive = listOf(a, b)), checkFinished = false)

        assertEquals(listOf("a.jpg", "b.jpg"), result.plan.entries.map { it.name })
        assertFalse(result.needsRecheck)
        assertFalse(result.plan.validated)
    }

    @Test
    fun `entries stay in album and name order`() {
        val other = item("z.jpg", 7L, album = "Temp 1")
        val result = ArchivePlan().reconciledWith(
            ArchiveFiles(toArchive = listOf(c, other, a)),
            checkFinished = false
        )

        assertEquals(listOf("z.jpg", "a.jpg", "c.jpg"), result.plan.entries.map { it.name })
    }

    // ── A confirmed file swiped out and back keeps its tick (Ian, 19 Sept 2026) ─────────────

    @Test
    fun `a confirmed file swiped out and back returns with its tick and nothing else changes`() {
        val out = checkedPlan(a, b, c).reconciledWith(
            ArchiveFiles(toArchive = listOf(a, c), optedOut = listOf(b)),
            checkFinished = true
        )
        assertEquals("the swiped-out file's confirmation is remembered", setOf(2L), out.setAside.keys)

        val back = out.plan.reconciledWith(
            ArchiveFiles(toArchive = listOf(a, b, c)),
            checkFinished = true,
            setAside = out.setAside
        )

        assertEquals(listOf("a.jpg", "b.jpg", "c.jpg"), back.plan.entries.map { it.name })
        assertTrue("every file is ticked again", back.plan.entries.all { it.mark == ArchiveMark.CONFIRMED })
        assertTrue("the finished check still stands", back.plan.validated)
        assertFalse(back.needsRecheck)
        assertTrue(back.setAside.isEmpty())
    }

    @Test
    fun `a file that changed while it was set aside is checked again`() {
        val out = checkedPlan(a, b).reconciledWith(
            ArchiveFiles(toArchive = listOf(a), optedOut = listOf(b)),
            checkFinished = true
        )
        val edited = item("b.jpg", 2L, size = 2_500L)

        val back = out.plan.reconciledWith(
            ArchiveFiles(toArchive = listOf(a, edited)),
            checkFinished = true,
            setAside = out.setAside
        )

        assertTrue("an edited file is a different file", back.needsRecheck)
        assertTrue(back.plan.confirmed.isEmpty())
    }

    @Test
    fun `a file that failed the check is not remembered`() {
        val plan = ArchivePlan(
            entries = listOf(
                ArchiveEntry(a, mark = ArchiveMark.CONFIRMED),
                ArchiveEntry(b, mark = ArchiveMark.FAILED, failure = ArchiveFailure.NOT_BACKED_UP)
            ),
            validated = true
        )
        val out = plan.reconciledWith(ArchiveFiles(toArchive = listOf(a), optedOut = listOf(b)), checkFinished = true)
        assertTrue(out.setAside.isEmpty())

        val back = out.plan.reconciledWith(ArchiveFiles(toArchive = listOf(a, b)), checkFinished = true)

        assertEquals(ArchiveMark.WAITING, back.plan.entries.single { it.name == "b.jpg" }.mark)
        assertTrue(back.needsRecheck)
    }

    @Test
    fun `a file that has never been checked still needs a check even as a set-aside file returns`() {
        val out = checkedPlan(a, b).reconciledWith(
            ArchiveFiles(toArchive = listOf(a), optedOut = listOf(b)),
            checkFinished = true
        )
        val fresh = item("new.jpg", 9L)

        val back = out.plan.reconciledWith(
            ArchiveFiles(toArchive = listOf(a, b, fresh)),
            checkFinished = true,
            setAside = out.setAside
        )

        assertTrue(back.needsRecheck)
        assertTrue("nothing stays confirmed once a new file needs checking", back.plan.confirmed.isEmpty())
        assertTrue(back.setAside.isEmpty())
    }

    @Test
    fun `several files set aside are remembered and returned independently`() {
        val first = checkedPlan(a, b, c).reconciledWith(
            ArchiveFiles(toArchive = listOf(c), optedOut = listOf(a, b)),
            checkFinished = true
        )
        assertEquals(setOf(1L, 2L), first.setAside.keys)

        val second = first.plan.reconciledWith(
            ArchiveFiles(toArchive = listOf(b, c), optedOut = listOf(a)),
            checkFinished = true,
            setAside = first.setAside
        )

        assertEquals(setOf("b.jpg", "c.jpg"), second.plan.confirmed.map { it.name }.toSet())
        assertEquals("a is still set aside", setOf(1L), second.setAside.keys)
        assertFalse(second.needsRecheck)
    }

    @Test
    fun `nothing is remembered or restored when no check has finished`() {
        val plan = ArchivePlan(entries = listOf(ArchiveEntry(a), ArchiveEntry(b)))

        val out = plan.reconciledWith(ArchiveFiles(toArchive = listOf(a), optedOut = listOf(b)), checkFinished = false)
        assertTrue(out.setAside.isEmpty())

        val stale = mapOf(2L to ArchiveEntry(b, mark = ArchiveMark.CONFIRMED))
        val back = out.plan.reconciledWith(ArchiveFiles(toArchive = listOf(a, b)), checkFinished = false, setAside = stale)

        assertTrue("a remembered tick is never applied outside a finished check", back.plan.confirmed.isEmpty())
    }
}
