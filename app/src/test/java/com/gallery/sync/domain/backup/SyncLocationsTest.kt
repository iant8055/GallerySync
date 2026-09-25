package com.gallery.sync.domain.backup

import org.junit.Assert.assertEquals
import org.junit.Test

/** The SQL list in the candidate queries must match the capability table, or a cloud is half switched on. */
class SyncLocationsTest {

    @Test
    fun `the query list is exactly the clouds other than OneDrive that can Sync`() {
        val inQueries = SyncLocations.SQL_LIST.split(',').map { it.trim().trim('\'') }.toSet()
        val inCapabilities = BackupLocation.entries
            .filter { it != BackupLocation.ONEDRIVE && it.capabilities.sync }
            .map { it.name }
            .toSet()
        assertEquals(inCapabilities, inQueries)
    }
}
