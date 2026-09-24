package com.gallery.sync.domain.backup

import org.junit.Assert.assertEquals
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
}
