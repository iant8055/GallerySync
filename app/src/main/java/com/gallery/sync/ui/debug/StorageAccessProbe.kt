package com.gallery.sync.ui.debug

import android.content.Intent
import android.content.Context
import android.net.Uri
import android.media.MediaScannerConnection
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.system.Os
import android.system.OsConstants
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.gallery.sync.util.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Throwaway research UI for one question: **can a persisted SAF tree grant modify and delete media
 * this app does not own, with no MediaStore consent dialog?**
 *
 * If it can, the approval tap disappears from proxying and archiving without
 * `MANAGE_EXTERNAL_STORAGE` and the Play-listing scrutiny that carries. See the escape-routes
 * section of `.claude/tasks/TASK-011.md`.
 *
 * **Answered on hardware, 19 Aug 2026** — Fold 4, device API 36, app targetSdk 37. The grant
 * writes, shortens and survives a reboot; it also deletes, which CLAUDE.md forbids. Full results in
 * the SAF entry of the hardware log in MILESTONES.
 *
 * Kept rather than deleted, because it is the only way to re-run this on another device — the
 * answer is Samsung-and-API-specific and an LG or a Moto may not agree. Debug builds only. It is a
 * probe, not a feature, and nothing in the app should grow to depend on it.
 *
 * ### Why the probes are shaped the way they are
 * The write probe opens `"rw"` and never `"w"`. Mode `"w"` truncates on open, so the obvious
 * version of this test would destroy a real photo to prove it could have written to it. `"rw"`
 * proves the same access and leaves the bytes alone.
 *
 * The delete probe is genuinely destructive and there is no way around that, so it targets the
 * **most recently modified** file in the tree and shows its name before doing anything. Take a
 * throwaway photo immediately before running it and the newest file is that photo.
 */
private data class TreeFile(
    val documentId: String,
    val displayName: String,
    val mimeType: String,
    val lastModified: Long
)

@Composable
fun StorageAccessProbe(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val log = remember { mutableStateListOf<String>() }
    var treeUri by remember { mutableStateOf<Uri?>(null) }
    var pendingDelete by remember { mutableStateOf<TreeFile?>(null) }
    val listed = remember { mutableStateListOf<TreeFile>() }

    fun say(line: String) {
        Logger.d(TAG, line)
        log += line
    }


    // The reboot test lives here rather than in a button. A persisted grant is only worth anything
    // if the app can pick it back up without the user re-picking the folder, so the probe restores
    // it on open and says what it found.
    LaunchedEffect(Unit) {
        val persisted = context.contentResolver.persistedUriPermissions
        if (persisted.isEmpty()) {
            say("No persisted URI permissions — the grant did NOT survive.")
        } else {
            persisted.forEach {
                say("Persisted: ${it.uri} read=${it.isReadPermission} write=${it.isWritePermission}")
            }
            treeUri = persisted.firstOrNull { it.isWritePermission }?.uri
            say("Restored tree without re-picking: $treeUri")
        }
    }
    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri == null) {
            say("Picker cancelled — nothing granted.")
            return@rememberLauncherForActivityResult
        }
        // The whole point of the route: a grant that outlives this Activity, so a background pass
        // can use it later without the user present.
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        }.onFailure { say("PERSIST FAILED: ${it.javaClass.simpleName}: ${it.message}") }
            .onSuccess { say("Persisted read+write on the tree.") }

        treeUri = uri
        say("Picked: $uri")
        say("Tree document id: ${DocumentsContract.getTreeDocumentId(uri)}")
    }

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("SAF tree probe", style = MaterialTheme.typography.titleMedium)
        Text(
            "Debug only. Answers whether a persisted tree grant can write and delete media this " +
                "app does not own, without a MediaStore consent dialog.",
            style = MaterialTheme.typography.bodySmall
        )

        Button(onClick = { picker.launch(null) }, modifier = Modifier.fillMaxWidth()) {
            Text("1 · Pick a folder (try DCIM, then DCIM/Camera)")
        }

        OutlinedButton(
            onClick = {
                val tree = treeUri
                if (tree == null) {
                    say("Pick a folder first.")
                    return@OutlinedButton
                }
                scope.launch {
                    val result = withContext(Dispatchers.IO) { probeWrite(context, tree) }
                    result.forEach(::say)
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("2 · Probe write (writes 1 identical byte)")
        }

        OutlinedButton(
            onClick = {
                val tree = treeUri
                if (tree == null) {
                    say("Pick a folder first.")
                    return@OutlinedButton
                }
                scope.launch {
                    val found = withContext(Dispatchers.IO) { recentFiles(context, tree) }
                    listed.clear()
                    listed += found
                    say("Listed ${found.size} files, newest first. Tap one to target it.")
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("3 · List recent files, newest first")
        }

        // The earlier version auto-targeted whatever it thought was newest, which on a real DCIM
        // turned out to be a months-old screenshot. Picking blind is fine for a read; it is not
        // fine for a delete, so the file is chosen by hand.
        listed.forEach { file ->
            OutlinedButton(
                onClick = { pendingDelete = file },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("${file.displayName} · ${java.util.Date(file.lastModified)}")
            }
        }

        pendingDelete?.let { target ->
            Text(
                "Targeted: ${target.displayName}",
                style = MaterialTheme.typography.titleSmall
            )

            Button(
                onClick = {
                    val tree = treeUri ?: return@Button
                    scope.launch {
                        val result = withContext(Dispatchers.IO) {
                            probeTruncate(context, tree, target) + probeRescan(context, target)
                        }
                        result.forEach(::say)
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("4 · TRUNCATE ${target.displayName} (destroys its contents)")
            }
        }
        pendingDelete?.let { target ->
            Button(
                onClick = {
                    val tree = treeUri ?: return@Button
                    scope.launch {
                        val result = withContext(Dispatchers.IO) {
                            probeDelete(context, tree, target)
                        }
                        result.forEach(::say)
                        pendingDelete = null
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("5 · DELETE ${target.displayName} via SAF")
            }
        }

        OutlinedButton(onClick = { log.clear() }, modifier = Modifier.fillMaxWidth()) {
            Text("Clear log")
        }

        log.forEach { line ->
            Text(
                line,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(vertical = 2.dp)
            )
        }
    }
}

/**
 * Opens the newest file for writing and writes one byte back over itself.
 *
 * Two probes in one, because they answer different questions and the weaker one flattered the
 * result. **Opening** `"rw"` proves the descriptor is granted; it does not prove the filesystem
 * will accept a write, and scoped storage can refuse at either point. So this also performs a
 * genuine write.
 *
 * The write is byte-identical: read the first byte, seek back, write the same byte. The file is
 * unchanged and its size is checked before and after to prove it. `"rw"` never truncates, unlike
 * `"w"`, which would answer the question by destroying the evidence.
 */
private fun probeWrite(context: Context, treeUri: Uri): List<String> {
    val out = mutableListOf<String>()
    val target = newestFile(context, treeUri)
        ?: return listOf("No files found under the tree.")

    out += "Write target: ${target.displayName} (${target.mimeType})"
    out += "Owner per MediaStore: ${ownerOf(context, target.displayName)}"
    val docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, target.documentId)

    runCatching {
        context.contentResolver.openFileDescriptor(docUri, "rw")?.use { pfd ->
            val fd = pfd.fileDescriptor
            val sizeBefore = Os.fstat(fd).st_size
            out += "OPEN OK — rw descriptor granted, no consent dialog. Size $sizeBefore."

            val buf = ByteArray(1)
            Os.lseek(fd, 0, OsConstants.SEEK_SET)
            val read = Os.read(fd, buf, 0, 1)
            if (read != 1) {
                out += "READ FAILED — got $read bytes."
                return@use
            }

            Os.lseek(fd, 0, OsConstants.SEEK_SET)
            val written = Os.write(fd, buf, 0, 1)
            Os.fsync(fd)
            val sizeAfter = Os.fstat(fd).st_size

            out += if (written == 1 && sizeAfter == sizeBefore) {
                "WRITE OK — wrote 1 identical byte to a file we do not own. Size unchanged."
            } else {
                "WRITE ODD — wrote $written bytes, size $sizeBefore -> $sizeAfter."
            }
        } ?: run { out += "OPEN FAILED — null descriptor." }
    }.onFailure {
        out += "FAILED — ${it.javaClass.simpleName}: ${it.message}"
    }

    return out
}

private fun probeDelete(context: Context, treeUri: Uri, target: TreeFile): List<String> {
    val out = mutableListOf<String>()
    val docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, target.documentId)

    runCatching {
        DocumentsContract.deleteDocument(context.contentResolver, docUri)
    }.onSuccess { deleted ->
        out += if (deleted) {
            "DELETE OK — removed via SAF with no consent dialog."
        } else {
            "DELETE returned false — provider refused without throwing."
        }
    }.onFailure {
        out += "DELETE FAILED — ${it.javaClass.simpleName}: ${it.message}"
    }

    // The third unknown: SAF writes do not themselves update MediaStore, so a gallery could keep
    // showing a file that is gone.
    out += "MediaStore row after delete: ${mediaStoreRow(context, target.displayName)}"
    return out
}

/** Newest file under [treeUri], descending one level into subdirectories so DCIM works as a pick. */
private fun newestFile(context: Context, treeUri: Uri): TreeFile? {
    val rootId = DocumentsContract.getTreeDocumentId(treeUri)
    val files = mutableListOf<TreeFile>()
    val dirs = mutableListOf<String>()

    childrenOf(context, treeUri, rootId).forEach { child ->
        if (child.mimeType == DocumentsContract.Document.MIME_TYPE_DIR) dirs += child.documentId
        else files += child
    }
    dirs.forEach { dirId ->
        childrenOf(context, treeUri, dirId).forEach { child ->
            if (child.mimeType != DocumentsContract.Document.MIME_TYPE_DIR) files += child
        }
    }

    return files.maxByOrNull { it.lastModified }
}

private fun childrenOf(context: Context, treeUri: Uri, parentId: String): List<TreeFile> {
    val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentId)
    val out = mutableListOf<TreeFile>()
    runCatching {
        context.contentResolver.query(
            childrenUri,
            arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE,
                DocumentsContract.Document.COLUMN_LAST_MODIFIED
            ),
            null,
            null,
            null
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                out += TreeFile(
                    documentId = cursor.getString(0),
                    displayName = cursor.getString(1) ?: "(unnamed)",
                    mimeType = cursor.getString(2) ?: "",
                    lastModified = cursor.getLong(3)
                )
            }
        }
    }.onFailure { Logger.e(TAG, "children query failed", it) }
    return out
}

/** Which package MediaStore thinks owns the file — the whole question is whether it is not us. */
private fun ownerOf(context: Context, displayName: String): String =
    queryImages(context, displayName, MediaStore.Images.Media.OWNER_PACKAGE_NAME)
        ?: "no MediaStore row"

private fun mediaStoreRow(context: Context, displayName: String): String =
    queryImages(context, displayName, MediaStore.Images.Media._ID)
        ?.let { "still present (id $it)" }
        ?: "gone"

private fun queryImages(context: Context, displayName: String, column: String): String? =
    context.contentResolver.query(
        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
        arrayOf(column),
        "${MediaStore.Images.Media.DISPLAY_NAME} = ?",
        arrayOf(displayName),
        null
    )?.use { if (it.moveToFirst()) it.getString(0) else null }

private const val TAG = "StorageAccessProbe"

/** The 15 most recently modified files under the tree, so a destructive probe can be aimed by hand. */
private fun recentFiles(context: Context, treeUri: Uri): List<TreeFile> {
    val rootId = DocumentsContract.getTreeDocumentId(treeUri)
    val files = mutableListOf<TreeFile>()
    val dirs = mutableListOf<String>()

    childrenOf(context, treeUri, rootId).forEach { child ->
        if (child.mimeType == DocumentsContract.Document.MIME_TYPE_DIR) dirs += child.documentId
        else files += child
    }
    dirs.forEach { dirId ->
        childrenOf(context, treeUri, dirId).forEach { child ->
            if (child.mimeType != DocumentsContract.Document.MIME_TYPE_DIR) files += child
        }
    }

    return files.sortedByDescending { it.lastModified }.take(15)
}

/**
 * Replaces the file's contents with something much smaller — what proxying actually does.
 *
 * The write already proved is byte-identical and same-length, which is the easy case. A proxy turns
 * a multi-megabyte photo into a few hundred kilobytes, so the write has to **shorten** the file, and
 * it leaves MediaStore holding a stale size and stale dimensions until something rescans. Both
 * halves are checked here.
 *
 * **Destructive.** It overwrites the target's bytes. Only ever aim it at a throwaway.
 */
private fun probeTruncate(context: Context, treeUri: Uri, target: TreeFile): List<String> {
    val out = mutableListOf<String>()
    val docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, target.documentId)
    out += "Truncate target: ${target.displayName}"
    out += "Owner per MediaStore: ${ownerOf(context, target.displayName)}"
    out += "MediaStore size before: ${mediaStoreSize(context, target.displayName)}"

    runCatching {
        context.contentResolver.openFileDescriptor(docUri, "rw")?.use { pfd ->
            val fd = pfd.fileDescriptor
            val before = Os.fstat(fd).st_size

            val payload = ByteArray(PAYLOAD_BYTES) { 0x41 }
            Os.lseek(fd, 0, OsConstants.SEEK_SET)
            var written = 0
            while (written < payload.size) {
                written += Os.write(fd, payload, written, payload.size - written)
            }
            Os.ftruncate(fd, payload.size.toLong())
            Os.fsync(fd)

            val after = Os.fstat(fd).st_size
            out += "TRUNCATING WRITE — $before -> $after bytes on disk."
            out += if (after == payload.size.toLong()) {
                "SHORTEN OK — ftruncate through the tree grant works, no dialog."
            } else {
                "SHORTEN ODD — expected ${payload.size}, got $after."
            }
        } ?: run { out += "OPEN FAILED — null descriptor." }
    }.onFailure {
        out += "FAILED — ${it.javaClass.simpleName}: ${it.message}"
    }

    // The second half of the question: does the index notice?
    out += "MediaStore size immediately after: ${mediaStoreSize(context, target.displayName)}"
    return out
}

/** Asks MediaStore to re-read the file, to see whether a rescan is what reconciles the index. */
private fun probeRescan(context: Context, target: TreeFile): List<String> {
    val out = mutableListOf<String>()
    val latch = java.util.concurrent.CountDownLatch(1)
    val path = "${android.os.Environment.getExternalStorageDirectory()}/DCIM/Camera/${target.displayName}"

    MediaScannerConnection.scanFile(context, arrayOf(path), null) { _, _ -> latch.countDown() }
    val finished = latch.await(10, java.util.concurrent.TimeUnit.SECONDS)

    out += if (finished) "Rescan completed for $path" else "Rescan timed out for $path"
    out += "MediaStore size after rescan: ${mediaStoreSize(context, target.displayName)}"
    return out
}

private fun mediaStoreSize(context: Context, displayName: String): String =
    queryImages(context, displayName, MediaStore.Images.Media.SIZE) ?: "no MediaStore row"

private const val PAYLOAD_BYTES = 4096
