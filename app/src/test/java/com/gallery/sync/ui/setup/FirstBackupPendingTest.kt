package com.gallery.sync.ui.setup

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The app is unavailable until the first backup has finished — a core rule (Ian, 24 Sept 2026). */
class FirstBackupPendingTest {

    @Test
    fun `a started first backup that has not finished holds the app`() {
        assertTrue(ReconcileUiState(wizardStep = WIZARD_BACKUP_STEP, hasCompletedFirstBackup = false).firstBackupPending)
    }

    @Test
    fun `it lets go once the first backup is recorded as done`() {
        assertFalse(ReconcileUiState(wizardStep = WIZARD_BACKUP_STEP, hasCompletedFirstBackup = true).firstBackupPending)
    }

    @Test
    fun `it does not hold an install that never reached the backup step`() {
        assertFalse(ReconcileUiState(wizardStep = 4, hasCompletedFirstBackup = false).firstBackupPending)
        assertFalse(ReconcileUiState(wizardStep = 0, hasCompletedFirstBackup = false).firstBackupPending)
    }
}
