package com.gallery.sync.ui.backup

import com.gallery.sync.data.local.media.FolderShare
import com.gallery.sync.data.local.media.MediaAlbum
import com.gallery.sync.domain.backup.BackupLocation
import com.gallery.sync.domain.backup.FolderDestination
import org.junit.Assert.assertEquals
import org.junit.Test

/** *Where each folder goes* with a folder inside DCIM that has a Cloud of its own (Ian, 22 Nov 2026). */
class FolderRowsTest {

    private fun album(name: String, vararg paths: Pair<String, Int>) = MediaAlbum(
        name = name,
        itemCount = paths.sumOf { it.second },
        totalBytes = paths.sumOf { it.second }.toLong(),
        inPaths = paths.associate { (path, n) -> path to FolderShare(n, n.toLong()) }
    )

    private val albums = listOf(
        album("Camera", "DCIM/Camera" to 40),
        album("Funny stuff", "DCIM/Funny stuff" to 7),
        album("Cats", "DCIM/Funny stuff/Cats" to 3),
        album("Screenshots", "Pictures/Screenshots" to 20)
    )

    private val chosen = mapOf(
        "DCIM" to BackupLocation.ONEDRIVE,
        "Pictures" to BackupLocation.ONEDRIVE,
        "DCIM/Funny stuff" to BackupLocation.PCLOUD
    )

    private fun rows(keys: Map<String, BackupLocation> = chosen, shownWhenEmpty: List<String> = emptyList()) =
        folderRows(albums, keys.keys, shownWhenEmpty) { FolderDestination.resolve(it, keys, BackupLocation.ONEDRIVE) }

    @Test
    fun `the folder gets its own row under its parent, and the parent no longer counts its files`() {
        val result = rows()

        assertEquals(listOf("DCIM", "DCIM/Funny stuff", "Pictures"), result.map { it.name })
        assertEquals(listOf(40, 10, 20), result.map { it.fileCount })
        assertEquals(listOf(BackupLocation.ONEDRIVE, BackupLocation.PCLOUD, BackupLocation.ONEDRIVE), result.map { it.location })
    }

    @Test
    fun `with no folder of its own everything is counted under the top-level folder, as before`() {
        val result = rows(chosen - "DCIM/Funny stuff")

        assertEquals(listOf("DCIM", "Pictures"), result.map { it.name })
        assertEquals(50, result[0].fileCount)
        assertEquals(3, result[0].albumCount)
    }

    @Test
    fun `a parent whose every file is in such a folder keeps a row at zero, so it can still be chosen for`() {
        val only = listOf(album("Funny stuff", "DCIM/Funny stuff" to 7))
        val result = folderRows(only, chosen.keys) { FolderDestination.resolve(it, chosen, BackupLocation.ONEDRIVE) }

        assertEquals(listOf("DCIM", "DCIM/Funny stuff"), result.map { it.name })
        assertEquals(listOf(0, 7), result.map { it.fileCount })
    }

    @Test
    fun `a chosen folder still in Folders to back up is listed even with nothing in it yet`() {
        val keys = chosen + ("DCIM/Empty" to BackupLocation.DROPBOX)
        val result = rows(keys, shownWhenEmpty = listOf("DCIM/Empty"))

        assertEquals(listOf("DCIM", "DCIM/Empty", "DCIM/Funny stuff", "Pictures"), result.map { it.name })
        assertEquals(BackupLocation.DROPBOX, result[1].location)
    }

    @Test
    fun `a chosen folder with nothing in it and no longer added is not listed`() {
        val keys = chosen + ("DCIM/Gone" to BackupLocation.DROPBOX)
        assertEquals(listOf("DCIM", "DCIM/Funny stuff", "Pictures"), rows(keys).map { it.name })
    }
}
