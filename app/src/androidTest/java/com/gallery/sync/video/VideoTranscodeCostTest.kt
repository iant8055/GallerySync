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
        assumeTrue("no 8K sample at ${input.path}", input.exists())
        report(transcode(input, shortSide = 1080, label = "8K->1080"))
    }

    /**
     * What downscaling buys on ordinary 1080p footage, which is what most phones actually shoot.
     *
     * Ian, 28 Aug 2026: *"maybe testing what a 1080p video looks like when it is transcoded down to
     * 720, 540 or even 480p and how much room that would save."* The 8K number is spectacular and
     * unrepresentative — most libraries are not 8K, and a phone that only shoots 1080p has far less
     * obvious headroom.
     *
     * **1080 is in the sweep deliberately.** Re-encoding at the same resolution changes nothing about
     * the picture's size and everything about its bitrate, and the sample here is ~30 Mbps. If most
     * of the saving comes from the bitrate rather than the pixels, the honest feature is "re-encode"
     * and not "downscale" — and it would cost far less quality than dropping to 480p.
     *
     * Outputs are left on the device so they can be watched. **Numbers cannot answer the half of the
     * question that matters**, which is what 540p looks like on the phone in your hand.
     */
    @Test
    fun measureTenEightyDownscaleSweep() {
        val input = File("/sdcard/Download/1080p-sample.mp4")
        assumeTrue("no 1080p sample at ${input.path}", input.exists())

        listOf(1080, 720, 540, 480).forEach { shortSide ->
            report(transcode(input, shortSide, label = "1080->$shortSide"))
        }
    }

    private data class Result(
        val label: String,
        val inBytes: Long,
        val outBytes: Long,
        val elapsedMs: Long,
        val footageMs: Long,
        val output: File
    )

    private fun transcode(input: File, shortSide: Int, label: String): Result {
        // Somewhere adb can reach without run-as, so the results can be watched rather than only
        // counted.
        val outDir = File("/sdcard/Download/transcode-samples").also { it.mkdirs() }
        val output = File(outDir, "${input.nameWithoutExtension}-${shortSide}p.mp4").also { it.delete() }

        var result: ExportResult? = null
        var failure: ExportException? = null
        val done = CountDownLatch(1)

        val elapsed = measureTimeMillis {
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

                val item = EditedMediaItem.Builder(MediaItem.fromUri(Uri.fromFile(input)))
                    .setEffects(Effects(emptyList(), listOf(Presentation.createForShortSide(shortSide))))
                    .build()

                // Tone-map to SDR rather than keeping HDR.
                //
                // Samsung's 8K is HEVC with a PQ transfer and HDR10+ metadata, and H.264 cannot carry
                // HDR10 at all. The default HDR_MODE_KEEP_HDR against an H.264 target is a
                // contradiction the pipeline reports as "Video frame processing error", naming the
                // symptom and not the cause. Harmless for SDR input, required for HDR.
                val composition = Composition.Builder(
                    ImmutableList.of(EditedMediaItemSequence.Builder(item).build())
                ).setHdrMode(Composition.HDR_MODE_TONE_MAP_HDR_TO_SDR_USING_OPEN_GL).build()

                transformer.start(composition, output.absolutePath)
            }

            check(done.await(30, TimeUnit.MINUTES)) { "$label did not finish within 30 minutes" }
        }

        failure?.let { throw AssertionError("$label failed: ${it.errorCodeName} — ${it.message}", it) }

        return Result(label, input.length(), output.length(), elapsed, result?.durationMs ?: 0, output)
    }

    private fun report(r: Result) {
        val inMb = r.inBytes / 1_000_000.0
        val outMb = r.outBytes / 1_000_000.0
        Log.i(
            TAG,
            "RESULT ${r.label}: ${"%.1f".format(inMb)} MB -> ${"%.1f".format(outMb)} MB " +
                "(${"%.1f".format(inMb / outMb.coerceAtLeast(0.001))}x smaller, " +
                "${"%.0f".format(100 - 100 * outMb / inMb)}% saved), " +
                "${r.elapsedMs}ms for ${r.footageMs}ms " +
                "(${"%.2f".format(r.elapsedMs.toDouble() / r.footageMs.coerceAtLeast(1))}x realtime) " +
                "-> ${r.output.absolutePath}"
        )
    }

    private companion object {
        const val TAG = "TranscodeCost"
    }
}
