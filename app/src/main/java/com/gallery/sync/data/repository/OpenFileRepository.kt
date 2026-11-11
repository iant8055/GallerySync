package com.gallery.sync.data.repository

import android.content.ContentUris
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.StatFs
import android.provider.MediaStore
import androidx.core.content.FileProvider
import com.gallery.sync.data.local.entity.BackupEntryEntity
import com.gallery.sync.di.IoDispatcher
import com.gallery.sync.domain.backup.BackupLocation
import com.gallery.sync.domain.model.DataResult
import com.gallery.sync.domain.model.RemoteError
import com.gallery.sync.domain.open.NetworkKind
import com.gallery.sync.domain.repository.CloudDownloaders
import com.gallery.sync.domain.repository.CloudFileLinks
import com.gallery.sync.domain.repository.OneDriveRepository
import com.gallery.sync.util.Logger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.coroutineContext

/**
 * Everything a long-press needs that is not a decision (TASK-028): is the file on the phone, what link its Cloud
 * gives, and a temporary copy when the user would rather download. The decision itself is `OpenFilePlan`.
 *
 * ### The temporary copy is a cache, not a file of the user's
 *
 * It goes to `cacheDir/open/` and nowhere else, reaches the other app through this app's own `FileProvider`, and is
 * never written to MediaStore, a gallery or an album. It is cleared before the next one and when the app starts
 * (CLAUDE.md: on-device file operations are cache management). Clearing it is not a deletion under the deletion
 * rules, because nothing outside this one folder can be reached by it.
 */
@Singleton
class OpenFileRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val oneDrive: OneDriveRepository,
    private val downloaders: CloudDownloaders,
    private val links: CloudFileLinks,
    @param:IoDispatcher private val dispatcher: CoroutineDispatcher
) {

    private val openDir: File get() = File(context.cacheDir, OPEN_DIR)

    /**
     * The file's URI if it is on the phone now, asked of MediaStore and never of the ledger (MILESTONES v0.4.0).
     * A ledger id that no longer resolves, or a file in the phone's trash, is not on the phone: MediaStore leaves
     * trashed rows out of a plain query.
     */
    suspend fun uriIfOnPhone(entry: BackupEntryEntity): Uri? = withContext(dispatcher) {
        val recorded = entry.contentUri.takeIf { it.isNotBlank() }?.let { runCatching { Uri.parse(it) }.getOrNull() }
        val present = recorded != null && runCatching {
            context.contentResolver.query(recorded, arrayOf(MediaStore.MediaColumns._ID), null, null, null)
                ?.use { it.moveToFirst() } == true
        }.getOrDefault(false)
        if (present) recorded else findByNameAndSize(entry)
    }

    /**
     * The same file found by name and size in its album's folder, for a row with no usable MediaStore address:
     * Restore's *Already on this phone* rows are built from the Cloud's listing and carry none, and a ledger id can
     * go stale. Name and size in the album is the test Restore itself uses to call a file already here
     * (`RestoreScope`), so the two never disagree. Seen on the Moto, 11 Nov 2026: without this, such a row went to
     * the Cloud.
     */
    private fun findByNameAndSize(entry: BackupEntryEntity): Uri? = runCatching {
        val collection = if (entry.isVideo) {
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }
        val projection = arrayOf(MediaStore.MediaColumns._ID, MediaStore.MediaColumns.RELATIVE_PATH)
        val selection = "${MediaStore.MediaColumns.DISPLAY_NAME} = ? AND ${MediaStore.MediaColumns.SIZE} = ?"
        val args = arrayOf(entry.displayName, entry.sizeBytes.toString())
        context.contentResolver.query(collection, projection, selection, args, null)?.use { cursor ->
            val album = entry.album.lowercase()
            var fallback: Long? = null
            while (cursor.moveToNext()) {
                val id = cursor.getLong(0)
                val folder = cursor.getString(1).orEmpty().trimEnd('/').substringAfterLast('/').lowercase()
                if (folder == album) return@runCatching ContentUris.withAppendedId(collection, id)
                if (fallback == null) fallback = id
            }
            // Only when the album's own folder has no match and exactly this name and size exist elsewhere: the
            // same bytes under the same name, so opening them shows the user the file they pressed.
            fallback?.let { ContentUris.withAppendedId(collection, it) }
        }
    }.getOrNull()

    fun cloudHasLinks(location: BackupLocation): Boolean = links.of(location) != null

    fun canDownload(location: BackupLocation): Boolean =
        location == BackupLocation.ONEDRIVE || downloaders.of(location) != null

    suspend fun linkFor(entry: BackupEntryEntity): DataResult<String> {
        val id = entry.remoteItemId?.takeIf { it.isNotBlank() }
            ?: return DataResult.Failure(RemoteError.Unknown(IOException("no Cloud id recorded")))
        val link = links.of(entry.location)
            ?: return DataResult.Failure(RemoteError.Unknown(IOException("this Cloud has no links")))
        return link.linkFor(id)
    }

    /** Wi-Fi here means any connection the phone does not count as metered, as WorkManager's network rule does. */
    fun network(): NetworkKind {
        val manager = context.getSystemService(ConnectivityManager::class.java) ?: return NetworkKind.NONE
        val caps = manager.getNetworkCapabilities(manager.activeNetwork) ?: return NetworkKind.NONE
        return when {
            !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) -> NetworkKind.NONE
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED) -> NetworkKind.WIFI
            else -> NetworkKind.MOBILE
        }
    }

    /** The size a temporary copy is checked against, as Restore checks it (`DownloadMissingFile`). */
    fun expectedBytes(entry: BackupEntryEntity): Long =
        if (entry.location == BackupLocation.ONEDRIVE) entry.remoteSizeBytes ?: entry.sizeBytes else entry.sizeBytes

    /**
     * Downloads a temporary copy and returns a URI another app can read. Cancelling the coroutine stops it and
     * leaves nothing behind. A copy whose size does not match is thrown away rather than opened: a truncated video
     * looks like a broken file in someone else's app.
     */
    suspend fun downloadTemporary(
        entry: BackupEntryEntity,
        onProgress: (written: Long, total: Long) -> Unit
    ): TemporaryOpen = withContext(dispatcher) {
        val id = entry.remoteItemId?.takeIf { it.isNotBlank() } ?: return@withContext TemporaryOpen.Failed
        val expected = expectedBytes(entry)

        clearTemporary()
        val dir = openDir.apply { mkdirs() }
        if (StatFs(dir.path).availableBytes < expected + SPACE_MARGIN_BYTES) return@withContext TemporaryOpen.NoSpace

        val opened = if (entry.location == BackupLocation.ONEDRIVE) {
            oneDrive.openStream(id)
        } else {
            downloaders.of(entry.location)?.openStream(id) ?: return@withContext TemporaryOpen.Failed
        }
        val stream = when (opened) {
            is DataResult.Success -> opened.value
            is DataResult.Failure -> {
                Logger.w(TAG, "could not open ${entry.displayName}: ${opened.error}")
                return@withContext if ((opened.error as? RemoteError.Http)?.code == 404) {
                    TemporaryOpen.GoneFromCloud
                } else {
                    TemporaryOpen.Failed
                }
            }
        }

        val target = File(dir, safeName(entry.displayName))
        var finished = false
        try {
            stream.use { input ->
                target.outputStream().use { output ->
                    val buffer = ByteArray(BUFFER_BYTES)
                    var written = 0L
                    while (true) {
                        coroutineContext.ensureActive()
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        written += read
                        onProgress(written, expected)
                    }
                }
            }
            if (target.length() != expected) {
                Logger.w(TAG, "temporary copy of ${entry.displayName} is ${target.length()} bytes, expected $expected")
                return@withContext TemporaryOpen.Failed
            }
            finished = true
            TemporaryOpen.Ready(FileProvider.getUriForFile(context, authority(), target), entry.mimeType)
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            Logger.w(TAG, "temporary copy of ${entry.displayName} failed: ${e.message}")
            TemporaryOpen.Failed
        } finally {
            if (!finished) target.delete()
        }
    }

    /** Empties `cacheDir/open/`, and only that folder. */
    fun clearTemporary() {
        openDir.listFiles()?.forEach { if (it.isFile) it.delete() }
    }

    private fun authority() = "${context.packageName}$AUTHORITY_SUFFIX"

    companion object {
        private const val TAG = "OpenFileRepository"

        /** Matches `res/xml/open_files.xml`. */
        const val OPEN_DIR = "open"

        /** Matches the `<provider>` in the manifest. */
        const val AUTHORITY_SUFFIX = ".openfiles"

        private const val BUFFER_BYTES = 64 * 1024

        /** Room left over after the copy, so a temporary file never fills the phone. */
        private const val SPACE_MARGIN_BYTES = 100L * 1000 * 1000

        /** A display name as a file name in the cache folder: never a path, never empty. */
        internal fun safeName(displayName: String): String =
            displayName.replace(Regex("[/\\\\:*?\"<>|\\u0000]"), "_").trim().trimStart('.').ifEmpty { "file" }
    }
}

sealed interface TemporaryOpen {
    data class Ready(val uri: Uri, val mimeType: String) : TemporaryOpen
    data object NoSpace : TemporaryOpen
    data object GoneFromCloud : TemporaryOpen
    data object Failed : TemporaryOpen
}
