package com.gallery.sync

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import androidx.work.WorkManager
import com.gallery.sync.data.local.settings.BackupSettings
import com.gallery.sync.util.Logger
import com.gallery.sync.worker.BackupScheduling
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Also supplies WorkManager's configuration, so workers can be constructor-injected.
 *
 * This requires WorkManager's automatic startup to be disabled in the manifest — otherwise it
 * initialises itself with the default factory before Hilt is ready, and [BackupWorker] fails to
 * construct at the moment it is needed.
 */
@HiltAndroidApp
class GallerySyncApplication : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var settings: BackupSettings

    private val scope = CoroutineScope(SupervisorJob())

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        armAutomaticSync()
    }

    /**
     * Arms the background work if the user wants automatic sync, and checks what is already
     * outstanding.
     *
     * Necessary because the preference defaults to on: until 19 Aug 2026 scheduling was only ever
     * armed by the settings toggle being flipped, so a default of "on" would have described
     * behaviour that never actually happened. Someone who installs and signs in must get automatic
     * sync without first toggling it off and on again.
     *
     * Safe to run on every launch. The periodic request uses `KEEP`, so re-arming does not reset
     * the interval and postpone the next run.
     *
     * ### Why this also enqueues a continuation, not only the future watch — TASK-021
     *
     * This runs before any other component in the process, on every cold start, for any reason.
     * WorkManager's own force-stop detection also runs once per cold start, and on this device
     * intermittently — not always — concludes the app was force-stopped and cancels whatever
     * content-trigger work was already in flight, rather than running it. A new photo's own trigger
     * can be the casualty, and the replacement watch that gets scheduled only catches the *next*
     * change, not what it just dropped. See TASK-021 and the 18 Sept MILESTONES entries.
     *
     * A continuation enqueued here is a fresh call made after this process's own force-stop check
     * has already run, so it is not subject to the same cancellation — the same reason the manual
     * "Right now" and "Sync now" paths have never failed. `BackupWorker` always refreshes the ledger
     * and checks everything outstanding regardless of what triggered it, so this catches a dropped
     * photo the same day, on the next time the process is touched at all, rather than waiting for
     * another trigger or the six-hour net.
     */
    private fun armAutomaticSync() {
        scope.launch {
            runCatching {
                val preferences = settings.current()
                if (preferences.isAutomaticEnabled) {
                    val workManager = WorkManager.getInstance(this@GallerySyncApplication)
                    BackupScheduling.enable(workManager, preferences.allowMeteredNetwork)
                    BackupScheduling.enqueueContinuation(workManager, preferences.allowMeteredNetwork)
                }
            }.onFailure {
                // Never fatal. Failing to schedule costs a delayed backup; crashing on launch
                // costs the app.
                Logger.e(TAG, "could not arm automatic sync: ${it.javaClass.simpleName}")
            }
        }
    }

    private companion object {
        const val TAG = "GallerySyncApplication"
    }
}
