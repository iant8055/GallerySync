package com.gallery.sync.domain.backup

import com.gallery.sync.data.local.entity.AlbumMode

/**
 * While the app-wide destination is Google Photos, Sync and Archive are not offered as choices for
 * any album — see TASK-026. Neither is safe to promise: Google Photos never reports a stored file's
 * size, so `BackupEntryDao.verifiedInCloud()` can never say a row sent there is confirmed, and both
 * modes act only on what that query calls verified. Picking either while routed to Google Photos
 * would be TASK-014's "an action that cannot succeed" — not unsafe, since `verifiedInCloud()` already
 * guards the actual file-removal/rewrite paths regardless of this restriction, just permanently inert
 * for anything uploaded from here on.
 *
 * An album already at Sync or Archive is left exactly as it is when the setting changes — mirrors
 * [CameraAlbum]'s own precedent for the same shape of rule — so this only ever governs what may be
 * newly *chosen*, never something rewritten out from under a standing choice. Files that album already
 * sent to OneDrive before the switch stay exactly as eligible as they always were: eligibility is
 * per-file, via `verifiedInCloud()`, not per-album and not tied to the current destination setting.
 */
object GooglePhotosDestination {

    fun modesFor(current: BackupLocation): List<AlbumMode> =
        if (current == BackupLocation.GOOGLE_PHOTOS) ALLOWED else AlbumMode.entries.toList()

    fun canChoose(current: BackupLocation, mode: AlbumMode): Boolean = mode in modesFor(current)

    /** Clamps a mode that arrives without a direct user choice — seeding, bulk "select all". */
    fun seeded(current: BackupLocation, mode: AlbumMode): AlbumMode =
        if (canChoose(current, mode)) mode else AlbumMode.BACKUP

    private val ALLOWED = listOf(AlbumMode.OFF, AlbumMode.BACKUP)
}
