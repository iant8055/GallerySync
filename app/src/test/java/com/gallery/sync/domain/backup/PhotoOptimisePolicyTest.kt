package com.gallery.sync.domain.backup

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * When photos in Sync albums are optimised. Ian, 19 Sept 2026: **Automatic** is *"as soon as a file hits
 * an Album whose mode is SYNC or an Album mode is switched to SYNC"*, **Manual** is *"through the Sync
 * Now button on the Albums tab"*, and both follow the Settings switches.
 *
 * The two modes are exact opposites: a photo is optimised on its own or on Sync now, never both and
 * never neither while the switches are on.
 */
class PhotoOptimisePolicyTest {

    private fun automatic(
        setupComplete: Boolean = true,
        optimiseEnabled: Boolean = true,
        optimisePhotos: Boolean = true,
        mode: OptimiseMode = OptimiseMode.Auto
    ) = PhotoOptimisePolicy.runsAutomatically(setupComplete, optimiseEnabled, optimisePhotos, mode)

    private fun onSyncNow(
        setupComplete: Boolean = true,
        optimiseEnabled: Boolean = true,
        optimisePhotos: Boolean = true,
        mode: OptimiseMode = OptimiseMode.Manual
    ) = PhotoOptimisePolicy.runsOnSyncNow(setupComplete, optimiseEnabled, optimisePhotos, mode)

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
        assertFalse("Manual is not Automatic", automatic(mode = OptimiseMode.Manual))
        assertFalse("Automatic needs no Sync now", onSyncNow(mode = OptimiseMode.Auto))
    }

    @Test
    fun `nothing runs before setup is finished, the wizard owns photos until then`() {
        assertFalse(automatic(setupComplete = false))
        assertFalse(onSyncNow(setupComplete = false))
    }

    @Test
    fun `the master switch and the photo switch are both required`() {
        assertFalse(automatic(optimiseEnabled = false))
        assertFalse(automatic(optimisePhotos = false))
        assertFalse(onSyncNow(optimiseEnabled = false))
        assertFalse(onSyncNow(optimisePhotos = false))
    }

    @Test
    fun `a chain already under way stops the moment photos are switched off`() {
        assertTrue(PhotoOptimisePolicy.mayContinue(setupComplete = true, optimiseEnabled = true, optimisePhotos = true))
        assertFalse(PhotoOptimisePolicy.mayContinue(setupComplete = true, optimiseEnabled = true, optimisePhotos = false))
        assertFalse(PhotoOptimisePolicy.mayContinue(setupComplete = true, optimiseEnabled = false, optimisePhotos = true))
        assertFalse(PhotoOptimisePolicy.mayContinue(setupComplete = false, optimiseEnabled = true, optimisePhotos = true))
    }
}
