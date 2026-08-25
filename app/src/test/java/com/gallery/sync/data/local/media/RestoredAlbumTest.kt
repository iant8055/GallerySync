package com.gallery.sync.data.local.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The album retrieved files land in, and why the scanner must skip it.
 *
 * [aUserFolderCalledRestoredElsewhereIsStillBackedUp] is the one that keeps this honest. Matching on
 * the name alone would silently stop backing up someone's own folder that happens to share it.
 */
class RestoredAlbumTest {

    @Test
    fun filesInTheAppsRestoredFolderAreExcluded() {
        assertTrue(RestoredAlbum.isRestored("DCIM/Restored/", RestoredAlbum.NAME))
        assertTrue(RestoredAlbum.isRestored("DCIM/Restored", RestoredAlbum.NAME))
    }

    @Test
    fun ordinaryFoldersAreNotExcluded() {
        assertFalse(RestoredAlbum.isRestored("DCIM/Camera/", "Camera"))
        assertFalse(RestoredAlbum.isRestored("Pictures/", "Pictures"))
    }

    /**
     * A folder called "Restored" that the user made themselves is their library, not ours, and must
     * keep backing up. Only the exact path this app writes to is exempt.
     */
    @Test
    fun aUserFolderCalledRestoredElsewhereIsStillBackedUp() {
        assertFalse(RestoredAlbum.isRestored("Pictures/Restored/", "Restored"))
        assertFalse(RestoredAlbum.isRestored("DCIM/Camera/Restored/", "Restored"))
    }

    /** Below API 29 there is no RELATIVE_PATH, so the bucket name is all there is to go on. */
    @Test
    fun withoutAPathTheBucketNameIsUsed() {
        assertTrue(RestoredAlbum.isRestored(null, RestoredAlbum.NAME))
        assertFalse(RestoredAlbum.isRestored(null, "Camera"))
    }

    // --- the `_restored` suffix, and the signature that has to see through it ---

    @Test
    fun theSuffixGoesBeforeTheExtension() {
        assertEquals(
            "20230819_121939_restored.mp4",
            RestoredAlbum.restoredNameOf("20230819_121939.mp4")
        )
    }

    /**
     * After the extension the name would read `IMG_0042.mp4_restored`, which changes the mime type
     * MediaStore infers from it and stops gallery apps recognising the file at all.
     */
    @Test
    fun theExtensionSurvives() {
        assertTrue(RestoredAlbum.restoredNameOf("IMG_0042.jpg").endsWith(".jpg"))
        assertTrue(RestoredAlbum.restoredNameOf("clip.mp4").endsWith(".mp4"))
    }

    @Test
    fun aNameWithNoExtensionStillGetsTheSuffix() {
        assertEquals("IMG_0042_restored", RestoredAlbum.restoredNameOf("IMG_0042"))
    }

    /** Fetching the same file twice must not build `_restored_restored`. */
    @Test
    fun theSuffixIsNotAppliedTwice() {
        val once = RestoredAlbum.restoredNameOf("clip.mp4")

        assertEquals(once, RestoredAlbum.restoredNameOf(once))
    }

    @Test
    fun theOriginalNameCanBeRecovered() {
        assertEquals("clip.mp4", RestoredAlbum.originalNameOf("clip_restored.mp4"))
        assertEquals("clip.mp4", RestoredAlbum.originalNameOf("clip.mp4"))
    }

    /**
     * The regression the suffix could have caused, and the reason the signature exists.
     *
     * `name|size` is how three separate places answer "is this content on the phone?" — the ledger
     * key, the pass that clears a row missing flag, and the last check before a cloud copy is moved
     * to the recycle bin. If a fetched file did not match its ledger row, the row would stay flagged
     * missing, become a cloud deletion candidate, and the guard that should have caught that would
     * be blinded by the same rename.
     */
    @Test
    fun aFetchedFileMatchesTheRowItCameFrom() {
        val onTheLedger = RestoredAlbum.contentSignature("clip.mp4", 461_492_580L)
        val onTheDevice = RestoredAlbum.contentSignature("clip_restored.mp4", 461_492_580L)

        assertEquals(onTheLedger, onTheDevice)
    }

    /** Size is still half the answer. A different file of the same name is a different file. */
    @Test
    fun theSameNameAtADifferentSizeIsNotAMatch() {
        assertNotEquals(
            RestoredAlbum.contentSignature("clip.mp4", 100L),
            RestoredAlbum.contentSignature("clip_restored.mp4", 200L)
        )
    }

    @Test
    fun unrelatedFilesDoNotMatch() {
        assertNotEquals(
            RestoredAlbum.contentSignature("a.jpg", 100L),
            RestoredAlbum.contentSignature("b.jpg", 100L)
        )
    }

    @Test
    fun theFolderSitsInsideDcimSoOneGrantCoversIt() {
        assertTrue(RestoredAlbum.RELATIVE_PATH.startsWith("DCIM/"))
    }
}
