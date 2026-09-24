package com.gallery.sync.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.gallery.sync.domain.backup.BackupLocation

/**
 * Where a top-level Android media folder's uploads go — `DCIM`, `Pictures`, `Movies`, whatever the
 * device actually has, per [com.gallery.sync.data.local.media.MediaScanRules.topLevelFolderOf].
 *
 * Ian, 24 Sept 2026, TASK-026, replacing the single app-wide setting from the day before: a folder
 * is the right granularity, coarser than an individual album ("Camera", "1999 car show" — this app's
 * existing, finer-grained concept, which stays completely uninvolved in destination choice) and finer
 * than one blanket setting for the whole device. The clean-cut reason it settled here rather than
 * per-album: "if the user has multiple cloud storage already... what is the point of bypassing that
 * and putting everything in OneDrive" — a user who already keeps one folder in one cloud service and
 * another folder in a different one should be able to say so once, at the folder level, rather than
 * either being forced through one destination for everything or having to repeat the choice per
 * individual album underneath.
 *
 * Only folders the user has explicitly touched get a row here — same "only what was chosen" shape as
 * [AlbumPreferenceEntity]. A folder with no row falls back to `BackupPreferences.backupLocation`
 * (com.gallery.sync.data.local.settings.BackupSettings), the app-wide default — never a hardcoded guess.
 */
@Entity(tableName = "folder_preferences")
data class FolderPreferenceEntity(

    @PrimaryKey val folderName: String,

    val backupLocation: BackupLocation
)
