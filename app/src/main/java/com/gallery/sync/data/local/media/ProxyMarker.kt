package com.gallery.sync.data.local.media

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import com.gallery.sync.util.Logger
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import javax.inject.Inject
import javax.inject.Singleton

/**
 * What kind of proxy a file is, not merely that it is one.
 *
 * Restoring has to know: a downscaled photo and a shortened video are both replaced by their
 * full-size original, but they were made in different ways and only one of them can be regenerated
 * locally. Ian, 27 Aug 2026 — *"whether it's truncated or optimized, let's build in the framework."*
 */
enum class ProxyKind {

    /** A photo re-encoded at ~2048px. The only kind that exists today. */
    PhotoDownscaled,

    /** A video re-encoded at a lower bitrate or resolution. TASK-013. */
    VideoTranscoded,

    /**
     * A video shortened rather than re-encoded — a stub standing in for the whole clip.
     *
     * Distinct from [VideoTranscoded] because it is not watchable. A gallery showing it will play a
     * fragment and stop, so anything offering it back has to say so rather than treating it as a
     * lower-quality copy of the same thing.
     */
    VideoTruncated
}

/**
 * Says whether a local file is one of GallerySync's proxies, by asking the file itself.
 *
 * The ledger cannot answer this reliably: it is wiped by an uninstall, absent on a new phone, and
 * has been observed going stale. A stamp inside the file survives all of that, and survives being
 * copied or shared as well.
 *
 * A photo's `Software` EXIF tag and a video's `mdta` metadata under [MDTA_KEY] both honestly describe
 * what wrote the file, which is exactly the claim being made.
 *
 * ### Two formats, one question
 *
 * Photos carry EXIF; MP4 files do not, and never will. Rather than let the caller care, this class
 * routes on the MIME type and each format answers in its own vocabulary. Every caller asks
 * [isProxy] or [kindOf] and gets the same answer shape for both.
 *
 * ### The video half, proven on hardware
 *
 * There is no framework API to set MP4 metadata on an existing file, so the marker is written as the
 * container is muxed — the transcoder adds an `MdtaMetadataEntry` under [MDTA_KEY] through Media3's
 * muxer hook, carrying exactly [videoStampValue]. It is read back here by walking the `moov` box,
 * without `MediaMetadataRetriever` (which cannot see an `mdta` key at all) and without
 * `media3-exoplayer` (which would only read what [videoStamp] does in a few lines). The route was
 * confirmed byte-identical on the Fold 4 and Moto G, 29 Aug 2026 — see `VideoMarkerProbeTest`.
 */
@Singleton
class ProxyMarker @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val resolver: ContentResolver get() = context.contentResolver

    /** Stamps [exif] as a downscaled photo. The caller still has to save it. */
    fun stamp(exif: ExifInterface) {
        exif.setAttribute(ExifInterface.TAG_SOFTWARE, valueFor(ProxyKind.PhotoDownscaled))
    }

    /**
     * The string a transcoder must write into an MP4's writer metadata for the result to be
     * recognisable later.
     *
     * Exposed rather than assumed so the muxing code and the detection code cannot drift: whatever
     * TASK-013 writes, this is what it writes.
     */
    fun videoStampValue(kind: ProxyKind): String = valueFor(kind)

    /**
     * Whether the file at [uri] carries the marker.
     *
     * For a photo this reads the EXIF header only, never the pixels, so it is cheap enough to ask
     * of every file in a backup run. For a video it reads the container's metadata, which is a
     * seek to the `moov` atom rather than a decode — the same order of cost.
     *
     * Anything unreadable answers false. Refusing to claim a file is a proxy is the safe direction
     * in both places it is asked: during backup a wrong "yes" skips a real upload, and on the
     * restore list a wrong "yes" offers to overwrite a file that is already the original.
     */
    fun isProxy(uri: Uri): Boolean = kindOf(uri) != null

    /**
     * Which kind of proxy [uri] is, or null if it is not one of ours.
     *
     * A bare [MARKER] with no kind means [ProxyKind.PhotoDownscaled]. Every photo stamped before
     * 27 Aug 2026 carries exactly that string, and those files are on real devices — reading them
     * as "not a proxy" would offer them for upload as if they were originals.
     */
    fun kindOf(uri: Uri): ProxyKind? {
        val stamp = when {
            isVideo(uri) -> videoStamp(uri)
            else -> photoStamp(uri)
        } ?: return null

        if (stamp == MARKER) return ProxyKind.PhotoDownscaled
        if (!stamp.startsWith("$MARKER$SEPARATOR")) return null

        return when (stamp.removePrefix("$MARKER$SEPARATOR")) {
            PHOTO_DOWNSCALED -> ProxyKind.PhotoDownscaled
            VIDEO_TRANSCODED -> ProxyKind.VideoTranscoded
            VIDEO_TRUNCATED -> ProxyKind.VideoTruncated
            // A marker this build does not know. Ours, but from a newer version — say nothing
            // rather than guess, since every use of the answer decides whether to rewrite a file.
            else -> null
        }
    }

    private fun photoStamp(uri: Uri): String? = runCatching {
        resolver.openInputStream(uri)?.use {
            ExifInterface(it).getAttribute(ExifInterface.TAG_SOFTWARE)
        }
    }.getOrNull()

    /**
     * The proxy string stored in the video's `mdta` metadata, or null.
     *
     * Only the `moov` box is read, reached by seeking past every other top-level box rather than
     * reading it — so an original whose `moov` trails a multi-gigabyte `mdat` costs a seek, not a
     * scan. Within `moov`, every `data` box is examined and the first whose text is one of ours is
     * returned, which ignores the `com.android.*` entries the camera writes alongside. A file whose
     * `moov` is not found within [MAX_SCAN_BYTES] is treated as not ours.
     */
    private fun videoStamp(uri: Uri): String? = runCatching {
        resolver.openFileDescriptor(uri, "r")?.use { pfd ->
            val moov = readMoovBox(FileInputStream(pfd.fileDescriptor).channel) ?: return@use null
            firstMarkerValue(moov)
        }
    }.getOrElse {
        Logger.w(TAG, "could not read video metadata: ${it.javaClass.simpleName}")
        null
    }

    /** The bytes of the `moov` box's contents, located by walking top-level boxes, or null. */
    private fun readMoovBox(channel: FileChannel): ByteArray? {
        val fileSize = channel.size()
        var pos = 0L
        while (pos + 8 <= fileSize && pos <= MAX_SCAN_BYTES) {
            val header = readAt(channel, pos, 8) ?: return null
            var size = u32(header, 0)
            val type = ascii(header, 4)
            var headerLen = 8L
            when {
                // 64-bit largesize in the eight bytes after the type.
                size == 1L -> {
                    val ext = readAt(channel, pos + 8, 8) ?: return null
                    size = u64(ext, 0)
                    headerLen = 16L
                }
                // A zero size means the box runs to end of file.
                size == 0L -> size = fileSize - pos
            }
            if (size < headerLen) return null
            val contentLen = size - headerLen
            if (type == "moov") {
                if (contentLen <= 0L || contentLen > MAX_MOOV_BYTES) return null
                return readAt(channel, pos + headerLen, contentLen.toInt())
            }
            pos += size
        }
        return null
    }

    /** The value of the first `data` box inside [moov] whose UTF-8 text is one of ours. */
    private fun firstMarkerValue(moov: ByteArray): String? {
        var from = 0
        while (true) {
            val at = nextFourcc(moov, from, "data") ?: return null
            from = at + 4
            val boxStart = at - 4
            if (boxStart < 0) continue
            val size = u32(moov, boxStart)
            val end = boxStart + size
            // data box: [size:4][type='data'][typeIndicator:4][locale:4][value...]
            val valueStart = boxStart + 16
            if (size < 16L || end > moov.size || valueStart >= end) continue
            val value = String(moov, valueStart, (end - valueStart).toInt(), Charsets.UTF_8)
            if (value.startsWith(MARKER)) return value
        }
    }

    private fun readAt(channel: FileChannel, position: Long, length: Int): ByteArray? {
        val buffer = ByteBuffer.allocate(length)
        var p = position
        while (buffer.hasRemaining()) {
            val n = channel.read(buffer, p)
            if (n < 0) return null
            p += n
        }
        return buffer.array()
    }

    private fun nextFourcc(b: ByteArray, from: Int, type: String): Int? {
        val f = type.toByteArray(Charsets.ISO_8859_1)
        var p = maxOf(from, 0)
        while (p + 4 <= b.size) {
            if (b[p] == f[0] && b[p + 1] == f[1] && b[p + 2] == f[2] && b[p + 3] == f[3]) return p
            p++
        }
        return null
    }

    private fun u32(b: ByteArray, o: Int): Long =
        ((b[o].toLong() and 0xFF) shl 24) or ((b[o + 1].toLong() and 0xFF) shl 16) or
            ((b[o + 2].toLong() and 0xFF) shl 8) or (b[o + 3].toLong() and 0xFF)

    private fun u64(b: ByteArray, o: Int): Long {
        var v = 0L
        for (i in 0 until 8) v = (v shl 8) or (b[o + i].toLong() and 0xFF)
        return v
    }

    private fun ascii(b: ByteArray, o: Int): String = String(b, o, 4, Charsets.ISO_8859_1)

    /**
     * Whether [uri] is a video, by MIME type where the resolver offers one and by extension where it
     * does not. `getType` returns null for a `file://` uri — which restore and the tests both use —
     * so relying on it alone routed real videos down the photo (EXIF) path and lost their marker.
     */
    private fun isVideo(uri: Uri): Boolean {
        runCatching { resolver.getType(uri) }.getOrNull()?.let { return it.startsWith("video/") }
        val name = uri.lastPathSegment ?: uri.path ?: return false
        return name.substringAfterLast('.', "").lowercase() in VIDEO_EXTENSIONS
    }

    private fun valueFor(kind: ProxyKind): String = when (kind) {
        // Bare, deliberately. Photos stamped before kinds existed carry this exact string, and
        // writing a longer one now would mean two vocabularies for the same thing on one device.
        ProxyKind.PhotoDownscaled -> MARKER
        ProxyKind.VideoTranscoded -> "$MARKER$SEPARATOR$VIDEO_TRANSCODED"
        ProxyKind.VideoTruncated -> "$MARKER$SEPARATOR$VIDEO_TRUNCATED"
    }

    companion object {
        const val MARKER = "GallerySync proxy"

        /**
         * The `mdta` key the marker is stored under in a video's `moov` metadata. Shared so the
         * transcoder's writer and [videoStamp]'s reader cannot drift onto different keys.
         */
        const val MDTA_KEY = "com.gallery.sync.proxy"

        private const val SEPARATOR = "/"
        private const val PHOTO_DOWNSCALED = "photo-downscaled"
        private const val VIDEO_TRANSCODED = "video-transcoded"
        private const val VIDEO_TRUNCATED = "video-truncated"

        private const val TAG = "ProxyMarker"

        private val VIDEO_EXTENSIONS = setOf("mp4", "mov", "m4v", "3gp", "mkv", "webm")

        /** A `moov` larger than this is not one of ours; refuse it rather than allocate for it. */
        private const val MAX_MOOV_BYTES = 64L * 1024 * 1024

        /** Give up seeking for `moov` past this offset — our proxies carry it near the front. */
        private const val MAX_SCAN_BYTES = 256L * 1024 * 1024
    }
}
