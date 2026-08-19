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
    @ApplicationContext private val context: Context,
    private val generator: ProxyGenerator,
    private val entryDao: BackupEntryDao,
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
    suspend fun candidates(): List<BackupEntryEntity> = withContext(dispatcher) {
        if (!isSupported()) return@withContext emptyList()

        val recorded = entryDao.proxyCandidates()
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
    suspend fun apply(entries: List<BackupEntryEntity>): ProxyOutcome = withContext(dispatcher) {
        if (!isSupported()) return@withContext ProxyOutcome.NotSupported
        if (entries.isEmpty()) return@withContext ProxyOutcome.NothingToDo

        var proxied = 0
        var skipped = 0
        var reclaimed = 0L

        for (entry in entries) {
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

            // "wt" truncates first, so no tail of the original survives beneath the new bytes.
            val wrote = runCatching {
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
