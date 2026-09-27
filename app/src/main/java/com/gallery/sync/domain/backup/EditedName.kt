package com.gallery.sync.domain.backup

import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * The name an edited photo or video is sent under, so an edit is always a new file in the Cloud and never
 * replaces the original there (Ian, 26 Sept 2026: "treat the edited optimized file as a new file").
 *
 * OneDrive would rename a clash by itself, but Dropbox and the S3 Clouds write to a fixed name and replace what is
 * there, so the name is made different here, once, for every Cloud. It carries the edit's own modified time, which
 * makes it unique per edit and the same on a retry, so a re-sent edit lands on the same edited copy and not a third.
 */
object EditedName {

    private val stamp = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneOffset.UTC)

    fun of(displayName: String, modifiedEpochSeconds: Long): String {
        val dot = displayName.lastIndexOf('.')
        val base = if (dot > 0) displayName.substring(0, dot) else displayName
        val extension = if (dot > 0) displayName.substring(dot) else ""
        return "$base (edited ${stamp.format(Instant.ofEpochSecond(modifiedEpochSeconds))})$extension"
    }
}
