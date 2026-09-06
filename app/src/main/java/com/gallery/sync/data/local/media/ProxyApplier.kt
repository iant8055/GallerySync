package com.gallery.sync.data.local.media

import android.content.ContentResolver
import android.content.Context
import android.content.IntentSender
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.exifinterface.media.ExifInterface
import com.gallery.sync.data.local.dao.BackupEntryDao
import com.gallery.sync.data.local.entity.BackupEntryEntity
import com.gallery.sync.data.local.settings.BackupSettings
import com.gallery.sync.di.IoDispatcher
import com.gallery.sync.util.Logger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** Outcome of replacing local originals with proxies. */
sealed interface ProxyOutcome {

    data class Completed(val proxiedCount: Int, val bytesReclaimed: Long) : ProxyOutcome

    /**
     * A file could not be replaced even after retrying, so the run stopped there.
     *
     * Everything before it is done and recorded; nothing after it was touched. A predictable
     * half-finished state is worth more than an unpredictable mostly-finished one.
     */
    data class Stopped(
        val proxiedCount: Int,
        val bytesReclaimed: Long,
        val failedFile: String,
        val reason: String
    ) : ProxyOutcome

    data object NothingToDo : ProxyOutcome

    /** Below API 30 there is no way to modify another app's media with user consent. */
    data object NotSupported : ProxyOutcome
}

/**
 * Replaces local originals with downscaled proxies.
 *
 * **The destructive half of proxying.** After this runs, the full-resolution image exists only in
 * OneDrive. Every safeguard here is deliberate:
 *
 *  - only files the ledger has verified in the cloud are ever candidates
 *  - the proxy is generated and decoded back before the original is touched
 *  - a failing file is retried before it is treated as fatal, because a locked file or a momentary
 *    IO error should not halt a two-hundred-photo run
 *  - a file that still fails stops the run rather than being skipped, because "which photos are
 *    still full quality" must remain answerable
 */
@Singleton
class ProxyApplier @Inject constructor(
    private val safWriter: SafMediaWriter,
    @ApplicationContext private val context: Context,
    private val generator: ProxyGenerator,
    private val entryDao: BackupEntryDao,
    private val settings: BackupSettings,
    @param:IoDispatcher private val dispatcher: CoroutineDispatcher
) {

    private val resolver: ContentResolver get() = context.contentResolver

    fun isSupported(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R

    /**
     * Photos eligible right now, largest first — the biggest wins for the least risk.
     *
     * The ledger on its own is not enough. It records what was uploaded, not what is still on the
     * phone, so a photo since deleted from the gallery leaves a row pointing at nothing. Those rows
     * have to be dropped before the list is used anywhere: they inflate the megabytes offered, and
     * a URI that no longer resolves makes [MediaStore.createWriteRequest] reject the entire batch
     * rather than just that item — one dead row silently disables optimising altogether.
     *
     * The rows are left in place rather than deleted. A file that is gone locally but present in
     * OneDrive is still genuinely backed up, and forgetting it would understate what is verified.
     */
    suspend fun candidates(): List<BackupEntryEntity> = candidatesFrom(entryDao.proxyCandidates())

    /**
     * Photos for the install wizard's one-time bulk optimise (Area 1).
     *
     * Not governed by album modes or by the age threshold — but **is** governed by the optimise
     * cutoff, which is the whole of the difference between Gate 2's #2 and #3. Ian, 6 Sept 2026,
     * on what each must do: #2 *"optimizes ALL files on phone"*, #3 *"ONLY optimizes files that
     * were actually backed up in the previous step"*.
     *
     * Read here rather than passed in. There are seven call sites, and a parameter every one of
     * them had to remember would be a parameter one of them eventually got wrong — which is the
     * failure this whole area has already had twice.
     */
    suspend fun candidatesAll(): List<BackupEntryEntity> =
        candidatesFrom(entryDao.proxyCandidatesAll(settings.current().optimiseCutoffEpochMillis))

    private suspend fun candidatesFrom(
        recorded: List<BackupEntryEntity>
    ): List<BackupEntryEntity> = withContext(dispatcher) {
        if (!isSupported()) return@withContext emptyList()
        val live = recorded.filter { stillOnDevice(Uri.parse(it.contentUri)) }

        val missing = recorded.size - live.size
        if (missing > 0) {
            Logger.i(TAG, "ignoring $missing candidates whose local file is gone")
        }

        // Largest-first ordering means a capped run still reclaims the most it can, and whatever
        // is trimmed here stays eligible for the next one.
        if (live.size > MAX_URIS_PER_REQUEST) {
            Logger.i(TAG, "capping ${live.size} candidates to $MAX_URIS_PER_REQUEST")
        }
        live.take(MAX_URIS_PER_REQUEST)
    }

    /**
     * Whether the URI still resolves to a row in MediaStore.
     *
     * Opening the file would be a stronger check, but this runs over every candidate each time the
     * settings screen refreshes; asking for a single column is the cheapest question that answers
     * it.
     */
    private fun stillOnDevice(uri: Uri): Boolean = runCatching {
        resolver.query(uri, arrayOf(MediaStore.MediaColumns._ID), null, null, null)
            ?.use { it.moveToFirst() } ?: false
    }.getOrDefault(false)

    /**
     * Whether these files still need Android's write dialog.
     *
     * False when every one of them sits inside a granted SAF tree, which is the ordinary case once
     * the user has answered Gate 1 — and it is what lets optimising happen without a tap. Asked
     * rather than assumed: a file outside the granted trees genuinely does need the dialog, and
     * skipping it silently would be worse than showing one.
     */
    suspend fun needsWriteRequest(entries: List<BackupEntryEntity>): Boolean {
        if (entries.isEmpty()) return false
        val paths = entries.mapNotNull { relativePathOf(Uri.parse(it.contentUri)) }
        if (paths.size != entries.size) return true
        return !safWriter.covers(paths)
    }

    private fun relativePathOf(uri: Uri): String? = runCatching {
        resolver.query(uri, arrayOf(MediaStore.MediaColumns.RELATIVE_PATH), null, null, null)
            ?.use { if (it.moveToFirst()) it.getString(0) else null }
    }.getOrNull()

    /**
     * Asks Android for permission to modify [entries].
     *
     * The system shows its own dialog listing the files. This app never rewrites someone's photos
     * on its own say-so.
     */
    fun createWriteRequest(entries: List<BackupEntryEntity>): IntentSender? {
        if (!isSupported() || entries.isEmpty()) return null

        val uris = entries.map { Uri.parse(it.contentUri) }
        return runCatching {
            MediaStore.createWriteRequest(resolver, uris).intentSender
        }.onFailure {
            // The message is what identifies a bad URI; the class name alone says nothing.
            Logger.e(TAG, "could not build write request: ${it.javaClass.simpleName}: ${it.message}")
            Logger.e(TAG, "uris: ${uris.size}, first: ${uris.firstOrNull()}")
            Logger.e(TAG, "distinct authorities: ${uris.mapNotNull { u -> u.authority }.distinct()}")
            Logger.e(
                TAG,
                "distinct paths: ${uris.map { u -> u.pathSegments.dropLast(1).joinToString("/") }.distinct()}"
            )
        }.getOrNull()
    }

    /**
     * Replaces each entry's local file with a proxy. Call only after the write request was granted.
     */
    suspend fun apply(
        entries: List<BackupEntryEntity>,
        onProgress: (done: Int, total: Int) -> Unit = { _, _ -> }
    ): ProxyOutcome = withContext(dispatcher) {
        if (!isSupported()) return@withContext ProxyOutcome.NotSupported
        if (entries.isEmpty()) return@withContext ProxyOutcome.NothingToDo

        var proxied = 0
        var skipped = 0
        var reclaimed = 0L

        // Reported per file so a caller can say how far through it is. A photo is quick, but a
        // whole library of them is not, and the wizard has no other way to tell.
        onProgress(0, entries.size)
        for ((index, entry) in entries.withIndex()) {
            onProgress(index, entries.size)
            when (val result = proxyWithRetries(entry)) {
                is FileResult.Replaced -> {
                    proxied++
                    reclaimed += entry.sizeBytes - result.newSizeBytes
                }

                // Not a failure: an image already at or under the target size, or already a proxy.
                // Recorded rather than merely tolerated — otherwise it is offered again on every
                // run, the count never reaches zero, and the button becomes a no-op.
                FileResult.NotWorthwhile -> {
                    entryDao.markProxySkipped(entry.id)
                    skipped++
                }

                is FileResult.Failed -> {
                    Logger.e(TAG, "stopping: ${entry.displayName} — ${result.reason}")
                    return@withContext ProxyOutcome.Stopped(
                        proxiedCount = proxied,
                        bytesReclaimed = reclaimed,
                        failedFile = entry.displayName,
                        reason = result.reason
                    )
                }
            }
        }

        Logger.i(TAG, "proxied $proxied files, reclaimed $reclaimed bytes, $skipped not worth proxying")
        ProxyOutcome.Completed(proxied, reclaimed)
    }

    /**
     * Tries one file up to [MAX_ATTEMPTS] times.
     *
     * The proxy is regenerated on each attempt rather than reused: if the first attempt failed
     * partway through writing, the cached proxy is the thing under suspicion.
     */
    private suspend fun proxyWithRetries(entry: BackupEntryEntity): FileResult {
        var lastReason = "unknown"

        repeat(MAX_ATTEMPTS) { attempt ->
            when (val result = proxyOnce(entry)) {
                is FileResult.Replaced, FileResult.NotWorthwhile -> return result
                is FileResult.Failed -> {
                    lastReason = result.reason
                    Logger.w(
                        TAG,
                        "attempt ${attempt + 1} failed for ${entry.displayName}: ${result.reason}"
                    )
                    if (attempt < MAX_ATTEMPTS - 1) delay(RETRY_DELAY_MILLIS)
                }
            }
        }
        return FileResult.Failed(lastReason)
    }

    private suspend fun proxyOnce(entry: BackupEntryEntity): FileResult {
        val uri = Uri.parse(entry.contentUri)

        val proxy = when (val result = generator.generate(uri, entry.displayName)) {
            is ProxyResult.Created -> result.proxy
            ProxyResult.NotWorthwhile -> return FileResult.NotWorthwhile
            is ProxyResult.Failed -> return FileResult.Failed(result.reason)
        }

        try {
            // Decode the proxy back before trusting it. A proxy that will not open is one that
            // would replace a real photo with garbage.
            if (!isReadableImage(proxy.file)) {
                return FileResult.Failed("generated proxy did not decode")
            }
            if (!hasOrientation(proxy.file)) {
                return FileResult.Failed("generated proxy lost its EXIF")
            }

            // A proxy that is not smaller is not a proxy. The generator decides on pixel
            // dimensions, which is the right test for whether downscaling is possible but not for
            // whether it helps: a heavily-compressed source above 2048px can re-encode larger.
            // Observed 26 Aug 2026 — a 404 KB image became a 490 KB "proxy", spending space,
            // quality and a cloud badge to save nothing, and making the reclaimed total negative.
            //
            // NotWorthwhile rather than Failed, because the answer will not change on a retry.
            if (proxy.sizeBytes >= entry.sizeBytes) {
                Logger.d(
                    TAG,
                    "${entry.displayName}: proxy ${proxy.sizeBytes} >= original ${entry.sizeBytes}; not worthwhile"
                )
                return FileResult.NotWorthwhile
            }

            // The tree grant first: it writes with no dialog, which is the whole reason optimising
            // can run unattended. Falls back to the MediaStore path for anything outside a granted
            // tree, where Android still requires the per-batch confirmation.
            val wroteViaTree = safWriter.writeTruncating(uri) { out ->
                proxy.file.inputStream().use { it.copyTo(out) }
            }

            val wrote = wroteViaTree || runCatching {
                // "wt" truncates first, so no tail of the original survives beneath the new bytes.
                resolver.openOutputStream(uri, "wt")?.use { out ->
                    proxy.file.inputStream().use { it.copyTo(out) }
                    true
                } ?: false
            }.getOrElse { error ->
                return FileResult.Failed(error.javaClass.simpleName)
            }

            if (!wrote) return FileResult.Failed("could not open the original for writing")

            entryDao.markProxied(entry.id, proxySizeBytes = proxy.sizeBytes)
            return FileResult.Replaced(proxy.sizeBytes)
        } finally {
            proxy.file.delete()
        }
    }

    private fun isReadableImage(file: File): Boolean = runCatching {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, options)
        options.outWidth > 0 && options.outHeight > 0
    }.getOrDefault(false)

    private fun hasOrientation(file: File): Boolean = runCatching {
        ExifInterface(file.absolutePath).getAttribute(ExifInterface.TAG_ORIENTATION) != null
    }.getOrDefault(false)

    private sealed interface FileResult {
        data class Replaced(val newSizeBytes: Long) : FileResult

        /** Examined and permanently not worth proxying. Recorded so it stops being offered. */
        data object NotWorthwhile : FileResult

        data class Failed(val reason: String) : FileResult
    }

    private companion object {
        const val TAG = "ProxyApplier"

        /** Enough to ride out a locked file or a transient IO error, not enough to hide a real one. */
        const val MAX_ATTEMPTS = 3
        const val RETRY_DELAY_MILLIS = 500L

        /**
         * MediaStore's own limit on a single write request, above which it throws.
         *
         * A full library easily exceeds this — the point at which optimising matters most is
         * exactly the point at which an uncapped request would fail.
         */
        const val MAX_URIS_PER_REQUEST = 2000
    }
}
