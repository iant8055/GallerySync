package com.gallery.sync.domain.repository

import com.gallery.sync.data.remote.onedrive.ResumableSession
import com.gallery.sync.data.remote.onedrive.UploadSource
import com.gallery.sync.domain.model.DataResult
import com.gallery.sync.domain.model.UploadedItem
import java.io.File

/**
 * Writes local media into the user's OneDrive.
 *
 * Deliberately separate from [OneDriveRepository], which is documented as read-only. Keeping the
 * write capability behind its own interface means a caller that only browses cannot accidentally
 * be handed the ability to upload.
 *
 * Nothing here deletes, moves, or overwrites: uploads use `conflictBehavior = rename`, so an
 * existing cloud file is never replaced.
 */
interface OneDriveUploadRepository {

    /**
     * Uploads [localFile] into the drive folder at [remoteFolderPath], e.g.
     * `Samsung Gallery/DCIM/Camera`. Creates the folder path if it does not exist.
     *
     * [onProgress] reports bytes the server has confirmed, so it can drive a truthful progress bar
     * rather than one that races ahead of the network.
     */
    suspend fun upload(
        localFile: File,
        remoteFolderPath: String,
        onProgress: (bytesSent: Long, total: Long) -> Unit = { _, _ -> }
    ): DataResult<UploadedItem>

    /**
     * Uploads an arbitrary [UploadSource] — in practice a MediaStore content URI, which is how the
     * backup reads media under scoped storage.
     *
     * [existingSession] continues an upload an earlier run began, and [onSessionCreated] hands back
     * a newly opened session so the caller can store it before any bytes are sent. Together they are
     * what stops a large file restarting from zero every time a run is cut short; omit both and the
     * upload simply begins afresh.
     */
    suspend fun upload(
        source: UploadSource,
        remoteFolderPath: String,
        onProgress: (bytesSent: Long, total: Long) -> Unit = { _, _ -> },
        existingSession: ResumableSession? = null,
        onSessionCreated: suspend (ResumableSession) -> Unit = {}
    ): DataResult<UploadedItem>
}
