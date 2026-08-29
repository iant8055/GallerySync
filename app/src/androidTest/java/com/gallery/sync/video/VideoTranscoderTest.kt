package com.gallery.sync.video

import android.net.Uri
import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import com.gallery.sync.data.local.media.ProxyMarker
import com.gallery.sync.data.local.media.TranscodeResult
import com.gallery.sync.data.local.media.VideoCapability
import com.gallery.sync.data.local.media.VideoTranscoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * The production transcoder, on a real device, against a real clip.
 *
 * Distinct from `VideoTranscodeCostTest`, which drives Media3 directly to answer "what does this
 * cost?". This one asks "does the class we ship actually work, and does it refuse correctly?" — and
 * the refusal half is as important as the success half, because the two devices here disagree.
 *
 * Expected outcomes, both correct:
 * - **Fold 4** — [TranscodeResult.Created], materially smaller than the source
 * - **Moto G 2026** — [TranscodeResult.NotWorthwhile], because its decoders stop at 2560x1440
 *
 * Needs a sample pushed first:
 * ```
 * adb -s <serial> push clip.mp4 /sdcard/Download/transcode-input.mp4
 * ```
 */
class VideoTranscoderTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private val transcoder = VideoTranscoder(
        context = context,
        marker = ProxyMarker(context),
        dispatcher = Dispatchers.IO
    )

    @Test
    // Explicit Unit: without it the `when` below makes this method return a value, and JUnit rejects
    // a non-void @Test with a class-level initialisation error rather than anything that names the
    // method.
    fun transcodesOrRefusesWithAReason(): Unit = runBlocking {
        val input = File("/sdcard/Download/transcode-input.mp4")
        assumeTrue("no sample at ${input.path}", input.exists())

        val result = transcoder.transcode(Uri.fromFile(input), input.name)

        when (result) {
            is TranscodeResult.Created -> {
                Log.i(TAG, "CREATED: ${input.length()} -> ${result.sizeBytes} bytes")
                assertTrue(
                    "a transcode that is not smaller should have been refused",
                    result.sizeBytes < input.length()
                )
                assertTrue("output missing", result.file.exists())
            }

            is TranscodeResult.NotWorthwhile ->
                Log.i(TAG, "REFUSED (permanent): ${result.reason}")

            is TranscodeResult.Failed ->
                throw AssertionError("transcode failed: ${result.reason}")
        }
    }

    /**
     * What this device says it can decode, printed rather than asserted.
     *
     * The limits differ per device by design — asserting a number here would pin one phone's
     * hardware into the test suite.
     */
    @Test
    fun reportDecoderLimits() {
        listOf(
            Triple("video/hevc", 7680, 4320),
            Triple("video/hevc", 3840, 2160),
            Triple("video/hevc", 1920, 1080),
            Triple("video/avc", 3840, 2160),
            Triple("video/avc", 1920, 1080)
        ).forEach { (mime, w, h) ->
            Log.i(TAG, "CAPABILITY $mime ${w}x$h -> ${VideoCapability.canDecode(mime, w, h)}")
        }
    }

    private companion object {
        const val TAG = "TranscoderTest"
    }
}
