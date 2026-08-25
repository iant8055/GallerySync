package com.gallery.sync.data.local.media

import org.junit.Assert.assertFalse
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

    @Test
    fun theFolderSitsInsideDcimSoOneGrantCoversIt() {
        assertTrue(RestoredAlbum.RELATIVE_PATH.startsWith("DCIM/"))
    }
}
