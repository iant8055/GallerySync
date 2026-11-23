package com.gallery.sync.domain.backup

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * When photos in Sync albums are optimized. Ian, 19 Sept 2026: **Automatic** is *"as soon as a file hits
 * an Album whose mode is SYNC or an Album mode is switched to SYNC"*, **Manual** is *"through the Sync
 * Now button on the Albums tab"*, and both follow the Settings switches.
 *
 * The two modes are exact opposites: a photo is optimized on its own or on Sync now, never both and
 * never neither while the switches are on.
 */
class PhotoOptimizePolicyTest {

    private fun automatic(
        setupComplete: Boolean = true,
        optimizeEnabled: Boolean = true,
        optimizePhotos: Boolean = true,
        mode: OptimizeMode = OptimizeMode.Auto
    ) = PhotoOptimizePolicy.runsAutomatically(setupComplete, optimizeEnabled, optimizePhotos, mode)

    private fun onSyncNow(
        setupComplete: Boolean = true,
        optimizeEnabled: Boolean = true,
        optimizePhotos: Boolean = true,
        mode: OptimizeMode = OptimizeMode.Manual
    ) = PhotoOptimizePolicy.runsOnSyncNow(setupComplete, optimizeEnabled, optimizePhotos, mode)

    @Test
    fun `automatic photos run on their own when every switch says so`() {
        assertTrue(automatic())
    }

    @Test
    fun `manual photos run when Sync now is pressed`() {
        assertTrue(onSyncNow())
    }

    @Test
    fun `automatic never waits for a button and manual never starts alone`() {
        assertFalse("Manual is not Automatic", automatic(mode = OptimizeMode.Manual))
        assertFalse("Automatic needs no Sync now", onSyncNow(mode = OptimizeMode.Auto))
    }

    @Test
    fun `nothing runs before setup is finished, the wizard owns photos until then`() {
        assertFalse(automatic(setupComplete = false))
        assertFalse(onSyncNow(setupComplete = false))
    }

    @Test
    fun `the master switch and the photo switch are both required`() {
        assertFalse(automatic(optimizeEnabled = false))
        assertFalse(automatic(optimizePhotos = false))
        assertFalse(onSyncNow(optimizeEnabled = false))
        assertFalse(onSyncNow(optimizePhotos = false))
    }

    @Test
    fun `a chain already under way stops the moment photos are switched off`() {
        assertTrue(PhotoOptimizePolicy.mayContinue(setupComplete = true, optimizeEnabled = true, optimizePhotos = true))
        assertFalse(PhotoOptimizePolicy.mayContinue(setupComplete = true, optimizeEnabled = true, optimizePhotos = false))
        assertFalse(PhotoOptimizePolicy.mayContinue(setupComplete = true, optimizeEnabled = false, optimizePhotos = true))
        assertFalse(PhotoOptimizePolicy.mayContinue(setupComplete = false, optimizeEnabled = true, optimizePhotos = true))
    }
}
