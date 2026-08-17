package com.gallery.sync.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A single cloud-hosted photo or video in the local index.
 *
 * This table is a **cache and index only**. It is never the source of truth: rows describe remote
 * content, and [localCachePath] describes an evictable local copy. Removing a row — or nulling
 * [localCachePath] — never deletes anything in the user's cloud account.
 *
 * There is deliberately **no** `@ForeignKey` to `media_folders`. Cloud listings arrive out of
 * order, so a child item is frequently indexed before its parent folder is; a real FK constraint
 * would reject that insert. [parentFolderId] is therefore a soft reference to
 * `media_folders.id`, enforced by the sync layer rather than by SQLite.
 */
@Entity(
    tableName = "media_items",
    indices = [
        Index(value = ["parentFolderId"]),
        Index(value = ["source"]),
        Index(value = ["lastAccessedUtc"])
    ]
)
data class MediaItemEntity(
    /** Stable composite id, built by [buildId]. */
    @PrimaryKey val id: String,
    /** The provider-native identifier for this item. */
    val remoteId: String,
    val source: MediaSource,
    val name: String,
    /** e.g. `image/jpeg`. */
    val mimeType: String,
    val sizeBytes: Long,
    val widthPx: Int?,
    val heightPx: Int?,
    /** Epoch millis, UTC. */
    val createdAtUtc: Long?,
    /** Epoch millis, UTC. */
    val modifiedAtUtc: Long,
    /** Soft reference to `media_folders.id`; `null` means the item lives at the source root. */
    val parentFolderId: String?,
    /** Provider change-detection token. */
    val eTag: String?,
    /** Absolute path of the local cached copy; `null` means not cached. */
    val localCachePath: String?,
    /** Epoch millis, UTC, when the local copy was written. */
    val cachedAtUtc: Long?,
    /** Epoch millis, UTC. Drives LRU cache eviction in v0.2.0. */
    val lastAccessedUtc: Long?
) {
    companion object {
        /**
         * Builds the stable primary key for an item: `"onedrive:ABC123"`.
         *
         * Namespacing by [MediaSource] keeps ids collision-free when two providers happen to hand
         * out the same [remoteId].
         */
        fun buildId(source: MediaSource, remoteId: String): String =
            "${source.name.lowercase()}:$remoteId"
    }
}
