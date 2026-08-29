package com.gallery.sync.data.local.media

import android.media.MediaCodecInfo
import android.media.MediaCodecList

/**
 * Whether this phone can decode a given clip at all.
 *
 * ### Why this exists
 *
 * Measured on a Moto G 2026, 28 Aug 2026: `c2.mtk.hevc.decoder` declares a maximum of 2560×1440, and
 * so do its AVC and VP9 decoders. The software fallbacks stop at 1920×1088. Handed the 8K sample it
 * did not transcode slowly — it threw. **A phone either has the hardware for a clip or it cannot
 * participate at all**, and no amount of charging or patience changes that.
 *
 * Without this check, the failure lands on exactly the largest files a user most wanted shrunk, with
 * a message the app cannot explain. With it, the honest answer is available before anything starts.
 *
 * The Fold 4 by contrast handled 7680×4320 comfortably, so this is genuinely a per-device question
 * rather than a floor everyone shares.
 */
object VideoCapability {

    /**
     * True when some decoder on this device claims it can handle [width]×[height] of [mimeType].
     *
     * Asks every decoder rather than the default one: vendors ship several, and the one Media3 picks
     * is not necessarily the first listed. A single capable decoder is enough.
     *
     * Both orientations are tried. A portrait clip is often described by its unrotated dimensions,
     * and a decoder declaring 2560×1440 will refuse 1440×2560 asked that way round while handling
     * the same pixels perfectly well.
     */
    fun canDecode(mimeType: String, width: Int, height: Int): Boolean {
        if (width <= 0 || height <= 0) return false

        return MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos
            .asSequence()
            .filterNot { it.isEncoder }
            .filter { info -> info.supportedTypes.any { it.equals(mimeType, ignoreCase = true) } }
            .mapNotNull { info ->
                runCatching { info.getCapabilitiesForType(mimeType).videoCapabilities }.getOrNull()
            }
            .any { it.supports(width, height) || it.supports(height, width) }
    }

    private fun MediaCodecInfo.VideoCapabilities.supports(w: Int, h: Int): Boolean =
        runCatching { isSizeSupported(w, h) }.getOrDefault(false)
}
