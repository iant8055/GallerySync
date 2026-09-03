package com.gallery.sync.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.gallery.sync.data.local.settings.BackupPreferences
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
        // A run the user asked for. Carried in the input data and passed to every continuation, so
        // the whole chain stays user-initiated rather than only its first batch.
        val manual = inputData.getBoolean(BackupScheduling.KEY_MANUAL, false)
        Logger.i(TAG, "backup run starting${if (manual) " (manual)" else ""}")

        val preferences = settings.current()

        // Held by the user, so nothing transfers — including a manual run, because Sync now is not
        // reachable while paused and anything else arriving here is a trigger the user already said
        // no to.
        //
        // Declining here rather than tearing down the schedule is the point: the triggers stay
        // armed, so Resume needs only to clear the flag rather than rebuild anything, and a pause
        // survives every path that would otherwise restart a cancelled chain.
        if (preferences.isPaused) {
            Logger.i(TAG, "backup run declined: paused by the user")
            return Result.success()
        }

        // Before a byte moves. A session held since an interruption more than ten minutes ago is
        // dropped, so the file starts clean rather than resuming against local bytes that may have
        // changed in the meantime.
        engine.discardStaleUploadSessions()

        // The denominator for the percentage on the hero. Set once per chain, not per batch.
        engine.updateRunBaseline()

        // The first whole-library upload is the heaviest thing this app does — 148 GB and roughly
        // fourteen hours on a real device — so it waits for a moment the user chose. Only automatic
        // runs are held: "Sync now" goes straight to the engine and is never gated, because someone
        // who asked has already decided this is a good moment.
        //
        // Refreshing the ledger still happens below either way. Knowing what is outstanding costs
        // nothing and is what lets the screen say how much is waiting.
        val hold = if (preferences.hasCompletedFirstBackup || manual) {
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
            // Lift the gate before returning, not after uploading. This check used to sit
            // downstream of this return, so while the window was closed nothing was capable of
            // noticing the window was no longer needed — the backlog could be empty for a day and
            // the app would still announce it was waiting. See FIX-001.
            if (engine.outstandingCount() == 0) {
                Logger.i(TAG, "backlog already clear; first-backup window no longer applies")
                settings.markFirstBackupComplete()
                // Closes the run's denominator when the queue is drained. Doing this only at the start of
        // the next run was not enough: by then new files may exist, so nothing cleared the old
        // baseline and the next run opened part-finished against it.
        engine.updateRunBaseline()

        rearmContentTrigger(preferences)
                return Result.success()
            }

            // success, not retry: nothing is wrong, and a retry would burn backoff attempts waiting
            // for a clock. The periodic pass and the content trigger both come back on their own.
            Logger.i(TAG, "first backup held ($hold); not uploading yet")
            rearmContentTrigger(preferences)
            return Result.success()
        }

        val allAlbums = inputData.getBoolean(BackupScheduling.KEY_ALL_ALBUMS, false)
        val result = engine.uploadPending(allAlbums = allAlbums) { progress ->
            setProgressAsync(
                Data.Builder()
                    .putInt(PROGRESS_COMPLETED, progress.completed)
                    .putInt(PROGRESS_TOTAL, progress.total)
                    .putString(PROGRESS_FILE, progress.currentFile)
                    .putLong(PROGRESS_CURRENT_SENT, progress.currentBytesSent)
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
                preferences.allowMeteredNetwork,
                manual = manual,
                allAlbums = allAlbums
            )
        }

        // Carried out of the worker so the screen can say what happened. Without this the UI keeps
        // whatever it last saw — which after a run that stopped on a full drive means "Uploading
        // 2 of 16" sitting there while the actionable reason is invisible. Observed 26 Aug 2026.
        val outcome = Data.Builder()
            .putInt(RESULT_UPLOADED, result.uploaded)
            .putInt(RESULT_SKIPPED, result.skipped)
            .putInt(RESULT_FAILED, result.failed)
            .putInt(RESULT_DEFERRED, result.deferred)
            .putInt(RESULT_PRUNED, result.pruned)
            .putInt(RESULT_REMAINING, result.remaining)
            .putString(RESULT_STOPPED, result.stoppedBecause?.name)
            .build()

        rearmContentTrigger(preferences)

        return when (result.stoppedBecause) {
            StopReason.NETWORK -> Result.retry()

            StopReason.NO_TOKEN,
            StopReason.UNAUTHORIZED,
            StopReason.DRIVE_FULL,
            StopReason.NO_MEDIA_ACCESS -> Result.failure(outcome)

            null -> Result.success(outcome)
        }
    }

    /**
     * Re-arms the watch for new photos.
     *
     * A content-triggered request fires once, so without this the app backs up one batch and goes
     * quiet forever.
     *
     * **Called at the end of the run, never at the start.** `enqueueContentTriggered` uses
     * `REPLACE` on `CONTENT_TRIGGER_WORK`, and when this run *is* that work, replacing it cancels
     * the worker doing the replacing. It did exactly that on the Fold 4, 26 Aug 2026: three photos
     * added to a Sync album, the run cancelled 188ms after starting, mid-`refreshLedger`, and
     * nothing uploaded.
     *
     * It hid for so long because a run that finds nothing to do finishes inside ~44ms and beats the
     * cancellation. Only a run with real work to do lives long enough to be killed by it.
     *
     * The original reason for arming first — a crash later in the run would leave the watch
     * unarmed — is covered twice already: `enable()` runs at application start, and the 6-hourly
     * periodic pass catches whatever a missed trigger dropped.
     */
    private suspend fun rearmContentTrigger(preferences: BackupPreferences) {
        if (!preferences.isAutomaticEnabled) return
        BackupScheduling.enqueueContentTriggered(
            WorkManager.getInstance(applicationContext),
            preferences.allowMeteredNetwork
        )
    }

    companion object {
        const val TAG = "BackupWorker"
        const val PROGRESS_COMPLETED = "completed"
        const val PROGRESS_TOTAL = "total"
        const val PROGRESS_FILE = "file"
        const val PROGRESS_PERCENT = "percent"

        /**
         * Bytes of the **current file** already sent.
         *
         * Not a per-run total. A run total resets when the process does, while the baseline it
         * would be divided by persists — so the two measured different things and the percentage
         * lurched whenever the app restarted. The ledger already knows what is finished; this is
         * only the part it cannot see, the file in flight.
         */
        const val PROGRESS_CURRENT_SENT = "currentSent"

        const val RESULT_UPLOADED = "r_uploaded"
        const val RESULT_SKIPPED = "r_skipped"
        const val RESULT_FAILED = "r_failed"
        const val RESULT_DEFERRED = "r_deferred"
        const val RESULT_PRUNED = "r_pruned"
        const val RESULT_REMAINING = "r_remaining"
        const val RESULT_STOPPED = "r_stopped"
    }
}
