package com.gallery.sync.data.remote.onedrive.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Body of `createUploadSession`. */
@Serializable
data class CreateUploadSessionRequestDto(
    val item: UploadablePropertiesDto = UploadablePropertiesDto()
)

@Serializable
data class UploadablePropertiesDto(
    /**
     * **`rename`, never `replace`.**
     *
     * CLAUDE.md forbids destroying a user's cloud file. Two devices can easily produce the same
     * camera filename, and `replace` would silently overwrite whichever photo got there first —
     * an unrecoverable loss of someone's picture. Renaming leaves both intact and is a problem a
     * human can sort out later.
     */
    @SerialName("@microsoft.graph.conflictBehavior")
    val conflictBehavior: String = CONFLICT_BEHAVIOUR_RENAME
) {
    companion object {
        const val CONFLICT_BEHAVIOUR_RENAME = "rename"
    }
}

/** Response to `createUploadSession`. [uploadUrl] is pre-authorised and short-lived. */
@Serializable
data class UploadSessionDto(
    val uploadUrl: String,
    val expirationDateTime: String? = null
)

/**
 * Response to an accepted intermediate chunk (HTTP 202).
 *
 * [nextExpectedRanges] entries look like `"26214400-"` — an open-ended start offset. The server's
 * answer here is authoritative on resume; a locally tracked offset can be wrong if a chunk was
 * only partially received.
 */
@Serializable
data class ChunkAcceptedDto(
    val nextExpectedRanges: List<String>? = null,
    val expirationDateTime: String? = null
)

/** The `driveItem` returned when the final chunk completes the upload. */
@Serializable
data class UploadedItemDto(
    val id: String? = null,
    val name: String? = null,
    val size: Long? = null,
    val eTag: String? = null
)
