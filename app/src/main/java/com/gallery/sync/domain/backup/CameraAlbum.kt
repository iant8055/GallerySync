package com.gallery.sync.domain.backup

import com.gallery.sync.data.local.entity.AlbumMode

/**
 * The camera folder, if the person has chosen one, and the one rule that sets it apart: **it is never a Sync
 * album.**
 *
 * Ian, 20 Sept 2026: *"I don't want a user to take a picture/video and then BAM it's optimized already."* Sync
 * optimises a file as soon as OneDrive has it, which is exactly right for a folder the user has set aside and
 * exactly wrong for the one the shutter writes to. So the camera folder offers Off, Backup and Archive; Sync is
 * not on its menu and nothing that seeds a mode may write it. In place of Sync it has a manual optimise in its
 * own file list: see [CameraSelection].
 *
 * ### There is no camera folder until the person chooses one (Ian, 27 Sept 2026)
 *
 * Settings has *Special settings for Camera*, **off** out of the box, and a folder picker under it. Ian: the
 * switch starts at No *"otherwise we are assuming what folder the system is saving camera to"*. Until it is
 * on and a folder is picked, every album is an ordinary one, including one named Camera, and Sync is on its
 * menu like any other. This replaced the 20 Sept rule that the album named Camera was always the camera folder.
 *
 * Every function here takes the chosen folder, or null when there is none; the caller works that out from
 * Settings with [chosen].
 *
 * ### Matching
 *
 * By name, ignoring case: the scanner names an album by the last folder of its path, so `DCIM/Camera` is
 * `Camera`, and `DCIM/camera` (the same directory, spelled by another writer) is `camera` until the reconciler
 * merges them. A second folder with the same name under another parent is treated the same way, which is the
 * safe direction: it is offered less.
 *
 * ### What this does not do
 *
 * An album already at Sync is left as it is when it becomes the camera folder. The restriction governs what can
 * be *chosen* and what can be *seeded*; rewriting a choice the user made earlier would be this code setting a
 * mode, which only the user may do.
 */
object CameraAlbum {

    /** The camera folder Settings describes: the picked folder while the switch is on, otherwise none. */
    fun chosen(enabled: Boolean, folder: String): String? = folder.takeIf { enabled && it.isNotBlank() }

    /**
     * The album name for a folder picked with Android's folder picker (*Other…* in Settings), from the picked tree's
     * document id, such as `primary:DCIM/OpenCamera`: its last folder, which is how the scanner names albums. Null
     * for a storage root, which is not an album.
     */
    fun folderNameFromTreeDocumentId(documentId: String): String? =
        documentId.substringAfter(':').trimEnd('/').substringAfterLast('/').takeIf { it.isNotBlank() }

    fun isCamera(album: String, cameraFolder: String?): Boolean =
        cameraFolder != null && album.equals(cameraFolder, ignoreCase = true)

    /** The modes [album] may be given from the Albums tab, in menu order. */
    fun modesFor(album: String, cameraFolder: String?): List<AlbumMode> =
        if (isCamera(album, cameraFolder)) AlbumMode.entries.filter { it != AlbumMode.SYNC } else AlbumMode.entries

    fun canChoose(album: String, mode: AlbumMode, cameraFolder: String?): Boolean =
        mode in modesFor(album, cameraFolder)

    /**
     * [mode] as it may be written for [album] by something other than a direct choice: seeding a new
     * album, or Select all. Sync becomes Backup, the nearest thing that still keeps the files safe.
     * Anything else passes through unchanged, so Off stays Off and Archive is never invented.
     */
    fun seeded(album: String, mode: AlbumMode, cameraFolder: String?): AlbumMode =
        if (canChoose(album, mode, cameraFolder)) mode else AlbumMode.BACKUP
}
