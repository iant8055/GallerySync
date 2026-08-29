package com.gallery.sync.data.local.media

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.container.MdtaMetadataEntry
import androidx.media3.effect.Presentation
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.InAppMp4Muxer
import androidx.media3.transformer.Transformer
import com.gallery.sync.di.IoDispatcher
import com.gallery.sync.domain.backup.VideoQuality
import com.gallery.sync.util.Logger
import com.google.common.collect.ImmutableList
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.math.abs

/** What came back from asking for a smaller copy of a clip. */
sealed interface TranscodeResult {

    data class Created(val file: File, val sizeBytes: Long) : TranscodeResult

    /**
     * Nothing to gain, ever. Already at or under the target, already a proxy, or this phone cannot
     * decode the format at all.
     *
     * Permanent, exactly as `ProxyResult.NotWorthwhile` is: the clip stops being offered rather than
     * being retried forever. Collapsing this into [Failed] is what made the photo candidate count
     * stick above zero before schema 5, and the same trap is here.
     */
    data class NotWorthwhile(val reason: String) : TranscodeResult

    /** Could not be read or encoded this time. Worth another attempt. */
    data class Failed(val reason: String) : TranscodeResult
}

/**
 * Produces the downscaled clip that will replace a video whose original is safely in OneDrive.
 *
 * Mirrors `ProxyGenerator` deliberately, including the part that matters most: **it writes only into
 * app cache and overwrites nothing**. The destructive half belongs elsewhere, so the risky code stays
 * small and this stays testable.
 *
 * ### What the measurements settled, 28 Aug 2026
 *
 * Numbers in MILESTONES; the ones that shaped this class:
 *
 * - **480 on the short edge**, not the 1080p this task originally proposed. On an 18-second daylight
 *   clip it saved **88%** against 47% for a same-resolution re-encode, and Ian could not tell them
 *   apart on the Fold's inner display — 2176x1812, where 480p upscales about 2.5x. The spec said the
 *   resolution was "to be confirmed by the measurement". This is the measurement.
 * - **Tone-mapping to SDR is required, not optional.** Samsung's 8K is HEVC with a PQ transfer and
 *   HDR10+ metadata, and H.264 cannot carry HDR10. The default `HDR_MODE_KEEP_HDR` against an H.264
 *   target fails as a bare "Video frame processing error" with the real cause three levels down.
 * - **Cost is not the constraint.** 0.11-0.19x realtime for 1080p input, 0.5x for 8K. Whether the
 *   decoder exists at all is the constraint, which is why [VideoCapability] is consulted first.
 */
@Singleton
class VideoTranscoder @Inject constructor(
    @ApplicationContext private val context: Context,
    private val marker: ProxyMarker,
    @param:IoDispatcher private val dispatcher: CoroutineDispatcher
) {

    /**
     * Transcodes [uri] into app cache, or explains why it will not.
     *
     * Every refusal is decided before a frame is decoded. Starting a transcode is the expensive act
     * and all of these answers are cheap.
     */
    suspend fun transcode(
        uri: Uri,
        displayName: String,
        quality: VideoQuality = VideoQuality.DEFAULT
    ): TranscodeResult =
        withContext(dispatcher) {
            if (marker.isProxy(uri)) {
                return@withContext TranscodeResult.NotWorthwhile("already a proxy")
            }

            val source = readSource(uri)
                ?: return@withContext TranscodeResult.Failed("could not read video metadata")

            if (source.shortSide in 1..quality.targetShortSide) {
                return@withContext TranscodeResult.NotWorthwhile(
                    "already ${source.width}x${source.height}, at or under the target"
                )
            }

            // Ask before spending anything. On a phone whose decoder tops out below this clip the
            // transcode does not run slowly, it throws - measured on the Moto G, 28 Aug 2026.
            if (!VideoCapability.canDecode(source.mimeType, source.width, source.height)) {
                return@withContext TranscodeResult.NotWorthwhile(
                    "this phone cannot decode ${source.width}x${source.height} ${source.mimeType}"
                )
            }

            val output = File(context.cacheDir, "transcode-${displayName.hashCode()}.mp4")
                .also { it.delete() }

            when (val outcome = runTransformer(uri, output, quality)) {
                is TranscodeResult.Created -> validate(outcome, source, displayName)
                else -> outcome
            }
        }

    /**
     * Checks the output before anything is allowed to be replaced by it.
     *
     * TASK-013 rule 2, and it matters more for video than for photos: a truncated clip fails
     * silently inside an editor and is discovered in the exported result, long after the original
     * has gone. Duration is the test that catches it, because a transcode cut short produces a
     * perfectly valid file that is simply too short.
     */
    private fun validate(
        created: TranscodeResult.Created,
        source: SourceInfo,
        displayName: String
    ): TranscodeResult {
        if (created.sizeBytes <= 0L) {
            created.file.delete()
            return TranscodeResult.Failed("transcode produced an empty file")
        }

        val out = readSource(Uri.fromFile(created.file))
        if (out == null) {
            created.file.delete()
            return TranscodeResult.Failed("transcode output could not be read back")
        }

        val drift = abs(out.durationMs - source.durationMs)
        if (drift > DURATION_TOLERANCE_MS) {
            created.file.delete()
            return TranscodeResult.Failed(
                "duration drifted ${drift}ms (${source.durationMs} -> ${out.durationMs})"
            )
        }

        if (created.sizeBytes >= source.sizeBytes) {
            // Re-encoding can grow a file that was already efficiently compressed. Photo proxying
            // hit exactly this on 26 Aug 2026 - 404 KB in, 490 KB out - and spending quality to gain
            // nothing is worse than doing nothing at all.
            created.file.delete()
            return TranscodeResult.NotWorthwhile("transcode came out no smaller")
        }

        Logger.i(
            TAG,
            "transcoded $displayName: ${source.sizeBytes} -> ${created.sizeBytes} bytes, " +
                "${source.width}x${source.height} -> ${out.width}x${out.height}"
        )
        return created
    }

    private suspend fun runTransformer(
        input: Uri,
        output: File,
        quality: VideoQuality
    ): TranscodeResult =
        withContext(Dispatchers.Main) {
            suspendCancellableCoroutine { continuation ->
                val transformer = Transformer.Builder(context)
                    .setVideoMimeType(MimeTypes.VIDEO_H264)
                    // Stamp the clip as a proxy as the container is muxed — the only moment an MP4's
                    // metadata can be set. Written as an mdta key so the reader needs no
                    // MediaMetadataRetriever; see ProxyMarker and VideoMarkerProbeTest.
                    .setMuxerFactory(
                        InAppMp4Muxer.Factory { entries ->
                            entries.add(
                                MdtaMetadataEntry(
                                    ProxyMarker.MDTA_KEY,
                                    marker.videoStampValue(ProxyKind.VideoTranscoded)
                                        .toByteArray(Charsets.UTF_8),
                                    MdtaMetadataEntry.TYPE_INDICATOR_STRING
                                )
                            )
                        }
                    )
                    .addListener(object : Transformer.Listener {
                        override fun onCompleted(composition: Composition, result: ExportResult) {
                            if (continuation.isActive) {
                                continuation.resume(TranscodeResult.Created(output, output.length()))
                            }
                        }

                        override fun onError(
                            composition: Composition,
                            result: ExportResult,
                            exception: ExportException
                        ) {
                            output.delete()
                            if (continuation.isActive) {
                                continuation.resume(
                                    TranscodeResult.Failed(
                                        "${exception.errorCodeName}: ${exception.message}"
                                    )
                                )
                            }
                        }
                    })
                    .build()

                val item = EditedMediaItem.Builder(MediaItem.fromUri(input))
                    .setEffects(
                        Effects(
                            emptyList(),
                            listOf(Presentation.createForShortSide(quality.targetShortSide))
                        )
                    )
                    .build()

                // Required for HDR sources, harmless for SDR ones. See the class comment.
                val composition = Composition.Builder(
                    ImmutableList.of(EditedMediaItemSequence.Builder(item).build())
                ).setHdrMode(Composition.HDR_MODE_TONE_MAP_HDR_TO_SDR_USING_OPEN_GL).build()

                transformer.start(composition, output.absolutePath)

                continuation.invokeOnCancellation {
                    runCatching { transformer.cancel() }
                    output.delete()
                }
            }
        }

    private data class SourceInfo(
        val width: Int,
        val height: Int,
        val durationMs: Long,
        val sizeBytes: Long,
        val mimeType: String
    ) {
        val shortSide: Int get() = minOf(width, height)
    }

    private fun readSource(uri: Uri): SourceInfo? = runCatching {
        MediaMetadataRetriever().use { retriever ->
            retriever.setDataSource(context, uri)

            fun value(key: Int) = retriever.extractMetadata(key)

            // Rotation is folded in here rather than carried separately. A portrait clip is usually
            // stored landscape with a 90-degree flag, and the target is expressed on the short side
            // - so ignoring rotation would downscale the wrong axis.
            val rotation =
                value(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
            val rawWidth = value(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
            val rawHeight = value(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
            val swapped = rotation == 90 || rotation == 270

            SourceInfo(
                width = if (swapped) rawHeight else rawWidth,
                height = if (swapped) rawWidth else rawHeight,
                durationMs = value(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L,
                sizeBytes = context.contentResolver.openFileDescriptor(uri, "r")
                    ?.use { it.statSize } ?: 0L,
                mimeType = codecMimeType(uri) ?: MimeTypes.VIDEO_H265
            )
        }
    }.getOrNull()

    /**
     * The **codec** mime of the video track, which is not what the retriever reports.
     *
     * `METADATA_KEY_MIMETYPE` gives the *container* — `video/mp4` — and no decoder on earth
     * advertises support for that. Asking [VideoCapability] with it returns false for every clip on
     * every device, so every video is written off as permanently un-transcodable and the feature
     * quietly does nothing while appearing to work.
     *
     * Caught on the Fold 4, 28 Aug 2026, by running the real class against a real file: the phone
     * that had just transcoded 8K refused a 1080p clip it demonstrably handles. The permanence of
     * `NotWorthwhile` is what would have made it expensive — those clips never come back for a
     * retry.
     */
    private fun codecMimeType(uri: Uri): String? = runCatching {
        val extractor = android.media.MediaExtractor()
        try {
            context.contentResolver.openFileDescriptor(uri, "r")?.use {
                extractor.setDataSource(it.fileDescriptor)
            } ?: return null

            (0 until extractor.trackCount)
                .asSequence()
                .map { extractor.getTrackFormat(it) }
                .mapNotNull { it.getString(android.media.MediaFormat.KEY_MIME) }
                .firstOrNull { it.startsWith("video/") }
        } finally {
            extractor.release()
        }
    }.getOrNull()

    private companion object {
        const val TAG = "VideoTranscoder"

        /** Half a second: enough for container rounding, nowhere near enough to hide a truncation. */
        const val DURATION_TOLERANCE_MS = 500L
    }
}
