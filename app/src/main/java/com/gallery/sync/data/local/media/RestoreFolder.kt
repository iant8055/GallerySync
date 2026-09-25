package com.gallery.sync.data.local.media

/**
 * Where a file that Restore brings back should be created.
 *
 * ### The problem this answers
 *
 * `MediaStore` only lets an app create a photo under `DCIM` or `Pictures`, and a video under `DCIM` or
 * `Movies`. An album that lives anywhere else, such as a folder made at the top of the storage
 * (`/iDrive`, `/dropbox`), could therefore only be restored into `DCIM/<album>`: the same album name,
 * but a second folder. That split the album across two places, and because routing to a Cloud is by
 * *top-level* folder, the restored copies would follow `DCIM`'s route rather than the route of the
 * folder they left (Ian, 25 Sept 2026, on the Moto G: `iDrive` archived to IDrive e2, restored into
 * `DCIM/iDrive`, which is routed to OneDrive).
 *
 * ### The way round it
 *
 * The user already gave the app a persisted tree grant on that folder, in setup, so the app can write to
 * it without a dialog ([SafMediaWriter]). This picks the granted folder whose name is the album, so the
 * file goes back where it came from. It applies only to a folder `MediaStore` would refuse: everywhere
 * else the proven `MediaStore` route stays as it was.
 */
object RestoreFolder {

    private val PHOTO_ROOTS = listOf("DCIM", "Pictures")
    private val VIDEO_ROOTS = listOf("DCIM", "Movies")

    /** Whether Android lets an app create this kind of media under [relativePath]'s top-level folder. */
    fun isWritableRoot(relativePath: String, isVideo: Boolean): Boolean {
        val primary = relativePath.trim('/').substringBefore('/')
        return primary in (if (isVideo) VIDEO_ROOTS else PHOTO_ROOTS)
    }

    /**
     * The granted folder an album's files should go back into, relative to the volume root, or null when
     * the ordinary `MediaStore` route should be used.
     *
     * A folder qualifies when it is a granted tree itself, or a direct sub-folder of one, its name is the
     * [album], and `MediaStore` could not create the file there. Names are compared ignoring case, because
     * `MediaStore` keeps whichever spelling the writer used.
     *
     * [subFolders] lists the names of the folders directly inside a granted folder. It is only asked about
     * a grant whose own name is not the album.
     */
    fun pick(
        album: String,
        isVideo: Boolean,
        grantedPaths: List<String>,
        subFolders: (String) -> List<String>
    ): String? {
        val wanted = album.trim()
        if (wanted.isEmpty()) return null
        for (grant in grantedPaths.map { it.trim('/') }.filter { it.isNotEmpty() }) {
            val candidate = when {
                grant.substringAfterLast('/').equals(wanted, ignoreCase = true) -> grant
                else -> subFolders(grant)
                    .firstOrNull { it.equals(wanted, ignoreCase = true) }
                    ?.let { "$grant/$it" }
            } ?: continue
            if (!isWritableRoot(candidate, isVideo)) return candidate
        }
        return null
    }
}
