package com.gallery.sync.domain.repository

import com.gallery.sync.data.remote.onedrive.UploadSource
import com.gallery.sync.domain.model.DataResult
import com.gallery.sync.domain.model.UploadedItem

/**
 * Writes local media into the signed-in user's Google Photos library.
 *
 * Two calls where OneDrive needs one, because that is how the Library API is shaped: raw bytes go
 * up first and earn an opaque **upload token**, then a second call — `mediaItems.batchCreate` —
 * turns that token into a real library item. Nothing is visible in the user's library between the
 * two calls; an interruption after the first and before the second simply strands an unused token,
 * which expires on Google's side. Safe to just retry from the top rather than resume mid-upload —
 * see TASK-026 on why v1 does not attempt the chunked/resumable variant the way OneDrive's uploads do.
 *
 * [UploadSource] and [UploadedItem] are reused from the OneDrive path — both are already
 * provider-neutral (a MediaStore content URI in, an id/name/size out). `UploadedItem.sizeBytes` here
 * is always the **local** file's size, never one Google reported, because Google never reports one —
 * see [GooglePhotosRepository] and TASK-026. Callers must not treat it as a verified remote size.
 */
interface GooglePhotosUploadRepository {

    /**
     * Uploads [source] to the general library — v1 creates no albums and files into none, since
     * GallerySync's own album concept (a phone folder) has no clean equivalent here. A future pass
     * may map a phone folder to a Google Photos album; not built now, kept deliberately small per
     * TASK-026.
     *
     * [onProgress] reports bytes sent during the raw-upload step; the create step that follows is a
     * small JSON call with no meaningful progress of its own.
     */
    suspend fun upload(
        source: UploadSource,
        onProgress: (bytesSent: Long, total: Long) -> Unit = { _, _ -> }
    ): DataResult<UploadedItem>
}
