package com.gallery.sync.data.local.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RestoreFolderTest {

    private val noSubFolders: (String) -> List<String> = { emptyList() }

    @Test
    fun `a photo folder at the top of the storage goes back where it came from`() {
        val picked = RestoreFolder.pick(
            album = "iDrive",
            isVideo = false,
            grantedPaths = listOf("iDrive", "dropbox", "DCIM"),
            subFolders = noSubFolders
        )
        assertEquals("iDrive", picked)
    }

    @Test
    fun `the name is compared without regard to case`() {
        val picked = RestoreFolder.pick("idrive", false, listOf("iDrive"), noSubFolders)
        assertEquals("iDrive", picked)
    }

    @Test
    fun `an album under DCIM is left to MediaStore, which can write there`() {
        val picked = RestoreFolder.pick(
            album = "Temp01",
            isVideo = false,
            grantedPaths = listOf("DCIM"),
            subFolders = { folder -> if (folder == "DCIM") listOf("Camera", "Temp01") else emptyList() }
        )
        assertNull(picked)
    }

    @Test
    fun `an album in a sub-folder of a granted folder that MediaStore cannot write to is found`() {
        val picked = RestoreFolder.pick(
            album = "Trips",
            isVideo = false,
            grantedPaths = listOf("Documents"),
            subFolders = { folder -> if (folder == "Documents") listOf("Trips", "Tax") else emptyList() }
        )
        assertEquals("Documents/Trips", picked)
    }

    @Test
    fun `a photo album under Movies is not a photo root, but a video album is`() {
        val photo = RestoreFolder.pick("Clips", false, listOf("Movies/Clips"), noSubFolders)
        val video = RestoreFolder.pick("Clips", true, listOf("Movies/Clips"), noSubFolders)
        assertEquals("Movies/Clips", photo)
        assertNull(video)
    }

    @Test
    fun `no grant with that name means the ordinary route`() {
        assertNull(RestoreFolder.pick("Holiday", false, listOf("iDrive", "dropbox"), noSubFolders))
        assertNull(RestoreFolder.pick("Holiday", false, emptyList(), noSubFolders))
        assertNull(RestoreFolder.pick("  ", false, listOf("iDrive"), noSubFolders))
    }

    @Test
    fun `sub-folders are only asked about a grant that is not itself the album`() {
        val asked = mutableListOf<String>()
        RestoreFolder.pick(
            album = "iDrive",
            isVideo = false,
            grantedPaths = listOf("iDrive"),
            subFolders = { asked += it; emptyList() }
        )
        assertTrue(asked.isEmpty())
    }

    @Test
    fun `writable roots follow the kind of media`() {
        assertTrue(RestoreFolder.isWritableRoot("DCIM/Camera/", false))
        assertTrue(RestoreFolder.isWritableRoot("Pictures/x", false))
        assertFalse(RestoreFolder.isWritableRoot("Movies/x", false))
        assertTrue(RestoreFolder.isWritableRoot("Movies/x", true))
        assertFalse(RestoreFolder.isWritableRoot("iDrive/", true))
    }
}
