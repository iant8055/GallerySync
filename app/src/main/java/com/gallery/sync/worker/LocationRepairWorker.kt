package com.gallery.sync.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.gallery.sync.data.local.settings.BackupSettings
import com.gallery.sync.domain.backup.BackupEngine
import com.gallery.sync.util.Logger
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Runs the location repair (`LocationRepair`) in batches, after the user asked for it in Settings.
 *
 * Each run does one batch from where the last stopped (the cursor is stored), then queues the next, the way the
 * backup's continuations do, so a long repair survives the process being killed. A network failure retries the
 * same batch later. Wi-Fi only unless the user allowed mobile data, as for the backup.
 */
@HiltWorker
class LocationRepairWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val engine: BackupEngine,
    private val settings: BackupSettings
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val prefs = settings.current()
        if (!prefs.locationRepairRunning || prefs.locationFixedAt <= 0L) return Result.success()

        val batch = engine.repairLocations(before = prefs.locationFixedAt, afterId = prefs.locationRepairCursor)
        if (batch.stopped && batch.checked == 0) return Result.retry()

        settings.recordLocationRepair(
            cursor = batch.lastId,
            checked = batch.checked,
            repaired = batch.repaired,
            noLocation = batch.noLocation,
            finished = batch.done
        )
        if (batch.done) {
            val after = settings.current()
            Logger.i(TAG, "location repair finished: ${after.locationRepairRepaired} repaired of ${after.locationRepairChecked} checked")
            return Result.success()
        }
        enqueue(WorkManager.getInstance(applicationContext), prefs.allowMeteredNetwork, ExistingWorkPolicy.APPEND_OR_REPLACE)
        return Result.success()
    }

    companion object {
        private const val TAG = "LocationRepair"
        const val WORK = "location_repair"

        fun enqueue(workManager: WorkManager, allowMeteredNetwork: Boolean, policy: ExistingWorkPolicy = ExistingWorkPolicy.KEEP) {
            val request = OneTimeWorkRequestBuilder<LocationRepairWorker>()
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(if (allowMeteredNetwork) NetworkType.CONNECTED else NetworkType.UNMETERED)
                        .setRequiresBatteryNotLow(true)
                        .build()
                )
                .build()
            workManager.enqueueUniqueWork(WORK, policy, request)
        }
    }
}
