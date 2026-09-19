package com.gallery.sync.domain.backup

import com.gallery.sync.data.local.media.RestoredAlbum

/**
 * An edited photo is not a deleted photo.
 *
 * ### The false positive this exists to prevent
 *
 * The ledger identifies a file by its folder, name, size and modified time. Saving an edit over
 * `IMG_1234.jpg` changes the last two, so the row for the original stops matching anything on the
 * phone and is marked missing, while the edited file goes up as a new one. Nothing was deleted, yet
 * the original's cloud copy would be offered for removal on a list headed "gone from this phone",
 * with a file of exactly that name still sitting in the gallery. It would happen on every in-place
 * edit. Ian, 19 Sept 2026: *"we can't have every edited file look like a deletion."*
 *
 * ### The rule
 *
 * A file is offered only when nothing of that name is left in its folder on the phone. The cloud
 * copy of an edited photo's original is harmless, since it is the unedited original, and the user
 * can remove it by hand.
 *
 * A file moved to another folder is a different case and is already caught elsewhere, by
 * [RestoredAlbum.contentSignature] (name and size, anywhere on the phone).
 *
 * ### Which way it errs
 *
 * Toward not offering. A brand-new photo that happens to reuse a deleted photo's name in the same
 * folder hides the old one from the list. That costs a cloud copy left behind, which is the setting
 * hardest to regret; the opposite mistake costs the only copy of a photo.
 *
 * Pure and Android-free so it is tested without a device.
 */
object EditedInPlace {

    /**
     * Folder and name, as one comparable key.
     *
     * The folder is compared without regard to case because MediaStore keeps each writer's spelling
     * (`camera` and `Camera` are one directory). The name is compared as written but with a
     * `_restored` suffix taken off, so a file the user has fetched back counts as present.
     */
    fun keyOf(album: String, displayName: String): String =
        album.lowercase() + "/" + RestoredAlbum.originalNameOf(displayName)

    /** The keys of everything currently on the phone. */
    fun keysOf(present: Iterable<Pair<String, String>>): Set<String> =
        present.mapTo(HashSet()) { (album, name) -> keyOf(album, name) }

    /** Whether a file of this folder and name is still on the phone, whatever its size. */
    fun isStillHere(album: String, displayName: String, presentKeys: Set<String>): Boolean =
        keyOf(album, displayName) in presentKeys
}
