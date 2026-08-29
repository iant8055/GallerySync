package com.gallery.sync.video

import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.Metadata
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
import androidx.test.platform.app.InstrumentationRegistry
import com.gallery.sync.data.local.media.ProxyKind
import com.gallery.sync.data.local.media.ProxyMarker
import com.gallery.sync.data.local.media.TranscodeResult
import com.gallery.sync.data.local.media.VideoTranscoder
import com.google.common.collect.ImmutableList
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.nio.charset.Charset
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Guards the video proxy marker end to end, on a real device.
 *
 * The mechanism it rests on was settled here on the Fold 4 and Moto G, 29 Aug 2026: Media3's muxer
 * cannot emit an MP4 `©wrt` atom, so the marker is written as a custom `mdta` key/value
 * ([MdtaMetadataEntry]) through the muxer's [InAppMp4Muxer.MetadataProvider] hook, and read back by
 * [ProxyMarker] walking the `moov` box — no `MediaMetadataRetriever`, which cannot see an `mdta` key,
 * and no `media3-exoplayer`. `moov` sits before `mdat`, so the alternative of appending a `©wrt` box
 * afterward would have meant rewriting every chunk offset; it was rejected.
 *
 * Two tests: [transcoderStampsTheClipItProduces] drives the shipping [VideoTranscoder] and confirms
 * [ProxyMarker] then recognises its output; [mdtaMarkerIsMuxedAndReadWithoutMmr] muxes the marker
 * directly to keep the mechanism findings visible — the box order, and that the retired
 * `MediaMetadataRetriever` path still cannot see the marker.
 *
 * The sample rides in the test APK's assets, and everything stays in the app cache dir, so no storage
 * permission is in play — scoped storage otherwise denies Media3's FileDataSource a raw `/sdcard`
 * path. Run just this class:
 * ```
 * adb -s <serial> shell am instrument -w -e class \
 *   com.gallery.sync.video.VideoMarkerProbeTest \
 *   com.gallery.sync.test/androidx.test.runner.AndroidJUnitRunner
 * ```
 */
class VideoMarkerProbeTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val marker = ProxyMarker(context)

    private fun sampleInCache(): File = File(context.cacheDir, "marker-probe-input.mp4").also { dst ->
        InstrumentationRegistry.getInstrumentation().context.assets
            .open("marker-probe-input.mp4").use { asset ->
                dst.outputStream().use { asset.copyTo(it) }
            }
    }

    /** The shipping path: [VideoTranscoder] stamps as it muxes, [ProxyMarker] recognises the result. */
    @Test
    fun transcoderStampsTheClipItProduces() = kotlinx.coroutines.runBlocking {
        val input = sampleInCache()
        assumeTrue("sample did not materialise", input.exists() && input.length() > 0)

        val transcoder = VideoTranscoder(context, marker, kotlinx.coroutines.Dispatchers.IO)
        val result = transcoder.transcode(Uri.fromFile(input), input.name)
        Log.i(TAG, "transcode result = $result")

        // The 720p sample downscales on every device here; a phone that somehow refuses it is not
        // evidence of a marker regression.
        assumeTrue("transcode did not produce a file: $result", result is TranscodeResult.Created)
        val produced = (result as TranscodeResult.Created).file

        val kind = marker.kindOf(Uri.fromFile(produced))
        Log.i(TAG, "transcoder output ${produced.length()} bytes, kindOf = $kind")
        assertTrue("a freshly transcoded clip must read back as a proxy", marker.isProxy(Uri.fromFile(produced)))
        assertEquals("and specifically as a transcoded video", ProxyKind.VideoTranscoded, kind)
    }

    /** The mechanism, kept visible: marker muxed directly, box order and the retired MMR path logged. */
    @Test
    fun mdtaMarkerIsMuxedAndReadWithoutMmr() {
        val input = sampleInCache()
        assumeTrue("sample did not materialise", input.exists() && input.length() > 0)

        val expected = marker.videoStampValue(ProxyKind.VideoTranscoded)
        val output = File(context.cacheDir, "marker-probe-output.mp4").also { it.delete() }

        transcodeWithMarker(input, output, expected)

        assumeTrue("transcode produced nothing", output.exists() && output.length() > 0)
        Log.i(TAG, "OUTPUT ${output.length()} bytes at ${output.absolutePath}")

        // The production reader now sees the mdta marker; MediaMetadataRetriever still cannot.
        val detected = marker.isProxy(Uri.fromFile(output))
        val writerViaMmr = MediaMetadataRetriever().let { r ->
            try {
                r.setDataSource(output.absolutePath)
                r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_WRITER)
            } finally {
                r.release()
            }
        }
        Log.i(TAG, "ProxyMarker.isProxy = $detected (expect true)")
        Log.i(TAG, "MMR METADATA_KEY_WRITER = ${writerViaMmr ?: "<null>"} (expect <null>, mdta is invisible to it)")

        // The dependency-free walk, kept as corroboration of the shipping reader.
        val bytes = output.readBytes()
        val order = topLevelBoxTypes(bytes)
        val keyPresent = String(bytes, Charsets.ISO_8859_1).contains(MDTA_KEY)
        val recoveredValue = readMdtaStringValue(bytes)

        Log.i(TAG, "mdta key '$MDTA_KEY' present = $keyPresent, recovered value = ${recoveredValue ?: "<none>"}")
        Log.i(TAG, "top-level box order = $order")

        assertTrue("marker must survive the mux", keyPresent)
        assertEquals("and read back exactly", expected, recoveredValue)
        assertTrue("production reader must detect it", detected)
        assertEquals("MMR must remain blind to the mdta marker", null, writerViaMmr)
    }

    private fun transcodeWithMarker(input: File, output: File, markerValue: String) {
        var failure: ExportException? = null
        val done = CountDownLatch(1)

        val metadataProvider = InAppMp4Muxer.MetadataProvider { entries ->
            entries.add(
                MdtaMetadataEntry(
                    MDTA_KEY,
                    markerValue.toByteArray(Charset.forName("UTF-8")),
                    MdtaMetadataEntry.TYPE_INDICATOR_STRING
                )
            )
        }

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val transformer = Transformer.Builder(context)
                .setVideoMimeType(MimeTypes.VIDEO_H264)
                .setMuxerFactory(InAppMp4Muxer.Factory(metadataProvider))
                .addListener(object : Transformer.Listener {
                    override fun onCompleted(composition: Composition, result: ExportResult) {
                        done.countDown()
                    }

                    override fun onError(
                        composition: Composition,
                        result: ExportResult,
                        exception: ExportException
                    ) {
                        failure = exception
                        done.countDown()
                    }
                })
                .build()

            val item = EditedMediaItem.Builder(MediaItem.fromUri(Uri.fromFile(input)))
                .setEffects(Effects(emptyList(), listOf(Presentation.createForShortSide(480))))
                .build()

            val composition = Composition.Builder(
                ImmutableList.of(EditedMediaItemSequence.Builder(item).build())
            ).setHdrMode(Composition.HDR_MODE_TONE_MAP_HDR_TO_SDR_USING_OPEN_GL).build()

            transformer.start(composition, output.absolutePath)
        }

        check(done.await(5, TimeUnit.MINUTES)) { "transcode did not finish in 5 minutes" }
        failure?.let { throw AssertionError("transcode failed: ${it.errorCodeName} — ${it.message}", it) }
    }

    // --- A minimal MP4 box walk: enough to prove a dependency-free reader, not a general parser. ---

    /** Types of the top-level boxes, in file order. 32-bit sizes only; a `largesize` box ends it. */
    private fun topLevelBoxTypes(b: ByteArray): List<String> {
        val types = mutableListOf<String>()
        var p = 0
        while (p + 8 <= b.size) {
            val size = u32(b, p)
            val type = ascii(b, p + 4)
            types.add(type)
            if (size < 8L) break
            p += size.toInt()
        }
        return types
    }

    /**
     * The value of the mdta string entry, recovered from moov → … → ilst → item → data.
     *
     * The entry's key and value live in different boxes: the string `com.gallery.sync.proxy` is in
     * `moov/meta/keys`, and its value is in `moov/meta/ilst/<index>/data`. Rather than model every
     * intermediate box (meta's FullBox-vs-QuickTime ambiguity, the numeric index item), this locates
     * `ilst` inside moov and then the `data` box inside it by fourcc, which is all a real reader needs
     * for a single custom key — and proves the reader owes nothing to media3-exoplayer.
     */
    private fun readMdtaStringValue(b: ByteArray): String? {
        val moov = findFourcc(b, 0, b.size, "moov") ?: return null
        val moovEnd = (moov.first + moov.second).toInt().coerceAtMost(b.size)
        val ilst = findFourcc(b, moov.first + 8, moovEnd, "ilst") ?: return null
        val ilstEnd = (ilst.first + ilst.second).toInt().coerceAtMost(b.size)
        val data = findFourcc(b, ilst.first + 8, ilstEnd, "data") ?: return null
        // data box: [size:4][type='data'][typeIndicator:4][locale:4][value...]
        val valueStart = data.first + 16
        val valueEnd = (data.first + data.second).toInt().coerceAtMost(b.size)
        if (valueStart >= valueEnd) return null
        return String(b, valueStart, valueEnd - valueStart, Charset.forName("UTF-8"))
    }

    /**
     * The first box of [type] whose header sits within [start, end), located by its fourcc. Returns
     * the box start (the size field) and its declared size. A fourcc is distinctive enough that a
     * scan needs no per-container box modelling for the one entry this probe writes.
     */
    private fun findFourcc(b: ByteArray, start: Int, end: Int, type: String): Pair<Int, Long>? {
        val fourcc = type.toByteArray(Charsets.ISO_8859_1)
        var p = start
        val limit = minOf(end, b.size) - 4
        while (p <= limit) {
            if (b[p] == fourcc[0] && b[p + 1] == fourcc[1] &&
                b[p + 2] == fourcc[2] && b[p + 3] == fourcc[3] && p >= 4
            ) {
                val boxStart = p - 4
                val size = u32(b, boxStart)
                if (size in 8L..(b.size - boxStart).toLong()) return boxStart to size
            }
            p++
        }
        return null
    }

    private fun u32(b: ByteArray, o: Int): Long =
        ((b[o].toLong() and 0xFF) shl 24) or
            ((b[o + 1].toLong() and 0xFF) shl 16) or
            ((b[o + 2].toLong() and 0xFF) shl 8) or
            (b[o + 3].toLong() and 0xFF)

    private fun ascii(b: ByteArray, o: Int): String = String(b, o, 4, Charsets.ISO_8859_1)

    private companion object {
        const val TAG = "MarkerProbe"
        const val MDTA_KEY = "com.gallery.sync.proxy"
    }
}
