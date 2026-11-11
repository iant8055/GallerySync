package com.gallery.sync.domain.repository

import com.gallery.sync.domain.backup.BackupLocation
import com.gallery.sync.domain.model.DataResult

/**
 * One Cloud that can give a link to a stored file, for a long-press to open in that Cloud's app or page
 * (TASK-028). A Cloud with no binding here has no link at all — Backblaze B2, IDrive e2 and pCloud — and the
 * long-press downloads a temporary copy instead.
 *
 * **Never a presigned or direct-download URL.** That would hand a working credential to whatever app opens it
 * (CLAUDE.md escalation: data exposure). Only a link that needs the user's own sign-in in the Cloud's app or site.
 */
interface CloudFileLink {

    val location: BackupLocation

    /** A link for the file the Cloud gave [remoteItemId] when it was uploaded. A gone file is `Http(404, …)`. */
    suspend fun linkFor(remoteItemId: String): DataResult<String>
}

/** Finds the [CloudFileLink] for a location. Injected as a set, as [CloudDownloaders] is. */
class CloudFileLinks(private val all: Set<CloudFileLink>) {

    fun of(location: BackupLocation): CloudFileLink? = all.firstOrNull { it.location == location }
}
