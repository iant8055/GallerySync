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
        isVideo = isVideo,
        relativePath = "DCIM/Camera/"
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

    /**
     * The number Ian asked about on 15 Sept 2026: an album whose files were optimised must not read
     * as unprotected. A proxy is smaller than the original OneDrive holds, so the plain size test
     * fails and every optimised file drops out of "verified in OneDrive" — the Albums tab said
     * "2 of 5 verified" for an album whose five were all uploaded (4 Sept 2026). Fixed then with the
     * recorded original size, and untested until now.
     */
    @Test
    fun anOptimisedFileCountsAsBackedUpAgainstItsOriginalSize() {
        val local = listOf(item("a.jpg", 180_000), item("b.mp4", 2_000_000, isVideo = true))
        val remote = mapOf(
            "a.jpg" to RemoteFileRef("R1", 4_000_000L),
            "b.mp4" to RemoteFileRef("R2", 60_000_000L)
        )

        val result = ReconciliationRules.tallyAlbum(
            local = local,
            remoteIndex = remote,
            proxiedOriginalSizes = mapOf("a.jpg" to 4_000_000L, "b.mp4" to 60_000_000L)
        )

        assertEquals(MediaTally(1, 180_000), result.photosBackedUp)
        assertEquals(MediaTally(1, 2_000_000), result.videosBackedUp)
        assertEquals("nothing optimised may read as outstanding", MediaTally.EMPTY, result.outstanding)
    }

    /**
     * The other half: with no record of the original — a wiped ledger, so a reinstall — the same
     * files fall back to the size on disk and read as outstanding. Nothing is re-uploaded (the
     * engine recognises them by their marker), but the count is pessimistic, and this is the case
     * that showed up as "of 33" for 25 files on 15 Sept 2026.
     */
    @Test
    fun withoutTheRecordedOriginalAnOptimisedFileReadsAsOutstanding() {
        val local = listOf(item("a.jpg", 180_000))
        val remote = mapOf("a.jpg" to RemoteFileRef("R1", 4_000_000L))

        val result = ReconciliationRules.tallyAlbum(local, remote)

        assertEquals(MediaTally(1, 180_000), result.photosOutstanding)
        assertEquals(MediaTally.EMPTY, result.backedUp)
    }

    @Test
    fun photosAndVideosAreTalliedApart() {
        val local = listOf(
            item("a.jpg", 100),
            item("b.jpg", 200),
            item("c.mp4", 5000, isVideo = true),
            item("d.mp4", 9000, isVideo = true)
        )
        val remote = mapOf("a.jpg" to RemoteFileRef("R1", 100L), "c.mp4" to RemoteFileRef("R2", 5000L))

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

        val result = ReconciliationRules.tallyAlbum(local, mapOf("a.jpg" to RemoteFileRef("R1", 12_345L)))

        assertEquals(MediaTally.EMPTY, result.photosBackedUp)
        assertEquals(MediaTally(1, 8_000_000), result.photosOutstanding)
    }

    @Test
    fun talliesAccumulateAcrossAlbums() {
        val checked = ReconciliationRules.tallyAlbum(
            listOf(item("a.jpg", 100)),
            mapOf("a.jpg" to RemoteFileRef("R1", 100L))
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
        val a = ReconciliationRules.tallyAlbum(listOf(item("a.jpg", 100)), mapOf("a.jpg" to RemoteFileRef("R1", 100L)))
        val b = ReconciliationRules.tallyAlbum(listOf(item("b.jpg", 50)), emptyMap())

        assertTrue((a + b).isComplete)
        assertEquals(2, (a + b).albumsChecked)
    }
}
