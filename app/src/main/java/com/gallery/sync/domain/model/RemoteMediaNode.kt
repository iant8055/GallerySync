package com.gallery.sync.domain.model

/**
 * A single entry in a cloud provider's folder tree, as returned by a remote listing.
 *
 * This is a *remote* description only. Nothing here implies the content exists on device: caching
 * is a separate concern owned by the sync layer, and a node is never a source-of-truth write
 * target.
 *
 * Pure Kotlin by design — `domain/` carries no Android or networking dependencies.
 */
sealed interface RemoteMediaNode {

    /** The provider-native identifier for this node. */
    val id: String

    /** Display name, e.g. `Holiday 2024` or `IMG_0042.jpg`. */
    val name: String

    /** Epoch millis, UTC. `0L` when the provider omitted or malformed the timestamp. */
    val modifiedAtUtc: Long

    data class Folder(
        override val id: String,
        override val name: String,
        override val modifiedAtUtc: Long,
        /** Number of direct children the provider reports; `0` when unreported. */
        val childCount: Int,
        /**
         * Total size of everything inside, as the provider reports it; `0` when unreported.
         *
         * Free: Graph returns `size` on a folder item and the listing already selects it. Carried so
         * the restore screen can say what a folder holds without listing it — one request per folder
         * across ninety albums is a screen nobody waits for.
         */
        val sizeBytes: Long,
        /** Provider path of the parent, e.g. `/drive/root:/Pictures`; `null` when unreported. */
        val parentPath: String?
    ) : RemoteMediaNode

    data class File(
        override val id: String,
        override val name: String,
        override val modifiedAtUtc: Long,
        /** e.g. `image/jpeg`. Falls back to `application/octet-stream` when unreported. */
        val mimeType: String,
        val sizeBytes: Long,
        /** Pixel width, when the provider exposes an image facet for this file. */
        val widthPx: Int?,
        /** Pixel height, when the provider exposes an image facet for this file. */
        val heightPx: Int?,
        /** Provider change-detection token. */
        val eTag: String?
    ) : RemoteMediaNode
}
