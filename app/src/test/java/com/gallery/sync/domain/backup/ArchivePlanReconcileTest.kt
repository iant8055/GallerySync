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

    private fun item(name: String, id: Long, album: String = "Temp 9") = LocalMediaItem(
        mediaStoreId = id,
        contentUri = mock<Uri>(),
        displayName = name,
        album = album,
        sizeBytes = 1_000L,
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
}
