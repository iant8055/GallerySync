package com.gallery.sync.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.gallery.sync.MainActivity
import com.gallery.sync.R
import com.gallery.sync.data.local.dao.FolderPreferenceDao
import com.gallery.sync.data.local.settings.BackupSettings
import com.gallery.sync.domain.billing.BillingRepository
import com.gallery.sync.domain.billing.MultiCloudEntitlement
import com.gallery.sync.domain.billing.MultiCloudTrial
import com.gallery.sync.util.Logger
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Tells the user, outside the app, that the free trial of extra Clouds is about to end and then that it has. Ian,
 * 15 Oct 2026, agreeing that without it backups to those Clouds stop quietly on day 31 and nobody notices.
 *
 * It only tells. Nothing is removed, paused or changed by it: the upload path already stops sending to extra Clouds
 * when the trial ends (`BackupEngine`, asked through [MultiCloudEntitlement]), and files already there stay there.
 *
 * - Only when Pro is not bought and a folder is actually paired with a Cloud other than the main one, so a user
 *   whom the end changes nothing for is not told about it.
 * - Once each: "ends soon" when [WARN_DAYS] or fewer are left, "ended" on the day. [BackupSettings.trialNoticeStage]
 *   records which has been posted.
 * - Called at the end of every complete [BackupWorker] run, like [DeletedFilesNotifier]; the six-hourly run means
 *   it is seen within hours of the day turning. Never allowed to fail the run.
 */
@Singleton
class TrialEndingNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
    private val entitlement: MultiCloudEntitlement,
    private val billing: BillingRepository,
    private val folders: FolderPreferenceDao,
    private val settings: BackupSettings
) {

    suspend fun checkAndNotify(now: Long = System.currentTimeMillis()) {
        val prefs = settings.current()
        if (prefs.trialNoticeStage >= STAGE_ENDED) return
        val trial = entitlement.trialState(now)
        if (trial == MultiCloudTrial.State.NotStarted) return
        if (billing.isPurchased()) return
        val main = prefs.backupLocation
        val pairedElsewhere = folders.all().any { it.backupLocation != main }
        val next = nextNotice(trial, prefs.trialNoticeStage, pairedElsewhere) ?: return
        if (!ArchiveNotifyPermission.granted(context)) return

        post(next, trial)
        settings.setTrialNoticeStage(next)
        Logger.i(TAG, "trial notice $next posted")
    }

    private fun post(stage: Int, trial: MultiCloudTrial.State) {
        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context, REQUEST_CODE, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val title = if (stage == STAGE_ENDED) {
            context.getString(R.string.notification_trial_ended_title)
        } else {
            val days = (trial as? MultiCloudTrial.State.Active)?.daysLeft ?: WARN_DAYS
            context.resources.getQuantityString(R.plurals.notification_trial_ending_title, days, days)
        }
        val body = context.getString(
            if (stage == STAGE_ENDED) R.string.notification_trial_ended_body else R.string.notification_trial_ending_body
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()
        val manager = context.getSystemService(NotificationManager::class.java)
        runCatching { manager.notify(NOTIFICATION_ID, notification) }
            .onFailure { Logger.w(TAG, "could not post the trial notice: ${it.javaClass.simpleName}") }
    }

    companion object {
        private const val TAG = "TrialEndingNotifier"
        const val CHANNEL_ID = "pro_trial"
        private const val NOTIFICATION_ID = 1003
        private const val REQUEST_CODE = 1003

        const val WARN_DAYS = 3
        const val STAGE_ENDING = 1
        const val STAGE_ENDED = 2

        /**
         * Which notice is due, or null. "Ended" may follow "ends soon" or come alone (a phone that was off through
         * the last days); "ends soon" is never posted after the trial has already ended.
         */
        fun nextNotice(trial: MultiCloudTrial.State, stage: Int, pairedElsewhere: Boolean): Int? {
            if (!pairedElsewhere) return null
            return when (trial) {
                MultiCloudTrial.State.Ended -> if (stage < STAGE_ENDED) STAGE_ENDED else null
                is MultiCloudTrial.State.Active ->
                    if (trial.daysLeft <= WARN_DAYS && stage < STAGE_ENDING) STAGE_ENDING else null
                MultiCloudTrial.State.NotStarted -> null
            }
        }

        /** Created once, at application start. */
        fun createChannel(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val channel = NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.notification_channel_trial),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply { description = context.getString(R.string.notification_channel_trial_description) }
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }
}
