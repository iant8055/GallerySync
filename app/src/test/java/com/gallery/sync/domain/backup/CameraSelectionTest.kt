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
 * Which files in the camera folder Sync now will optimize.
 *
 * Ian, 27 Sept 2026: what is listed and what is selected are separate. Every file is listed; the header's age,
 * Photos and Videos select files; a swipe changes one; and the selection is for the visit. The property the rest
 * depends on is that [CameraSelection.toOptimize], the only list the worker acts on, never holds a file outside
 * the selection or one that cannot be optimized.
 */
class CameraSelectionTest {

    /** A Tuesday noon, well clear of any boundary. */
    private val now: Instant = Instant.parse("2026-09-15T12:00:00Z")
    private val day = 86_400L

    private val both = CameraOptimizeChoice(ArchiveAge(1, ArchiveAgeUnit.Week), photos = true, videos = true)

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

    private fun selectedBy(choice: CameraOptimizeChoice, vararg entries: BackupEntryEntity): Set<String> =
        CameraSelection.byChoice(entries.toList(), choice, choice.age.thresholdEpochSeconds(now))
            .map { id -> entries.first { it.id == id }.displayName }.toSet()

    // ── The ages and the defaults ───────────────────────────────────────────

    @Test
    fun `a Camera age saved by the old fixed list keeps its meaning`() {
        assertEquals(ArchiveAge(1, ArchiveAgeUnit.Day), CameraOptimizeAge.fromNameOrDefault("OneDay"))
        assertEquals(ArchiveAge(1, ArchiveAgeUnit.Week), CameraOptimizeAge.fromNameOrDefault("OneWeek"))
        assertEquals(ArchiveAge(1, ArchiveAgeUnit.Month), CameraOptimizeAge.fromNameOrDefault("OneMonth"))
        assertEquals(ArchiveAge(6, ArchiveAgeUnit.Month), CameraOptimizeAge.fromNameOrDefault("SixMonths"))
        assertEquals(ArchiveAge(1, ArchiveAgeUnit.Year), CameraOptimizeAge.fromNameOrDefault("OneYear"))
        assertEquals(ArchiveAge.All, CameraOptimizeAge.fromNameOrDefault("All"))
    }

    /** Ian, 27 Sept 2026: the defaults in Settings are Off for optimizing and All for age. */
    @Test
    fun `out of the box both kinds are off and the age is All, so nothing is selected`() {
        assertEquals(CameraOptimizeAge.All, CameraOptimizeAge.DEFAULT)
        assertEquals(CameraOptimizeChoice(CameraOptimizeAge.All, photos = false, videos = false), CameraOptimizeChoice.DEFAULT)
        assertTrue(CameraOptimizeChoice.DEFAULT.nothingChosen)
        assertEquals(emptySet<String>(), selectedBy(CameraOptimizeChoice.DEFAULT, entry("p.jpg"), entry("v.mp4", video = true)))
    }

    @Test
    fun `an unknown stored age falls back to the default`() {
        assertEquals(CameraOptimizeAge.DEFAULT, CameraOptimizeAge.fromNameOrDefault(null))
        assertEquals(CameraOptimizeAge.DEFAULT, CameraOptimizeAge.fromNameOrDefault("Fortnight"))
        ArchiveAge.COUNTS.forEach { n ->
            val age = ArchiveAge(n, ArchiveAgeUnit.Week)
            assertEquals(age, CameraOptimizeAge.fromNameOrDefault(age.storedName))
        }
    }

    @Test
    fun `a choice differs from the defaults when any one of its three parts does`() {
        val defaults = CameraOptimizeChoice.DEFAULT
        assertEquals(defaults, defaults.copy())
        assertTrue(defaults != defaults.copy(age = ArchiveAge(1, ArchiveAgeUnit.Year)))
        assertTrue(defaults != defaults.copy(photos = true))
        assertTrue(defaults != defaults.copy(videos = true))
    }

    // ── What the header selects ─────────────────────────────────────────────

    @Test
    fun `All selects a file modified this very minute as well as an old one`() {
        val all = both.copy(age = CameraOptimizeAge.All)
        assertEquals(setOf("fresh.jpg", "old.jpg"), selectedBy(all, entry("fresh.jpg", ageDays = 0), entry("old.jpg", ageDays = 900)))
    }

    @Test
    fun `a file exactly on the line is old enough and one second newer is not`() {
        val cutoff = ArchiveAge(1, ArchiveAgeUnit.Week).thresholdEpochSeconds(now)
        val onLine = entry("on.jpg").copy(dateModifiedEpochSeconds = cutoff)
        val newer = entry("newer.jpg").copy(dateModifiedEpochSeconds = cutoff + 1)
        assertEquals(setOf("on.jpg"), selectedBy(both, onLine, newer))
    }

    @Test
    fun `a longer age selects fewer files`() {
        val files = arrayOf(entry("a.jpg", ageDays = 3), entry("b.jpg", ageDays = 40), entry("c.jpg", ageDays = 400))
        assertEquals(setOf("b.jpg", "c.jpg"), selectedBy(both, *files))
        assertEquals(setOf("c.jpg"), selectedBy(both.copy(age = ArchiveAge(1, ArchiveAgeUnit.Year)), *files))
    }

    @Test
    fun `photos and videos are each selected by their own toggle`() {
        val photo = entry("p.jpg")
        val video = entry("v.mp4", video = true)
        assertEquals(setOf("p.jpg"), selectedBy(both.copy(videos = false), photo, video))
        assertEquals(setOf("v.mp4"), selectedBy(both.copy(photos = false), photo, video))
        assertEquals(setOf("p.jpg", "v.mp4"), selectedBy(both, photo, video))
    }

    @Test
    fun `a file kept at full size is not selected by the header`() {
        assertEquals(setOf("free.jpg"), selectedBy(both, entry("free.jpg"), entry("kept.jpg", pin = FilePin.MODE)))
    }

    // ── What can be selected at all ─────────────────────────────────────────

    @Test
    fun `only a file OneDrive confirmed at its full size can be selected`() {
        assertTrue(CameraSelection.isSelectable(entry("ok.jpg")))
        assertFalse(CameraSelection.isSelectable(entry("pending.jpg", state = BackupState.PENDING)))
        assertFalse(CameraSelection.isSelectable(entry("unchecked.jpg", remoteSize = null)))
        assertFalse(CameraSelection.isSelectable(entry("different.jpg", remoteSize = 1L)))
    }

    @Test
    fun `a file already optimized, declined, or gone from the phone cannot be selected`() {
        assertFalse(CameraSelection.isSelectable(entry("done.jpg", proxied = true)))
        assertFalse(CameraSelection.isSelectable(entry("declined.jpg", skipped = true)))
        assertFalse(CameraSelection.isSelectable(entry("gone.jpg", missing = 1L)))
    }

    @Test
    fun `a file kept at full size can still be selected by hand`() {
        assertTrue(CameraSelection.isSelectable(entry("kept.jpg", pin = FilePin.MODE)))
    }

    // ── What the worker acts on ─────────────────────────────────────────────

    @Test
    fun `nothing outside the selection is ever optimized`() {
        val a = entry("a.jpg")
        val b = entry("b.jpg")
        val c = entry("c.jpg")
        assertEquals(listOf("b.jpg"), CameraSelection.toOptimize(listOf(a, b, c), setOf(b.id)).map { it.displayName })
        assertTrue(CameraSelection.toOptimize(listOf(a, b, c), emptySet()).isEmpty())
    }

    @Test
    fun `a selected file that stopped being optimizable drops out, largest first for the rest`() {
        val small = entry("small.jpg", size = 1_000_000L)
        val large = entry("large.jpg", size = 9_000_000L)
        val done = entry("done.jpg", proxied = true)
        val selection = setOf(small.id, large.id, done.id)
        assertEquals(listOf("large.jpg", "small.jpg"), CameraSelection.toOptimize(listOf(small, large, done), selection).map { it.displayName })
    }

    @Test
    fun `the estimate uses the photo figure for photos and the video figure for clips`() {
        val photo = entry("p.jpg", size = 1_000L)
        val video = entry("v.mp4", size = 1_000L, video = true)
        assertEquals(800L + 850L, CameraSelection.estimatedSavedBytes(listOf(photo, video), 80, 85))
        assertEquals(0L, CameraSelection.estimatedSavedBytes(emptyList(), 80, 85))
    }
}
