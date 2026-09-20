package com.gallery.sync.worker

import android.content.Context
import androidx.work.WorkManager
import com.gallery.sync.data.local.media.ProxyApplier
import com.gallery.sync.data.local.settings.BackupSettings
import com.gallery.sync.domain.backup.PhotoOptimisePolicy
import com.gallery.sync.util.Logger
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Starts the ongoing photo optimise pass when the Settings say it should run. Ian, 19 Sept 2026.
 *
 * **Automatic** is *"as soon as a file hits an Album whose mode is SYNC or an Album mode is switched to
 * SYNC"*. So it is asked at the two moments that make a photo newly eligible: the end of a backup run
 * (a new file has just been sent and verified, which is the earliest it may be touched) and the album
 * being set to Sync, and again whenever a Settings switch changes.
 * **Manual** is *"through the Sync Now button on the Albums tab"*.
 *
 * The chain itself is [OptimiseWorker]'s `PHASE_SYNC_PHOTOS`. It works only inside folders the user
 * granted at setup, where no dialog is needed; anything outside them waits for the app to be open.
 *
 * It is a convenience that must never fail the thing that asked, so callers guard it.
 */
@Singleton
class PhotoOptimiseLauncher @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val settings: BackupSettings,
    private val proxyApplier: ProxyApplier
) {

    /** Starts the pass if photos should be optimising on their own and there is something to do. */
    suspend fun requestAutomatic(): Boolean {
        val prefs = settings.current()
        if (!PhotoOptimisePolicy.runsAutomatically(
                setupComplete = prefs.hasCompletedSetup,
                optimiseEnabled = prefs.isOptimiseEnabled,
                optimisePhotos = prefs.optimisePhotos,
                mode = prefs.photoOptimiseMode
            )
        ) return false
        return startIfThereIsWork()
    }

    /** Starts the pass because **Sync now** was pressed and photos are set to Manual. */
    suspend fun requestOnSyncNow(): Boolean {
        val prefs = settings.current()
        if (!PhotoOptimisePolicy.runsOnSyncNow(
                setupComplete = prefs.hasCompletedSetup,
                optimiseEnabled = prefs.isOptimiseEnabled,
                optimisePhotos = prefs.optimisePhotos,
                mode = prefs.photoOptimiseMode
            )
        ) return false
        return startIfThereIsWork()
    }

    private suspend fun startIfThereIsWork(): Boolean {
        if (!proxyApplier.isSupported()) return false

        // Asked before queueing, so a run that finds nothing to do does not leave a job behind.
        if (proxyApplier.splitByConsent(proxyApplier.candidates()).inside.isEmpty()) return false

        val started = BackupScheduling.enqueueOptimiseIfAbsent(
            WorkManager.getInstance(context),
            BackupScheduling.PHASE_SYNC_PHOTOS
        )
        Logger.i(TAG, if (started) "photo optimising queued" else "photo optimising already queued")
        return started
    }

    private companion object {
        const val TAG = "PhotoOptimiseLauncher"
    }
}
