package com.gallery.sync.domain.backup

import com.gallery.sync.data.local.entity.AlbumMode

/** A feature that depends on which cloud holds the backup. */
enum class CloudFeature { RESTORE, ARCHIVE, SYNC }

/**
 * What one cloud can do beyond taking a copy. Ian, 24 Sept 2026: *"All modes and functionality should
 * now be based on the cloud service."* Every screen and rule that used to say "OneDrive" or "backup-only"
 * now asks this instead: which album modes are on offer, whether the Restore and Archive tabs are live,
 * whether a plan that optimises can be chosen.
 *
 * All three rest on the same thing: **the cloud can prove a copy is there by its size**, and the app has
 * a way to fetch it back. Archive removes the local file only after that proof, Sync shrinks it only after
 * it, and Restore downloads it. So a capability is turned on for a cloud only when its adapter records the
 * verified remote size (`markUploaded`, not `markUploadedWithoutSizeVerification`) *and* Restore can
 * download from it — never by editing this table alone.
 *
 * Today that is OneDrive only. Google Photos can never have them: it reports no size for a stored file.
 * Google Drive, Dropbox, IDrive e2, Backblaze B2 and pCloud can report one, so they are the next to earn
 * these, one cloud at a time.
 */
data class CloudCapabilities(val restore: Boolean, val archive: Boolean, val sync: Boolean) {

    fun supports(feature: CloudFeature): Boolean = when (feature) {
        CloudFeature.RESTORE -> restore
        CloudFeature.ARCHIVE -> archive
        CloudFeature.SYNC -> sync
    }

    /** Off and Backup are always available; Sync and Archive follow the cloud. */
    fun allows(mode: AlbumMode): Boolean = when (mode) {
        AlbumMode.OFF, AlbumMode.BACKUP -> true
        AlbumMode.SYNC -> sync
        AlbumMode.ARCHIVE -> archive
    }

    companion object {
        val FULL = CloudCapabilities(restore = true, archive = true, sync = true)
        val BACKUP_ONLY = CloudCapabilities(restore = false, archive = false, sync = false)
    }
}

/** The one table. See [CloudCapabilities] for when a row may change. */
val BackupLocation.capabilities: CloudCapabilities
    get() = when (this) {
        BackupLocation.ONEDRIVE -> CloudCapabilities.FULL
        else -> CloudCapabilities.BACKUP_ONLY
    }
