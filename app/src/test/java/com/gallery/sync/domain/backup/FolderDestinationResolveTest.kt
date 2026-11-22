package com.gallery.sync.domain.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FolderDestinationResolveTest {

    private val chosen = mapOf("Pictures" to BackupLocation.GOOGLE_PHOTOS)

    @Test
    fun `a chosen folder resolves to its own destination`() {
        assertEquals(
            BackupLocation.GOOGLE_PHOTOS,
            FolderDestination.resolve("Pictures", chosen, BackupLocation.ONEDRIVE)
        )
    }

    @Test
    fun `an unchosen folder and a missing folder both fall back`() {
        assertEquals(BackupLocation.ONEDRIVE, FolderDestination.resolve("DCIM", chosen, BackupLocation.ONEDRIVE))
        assertEquals(BackupLocation.ONEDRIVE, FolderDestination.resolve(null, chosen, BackupLocation.ONEDRIVE))
    }

    // Ian, 22 Nov 2026: choosing pCloud for DCIM/Funny stuff moved the whole of DCIM to pCloud.
    private val nested = mapOf(
        "DCIM" to BackupLocation.ONEDRIVE,
        "DCIM/Funny stuff" to BackupLocation.PCLOUD
    )

    @Test
    fun `a folder inside a top-level folder can have its own Cloud, and the rest keep the parent's`() {
        assertEquals(BackupLocation.PCLOUD, FolderDestination.resolve("DCIM/Funny stuff/", nested, BackupLocation.DROPBOX))
        assertEquals(BackupLocation.ONEDRIVE, FolderDestination.resolve("DCIM/Camera/", nested, BackupLocation.DROPBOX))
        assertEquals(BackupLocation.ONEDRIVE, FolderDestination.resolve("DCIM", nested, BackupLocation.DROPBOX))
    }

    @Test
    fun `the choice covers folders further down, and the most specific one wins`() {
        val deeper = nested + ("DCIM/Funny stuff/Cats" to BackupLocation.DROPBOX)
        assertEquals(BackupLocation.PCLOUD, FolderDestination.resolve("DCIM/Funny stuff/Dogs/", deeper, BackupLocation.ONEDRIVE))
        assertEquals(BackupLocation.DROPBOX, FolderDestination.resolve("DCIM/Funny stuff/Cats/", deeper, BackupLocation.ONEDRIVE))
    }

    @Test
    fun `a name that only starts the same is not inside it`() {
        assertEquals(BackupLocation.ONEDRIVE, FolderDestination.resolve("DCIM/Funny stuff 2/", nested, BackupLocation.DROPBOX))
    }

    @Test
    fun `folders match ignoring case, as MediaStore keeps each writer's spelling`() {
        assertEquals(BackupLocation.PCLOUD, FolderDestination.resolve("DCIM/funny STUFF/", nested, BackupLocation.DROPBOX))
        assertEquals("DCIM/Funny stuff", FolderDestination.routeOf("dcim/funny stuff/", nested.keys))
    }

    @Test
    fun `with no folder of its own the route is the top-level folder, chosen or not`() {
        assertEquals("Movies", FolderDestination.routeOf("Movies/Trip/", nested.keys))
        assertNull(FolderDestination.routeOf(null, nested.keys))
        assertNull(FolderDestination.routeOf(".hidden/x/", nested.keys))
    }
}
