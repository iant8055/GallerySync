package com.gallery.sync.worker

import android.provider.MediaStore
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.first

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
    const val CONTINUATION_WORK = "gallery-sync-backup-continuation"
    const val PERIODIC_WORK = "gallery-sync-backup-periodic"

    /**
     * The chain a person started by pressing Sync now.
     *
     * Named separately from [CONTINUATION_WORK] so Stop can cancel it without touching automatic
     * work, and so its continuations stay recognisably user-initiated all the way down — which is
     * what keeps them out of the first-backup window.
     */
    const val MANUAL_WORK = "gallery-sync-backup-manual"
    const val OPTIMISE_WORK = "gallery-sync-optimise"

    /** Marks a run as user-initiated. Carried into every continuation of that chain. */
    const val KEY_MANUAL = "manual"
    const val KEY_OPTIMISE_PHASE = "optimise_phase"
    const val PHASE_PHOTOS = "photos"
    const val PHASE_VIDEO = "video"

    /** Upload all albums regardless of album modes. Used by the wizard on fresh installs. */
    const val KEY_ALL_ALBUMS = "all_albums"

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
        workManager.cancelUniqueWork(CONTINUATION_WORK)
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

    /**
     * Schedules the next batch when the current run finished with files still pending.
     *
     * Distinct from the content trigger (which waits for a MediaStore change) and the periodic
     * net (which waits 6 hours). This fires as soon as constraints are met, so a library that
     * spans multiple batches uploads continuously rather than stalling between runs.
     */
    fun enqueueContinuation(
        workManager: WorkManager,
        allowMeteredNetwork: Boolean,
        manual: Boolean = false,
        allAlbums: Boolean = false,
        initialDelayMillis: Long = 0L
    ) {
        val request = OneTimeWorkRequestBuilder<BackupWorker>()
            .setConstraints(constraints(allowMeteredNetwork))
            .apply { if (initialDelayMillis > 0L) setInitialDelay(initialDelayMillis, TimeUnit.MILLISECONDS) }
            .setInputData(
                Data.Builder()
                    .putBoolean(KEY_MANUAL, manual)
                    .putBoolean(KEY_ALL_ALBUMS, allAlbums)
                    .build()
            )
            .build()

        // A manual chain continues under its own name, so Stop cancels the whole chain with one
        // call and automatic work is left alone.
        workManager.enqueueUniqueWork(
            if (manual) MANUAL_WORK else CONTINUATION_WORK,
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    /**
     * Starts the chain a person asked for.
     *
     * Runs through WorkManager rather than in a ViewModel scope, which dies with the screen: a run
     * to completion over a real library is hours. Network constraints still apply, because "use
     * mobile data" is a separate choice the user made and a tap on Sync now is not a licence to
     * spend their data plan.
     */
    fun enqueueManualRun(
        workManager: WorkManager,
        allowMeteredNetwork: Boolean,
        allAlbums: Boolean = false
    ) {
        enqueueContinuation(workManager, allowMeteredNetwork, manual = true, allAlbums = allAlbums)
    }

    /**
     * The wizard's delayed first backup.
     *
     * Held by WorkManager rather than by a timer in the wizard, so it fires whether or not the app
     * is running. A countdown that only advances while someone watches it is not a delay, it is a
     * progress bar with extra steps.
     *
     * Still a manual run: the user picked this moment, which is exactly what the first-backup
     * window's manual exemption is for. Network and battery constraints continue to apply.
     *
     * Enqueued under [MANUAL_WORK] with `REPLACE`, so re-arming, "Sync now" and a plain manual run
     * all supersede a pending one rather than stacking a second chain behind it.
     */
    fun enqueueDelayedManualRun(
        workManager: WorkManager,
        allowMeteredNetwork: Boolean,
        delayMillis: Long,
        allAlbums: Boolean = false
    ) {
        enqueueContinuation(
            workManager,
            allowMeteredNetwork,
            manual = true,
            allAlbums = allAlbums,
            initialDelayMillis = delayMillis
        )
    }

    /**
     * Runs one optimise phase in the background.
     *
     * Its own unique chain, kept apart from the backup one: an abort should stop uploading without
     * abandoning proxies half-written, and the two run at different times over different files.
     *
     * `APPEND_OR_REPLACE` rather than `REPLACE`, so the video phase queues behind the photo phase
     * instead of cancelling it — the wizard enqueues them as each consent is granted, and the second
     * must not discard the first.
     */
    fun enqueueOptimise(workManager: WorkManager, phase: String) {
        val request = OneTimeWorkRequestBuilder<OptimiseWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiresBatteryNotLow(true)
                    .setRequiresStorageNotLow(true)
                    .build()
            )
            .addTag(optimiseTag(phase))
            .setInputData(Data.Builder().putString(KEY_OPTIMISE_PHASE, phase).build())
            .build()

        workManager.enqueueUniqueWork(
            OPTIMISE_WORK,
            ExistingWorkPolicy.APPEND_OR_REPLACE,
            request
        )
    }

    /**
     * Identifies a queued pass by which phase it is, since both share [OPTIMISE_WORK].
     *
     * The unique name alone cannot answer "is a photo pass already pending", because
     * `APPEND_OR_REPLACE` deliberately lets the video pass queue behind the photo one.
     */
    fun optimiseTag(phase: String) = "$OPTIMISE_WORK-$phase"

    /**
     * Starts a phase unless one is already queued or running.
     *
     * Two things now start the optimise passes — the worker chain when an unattended first backup
     * drains, and the wizard card when someone is watching — and both are correct. Without this
     * check, opening the app during an optimise the worker already began would append a second pass
     * of the same phase: harmless in outcome, since the second finds the files already proxied, but
     * it doubles the video transcodes and makes the progress the card reads meaningless.
     *
     * Deliberately not used by [OptimiseWorker]'s own continuation: that runs while its phase is
     * still `RUNNING` and carries the same tag, so it must append rather than ask.
     *
     * @return true when a pass was enqueued, false when one was already live.
     */
    suspend fun enqueueOptimiseIfAbsent(workManager: WorkManager, phase: String): Boolean {
        val alreadyLive = workManager.getWorkInfosForUniqueWorkFlow(OPTIMISE_WORK)
            .first()
            .any { !it.state.isFinished && optimiseTag(phase) in it.tags }

        if (alreadyLive) return false

        enqueueOptimise(workManager, phase)
        return true
    }

    /**
     * Whether the optimise chain is queued or running, in any phase.
     *
     * Read by [BackupWorker] to tell a content trigger it raised itself from a real one: optimising
     * rewrites files, MediaStore reports the rewrites, and the trigger fires on the app's own work.
     * Unlike [enqueueOptimiseIfAbsent] this asks about the whole chain rather than one phase, since
     * the caller does not care which pass is running - only that one is.
     */
    suspend fun optimiseChainLive(workManager: WorkManager): Boolean =
        workManager.getWorkInfosForUniqueWorkFlow(OPTIMISE_WORK)
            .first()
            .any { !it.state.isFinished }

    /** Stops the optimise chain. Files already proxied stay proxied; nothing is undone. */
    fun cancelOptimise(workManager: WorkManager) {
        workManager.cancelUniqueWork(OPTIMISE_WORK)
    }

    /**
     * Whether a manual chain is queued, waiting out a delay, or running.
     *
     * Asked when a countdown reaches zero, because a countdown proves only that a due time was
     * written: the arming itself can be lost, and watching for a chain that was never queued waits
     * for ever while the card says it is uploading.
     */
    suspend fun manualRunLive(workManager: WorkManager): Boolean =
        workManager.getWorkInfosForUniqueWorkFlow(MANUAL_WORK)
            .first()
            .any { !it.state.isFinished }

    /** Stops a manual chain, including whatever batch it is in the middle of. */
    fun cancelManualRun(workManager: WorkManager) {
        workManager.cancelUniqueWork(MANUAL_WORK)
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
