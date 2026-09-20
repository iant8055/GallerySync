package com.gallery.sync.domain.backup

import com.gallery.sync.data.local.entity.AlbumMode
import com.gallery.sync.data.local.entity.BackupEntryEntity
import com.gallery.sync.data.local.entity.BackupState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/**
 * What the Camera album's *Only list Photos/Videos older than…* control will touch.
 *
 * Ian, 20 Sept 2026: age is read from the file's modified time, both Settings switches are respected,
 * and a file swiped out of the list is left alone. The property the rest depends on is that a swiped-out
 * file is never in [CameraOptimisePlan.eligible], because that is the only list the worker acts on.
 */
class CameraOptimisePlanTest {

    /** A Tuesday noon, well clear of any boundary. */
    private val now: Instant = Instant.parse("2026-09-15T12:00:00Z")
    private val day = 86_400L

    private val allOn = CameraOptimiseSettings(
        enabled = true, photos = true, videos = true, photoSavingPercent = 80, videoSavingPercent = 85
    )

    private fun entry(
        name: String,
        ageDays: Long = 30,
        size: Long = 4_000_000L,
        video: Boolean = false,
        state: BackupState = BackupState.UPLOADED,
        remoteSize: Long? = size,
        proxied: Boolean = false,
        skipped: Boolean = false,
        missing: Long? = null,
        pin: AlbumMode? = null
    ) = BackupEntryEntity(
        id = "Camera/$name|$size|1",
        mediaStoreId = name.hashCode().toLong(),
        contentUri = "content://media/external/images/media/${name.hashCode()}",
        displayName = name,
        album = "Camera",
        sizeBytes = size,
        dateModifiedEpochSeconds = now.epochSecond - ageDays * day,
        mimeType = if (video) "video/mp4" else "image/jpeg",
        isVideo = video,
        state = state,
        remoteSizeBytes = remoteSize,
        isProxied = proxied,
        isProxySkipped = skipped,
        localMissingSinceEpochMillis = missing,
        modeOverride = pin
    )

    private fun plan(
        vararg entries: BackupEntryEntity,
        age: CameraOptimiseAge = CameraOptimiseAge.OneWeek,
        settings: CameraOptimiseSettings = allOn
    ) = CameraOptimisePlan.of(entries.toList(), age.thresholdEpochSeconds(now), settings)

    private fun names(list: List<BackupEntryEntity>) = list.map { it.displayName }

    // ── The ages ────────────────────────────────────────────────────────────

    @Test
    fun `the ages are Ian's list in order, with All last`() {
        assertEquals(
            listOf(1L, 7L, 30L, 182L, 365L, 0L),
            CameraOptimiseAge.entries.map { it.duration.toDays() }
        )
        assertEquals(CameraOptimiseAge.All, CameraOptimiseAge.entries.last())
    }

    @Test
    fun `All lists a file modified this very minute as well as an old one`() {
        val fresh = entry("fresh.jpg", ageDays = 0)
        val old = entry("old.jpg", ageDays = 900)
        assertEquals(setOf("fresh.jpg", "old.jpg"), names(plan(fresh, old, age = CameraOptimiseAge.All).eligible).toSet())
        // ...where every other age leaves the fresh one out
        assertEquals(listOf("old.jpg"), names(plan(fresh, old, age = CameraOptimiseAge.OneDay).eligible))
    }

    @Test
    fun `a file exactly on the line is old enough and one second newer is not`() {
        val threshold = CameraOptimiseAge.OneDay.thresholdEpochSeconds(now)
        assertEquals(now.epochSecond - day, threshold)
        val onTheLine = entry("on.jpg").copy(dateModifiedEpochSeconds = threshold)
        val justNewer = entry("newer.jpg").copy(dateModifiedEpochSeconds = threshold + 1)
        val result = CameraOptimisePlan.of(listOf(onTheLine, justNewer), threshold, allOn)
        assertEquals(listOf("on.jpg"), names(result.eligible))
    }

    @Test
    fun `age is read from the modified time and a longer age lists fewer files`() {
        val recent = entry("recent.jpg", ageDays = 3)
        val old = entry("old.jpg", ageDays = 400)
        assertEquals(listOf("old.jpg"), names(plan(recent, old, age = CameraOptimiseAge.OneWeek).eligible))
        assertEquals(setOf("recent.jpg", "old.jpg"), names(plan(recent, old, age = CameraOptimiseAge.OneDay).eligible).toSet())
        assertEquals(listOf("old.jpg"), names(plan(recent, old, age = CameraOptimiseAge.OneYear).eligible))
        assertTrue(plan(recent, entry("mid.jpg", ageDays = 200), age = CameraOptimiseAge.OneYear).eligible.isEmpty())
    }

    // ── Who qualifies ───────────────────────────────────────────────────────

    @Test
    fun `only a file OneDrive confirmed at its full size qualifies`() {
        val result = plan(
            entry("good.jpg"),
            entry("pending.jpg", state = BackupState.PENDING, remoteSize = null),
            entry("failed.jpg", state = BackupState.FAILED, remoteSize = null),
            entry("short.jpg", remoteSize = 100L),
            entry("unknown.jpg", remoteSize = null)
        )
        assertEquals(listOf("good.jpg"), names(result.eligible))
    }

    @Test
    fun `a file already optimised, declined, or gone from the phone is not offered`() {
        val result = plan(
            entry("ok.jpg"),
            entry("done.jpg", proxied = true),
            entry("declined.jpg", skipped = true),
            entry("gone.jpg", missing = 5L)
        )
        assertEquals(listOf("ok.jpg"), names(result.eligible))
    }

    @Test
    fun `largest first`() {
        val result = plan(entry("s.jpg", size = 1_000), entry("l.jpg", size = 9_000), entry("m.jpg", size = 5_000))
        assertEquals(listOf("l.jpg", "m.jpg", "s.jpg"), names(result.eligible))
    }

    // ── The Settings switches ───────────────────────────────────────────────

    @Test
    fun `photos and videos are each governed by their own switch`() {
        val photo = entry("p.jpg")
        val video = entry("v.mp4", video = true)

        assertEquals(listOf("p.jpg"), names(plan(photo, video, settings = allOn.copy(videos = false)).eligible))
        assertEquals(listOf("v.mp4"), names(plan(photo, video, settings = allOn.copy(photos = false)).eligible))
        assertEquals(setOf("p.jpg", "v.mp4"), names(plan(photo, video).eligible).toSet())
    }

    @Test
    fun `with the master switch off nothing is offered and the plan says everything is off`() {
        val off = allOn.copy(enabled = false)
        val result = plan(entry("p.jpg"), entry("v.mp4", video = true), settings = off)
        assertTrue(result.eligible.isEmpty())
        assertTrue(off.everythingOff)
        assertTrue(allOn.copy(photos = false, videos = false).everythingOff)
        assertFalse(allOn.everythingOff)
    }

    // ── Swiping a file out ──────────────────────────────────────────────────

    @Test
    fun `a swiped-out file is never in the list the worker acts on`() {
        val kept = entry("kept.jpg", pin = FilePin.MODE)
        val goes = entry("goes.jpg")
        val result = plan(kept, goes)

        assertEquals(listOf("goes.jpg"), names(result.eligible))
        assertEquals(listOf("kept.jpg"), names(result.optedOut))
        assertFalse(result.eligible.any { FilePin.isPinned(it.modeOverride) })
    }

    @Test
    fun `a swiped-out file is left out of the count and of the saving`() {
        val result = plan(
            entry("kept.jpg", size = 10_000_000, pin = FilePin.MODE),
            entry("goes.jpg", size = 1_000_000)
        )
        assertEquals(1, result.eligible.size)
        assertEquals(800_000L, result.estimatedSavedBytes)
    }

    @Test
    fun `swiping a file back in returns it to the list`() {
        val back = entry("back.jpg", pin = FilePin.overrideFor(pinned = false))
        assertEquals(listOf("back.jpg"), names(plan(back).eligible))
    }

    @Test
    fun `a pinned file that would not have qualified anyway is not shown greyed`() {
        val result = plan(entry("recent.jpg", ageDays = 2, pin = FilePin.MODE), age = CameraOptimiseAge.OneWeek)
        assertTrue(result.eligible.isEmpty())
        assertTrue(result.optedOut.isEmpty())
    }

    // ── The estimate ────────────────────────────────────────────────────────

    @Test
    fun `the saving uses the photo figure for photos and the video figure for clips`() {
        val result = plan(entry("p.jpg", size = 1_000_000), entry("v.mp4", size = 10_000_000, video = true))
        assertEquals(800_000L + 8_500_000L, result.estimatedSavedBytes)
        assertEquals(1, result.photoCount)
        assertEquals(1, result.videoCount)
    }

    @Test
    fun `an empty plan saves nothing`() {
        val result = plan()
        assertEquals(0L, result.estimatedSavedBytes)
        assertEquals(0, result.photoCount)
    }
}
