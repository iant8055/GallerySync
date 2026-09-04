package com.gallery.sync.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.gallery.sync.data.local.media.ProxyApplier
import com.gallery.sync.data.local.media.VideoOptimiser
import com.gallery.sync.data.local.settings.BackupSettings
import com.gallery.sync.util.Logger
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Replaces originals with proxies, outside the wizard's lifetime.
 *
 * ### Why this exists
 *
 * Both optimise passes used to run in `viewModelScope`, which is cleared with the activity. Uploading
 * survived Close because it is a WorkManager chain; optimising did not, and the difference was
 * invisible to the user — the card said "You can check progress any time by opening the app", which
 * was true of one half and not the other. Observed on the Moto G, 4 Sept 2026: closing during the
 * video pass left three clips transcoded and two untouched, with nothing to resume them.
 *
 * ### Consent still belongs to the activity
 *
 * `MediaStore.createWriteRequest` can only be raised from an Activity, so the wizard still asks; this
 * worker runs only after that grant exists. The grant is per-URI and persists, which is what lets the
 * work continue once the wizard is gone.
 *
 * ### Bounded, like [BackupWorker]
 *
 * A transcode is tens of seconds a clip and WorkManager stops a worker that runs too long, so each
 * run takes a bounded batch and enqueues a continuation while work remains. An interrupted batch
 * loses only the file in flight; every completed one is already recorded in the ledger.
 *
 * Nothing here removes anything. A proxy is written over a file the ledger has already verified in
 * the cloud, and `ProxyApplier` refuses any file that would grow.
 */
@HiltWorker
class OptimiseWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val proxyApplier: ProxyApplier,
    private val videoOptimiser: VideoOptimiser,
    private val settings: BackupSettings
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val phase = inputData.getString(BackupScheduling.KEY_OPTIMISE_PHASE)
            ?: return Result.success()

        val remaining = when (phase) {
            BackupScheduling.PHASE_PHOTOS -> runPhotos()
            BackupScheduling.PHASE_VIDEO -> runVideo()
            else -> {
                Logger.w(TAG, "unknown optimise phase '$phase'")
                0
            }
        }

        // More to do than one batch could hold. Re-enqueued rather than looped, so each run stays
        // inside WorkManager's execution window instead of being killed part way through a file.
        if (remaining > 0) {
            Logger.i(TAG, "$phase: $remaining left, queueing another batch")
            BackupScheduling.enqueueOptimise(WorkManager.getInstance(applicationContext), phase)
        }

        return Result.success()
    }

    private suspend fun runPhotos(): Int {
        val candidates = proxyApplier.candidatesAll()
        if (candidates.isEmpty()) {
            Logger.d(TAG, "photos: nothing eligible")
            return 0
        }

        val batch = candidates.take(PHOTO_BATCH)
        Logger.i(TAG, "photos: proxying ${batch.size} of ${candidates.size}")
        proxyApplier.apply(batch)
        return (candidates.size - batch.size).coerceAtLeast(0)
    }

    private suspend fun runVideo(): Int {
        val candidates = videoOptimiser.wizardCandidates()
        if (candidates.isEmpty()) {
            Logger.d(TAG, "video: nothing eligible")
            return 0
        }

        val quality = settings.current().videoQuality
        Logger.i(TAG, "video: optimising up to $VIDEO_BATCH of ${candidates.size} at $quality")
        videoOptimiser.runForWizard(quality, limit = VIDEO_BATCH)
        return (candidates.size - VIDEO_BATCH).coerceAtLeast(0)
    }

    private companion object {
        const val TAG = "OptimiseWorker"

        /** A photo proxy is fast — around 150 in ninety seconds on the Moto G. */
        const val PHOTO_BATCH = 60

        /** A transcode is tens of seconds, so few enough to finish well inside the window. */
        const val VIDEO_BATCH = 3
    }
}
