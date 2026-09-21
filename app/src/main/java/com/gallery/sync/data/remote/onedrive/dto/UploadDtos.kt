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

        /**
         * The first thing every upload asks for, since 20 Sept 2026. A name that is already taken then
         * comes back as a 409 the uploader can look into, instead of a silent " 1" copy. See
         * `ChunkedUploader.upload`.
         */
        const val CONFLICT_BEHAVIOUR_FAIL = "fail"

        /**
         * **Sent in exactly one situation: to fill an empty placeholder that an interrupted upload left.**
         *
         * `createUploadSession` makes a zero-byte item under the file's name at once, and keeps it until
         * the session finishes or expires. Kill the app in that moment, before it has read the session
         * back, and the placeholder is orphaned: the next attempt is told the name is taken, by a file of
         * 0 bytes. Renaming around it (as `rename` does) leaves the empty file under the real name and
         * files the photo as " 1", which is what a repeated-kill test on the Moto G produced on
         * 20 Sept 2026.
         *
         * Replacing **nothing** loses nothing. The uploader only asks for this after reading the item at
         * that path and finding it empty, and it sends the item's eTag as `If-Match` so that it cannot
         * fill a file that has gained content in the meantime. A file with any bytes in it is never
         * replaced: that case is `rename`, as before. The app also never uploads an empty file itself
         * (`UploadOutcome.EmptySource`), so an empty item under one of its names is not its own data.
         */
        const val CONFLICT_BEHAVIOUR_REPLACE = "replace"
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
