package com.gallery.sync.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A folder in the cloud provider's hierarchy, mirrored into the local index.
 *
 * Like [MediaItemEntity] this is a cache and index only, and for the same reason it declares no
 * `@ForeignKey` on [parentFolderId]: cloud listings arrive out of order, so a child folder is
 * often indexed before its parent.
 */
@Entity(
    tableName = "media_folders",
    indices = [
        Index(value = ["parentFolderId"]),
        Index(value = ["source"])
    ]
)
data class MediaFolderEntity(
    /** Stable composite id, built by [buildId]. */
    @PrimaryKey val id: String,
    /** The provider-native identifier for this folder. */
    val remoteId: String,
    val source: MediaSource,
    val name: String,
    /** Soft reference to another `media_folders.id`; `null` means this is a source root. */
    val parentFolderId: String?,
    val childCount: Int,
    /** Epoch millis, UTC. */
    val modifiedAtUtc: Long,
    /** Provider-reported path, for display only. */
    val path: String
) {
    companion object {
        /**
         * Builds the stable primary key for a folder: `"onedrive:ABC123"`.
         *
         * Uses the same source-namespaced scheme as [MediaItemEntity.buildId].
         */
        fun buildId(source: MediaSource, remoteId: String): String =
            "${source.name.lowercase()}:$remoteId"
    }
}
