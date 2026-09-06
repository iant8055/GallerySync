package com.gallery.sync.data.local.media

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import com.gallery.sync.data.local.dao.BackupEntryDao
import com.gallery.sync.data.local.entity.BackupEntryEntity
import com.gallery.sync.data.local.settings.BackupSettings
import com.gallery.sync.di.IoDispatcher
import com.gallery.sync.domain.backup.MediaAge
import com.gallery.sync.domain.backup.OptimiseCutoff
import com.gallery.sync.util.Logger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.coroutineContext

/** What one run of video optimising achieved. */
data class VideoOptimiseResult(
    val optimised: Int = 0,
    val reclaimedBytes: Long = 0L,
    /** Examined and permanently declined — too small, or this phone cannot decode them. */
    val skipped: Int = 0,
    /** Failed this time and left as candidates. */
    val failed: Int = 0,
    /** Eligible but not attempted, because the tree grant does not cover their folder. */
    val notCovered: Int = 0
) {
    val didAnything: Boolean get() = optimised > 0 || skipped > 0 || failed > 0
}

/**
 * Replaces old video in Sync albums with a smaller local copy.
 *
 * The destructive half of video optimising. [VideoTranscoder] makes the smaller file and touches
 * nothing; this is what overwrites the original, and it is deliberately the smaller of the two
 * classes for that reason.
 *
 * ### It only ever writes through the tree grant
 *
 * `ProxyApplier` keeps two routes for photos — the SAF grant where the folder is covered, and
 * `MediaStore.createWriteRequest` with a per-batch tap where it is not. **This has one.** A photo
 * batch behind one dialog is reasonable; a clip that takes seconds to transcode, paired with a
 * consent dialog per batch, is not — TASK-013 says so plainly, and it is why the SAF finding of
 * 19 Aug is what made this feature buildable at all.
 *
 * A clip outside every granted tree is counted in [VideoOptimiseResult.notCovered] and left alone.
 * That is a real state and the UI should be able to say so, rather than the run silently doing less
 * than the candidate count promised.
 *
 * ### Order of operations, and why it is this order
 *
 * Transcode to cache, validate, **then** overwrite. The validation lives in the transcoder and
 * checks duration against the source, because a truncated clip is a perfectly valid file that is
 * simply too short — and that failure is discovered inside an editor, long after the original is
 * gone.
 */
@Singleton
class VideoOptimiser @Inject constructor(
    @ApplicationContext private val context: Context,
    private val entryDao: BackupEntryDao,
    private val transcoder: VideoTranscoder,
    private val safWriter: SafMediaWriter,
    private val settings: BackupSettings,
    @param:IoDispatcher private val dispatcher: CoroutineDispatcher
) {

    private val resolver: ContentResolver get() = context.contentResolver

    /**
     * All verified videos regardless of album mode — for the wizard's one-time pass.
     */
    suspend fun wizardCandidates(): List<BackupEntryEntity> = withContext(dispatcher) {
        entryDao.videoOptimiseCandidatesAll(settings.current().optimiseCutoffEpochMillis)
    }

    /**
     * One-time bulk optimise for the install wizard (Area 1).
     *
     * Ignores Area 2 settings gates (isOptimiseEnabled, optimiseVideo, age, cutoff) and album
     * modes — the library choice on step 6 is the only gate. Uses the quality the user chose on
     * step 7.
     *
     * Unlike the ongoing [run], this falls back to ContentResolver when SAF does not cover a
     * file. The wizard acquires a write request up front (one dialog for all videos), so the
     * ContentResolver path works for everything the SAF grant does not reach.
     */
    suspend fun runForWizard(
        quality: com.gallery.sync.domain.backup.VideoQuality,
        limit: Int = WIZARD_LIMIT,
        onProgress: (done: Int, total: Int) -> Unit = { _, _ -> }
    ): VideoOptimiseResult = withContext(dispatcher) {
        val candidates = entryDao.videoOptimiseCandidatesAll(
            cutoffMillis = settings.current().optimiseCutoffEpochMillis,
            limit = limit
        )

        if (candidates.isEmpty()) {
            Logger.d(TAG, "wizard: no video is eligible for optimising")
            return@withContext VideoOptimiseResult()
        }

        Logger.i(TAG, "wizard: optimising up to ${candidates.size} clips at $quality")

        var result = VideoOptimiseResult()
        // Reported per clip, because a transcode is tens of seconds and the wizard sits on this
        // phase for minutes. The batch total is only known here, so it is handed out with each
        // step rather than asked for separately.
        onProgress(0, candidates.size)
        for ((index, entry) in candidates.withIndex()) {
            coroutineContext.ensureActive()
            result = optimiseForWizard(entry, quality, result)
            onProgress(index + 1, candidates.size)
        }

        Logger.i(
            TAG,
            "wizard video optimising finished: ${result.optimised} optimised, " +
                "${result.reclaimedBytes} bytes reclaimed, ${result.skipped} not worth it, " +
                "${result.failed} failed, ${result.notCovered} outside a granted folder"
        )
        result
    }

    private suspend fun optimiseForWizard(
        entry: BackupEntryEntity,
        quality: com.gallery.sync.domain.backup.VideoQuality,
        running: VideoOptimiseResult
    ): VideoOptimiseResult {
        val uri = Uri.parse(entry.contentUri)

        return when (val outcome = transcoder.transcode(uri, entry.displayName, quality)) {
            is TranscodeResult.NotWorthwhile -> {
                Logger.d(TAG, "${entry.displayName}: ${outcome.reason}")
                entryDao.markProxySkipped(entry.id)
                running.copy(skipped = running.skipped + 1)
            }

            is TranscodeResult.Failed -> {
                Logger.w(TAG, "${entry.displayName} failed to transcode: ${outcome.reason}")
                running.copy(failed = running.failed + 1)
            }

            is TranscodeResult.Created -> {
                val wrote = safWriter.writeTruncating(uri) { out ->
                    outcome.file.inputStream().use { it.copyTo(out) }
                } || runCatching {
                    resolver.openOutputStream(uri, "wt")?.use { out ->
                        outcome.file.inputStream().use { it.copyTo(out) }
                        true
                    } ?: false
                }.getOrElse { false }

                outcome.file.delete()

                if (!wrote) {
                    Logger.w(TAG, "could not write the smaller copy of ${entry.displayName}")
                    return running.copy(failed = running.failed + 1)
                }

                entryDao.markProxied(entry.id, outcome.sizeBytes)
                val reclaimed = (entry.sizeBytes - outcome.sizeBytes).coerceAtLeast(0)
                Logger.i(
                    TAG,
                    "optimised ${entry.displayName}: ${entry.sizeBytes} -> ${outcome.sizeBytes} bytes"
                )
                running.copy(
                    optimised = running.optimised + 1,
                    reclaimedBytes = running.reclaimedBytes + reclaimed
                )
            }
        }
    }

    /**
     * Optimises what is eligible right now, or explains why it did nothing.
     *
     * Reads every gate itself rather than taking them as parameters: the master switch, the video
     * toggle and the age. A caller that had to assemble those correctly would be a second place for
     * them to be assembled wrongly.
     *
     * **No optimise cutoff here.** Ian, 6 Sept 2026: *"Once this ONE TIME backup has been completed
     * the user then sets their preference for how the backup/sync works going forward."* The install
     * choice governs the one-time pass and nothing after it, so a cutoff written by Gate 2 must not
     * still be narrowing what the ongoing pass will touch months later. Until this date it did, and
     * only for video — the photo query never read it — so a #3 install left videos already in
     * OneDrive permanently exempt while their photos were optimised as normal.
     *
     * This is CLAUDE.md's three-areas rule in the direction that is easiest to miss: Area 1 must not
     * reach into Area 2's behaviour any more than it may write Area 2's settings.
     */
    suspend fun run(limit: Int = DEFAULT_LIMIT): VideoOptimiseResult = withContext(dispatcher) {
        val prefs = settings.current()

        if (!prefs.isOptimiseEnabled || !prefs.optimiseVideo) {
            Logger.d(TAG, "video optimising is switched off")
            return@withContext VideoOptimiseResult()
        }

        val candidates = entryDao.videoOptimiseCandidates(
            modifiedBeforeEpochSeconds = cutoffSecondsFor(prefs.videoOptimiseAge),
            cutoffMillis = OptimiseCutoff.EVERYTHING,
            limit = limit
        )

        if (candidates.isEmpty()) {
            Logger.d(TAG, "no video is eligible for optimising")
            return@withContext VideoOptimiseResult()
        }

        Logger.i(TAG, "optimising up to ${candidates.size} clips at ${prefs.videoQuality}")

        var result = VideoOptimiseResult()
        for (entry in candidates) {
            // A run cut short must not leave a half-written clip. The transcoder cancels its
            // Transformer and deletes its output; nothing has been overwritten by this point.
            coroutineContext.ensureActive()
            result = optimise(entry, prefs.videoQuality, result)
        }

        Logger.i(
            TAG,
            "video optimising finished: ${result.optimised} optimised, " +
                "${result.reclaimedBytes} bytes reclaimed, ${result.skipped} not worth it, " +
                "${result.failed} failed, ${result.notCovered} outside a granted folder"
        )
        result
    }

    private suspend fun optimise(
        entry: BackupEntryEntity,
        quality: com.gallery.sync.domain.backup.VideoQuality,
        running: VideoOptimiseResult
    ): VideoOptimiseResult {
        val uri = Uri.parse(entry.contentUri)

        // Asked before transcoding rather than after. Spending seconds of encode on a clip we then
        // cannot write would be the most expensive way to discover it.
        if (!safWriter.covers(listOf(entry.contentUri))) {
            Logger.d(TAG, "${entry.displayName} is outside every granted folder; leaving it")
            return running.copy(notCovered = running.notCovered + 1)
        }

        return when (val outcome = transcoder.transcode(uri, entry.displayName, quality)) {
            is TranscodeResult.NotWorthwhile -> {
                // Permanent. Recorded so the candidate count reaches zero instead of offering the
                // same clip forever - the schema 5 lesson from photos, and it covers "this phone
                // cannot decode it" as well as "already small enough".
                Logger.d(TAG, "${entry.displayName}: ${outcome.reason}")
                entryDao.markProxySkipped(entry.id)
                running.copy(skipped = running.skipped + 1)
            }

            is TranscodeResult.Failed -> {
                Logger.w(TAG, "${entry.displayName} failed to transcode: ${outcome.reason}")
                running.copy(failed = running.failed + 1)
            }

            is TranscodeResult.Created -> {
                val wrote = safWriter.writeTruncating(uri) { out ->
                    outcome.file.inputStream().use { it.copyTo(out) }
                }
                outcome.file.delete()

                if (!wrote) {
                    Logger.w(TAG, "could not write the smaller copy of ${entry.displayName}")
                    return running.copy(failed = running.failed + 1)
                }

                // NOT STAMPED, and this is the one hole left in the feature.
                //
                // A photo proxy carries its claim in EXIF, so the app can recognise it from the file
                // alone - which is the whole argument in ProxyMarker: the ledger is wiped by an
                // uninstall, absent on a new phone, and has been seen going stale. An MP4 has no
                // equivalent that can be set afterwards. The `©wrt` writer field has to be written
                // as the container is muxed, inside the transcode, and whether Media3's muxer can
                // emit the atom that METADATA_KEY_WRITER reads is unverified.
                //
                // So a transcoded clip is identifiable only from `isProxied` on its ledger row.
                // Restore works today, because restorableProxies() reads exactly that. What breaks
                // is the case ProxyMarker exists for: a reinstall leaves smaller copies on the phone
                // that nothing can recognise as proxies, and their full-quality originals sit in
                // OneDrive unoffered.
                //
                // Deliberately not papered over with a no-op stamp - ProxyMarker says why, and it is
                // right: a file that claims to be marked and is not is worse than one honestly
                // unmarked. Needs its own investigation before this ships.

                // SafMediaWriter rescans after every write, so MediaStore picks up the new size
                // without a second call here.
                entryDao.markProxied(entry.id, outcome.sizeBytes)

                val reclaimed = (entry.sizeBytes - outcome.sizeBytes).coerceAtLeast(0)
                Logger.i(
                    TAG,
                    "optimised ${entry.displayName}: ${entry.sizeBytes} -> ${outcome.sizeBytes} bytes"
                )
                running.copy(
                    optimised = running.optimised + 1,
                    reclaimedBytes = running.reclaimedBytes + reclaimed
                )
            }
        }
    }

    /**
     * The newest modification time a clip may have and still be eligible.
     *
     * In seconds, because that is what `dateModifiedEpochSeconds` holds. [MediaAge.Immediately]
     * yields "now", which lets everything through — including a clip shot this morning, which is
     * the documented cost of that option.
     */
    private fun cutoffSecondsFor(age: MediaAge): Long =
        Instant.now().minus(age.duration).epochSecond

    private companion object {
        const val TAG = "VideoOptimiser"

        /**
         * Clips per run.
         *
         * Smaller than the photo batch on purpose: a clip is seconds to minutes of encoding against
         * a photo's fraction of a second, and a run that outlasts its execution window is a run that
         * achieves nothing. Ten at roughly 0.15x realtime is a few minutes of work.
         */
        const val DEFAULT_LIMIT = 10

        /** Wizard pass handles more clips since it runs with the user watching. */
        const val WIZARD_LIMIT = 500
    }
}
