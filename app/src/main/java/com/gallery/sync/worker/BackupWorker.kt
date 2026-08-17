package com.gallery.sync.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.gallery.sync.domain.backup.BackupEngine
import com.gallery.sync.domain.backup.StopReason
import com.gallery.sync.util.Logger
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

/**
 * Runs a backup pass in the background.
 *
 * Refreshes the ledger, then uploads a bounded batch. Bounded rather than exhaustive because
 * WorkManager will stop a worker that runs too long, and a batch that is interrupted loses only
 * the file in flight — the ledger already records everything that succeeded.
 */
@HiltWorker
class BackupWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val engine: BackupEngine
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        Logger.i(TAG, "backup run starting")

        val seen = engine.refreshLedger()
        if (seen == null) {
            // No media permission. Retrying on a timer will not obtain one; the user has to grant
            // it in the app, and that will schedule a fresh run.
            Logger.w(TAG, "no media access, not retrying")
            return Result.failure()
        }

        val result = engine.uploadPending()
        Logger.i(
            TAG,
            "backup run finished: ${result.uploaded} uploaded, ${result.failed} failed, " +
                "${result.remaining} remaining, stopped=${result.stoppedBecause}"
        )

        return when (result.stoppedBecause) {
            // Transient: come back later with backoff.
            StopReason.NETWORK -> Result.retry()

            // The user must act — sign in, or free space in OneDrive. Retrying on a timer would
            // burn battery to reproduce the same error, so stop and let the app prompt them.
            StopReason.NO_TOKEN,
            StopReason.UNAUTHORIZED,
            StopReason.DRIVE_FULL,
            StopReason.NO_MEDIA_ACCESS -> Result.failure()

            // Finished the batch. If files remain, the next periodic run continues; WorkManager
            // is a better scheduler for that than a loop holding a wakelock.
            null -> Result.success()
        }
    }

    companion object {

        private const val TAG = "BackupWorker"

        const val WORK_NAME = "gallery-sync-backup"

        /**
         * Schedules the recurring backup.
         *
         * `KEEP` so that re-scheduling on every app launch does not reset the interval and
         * postpone a run indefinitely.
         */
        fun schedule(workManager: WorkManager) {
            val constraints = Constraints.Builder()
                // Unmetered by default: uploading a library of videos over mobile data without
                // asking would be an expensive surprise. Making this configurable is a later task.
                .setRequiredNetworkType(NetworkType.UNMETERED)
                .setRequiresBatteryNotLow(true)
                .setRequiresStorageNotLow(true)
                .build()

            val request = PeriodicWorkRequestBuilder<BackupWorker>(
                repeatInterval = 6,
                repeatIntervalTimeUnit = TimeUnit.HOURS
            )
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.MINUTES)
                .build()

            workManager.enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }
    }
}
