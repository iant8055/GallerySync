package com.gallery.sync.data.local.media

/**
 * Where files fetched back from the cloud land.
 *
 * ### Why it is its own album, and a hidden one
 *
 * Ian, 24 Aug 2026: retrieved files go to a Restored album that does not appear in the album list.
 *
 * Hiding it is not tidiness. Under the opt-in gate an album with no mode is never eligible, so a
 * hidden album cannot be given one and cannot upload — which closes a loop that would otherwise be
 * ugly: a file removed by Archive, fetched back, then seen as new local media and archived again.
 *
 * ### It must be excluded from the scan, not merely from the list
 *
 * Restored files came *from* OneDrive, so they are already backed up. But they arrive in an album
 * whose remote path does not exist, so the skip-existing check would find nothing there and upload
 * every one of them a second time — paying quota and transfer to store a duplicate of a file the
 * user already had. Excluding the album from the scan is what prevents that.
 */
object RestoredAlbum {

    /** Under DCIM so it sits inside the folder people normally grant, rather than needing another. */
    const val RELATIVE_PATH = "DCIM/Restored"

    /** What MediaStore will report as the bucket name, and what a gallery app will show. */
    const val NAME = "Restored"

    /**
     * Whether a scanned item belongs to this album and must therefore be left alone.
     *
     * Checks the path rather than the album name: a user with their own folder called "Restored"
     * somewhere else should keep backing it up, and only the one this app writes to is exempt.
     * Falls back to the bucket name only when there is no path at all, which is the pre-API-29 case.
     */
    fun isRestored(relativePath: String?, album: String): Boolean {
        val path = relativePath?.trim('/')
        return if (path != null) path == RELATIVE_PATH else album == NAME
    }
}
