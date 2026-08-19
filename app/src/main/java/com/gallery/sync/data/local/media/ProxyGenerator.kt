package com.gallery.sync.data.local.media

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import com.gallery.sync.di.IoDispatcher
import com.gallery.sync.util.Logger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Why no proxy came back.
 *
 * The distinction is load-bearing rather than cosmetic. [NotWorthwhile] is permanent — the file can
 * never shrink, so it should stop being offered. [Failed] may be transient, so the file stays a
 * candidate and gets another chance. Collapsing the two, as a bare null did, meant either offering
 * work that can never happen or excluding photos on one bad decode.
 */
sealed interface ProxyResult {

    data class Created(val proxy: GeneratedProxy) : ProxyResult

    /** Already at or under the target size, or already a proxy. Nothing to gain, ever. */
    data object NotWorthwhile : ProxyResult

    /** Could not be read, decoded or written this time. Worth trying again. */
    data class Failed(val reason: String) : ProxyResult
}

/** A downscaled stand-in for a photo whose original is safely in OneDrive. */
data class GeneratedProxy(
    val file: File,
    val sizeBytes: Long,
    val widthPx: Int,
    val heightPx: Int
)

/**
 * Produces the downscaled image that will replace a photo locally.
 *
 * Deliberately does not touch MediaStore or overwrite anything — it only writes into app cache.
 * The destructive half lives in `ProxyApplier`, so the risky code stays small and this part stays
 * testable.
 */
@Singleton
class ProxyGenerator @Inject constructor(
    @ApplicationContext private val context: Context,
    private val marker: ProxyMarker,
    @param:IoDispatcher private val dispatcher: CoroutineDispatcher
) {

    private val resolver: ContentResolver get() = context.contentResolver

    /**
     * Builds a proxy for [uri], or null when one is not worth making — the image is already small,
     * or it could not be decoded at all.
     *
     * Null is a normal outcome. A photo that cannot be decoded must be left completely alone: it
     * is the case where overwriting would destroy something we do not understand.
     */
    suspend fun generate(uri: Uri, displayName: String): ProxyResult = withContext(dispatcher) {
        val bounds = readBounds(uri) ?: run {
            Logger.w(TAG, "could not read bounds for $displayName; leaving it alone")
            return@withContext ProxyResult.Failed("could not read bounds")
        }

        val longEdge = maxOf(bounds.outWidth, bounds.outHeight)
        if (longEdge <= 0) return@withContext ProxyResult.Failed("no usable dimensions")

        // Asked of the file itself, not the ledger. A second pass would burn a second badge into
        // the same photo, and the ledger is exactly the thing that has been observed going stale.
        if (marker.isProxy(uri)) {
            Logger.d(TAG, "$displayName is already a proxy; leaving it alone")
            return@withContext ProxyResult.NotWorthwhile
        }

        // Already small enough. Never upscale — that costs space and adds nothing.
        if (longEdge <= TARGET_LONG_EDGE_PX) {
            Logger.d(TAG, "$displayName is already ${longEdge}px; no proxy needed")
            return@withContext ProxyResult.NotWorthwhile
        }

        val decoded = decodeScaled(uri, longEdge) ?: run {
            Logger.w(TAG, "could not decode $displayName; leaving it alone")
            return@withContext ProxyResult.Failed("could not decode")
        }

        val scaled = scaleToTarget(decoded)
        if (scaled !== decoded) decoded.recycle()

        // Drawing needs a mutable target; a decode can hand back an immutable one.
        val canvasReady = ensureMutable(scaled)
        if (canvasReady !== scaled) scaled.recycle()

        // The gallery rotates the photo by its EXIF orientation before showing it, so the badge has
        // to be placed against that rotation or it appears sideways in the wrong corner.
        ProxyBadge.drawOn(canvasReady, readRotationDegrees(uri))

        val output = File(context.cacheDir, "proxy_${displayName.substringBeforeLast('.')}.jpg")
        val written = runCatching {
            output.outputStream().use { out ->
                canvasReady.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
            }
        }.isSuccess

        val width = canvasReady.width
        val height = canvasReady.height
        canvasReady.recycle()

        if (!written || !output.exists() || output.length() == 0L) {
            output.delete()
            Logger.w(TAG, "proxy for $displayName was not written; leaving the original alone")
            return@withContext ProxyResult.Failed("proxy was not written")
        }

        // Without EXIF the gallery loses date grouping and map placement, and — most visibly —
        // orientation, which turns portraits sideways. Copy it before the proxy is usable.
        if (!copyExif(uri, output)) {
            output.delete()
            Logger.w(TAG, "EXIF could not be copied for $displayName; not proxying it")
            return@withContext ProxyResult.Failed("EXIF could not be copied")
        }

        ProxyResult.Created(
            GeneratedProxy(
                file = output,
                sizeBytes = output.length(),
                widthPx = width,
                heightPx = height
            )
        )
    }

    private fun readBounds(uri: Uri): BitmapFactory.Options? = runCatching {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
        options.takeIf { it.outWidth > 0 && it.outHeight > 0 }
    }.getOrNull()

    /**
     * Decodes at a reduced sample size.
     *
     * A 50 MP image decoded whole is roughly 200 MB in memory and will kill the process on many
     * phones. `inSampleSize` keeps the decode near the size actually needed.
     */
    private fun decodeScaled(uri: Uri, longEdge: Int): Bitmap? = runCatching {
        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSizeFor(longEdge, TARGET_LONG_EDGE_PX)
            // Asked for up front so the badge can usually be drawn without copying the bitmap.
            inMutable = true
        }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
    }.getOrNull()

    private fun ensureMutable(source: Bitmap): Bitmap =
        if (source.isMutable) {
            source
        } else {
            source.copy(source.config ?: Bitmap.Config.ARGB_8888, true) ?: source
        }

    /** The photo's EXIF rotation, which is what a gallery applies before displaying it. */
    private fun readRotationDegrees(uri: Uri): Int = runCatching {
        resolver.openInputStream(uri)?.use { ExifInterface(it).rotationDegrees } ?: 0
    }.getOrDefault(0)

    private fun scaleToTarget(source: Bitmap): Bitmap {
        val longEdge = maxOf(source.width, source.height)
        if (longEdge <= TARGET_LONG_EDGE_PX) return source

        val ratio = TARGET_LONG_EDGE_PX.toFloat() / longEdge
        return Bitmap.createScaledBitmap(
            source,
            (source.width * ratio).toInt().coerceAtLeast(1),
            (source.height * ratio).toInt().coerceAtLeast(1),
            true
        )
    }

    private fun copyExif(source: Uri, destination: File): Boolean = runCatching {
        val from = resolver.openInputStream(source)?.use { ExifInterface(it) } ?: return false
        val to = ExifInterface(destination.absolutePath)

        PRESERVED_EXIF_TAGS.forEach { tag ->
            from.getAttribute(tag)?.let { to.setAttribute(tag, it) }
        }

        // Written last so nothing copied from the source can overwrite it. This is what makes a
        // proxy self-describing: recognisable from the file alone, with no ledger to go stale.
        marker.stamp(to)

        to.saveAttributes()
        true
    }.getOrDefault(false)

    companion object {

        private const val TAG = "ProxyGenerator"

        /** Long edge of the proxy. Roughly a tenth the bytes, still good for viewing and sharing. */
        const val TARGET_LONG_EDGE_PX = 2048

        const val JPEG_QUALITY = 90

        /**
         * Largest power-of-two sample size that still leaves the image at or above [target].
         *
         * Pure, and public so it is unit tested — an off-by-one here means either an out-of-memory
         * crash or a proxy blurrier than intended.
         */
        fun sampleSizeFor(longEdge: Int, target: Int): Int {
            if (longEdge <= target || target <= 0) return 1
            var sample = 1
            while (longEdge / (sample * 2) >= target) {
                sample *= 2
            }
            return sample
        }

        /**
         * What the gallery actually uses. Orientation and date matter most: without orientation
         * portraits display sideways, and without date the gallery cannot group by day.
         */
        val PRESERVED_EXIF_TAGS = listOf(
            ExifInterface.TAG_DATETIME,
            ExifInterface.TAG_DATETIME_ORIGINAL,
            ExifInterface.TAG_DATETIME_DIGITIZED,
            ExifInterface.TAG_OFFSET_TIME,
            ExifInterface.TAG_OFFSET_TIME_ORIGINAL,
            ExifInterface.TAG_ORIENTATION,
            ExifInterface.TAG_GPS_LATITUDE,
            ExifInterface.TAG_GPS_LATITUDE_REF,
            ExifInterface.TAG_GPS_LONGITUDE,
            ExifInterface.TAG_GPS_LONGITUDE_REF,
            ExifInterface.TAG_GPS_ALTITUDE,
            ExifInterface.TAG_GPS_ALTITUDE_REF,
            ExifInterface.TAG_GPS_TIMESTAMP,
            ExifInterface.TAG_GPS_DATESTAMP,
            ExifInterface.TAG_MAKE,
            ExifInterface.TAG_MODEL,
            ExifInterface.TAG_F_NUMBER,
            ExifInterface.TAG_EXPOSURE_TIME,
            ExifInterface.TAG_FOCAL_LENGTH,
            ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY,
            ExifInterface.TAG_WHITE_BALANCE
        )
    }
}
