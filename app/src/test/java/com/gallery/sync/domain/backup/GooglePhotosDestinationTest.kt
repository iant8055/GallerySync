package com.gallery.sync.domain.backup

import com.gallery.sync.data.local.entity.AlbumMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * While the app-wide destination is Google Photos, Sync and Archive are never offered — neither can
 * ever succeed, since a row sent there can never satisfy `verifiedInCloud()`. See TASK-026.
 */
class GooglePhotosDestinationTest {

    @Test
    fun `Google Photos offers only Off and Backup`() {
        assertEquals(
            listOf(AlbumMode.OFF, AlbumMode.BACKUP),
            GooglePhotosDestination.modesFor(BackupLocation.GOOGLE_PHOTOS)
        )
    }

    @Test
    fun `every other destination keeps all four modes`() {
        assertEquals(AlbumMode.entries.toList(), GooglePhotosDestination.modesFor(BackupLocation.ONEDRIVE))
        assertTrue(GooglePhotosDestination.canChoose(BackupLocation.ONEDRIVE, AlbumMode.SYNC))
        assertTrue(GooglePhotosDestination.canChoose(BackupLocation.ONEDRIVE, AlbumMode.ARCHIVE))
    }

    @Test
    fun `Google Photos cannot be given Sync or Archive and can be given the rest`() {
        assertFalse(GooglePhotosDestination.canChoose(BackupLocation.GOOGLE_PHOTOS, AlbumMode.SYNC))
        assertFalse(GooglePhotosDestination.canChoose(BackupLocation.GOOGLE_PHOTOS, AlbumMode.ARCHIVE))
        assertTrue(GooglePhotosDestination.canChoose(BackupLocation.GOOGLE_PHOTOS, AlbumMode.OFF))
        assertTrue(GooglePhotosDestination.canChoose(BackupLocation.GOOGLE_PHOTOS, AlbumMode.BACKUP))
    }

    @Test
    fun `seeding turns Sync and Archive into Backup for Google Photos and touches nothing else`() {
        assertEquals(AlbumMode.BACKUP, GooglePhotosDestination.seeded(BackupLocation.GOOGLE_PHOTOS, AlbumMode.SYNC))
        assertEquals(AlbumMode.BACKUP, GooglePhotosDestination.seeded(BackupLocation.GOOGLE_PHOTOS, AlbumMode.ARCHIVE))
        assertEquals(AlbumMode.OFF, GooglePhotosDestination.seeded(BackupLocation.GOOGLE_PHOTOS, AlbumMode.OFF))
        assertEquals(AlbumMode.SYNC, GooglePhotosDestination.seeded(BackupLocation.ONEDRIVE, AlbumMode.SYNC))
    }
}
