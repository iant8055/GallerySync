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
import com.google.common.collect.ImmutableList
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.nio.charset.Charset
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Does Option A actually work? Settles the video proxy-marker question on hardware before any
 * production code is written for it.
 *
 * The plan the commit left open assumed the transcode would write an MP4 `©wrt` atom that
 * [MediaMetadataRetriever.METADATA_KEY_WRITER] reads. Media3's muxer cannot emit `©wrt`; it can emit
 * a custom `mdta` key/value ([MdtaMetadataEntry]). This probe writes the marker as an mdta entry via
 * the muxer's [InAppMp4Muxer.MetadataProvider] hook, then checks three things and logs each:
 *
 *  - **R4** — the *current* [ProxyMarker.isProxy] (which reads `©wrt`) still returns false, and MMR's
 *    WRITER key is null. Confirms the read side has to change: an mdta marker is invisible to it.
 *  - **R2/read** — a small, dependency-free `moov/meta/keys+ilst` walk finds the key and value.
 *    Confirms Option A's read side needs no `media3-exoplayer`, just a parser.
 *  - **R5** — the top-level box order (moov before or after mdat), which decides whether the
 *    rejected Option B was even cheap.
 *
 * Nothing is asserted that pins a device; the transcode failing is the only hard failure. Push the
 * sample first, then run just this class:
 * ```
 * adb -s <serial> push marker-probe-input.mp4 /sdcard/Download/marker-probe-input.mp4
 * adb -s <serial> shell am instrument -w -e class \
 *   com.gallery.sync.video.VideoMarkerProbeTest \
 *   com.gallery.sync.test/androidx.test.runner.AndroidJUnitRunner
 * ```
 */
class VideoMarkerProbeTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val marker = ProxyMarker(context)

    @Test
    fun mdtaMarkerSurvivesTheMuxAndIsReadableWithoutMmr() {
        // Everything stays in the app's cache dir, so no storage permission is in play — scoped
        // storage otherwise denies Media3's FileDataSource a raw /sdcard path. The sample rides in
        // the test APK's assets.
        val input = File(context.cacheDir, "marker-probe-input.mp4")
        InstrumentationRegistry.getInstrumentation().context.assets
            .open("marker-probe-input.mp4").use { asset ->
                input.outputStream().use { asset.copyTo(it) }
            }
        assumeTrue("sample asset did not materialise", input.exists() && input.length() > 0)

        val expected = marker.videoStampValue(ProxyKind.VideoTranscoded)
        val output = File(context.cacheDir, "marker-probe-output.mp4").also { it.delete() }

        transcodeWithMarker(input, output, expected)

        assumeTrue("transcode produced nothing", output.exists() && output.length() > 0)
        Log.i(TAG, "OUTPUT ${output.length()} bytes at ${output.absolutePath}")

        // R4 — the read side we ship today cannot see an mdta marker.
        val proxyByCurrent = marker.isProxy(Uri.fromFile(output))
        val writerViaMmr = MediaMetadataRetriever().let { r ->
            try {
                r.setDataSource(output.absolutePath)
                r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_WRITER)
            } finally {
                r.release()
            }
        }
        Log.i(TAG, "R4 current ProxyMarker.isProxy = $proxyByCurrent (expect false)")
        Log.i(TAG, "R4 MMR METADATA_KEY_WRITER = ${writerViaMmr ?: "<null>"} (expect <null>)")

        // R2 — a dependency-free walk of the box tree.
        val bytes = output.readBytes()
        val order = topLevelBoxTypes(bytes)
        val keyPresent = String(bytes, Charsets.ISO_8859_1).contains(MDTA_KEY)
        val recoveredValue = readMdtaStringValue(bytes)

        Log.i(TAG, "R2 mdta key '$MDTA_KEY' present in file = $keyPresent")
        Log.i(TAG, "R2 recovered mdta value = ${recoveredValue ?: "<none>"} (expect '$expected')")
        Log.i(TAG, "R2 verdict = ${if (recoveredValue == expected) "MATCH" else "MISMATCH"}")

        // R5 — moov relative to mdat decides Option B's cost.
        Log.i(TAG, "R5 top-level box order = $order")
        val moov = order.indexOf("moov")
        val mdat = order.indexOf("mdat")
        Log.i(
            TAG,
            "R5 verdict = " + when {
                moov < 0 || mdat < 0 -> "INDETERMINATE (moov=$moov mdat=$mdat)"
                moov > mdat -> "moov AFTER mdat — a ©wrt append would need no offset fixups"
                else -> "moov BEFORE mdat — a ©wrt append would have to rewrite chunk offsets"
            }
        )

        Log.i(
            TAG,
            "SUMMARY optionA_write=${keyPresent} optionA_read=${recoveredValue == expected} " +
                "currentReadFails=${!proxyByCurrent && writerViaMmr == null}"
        )
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
