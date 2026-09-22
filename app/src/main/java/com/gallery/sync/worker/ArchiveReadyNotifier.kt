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
import com.gallery.sync.domain.backup.ArchiveReadyNotice
import com.gallery.sync.domain.backup.BackupEngine
import com.gallery.sync.util.Logger
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Tells the user, outside the app, when files in an Archive album have come of age.
 *
 * Ian, 22 Sept 2026, deliberately **in addition to** the Albums tab summons and the exit-warning
 * dialog — never in place of them. Those two need no permission and cannot be silently switched
 * off; this one can, at any time, outside the app's knowledge (the user denies it, or later turns
 * notifications off for the app in the phone's own Settings), which is exactly the failure mode
 * `ExitWarning`'s own notes describe. So this is opt-in, off by default, and its absence is never
 * the only way the user could have known.
 *
 * ### Where this is called from
 *
 * [checkAndNotify] is called at the end of every complete [BackupWorker] run, the same point that
 * queues the photo and video optimisers — reached by every content trigger and by the six-hourly
 * safety net, so a file that has just become ready is noticed without a clock of its own. It costs
 * nothing extra to ask: [BackupEngine.redundantLocalCopies] is the same local-only ledger read
 * `BackupViewModel.refreshCounts` already does for the exit-warning dialog's count, not a fresh
 * network call.
 *
 * ### Never allowed to fail the backup
 *
 * Every call site wraps this in `runCatching`, matching how the optimise requests beside it are
 * called — a failure to notify is a missed convenience, not a reason to report the run as failed.
 */
@Singleton
class ArchiveReadyNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
    private val engine: BackupEngine,
    private val settings: BackupSettings
) {

    suspend fun checkAndNotify() {
        val preferences = settings.current()
        val redundant = engine.redundantLocalCopies()
        val count = redundant.size

        if (preferences.archiveNotifyEnabled && ArchiveNotifyPermission.granted(context) &&
            ArchiveReadyNotice.shouldNotify(preferences.archiveReadyLastSeenCount, count)
        ) {
            post(count = count, albums = redundant.map { it.album }.distinct().sorted())
        }

        // Persisted whatever the outcome, so a later shrink (files archived, or opted out) resets
        // the baseline and a future regrowth above it notifies again. See ArchiveReadyNotice.
        settings.setArchiveReadyLastSeenCount(count)
    }

    private fun post(count: Int, albums: List<String>) {
        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MainActivity.EXTRA_OPEN_ARCHIVE, true)
        }
        val pendingIntent = PendingIntent.getActivity(
            context, REQUEST_CODE, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val body = context.resources.getQuantityString(
            R.plurals.notification_archive_ready_body, count, count
        ) + if (albums.size == 1) " " + context.getString(R.string.notification_archive_ready_album, albums[0]) else ""

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle(context.getString(R.string.notification_archive_ready_title))
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        // runCatching rather than a second permission check: the check above covers the ordinary
        // path, and this also catches the narrow race where the permission is revoked between it
        // and this call — notify() throws SecurityException in that case, on API 33+.
        val manager = context.getSystemService(NotificationManager::class.java)
        runCatching { manager.notify(NOTIFICATION_ID, notification) }
            .onFailure { Logger.w(TAG, "could not post the archive-ready notification: ${it.javaClass.simpleName}") }
    }

    companion object {
        private const val TAG = "ArchiveReadyNotifier"
        const val CHANNEL_ID = "archive_ready"
        private const val NOTIFICATION_ID = 1001
        private const val REQUEST_CODE = 1001

        /** Created once, at application start. Importance DEFAULT: informational, not urgent. */
        fun createChannel(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val channel = NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.notification_channel_archive_ready),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = context.getString(R.string.notification_channel_archive_ready_description)
            }
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }
}
