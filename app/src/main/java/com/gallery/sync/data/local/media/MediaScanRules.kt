package com.gallery.sync.data.local.media

import android.Manifest
import android.os.Build
import java.util.Locale

/**
 * The decisions the scanner makes, extracted as pure functions.
 *
 * MediaStore itself cannot be queried from a plain JVM test, so the judgement calls live here where
 * they can be tested directly with no device and no mocks — the same approach `MediaUriMatcher`
 * takes for the ContentProvider.
 */
internal object MediaScanRules {

    /** Album shown when MediaStore has no bucket name for an item. */
    const val UNKNOWN_ALBUM = "Other"

    /**
     * Whether an item is worth offering to the backup.
     *
     * A zero-size row is a file still being written — a photo mid-save, or a download in flight.
     * Uploading it would either fail or store a truncated file, and either way it would be retried
     * forever. `IS_PENDING` marks the same situation explicitly on API 29+.
     */
    fun shouldInclude(sizeBytes: Long, isPending: Boolean): Boolean =
        sizeBytes > 0 && !isPending

    /**
     * Resolves the album name.
     *
     * `BUCKET_DISPLAY_NAME` is normally present, but it can be null for items in odd locations. The
     * relative path is the better fallback because it is what the user sees as a folder; only when
     * both are missing does this give up and return [UNKNOWN_ALBUM].
     */
    fun albumNameOf(bucketDisplayName: String?, relativePath: String?): String {
        bucketDisplayName?.takeIf { it.isNotBlank() }?.let { return it }

        // "DCIM/Camera/" -> "Camera"
        relativePath?.trim('/')?.substringAfterLast('/')?.takeIf { it.isNotBlank() }
            ?.let { return it }

        return UNKNOWN_ALBUM
    }

    /**
     * Works out how much media the app may read.
     *
     * [sdkInt] and [isGranted] are parameters rather than being read from the platform so this can
     * be exercised for every API level from a unit test.
     */
    fun resolveAccess(sdkInt: Int, isGranted: (String) -> Boolean): MediaAccess = when {
        sdkInt >= Build.VERSION_CODES.TIRAMISU -> {
            val images = isGranted(Manifest.permission.READ_MEDIA_IMAGES)
            val video = isGranted(Manifest.permission.READ_MEDIA_VIDEO)
            val selected = isGranted(PERMISSION_USER_SELECTED)

            when {
                images && video -> MediaAccess.FULL
                // Android 14+: the user picked specific photos. Partial, and it matters.
                selected -> MediaAccess.PARTIAL
                // One of the two granted is still incomplete for an app backing up both.
                images || video -> MediaAccess.PARTIAL
                else -> MediaAccess.NONE
            }
        }

        // Android 12 and below had a single, all-or-nothing storage permission.
        isGranted(Manifest.permission.READ_EXTERNAL_STORAGE) -> MediaAccess.FULL

        else -> MediaAccess.NONE
    }

    /**
     * Named literally rather than via `Manifest.permission` so the constant resolves when compiling
     * against, and running on, API levels below 34.
     */
    const val PERMISSION_USER_SELECTED = "android.permission.READ_MEDIA_VISUAL_USER_SELECTED"

    /**
     * Which folder an item is in, spelled so that case cannot tell two readings of it apart.
     *
     * Android's shared storage is case-insensitive, so `DCIM/camera/` and `DCIM/Camera/` are one
     * directory. MediaStore, however, keeps whatever spelling each writer used. TASK-023: on 7 Sept
     * 2026 a copied `camera` folder and the camera app's `Camera` produced two albums over one
     * folder on the Moto G, and one folder could then carry two modes.
     *
     * The whole path rather than the album name, so `DCIM/Camera` and `Pictures/camera` stay two
     * folders. Below API 29 there is no path, and the name is all there is.
     */
    fun folderKeyOf(relativePath: String?, album: String): String =
        relativePath?.trim('/')?.takeIf { it.isNotEmpty() }?.lowercase(Locale.ROOT)
            ?: "name:${album.lowercase(Locale.ROOT)}"

    /** One item's contribution to [canonicalAlbumNames]. */
    data class AlbumSighting(val folderKey: String, val album: String, val mediaStoreId: Long)

    /**
     * The one spelling each folder is shown and stored under.
     *
     * **The spelling of the newest item** — the highest MediaStore id. MediaProvider records a new
     * insert under the directory's on-disk spelling, whatever spelling the writer asked for. Seen
     * twice on the Moto G on 16 Sept 2026: a restore inserted into `DCIM/camera/` came back as
     * `DCIM/Camera/` against a `Camera` folder, and camera shots into an existing `camera` folder
     * came back as `camera`. So the newest row is the best reading of the disk the app can get
     * without all-files access. The Files app is no help: it showed MediaStore's view, not the disk.
     *
     * Returns a map from folder key to spelling, for every folder seen.
     */
    fun canonicalAlbumNames(sightings: List<AlbumSighting>): Map<String, String> =
        sightings
            .groupBy { it.folderKey }
            .mapValues { (_, inFolder) -> inFolder.maxBy { it.mediaStoreId }.album }
}
