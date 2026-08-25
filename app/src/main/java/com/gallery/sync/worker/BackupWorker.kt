package com.gallery.sync.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.gallery.sync.data.local.settings.BackupSettings
import com.gallery.sync.domain.backup.BackupEngine
import com.gallery.sync.domain.backup.FirstBackupWindow
import com.gallery.sync.domain.backup.StopReason
import java.time.LocalTime
import com.gallery.sync.util.ChargingState
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
    private val settings: BackupSettings,
    private val charging: ChargingState
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

        // The first whole-library upload is the heaviest thing this app does — 148 GB and roughly
        // fourteen hours on a real device — so it waits for a moment the user chose. Only automatic
        // runs are held: "Sync now" goes straight to the engine and is never gated, because someone
        // who asked has already decided this is a good moment.
        //
        // Refreshing the ledger still happens below either way. Knowing what is outstanding costs
        // nothing and is what lets the screen say how much is waiting.
        val hold = if (preferences.hasCompletedFirstBackup) {
            null
        } else {
            FirstBackupWindow.heldBecause(
                hourOfDay = LocalTime.now().hour,
                isCharging = charging.isCharging(),
                startHour = preferences.firstBackupStartHour,
                requiresCharging = preferences.firstBackupRequiresCharging
            )
        }

        val seen = engine.refreshLedger()
        if (seen == null) {
            // No media permission. A timer will not obtain one — the user has to grant it, and
            // doing so schedules a fresh run.
            Logger.w(TAG, "no media access, not retrying")
            return Result.failure()
        }

        if (hold != null) {
            // success, not retry: nothing is wrong, and a retry would burn backoff attempts waiting
            // for a clock. The periodic pass and the content trigger both come back on their own.
            Logger.i(TAG, "first backup held ($hold); not uploading yet")
            return Result.success()
        }

        val result = engine.uploadPending { progress ->
            setProgressAsync(
                Data.Builder()
                    .putInt(PROGRESS_COMPLETED, progress.completed)
                    .putInt(PROGRESS_TOTAL, progress.total)
                    .putString(PROGRESS_FILE, progress.currentFile)
                    .putInt(PROGRESS_PERCENT, if (progress.currentBytesTotal > 0) {
                        ((progress.currentBytesSent * 100) / progress.currentBytesTotal)
                            .toInt().coerceIn(0, 100)
                    } else 0)
                    .build()
            )
        }
        Logger.i(
            TAG,
            "backup run finished: ${result.uploaded} uploaded, ${result.skipped} already there, " +
                "${result.failed} failed, ${result.deferred} deferred (album not listable), " +
                "${result.remaining} remaining, " +
                "stopped=${result.stoppedBecause}"
        )

        // The backlog is clear, so the overnight window has done its job and lifts for good. Every
        // later run is incremental; keeping the gate would make a photo taken at noon wait until 1am.
        if (result.isComplete && !preferences.hasCompletedFirstBackup) {
            Logger.i(TAG, "backlog cleared; first-backup window no longer applies")
            settings.markFirstBackupComplete()
        }

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

    companion object {
        const val TAG = "BackupWorker"
        const val PROGRESS_COMPLETED = "completed"
        const val PROGRESS_TOTAL = "total"
        const val PROGRESS_FILE = "file"
        const val PROGRESS_PERCENT = "percent"
    }
}
