package com.gallery.sync.data.local.media

import android.net.Uri

/**
 * One photo or video on the device, as MediaStore describes it.
 *
 * Lives in `data/` rather than `domain/` because it carries a [Uri]: CLAUDE.md requires the domain
 * layer to stay free of Android types.
 *
 * [contentUri] is the only supported way to read the bytes. The `DATA` column — the raw filesystem
 * path — is deliberately absent: direct path access to media is restricted under scoped storage and
 * behaves differently across API 26 to 37, whereas the content URI works on every version.
 */
data class LocalMediaItem(
    val mediaStoreId: Long,
    val contentUri: Uri,
    val displayName: String,
    /** Folder the user thinks of this as living in, e.g. `Camera`. */
    val album: String,
    val sizeBytes: Long,
    val dateModifiedEpochSeconds: Long,
    val mimeType: String,
    val isVideo: Boolean,
    /**
     * MediaStore's `RELATIVE_PATH`, e.g. `DCIM/Camera/`. Null below API 29, which has no such column.
     *
     * Kept rather than discarded after resolving the album name, because it is the only thing
     * comparable to a SAF tree grant — the album name alone cannot say which folder a file lives in.
     */
    val relativePath: String?
)

/** An album on the device, as the user would recognise it in Gallery. */
data class MediaAlbum(
    val name: String,
    val itemCount: Int,
    val totalBytes: Long,
    /**
     * Split out because the two behave differently and the user knows it: videos are never
     * optimised, and they are what makes an album large. "17 files" hides whether an album is a
     * handful of clips or a hundred photos; "2 videos, 15 images" does not.
     */
    val imageCount: Int = 0,
    val videoCount: Int = 0
)

/** A top-level directory on the device containing media files, discovered by scanning MediaStore. */
data class DiscoveredDirectory(
    /** Top-level directory name, e.g. "DCIM", "Pictures", "Download". */
    val name: String,
    /** Number of distinct sub-folders (albums) within this directory. */
    val albumCount: Int,
    /** Number of photo files. */
    val photoCount: Int,
    /** Number of video files. */
    val videoCount: Int,
    /** Total size of all files in bytes. */
    val totalBytes: Long
) {
    val totalFiles: Int get() = photoCount + videoCount
}

/** How much of the user's media the app is currently allowed to see. */
enum class MediaAccess {

    /** Everything. The only state in which a backup can be described as complete. */
    FULL,

    /**
     * Android 14+ and the user granted access to *selected* photos only.
     *
     * Must never be treated as [FULL]. A backup app that reports success while it can only see a
     * handful of hand-picked photos leaves the user believing a library is safe when it is not.
     */
    PARTIAL,

    NONE
}
