package com.gallery.sync.data.remote.googlephotos.dto

import kotlinx.serialization.Serializable

/** Body of `mediaItems:batchCreate`. v1 sends exactly one item per call — see TASK-026 on why. */
@Serializable
data class BatchCreateRequestDto(
    val newMediaItems: List<NewMediaItemDto>
)

@Serializable
data class NewMediaItemDto(
    val simpleMediaItem: SimpleMediaItemDto
)

@Serializable
data class SimpleMediaItemDto(
    /** The opaque token `/v1/uploads` returned for the raw bytes already sent. */
    val uploadToken: String,
    val fileName: String
)

/** Response to `mediaItems:batchCreate`. */
@Serializable
data class BatchCreateResponseDto(
    val newMediaItemResults: List<NewMediaItemResultDto>? = null
)

@Serializable
data class NewMediaItemResultDto(
    val uploadToken: String? = null,
    val status: BatchCreateStatusDto? = null,
    val mediaItem: MediaItemDto? = null
)

/**
 * Per-item outcome inside a batch response. `code` is a `google.rpc.Code` value: absent or `0`
 * means success, matching how the rest of this Dto's siblings treat "field absent" as the
 * unremarkable case.
 */
@Serializable
data class BatchCreateStatusDto(
    val message: String? = null,
    val code: Int? = null
)
