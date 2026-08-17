package com.gallery.sync.domain.repository

import com.gallery.sync.domain.model.DataResult
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
}
