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
        withOneSpellingPerFolder(items).sortedByDescending { it.dateModifiedEpochSeconds }
    }

    /**
     * The MediaStore ids of the photos and videos in the phone's trash, whichever app put them there.
     *
     * Trashing keeps a file's id and its bytes, and an app holding the media permission can list
     * trashed items and read them (measured on the Moto G, 19 Sept 2026: ten photos another package
     * had trashed, every byte of every one readable). That is what makes it possible to back up a
     * file after it has been deleted.
     *
     * Empty below Android 11, where there is no trash, and without full media access, where the answer
     * would be partial. **Empty is also what a failed query returns**, which is the safe direction:
     * the caller offers less, never more.
     */
    suspend fun trashedIds(): Set<Long> = withContext(dispatcher) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R || access() != MediaAccess.FULL) {
            return@withContext emptySet()
        }
        (trashedIn(imagesCollection()) + trashedIn(videosCollection())).toSet()
    }

    private fun trashedIn(collection: Uri): List<Long> {
        val args = android.os.Bundle().apply {
            putInt(MediaStore.QUERY_ARG_MATCH_TRASHED, MediaStore.MATCH_ONLY)
        }
        return runCatching {
            resolver.query(collection, arrayOf(MediaStore.MediaColumns._ID), args, null)?.use { cursor ->
                buildList { while (cursor.moveToNext()) add(cursor.getLong(0)) }
            }.orEmpty()
        }.onFailure {
            Logger.e(TAG, "trash query failed for $collection: ${it.javaClass.simpleName}")
        }.getOrDefault(emptyList())
    }

    /**
     * Gives every item in a folder the same album name, however each writer spelled the folder.
     *
     * Done here, beneath every consumer, so nothing downstream can see a folder as two albums.
     * The stored state that already holds two spellings is merged separately, by
     * `AlbumIdentityReconciler`. See TASK-023.
     */
    private fun withOneSpellingPerFolder(items: List<LocalMediaItem>): List<LocalMediaItem> {
        val keyOf = { item: LocalMediaItem -> MediaScanRules.folderKeyOf(item.relativePath, item.album) }
        val canonical = MediaScanRules.canonicalAlbumNames(
            items.map { MediaScanRules.AlbumSighting(keyOf(it), it.album, it.mediaStoreId) }
        )
        return items.map { item ->
            val name = canonical[keyOf(item)] ?: item.album
            if (name == item.album) item else item.copy(album = name)
        }
    }

    /**
     * Discovers top-level media directories by scanning all of MediaStore (unscoped).
     *
     * Groups every photo and video by the first segment of its `RELATIVE_PATH` (e.g. "DCIM",
     * "Pictures", "Download"). Excludes hidden directories (starting with ".") and directories
     * with zero media files.
     *
     * This is NOT scoped to granted folders — it reads everything MediaStore returns, because
     * the point is to show the user what exists so they can choose what to grant.
     */
    suspend fun discoverDirectories(): List<DiscoveredDirectory> = withContext(dispatcher) {
        if (access() == MediaAccess.NONE) {
            Logger.w(TAG, "discoverDirectories: no media permission")
            return@withContext emptyList()
        }

        val allItems = scanEverything()

        allItems
            .mapNotNull { item -> MediaScanRules.topLevelFolderOf(item.relativePath)?.let { it to item } }
            .groupBy({ it.first }, { it.second })
            .map { (name, items) ->
                DiscoveredDirectory(
                    name = name,
                    albumCount = items.map { it.album }.distinct().size,
                    photoCount = items.count { !it.isVideo },
                    videoCount = items.count { it.isVideo },
                    totalBytes = items.sumOf { it.sizeBytes }
                )
            }
            .sortedByDescending { it.totalFiles }
            .also { Logger.d(TAG, "discoverDirectories: ${it.size} directories found") }
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
                    videoCount = items.count { it.isVideo },
                    // An album's files should all share one top-level folder — it is one physical
                    // directory — so the first item that resolves one speaks for the whole group.
                    // Not assumed to be perfectly uniform: a stray item with no RELATIVE_PATH beside
                    // others that have one is exactly the API < 29 case this falls back for.
                    topLevelFolder = items.firstNotNullOfOrNull { MediaScanRules.topLevelFolderOf(it.relativePath) },
                    inFolders = items
                        .mapNotNull { item -> MediaScanRules.topLevelFolderOf(item.relativePath)?.let { it to item } }
                        .groupBy({ it.first }, { it.second })
                        .mapValues { (_, inFolder) -> FolderShare(inFolder.size, inFolder.sumOf { it.sizeBytes }) }
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
