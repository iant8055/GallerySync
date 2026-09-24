package com.gallery.sync.data.repository

import com.gallery.sync.data.remote.auth.GooglePhotosTokenProvider
import com.gallery.sync.data.remote.onedrive.UploadSource
import com.gallery.sync.domain.backup.BackupLocation
import com.gallery.sync.domain.model.DataResult
import com.gallery.sync.domain.model.UploadedItem
import com.gallery.sync.domain.repository.CloudUploader
import com.gallery.sync.domain.repository.GooglePhotosUploadRepository
import javax.inject.Inject

/** Google Photos as a [CloudUploader]. It files nothing into albums, so [album] is ignored. */
class GooglePhotosCloudUploader @Inject constructor(
    private val repository: GooglePhotosUploadRepository,
    private val tokens: GooglePhotosTokenProvider
) : CloudUploader {

    override val location = BackupLocation.GOOGLE_PHOTOS

    override suspend fun isConnected(): Boolean = tokens.getAccessToken() != null

    override suspend fun upload(
        source: UploadSource,
        album: String,
        onProgress: (bytesSent: Long, total: Long) -> Unit
    ): DataResult<UploadedItem> = repository.upload(source, onProgress)
}
