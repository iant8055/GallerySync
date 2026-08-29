package com.gallery.sync.data.remote.onedrive

import com.gallery.sync.data.remote.onedrive.dto.GraphDriveItemDto
import com.gallery.sync.domain.model.RemoteMediaNode
import java.time.Instant
import java.time.format.DateTimeParseException

/** Used when Graph reports a file with no `mimeType` on its file facet. */
const val DEFAULT_MIME_TYPE = "application/octet-stream"

/**
 * Converts a Graph `driveItem` into a [RemoteMediaNode], or `null` if it is neither a folder nor a
 * file we can represent.
 *
 * Returning `null` is a normal outcome, not an error: Graph mixes package and bundle items into
 * `children` collections, and those carry neither a `folder` nor a `file` facet. Callers drop them.
 *
 * Deliberately a pure function with no Android, Retrofit, or coroutine dependencies so it is unit
 * testable with zero mocks.
 */
fun GraphDriveItemDto.toRemoteMediaNode(): RemoteMediaNode? {
    val itemId = id ?: return null
    val itemName = name ?: return null
    val modifiedAtUtc = parseIso8601ToEpochMillis(lastModifiedDateTime)

    return when {
        folder != null -> RemoteMediaNode.Folder(
            id = itemId,
            name = itemName,
            modifiedAtUtc = modifiedAtUtc,
            childCount = folder.childCount ?: 0,
            sizeBytes = size ?: 0L,
            parentPath = parentReference?.path
        )

        file != null -> RemoteMediaNode.File(
            id = itemId,
            name = itemName,
            modifiedAtUtc = modifiedAtUtc,
            mimeType = file.mimeType ?: DEFAULT_MIME_TYPE,
            // Carried through as-is. `?: 0L` here is what made a file with no reported size look
            // like an empty one to every caller downstream.
            sizeBytes = size,
            widthPx = image?.width,
            heightPx = image?.height,
            eTag = eTag
        )

        else -> null
    }
}

/**
 * Parses an ISO-8601 instant such as `2024-01-15T10:30:00Z` into epoch millis.
 *
 * Returns `0L` for a missing, blank, or unparseable value: a bad timestamp on one item must not
 * fail an entire folder listing, and downstream sorting treats `0L` as "unknown, sort last".
 *
 * [Instant.parse] is API 26+, which matches the project's `minSdk 26`, so no desugaring is needed.
 */
internal fun parseIso8601ToEpochMillis(raw: String?): Long {
    if (raw.isNullOrBlank()) return 0L
    return try {
        Instant.parse(raw).toEpochMilli()
    } catch (_: DateTimeParseException) {
        0L
    }
}
