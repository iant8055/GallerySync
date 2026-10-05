package com.gallery.sync.ui.backup

import com.gallery.sync.domain.backup.WizardGate

/**
 * Whether to put the *New Albums* pop-up in front of the user.
 *
 * **It must never appear while the wizard is running.** Ian, 5 Oct 2026, after a fresh install on his Galaxy
 * Z Fold 8: *"this New Albums should NOT appear until AFTER the Wizard is FINISHED"*. The pop-up came up over
 * the wizard's Backup Progress card, during the first backup, asking him to choose a mode for 86 albums —
 * album modes put in front of somebody who is still being walked through setup, which is the same boundary
 * CLAUDE.md draws when it says the wizard does not know album modes exist.
 *
 * It happened because `BackupScreen` composes behind the tour and its own condition said nothing about the
 * wizard: waiting albums, not already narrowed to them, not already asked. Nothing more.
 *
 * "After the wizard is finished" is `WizardGate.finished`, the same predicate that decides whether the wizard
 * shows at all, so the two can never drift apart. While the wizard is unfinished the prompt simply does not
 * exist: nothing is recorded, nothing is dismissed, and the albums are still waiting afterwards.
 */
object NewAlbumsPrompt {

    /**
     * @param firstBackupDone the record `WizardGate` reads
     * @param setupCompleted the other record it reads — only the Finish button writes it
     * @param waitingAlbums albums with no mode chosen
     * @param showingNewOnly the list is already narrowed to the new albums, so the pop-up would be redundant
     * @param alreadyAsked every waiting album has been through this pop-up once
     */
    fun shows(
        firstBackupDone: Boolean,
        setupCompleted: Boolean,
        waitingAlbums: Int,
        showingNewOnly: Boolean,
        alreadyAsked: Boolean
    ): Boolean {
        if (!WizardGate.finished(firstBackupDone, setupCompleted)) return false
        return waitingAlbums > 0 && !showingNewOnly && !alreadyAsked
    }
}
