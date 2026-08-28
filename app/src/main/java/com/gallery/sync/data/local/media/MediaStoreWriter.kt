package com.gallery.sync.data.local.media

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import com.gallery.sync.util.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton

/** What happened when a file was written back to the device. */
sealed interface WriteOutcome {

    data class Success(val uri: Uri, val bytesWritten: Long) : WriteOutcome

    /** Android below API 29 has no `RELATIVE_PATH` or `IS_PENDING`, so this route does not exist. */
    data object Unsupported : WriteOutcome

    data class Failed(val reason: String) : WriteOutcome
}

/**
 * Writes a new photo or video into MediaStore.
 *
 * ### The one direction Android does not fight
 *
 * Everything else this app does to media needs the user: `createWriteRequest` for a rewrite,
 * `createTrashRequest` for a removal, a SAF grant for anything it does not own. **Creating a new
 * file needs none of them** — an app owns what it inserts, so this runs unattended in a worker with
 * no dialog and no tap. That is what makes retrieval buildable at all, and it is worth stating
 * plainly because the rest of this codebase is shaped by the opposite constraint.
 *
 * ### IS_PENDING is what makes it safe
 *
 * A row is created as pending, the bytes are streamed in, and only then is it published. Without it
 * a half-written file is visible to every gallery app on the phone the moment the row appears — and
 * an interrupted download would leave a permanently truncated photo that looks like the real one.
 * On failure the pending row is deleted, which removes a file this app created and never a file the
 * user had.
 */
@Singleton
class MediaStoreWriter @Inject constructor(
    @param:ApplicationContext private val context: Context
) {

    fun isSupported(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q

    /**
     * Creates [displayName] under [relativePath] and fills it from [source].
     *
     * [expectedBytes] is checked against what actually arrived. A short read is a failure, not a
     * smaller file: this is the only copy being restored, and a truncated photo that looks whole is
     * worse than no photo at all.
     */
    suspend fun write(
        displayName: String,
        mimeType: String,
        relativePath: String,
        isVideo: Boolean,
        expectedBytes: Long,
        onProgress: (bytesWritten: Long) -> Unit = {},
        source: () -> InputStream
    ): WriteOutcome {
        if (!isSupported()) return WriteOutcome.Unsupported

        val resolver = context.contentResolver
        val collection = if (isVideo) {
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        }

        val pending = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
            put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
            // Invisible to every other app until the bytes are all here.
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }

        // MediaStore renames rather than overwrites when the name is taken, so restoring twice
        // produces "photo (1).jpg" instead of destroying the first copy.
        val uri = runCatching { resolver.insert(collection, pending) }.getOrNull()
            ?: return WriteOutcome.Failed("MediaStore refused to create the row")

        var total = 0L
        val written: Long? = try {
            resolver.openOutputStream(uri)?.use { out ->
                source().use { input ->
                    val buffer = ByteArray(BUFFER_BYTES)
                    while (true) {
                        // Stop means stop, mid-file. This loop is plain blocking IO with no
                        // suspension point in it, so without an explicit check a cancelled restore
                        // kept pulling bytes until the current file finished — which on a 2 GB clip
                        // is minutes after the user pressed the button. Ian asked for a Stop
                        // control on 27 Aug 2026; this is what makes it mean anything.
                        //
                        // **Verified on the Fold 4, 27 Aug 2026.** Two runs, both abandoned mid
                        // file: a 70,302,605-byte clip stopped at 36,644,457, and a 200,584,568-byte
                        // clip stopped at 47,656,084. The second is the one that proves it — a
                        // quarter of the way in, on a file that would otherwise have taken minutes
                        // more. Ian: no delay, stop was immediate. The backup run that followed each
                        // stop uploaded nothing, so neither partial survived to become a candidate.
                        currentCoroutineContext().ensureActive()
                        val read = input.read(buffer)
                        if (read <= 0) break
                        out.write(buffer, 0, read)
                        total += read
                        onProgress(total)
                    }
                }
                total
            }
        } catch (cancelled: CancellationException) {
            // A stop, not a failure, and the difference reaches the user: rethrowing means the
            // batch ends where it was rather than counting this file as one that could not be
            // restored. The half-written row goes either way — see [discard].
            Logger.i(TAG, "restore of $displayName stopped at $total bytes")
            discard(uri)
            throw cancelled
        } catch (error: Throwable) {
            Logger.e(TAG, "restore of $displayName failed: ${error.javaClass.simpleName}")
            null
        }

        if (written == null) {
            discard(uri)
            return WriteOutcome.Failed("could not write the file")
        }

        // Size equality is the proof, the same bar the upload path uses. "Some bytes arrived" is
        // also true of a dropped connection.
        if (expectedBytes > 0 && written != expectedBytes) {
            Logger.e(TAG, "restore of $displayName was short: $written of $expectedBytes bytes")
            discard(uri)
            return WriteOutcome.Failed("incomplete download: $written of $expectedBytes bytes")
        }

        val publish = ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }
        runCatching { resolver.update(uri, publish, null, null) }
            .onFailure {
                discard(uri)
                return WriteOutcome.Failed("could not publish the restored file")
            }

        Logger.i(TAG, "restored $displayName ($written bytes)")
        return WriteOutcome.Success(uri, written)
    }

    /**
     * Removes a pending row this app just created.
     *
     * Not a deletion in the sense CLAUDE.md governs: the file never became visible, never belonged
     * to the user, and holds no bytes they had before. Leaving it would litter the gallery with
     * invisible half-files that nothing can clean up.
     *
     * **Verified on the Fold 4, 27 Aug 2026.** Ian stopped two restores mid-transfer — 36 MB and
     * 47 MB already written — and found no partial file in the gallery afterwards. That is this
     * function working on the cancellation path, which until then had only ever been reasoned
     * about: the abandoned bytes are reachable by nothing, so they have to be cleaned up here or
     * not at all.
     */
    private fun discard(uri: Uri) {
        runCatching { context.contentResolver.delete(uri, null, null) }
            .onFailure { Logger.w(TAG, "could not discard an incomplete restore") }
    }

    private companion object {
        const val TAG = "MediaStoreWriter"
        const val BUFFER_BYTES = 64 * 1024
    }
}
