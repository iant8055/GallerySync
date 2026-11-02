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
    fun `the units are in order with All last, and the number multiplies them`() {
        assertEquals(
            listOf(1L, 24L, 168L, 720L, 8_760L, 0L),
            ArchiveAgeUnit.entries.map { it.duration.toHours() }
        )
        assertEquals(ArchiveAgeUnit.All, ArchiveAgeUnit.entries.last())
        assertEquals(1..9, ArchiveAge.COUNTS)
        assertEquals(9 * 8_760L, ArchiveAge(9, ArchiveAgeUnit.Year).duration.toHours())
        assertEquals(3 * 720L, ArchiveAge(3, ArchiveAgeUnit.Month).duration.toHours())
    }

    @Test
    fun `All waits for nothing whatever number was showing before it`() {
        assertEquals(0L, ArchiveAge(7, ArchiveAgeUnit.All).duration.toHours())
    }

    @Test
    fun `a stored age reads back as itself`() {
        ArchiveAgeUnit.entries.filter { it != ArchiveAgeUnit.All }.forEach { unit ->
            ArchiveAge.COUNTS.forEach { count ->
                val age = ArchiveAge(count, unit)
                assertEquals(age, ArchiveAge.fromNameOrDefault(age.storedName))
            }
        }
        assertEquals(ArchiveAge.All, ArchiveAge.fromNameOrDefault(ArchiveAge(7, ArchiveAgeUnit.All).storedName))
    }

    @Test
    fun `a default saved by the old fixed list reads as what it was`() {
        assertEquals(ArchiveAge(1, ArchiveAgeUnit.Hour), ArchiveAge.fromNameOrDefault("OneHour"))
        assertEquals(ArchiveAge(1, ArchiveAgeUnit.Day), ArchiveAge.fromNameOrDefault("OneDay"))
        assertEquals(ArchiveAge(1, ArchiveAgeUnit.Week), ArchiveAge.fromNameOrDefault("OneWeek"))
        assertEquals(ArchiveAge(1, ArchiveAgeUnit.Month), ArchiveAge.fromNameOrDefault("OneMonth"))
        assertEquals(ArchiveAge(1, ArchiveAgeUnit.Year), ArchiveAge.fromNameOrDefault("OneYear"))
        assertEquals(ArchiveAge.All, ArchiveAge.fromNameOrDefault("All"))
    }

    @Test
    fun `a stored number outside one to nine falls back to the default`() {
        assertEquals(ArchiveAge.All, ArchiveAge.fromNameOrDefault("0 Day"))
        assertEquals(ArchiveAge.All, ArchiveAge.fromNameOrDefault("10 Day"))
        assertEquals(ArchiveAge.All, ArchiveAge.fromNameOrDefault("3 Decade"))
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
        val threshold = ArchiveAge(1, ArchiveAgeUnit.Day).thresholdEpochSeconds(now)
        assertEquals(now.epochSecond - day, threshold)
        val onTheLine = item("on.jpg", ageSeconds = day)
        val justNewer = item("newer.jpg", ageSeconds = day - 1)
        val result = ArchiveAge(1, ArchiveAgeUnit.Day).split(listOf(onTheLine, justNewer), now)
        assertEquals(listOf("on.jpg"), names(result.eligible))
        assertEquals(listOf("newer.jpg"), names(result.hiddenByAge))
    }

    @Test
    fun `age is read from the modified time and a longer age holds back more`() {
        val recent = item("recent.jpg", ageSeconds = 3 * hour)
        val old = item("old.jpg", ageSeconds = 400 * day)

        assertEquals(listOf("old.jpg"), names(ArchiveAge(1, ArchiveAgeUnit.Day).split(listOf(recent, old), now).eligible))
        assertEquals(
            setOf("recent.jpg", "old.jpg"),
            names(ArchiveAge(1, ArchiveAgeUnit.Hour).split(listOf(recent, old), now).eligible).toSet()
        )
        assertEquals(listOf("old.jpg"), names(ArchiveAge(1, ArchiveAgeUnit.Year).split(listOf(recent, old), now).eligible))
    }

    @Test
    fun `a file held back by age is neither eligible nor lost — it is in the other list`() {
        val young = item("young.jpg", ageSeconds = hour)
        val result = ArchiveAge(1, ArchiveAgeUnit.Week).split(listOf(young), now)
        assertTrue(result.eligible.isEmpty())
        assertEquals(listOf("young.jpg"), names(result.hiddenByAge))
    }

    @Test
    fun `an old photo optimised today is judged by when it was taken`() {
        val optimised = item("old.jpg", ageSeconds = 0).copy(dateTakenEpochSeconds = now.epochSecond - 400 * day)
        val taken = ArchiveAge(1, ArchiveAgeUnit.Year).split(listOf(optimised), now)
        assertEquals(listOf("old.jpg"), names(taken.eligible))
    }

    @Test
    fun `a new photo with an old modified date is held back by when it was taken`() {
        val fresh = item("new.jpg", ageSeconds = 400 * day).copy(dateTakenEpochSeconds = now.epochSecond - day)
        val taken = ArchiveAge(1, ArchiveAgeUnit.Year).split(listOf(fresh), now)
        assertEquals(listOf("new.jpg"), names(taken.hiddenByAge))
    }

    @Test
    fun `an empty list splits into two empty lists`() {
        val result = ArchiveAge(1, ArchiveAgeUnit.Month).split(emptyList(), now)
        assertTrue(result.eligible.isEmpty())
        assertTrue(result.hiddenByAge.isEmpty())
    }
}
