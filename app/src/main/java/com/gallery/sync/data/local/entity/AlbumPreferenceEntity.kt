package com.gallery.sync.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.gallery.sync.domain.backup.BackupLocation

/**
 * What the user chose for an album.
 *
 * Only albums the user has explicitly touched get a row. An album with no row takes
 * [AlbumMode.DEFAULT].
 *
 * **This table is the one part of the database that cannot be rebuilt.** Everything else — what has
 * been uploaded, byte sizes, remote ids — can be reconstructed by rescanning the phone and asking
 * OneDrive. These rows are pure user intent and exist nowhere else, which is why schema changes
 * here are handled more carefully than elsewhere.
 */
@Entity(tableName = "album_preferences")
data class AlbumPreferenceEntity(

    @PrimaryKey val albumName: String,

    val mode: AlbumMode,

    /**
     * Where this album's uploads go. One destination per album, chosen by the user — never mirrored
     * to more than one. See TASK-026: this is what Ian described from other apps, a folder-to-provider
     * mapping, not a fan-out. [BackupLocation.DEFAULT] (OneDrive) for every album until a second
     * provider actually works and someone chooses it.
     */
    val backupLocation: BackupLocation = BackupLocation.DEFAULT
)
