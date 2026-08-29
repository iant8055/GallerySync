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
 * ### The trash is reached, on every device tried so far
 *
 * **The "removed the files outright" observation that used to head this comment is withdrawn.** It
 * was almost certainly a conflation with `DocumentsContract.deleteDocument`, a permanent SAF delete
 * that genuinely does leave nothing behind and is forbidden by CLAUDE.md. Four runs since have all
 * behaved identically:
 *
 * - Fold 4, 25 Aug 2026 — one file, 461 MB, renamed and in Samsung Gallery's Recycle Bin
 * - Fold 4, 27 Aug 2026 — 51 files, album `Anne`, same, 30-day expiry
 * - Moto G, 28 Aug 2026 — 8 files, 1.07 GB, renamed in place, Ian found all eight in the Files
 *   app's Trash, expiry 31 days
 *
 * Two vendors and two skins, so this is the platform's behaviour rather than Samsung's. The file is
 * renamed to `.trashed-<expiry>-<name>`, keeps its bytes, and expires after about a month.
 *
 * **Keeping its bytes is the part the UI has to say out loud.** `du` was unchanged across a removal
 * on both handsets: archiving frees nothing at the moment it runs, and the space returns when the
 * user empties the bin or the expiry passes. This app never empties it for them.
 *
 * Two devices is still not every device, so the guarantee this feature *rests* on remains the cloud
 * copy: nothing is eligible unless OneDrive has confirmed the file and reported a matching byte
 * size. The trash is what the user recovers from; the cloud copy is what makes the removal safe.
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
