package com.gallery.sync.ui.backup

import com.gallery.sync.domain.backup.BackupLocation
import org.junit.Assert.assertEquals
import org.junit.Test

class WithGrantedFoldersTest {

    private fun row(name: String, files: Int) = FolderRow(
        name = name,
        albumCount = 1,
        fileCount = files,
        totalBytes = files * 10L,
        location = BackupLocation.DROPBOX
    )

    private fun rows(scanned: List<FolderRow>, vararg granted: String) =
        withGrantedFolders(scanned, granted.toList()) { BackupLocation.ONEDRIVE }

    @Test
    fun `a folder added with no media still gets a row at zero files`() {
        val result = rows(listOf(row("DCIM", 5)), "Documents")

        assertEquals(listOf("DCIM", "Documents"), result.map { it.name })
        assertEquals(0, result[1].fileCount)
        assertEquals(BackupLocation.ONEDRIVE, result[1].location)
    }

    @Test
    fun `a folder the scan found is left exactly as the scan reported it`() {
        val scanned = listOf(row("DCIM", 5))

        assertEquals(scanned, rows(scanned, "DCIM"))
    }

    @Test
    fun `a subfolder grant counts as its top level folder, the level a Cloud is chosen at`() {
        assertEquals(listOf(row("DCIM", 5)), rows(listOf(row("DCIM", 5)), "DCIM/Camera"))
        assertEquals(listOf("Documents"), rows(emptyList(), "Documents/Scans", "Documents/Work").map { it.name })
    }

    @Test
    fun `the same folder in another spelling does not get a second row`() {
        assertEquals(listOf("DCIM"), rows(listOf(row("DCIM", 5)), "dcim").map { it.name })
    }

    @Test
    fun `a hidden folder or the root of the phone gets no row`() {
        assertEquals(emptyList<FolderRow>(), rows(emptyList(), ".thumbnails", ""))
    }
}
