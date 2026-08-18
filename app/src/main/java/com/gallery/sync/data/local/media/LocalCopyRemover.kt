package com.gallery.sync.data.local.media

import android.content.Context
import android.content.IntentSender
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import com.gallery.sync.util.Logger
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Removes the phone's copy of files that are already safely in OneDrive.
 *
 * Uses [MediaStore.createTrashRequest], never a delete. Trashed media stays recoverable in the
 * user's gallery for 30 days, and only the user ever empties it — CLAUDE.md forbids this app from
 * permanently destroying anything.
 *
 * Two consequences that the UI has to be honest about:
 *  - Space is not reclaimed until the trash is emptied. Trashed files still occupy storage.
 *  - Android shows its own confirmation dialog listing the files, so the user always sees exactly
 *    what is about to happen and can refuse.
 */
@Singleton
class LocalCopyRemover @Inject constructor(
    @ApplicationContext private val context: Context
) {

    /**
     * Whether removing local copies can be offered at all.
     *
     * Below API 30 Android has no media trash, so the only way to remove a file is permanently.
     * The feature is withheld there rather than made destructive.
     */
    fun isSupported(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R

    /**
     * Builds the system request to move [uris] into the gallery's trash.
     *
     * Returns null when unsupported or when there is nothing to move. The caller launches the
     * returned sender, and Android — not this app — asks the user to confirm.
     */
    fun createMoveToBackupRequest(uris: List<Uri>): IntentSender? {
        if (!isSupported() || uris.isEmpty()) return null

        return runCatching {
            MediaStore.createTrashRequest(context.contentResolver, uris, true).intentSender
        }.onFailure {
            Logger.e(TAG, "could not build trash request: ${it.javaClass.simpleName}")
        }.getOrNull()
    }

    private companion object {
        const val TAG = "LocalCopyRemover"
    }
}
