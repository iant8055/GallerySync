package com.gallery.sync.data.local.media

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Says whether a local file is one of GallerySync's proxies, by asking the file itself.
 *
 * The ledger cannot answer this reliably: it is wiped by an uninstall, absent on a new phone, and
 * has been observed going stale. A stamp inside the file survives all of that, and survives being
 * copied or shared as well.
 *
 * `Software` is the tag that honestly describes what wrote the file, which is exactly the claim
 * being made.
 */
@Singleton
class ProxyMarker @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val resolver: ContentResolver get() = context.contentResolver

    /** Stamps [exif]. The caller still has to save it. */
    fun stamp(exif: ExifInterface) {
        exif.setAttribute(ExifInterface.TAG_SOFTWARE, MARKER)
    }

    /**
     * Whether the file at [uri] carries the marker.
     *
     * Reads the EXIF header only, never the pixels, so it is cheap enough to ask of every file in
     * a backup run. Anything unreadable answers false: refusing to claim a file is a proxy is the
     * safe direction, since the consequence of a wrong "yes" is skipping a real upload.
     */
    fun isProxy(uri: Uri): Boolean = runCatching {
        resolver.openInputStream(uri)?.use {
            ExifInterface(it).getAttribute(ExifInterface.TAG_SOFTWARE) == MARKER
        } ?: false
    }.getOrDefault(false)

    companion object {
        const val MARKER = "GallerySync proxy"
    }
}
