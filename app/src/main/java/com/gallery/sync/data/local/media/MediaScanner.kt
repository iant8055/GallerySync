package com.gallery.sync.data.local.media

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import com.gallery.sync.di.IoDispatcher
import com.gallery.sync.domain.backup.TreeScope
import com.gallery.sync.util.Logger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Enumerates the photos and videos on the device, grouped by album.
 *
 * Reads exclusively through MediaStore content URIs. Nothing here builds a `java.io.File` from the
 * `DATA` column or walks the filesystem: direct path access to media is restricted under scoped
 * storage and differs across API 26 to 37, while the content URI is readable on every version.
 *
 * Reports only. Deciding what still needs uploading, and uploading it, belong elsewhere.
 */
@Singleton
class MediaScanner @Inject constructor(
    @ApplicationContext private val context: Context,
    private val scopedDirectories: ScopedDirectories,
    @param:IoDispatcher private val dispatcher: CoroutineDispatcher
) {

    private val resolver: ContentResolver get() = context.contentResolver

    /** How much of the user's media this app may currently read. */
    fun access(): MediaAccess = MediaScanRules.resolveAccess(Build.VERSION.SDK_INT) { permission ->
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Every readable photo and video inside the granted folders, newest first.
     *
     * **Scoped.** Before Gate 1 has been answered nothing is granted and this returns nothing, which
     * is deliberate: the engine must have nothing to do until the user has said where to look. See
     * [TreeScope].
     */
    suspend fun scanAll(): List<LocalMediaItem> = withContext(dispatcher) {
        val granted = scopedDirectories.currentScope()
        if (granted.isEmpty()) {
            Logger.d(TAG, "scanAll: no folders granted yet, returning nothing")
            return@withContext emptyList()
        }

        scanEverything()
            .filter { TreeScope.isInScope(it.relativePath, granted) }
            // Files fetched back from the cloud are already backed up, but they land in an album
            // whose remote folder does not exist — so the skip-existing check would find nothing
            // there and upload every one of them again. Excluding the album is what prevents a
            // restore from costing a second copy. See [RestoredAlbum].
            .filterNot { RestoredAlbum.isRestored(it.relativePath, it.album) }
            .also {
                Logger.d(
                    TAG,
                    "scanAll: ${it.size} items across ${it.distinctBy { i -> i.album }.size} albums " +
                        "within ${granted.size} granted folders"
                )
            }
    }

    /**
     * Every readable photo and video on the device, ignoring the granted folders entirely.
     *
     * **Only for deciding what still physically exists.** `BackupEngine` prunes ledger rows for
     * albums the device no longer has, and driving that from a scoped scan would delete the record
     * of every album the user merely narrowed away — losing their modes and their backup history for
     * a folder that is still sitting on the phone. Narrowing hides; it must never forget.
     *
     * Not for offering anything to the user: the whole point of Gate 1 is that ninety albums of app
     * caches and thumbnails are not someone's photo library.
     */
    suspend fun scanEverything(): List<LocalMediaItem> = withContext(dispatcher) {
        if (access() == MediaAccess.NONE) {
            Logger.w(TAG, "scan: no media permission, returning nothing")
            return@withContext emptyList()
        }

        val items = query(imagesCollection(), isVideo = false) + query(videosCollection(), isVideo = true)

        // Newest first, so that a run cut short has already protected the most recent photos.
        items.sortedByDescending { it.dateModifiedEpochSeconds }
    }

    /** Albums with their item count and total size, for the backup selection UI. */
    suspend fun scanAlbums(): List<MediaAlbum> = withContext(dispatcher) {
        scanAll()
            .groupBy { it.album }
            .map { (name, items) ->
                MediaAlbum(
                    name = name,
                    itemCount = items.size,
                    totalBytes = items.sumOf { it.sizeBytes },
                    imageCount = items.count { !it.isVideo },
                    videoCount = items.count { it.isVideo }
                )
            }
            .sortedBy { it.name.lowercase() }
    }

    /** Everything in one album, newest first. */
    suspend fun scanAlbum(album: String): List<LocalMediaItem> = withContext(dispatcher) {
        scanAll().filter { it.album == album }
    }

    private fun query(collection: Uri, isVideo: Boolean): List<LocalMediaItem> {
        val projection = buildList {
            add(MediaStore.MediaColumns._ID)
            add(MediaStore.MediaColumns.DISPLAY_NAME)
            add(MediaStore.MediaColumns.SIZE)
            add(MediaStore.MediaColumns.DATE_MODIFIED)
            add(MediaStore.MediaColumns.MIME_TYPE)
            add(MediaStore.MediaColumns.BUCKET_DISPLAY_NAME)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                add(MediaStore.MediaColumns.RELATIVE_PATH)
                add(MediaStore.MediaColumns.IS_PENDING)
            }
        }.toTypedArray()

        return runCatching {
            resolver.query(
                collection,
                projection,
                null,
                null,
                "${MediaStore.MediaColumns.DATE_MODIFIED} DESC"
            )?.use { cursor -> readAll(cursor, collection, isVideo) }.orEmpty()
        }.onFailure {
            // A revoked permission mid-scan, or an OEM MediaStore quirk, must not crash a
            // background backup run.
            Logger.e(TAG, "query failed for $collection: ${it.javaClass.simpleName}")
        }.getOrDefault(emptyList())
    }

    private fun readAll(cursor: Cursor, collection: Uri, isVideo: Boolean): List<LocalMediaItem> {
        val idCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
        val nameCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
        val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
        val modifiedCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_MODIFIED)
        val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.MIME_TYPE)
        val bucketCol = cursor.getColumnIndex(MediaStore.MediaColumns.BUCKET_DISPLAY_NAME)
        val pathCol = cursor.getColumnIndex(MediaStore.MediaColumns.RELATIVE_PATH)
        val pendingCol = cursor.getColumnIndex(MediaStore.MediaColumns.IS_PENDING)

        val items = ArrayList<LocalMediaItem>(cursor.count)
        while (cursor.moveToNext()) {
            val size = cursor.getLong(sizeCol)
            val pending = pendingCol >= 0 && cursor.getInt(pendingCol) == 1
            if (!MediaScanRules.shouldInclude(size, pending)) continue

            val id = cursor.getLong(idCol)
            val relativePath = pathCol.takeIf { it >= 0 }?.let { cursor.getString(it) }
            items += LocalMediaItem(
                mediaStoreId = id,
                contentUri = ContentUris.withAppendedId(collection, id),
                displayName = cursor.getString(nameCol).orEmpty(),
                album = MediaScanRules.albumNameOf(
                    bucketDisplayName = bucketCol.takeIf { it >= 0 }?.let { cursor.getString(it) },
                    relativePath = relativePath
                ),
                sizeBytes = size,
                dateModifiedEpochSeconds = cursor.getLong(modifiedCol),
                mimeType = cursor.getString(mimeCol).orEmpty(),
                isVideo = isVideo,
                relativePath = relativePath
            )
        }
        return items
    }

    private fun imagesCollection(): Uri =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }

    private fun videosCollection(): Uri =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        }

    private companion object {
        const val TAG = "MediaScanner"
    }
}
