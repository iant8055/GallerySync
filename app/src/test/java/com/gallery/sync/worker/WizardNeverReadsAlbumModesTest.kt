package com.gallery.sync.worker

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Nothing that decides whether the first backup is over may look at an album mode.
 *
 * Ian, 5 Oct 2026: *"the Wizard should NEVER EVER EVER EVER call Album modes. the Wizard does not need to know
 * Album modes even exist"*, and *"ONLY PRESSING THE FINISH button"* may end the wizard. He has said it more than
 * once; the reason it kept coming back is that nothing failed the build when it did. `WizardGateTest` holds the
 * gate and `WizardDoesNotWriteSettingsTest` holds the Settings boundary — this holds the counting.
 *
 * What went wrong: `BackupWorker` asked `engine.outstandingCount()`, which is
 * `countPendingInSelectedAlbums` and therefore mode-aware, to decide whether the first-backup backlog was clear.
 * The first backup runs with `allAlbums = true` and ignores modes on purpose, so the two disagree the moment an
 * album is Off — and after a first backup every album is Off, which CLAUDE.md says is correct. On the Galaxy
 * Z Fold 8, with 3,200 files still queued, it read 0, wrote `markFirstBackupComplete()`, and the wizard was over:
 * `WizardGate.finished()` is `firstBackupDone || setupCompleted`. No Finish button was ever pressed.
 *
 * This reads the source. The storage and the worker both need an Android context, and a rule broken this
 * quietly needs a check that fails the build rather than one that needs remembering.
 */
class WizardNeverReadsAlbumModesTest {

    /**
     * Counts and queries that filter by album mode or album selection.
     *
     * Adding another mode-aware counter means adding it here. The mode-blind twins — `outstandingCountAll`,
     * `countPendingAll` — are what this path is allowed to use.
     */
    private val modeAware = listOf(
        "outstandingCount()",
        "countPendingInSelectedAlbums",
        "pendingInSelectedAlbums",
        "selectedAlbums("
    )

    private fun source(relative: String): String {
        // Gradle runs unit tests from the module directory; an IDE may run them from the repository root.
        val candidates = listOf(File(relative), File("app/$relative"))
        return candidates.first { it.exists() }.readText()
    }

    private val worker = source("src/main/java/com/gallery/sync/worker/BackupWorker.kt")
    private val reconcileViewModel = source("src/main/java/com/gallery/sync/ui/setup/ReconcileViewModel.kt")
    private val tour = source("src/main/java/com/gallery/sync/ui/setup/SetupTour.kt")

    @Test
    fun `the worker never decides the first backup is over from a mode-aware count`() {
        // Narrowed to the window where the decision is made: everything up to and including the call that
        // records it. A mode-aware count elsewhere in the worker is somebody else's business.
        val marker = "markFirstBackupComplete"
        val lastDecision = worker.lastIndexOf(marker)
        assertTrue("expected BackupWorker to record the first backup somewhere", lastDecision > 0)

        val decidingRegion = worker.substring(0, lastDecision)
        val found = modeAware.filter { decidingRegion.contains(it) }
        assertEquals(
            "BackupWorker decides the first backup is over using a mode-aware count: $found. " +
                "The first backup runs allAlbums = true, so it must use outstandingCountAll().",
            emptyList<String>(),
            found
        )
    }

    @Test
    fun `the wizard's view model never counts by album mode`() {
        val found = modeAware.filter { reconcileViewModel.contains(it) }
        assertEquals(
            "The wizard's view model reads album modes: $found",
            emptyList<String>(),
            found
        )
    }

    @Test
    fun `the tour never counts by album mode`() {
        val found = modeAware.filter { tour.contains(it) }
        assertEquals("The wizard UI reads album modes: $found", emptyList<String>(), found)
    }

    /**
     * The test is only worth anything if the names it looks for still exist. A rename that silently emptied
     * [modeAware] would leave three passing tests guarding nothing.
     */
    @Test
    fun `the mode-aware counter this guards against still exists`() {
        val engine = source("src/main/java/com/gallery/sync/domain/backup/BackupEngine.kt")
        assertTrue(
            "outstandingCount() has been renamed; update modeAware in this test",
            engine.contains("fun outstandingCount()")
        )
        assertTrue(
            "countPendingInSelectedAlbums has been renamed; update modeAware in this test",
            engine.contains("countPendingInSelectedAlbums")
        )
    }
}
