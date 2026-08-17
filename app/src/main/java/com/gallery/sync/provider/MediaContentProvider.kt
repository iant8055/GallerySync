package com.gallery.sync.provider

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.gallery.sync.util.Logger
import java.io.FileNotFoundException

/**
 * Exposes cloud-hosted media to other apps through the Android ContentProvider surface.
 *
 * v0.1.0 is a skeleton: it registers the authority and the URI shape, and answers valid queries
 * with a well-formed **empty** cursor. Real media serving arrives in v0.2.0.
 *
 * The provider ships `android:exported="false"`. It has nothing useful to serve yet, and opening
 * it up is a security decision that belongs to Ian rather than to this skeleton.
 *
 * There is intentionally **no Hilt field injection** here. A ContentProvider is instantiated
 * before `Application.onCreate` finishes, so the Hilt component does not exist yet. When v0.2.0
 * needs dependencies it will fetch them lazily via `EntryPointAccessors`.
 */
class MediaContentProvider : ContentProvider() {

    /**
     * Runs on the main thread before the app is fully initialised, so it does no work beyond
     * reporting readiness — no Room, no Retrofit, no disk I/O.
     */
    override fun onCreate(): Boolean {
        Logger.i(TAG, "onCreate: authority=${MediaContract.AUTHORITY}")
        return true
    }

    /**
     * Returns an empty cursor with the requested (or default) projection for any valid media URI.
     *
     * Zero rows, never null — v0.2.0 fills these in from the local index.
     */
    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor? {
        val columns = projection ?: MediaContract.DEFAULT_PROJECTION
        return when (MediaUriMatcher.match(uri.pathSegments)) {
            MediaRoute.MEDIA_DIRECTORY, MediaRoute.MEDIA_ITEM -> {
                MatrixCursor(columns).apply {
                    // Registered now so v0.2.0 can notify observers as the index syncs.
                    setNotificationUri(context?.contentResolver, uri)
                }
            }

            // The documented ContentProvider contract for an unroutable URI. Returning null here
            // would leave callers unable to tell "no results" from "wrong URI".
            MediaRoute.UNKNOWN -> throw IllegalArgumentException("Unknown URI: $uri")
        }
    }

    override fun getType(uri: Uri): String? = when (MediaUriMatcher.match(uri.pathSegments)) {
        MediaRoute.MEDIA_DIRECTORY -> MediaContract.MIME_TYPE_DIR
        MediaRoute.MEDIA_ITEM -> MediaContract.MIME_TYPE_ITEM
        MediaRoute.UNKNOWN -> null
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? =
        throw UnsupportedOperationException("MediaContentProvider is read-only in v0.1.0")

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?
    ): Int = throw UnsupportedOperationException("MediaContentProvider is read-only in v0.1.0")

    /**
     * Always throws.
     *
     * Deletion is never supported through this provider. The user's cloud files are the source of
     * truth and must never be deleted from the device; any future implementation may only evict a
     * local cache entry, and would be named for that.
     */
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int =
        throw UnsupportedOperationException("MediaContentProvider is read-only in v0.1.0")

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor? {
        // TODO(v0.2.0): on-demand download
        throw FileNotFoundException("On-demand download lands in v0.2.0")
    }

    private companion object {
        const val TAG = "MediaProvider"
    }
}
