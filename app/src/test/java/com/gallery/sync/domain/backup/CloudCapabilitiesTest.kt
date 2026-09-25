package com.gallery.sync.domain.backup

import com.gallery.sync.data.local.entity.AlbumMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudCapabilitiesTest {

    @Test
    fun `OneDrive can do everything, four clouds can restore and archive, and Google Photos and pCloud are backup-only for now`() {
        assertEquals(CloudCapabilities.FULL, BackupLocation.ONEDRIVE.capabilities)
        val restoreAndArchive = listOf(
            BackupLocation.DROPBOX, BackupLocation.GOOGLE_DRIVE, BackupLocation.BACKBLAZE_B2, BackupLocation.IDRIVE_E2
        )
        restoreAndArchive.forEach { assertEquals("$it", CloudCapabilities.RESTORE_AND_ARCHIVE, it.capabilities) }
        // Sync is the one thing none of them has yet: TASK-027 stage 2.
        restoreAndArchive.forEach { assertFalse("$it", it.capabilities.sync) }
        BackupLocation.entries.filter { it != BackupLocation.ONEDRIVE && it !in restoreAndArchive }.forEach {
            assertEquals("$it", CloudCapabilities.BACKUP_ONLY, it.capabilities)
        }
    }

    @Test
    fun `off and backup are always allowed and sync and archive follow the cloud`() {
        val syncOnly = CloudCapabilities(restore = false, archive = false, sync = true)
        assertTrue(syncOnly.allows(AlbumMode.OFF))
        assertTrue(syncOnly.allows(AlbumMode.BACKUP))
        assertTrue(syncOnly.allows(AlbumMode.SYNC))
        assertFalse(syncOnly.allows(AlbumMode.ARCHIVE))
    }

    @Test
    fun `a feature is supported exactly when its flag is`() {
        val restoreOnly = CloudCapabilities(restore = true, archive = false, sync = false)
        assertTrue(restoreOnly.supports(CloudFeature.RESTORE))
        assertFalse(restoreOnly.supports(CloudFeature.ARCHIVE))
        assertFalse(restoreOnly.supports(CloudFeature.SYNC))
    }

    @Test
    fun `the modes on offer for a cloud are the modes its capabilities allow`() {
        assertEquals(
            listOf(AlbumMode.OFF, AlbumMode.BACKUP, AlbumMode.ARCHIVE),
            GooglePhotosDestination.modesFor(BackupLocation.DROPBOX)
        )
        assertEquals(listOf(AlbumMode.OFF, AlbumMode.BACKUP), GooglePhotosDestination.modesFor(BackupLocation.GOOGLE_PHOTOS))
    }
}
