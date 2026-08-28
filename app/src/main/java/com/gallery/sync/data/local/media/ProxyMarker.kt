package com.gallery.sync.data.local.media

import android.content.ContentResolver
import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import com.gallery.sync.util.Logger
import dagger.hilt.android.qualifiers.ApplicationContext
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
 * `Software` — and its MP4 equivalent, the `©wrt` writer field — is the tag that honestly describes
 * what wrote the file, which is exactly the claim being made.
 *
 * ### Two formats, one question
 *
 * Photos carry EXIF; MP4 files do not, and never will. Rather than let the caller care, this class
 * routes on the MIME type and each format answers in its own vocabulary. Every caller asks
 * [isProxy] or [kindOf] and gets the same answer shape for both.
 *
 * ### The video half has no writer yet
 *
 * Detection for video is real and works the moment anything stamps a file. Stamping is not, because
 * there is no framework API to set MP4 metadata on an existing file — the value has to be written
 * as the container is muxed, which happens inside the transcode that TASK-013 has not built. See
 * [videoStampValue] for the contract that work has to honour. Nothing here pretends to stamp a
 * video; a method that silently did nothing would be worse than an absent one, because the file
 * would then be a proxy that no future scan could recognise.
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
     * The MP4 writer field, which is where a muxer can put a "made by" string.
     *
     * `MediaMetadataRetriever` must be released, and throws rather than returning null for a file it
     * cannot parse — including any file still being written.
     */
    private fun videoStamp(uri: Uri): String? = runCatching {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_WRITER)
        } finally {
            // `release()`, not `use`. MediaMetadataRetriever only became AutoCloseable at API 29
            // and this app runs from 26, where `use` would resolve at compile time and throw
            // NoSuchMethodError on the device.
            retriever.release()
        }
    }.getOrElse {
        Logger.w(TAG, "could not read video metadata: ${it.javaClass.simpleName}")
        null
    }

    private fun isVideo(uri: Uri): Boolean =
        runCatching { resolver.getType(uri) }.getOrNull()?.startsWith("video/") == true

    private fun valueFor(kind: ProxyKind): String = when (kind) {
        // Bare, deliberately. Photos stamped before kinds existed carry this exact string, and
        // writing a longer one now would mean two vocabularies for the same thing on one device.
        ProxyKind.PhotoDownscaled -> MARKER
        ProxyKind.VideoTranscoded -> "$MARKER$SEPARATOR$VIDEO_TRANSCODED"
        ProxyKind.VideoTruncated -> "$MARKER$SEPARATOR$VIDEO_TRUNCATED"
    }

    companion object {
        const val MARKER = "GallerySync proxy"

        private const val SEPARATOR = "/"
        private const val PHOTO_DOWNSCALED = "photo-downscaled"
        private const val VIDEO_TRANSCODED = "video-transcoded"
        private const val VIDEO_TRUNCATED = "video-truncated"

        private const val TAG = "ProxyMarker"
    }
}
