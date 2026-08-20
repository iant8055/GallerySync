package com.gallery.sync.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.gallery.sync.data.local.settings.BackupSettings
import com.gallery.sync.domain.backup.BackupEngine
import com.gallery.sync.domain.backup.StopReason
import com.gallery.sync.util.Logger
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Runs a backup pass in the background.
 *
 * Refreshes the ledger, then uploads a bounded batch. Bounded rather than exhaustive because
 * WorkManager stops a worker that runs too long, and an interrupted batch loses only the file in
 * flight — the ledger already records everything that succeeded.
 */
@HiltWorker
class BackupWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val engine: BackupEngine,
    private val settings: BackupSettings
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        Logger.i(TAG, "backup run starting")

        val preferences = settings.current()

        // Re-arm the watch for new photos straight away. A content-triggered request fires once,
        // so without this the app would back up exactly one batch and then go quiet forever.
        // Doing it first means a crash later in this run still leaves the watch armed.
        if (preferences.isAutomaticEnabled) {
            BackupScheduling.enqueueContentTriggered(
                WorkManager.getInstance(applicationContext),
                preferences.allowMeteredNetwork
            )
        }

        val seen = engine.refreshLedger()
        if (seen == null) {
            // No media permission. A timer will not obtain one — the user has to grant it, and
            // doing so schedules a fresh run.
            Logger.w(TAG, "no media access, not retrying")
            return Result.failure()
        }

        val result = engine.uploadPending()
        Logger.i(
            TAG,
            "backup run finished: ${result.uploaded} uploaded, ${result.skipped} already there, " +
                "${result.failed} failed, ${result.deferred} deferred (album not listable), " +
                "${result.remaining} remaining, " +
                "stopped=${result.stoppedBecause}"
        )

        // More files waiting and nothing wrong — schedule the next batch immediately rather than
        // waiting 6 hours for the periodic safety net. The byte budget splits large libraries into
        // multiple runs, and each one should follow the last without the user pressing a button.
        if (result.stoppedBecause == null && result.remaining > 0) {
            Logger.i(TAG, "${result.remaining} remaining, scheduling next batch")
            BackupScheduling.enqueueContinuation(
                WorkManager.getInstance(applicationContext),
                preferences.allowMeteredNetwork
            )
        }

        return when (result.stoppedBecause) {
            StopReason.NETWORK -> Result.retry()

            StopReason.NO_TOKEN,
            StopReason.UNAUTHORIZED,
            StopReason.DRIVE_FULL,
            StopReason.NO_MEDIA_ACCESS -> Result.failure()

            null -> Result.success()
        }
    }

    private companion object {
        const val TAG = "BackupWorker"
    }
}
