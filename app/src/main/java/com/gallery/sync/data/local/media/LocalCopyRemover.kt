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
 * recoverable. Observed on the *same handset* on 25 Aug 2026: a 461 MB video was renamed to
 * `.trashed-<expiry>-<name>`, intact, with a 30-day expiry, and was visible in Samsung Gallery's
 * Recycle Bin. Same device, same API, opposite outcomes.
 *
 * **Why is not known.** An earlier version of this comment said Samsung routes the request through
 * Gallery's Recycle Bin and that a user setting governs it. Ian checked on 25 Aug 2026: there is no
 * such setting. That explanation is withdrawn; the observations stand and the cause does not.
 *
 * So the guarantee this feature actually rests on is **not** the trash. It is that nothing is ever
 * eligible unless OneDrive has confirmed the file and reported a matching byte size. The cloud copy
 * is the safety net; the trash is a bonus that may or may not exist on a given run.
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
        if (uris.size > MAX_URIS_PER_REQUEST) {
            // Guarding rather than trusting the caller: exceeding the cap throws
            // IllegalArgumentException, and a crash at the moment of removal is the worst place in
            // the app to discover a batching mistake. Callers split with [batch].
            Logger.e(TAG, "trash request of ${uris.size} exceeds the $MAX_URIS_PER_REQUEST cap")
            return null
        }

        return runCatching {
            MediaStore.createTrashRequest(context.contentResolver, uris, true).intentSender
        }.onFailure {
            Logger.e(TAG, "could not build trash request: ${it.javaClass.simpleName}")
        }.getOrNull()
    }

    /**
     * Splits [uris] into requests Android will accept.
     *
     * Apps targeting Android 15+ may pass at most 2000 URIs to `createTrashRequest`, and the same
     * cap applies to the delete, write and favorite requests. It is a cap **per request**, not a
     * lifetime quota, so a large album is several dialogs rather than a refusal — and the screen
     * driving this has to make that read as one operation progressing, not as the app asking again
     * because something went wrong.
     */
    fun <T> batch(uris: List<T>): List<List<T>> = uris.chunked(MAX_URIS_PER_REQUEST)

    private companion object {
        const val TAG = "LocalCopyRemover"

        /** Android's cap for apps targeting 15+. Exceeding it throws. */
        const val MAX_URIS_PER_REQUEST = 2000
    }
}
