package com.gallery.sync.worker

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Only the Finish button may end the wizard.
 *
 * Ian, 5 Oct 2026: *"ONLY PRESSING THE FINISH button should"* end it. He also pointed out, correctly, that the
 * wizard had run to Finish before — the record has it working on 5 and 15 Sept — and that something we did
 * broke it. This test exists because the thing we did was invisible to every other check.
 *
 * **What happened.** `BackupWorker` had written `markFirstBackupComplete()` since 25 Aug 2026 to mean one
 * thing: the backlog has drained, so the overnight first-backup window no longer applies and a photo taken at
 * noon need not wait until 1am. On **28 Sept 2026** (`6060879`, version 17) `WizardGate.finished()` began
 * reading that same record to mean something else: the wizard is over. From then on, a first backup that
 * drained while the wizard was on screen destroyed the wizard — measured at **forty milliseconds** before it
 * could offer the Finish button, on the Galaxy Z Fold 8, traced to `BackupWorker.kt:187` by a stack trace.
 * The rule was right; the implementation reused a flag that already had an owner.
 *
 * The records are now separate: `firstBackupWindowLifted` is the scheduling fact and is the worker's, and
 * `hasCompletedFirstBackup` is the wizard's and is written only by the Finish handler.
 *
 * This reads the source. Both records live behind DataStore and the worker needs a context, and a rule that
 * was broken this quietly needs a check that fails the build rather than one that needs remembering.
 */
class OnlyFinishEndsTheWizardTest {

    private fun source(relative: String): String {
        // Gradle runs unit tests from the module directory; an IDE may run them from the repository root.
        val candidates = listOf(File(relative), File("app/$relative"))
        return candidates.first { it.exists() }.readText()
    }

    private val worker = source("src/main/java/com/gallery/sync/worker/BackupWorker.kt")
    private val reconcileViewModel = source("src/main/java/com/gallery/sync/ui/setup/ReconcileViewModel.kt")
    private val settings = source("src/main/java/com/gallery/sync/data/local/settings/BackupSettings.kt")
    private val gate = source("src/main/java/com/gallery/sync/domain/backup/WizardGate.kt")

    /** Writing either of the wizard's two records. A worker may write neither. */
    private val endsTheWizard = listOf("markFirstBackupComplete", "setSetupCompleted")

    @Test
    fun `no worker ends the wizard`() {
        val dir = File("src/main/java/com/gallery/sync/worker")
            .takeIf { it.exists() }
            ?: File("app/src/main/java/com/gallery/sync/worker")
        val offenders = dir.walkTopDown().filter { it.extension == "kt" }.flatMap { file ->
            val text = file.readText()
            endsTheWizard.filter { text.contains("settings.$it(") || text.contains(".$it()") }
                .map { "${file.name} calls $it" }
        }.toList()
        assertEquals(
            "A worker writes a record that ends the wizard: $offenders. " +
                "The backlog draining lifts the scheduling window (markFirstBackupWindowLifted) and nothing more.",
            emptyList<String>(),
            offenders
        )
    }

    @Test
    fun `the wizard's record is written only by the Finish handler`() {
        val callers = Regex("""fun (\w+)\(\)[^}]*?markFirstBackupComplete\(\)""", RegexOption.DOT_MATCHES_ALL)
            .findAll(reconcileViewModel).map { it.groupValues[1] }.toList()
        assertEquals(
            "markFirstBackupComplete is called from $callers; only completeSetupAfterBackup may call it",
            listOf("completeSetupAfterBackup"),
            callers
        )
    }

    @Test
    fun `the gate never reads the scheduling record`() {
        assertTrue(
            "WizardGate reads firstBackupWindowLifted. That record means the overnight window has lifted, " +
                "not that the wizard is over — the exact conflation that broke it on 28 Sept 2026.",
            !gate.contains("WindowLifted") && !gate.contains("windowLifted")
        )
    }

    /** A rename that emptied the names above would leave three passing tests guarding nothing. */
    @Test
    fun `the records this guards still exist`() {
        assertTrue("markFirstBackupWindowLifted has been renamed", settings.contains("fun markFirstBackupWindowLifted"))
        assertTrue("markFirstBackupComplete has been renamed", settings.contains("fun markFirstBackupComplete"))
        assertTrue("the worker no longer lifts the window", worker.contains("markFirstBackupWindowLifted"))
    }
}
