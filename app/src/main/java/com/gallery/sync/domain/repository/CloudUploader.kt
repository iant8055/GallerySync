package com.gallery.sync.domain.repository

import com.gallery.sync.data.remote.onedrive.UploadSource
import com.gallery.sync.domain.backup.BackupLocation
import com.gallery.sync.domain.model.DataResult
import com.gallery.sync.domain.model.UploadedItem

/**
 * One cloud the engine can send a file to, other than OneDrive — TASK-026.
 *
 * OneDrive keeps its own richer path (a remote index, byte-size verification, resumable chunks) and is
 * deliberately not forced through this. Everything else is **backup-only**: [UploadedItem.sizeBytes]
 * is the local size, never a verified remote one, and the engine records the row through
 * `markUploadedWithoutSizeVerification` so it can never satisfy `verifiedInCloud()`. That is the rule
 * that keeps Archive, Sync and Restore from ever acting on a copy this app never proved.
 *
 * A failure here must only ever stop *this* cloud's loop — Ian, 23 Sept 2026: a failed run on one
 * provider must never stop another backup.
 */
interface CloudUploader {

    val location: BackupLocation

    /** Whether credentials are present, so a pass can skip a provider the user has not connected. */
    suspend fun isConnected(): Boolean

    /**
     * Sends [source]. [album] is the phone folder it came from, for providers that keep a folder
     * structure (`GallerySync/<album>/<name>`); providers with no folders ignore it.
     */
    suspend fun upload(
        source: UploadSource,
        album: String,
        onProgress: (bytesSent: Long, total: Long) -> Unit = { _, _ -> }
    ): DataResult<UploadedItem>
}

/** Finds the [CloudUploader] for a location. Injected as a set so a new provider is one binding. */
class CloudUploaders(private val all: Set<CloudUploader>) {

    fun of(location: BackupLocation): CloudUploader? = all.firstOrNull { it.location == location }
}
