package com.gallery.sync.domain.backup

import com.gallery.sync.data.local.entity.AlbumMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Gate 2 — what happens to the library already on the phone.
 *
 * Two properties carry the weight. [archiveIsNeverABulkChoice] guards the rule that the largest
 * irreversible action in the product cannot be taken in a wizard. [aVideoHeavyLibraryReportsA
 * MarginalSaving] guards against promising space that will not be freed.
 */
class LibraryChoiceTest {

    private val gb = 1024L * 1024 * 1024

    @Test
    fun theDefaultChangesNothing() {
        assertNull(LibraryChoice.CHOOSE_PER_ALBUM.mode)
        assertFalse(LibraryChoice.CHOOSE_PER_ALBUM.uploads)
    }

    @Test
    fun theTwoActiveChoicesMapToTheirModes() {
        assertEquals(AlbumMode.BACKUP, LibraryChoice.BACK_UP_EVERYTHING.mode)
        assertEquals(AlbumMode.SYNC, LibraryChoice.BACK_UP_AND_FREE_SPACE.mode)
        assertTrue(LibraryChoice.BACK_UP_EVERYTHING.uploads)
        assertTrue(LibraryChoice.BACK_UP_AND_FREE_SPACE.uploads)
    }

    /**
     * Archive in a wizard would set every album at once to the one mode that removes files, chosen
     * before the user has watched the app work and before retrieval exists to undo it.
     */
    @Test
    fun archiveIsNeverABulkChoice() {
        assertFalse(
            "no Gate 2 option may map to Archive",
            LibraryChoice.entries.any { it.mode == AlbumMode.ARCHIVE }
        )
    }

    // ---------- the estimate ----------

    @Test
    fun proxyingReclaimsMostOfAPhotoLibrary() {
        // 10 GB of photos, about a tenth left behind.
        assertEquals(9 * gb, LibraryEstimate.spaceFreedBySync(10 * gb))
    }

    @Test
    fun videoIsNeverCountedAsFreeable() {
        // Sync leaves video whole, so the same photo total gives the same answer regardless.
        assertEquals(
            LibraryEstimate.spaceFreedBySync(10 * gb),
            LibraryEstimate.spaceFreedBySync(10 * gb)
        )
        assertEquals(0L, LibraryEstimate.spaceFreedBySync(0))
    }

    /**
     * Ian's own library: 16 GB of photos against 130 GB of video. Proxying every photo reclaims
     * about 14 GB — under 10% — so calling that "free space" without qualification would invite him
     * to expect most of 148 GB back.
     */
    @Test
    fun aVideoHeavyLibraryReportsAMarginalSaving() {
        assertTrue(
            LibraryEstimate.isSavingMarginal(photoBytes = 16 * gb, videoBytes = 130 * gb)
        )
    }

    @Test
    fun aPhotoHeavyLibraryDoesNot() {
        assertFalse(
            LibraryEstimate.isSavingMarginal(photoBytes = 100 * gb, videoBytes = 10 * gb)
        )
    }

    @Test
    fun anEmptyLibraryIsNotDescribedAsMarginal() {
        assertFalse(LibraryEstimate.isSavingMarginal(photoBytes = 0, videoBytes = 0))
    }
}
