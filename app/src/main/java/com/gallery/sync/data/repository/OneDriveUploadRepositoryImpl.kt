package com.gallery.sync.data.repository

import com.gallery.sync.data.remote.auth.OneDriveTokenProvider
import com.gallery.sync.data.remote.onedrive.ChunkedUploader
import com.gallery.sync.data.remote.onedrive.GraphUploadService
import com.gallery.sync.data.remote.onedrive.dto.CreateFolderRequestDto
import com.gallery.sync.data.remote.onedrive.toRemoteMediaNode
import com.gallery.sync.data.remote.onedrive.FileUploadSource
import com.gallery.sync.data.remote.onedrive.ResumableSession
import com.gallery.sync.data.remote.onedrive.UploadOutcome
import com.gallery.sync.data.remote.onedrive.UploadSource
import com.gallery.sync.di.IoDispatcher
import com.gallery.sync.domain.model.DataResult
import com.gallery.sync.domain.model.RemoteError
import com.gallery.sync.domain.model.RemoteMediaNode
import com.gallery.sync.domain.model.UploadedItem
import com.gallery.sync.domain.repository.OneDriveUploadRepository
import com.gallery.sync.util.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.File
import java.io.EOFException
import java.io.FileNotFoundException
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Microsoft Graph implementation of [OneDriveUploadRepository].
 *
 * Mirrors [OneDriveRepositoryImpl]: the network boundary lives here, and no Retrofit or OkHttp
 * type escapes the data layer. Nothing in this file logs a token.
 */
@Singleton
class OneDriveUploadRepositoryImpl @Inject constructor(
    private val uploader: ChunkedUploader,
    private val uploadService: GraphUploadService,
    private val tokenProvider: OneDriveTokenProvider,
    @param:IoDispatcher private val dispatcher: CoroutineDispatcher
) : OneDriveUploadRepository {

    override suspend fun upload(
        localFile: File,
        remoteFolderPath: String,
        onProgress: (bytesSent: Long, total: Long) -> Unit
    ): DataResult<UploadedItem> {
        if (!localFile.exists()) {
            // The scanner can hand us a file the user deleted moments ago. That is expected
            // churn, not an error worth retrying forever.
            Logger.w(TAG, "upload: file no longer exists, skipping")
            return DataResult.Failure(RemoteError.Unknown(IOException("file missing")))
        }
        return upload(FileUploadSource(localFile), remoteFolderPath, onProgress)
    }

    override suspend fun upload(
        source: UploadSource,
        remoteFolderPath: String,
        onProgress: (bytesSent: Long, total: Long) -> Unit,
        existingSession: ResumableSession?,
        onSessionCreated: suspend (ResumableSession) -> Unit
    ): DataResult<UploadedItem> = withContext(dispatcher) {

        if (tokenProvider.getAccessToken() == null) {
            Logger.w(TAG, "upload: no access token, skipping network call")
            return@withContext DataResult.Failure(RemoteError.NoToken)
        }

        try {
            val outcome = uploader.upload(
                source = source,
                remoteFolderPath = remoteFolderPath,
                onProgress = onProgress,
                existingSession = existingSession,
                onSessionCreated = onSessionCreated
            )
            when (outcome) {
                is UploadOutcome.Success -> {
                    val item = outcome.item
                    Logger.i(TAG, "upload: stored ${source.displayName} (${item.size ?: -1} bytes)")
                    DataResult.Success(
                        UploadedItem(
                            id = item.id.orEmpty(),
                            name = item.name ?: source.displayName,
                            // -1 for "the server did not say", never 0. Zero is a size, and the
                            // caller compares this against the local length to decide the upload
                            // worked — so an unreported size must fail that test, not accidentally
                            // pass it for an empty file.
                            sizeBytes = item.size ?: -1L,
                            eTag = item.eTag
                        )
                    )
                }

                is UploadOutcome.HttpFailure -> mapFailure(outcome)

                UploadOutcome.EmptySource -> {
                    Logger.w(TAG, "upload: ${source.displayName} read as zero bytes, nothing sent")
                    DataResult.Failure(RemoteError.EmptyLocalFile)
                }
            }
        } catch (e: FileNotFoundException) {
            // Must be caught before IOException, which it extends. A ledger row can outlive the
            // file it describes, and treating that as a lost connection stops the entire run over
            // one deleted photo.
            Logger.w(TAG, "upload: local file is gone, skipping this one")
            DataResult.Failure(RemoteError.LocalFileMissing)
        } catch (e: EOFException) {
            // The local file is shorter than sizeBytes claims — truncated, corrupt, or mid-write.
            // A per-file problem, not a network one: letting it fall through to IOException would
            // stop the entire run over one bad photo.
            Logger.w(TAG, "upload: local file truncated, skipping: ${e.message}")
            DataResult.Failure(RemoteError.Unknown(e))
        } catch (e: IOException) {
            Logger.w(TAG, "upload: network failure", e)
            DataResult.Failure(RemoteError.Network)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            Logger.e(TAG, "upload: unexpected failure", e)
            DataResult.Failure(RemoteError.Unknown(e))
        }
    }

    private suspend fun mapFailure(failure: UploadOutcome.HttpFailure): DataResult<UploadedItem> =
        when (failure.code) {
            HTTP_UNAUTHORIZED -> {
                Logger.w(TAG, "upload: Graph returned 401, invalidating stored token")
                tokenProvider.invalidateAccessToken()
                DataResult.Failure(RemoteError.Unauthorized)
            }

            HTTP_INSUFFICIENT_STORAGE -> {
                Logger.w(TAG, "upload: drive is full")
                DataResult.Failure(RemoteError.InsufficientStorage)
            }

            else -> {
                Logger.w(TAG, "upload: Graph returned HTTP ${failure.code}")
                DataResult.Failure(RemoteError.Http(failure.code, failure.body))
            }
        }

    private companion object {
        const val TAG = "OneDriveUpload"
        const val HTTP_UNAUTHORIZED = 401
        const val HTTP_CONFLICT = 409
        const val HTTP_INSUFFICIENT_STORAGE = 507
    }

    override suspend fun createFolder(
        parentFolderId: String?,
        name: String
    ): DataResult<RemoteMediaNode.Folder> = withContext(dispatcher) {

        if (tokenProvider.getAccessToken() == null) {
            Logger.w(TAG, "createFolder: no access token, skipping network call")
            return@withContext DataResult.Failure(RemoteError.NoToken)
        }

        val body = CreateFolderRequestDto(name = name)
        try {
            val response = if (parentFolderId == null) {
                uploadService.createFolderInRoot(body)
            } else {
                uploadService.createFolderInFolder(parentFolderId, body)
            }

            val item = response.body()
            when {
                response.code() == HTTP_UNAUTHORIZED -> DataResult.Failure(RemoteError.Unauthorized)

                // Graph's answer to conflictBehavior=fail. Reported as its own error so the picker
                // can say the name is taken instead of "something went wrong".
                response.code() == HTTP_CONFLICT ->
                    DataResult.Failure(RemoteError.Unknown(IOException("name already taken")))

                !response.isSuccessful || item == null ->
                    DataResult.Failure(RemoteError.Unknown(IOException("HTTP ${'$'}{response.code()}")))

                else -> {
                    val node = item.toRemoteMediaNode()
                    if (node is RemoteMediaNode.Folder) {
                        DataResult.Success(node)
                    } else {
                        // Graph answered with something that is not a folder. Nothing sane to do
                        // with it, and guessing would put the destination somewhere unintended.
                        DataResult.Failure(
                            RemoteError.Unknown(IOException("created item is not a folder"))
                        )
                    }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            Logger.w(TAG, "createFolder: network failure")
            DataResult.Failure(RemoteError.Network)
        }
    }

    override suspend fun cancelUploadSession(uploadUrl: String) {
        withContext(dispatcher) { uploader.cancelSession(uploadUrl) }
    }
}
