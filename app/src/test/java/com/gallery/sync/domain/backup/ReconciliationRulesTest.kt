package com.gallery.sync.domain.backup

import android.net.Uri
import com.gallery.sync.data.local.media.LocalMediaItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.mock

/**
 * The reconciliation shown during first-run setup.
 *
 * The case that carries the most weight is [anAlbumThatCouldNotBeListedIsNeverCountedAsMissing].
 * Reporting an unreachable album as "not backed up" tells the user their library is unprotected and
 * pushes them into re-uploading files that were already safe — which is precisely what happened on
 * 19 Aug 2026, when 8,177 files were reported missing because 81 albums never got listed.
 */
class ReconciliationRulesTest {

    private val uri: Uri = mock()

    private fun item(name: String, size: Long, isVideo: Boolean = false) = LocalMediaItem(
        mediaStoreId = name.hashCode().toLong(),
        contentUri = uri,
        displayName = name,
        album = "Camera",
        sizeBytes = size,
        dateModifiedEpochSeconds = 1_700_000_000L,
        mimeType = if (isVideo) "video/mp4" else "image/jpeg",
        isVideo = isVideo
    )

    @Test
    fun anAlbumThatCouldNotBeListedIsNeverCountedAsMissing() {
        val local = listOf(item("a.jpg", 100), item("b.mp4", 900, isVideo = true))

        val result = ReconciliationRules.tallyAlbum(local, remoteIndex = null)

        assertEquals(MediaTally(2, 1000), result.unchecked)
        assertEquals("nothing may be reported as outstanding", MediaTally.EMPTY, result.outstanding)
        assertEquals("nor as backed up", MediaTally.EMPTY, result.backedUp)
        assertEquals(1, result.albumsUnchecked)
        assertFalse("the totals are a floor, not a total", result.isComplete)
    }

    /** An empty album really is empty — unlike a failed listing, this one is evidence. */
    @Test
    fun anEmptyRemoteAlbumMeansEverythingIsOutstanding() {
        val local = listOf(item("a.jpg", 100))

        val result = ReconciliationRules.tallyAlbum(local, remoteIndex = emptyMap())

        assertEquals(MediaTally(1, 100), result.photosOutstanding)
        assertEquals(MediaTally.EMPTY, result.unchecked)
        assertTrue(result.isComplete)
    }

    @Test
    fun photosAndVideosAreTalliedApart() {
        val local = listOf(
            item("a.jpg", 100),
            item("b.jpg", 200),
            item("c.mp4", 5000, isVideo = true),
            item("d.mp4", 9000, isVideo = true)
        )
        val remote = mapOf("a.jpg" to 100L, "c.mp4" to 5000L)

        val result = ReconciliationRules.tallyAlbum(local, remote)

        assertEquals(MediaTally(1, 100), result.photosBackedUp)
        assertEquals(MediaTally(1, 200), result.photosOutstanding)
        assertEquals(MediaTally(1, 5000), result.videosBackedUp)
        assertEquals(MediaTally(1, 9000), result.videosOutstanding)
        assertEquals(MediaTally(2, 9200), result.outstanding)
    }

    /** Same bar as `verifiedInCloud`: a name match alone is also true of a truncated upload. */
    @Test
    fun aNameMatchWithTheWrongSizeIsNotBackedUp() {
        val local = listOf(item("a.jpg", 8_000_000))

        val result = ReconciliationRules.tallyAlbum(local, mapOf("a.jpg" to 12_345L))

        assertEquals(MediaTally.EMPTY, result.photosBackedUp)
        assertEquals(MediaTally(1, 8_000_000), result.photosOutstanding)
    }

    @Test
    fun talliesAccumulateAcrossAlbums() {
        val checked = ReconciliationRules.tallyAlbum(
            listOf(item("a.jpg", 100)),
            mapOf("a.jpg" to 100L)
        )
        val failed = ReconciliationRules.tallyAlbum(listOf(item("b.jpg", 50)), null)

        val total = checked + failed

        assertEquals(MediaTally(1, 100), total.backedUp)
        assertEquals(MediaTally(1, 50), total.unchecked)
        assertEquals(1, total.albumsChecked)
        assertEquals(1, total.albumsUnchecked)
        assertFalse(total.isComplete)
    }

    @Test
    fun anAllCheckedRunReportsComplete() {
        val a = ReconciliationRules.tallyAlbum(listOf(item("a.jpg", 100)), mapOf("a.jpg" to 100L))
        val b = ReconciliationRules.tallyAlbum(listOf(item("b.jpg", 50)), emptyMap())

        assertTrue((a + b).isComplete)
        assertEquals(2, (a + b).albumsChecked)
    }
}
