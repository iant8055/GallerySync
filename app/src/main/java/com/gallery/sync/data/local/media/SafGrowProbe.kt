package com.gallery.sync.data.local.media

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import com.gallery.sync.di.IoDispatcher
import com.gallery.sync.util.Logger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/** What the probe found out. */
sealed interface SafGrowResult {

    /**
     * The tree grant let the file grow, and MediaStore agreed after the rescan.
     *
     * [indexedBytes] is what MediaStore reported. It is carried separately from [grewToBytes]
     * because the two disagreeing is a distinct and more dangerous outcome than the write failing:
     * the file would be right on disk and wrong to every app that reads the index.
     */
    data class Grew(val fromBytes: Long, val grewToBytes: Long, val indexedBytes: Long) :
        SafGrowResult

    /** The write went through but the file is not the size it should be. */
    data class WrongSize(val expectedBytes: Long, val actualBytes: Long) : SafGrowResult

    /** No granted tree covers the probe directory, so the question cannot be asked. */
    data object NoGrant : SafGrowResult

    /** Below API 30 there is no unattended write path at all. */
    data object NotSupported : SafGrowResult

    data class Failed(val reason: String) : SafGrowResult
}

/**
 * Answers one question, on the device, without touching anything the user owns:
 * **will a persisted SAF tree grant let a file get bigger?**
 *
 * ### Why this needs a probe rather than a test
 *
 * The answer depends on the live grant, and `connectedDebugAndroidTest` **uninstalls the app** to
 * install the test APK — taking the ledger, the signed-in session, and the very grant the question
 * is about. An instrumented test would therefore run without a grant and answer nothing. This runs
 * in the app that holds the grant.
 *
 * ### Why the question matters
 *
 * Everything TASK-018 proposes rests on it. What is verified on the Fold 4, 19 Aug 2026, is a
 * *truncating* write: a 4.4 MB photo shortened to 4 KB through the tree grant with no dialog.
 * Restoring is that in reverse — a 5 MB original written back over a 400 KB proxy — and no one has
 * watched it happen. `openOutputStream(document, "wt")` truncates and then writes, so there is no
 * API-level reason for a grow to behave differently, but "no reason it should fail" is not the same
 * as having seen it work, and this app has been wrong about that distinction before.
 *
 * ### It only ever touches its own file
 *
 * The probe creates [PROBE_NAME], writes to it, measures it, and removes the row it created. No
 * file belonging to the user is opened, read, or written at any point. Removing that row is the
 * same act [MediaStoreWriter.discard] already performs and for the same reason: a row this app
 * created moments earlier, holding nothing the user had, is not a deletion in the sense CLAUDE.md
 * governs. It is nonetheless the only removal in this class, and it is deliberate that the probe
 * cleans up rather than leaving debris in DCIM.
 */
@Singleton
class SafGrowProbe @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val safWriter: SafMediaWriter,
    @param:IoDispatcher private val dispatcher: CoroutineDispatcher
) {

    suspend fun run(): SafGrowResult = withContext(dispatcher) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return@withContext SafGrowResult.NotSupported
        if (!safWriter.covers(listOf(PROBE_PATH))) return@withContext SafGrowResult.NoGrant

        val uri = createSmallFile() ?: return@withContext SafGrowResult.Failed(
            "MediaStore would not create the probe file"
        )

        try {
            val before = sizeOnDisk(uri)

            // The whole experiment: write more bytes than are already there, through the grant.
            val large = ByteArray(GROWN_BYTES) { (it % 251).toByte() }
            val wrote = safWriter.writeTruncating(uri) { out -> out.write(large) }
            if (!wrote) {
                return@withContext SafGrowResult.Failed("the tree write was refused")
            }

            val after = sizeOnDisk(uri)
            if (after != GROWN_BYTES.toLong()) {
                return@withContext SafGrowResult.WrongSize(GROWN_BYTES.toLong(), after)
            }

            // The rescan is fire-and-forget inside the writer, so give it a moment before asking
            // the index. A disagreement here is the finding, not a flaky measurement — the same
            // staleness cost a correct proxy its ledger key on 19 Aug 2026.
            delay(RESCAN_GRACE_MS)

            SafGrowResult.Grew(
                fromBytes = before,
                grewToBytes = after,
                indexedBytes = indexedSize(uri)
            )
        } catch (error: Throwable) {
            Logger.e(TAG, "probe failed: ${error.javaClass.simpleName}")
            SafGrowResult.Failed(error.javaClass.simpleName)
        } finally {
            discardProbeFile(uri)
        }
    }

    /** A published file, because a pending one is renamed on disk and the tree lookup would miss it. */
    private fun createSmallFile(): Uri? = runCatching {
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, PROBE_NAME)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            put(MediaStore.MediaColumns.RELATIVE_PATH, PROBE_PATH)
        }
        val uri = context.contentResolver.insert(
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),
            values
        ) ?: return@runCatching null

        context.contentResolver.openOutputStream(uri, "wt")?.use { out ->
            out.write(ByteArray(SEED_BYTES) { (it % 251).toByte() })
        }
        uri
    }.getOrNull()

    private fun sizeOnDisk(uri: Uri): Long = runCatching {
        context.contentResolver.openFileDescriptor(uri, "r")?.use { it.statSize }
    }.getOrNull() ?: -1L

    private fun indexedSize(uri: Uri): Long = runCatching {
        context.contentResolver.query(
            uri,
            arrayOf(MediaStore.MediaColumns.SIZE),
            null,
            null,
            null
        )?.use { cursor -> if (cursor.moveToFirst()) cursor.getLong(0) else null }
    }.getOrNull() ?: -1L

    /** See the class comment: this removes the probe's own row and nothing else, ever. */
    private fun discardProbeFile(uri: Uri) {
        runCatching { context.contentResolver.delete(uri, null, null) }
            .onFailure { Logger.w(TAG, "could not remove the probe file") }
    }

    private companion object {
        const val TAG = "SafGrowProbe"

        /** Named so it is obvious what it is if cleanup ever fails and it turns up in a gallery. */
        const val PROBE_NAME = "gallerysync_saf_grow_probe.jpg"

        /** DCIM/Camera, because that is the directory the app is already granted on this device. */
        const val PROBE_PATH = "DCIM/Camera/"

        const val SEED_BYTES = 4 * 1024
        const val GROWN_BYTES = 512 * 1024

        const val RESCAN_GRACE_MS = 1500L
    }
}
