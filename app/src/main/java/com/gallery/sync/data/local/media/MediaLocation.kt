package com.gallery.sync.data.local.media

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.exifinterface.media.ExifInterface

/**
 * Whether a photo or video on the phone carries a location, read from the original (see [OriginalMedia]). The
 * location repair only resends a file that has one: a photo taken with location off has nothing to put back.
 *
 * Zero latitude and longitude together count as none, since that is what a blanked read looks like.
 */
object MediaLocation {

    fun has(context: Context, uri: Uri, isVideo: Boolean): Boolean = runCatching {
        if (isVideo) {
            val retriever = MediaMetadataRetriever()
            try {
                val original = if (
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                    uri.authority == MediaStore.AUTHORITY &&
                    OriginalMedia.canReadLocation(context)
                ) MediaStore.setRequireOriginal(uri) else uri
                retriever.setDataSource(context, original)
                val location = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_LOCATION)
                !location.isNullOrBlank() && !isZero(location)
            } finally {
                retriever.release()
            }
        } else {
            OriginalMedia.openInputStream(context.contentResolver, uri)?.use { stream ->
                val latLong = ExifInterface(stream).latLong
                latLong != null && !(latLong[0] == 0.0 && latLong[1] == 0.0)
            } ?: false
        }
    }.getOrDefault(false)

    /** "+00.0000+000.0000/" and the like. */
    private fun isZero(iso6709: String): Boolean =
        Regex("""[+-]?\d+(\.\d+)?""").findAll(iso6709).take(2).all { it.value.toDouble() == 0.0 }
}
