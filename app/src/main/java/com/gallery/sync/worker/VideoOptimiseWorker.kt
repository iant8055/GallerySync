package com.gallery.sync.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.gallery.sync.data.local.media.VideoOptimiser
import com.gallery.sync.data.local.settings.BackupSettings
import com.gallery.sync.domain.backup.VideoOptimisePolicy
import com.gallery.sync.util.Logger
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Shrinks old video in Sync albums, after setup: what the Optimise video controls in Settings do.
 *
 * ### Not the wizard's worker
 *
 * [OptimiseWorker] is the one-time pass at install. It ignores album modes, the age and the switches,
 * because the library choice on the setup card is its only gate. This one is the opposite: it obeys
 * the Settings tree and nothing else. They are separate classes for the same reason CLAUDE.md keeps
 * setup and Settings apart — each reaching into the other's rules is how a setup answer ends up
 * governing a phone months later.
 *
 * The gates themselves live in [VideoOptimiser.run], which reads them fresh on every batch. So
 * switching Optimise video off while a chain is running ends it at the next batch boundary, with
 * nothing to cancel.
 *
 * ### Bounded, like the other workers
 *
 * A transcode is tens of seconds and WorkManager stops a worker that outlasts its window, so a
 * batch is [VideoOptimisePolicy.BATCH] clips and the rest are queued as a continuation. A batch cut
 * short loses only the clip in flight: the transcoder cancels and deletes its output, and nothing has
 * been overwritten until a full, validated copy exists.
 *
 * **What this does not remove.** It replaces a clip with a smaller copy, and only one whose original
 * OneDrive has confirmed at the same size (the candidate query requires it). It removes nothing and
 * calls no delete of any kind.
 */
@HiltWorker
class VideoOptimiseWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val videoOptimiser: VideoOptimiser,
    private val settings: BackupSettings
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        // Until setup is finished the wizard's pass owns video. This is the same check the triggers
        // make, repeated because a batch can sit queued through the moment setup is reset.
        if (!settings.current().hasCompletedSetup) {
            Logger.d(TAG, "setup is not finished; leaving video to the wizard")
            return Result.success()
        }

        val excluded = inputData.getStringArray(BackupScheduling.KEY_EXCLUDED_CLIPS)
            ?.toSet()
            ?: emptySet()
        val requiresCharging = inputData.getBoolean(BackupScheduling.KEY_REQUIRES_CHARGING, true)

        val result = videoOptimiser.run(limit = VideoOptimisePolicy.BATCH, exclude = excluded)
        Logger.i(
            TAG,
            "batch done: ${result.optimised} optimised, ${result.skipped} not worth it, " +
                "${result.failed} failed, ${result.notCovered} outside a granted folder"
        )

        val nowExcluded = excluded + result.failedIds
        val moreReady = if (result.attempted > 0) videoOptimiser.readiness(nowExcluded).count else 0

        if (VideoOptimisePolicy.shouldContinue(result.attempted, moreReady, nowExcluded.size)) {
            Logger.i(TAG, "$moreReady left, queueing another batch")
            // Appended, not replacing: this batch is still RUNNING while it queues the next, and a
            // replace would cancel the worker doing the replacing.
            BackupScheduling.enqueueVideoOptimise(
                WorkManager.getInstance(applicationContext),
                requiresCharging = requiresCharging,
                excludedClips = nowExcluded,
                policy = ExistingWorkPolicy.APPEND_OR_REPLACE
            )
        } else if (nowExcluded.size > VideoOptimisePolicy.MAX_EXCLUDED) {
            Logger.w(TAG, "too many clips failed in one chain; stopping until the next trigger")
        }

        return Result.success()
    }

    private companion object {
        const val TAG = "VideoOptimiseWorker"
    }
}
