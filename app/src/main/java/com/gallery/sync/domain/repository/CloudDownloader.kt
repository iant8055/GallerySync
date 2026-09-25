package com.gallery.sync.domain.repository

import com.gallery.sync.domain.backup.BackupLocation
import com.gallery.sync.domain.model.DataResult
import java.io.InputStream

/**
 * One cloud the app can fetch a file back from, other than OneDrive — TASK-026, Restore for the other clouds.
 *
 * The other half of [CloudUploader]. It is deliberately small: given the id the cloud handed back when the
 * file was uploaded (recorded in the ledger as `remoteItemId`), open the file's bytes. Everything else about a
 * restore — where the file lands, the size check, re-pointing the ledger row — is the same for every cloud and
 * lives in `DownloadMissingFile`.
 *
 * Restore needs no proof of the stored size, because it only ever *adds* a file to the phone and the writer
 * checks the finished download against the size the phone recorded when it uploaded. Archive and Sync do
 * need that proof, since they remove or replace the local file, so a cloud gaining this interface does not
 * gain them. See `CloudCapabilities`.
 */
interface CloudDownloader {

    val location: BackupLocation

    /**
     * The file's bytes. The caller owns the stream and must close it. A missing file is
     * `RemoteError.Http(404, …)`, which the restore reports as gone from the cloud.
     */
    suspend fun openStream(remoteItemId: String): DataResult<InputStream>
}

/** Finds the [CloudDownloader] for a location. Injected as a set so a new cloud is one binding. */
class CloudDownloaders(private val all: Set<CloudDownloader>) {

    fun of(location: BackupLocation): CloudDownloader? = all.firstOrNull { it.location == location }
}
