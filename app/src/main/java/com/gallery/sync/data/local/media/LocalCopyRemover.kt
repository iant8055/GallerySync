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
 * Uses [MediaStore.createTrashRequest], never a delete — this app never issues a permanent-delete
 * call.
 *
 * ### Recoverability is NOT guaranteed, and the UI must not claim it is
 *
 * Observed on a Galaxy Z Fold 4: the request removed the files outright rather than leaving them
 * recoverable. On Samsung the request is routed through Samsung Gallery's Recycle Bin, and when
 * that setting is off there is nothing to route into — the trash request becomes a delete. The
 * platform offers no way to detect this beforehand.
 *
 * So the guarantee this feature actually rests on is **not** the trash. It is that nothing is ever
 * eligible unless OneDrive has confirmed the file and reported a matching byte size. The cloud copy
 * is the safety net; the trash is a bonus that may or may not exist on a given device.
 *
 * Android shows its own confirmation listing the files, so the user always sees what is about to
 * happen and can refuse.
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
