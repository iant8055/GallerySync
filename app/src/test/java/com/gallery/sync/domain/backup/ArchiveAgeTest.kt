package com.gallery.sync.domain.backup

import android.net.Uri
import com.gallery.sync.data.local.media.LocalMediaItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.mock
import java.time.Instant

/**
 * What the Archive tab's *Only show files older than* control will hold back.
 *
 * Ian's list, 22 Sept 2026: 1 hour, 1 day, 1 week, 1 month, 1 year, All — a shorter first step than
 * [CameraOptimiseAge]'s, because Archive removes a file outright rather than shrinking it.
 */
class ArchiveAgeTest {

    /** A Tuesday noon, well clear of any boundary. */
    private val now: Instant = Instant.parse("2026-09-22T12:00:00Z")
    private val hour = 3_600L
    private val day = 86_400L

    private fun item(name: String, ageSeconds: Long) = LocalMediaItem(
        mediaStoreId = name.hashCode().toLong(),
        contentUri = mock<Uri>(),
        displayName = name,
        album = "Temp 9",
        sizeBytes = 1_000L,
        dateModifiedEpochSeconds = now.epochSecond - ageSeconds,
        mimeType = "image/jpeg",
        isVideo = false,
        relativePath = "DCIM/Temp 9/"
    )

    private fun names(list: List<LocalMediaItem>) = list.map { it.displayName }

    @Test
    fun `the ages are Ian's list in order, with All last`() {
        assertEquals(
            listOf(1L, 24L, 168L, 720L, 8_760L, 0L),
            ArchiveAge.entries.map { it.duration.toHours() }
        )
        assertEquals(ArchiveAge.All, ArchiveAge.entries.last())
    }

    @Test
    fun `the default is All, so out of the box nothing is held back`() {
        assertEquals(ArchiveAge.All, ArchiveAge.DEFAULT)
        assertEquals(ArchiveAge.All, ArchiveAge.fromNameOrDefault(null))
        assertEquals(ArchiveAge.All, ArchiveAge.fromNameOrDefault("not a real one"))
    }

    @Test
    fun `All holds nothing back, not even a file modified this minute`() {
        val fresh = item("fresh.jpg", ageSeconds = 0)
        val old = item("old.jpg", ageSeconds = 900 * day)
        val result = ArchiveAge.All.split(listOf(fresh, old), now)
        assertEquals(setOf("fresh.jpg", "old.jpg"), names(result.eligible).toSet())
        assertTrue(result.hiddenByAge.isEmpty())
    }

    @Test
    fun `a file exactly on the line is old enough and one second newer is not`() {
        val threshold = ArchiveAge.OneDay.thresholdEpochSeconds(now)
        assertEquals(now.epochSecond - day, threshold)
        val onTheLine = item("on.jpg", ageSeconds = day)
        val justNewer = item("newer.jpg", ageSeconds = day - 1)
        val result = ArchiveAge.OneDay.split(listOf(onTheLine, justNewer), now)
        assertEquals(listOf("on.jpg"), names(result.eligible))
        assertEquals(listOf("newer.jpg"), names(result.hiddenByAge))
    }

    @Test
    fun `age is read from the modified time and a longer age holds back more`() {
        val recent = item("recent.jpg", ageSeconds = 3 * hour)
        val old = item("old.jpg", ageSeconds = 400 * day)

        assertEquals(listOf("old.jpg"), names(ArchiveAge.OneDay.split(listOf(recent, old), now).eligible))
        assertEquals(
            setOf("recent.jpg", "old.jpg"),
            names(ArchiveAge.OneHour.split(listOf(recent, old), now).eligible).toSet()
        )
        assertEquals(listOf("old.jpg"), names(ArchiveAge.OneYear.split(listOf(recent, old), now).eligible))
    }

    @Test
    fun `a file held back by age is neither eligible nor lost — it is in the other list`() {
        val young = item("young.jpg", ageSeconds = hour)
        val result = ArchiveAge.OneWeek.split(listOf(young), now)
        assertTrue(result.eligible.isEmpty())
        assertEquals(listOf("young.jpg"), names(result.hiddenByAge))
    }

    @Test
    fun `an empty list splits into two empty lists`() {
        val result = ArchiveAge.OneMonth.split(emptyList(), now)
        assertTrue(result.eligible.isEmpty())
        assertTrue(result.hiddenByAge.isEmpty())
    }
}
