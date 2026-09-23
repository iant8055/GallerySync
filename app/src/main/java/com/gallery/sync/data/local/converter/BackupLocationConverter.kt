package com.gallery.sync.data.local.converter

import androidx.room.TypeConverter
import com.gallery.sync.domain.backup.BackupLocation

/**
 * Stores [BackupLocation] as its name, following [AlbumModeConverter].
 *
 * An unreadable value falls back to [BackupLocation.DEFAULT] (OneDrive) — the only location that has
 * ever actually been usable, so a corrupt row reads as what it almost certainly is rather than as
 * something that might silently route uploads somewhere unsigned-in.
 */
class BackupLocationConverter {

    @TypeConverter
    fun fromBackupLocation(location: BackupLocation): String = location.name

    @TypeConverter
    fun toBackupLocation(value: String): BackupLocation = BackupLocation.fromNameOrDefault(value)
}
