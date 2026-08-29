package com.gallery.sync.video

import android.net.Uri
import android.util.Log
import androidx.media3.common.MediaItem
import com.google.common.collect.ImmutableList
import androidx.media3.common.MimeTypes
import androidx.media3.effect.Presentation
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.system.measureTimeMillis

/**
 * How long does downscaling real 8K footage actually take, and what does it produce?
 *
 * ### Why this is a test and not a feature
 *
 * TASK-013 has been blocked since 19 Aug 2026 on one number. MILESTONES: *"Old video may be
 * downscaled full-length … Needs Media3 Transformer and a transcode cost measured on real 8K footage
 * before committing."* Everything else about video downscaling is decided — the age setting, that
 * recent video is never touched, that truncation is rejected. What nobody has is the cost.
 *
 * So this measures before the app takes on a media stack. Media3 is an `androidTest` dependency
 * only, and stays that way unless the number below says the feature is affordable.
 *
 * ### What it does not assert
 *
 * Almost nothing. A measurement that fails the build when a phone is slow is a worse measurement.
 * It fails only if the transcode itself errors — which is its own finding, because a device that
 * cannot decode 8K at all tells us the feature is device-dependent before a line of it is written.
 *
 * ### Running it
 *
 * Needs the sample pushed to the device first:
 *
 * ```
 * adb -s <serial> push 8k.mp4 /sdcard/Download/8k-sample.mp4
 * adb -s <serial> shell am instrument -w -e class \
 *   com.gallery.sync.video.VideoTranscodeCostTest \
 *   com.gallery.sync.test/androidx.test.runner.AndroidJUnitRunner
 * ```
 *
 * Skips itself when the sample is absent, so a normal `connectedAndroidTest` run is unaffected.
 */
class VideoTranscodeCostTest {

    /**
     * The target app's context, and it has to be.
     *
     * Two constraints meet here, and only a real dependency satisfies both. Transformer needs an
     * application context — the instrumentation context has none, and building with it throws an
     * NPE inside `DefaultEncoderFactory`. And Media3 loads its GLSL shaders as assets from whichever
     * context it is handed, so those assets must be in the same APK. Media3 as an `androidTest`
     * dependency fails one way or the other whichever context is used, which is why it is now an
     * `implementation` dependency — provisionally, pending this very measurement.
     */
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun measureEightKDownscale() {
        val input = File("/sdcard/Download/8k-sample.mp4")
        assumeTrue("no 8K sample on this device at ${input.path}", input.exists())

        val output = File(context.cacheDir, "8k-downscaled.mp4").also { it.delete() }

        var result: ExportResult? = null
        var failure: ExportException? = null
        val done = CountDownLatch(1)

        val elapsedMs = measureTimeMillis {
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                val transformer = Transformer.Builder(context)
                    .setVideoMimeType(MimeTypes.VIDEO_H264)
                    .addListener(object : Transformer.Listener {
                        override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                            result = exportResult
                            done.countDown()
                        }

                        override fun onError(
                            composition: Composition,
                            exportResult: ExportResult,
                            exportException: ExportException
                        ) {
                            failure = exportException
                            done.countDown()
                        }
                    })
                    .build()

                // Long edge to 1080. Presentation scales while preserving aspect, which matters for
                // the portrait video phones actually produce.
                val item = EditedMediaItem.Builder(MediaItem.fromUri(Uri.fromFile(input)))
                    .setEffects(Effects(emptyList(), listOf(Presentation.createForShortSide(1080))))
                    .build()

                // Tone-map to SDR rather than keeping HDR.
                //
                // The first run failed here, and the reason is worth keeping: Samsung's 8K is HEVC
                // with a PQ transfer and HDR10+ metadata, and H.264 cannot carry HDR10 at all. The
                // default HDR_MODE_KEEP_HDR against an H.264 target is a contradiction the pipeline
                // reports as "Video frame processing error", which names the symptom and not the
                // cause. Tone-mapping is also the right product choice: a downscaled clip is for
                // watching, and SDR H.264 plays everywhere.
                val composition = Composition.Builder(
                    ImmutableList.of(EditedMediaItemSequence.Builder(item).build())
                ).setHdrMode(Composition.HDR_MODE_TONE_MAP_HDR_TO_SDR_USING_OPEN_GL).build()

                transformer.start(composition, output.absolutePath)
            }

            // Generous: this is a measurement, and an 8K transcode being slow is the finding rather
            // than a reason to give up on it.
            check(done.await(30, TimeUnit.MINUTES)) { "transcode did not finish within 30 minutes" }
        }

        val inMb = input.length() / 1_000_000.0
        val outMb = output.length() / 1_000_000.0

        Log.i(
            TAG,
            failure?.let { "TRANSCODE FAILED after ${elapsedMs}ms: ${it.errorCodeName} — ${it.message}" }
                ?: buildString {
                    append("TRANSCODE OK: ")
                    append("${"%.1f".format(inMb)} MB in, ${"%.1f".format(outMb)} MB out ")
                    append("(${"%.1f".format(inMb / outMb.coerceAtLeast(0.001))}x smaller), ")
                    append("${elapsedMs}ms elapsed, ")
                    append("${"%.2f".format(elapsedMs / 1000.0)}s for ")
                    append("${result?.durationMs ?: -1}ms of footage, ")
                    append("ratio ${"%.2f".format(elapsedMs.toDouble() / (result?.durationMs ?: 1))}x realtime")
                }
        )

        failure?.let { throw AssertionError("transcode failed: ${it.errorCodeName} — ${it.message}", it) }
    }

    private companion object {
        const val TAG = "TranscodeCost"
    }
}
