package com.gallery.sync.ui.common

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import com.gallery.sync.util.Logger

/**
 * Opens the OneDrive app, so the user can reach files this app deliberately does not browse.
 *
 * GallerySync shows no photo grid and no thumbnail browser — that is the design principle, and
 * OneDrive already does it properly. Handing the user over to it is a better answer than building
 * a worse version of an app they already have.
 *
 * Falls back to the Play listing when OneDrive is absent, and to the web when there is no store
 * either. Every step is optional: this is a convenience, and it must never crash the settings
 * screen because a phone is missing something.
 */
object OneDriveLauncher {

    /** Requires the matching `<queries>` entry in the manifest, or this always returns null on API 30+. */
    fun isInstalled(context: Context): Boolean =
        context.packageManager.getLaunchIntentForPackage(ONEDRIVE_PACKAGE) != null

    fun open(context: Context) {
        val launch = context.packageManager.getLaunchIntentForPackage(ONEDRIVE_PACKAGE)
        if (launch != null) {
            runCatching { context.startActivity(launch) }
                .onFailure { Logger.w(TAG, "OneDrive is installed but would not start: ${it.message}") }
            return
        }

        Logger.i(TAG, "OneDrive is not installed; offering the store instead")
        if (!openUri(context, "market://details?id=$ONEDRIVE_PACKAGE")) {
            openUri(context, "https://play.google.com/store/apps/details?id=$ONEDRIVE_PACKAGE")
        }
    }

    private fun openUri(context: Context, uri: String): Boolean = runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, uri.toUri()))
        true
    }.getOrElse { error ->
        if (error !is ActivityNotFoundException) Logger.w(TAG, "could not open $uri: ${error.message}")
        false
    }

    private const val TAG = "OneDriveLauncher"
    private const val ONEDRIVE_PACKAGE = "com.microsoft.skydrive"
}
