package com.gallery.sync.data.local.media

import android.Manifest
import android.content.ContentResolver
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import com.gallery.sync.util.Logger
import java.io.InputStream

/**
 * Reads a photo or video **with its location intact**.
 *
 * From Android 10, MediaStore hands an app a copy of a file with its location blanked out (the GPS tags
 * zeroed in place, same length) unless the app holds `ACCESS_MEDIA_LOCATION` *and* asks for the original
 * with [MediaStore.setRequireOriginal]. GallerySync did neither, so every file it backed up reached the
 * Cloud without its location, and every optimised copy was made from the blanked read. Found on the
 * Moto G, 8 Oct 2026: a photo taken at 45°03′N 93°18′W came back from OneDrive with 43 bytes of its
 * GPS block zeroed, at exactly its original size, so no size check could ever have caught it.
 *
 * Every read that can end up in the Cloud or in an optimised copy goes through here. Without the
 * permission (Android 9 and below have nothing to redact; a user can refuse it) the plain read is used
 * and the fact is logged, because failing the backup would protect nothing.
 */
object OriginalMedia {

    private const val TAG = "OriginalMedia"

    /** Whether this app may read locations at all. Below Android 10 nothing is redacted. */
    fun canReadLocation(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
            context.checkSelfPermission(Manifest.permission.ACCESS_MEDIA_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    /** [uri] asking for the unredacted file, when it is a MediaStore item and that is possible. */
    private fun originalOf(uri: Uri): Uri =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && uri.authority == MediaStore.AUTHORITY) {
            MediaStore.setRequireOriginal(uri)
        } else {
            uri
        }

    fun openFileDescriptor(resolver: ContentResolver, uri: Uri): ParcelFileDescriptor? {
        val original = originalOf(uri)
        if (original == uri) return resolver.openFileDescriptor(uri, "r")
        return try {
            resolver.openFileDescriptor(original, "r")
        } catch (e: SecurityException) {
            // No ACCESS_MEDIA_LOCATION: the plain read still works, without the location.
            Logger.w(TAG, "no media location access; reading $uri without its location")
            resolver.openFileDescriptor(uri, "r")
        } catch (e: UnsupportedOperationException) {
            resolver.openFileDescriptor(uri, "r")
        }
    }

    fun openInputStream(resolver: ContentResolver, uri: Uri): InputStream? {
        val original = originalOf(uri)
        if (original == uri) return resolver.openInputStream(uri)
        return try {
            resolver.openInputStream(original)
        } catch (e: SecurityException) {
            Logger.w(TAG, "no media location access; reading $uri without its location")
            resolver.openInputStream(uri)
        } catch (e: UnsupportedOperationException) {
            resolver.openInputStream(uri)
        }
    }
}
