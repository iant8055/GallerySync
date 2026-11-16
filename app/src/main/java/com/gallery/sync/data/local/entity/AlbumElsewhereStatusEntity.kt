package com.gallery.sync.data.local.entity

import androidx.room.Entity
import com.gallery.sync.domain.backup.BackupLocation

/**
 * What one Cloud other than OneDrive said about one album, the last time it was asked (Ian, 16 Nov 2026: *"every Cloud
 * that can be checked - per folder like OneDrive"*).
 *
 * The same idea as [AlbumCloudStatusEntity], one row per album **and** Cloud, because an album's files can sit in more
 * than one. Derived and disposable: asking the Cloud again rebuilds it. **Display only** — nothing reads it to decide
 * whether a file is safe, uploaded or removable; Archive still asks the Cloud live about each file.
 *
 * A file counts as verified when the Cloud holds the object recorded at upload, at exactly the local size: the same bar
 * Archive uses. No row means never checked, and the Albums tab falls back to "N sent to <Cloud>".
 */
@Entity(tableName = "album_elsewhere_status", primaryKeys = ["albumName", "location"])
data class AlbumElsewhereStatusEntity(
    val albumName: String,
    val location: BackupLocation,
    val checkedAtEpochMillis: Long,
    /** Files the Cloud holds at the local size. */
    val verifiedFiles: Int,
    /** Files the Cloud answered for and does not hold at the right size: gone, in its trash, or a different size. */
    val missingFiles: Int,
    /** Files that were not answered for this time. Neither verified nor missing. */
    val uncheckedFiles: Int,
    /** True when the album's folder could not be listed at all, so the counts describe nothing. */
    val couldNotCheck: Boolean
)
