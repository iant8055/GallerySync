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
     * What is added to a retrieved file name, before the extension.
     *
     * Ian, 25 Aug 2026. Retrieval lists what OneDrive holds rather than what our ledger remembers,
     * so fetching a file the gallery already has is a normal case rather than an edge one, and the
     * copy can land beside the original. The suffix is what tells them apart at a glance.
     */
    const val SUFFIX = "_restored"

    /**
     * The name a retrieved copy is published under.
     *
     * Before the extension, never after: `IMG_0042.mp4_restored` changes the mime type MediaStore
     * infers from the name, and gallery apps stop recognising the file.
     *
     * Idempotent, so fetching an already-suffixed name back does not build `_restored_restored`. A
     * second copy of the same file therefore collides, and MediaStore appends ` (1)` — which is the
     * right outcome, and the reason we choose the first suffix ourselves rather than leaving all the
     * naming to Android.
     */
    fun restoredNameOf(displayName: String): String {
        val dot = displayName.lastIndexOf('.')
        val stem = if (dot > 0) displayName.substring(0, dot) else displayName
        val extension = if (dot > 0) displayName.substring(dot) else ""
        return if (stem.endsWith(SUFFIX)) displayName else "$stem$SUFFIX$extension"
    }

    /** The name a retrieved copy was made from, or [displayName] unchanged if it is not one. */
    fun originalNameOf(displayName: String): String {
        val dot = displayName.lastIndexOf('.')
        val stem = if (dot > 0) displayName.substring(0, dot) else displayName
        val extension = if (dot > 0) displayName.substring(dot) else ""
        return if (stem.endsWith(SUFFIX)) stem.removeSuffix(SUFFIX) + extension else displayName
    }

    /**
     * How the app recognises the same content wherever it now sits.
     *
     * Name and size, with any [SUFFIX] taken off first. **The suffix is why this function exists.**
     * Three separate places test `name|size` to answer "is this content on the phone?" — the ledger
     * key, the pass that clears a row missing flag, and the last check before a cloud copy goes to
     * the recycle bin. Without the strip, a file the user has just fetched back still reads as gone:
     * its row stays flagged missing, becomes a cloud deletion candidate, and the guard that should
     * have caught that is blinded by the same rename.
     *
     * Size still has to match, so a user file genuinely called `holiday_restored.mp4` would have to
     * be byte-identical to a ledger row to collide. If it ever were, this fails in the safe
     * direction: we read the file as back, and decline to delete anything.
     *
     * A *second* retrieved copy carries the MediaStore ` (1)` and will not match. That costs
     * nothing: the first copy already cleared the flag.
     */
    fun contentSignature(displayName: String, sizeBytes: Long): String =
        "${originalNameOf(displayName)}|$sizeBytes"

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
