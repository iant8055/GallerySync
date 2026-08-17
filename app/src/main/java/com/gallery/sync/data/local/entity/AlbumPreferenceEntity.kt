package com.gallery.sync.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Whether an album is backed up, as the user chose.
 *
 * Only albums the user has explicitly touched get a row. An album with no row takes
 * [DEFAULT_ENABLED].
 */
@Entity(tableName = "album_preferences")
data class AlbumPreferenceEntity(

    @PrimaryKey val albumName: String,

    val isEnabled: Boolean
) {
    companion object {

        /**
         * A newly-discovered album is backed up unless the user says otherwise.
         *
         * The opposite default would be worse: someone creates an album, assumes an app whose whole
         * purpose is backup is backing it up, and silently loses it. Making the failure mode
         * "uploaded something you didn't need" rather than "lost something you did" is the right
         * way round. Every album is listed with a toggle, so nothing is hidden.
         */
        const val DEFAULT_ENABLED = true
    }
}
