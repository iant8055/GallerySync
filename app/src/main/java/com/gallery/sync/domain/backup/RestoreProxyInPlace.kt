package com.gallery.sync.domain.backup

import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import com.gallery.sync.data.local.dao.BackupEntryDao
import com.gallery.sync.data.local.entity.AlbumMode
import com.gallery.sync.data.local.entity.BackupEntryEntity
import com.gallery.sync.data.local.entity.backupKeyOf
import com.gallery.sync.data.local.media.SafMediaWriter
import com.gallery.sync.domain.model.DataResult
import com.gallery.sync.domain.model.RemoteError
import com.gallery.sync.domain.repository.OneDriveRepository
import com.gallery.sync.di.IoDispatcher
import com.gallery.sync.util.Logger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** What happened when a proxy was replaced by its full-size original. */
sealed interface RestoreInPlaceResult {

    data class Restored(val bytesWritten: Long) : RestoreInPlaceResult

    /** The item is no longer in OneDrive, so there is nothing to restore from. Final, not a retry. */
    data object GoneFromCloud : RestoreInPlaceResult

    /**
     * No granted tree covers this file, so it cannot be rewritten without a dialog.
     *
     * Distinct from [Failed] because the fix is a folder grant rather than a retry, and the UI can
     * say so.
     */
    data object NotCovered : RestoreInPlaceResult

    data class Failed(val reason: String) : RestoreInPlaceResult
}

/**
 * Replaces a proxy on the phone with the full-size original from OneDrive.
 *
 * The operation the Restore tab exists for, and the one OneDrive itself cannot perform: only this
 * app knows which local files are proxies and which cloud item each was made from. See TASK-018.
 *
 * ### Download first, overwrite second
 *
 * The bytes land in a staging file and are checked against the size OneDrive reports before
 * anything on the phone is touched. Streaming straight onto the proxy would mean a dropped
 * connection costs the user both files — the small one gone and the large one never arrived — which
 * is the single outcome this must never produce. `MediaStoreWriter` states the principle for the
 * download path; here there is something to lose, so it applies with more force.
 *
 * A restore is a read from OneDrive and a write to the phone. **Nothing remote is touched**, which
 * is why a failure costs nothing and the same file can be tried again immediately.
 *
 * ### The ledger row moves with the file
 *
 * A row's identity is `backupKeyOf(album, name, size, mtime)` and a restore rewrites the mtime. Left
 * alone, the next scan computes a key it has never seen and inserts a second, PENDING row — so a
 * file already in OneDrive is uploaded again. Ian, 27 Aug 2026: *"a restored file should not trigger
 * a sync, only if the file is moved or saved."* Moving the row onto the new key is what tells those
 * two apart, because this class knows which one just happened and an mtime does not.
 *
 * The row is **updated**, never replaced, so `remoteItemId` survives — a delete-and-reinsert would
 * lose the pointer to the cloud original and make the file unrestorable ever after.
 */
@Singleton
class RestoreProxyInPlace @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val repository: OneDriveRepository,
    private val safWriter: SafMediaWriter,
    private val entryDao: BackupEntryDao,
    @param:IoDispatcher private val dispatcher: CoroutineDispatcher
) {

    suspend fun restore(
        entry: BackupEntryEntity,
        onProgress: (bytesWritten: Long, total: Long) -> Unit = { _, _ -> }
    ): RestoreInPlaceResult = withContext(dispatcher) {
        val remoteItemId = entry.remoteItemId
            ?: return@withContext RestoreInPlaceResult.Failed("no cloud item recorded")
        val expected = entry.remoteSizeBytes
            ?: return@withContext RestoreInPlaceResult.Failed("no cloud size recorded")
        val uri = Uri.parse(entry.contentUri)

        // Asked before a byte moves. A file outside every granted tree cannot be rewritten without
        // the dialog, and finding that out after a 40 MB download would be a waste of the user's
        // data as well as their time.
        if (!safWriter.covers(listOf(entry.album.asRelativePathGuess()))) {
            Logger.w(TAG, "${entry.displayName}: no granted tree covers it")
        }

        val staging = File(context.cacheDir, "restore-${entry.mediaStoreId}.tmp")
        try {
            when (val downloaded = downloadTo(staging, remoteItemId, expected, onProgress)) {
                is Download.Failed -> return@withContext downloaded.result
                Download.Ok -> Unit
            }

            // The proxy is still untouched at this point, and stays untouched unless the bytes are
            // all here and the count matches. This is the line the whole design rests on.
            if (staging.length() != expected) {
                Logger.e(
                    TAG,
                    "${entry.displayName}: short download, ${staging.length()} of $expected"
                )
                return@withContext RestoreInPlaceResult.Failed(
                    "incomplete download: ${staging.length()} of $expected bytes"
                )
            }

            val wrote = safWriter.writeTruncating(uri) { out ->
                staging.inputStream().use { it.copyTo(out) }
            }
            if (!wrote) {
                Logger.w(TAG, "${entry.displayName}: tree write refused")
                return@withContext RestoreInPlaceResult.NotCovered
            }

            adoptRestoredFile(entry, uri, expected)
            Logger.i(TAG, "restored ${entry.displayName} to $expected bytes")
            RestoreInPlaceResult.Restored(expected)
        } catch (error: Throwable) {
            // Cancellation included: the proxy has not been touched unless the write already
            // completed, and the staging file goes either way in the finally below.
            Logger.w(TAG, "${entry.displayName}: ${error.javaClass.simpleName}")
            throw error
        } finally {
            staging.delete()
        }
    }

    private sealed interface Download {
        data object Ok : Download
        data class Failed(val result: RestoreInPlaceResult) : Download
    }

    private suspend fun downloadTo(
        staging: File,
        remoteItemId: String,
        expected: Long,
        onProgress: (Long, Long) -> Unit
    ): Download {
        val stream = when (val opened = repository.openStream(remoteItemId)) {
            is DataResult.Success -> opened.value
            is DataResult.Failure -> {
                // A 404 for an item id is final: the file has been removed from the drive. Anything
                // else — no token, a dropped connection — is worth trying again later.
                val gone = (opened.error as? RemoteError.Http)?.code == HTTP_NOT_FOUND
                return Download.Failed(
                    if (gone) {
                        RestoreInPlaceResult.GoneFromCloud
                    } else {
                        RestoreInPlaceResult.Failed("could not reach OneDrive")
                    }
                )
            }
        }

        staging.outputStream().use { out ->
            stream.use { input ->
                val buffer = ByteArray(BUFFER_BYTES)
                var total = 0L
                while (true) {
                    // Stop means stop, mid-file. Plain blocking IO with no suspension point of its
                    // own, so without this a cancelled restore keeps pulling bytes to the end of the
                    // current file — minutes, on a large one.
                    currentCoroutineContext().ensureActive()
                    val read = input.read(buffer)
                    if (read <= 0) break
                    out.write(buffer, 0, read)
                    total += read
                    onProgress(total, expected)
                }
            }
        }
        return Download.Ok
    }

    /**
     * Points the ledger row at the file that now exists, so the next scan recognises it.
     *
     * Waits for MediaStore to catch up, unlike the proxy path, which deliberately does not. The
     * trade is different in each direction: proxying runs unattended over hundreds of files where a
     * momentarily stale index costs a thumbnail, while a restore is one file the user is watching
     * and a wrong key here costs an entire re-upload. A second or two is affordable to be right.
     *
     * If the index never settles the row is still moved, using what was read. The worst outcome is a
     * redundant upload of a file already in the cloud — wasteful, not harmful, and self-correcting on
     * the next scan.
     */
    private suspend fun adoptRestoredFile(entry: BackupEntryEntity, uri: Uri, expected: Long) {
        var observed = readIndexed(uri)
        var waited = 0L
        while (observed != null && observed.sizeBytes != expected && waited < SETTLE_TIMEOUT_MS) {
            delay(SETTLE_STEP_MS)
            waited += SETTLE_STEP_MS
            observed = readIndexed(uri)
        }

        if (observed == null || observed.sizeBytes != expected) {
            Logger.w(
                TAG,
                "${entry.displayName}: MediaStore still reports ${observed?.sizeBytes} after ${waited}ms"
            )
        }

        val mediaStoreId = observed?.mediaStoreId ?: entry.mediaStoreId
        val modified = observed?.dateModifiedEpochSeconds ?: entry.dateModifiedEpochSeconds

        entryDao.markRestored(
            oldId = entry.id,
            newId = backupKeyOf(
                album = entry.album,
                displayName = entry.displayName,
                sizeBytes = expected,
                dateModifiedEpochSeconds = modified
            ),
            dateModifiedEpochSeconds = modified,
            sizeBytes = expected,
            mediaStoreId = mediaStoreId,
            contentUri = uri.toString(),
            modeOverride = AlbumMode.BACKUP
        )
    }

    private fun readIndexed(uri: Uri): Indexed? = runCatching {
        context.contentResolver.query(
            uri,
            arrayOf(
                MediaStore.MediaColumns._ID,
                MediaStore.MediaColumns.SIZE,
                MediaStore.MediaColumns.DATE_MODIFIED
            ),
            null,
            null,
            null
        )?.use { cursor ->
            if (!cursor.moveToFirst()) return@use null
            Indexed(
                mediaStoreId = cursor.getLong(0),
                sizeBytes = cursor.getLong(1),
                dateModifiedEpochSeconds = cursor.getLong(2)
            )
        }
    }.getOrNull()

    private data class Indexed(
        val mediaStoreId: Long,
        val sizeBytes: Long,
        val dateModifiedEpochSeconds: Long
    )

    /** MediaStore album names are bucket names; the tree grant is checked against a relative path. */
    private fun String.asRelativePathGuess(): String = "DCIM/$this/"

    private companion object {
        const val TAG = "RestoreInPlace"
        const val BUFFER_BYTES = 64 * 1024
        const val HTTP_NOT_FOUND = 404

        const val SETTLE_STEP_MS = 250L
        const val SETTLE_TIMEOUT_MS = 3_000L
    }
}
