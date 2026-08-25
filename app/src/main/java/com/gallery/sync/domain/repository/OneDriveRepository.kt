package com.gallery.sync.domain.repository

import com.gallery.sync.domain.model.DataResult
import java.io.InputStream
import com.gallery.sync.domain.model.FolderPage

/**
 * Read-only access to the user's OneDrive folder tree.
 *
 * Implementations of this interface are the **only** place in the app permitted to make network
 * calls (CLAUDE.md hard rule). ViewModels, workers and the ContentProvider go through here.
 *
 * v0.1.0 is browse-only: no downloading, uploading, or deleting.
 */
interface OneDriveRepository {

    /** Lists the immediate children of the drive root. */
    suspend fun listRoot(): DataResult<FolderPage>

    /** Lists the immediate children of [folderId]. */
    suspend fun listFolder(folderId: String): DataResult<FolderPage>

    /**
     * Lists a folder by path, e.g. `Samsung Gallery/DCIM/Camera`.
     *
     * A folder that does not exist yet returns an empty page rather than a failure — for the
     * backup, "nothing is there" and "the folder has not been created" mean the same thing.
     */
    suspend fun listFolderByPath(path: String): DataResult<FolderPage>

    /** Fetches the page identified by a [FolderPage.nextPageToken] from a previous call. */
    suspend fun listNextPage(nextPageToken: String): DataResult<FolderPage>

    /**
     * Opens the bytes of one cloud file for reading.
     *
     * A read, which is why it belongs on this interface rather than the upload one — fetching a file
     * back changes nothing in OneDrive, and the same file can be fetched as many times as the user
     * likes.
     *
     * The caller owns the returned stream and must close it. A `java.io.InputStream` rather than an
     * OkHttp body, so no network type escapes the data layer.
     */
    suspend fun openStream(itemId: String): DataResult<InputStream>
}
