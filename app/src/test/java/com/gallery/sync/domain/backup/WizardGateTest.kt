package com.gallery.sync.domain.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * ONCE THE INITIAL BACKUP HAS COMPLETED, NOTHING EVER SHOWS THE WIZARD AGAIN. Ian, 28 Sept 2026, after signing out of
 * every cloud on the Moto G put the app back into the wizard. See [WizardGate].
 */
class WizardGateTest {

    private fun shows(
        firstBackupDone: Boolean = false,
        setupCompleted: Boolean = false,
        hasSources: Boolean = true,
        firstBackupPending: Boolean = false,
        anyCloudConnected: Boolean = true
    ) = WizardGate.shows(firstBackupDone, setupCompleted, hasSources, firstBackupPending, anyCloudConnected)

    @Test
    fun `after the first backup nothing brings the wizard back, whatever else is true`() {
        for (setup in listOf(true, false)) for (sources in listOf(true, false))
            for (pending in listOf(true, false)) for (cloud in listOf(true, false)) {
                assertFalse(
                    "wizard shown after the first backup with setup=$setup sources=$sources pending=$pending cloud=$cloud",
                    shows(firstBackupDone = true, setupCompleted = setup, hasSources = sources, firstBackupPending = pending, anyCloudConnected = cloud)
                )
            }
    }

    @Test
    fun `once setup is complete, signing out of every cloud or removing every folder does not bring it back`() {
        assertFalse(shows(setupCompleted = true, anyCloudConnected = false))
        assertFalse(shows(setupCompleted = true, hasSources = false))
        assertFalse(shows(setupCompleted = true, hasSources = false, anyCloudConnected = false))
    }

    @Test
    fun `before setup is finished the wizard shows, as it always has`() {
        assertTrue(shows())
        assertTrue(shows(anyCloudConnected = false))
        assertTrue(shows(hasSources = false))
        assertTrue(shows(firstBackupPending = true))
    }

    @Test
    fun `finished is either record`() {
        assertTrue(WizardGate.finished(firstBackupDone = true, setupCompleted = false))
        assertTrue(WizardGate.finished(firstBackupDone = false, setupCompleted = true))
        assertFalse(WizardGate.finished(firstBackupDone = false, setupCompleted = false))
    }

    /** The decision is WizardGate's alone: MainActivity asks it, and shows the tour in exactly one place. */
    @Test
    fun `MainActivity shows the wizard only where WizardGate says so`() {
        val main = listOf(File("src/main/java/com/gallery/sync/MainActivity.kt"), File("app/src/main/java/com/gallery/sync/MainActivity.kt"))
            .first { it.exists() }.readText()
        assertTrue("MainActivity no longer asks WizardGate", Regex("""val needsSetup = WizardGate\.shows\(""").containsMatchIn(main))
        assertEquals("the tour must be switched on in exactly one place", 1, Regex("""showTour = true""").findAll(main).count())
        assertTrue("the tour must be shown only in the needsSetup branch", Regex("""needsSetup -> SignedInApp\(""").containsMatchIn(main))
        assertFalse("anyConnected must not decide the wizard directly", Regex("""needsSetup \|\|""").containsMatchIn(main))
    }
}
