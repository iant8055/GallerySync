package com.gallery.sync.worker

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/**
 * Whether `POST_NOTIFICATIONS` is actually available right now.
 *
 * Checked fresh rather than trusted from a stored preference — Android can revoke it, or never
 * have granted it, independent of anything this app remembers about the Settings switch. Shared by
 * [ArchiveReadyNotifier], which posts the notification, and the Settings switch, which must not
 * read as on when nothing would actually arrive.
 */
object ArchiveNotifyPermission {
    const val MANIFEST_NAME = Manifest.permission.POST_NOTIFICATIONS

    /** Below API 33 the permission is granted at install time; there is no runtime prompt for it. */
    fun needsRuntimeRequest(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

    fun granted(context: Context): Boolean {
        if (!needsRuntimeRequest()) return true
        return ContextCompat.checkSelfPermission(context, MANIFEST_NAME) == PackageManager.PERMISSION_GRANTED
    }
}
