package com.gallery.sync.domain.backup

import com.gallery.sync.data.local.entity.AlbumMode

/**
 * Which album modes are on offer for an album, from the cloud its folder goes to — see
 * [CloudCapabilities] and TASK-026. (Named for Google Photos, which was the first cloud to need it.)
 *
 * Sync and Archive act only on what `BackupEntryDao.verifiedInCloud()` calls verified, so they are
 * offered only for a cloud that can be verified. Picking one for a cloud that cannot would be TASK-014's
 * "an action that cannot succeed" — not unsafe, since `verifiedInCloud()` already guards the actual
 * file-removal and rewrite paths regardless, just permanently inert.
 *
 * An album already at Sync or Archive is left exactly as it is when its folder's cloud changes — mirrors
 * [CameraAlbum]'s own precedent for the same shape of rule — so this only ever governs what may be newly
 * *chosen*, never something rewritten out from under a standing choice.
 */
object GooglePhotosDestination {

    fun modesFor(current: BackupLocation): List<AlbumMode> =
        AlbumMode.entries.filter { current.capabilities.allows(it) }

    fun canChoose(current: BackupLocation, mode: AlbumMode): Boolean = current.capabilities.allows(mode)

    /** Clamps a mode that arrives without a direct user choice — seeding, bulk "select all". */
    fun seeded(current: BackupLocation, mode: AlbumMode): AlbumMode =
        if (canChoose(current, mode)) mode else AlbumMode.BACKUP
}
