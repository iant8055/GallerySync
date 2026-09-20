package com.gallery.sync.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.Data
import com.gallery.sync.data.local.dao.BackupEntryDao
import com.gallery.sync.data.local.media.ProxyApplier
import com.gallery.sync.data.local.media.ProxyOutcome
import com.gallery.sync.data.local.media.VideoOptimiser
import com.gallery.sync.data.local.settings.BackupSettings
import com.gallery.sync.domain.backup.CameraOptimisePlan
import com.gallery.sync.domain.backup.CameraOptimiseSettings
import com.gallery.sync.domain.backup.PhotoOptimisePolicy
import com.gallery.sync.domain.backup.WizardBulkOptimise
import com.gallery.sync.util.Logger
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

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
    private val settings: BackupSettings,
    private val entryDao: BackupEntryDao
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val phase = inputData.getString(BackupScheduling.KEY_OPTIMISE_PHASE)
            ?: return Result.success()

        if (phase == BackupScheduling.PHASE_CAMERA) return runCamera()

        val remaining = when (phase) {
            BackupScheduling.PHASE_PHOTOS -> runPhotos()
            BackupScheduling.PHASE_SYNC_PHOTOS -> runSyncPhotos()
            BackupScheduling.PHASE_VIDEO -> runVideo()
            else -> {
                Logger.w(TAG, "unknown optimise phase '$phase'")
                0
            }
        }

        // More to do than one batch could hold. Re-enqueued rather than looped, so each run stays
        // inside WorkManager's execution window instead of being killed part way through a file.
        //
        // Enqueued directly, not through `enqueueOptimiseIfAbsent`: this phase is `RUNNING` right
        // now and carries the tag that check looks for, so asking would refuse its own continuation.
        if (remaining > 0) {
            Logger.i(TAG, "$phase: $remaining left, queueing another batch")
            BackupScheduling.enqueueOptimise(WorkManager.getInstance(applicationContext), phase)
            return Result.success()
        }

        // The photo pass has drained, so the video pass follows it here rather than on the wizard
        // card — same reason the upload hands off to us at all. A first run left alone overnight
        // used to do the upload and stop; both halves now carry themselves.
        if (phase == BackupScheduling.PHASE_PHOTOS) {
            handOffToVideo()
        }

        return Result.success()
    }

    /**
     * One batch of the Camera album's manual optimise, and a continuation while more is left.
     *
     * The list is worked out again here with [CameraOptimisePlan], from the cutoff the person tapped
     * with, so it is the list they were shown minus anything swiped out, finished or gone since. It
     * reads the Settings switches again too: switching photos off mid-run stops photos at the next file.
     *
     * A file that fails is not retried by the next batch. Its id travels in the continuation and is
     * stepped over, which is what keeps one unwritable file from being the first candidate of every
     * batch for ever. A batch that made no progress ends the chain instead of queueing another.
     *
     * Files outside a granted folder are written through the grant Android gave when the person
     * confirmed its dialog, which is asked for before this is queued. Where that grant is missing the
     * write fails and the file is stepped over like any other failure.
     */
    private suspend fun runCamera(): Result {
        val album = inputData.getString(BackupScheduling.KEY_OPTIMISE_ALBUM) ?: return Result.success()
        val before = inputData.getLong(BackupScheduling.KEY_OPTIMISE_BEFORE, Long.MIN_VALUE)
        if (before == Long.MIN_VALUE) return Result.success()
        val excluded = inputData.getStringArray(BackupScheduling.KEY_OPTIMISE_EXCLUDED)?.toMutableSet()
            ?: mutableSetOf()

        val prefs = settings.current()
        val plan = CameraOptimisePlan.of(
            entries = entryDao.entriesForAlbum(album),
            modifiedBeforeEpochSeconds = before,
            settings = CameraOptimiseSettings(
                enabled = prefs.isOptimiseEnabled,
                photos = prefs.optimisePhotos,
                videos = prefs.optimiseVideo,
                photoSavingPercent = 0,
                videoSavingPercent = 0
            )
        )
        val ready = proxyApplier.onDevice(plan.eligible.filter { it.id !in excluded })
        if (ready.isEmpty()) {
            Logger.d(TAG, "camera: nothing left to optimise")
            return Result.success()
        }

        val photos = ready.filter { !it.isVideo }.take(PHOTO_BATCH)
        val videos = ready.filter { it.isVideo }.take(VIDEO_BATCH)
        Logger.i(TAG, "camera: ${photos.size} photos and ${videos.size} clips of ${ready.size} ready")

        var progressed = 0
        for (photo in photos) {
            currentCoroutineContext().ensureActive()
            when (val outcome = proxyApplier.apply(listOf(photo))) {
                is ProxyOutcome.Completed -> progressed++
                is ProxyOutcome.Stopped -> {
                    Logger.w(TAG, "camera: ${outcome.failedFile} not written (${outcome.reason})")
                    excluded += photo.id
                }
                ProxyOutcome.NothingToDo, ProxyOutcome.NotSupported -> excluded += photo.id
            }
        }
        if (videos.isNotEmpty()) {
            val result = videoOptimiser.optimiseEntries(videos, prefs.videoQuality)
            progressed += result.optimised + result.skipped
            excluded += result.failedIds
        }

        val notYetTried = ready.size - photos.size - videos.size
        if (progressed > 0 && notYetTried > 0 && excluded.size <= MAX_EXCLUDED) {
            Logger.i(TAG, "camera: $notYetTried left, queueing another batch")
            BackupScheduling.enqueueOptimise(
                WorkManager.getInstance(applicationContext),
                BackupScheduling.PHASE_CAMERA,
                Data.Builder()
                    .putString(BackupScheduling.KEY_OPTIMISE_ALBUM, album)
                    .putLong(BackupScheduling.KEY_OPTIMISE_BEFORE, before)
                    .putStringArray(BackupScheduling.KEY_OPTIMISE_EXCLUDED, excluded.toTypedArray())
                    .build()
            )
        }
        return Result.success()
    }

    private suspend fun handOffToVideo() {
        val preferences = settings.current()
        if (!WizardBulkOptimise.shouldContinueToVideo(
                setupComplete = preferences.hasCompletedSetup,
                choice = preferences.libraryChoice
            )
        ) return

        if (videoOptimiser.wizardCandidates().isEmpty()) {
            Logger.d(TAG, "photos done; no video eligible")
            return
        }

        val started = BackupScheduling.enqueueOptimiseIfAbsent(
            WorkManager.getInstance(applicationContext),
            BackupScheduling.PHASE_VIDEO
        )
        Logger.i(
            TAG,
            if (started) "photos done; video pass queued"
            else "photos done; video pass already queued"
        )
    }

    /**
     * The ongoing photo pass: Sync albums only, and only through folders the user granted, because a
     * worker cannot show Android's dialog. Photos outside them are left for the moment someone is in
     * the app to be asked, so they are not counted as work this chain still owes.
     *
     * Stops the moment the Settings switches say photos are no longer wanted, and **stops rather than
     * repeats** when a photo will not replace: the same file would be the first candidate of the next
     * batch, and a chain that retried it for ever would never end.
     */
    private suspend fun runSyncPhotos(): Int {
        val prefs = settings.current()
        if (!PhotoOptimisePolicy.mayContinue(prefs.hasCompletedSetup, prefs.isOptimiseEnabled, prefs.optimisePhotos)) {
            Logger.d(TAG, "sync photos: switched off, stopping")
            return 0
        }

        val inside = proxyApplier.splitByConsent(proxyApplier.candidates()).inside
        if (inside.isEmpty()) {
            Logger.d(TAG, "sync photos: nothing eligible inside the granted folders")
            return 0
        }

        val batch = inside.take(PHOTO_BATCH)
        Logger.i(TAG, "sync photos: proxying ${batch.size} of ${inside.size}")
        return when (val outcome = proxyApplier.apply(batch)) {
            is ProxyOutcome.Completed -> (inside.size - batch.size).coerceAtLeast(0)
            is ProxyOutcome.Stopped -> {
                Logger.w(TAG, "sync photos: stopped at ${outcome.failedFile} (${outcome.reason})")
                0
            }
            ProxyOutcome.NothingToDo, ProxyOutcome.NotSupported -> 0
        }
    }

    private suspend fun runPhotos(): Int {
        val candidates = proxyApplier.candidatesAll()
        if (candidates.isEmpty()) {
            Logger.d(TAG, "photos: nothing eligible")
            return 0
        }

        if (proxyApplier.needsWriteRequest(candidates)) {
            Logger.i(
                TAG,
                "photos: ${candidates.size} sit outside the granted trees and need a tap; " +
                    "left for the wizard"
            )
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

        if (proxyApplier.needsWriteRequest(candidates)) {
            Logger.i(
                TAG,
                "video: ${candidates.size} sit outside the granted trees and need a tap; " +
                    "left for the wizard"
            )
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

        /** Failures a Camera pass carries forward before it gives up rather than grow its input without bound. */
        const val MAX_EXCLUDED = 100
    }
}
