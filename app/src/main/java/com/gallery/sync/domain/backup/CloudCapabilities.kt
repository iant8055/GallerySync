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
 * **Restore** needs only a way to fetch the file back (`CloudDownloader`): it adds a file to the phone and the
 * writer checks the finished download against the size the phone recorded at upload, so it cannot cost anything.
 * **Archive** and **Sync** also need **proof the cloud copy is there by its size**, because they remove or
 * replace the local file: they are turned on for a cloud only when its adapter records the verified remote size
 * (`markUploaded`, not `markUploadedWithoutSizeVerification`) and the removal paths can check it. Never by
 * editing this table alone.
 *
 * Today OneDrive has all three and Dropbox has Restore. Google Photos can never have any: it reports no size
 * for a stored file and gives no reliable way to fetch the original back. Google Drive, IDrive e2, Backblaze
 * B2 and pCloud can, so they are the next to earn them, one cloud at a time.
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
        // Can fetch a file back (`DropboxCloud.openStream`); not yet proved by size, so no Archive or Sync.
        BackupLocation.DROPBOX -> CloudCapabilities(restore = true, archive = false, sync = false)
        else -> CloudCapabilities.BACKUP_ONLY
    }
