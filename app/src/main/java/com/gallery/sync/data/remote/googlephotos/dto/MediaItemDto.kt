package com.gallery.sync.data.remote.googlephotos.dto

import kotlinx.serialization.Serializable

/** One item as `mediaItems.list` or `mediaItems:batchCreate` describes it. No size anywhere — see TASK-026. */
@Serializable
data class MediaItemDto(
    val id: String? = null,
    val filename: String? = null,
    val mediaMetadata: MediaMetadataDto? = null
)

@Serializable
data class MediaMetadataDto(
    /** RFC 3339 UTC, e.g. `"2026-09-22T14:03:11Z"`. Parsed by the mapper, never here. */
    val creationTime: String? = null
)

/** Response to `GET /v1/mediaItems`. */
@Serializable
data class MediaItemsListResponseDto(
    val mediaItems: List<MediaItemDto>? = null,
    val nextPageToken: String? = null
)
