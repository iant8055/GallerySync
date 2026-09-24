package com.gallery.sync.ui.common

import androidx.annotation.StringRes
import com.gallery.sync.R
import com.gallery.sync.domain.backup.BackupLocation

/** The name of a backup location as the user reads it. One place, so a new provider is one line. */
@StringRes
fun BackupLocation.labelRes(): Int = when (this) {
    BackupLocation.ONEDRIVE -> R.string.backup_location_onedrive
    BackupLocation.GOOGLE_PHOTOS -> R.string.backup_location_google_photos
    BackupLocation.USB_DRIVE -> R.string.backup_location_usb_drive
    BackupLocation.GOOGLE_DRIVE -> R.string.backup_location_google_drive
    BackupLocation.DROPBOX -> R.string.backup_location_dropbox
    BackupLocation.PCLOUD -> R.string.backup_location_pcloud
    BackupLocation.IDRIVE_E2 -> R.string.backup_location_idrive_e2
    BackupLocation.BACKBLAZE_B2 -> R.string.backup_location_backblaze_b2
}
