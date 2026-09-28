package com.gallery.sync.domain.backup

/**
 * Whether the install wizard is shown instead of the app.
 *
 * ## ONCE THE INITIAL BACKUP HAS COMPLETED, NOTHING EVER SHOWS THE WIZARD AGAIN
 *
 * Ian, 28 Sept 2026, in capitals, after signing out of every cloud on the Moto G sent the app back into the wizard:
 * *"NOTHING SHOULD CALL THE WIZARD ONCE IT SUCCESSFULLY COMPLETES THE INITIAL BACKUP"* and *"Once the Wizard has
 * finished NOTHING should EVER CALL IT"*. It had been doing so since 24 Sept, when "not signed in to OneDrive" (from
 * the days when signing in was the first step of setup) became "no cloud connected" and kept pointing at the wizard.
 * Removing every folder to back up in Settings did the same. Nobody ever decided either.
 *
 * So the wizard is over for good the moment either record says so: the first backup finished
 * ([firstBackupDone], written once and never cleared) or setup was marked complete ([setupCompleted]). After that
 * the app opens whatever the state of the clouds or the folders, and says what is missing where it is fixed
 * (Settings), rather than sending the user through setup again.
 *
 * Before then, nothing here changed: the wizard shows until setup is finished, and it holds the app back while the
 * first backup is running, or while there is no cloud or no folder to back up.
 *
 * `WizardGateTest` pins this, and a source check there fails the build if `MainActivity` decides it any other way.
 */
object WizardGate {

    /** True once the wizard may never be shown again. */
    fun finished(firstBackupDone: Boolean, setupCompleted: Boolean): Boolean = firstBackupDone || setupCompleted

    fun shows(
        firstBackupDone: Boolean,
        setupCompleted: Boolean,
        hasSources: Boolean,
        firstBackupPending: Boolean,
        anyCloudConnected: Boolean
    ): Boolean {
        if (finished(firstBackupDone, setupCompleted)) return false
        return !setupCompleted || !hasSources || firstBackupPending || !anyCloudConnected
    }
}
