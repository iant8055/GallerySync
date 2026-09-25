package com.gallery.sync.data.local.media

import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import java.io.File
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import com.gallery.sync.di.IoDispatcher
import com.gallery.sync.util.Logger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.InputStream
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
/** Where one file stands against the folders the user granted. See [SafMediaWriter.coverage]. */
enum class SafCoverage {
    /** A granted tree covers it, so it can be rewritten with no dialog. */
    COVERED,

    /** It is on the phone but in a folder nobody granted. */
    OUTSIDE,

    /** MediaStore no longer has it. */
    GONE
}

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
     * Whether a write to the file behind [contentUri] would go through a granted tree.
     *
     * The single-file form of [covers], which takes **folder paths**, not content URIs. The two were
     * confused once: video optimising passed a URI here, no folder path ever starts with
     * `content://`, and so every clip was reported as outside the granted folders. Asking MediaStore
     * where the file lives is what turns a URI into the path [covers] wants.
     *
     * [SafCoverage.GONE] is its own answer because "no longer on the phone" and "not ours to write"
     * call for different things: the first is a stale ledger row, the second is a folder the user
     * never granted, and only the second is worth telling them about.
     */
    suspend fun coverage(contentUri: Uri): SafCoverage = withContext(dispatcher) {
        val location = locate(contentUri) ?: return@withContext SafCoverage.GONE
        val granted = scopedDirectories.current()
        if (granted.any { covers(it.relativePath, location.relativePath) }) {
            SafCoverage.COVERED
        } else {
            SafCoverage.OUTSIDE
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


    // ---- Creating a file where it came from ------------------------------------------------------

    /**
     * The granted folder an album's restored files should go back into, or null when `MediaStore` can
     * take them. See [RestoreFolder] for why, and for which folders qualify.
     */
    suspend fun restoreFolderFor(album: String, isVideo: Boolean): String? = withContext(dispatcher) {
        val granted = scopedDirectories.current().filter { it.volume == PRIMARY_VOLUME }
        RestoreFolder.pick(
            album = album,
            isVideo = isVideo,
            grantedPaths = granted.map { it.relativePath },
            subFolders = { folder -> subFoldersOf(granted, folder) }
        )
    }

    /**
     * Creates a new file in [folderRelativePath] through the granted tree, fills it from [source], and
     * returns where MediaStore now has it.
     *
     * Nothing here deletes. **[WriteOutcome.Unsupported] means this route is not available for that folder
     * and nothing was read from [source]**, so the caller can still use the `MediaStore` route with the same
     * stream. Any other failure comes after the source was read.
     *
     * The download goes to a temporary file in the app's own cache first and is checked against
     * [expectedBytes] before anything appears in the user's folder. That is deliberate: a tree grant cannot
     * take back a half-written file (deleting through it is forbidden), so a short read has to be caught
     * while the only thing that exists is the app's own temporary copy.
     *
     * If the name is taken, the provider names the new file `photo (1).jpg` rather than overwriting.
     */
    suspend fun createFile(
        folderRelativePath: String,
        displayName: String,
        mimeType: String,
        expectedBytes: Long,
        onProgress: (bytesWritten: Long) -> Unit = {},
        source: () -> InputStream
    ): WriteOutcome = withContext(dispatcher) {
        val granted = scopedDirectories.current().filter { it.volume == PRIMARY_VOLUME }
            .firstOrNull { covers(it.relativePath, folderRelativePath) }
            ?: return@withContext WriteOutcome.Unsupported
        val treeUri = Uri.parse(granted.treeUri)
        val parent = ensureFolder(granted, treeUri, folderRelativePath)
            ?: return@withContext WriteOutcome.Unsupported

        val temp = File.createTempFile("restore-", ".part", context.cacheDir)
        try {
            var total = 0L
            try {
                temp.outputStream().use { out ->
                    source().use { input ->
                        val buffer = ByteArray(BUFFER_BYTES)
                        while (true) {
                            // A stop means stop, mid-file, the same as the MediaStore route.
                            currentCoroutineContext().ensureActive()
                            val read = input.read(buffer)
                            if (read <= 0) break
                            out.write(buffer, 0, read)
                            total += read
                            onProgress(total)
                        }
                    }
                }
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                Logger.i(TAG, "restore of $displayName stopped at $total bytes")
                throw cancelled
            } catch (error: Throwable) {
                Logger.e(TAG, "download of $displayName failed: ${error.javaClass.simpleName}")
                return@withContext WriteOutcome.Failed("could not download the file")
            }

            if (expectedBytes > 0 && total != expectedBytes) {
                Logger.e(TAG, "restore of $displayName was short: $total of $expectedBytes bytes")
                return@withContext WriteOutcome.Failed("incomplete download: $total of $expectedBytes bytes")
            }

            val document = runCatching {
                DocumentsContract.createDocument(context.contentResolver, parent, mimeType, displayName)
            }.getOrNull() ?: return@withContext WriteOutcome.Failed("could not create the file in that folder")

            val copied = runCatching {
                // Deliberately not cancellable: this copies from the phone's own storage to the phone's own
                // storage, and stopping half way would leave a partial file that cannot be removed.
                context.contentResolver.openOutputStream(document, "wt")?.use { out ->
                    temp.inputStream().use { it.copyTo(out, BUFFER_BYTES) }
                } ?: return@runCatching -1L
                temp.length()
            }.getOrElse {
                Logger.e(TAG, "copy of $displayName into the folder failed: ${it.javaClass.simpleName}")
                -1L
            }
            if (copied != total) return@withContext WriteOutcome.Failed("could not write the file into that folder")

            val actualName = runCatching {
                context.contentResolver.query(
                    document, arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null
                )?.use { if (it.moveToFirst()) it.getString(0) else null }
            }.getOrNull() ?: displayName

            val path = "${Environment.getExternalStorageDirectory()}/${folderRelativePath.trim('/')}/$actualName"
            val indexed = scanAndWait(path, mimeType)
                ?: return@withContext WriteOutcome.Failed("the file was saved, but Android has not listed it yet")

            Logger.i(TAG, "restored $actualName ($total bytes) into $folderRelativePath through the tree grant")
            WriteOutcome.Success(indexed, total)
        } finally {
            temp.delete()
        }
    }

    /** The names of the folders directly inside a granted folder. Empty when [folder] is not itself a grant. */
    private fun subFoldersOf(granted: List<GrantedDirectory>, folder: String): List<String> {
        val grant = granted.firstOrNull { it.relativePath.trim('/') == folder.trim('/') } ?: return emptyList()
        val treeUri = Uri.parse(grant.treeUri)
        val documentId = runCatching { DocumentsContract.getTreeDocumentId(treeUri) }.getOrNull() ?: return emptyList()
        return runCatching {
            val children = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, documentId)
            context.contentResolver.query(
                children,
                arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME, DocumentsContract.Document.COLUMN_MIME_TYPE),
                null, null, null
            )?.use { cursor ->
                buildList {
                    while (cursor.moveToNext()) {
                        if (cursor.getString(1) == DocumentsContract.Document.MIME_TYPE_DIR) add(cursor.getString(0))
                    }
                }
            }.orEmpty()
        }.getOrDefault(emptyList())
    }

    /**
     * The document URI of [folderRelativePath] inside [granted], creating any missing folders on the way.
     * Creating a folder removes nothing. Null when it cannot be reached.
     */
    private fun ensureFolder(granted: GrantedDirectory, treeUri: Uri, folderRelativePath: String): Uri? = runCatching {
        val rootId = DocumentsContract.getTreeDocumentId(treeUri)
        val remainder = folderRelativePath.trim('/').removePrefix(granted.relativePath.trim('/')).trim('/')
        var currentId = rootId
        if (remainder.isNotEmpty()) {
            for (segment in remainder.split('/')) {
                val nextId = "$currentId/$segment"
                val nextUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, nextId)
                val exists = context.contentResolver.query(
                    nextUri, arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID), null, null, null
                )?.use { it.moveToFirst() } == true
                if (!exists) {
                    val parentUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, currentId)
                    DocumentsContract.createDocument(
                        context.contentResolver, parentUri, DocumentsContract.Document.MIME_TYPE_DIR, segment
                    ) ?: return null
                }
                currentId = nextId
            }
        }
        DocumentsContract.buildDocumentUriUsingTree(treeUri, currentId)
    }.getOrNull()

    /** Asks MediaStore to index [path] and waits for the answer, so the row can be adopted straight away. */
    private suspend fun scanAndWait(path: String, mimeType: String): Uri? = withTimeoutOrNull(SCAN_WAIT_MILLIS) {
        suspendCancellableCoroutine<Uri?> { continuation ->
            runCatching {
                MediaScannerConnection.scanFile(context, arrayOf(path), arrayOf(mimeType)) { _, uri ->
                    if (continuation.isActive) continuation.resume(uri) { }
                }
            }.onFailure { if (continuation.isActive) continuation.resume(null) { } }
        }
    }

    private data class MediaLocation(val relativePath: String, val displayName: String)

    private companion object {
        const val TAG = "SafMediaWriter"
        const val PRIMARY_VOLUME = "primary"
        const val BUFFER_BYTES = 64 * 1024
        const val SCAN_WAIT_MILLIS = 15_000L
    }
}
