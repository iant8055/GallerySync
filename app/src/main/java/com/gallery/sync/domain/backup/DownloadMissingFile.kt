package com.gallery.sync.domain.backup

import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import com.gallery.sync.data.local.dao.BackupEntryDao
import com.gallery.sync.data.local.entity.AlbumMode
import com.gallery.sync.data.local.entity.BackupEntryEntity
import com.gallery.sync.data.local.entity.backupKeyOf
import com.gallery.sync.data.local.media.MediaStoreWriter
import com.gallery.sync.data.local.media.RestoreFolder
import com.gallery.sync.data.local.media.SafMediaWriter
import com.gallery.sync.data.local.media.WriteOutcome
import com.gallery.sync.di.IoDispatcher
import com.gallery.sync.domain.model.DataResult
import com.gallery.sync.domain.model.RemoteError
import com.gallery.sync.domain.repository.CloudDownloaders
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
    private val downloaders: CloudDownloaders,
    private val writer: MediaStoreWriter,
    private val safWriter: SafMediaWriter,
    private val entryDao: BackupEntryDao,
    @param:IoDispatcher private val dispatcher: CoroutineDispatcher
) {

    suspend fun download(
        entry: BackupEntryEntity,
        onProgress: (bytesWritten: Long, total: Long) -> Unit = { _, _ -> }
    ): RestoreInPlaceResult = withContext(dispatcher) {
        val remoteItemId = entry.remoteItemId?.takeIf { it.isNotBlank() }
            ?: return@withContext RestoreInPlaceResult.Failed(
                "OneDrive has not been checked for this file yet. Press Refresh and try again."
            )

        // OneDrive's copy was proved by size, so that is what the download is checked against. Another
        // cloud's was not (it is recorded unverified), so the download is checked against the size the phone
        // had when it sent the file: the writer still rejects a short read and discards the half-written row.
        val viaOtherCloud = entry.location != BackupLocation.ONEDRIVE
        val expected = if (viaOtherCloud) {
            entry.sizeBytes
        } else {
            entry.remoteSizeBytes
                ?: return@withContext RestoreInPlaceResult.Failed("no Cloud size recorded")
        }

        val opened = if (viaOtherCloud) {
            val downloader = downloaders.of(entry.location)
                ?: return@withContext RestoreInPlaceResult.Failed("restoring from that Cloud is not available yet")
            downloader.openStream(remoteItemId)
        } else {
            repository.openStream(remoteItemId)
        }
        val stream = when (opened) {
            is DataResult.Success -> opened.value
            is DataResult.Failure -> {
                val gone = (opened.error as? RemoteError.Http)?.code == HTTP_NOT_FOUND
                Logger.w(TAG, "could not open ${entry.displayName}: ${opened.error}")
                return@withContext when {
                    gone -> RestoreInPlaceResult.GoneFromCloud
                    viaOtherCloud && opened.error == RemoteError.Unauthorized ->
                        RestoreInPlaceResult.Failed("reconnect that Cloud in Settings, and make sure its sign-in or keys allow reading")
                    viaOtherCloud -> RestoreInPlaceResult.Failed("could not reach the cloud")
                    else -> RestoreInPlaceResult.Failed("could not reach OneDrive")
                }
            }
        }

        // Straight to the album it came from. `expectedBytes` makes the writer reject a short read
        // and discard the half-written row rather than publishing a truncated photo.
        //
        // **Into the folder it left, when Android would refuse it there (Ian, 25 Sept 2026).** MediaStore only
        // creates photos under DCIM or Pictures, so an album at the top of the storage used to come back into
        // DCIM/<album>: a second folder, and one routed to whatever cloud DCIM goes to. The user's own folder
        // grant can write there instead, with no dialog, so it is tried first for exactly those albums. The
        // MediaStore route below is unchanged for everything else, and is the fallback when the grant cannot
        // be used before anything has been read.
        val treeFolder = runCatching { safWriter.restoreFolderFor(entry.album, entry.isVideo) }.getOrNull()
        val viaTree = treeFolder?.let { folder ->
            safWriter.createFile(
                folderRelativePath = folder,
                displayName = entry.displayName,
                mimeType = entry.mimeType,
                expectedBytes = expected,
                onProgress = { written -> onProgress(written, expected) },
                source = { stream }
            )
        }
        val outcome = if (viaTree != null && viaTree !is WriteOutcome.Unsupported) {
            viaTree
        } else {
            writer.write(
                displayName = entry.displayName,
                mimeType = entry.mimeType,
                relativePath = if (viaOtherCloud) {
                    deviceRelativePathOf(entry.album, entry.isVideo) ?: relativePathFor(entry.album)
                } else {
                    relativePathFor(entry.album)
                },
                isVideo = entry.isVideo,
                expectedBytes = expected,
                onProgress = { written -> onProgress(written, expected) },
                source = { stream }
            )
        }

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

        // No row means OneDrive held a file this app never recorded (Ian, 18 Sept 2026: Restore offers
        // any file the drive has). The file is really here now, so this is the moment to write its
        // row. Doing it earlier would have put a "missing" file in the ledger, which is the shape the
        // cloud-deletion review looks for. It is written already pinned, for the same reason as
        // below, and as uploaded, so the next scan finds it known rather than new and does not send
        // it back to the drive it has just come from.
        if (entryDao.find(entry.id) == null) {
            entryDao.insertIfNew(
                listOf(
                    entry.copy(
                        id = backupKeyOf(
                            album = entry.album,
                            displayName = entry.displayName,
                            sizeBytes = expected,
                            dateModifiedEpochSeconds = modified
                        ),
                        mediaStoreId = mediaStoreId,
                        contentUri = uri.toString(),
                        dateModifiedEpochSeconds = modified,
                        sizeBytes = expected,
                        remoteSizeBytes = expected,
                        isProxied = false,
                        localProxySizeBytes = null,
                        localMissingSinceEpochMillis = null,
                        modeOverride = AlbumMode.BACKUP
                    )
                )
            )
            return
        }

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

    /**
     * Where the album really lives on this phone, from any file MediaStore still holds in a folder of that
     * name (`Movies/blaze-test/`). Only the other clouds use it: their albums are not all under DCIM, and the
     * ledger records no top-level folder to say otherwise.
     *
     * Only a folder Android lets an app create media in: photos under `DCIM` or `Pictures`, videos under `DCIM`
     * or `Movies`. A folder made by hand at the top of the storage (`/storage/emulated/0/dropbox/`) is not one, and
     * MediaStore would refuse the write, so it is treated like a missing folder. Null then, which leaves the caller
     * on [relativePathFor].
     */
    private fun deviceRelativePathOf(album: String, isVideo: Boolean): String? = runCatching {
        context.contentResolver.query(
            MediaStore.Files.getContentUri("external"),
            arrayOf(MediaStore.MediaColumns.RELATIVE_PATH),
            "${MediaStore.MediaColumns.BUCKET_DISPLAY_NAME} = ?",
            arrayOf(album),
            null
        )?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
    }.getOrNull()?.takeIf { isWritableRoot(it, isVideo) }

    private fun isWritableRoot(relativePath: String, isVideo: Boolean): Boolean =
        RestoreFolder.isWritableRoot(relativePath, isVideo)

    private companion object {
        const val TAG = "DownloadMissing"
        const val HTTP_NOT_FOUND = 404
    }
}
