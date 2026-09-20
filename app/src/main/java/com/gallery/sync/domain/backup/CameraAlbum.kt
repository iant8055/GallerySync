package com.gallery.sync.domain.backup

import com.gallery.sync.data.local.entity.AlbumMode

/**
 * The phone's own Camera album, and the one rule that sets it apart: **it is never a Sync album.**
 *
 * Ian, 20 Sept 2026: *"I don't want a user to take a picture/video and then BAM it's optimized
 * already."* Sync optimises a file as soon as OneDrive has it, which is exactly right for a folder the
 * user has set aside and exactly wrong for the one the shutter writes to. So the Camera album offers
 * Off, Backup and Archive; Sync is not on its menu and nothing that seeds a mode may write it.
 *
 * What replaces Sync there is a one-shot, manual optimise of what is already old, chosen in the
 * album's own screen: see [CameraOptimisePlan]. It is not a mode and it is not a standing rule.
 *
 * ### Which album this is
 *
 * By name, ignoring case: the scanner names an album by the last folder of its path, so
 * `DCIM/Camera` is `Camera`, and `DCIM/camera` (the same directory, spelled by another writer) is
 * `camera` until the reconciler merges them. A second folder that happens to be called Camera under
 * another parent is treated the same way, which is the safe direction: it is offered less.
 *
 * ### What this does not do
 *
 * An album already at Sync is left as it is. The restriction governs what can be *chosen* and what
 * can be *seeded*; rewriting a choice the user made earlier would be this code setting a mode, which
 * only the user may do.
 */
object CameraAlbum {

    const val NAME = "Camera"

    fun isCamera(album: String): Boolean = album.equals(NAME, ignoreCase = true)

    /** The modes [album] may be given from the Albums tab, in menu order. */
    fun modesFor(album: String): List<AlbumMode> =
        if (isCamera(album)) AlbumMode.entries.filter { it != AlbumMode.SYNC } else AlbumMode.entries

    fun canChoose(album: String, mode: AlbumMode): Boolean = mode in modesFor(album)

    /**
     * [mode] as it may be written for [album] by something other than a direct choice: seeding a new
     * album, or Select all. Sync becomes Backup, the nearest thing that still keeps the files safe.
     * Anything else passes through unchanged, so Off stays Off and Archive is never invented.
     */
    fun seeded(album: String, mode: AlbumMode): AlbumMode =
        if (canChoose(album, mode)) mode else AlbumMode.BACKUP
}
