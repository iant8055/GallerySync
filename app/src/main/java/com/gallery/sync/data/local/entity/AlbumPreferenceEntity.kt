package com.gallery.sync.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

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

    val mode: AlbumMode
)
