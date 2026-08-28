package com.gallery.sync.domain.backup

import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import com.gallery.sync.data.local.dao.BackupEntryDao
import com.gallery.sync.data.local.entity.AlbumMode
import com.gallery.sync.data.local.entity.BackupEntryEntity
import com.gallery.sync.data.local.entity.backupKeyOf
import com.gallery.sync.data.local.media.MediaStoreWriter
import com.gallery.sync.data.local.media.WriteOutcome
import com.gallery.sync.di.IoDispatcher
import com.gallery.sync.domain.model.DataResult
import com.gallery.sync.domain.model.RemoteError
import com.gallery.sync.domain.repository.OneDriveRepository
import com.gallery.sync.util.Logger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Brings back a file the phone no longer has, into the album it came from.
 *
 * The other half of the Restore tab. Where [RestoreProxyInPlace] replaces a shrunken file, this one
 * has nothing to replace — the local copy is gone, most often because an Archive album did its job.
 *
 * ### It lands under its own name
 *
 * No `DCIM/Restored`, and no `_restored` suffix. That suffix exists only to tell a fetched copy apart
 * from a file already present, and by definition this one is not present. Ian, 27 Aug 2026: a
 * download that puts the album back the way it was should be indistinguishable from never having
 * lost the file. `RestoredAlbum` is deliberately not used here.
 *
 * ### Nothing can be overwritten
 *
 * `MediaStoreWriter` creates a new row and MediaStore renames rather than overwrites when a name is
 * taken — a second copy would arrive as `photo (1).jpg` rather than destroying anything. So unlike
 * the in-place path, this one cannot cost the user a file even if the classification was wrong.
 *
 * ### The ledger row is reused, never re-created
 *
 * The row already exists: this device uploaded the file and later noticed it gone. Moving it onto
 * the new local identity keeps `remoteItemId` and stops the next scan treating the arrival as a
 * brand-new file to upload — the same reasoning as the in-place path, through the same
 * `markRestored`, because it is the same operation: *this row's local file is now this file on disk.*
 */
@Singleton
class DownloadMissingFile @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val repository: OneDriveRepository,
    private val writer: MediaStoreWriter,
    private val entryDao: BackupEntryDao,
    @param:IoDispatcher private val dispatcher: CoroutineDispatcher
) {

    suspend fun download(
        entry: BackupEntryEntity,
        onProgress: (bytesWritten: Long, total: Long) -> Unit = { _, _ -> }
    ): RestoreInPlaceResult = withContext(dispatcher) {
        val remoteItemId = entry.remoteItemId
            ?: return@withContext RestoreInPlaceResult.Failed("no cloud item recorded")
        val expected = entry.remoteSizeBytes
            ?: return@withContext RestoreInPlaceResult.Failed("no cloud size recorded")

        val stream = when (val opened = repository.openStream(remoteItemId)) {
            is DataResult.Success -> opened.value
            is DataResult.Failure -> {
                val gone = (opened.error as? RemoteError.Http)?.code == HTTP_NOT_FOUND
                Logger.w(TAG, "could not open ${entry.displayName}: ${opened.error}")
                return@withContext if (gone) {
                    RestoreInPlaceResult.GoneFromCloud
                } else {
                    RestoreInPlaceResult.Failed("could not reach OneDrive")
                }
            }
        }

        // Straight to the album it came from. `expectedBytes` makes the writer reject a short read
        // and discard the half-written row rather than publishing a truncated photo.
        val outcome = writer.write(
            displayName = entry.displayName,
            mimeType = entry.mimeType,
            relativePath = relativePathFor(entry.album),
            isVideo = entry.isVideo,
            expectedBytes = expected,
            onProgress = { written -> onProgress(written, expected) },
            source = { stream }
        )

        when (outcome) {
            is WriteOutcome.Success -> {
                adopt(entry, outcome.uri, expected)
                Logger.i(TAG, "downloaded ${entry.displayName} into ${entry.album}")
                RestoreInPlaceResult.Restored(outcome.bytesWritten)
            }

            is WriteOutcome.Unsupported ->
                RestoreInPlaceResult.Failed("needs Android 10 or newer")

            is WriteOutcome.Failed -> RestoreInPlaceResult.Failed(outcome.reason)
        }
    }

    /**
     * Points the existing row at the file that has just arrived.
     *
     * Read back from MediaStore rather than assumed: the writer created the row, so this is the one
     * place the new `_ID` and modification time can be learned, and both go into the key the next
     * scan will compute.
     */
    private suspend fun adopt(entry: BackupEntryEntity, uri: Uri, expected: Long) {
        val indexed = runCatching {
            context.contentResolver.query(
                uri,
                arrayOf(
                    MediaStore.MediaColumns._ID,
                    MediaStore.MediaColumns.DATE_MODIFIED
                ),
                null,
                null,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getLong(0) to cursor.getLong(1) else null
            }
        }.getOrNull()

        val mediaStoreId = indexed?.first ?: entry.mediaStoreId
        val modified = indexed?.second ?: entry.dateModifiedEpochSeconds

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
            // A downloaded file is pinned out of Sync for the same reason a restored one is: the
            // user asked for it at full size, and optimising it again the same evening would be the
            // app undoing what it was just told to do.
            modeOverride = AlbumMode.BACKUP
        )
    }

    /** MediaStore album names are bucket names; a write needs the relative path that produces one. */
    private fun relativePathFor(album: String): String = "DCIM/$album/"

    private companion object {
        const val TAG = "DownloadMissing"
        const val HTTP_NOT_FOUND = 404
    }
}
