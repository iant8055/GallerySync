package com.gallery.sync.data.local.media

import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import com.gallery.sync.di.IoDispatcher
import com.gallery.sync.util.Logger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.OutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Writes to media files through a persisted SAF tree grant, with no consent dialog.
 *
 * ### Why this exists
 *
 * `MediaStore.createWriteRequest` raises a system dialog for every batch and only launches from an
 * Activity, so optimising could never be unattended — the user had to be present and tap for each
 * batch. A persisted tree grant has no such limit.
 *
 * **Verified on the Fold 4, 19 Aug 2026**, including the case that matters here: a 4.4 MB photo owned
 * by `com.sec.android.app.camera` shortened to 4 KB through the tree grant, no dialog at any point,
 * and the grant survived both a reboot and an app reinstall.
 *
 * CLAUDE.md permits exactly this and no more: *"The SAF tree grant is still the right route for
 * proxying, which shortens a file and removes nothing. The prohibition is on deleting through it, not
 * on using it."* Nothing in this class deletes. `DocumentsContract.deleteDocument` must never appear
 * here — it is a permanent delete with no recoverable trash, which is why it is forbidden outright.
 *
 * ### The rescan is not optional
 *
 * MediaStore does not notice a size change made through the tree. The same 19 Aug run measured the
 * index still reporting 4,420,894 bytes after the file on disk had become 4,096. That matters twice
 * over: the gallery shows stale sizes and dimensions, and the ledger's `album + name + size + mtime`
 * key is computed against a size that is no longer true — so the scanner would treat the proxy as a
 * brand-new file. Every write here is followed by [MediaScannerConnection.scanFile].
 */
@Singleton
class SafMediaWriter @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val scopedDirectories: ScopedDirectories,
    @param:IoDispatcher private val dispatcher: CoroutineDispatcher
) {

    /**
     * Whether a granted tree covers every one of [relativePaths].
     *
     * Asked before offering an unattended run: a file outside the granted trees still needs the
     * old dialog, and claiming otherwise would mean silently skipping it.
     */
    suspend fun covers(relativePaths: Collection<String>): Boolean = withContext(dispatcher) {
        val granted = scopedDirectories.current()
        relativePaths.isNotEmpty() && relativePaths.all { path ->
            granted.any { covers(it.relativePath, path) }
        }
    }

    /**
     * Truncates the file at [contentUri] and writes new bytes through the tree grant.
     *
     * Returns false when no granted tree covers the file, or the write fails — the caller then falls
     * back to the MediaStore path, which asks the user. Never throws for an uncovered file, because
     * "not ours to write" is an ordinary answer rather than an error.
     */
    suspend fun writeTruncating(
        contentUri: Uri,
        write: (OutputStream) -> Unit
    ): Boolean = withContext(dispatcher) {
        val location = locate(contentUri) ?: return@withContext false
        val document = documentUriFor(location) ?: return@withContext false

        val wrote = runCatching {
            // "wt" truncates first, so no tail of the original survives beneath the new bytes.
            context.contentResolver.openOutputStream(document, "wt")?.use(write) ?: return@runCatching false
            true
        }.getOrElse { error ->
            Logger.w(TAG, "tree write failed for ${location.displayName}: ${error.javaClass.simpleName}")
            false
        }

        if (!wrote) return@withContext false

        rescan(location)
        true
    }

    /**
     * Brings MediaStore back in line with the file on disk.
     *
     * **Fire and forget, deliberately.** `scanFile` with a null callback returns immediately and the
     * scan lands a moment later; this does not wait for it. Measured on the Fold 4, 26 Aug 2026:
     * nine rewritten photos all had matching MediaStore and on-disk sizes when checked seconds
     * afterwards.
     *
     * Waiting would be the wrong trade. The ledger records `localProxySizeBytes` from the file this
     * app just wrote rather than from MediaStore, and `isProxied` is what stops a re-upload — so a
     * briefly stale index costs a gallery thumbnail that is a moment behind, not a wrong decision.
     * A failed scan leaves a stale row rather than a lost file, and the next full scan reconciles it.
     */
    private suspend fun rescan(location: MediaLocation) {
        val path = "${Environment.getExternalStorageDirectory()}/${location.relativePath}${location.displayName}"
        runCatching {
            MediaScannerConnection.scanFile(context, arrayOf(path), null, null)
        }.onFailure {
            // The write already succeeded; a failed rescan leaves a stale index rather than a lost
            // file, and the next full scan reconciles it.
            Logger.w(TAG, "rescan failed for $path: ${it.javaClass.simpleName}")
        }
    }

    /** Where MediaStore says this item lives. Null when the row is gone. */
    private fun locate(contentUri: Uri): MediaLocation? = runCatching {
        context.contentResolver.query(
            contentUri,
            arrayOf(MediaStore.MediaColumns.RELATIVE_PATH, MediaStore.MediaColumns.DISPLAY_NAME),
            null,
            null,
            null
        )?.use { cursor ->
            if (!cursor.moveToFirst()) return@use null
            val relative = cursor.getString(0) ?: return@use null
            val name = cursor.getString(1) ?: return@use null
            MediaLocation(relativePath = relative, displayName = name)
        }
    }.getOrNull()

    /**
     * The document URI for a file inside a granted tree.
     *
     * A tree's document id is its own path — `primary:DCIM` — and a descendant's is that plus the
     * remainder of the path. Built rather than searched: walking the tree with `queryChildren` costs
     * a query per directory level, for an answer the path already contains.
     */
    private suspend fun documentUriFor(location: MediaLocation): Uri? {
        val granted = scopedDirectories.current()
            .firstOrNull { covers(it.relativePath, location.relativePath) }
            ?: return null

        val treeUri = Uri.parse(granted.treeUri)
        val treeDocumentId = runCatching {
            DocumentsContract.getTreeDocumentId(treeUri)
        }.getOrNull() ?: return null

        // The part of the file's path below the granted root, plus the file itself.
        val root = granted.relativePath.trim('/')
        val full = "${location.relativePath.trim('/')}/${location.displayName}"
        val remainder = full.removePrefix(root).trim('/')

        val documentId = if (remainder.isEmpty()) treeDocumentId else "$treeDocumentId/$remainder"
        return runCatching {
            DocumentsContract.buildDocumentUriUsingTree(treeUri, documentId)
        }.getOrNull()
    }

    /** Whether [root] is an ancestor of, or equal to, [path]. Both relative to the volume root. */
    private fun covers(root: String, path: String): Boolean {
        val normalisedRoot = root.trim('/')
        val normalisedPath = path.trim('/')
        return normalisedPath == normalisedRoot || normalisedPath.startsWith("$normalisedRoot/")
    }

    private data class MediaLocation(val relativePath: String, val displayName: String)

    private companion object {
        const val TAG = "SafMediaWriter"
    }
}
