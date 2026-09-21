package com.gallery.sync.domain.backup

import com.gallery.sync.data.local.entity.AlbumMode
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RetryFailedTest {

    @Test
    fun `an album with failed files that is being backed up offers a retry, in every backing-up mode`() {
        for (mode in listOf(AlbumMode.BACKUP, AlbumMode.SYNC, AlbumMode.ARCHIVE)) {
            assertTrue("$mode", RetryFailed.offered(mode, failed = 3))
        }
    }

    @Test
    fun `nothing failed means no retry`() {
        for (mode in AlbumMode.entries) {
            assertFalse("$mode", RetryFailed.offered(mode, failed = 0))
        }
    }

    @Test
    fun `an Off album never offers it, since nothing there is being sent`() {
        assertFalse(RetryFailed.offered(AlbumMode.OFF, failed = 5))
    }
}
