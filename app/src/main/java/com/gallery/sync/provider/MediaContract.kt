package com.gallery.sync.provider

import android.net.Uri

/**
 * The public addressing contract for [MediaContentProvider].
 *
 * Third-party apps and our own v0.2.0 code reach the provider through this object and nothing
 * else. Everything here is effectively published API: changing [AUTHORITY] or a column name after
 * release breaks every integration built against it.
 */
object MediaContract {

    /**
     * Must stay byte-identical to `android:authorities` on the `<provider>` element in
     * `AndroidManifest.xml`.
     *
     * Hardcoded on purpose. Deriving it from `BuildConfig.APPLICATION_ID` would let the constant
     * and the manifest drift apart, since the manifest cannot reference a Kotlin constant.
     */
    const val AUTHORITY = "com.gallery.sync.provider"

    /** The media directory: `content://com.gallery.sync.provider/media`. */
    val CONTENT_URI: Uri by lazy { Uri.parse("content://$AUTHORITY/$PATH_MEDIA") }

    /** The single path segment under which media is exposed. */
    const val PATH_MEDIA = "media"

    /**
     * Column names returned by [MediaContentProvider.query].
     *
     * [ID], [DISPLAY_NAME] and [SIZE] use the literal `_id` / `_display_name` / `_size` spellings
     * because consumers such as CapCut read them via `android.provider.OpenableColumns`. These
     * names are load-bearing — do not tidy them up.
     */
    object Columns {
        const val ID = "_id"
        const val DISPLAY_NAME = "_display_name"
        const val SIZE = "_size"
        const val MIME_TYPE = "mime_type"
        const val DATE_MODIFIED = "date_modified"
        const val SOURCE = "source"
    }

    /** Projection used when a caller passes `null`. */
    val DEFAULT_PROJECTION = arrayOf(
        Columns.ID,
        Columns.DISPLAY_NAME,
        Columns.SIZE,
        Columns.MIME_TYPE,
        Columns.DATE_MODIFIED,
        Columns.SOURCE
    )

    /** MIME type of the media directory. */
    const val MIME_TYPE_DIR = "vnd.android.cursor.dir/vnd.com.gallery.sync.media"

    /** MIME type of a single media item. */
    const val MIME_TYPE_ITEM = "vnd.android.cursor.item/vnd.com.gallery.sync.media"
}
