package com.gallery.sync.domain.backup

import com.gallery.sync.ui.backup.BackupUiState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Whether the **Sync now** button can be pressed with nothing to send. Ian, 19 Sept 2026: Manual
 * optimising is *"through the Sync Now Button on the Albums Tab"*, and the button used to be enabled
 * only while files were waiting to upload, so a Manual album that was already backed up had nothing to
 * press.
 */
class OptimiseOnSyncNowTest {

    private fun waiting(
        photoMode: OptimiseMode = OptimiseMode.Manual,
        videoMode: OptimiseMode = OptimiseMode.Manual,
        optimisePhotos: Boolean = true,
        optimiseVideo: Boolean = true,
        photosReady: Int = 0,
        videoReady: Int = 0,
        optimiseEnabled: Boolean = true,
        setupComplete: Boolean = true
    ) = OptimiseOnSyncNow.isWaiting(
        setupComplete = setupComplete,
        optimiseEnabled = optimiseEnabled,
        optimisePhotos = optimisePhotos,
        photoMode = photoMode,
        optimiseVideo = optimiseVideo,
        videoMode = videoMode,
        photosReady = photosReady,
        videoReady = videoReady
    )

    @Test
    fun `manual photos ready make Sync now worth pressing`() {
        assertTrue(waiting(photosReady = 3))
    }

    @Test
    fun `manual video ready makes Sync now worth pressing`() {
        assertTrue(waiting(videoReady = 1))
    }

    @Test
    fun `nothing ready means nothing to press for`() {
        assertFalse(waiting())
    }

    @Test
    fun `automatic kinds never wait for the button, whatever is ready`() {
        assertFalse(waiting(photoMode = OptimiseMode.Auto, videoMode = OptimiseMode.Auto, photosReady = 9, videoReady = 9))
    }

    @Test
    fun `a manual kind that is switched off is not waiting`() {
        assertFalse(waiting(optimisePhotos = false, optimiseVideo = false, photosReady = 9, videoReady = 9))
        assertFalse(waiting(optimiseEnabled = false, photosReady = 9, videoReady = 9))
    }

    @Test
    fun `the button follows what is waiting, alongside files to send`() {
        assertTrue(BackupUiState(pendingCount = 2).canSyncNow)
        assertTrue(BackupUiState(manualOptimiseWaiting = true).canSyncNow)
        assertFalse(BackupUiState().canSyncNow)
    }
}
