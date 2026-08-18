package com.gallery.sync.worker

import android.provider.MediaStore
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/**
 * Schedules automatic backup.
 *
 * Two jobs working together, because neither is sufficient alone:
 *
 *  - **Content-triggered.** The system tells us when MediaStore changes, so a new photo uploads
 *    shortly after it is taken rather than at the next arbitrary tick. This is what makes the app
 *    feel continuous, matching what Samsung's own sync does. Content triggers only work on
 *    one-time requests, so the worker re-enqueues this after every run.
 *  - **Periodic safety net.** Content triggers can be missed — Doze, a reboot, the app being
 *    force-stopped. A slow periodic pass catches whatever slipped through. Without it a missed
 *    trigger would mean a photo silently never backed up.
 */
object BackupScheduling {

    const val CONTENT_TRIGGER_WORK = "gallery-sync-backup-on-change"
    const val PERIODIC_WORK = "gallery-sync-backup-periodic"

    /** Turns automatic backup on. Safe to call repeatedly. */
    fun enable(workManager: WorkManager, allowMeteredNetwork: Boolean) {
        enqueueContentTriggered(workManager, allowMeteredNetwork)

        val request = PeriodicWorkRequestBuilder<BackupWorker>(
            repeatInterval = 6,
            repeatIntervalTimeUnit = TimeUnit.HOURS
        )
            .setConstraints(constraints(allowMeteredNetwork))
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.MINUTES)
            .build()

        // KEEP so re-enabling on every app launch does not reset the interval and postpone the
        // next run indefinitely.
        workManager.enqueueUniquePeriodicWork(
            PERIODIC_WORK,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }

    fun disable(workManager: WorkManager) {
        workManager.cancelUniqueWork(CONTENT_TRIGGER_WORK)
        workManager.cancelUniqueWork(PERIODIC_WORK)
    }

    /**
     * Arms the watch for new photos.
     *
     * Called again by [BackupWorker] after each run, because a content-triggered request fires
     * once. It waits for the next change rather than running immediately, so re-arming does not
     * loop.
     */
    fun enqueueContentTriggered(workManager: WorkManager, allowMeteredNetwork: Boolean) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(networkType(allowMeteredNetwork))
            .setRequiresBatteryNotLow(true)
            .setRequiresStorageNotLow(true)
            .addContentUriTrigger(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, true)
            .addContentUriTrigger(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, true)
            // Wait a little after a change in case more arrive — a burst of photos should be one
            // run, not twenty. The max delay stops a continuously-changing library from deferring
            // the run forever.
            .setTriggerContentUpdateDelay(TRIGGER_DELAY_SECONDS, TimeUnit.SECONDS)
            .setTriggerContentMaxDelay(TRIGGER_MAX_DELAY_SECONDS, TimeUnit.SECONDS)
            .build()

        val request = OneTimeWorkRequestBuilder<BackupWorker>()
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 5, TimeUnit.MINUTES)
            .build()

        workManager.enqueueUniqueWork(
            CONTENT_TRIGGER_WORK,
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    private fun constraints(allowMeteredNetwork: Boolean) = Constraints.Builder()
        .setRequiredNetworkType(networkType(allowMeteredNetwork))
        .setRequiresBatteryNotLow(true)
        .setRequiresStorageNotLow(true)
        .build()

    /**
     * `CONNECTED` allows mobile data; `UNMETERED` is Wi-Fi and equivalents.
     *
     * Defaults to unmetered elsewhere — uploading several gigabytes over cellular is an expensive
     * surprise unless the user asked for it.
     */
    private fun networkType(allowMeteredNetwork: Boolean) =
        if (allowMeteredNetwork) NetworkType.CONNECTED else NetworkType.UNMETERED

    private const val TRIGGER_DELAY_SECONDS = 30L
    private const val TRIGGER_MAX_DELAY_SECONDS = 300L
}
