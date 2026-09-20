package com.gallery.sync.worker

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.WorkManager
import com.gallery.sync.data.local.media.VideoOptimiser
import com.gallery.sync.data.local.settings.BackupSettings
import com.gallery.sync.domain.backup.VideoOptimisePolicy
import com.gallery.sync.util.Logger
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

/**
 * The two ways a video chain starts, so no caller has to assemble the gates itself.
 *
 * Both are read from the Settings tree afresh each time. A caller that had to pass them in would be
 * one more place for them to be assembled wrongly, which is the failure this whole area has had.
 *
 * **Automatic** is asked from the end of a backup run and when a switch changes. A backup run
 * reaches its end on every content trigger and every six-hourly pass, and that is what brings a clip
 * that has just grown older than the age setting into range: nothing else is watching the clock.
 *
 * **Now** is the button. It is the same chain, minus the wait for the charger.
 */
@Singleton
class VideoOptimiseLauncher @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val settings: BackupSettings,
    private val videoOptimiser: VideoOptimiser
) {

    /**
     * Starts a chain if video should be optimising on its own and there is something to do.
     *
     * Returns whether one was queued. It queues nothing when a chain is already waiting or running:
     * asking again on every backup run must not stack batches.
     */
    suspend fun requestAutomatic(): Boolean {
        val prefs = settings.current()
        if (!VideoOptimisePolicy.runsAutomatically(
                setupComplete = prefs.hasCompletedSetup,
                optimiseEnabled = prefs.isOptimiseEnabled,
                optimiseVideo = prefs.optimiseVideo,
                mode = prefs.videoOptimiseMode
            )
        ) return false

        val workManager = WorkManager.getInstance(context)
        if (BackupScheduling.videoOptimiseWork(workManager).first().any { !it.state.isFinished }) {
            return false
        }

        val ready = videoOptimiser.readiness()
        if (ready.count == 0) return false

        Logger.i(TAG, "${ready.count} clips are ready; queueing video optimising for the charger")
        BackupScheduling.enqueueVideoOptimise(
            workManager,
            requiresCharging = true,
            excludedClips = emptySet(),
            policy = ExistingWorkPolicy.KEEP
        )
        return true
    }

    /**
     * Starts a chain because somebody pressed **Sync now** and video is set to Manual.
     *
     * Ian, 19 Sept 2026: Manual means through the Sync Now button. Not held for the charger, since they
     * asked. Does nothing for Automatic video, which has already been queued for the charger, and does
     * not need a second nudge that would skip the wait.
     */
    suspend fun requestOnSyncNow(): Boolean {
        val prefs = settings.current()
        if (!VideoOptimisePolicy.runsOnSyncNow(
                setupComplete = prefs.hasCompletedSetup,
                optimiseEnabled = prefs.isOptimiseEnabled,
                optimiseVideo = prefs.optimiseVideo,
                mode = prefs.videoOptimiseMode
            )
        ) return false
        if (videoOptimiser.readiness().count == 0) return false
        return requestNow()
    }

    /**
     * Starts a chain because somebody pressed Optimise now.
     *
     * Not held for the charger, since they asked. A run that is only *waiting* for it is replaced;
     * one that is executing is left alone, because replacing it would throw away the clip in flight.
     * Still requires a battery that is not low, as every background job here does.
     */
    suspend fun requestNow(): Boolean {
        val prefs = settings.current()
        if (!prefs.hasCompletedSetup || !prefs.isOptimiseEnabled || !prefs.optimiseVideo) return false

        val workManager = WorkManager.getInstance(context)
        if (BackupScheduling.videoOptimiseRunning(workManager)) return false

        Logger.i(TAG, "video optimising started by hand")
        BackupScheduling.enqueueVideoOptimise(
            workManager,
            requiresCharging = false,
            excludedClips = emptySet(),
            policy = ExistingWorkPolicy.REPLACE
        )
        return true
    }

    private companion object {
        const val TAG = "VideoOptimiseLauncher"
    }
}
