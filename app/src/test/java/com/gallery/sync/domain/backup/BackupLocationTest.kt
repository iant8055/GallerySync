package com.gallery.sync.domain.backup

import com.gallery.sync.domain.backup.BackupLocation.GOOGLE_PHOTOS
import com.gallery.sync.domain.backup.BackupLocation.ONEDRIVE
import com.gallery.sync.domain.backup.BackupLocation.USB_DRIVE
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupLocationTest {

    @Test
    fun `the only location in use cannot be switched off`() {
        assertFalse(BackupLocations.canSwitchOff(setOf(ONEDRIVE), ONEDRIVE))
    }

    @Test
    fun `with two in use either one may go`() {
        val both = setOf(ONEDRIVE, GOOGLE_PHOTOS)

        assertTrue(BackupLocations.canSwitchOff(both, ONEDRIVE))
        assertTrue(BackupLocations.canSwitchOff(both, GOOGLE_PHOTOS))
    }

    @Test
    fun `after one goes the survivor is locked again`() {
        val remaining = setOf(ONEDRIVE, GOOGLE_PHOTOS, USB_DRIVE) - GOOGLE_PHOTOS - USB_DRIVE

        assertFalse(BackupLocations.canSwitchOff(remaining, ONEDRIVE))
    }

    @Test
    fun `a location that is not on cannot be switched off`() {
        assertFalse(BackupLocations.canSwitchOff(setOf(ONEDRIVE), GOOGLE_PHOTOS))
    }

    @Test
    fun `nothing is in use but OneDrive in this build`() {
        assertEquals(setOf(ONEDRIVE), BackupLocations.inUse)
    }
}
