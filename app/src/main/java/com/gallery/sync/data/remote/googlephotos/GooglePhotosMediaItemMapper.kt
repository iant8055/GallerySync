package com.gallery.sync.data.remote.googlephotos

import com.gallery.sync.data.remote.googlephotos.dto.MediaItemDto
import com.gallery.sync.domain.repository.GooglePhotosMediaItem
import java.time.Instant
import java.time.format.DateTimeParseException

/**
 * Converts a Library API `mediaItem` into a [GooglePhotosMediaItem], or `null` if it has no id —
 * nothing downstream can key on an item without one.
 *
 * Deliberately a pure function with no Android, Retrofit, or coroutine dependencies so it is unit
 * testable with zero mocks, same as [com.gallery.sync.data.remote.onedrive.toRemoteMediaNode].
 */
fun MediaItemDto.toGooglePhotosMediaItem(): GooglePhotosMediaItem? {
    val itemId = id ?: return null
    return GooglePhotosMediaItem(
        id = itemId,
        filename = filename.orEmpty(),
        creationTimeEpochSeconds = parseCreationTimeEpochSeconds(mediaMetadata?.creationTime)
    )
}

/**
 * Parses an RFC 3339 instant such as `2026-09-22T14:03:11Z` into epoch seconds.
 *
 * Returns `null` for a missing, blank, or unparseable value — unlike OneDrive's equivalent, which
 * has [com.gallery.sync.domain.model.RemoteMediaNode.modifiedAtUtc] to fall back on. This field has
 * no such fallback, so `null` (unknown) is the honest reading rather than a synthetic `0`.
 */
internal fun parseCreationTimeEpochSeconds(raw: String?): Long? {
    if (raw.isNullOrBlank()) return null
    return try {
        Instant.parse(raw).epochSecond
    } catch (_: DateTimeParseException) {
        null
    }
}
