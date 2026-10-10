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
import com.gallery.sync.data.local.settings.BackupSettings
import com.gallery.sync.domain.backup.CloudDeletionPolicy
import com.gallery.sync.domain.backup.SyncDeletionsToCloud
import com.gallery.sync.util.Logger
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Tells the user, outside the app, that a photo or video they deleted still has a copy in the Cloud. Ian, 10 Oct
 * 2026: *"can GallerySync see when a new image or video gets deleted … then ask what the user wants to do with the
 * backed up copy?"*
 *
 * Until this, the question waited for the next time the app was opened, which could be a week. The notification
 * only brings the user to the question. It decides nothing and removes nothing: tapping it opens the app, where
 * the Deleted Files window (`ui/deleted/DeletedFilesGate`) asks as it always has, nothing ticked, with deleting a
 * Cloud copy still needing a tick and a confirmation. Ignoring it, or swiping it away, leaves every copy alone.
 *
 * - Only under **Ask**. Under Leave nothing is looked for, as before.
 * - Only files with a Cloud copy are announced; the window still shows the trashed files without one when opened.
 * - Each file is announced once: [BackupSettings.setDeletionNotifiedUpTo] records the newest one announced, kept
 *   apart from the window's own "seen" mark so the window still shows them.
 * - Called at the end of every complete [BackupWorker] run, like [ArchiveReadyNotifier]. The run is woken by the
 *   media library changing, and a file moved to the trash is such a change. Never allowed to fail the run.
 */
@Singleton
class DeletedFilesNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
    private val deletions: SyncDeletionsToCloud,
    private val settings: BackupSettings
) {

    suspend fun checkAndNotify() {
        val prefs = settings.current()
        if (prefs.cloudDeletionPolicy != CloudDeletionPolicy.ASK) return
        if (!ArchiveNotifyPermission.granted(context)) return

        val since = maxOf(prefs.deletionPromptSeenUpToEpochMillis, prefs.deletionNotifiedUpToEpochMillis)
        val offer = deletions.offer(newerThan = since)
        val fresh = offer.inCloud.filter { it.departedAtEpochMillis > since }
        if (fresh.isNotEmpty()) {
            post(fresh.size)
            Logger.i(TAG, "${fresh.size} deleted files with a Cloud copy announced")
        }
        // Not moved when some files could not be placed: they are announced once the Cloud can be asked.
        if (offer.complete && offer.newestDeparture > since) settings.setDeletionNotifiedUpTo(offer.newestDeparture)
    }

    private fun post(count: Int) {
        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context, REQUEST_CODE, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val body = context.resources.getQuantityString(R.plurals.notification_deleted_body, count, count)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle(context.getString(R.string.notification_deleted_title))
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()
        val manager = context.getSystemService(NotificationManager::class.java)
        runCatching { manager.notify(NOTIFICATION_ID, notification) }
            .onFailure { Logger.w(TAG, "could not post the deleted-files notification: ${it.javaClass.simpleName}") }
    }

    companion object {
        private const val TAG = "DeletedFilesNotifier"
        const val CHANNEL_ID = "deleted_files"
        private const val NOTIFICATION_ID = 1002
        private const val REQUEST_CODE = 1002

        /** Created once, at application start. */
        fun createChannel(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val channel = NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.notification_channel_deleted),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply { description = context.getString(R.string.notification_channel_deleted_description) }
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }

        /** The window is up, so the notification has done its job. */
        fun cancel(context: Context) {
            runCatching { context.getSystemService(NotificationManager::class.java).cancel(NOTIFICATION_ID) }
        }
    }
}
