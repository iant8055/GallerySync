package com.gallery.sync.ui.backup

import com.gallery.sync.data.local.entity.AlbumMode
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** An album that belongs to another cloud does not report on OneDrive. */
class AlbumCloudLineTest {

    private fun album(backedUp: Int, elsewhere: Int) = AlbumRow(
        name = "a", itemCount = backedUp, totalBytes = 0, mode = AlbumMode.BACKUP,
        backedUpCount = backedUp, sentElsewhereCount = elsewhere
    )

    @Test
    fun `everything sent to another cloud leaves OneDrive out of the line`() {
        assertFalse(album(backedUp = 46, elsewhere = 46).showsOneDriveClause)
    }

    @Test
    fun `an album with files in OneDrive keeps its OneDrive clause, even beside another cloud`() {
        assertTrue(album(backedUp = 10, elsewhere = 0).showsOneDriveClause)
        assertTrue(album(backedUp = 10, elsewhere = 4).showsOneDriveClause)
    }

    @Test
    fun `an album with nothing sent anywhere still shows OneDrive`() {
        assertTrue(album(backedUp = 0, elsewhere = 0).showsOneDriveClause)
    }
}
