package com.gallery.sync.data.repository

import com.gallery.sync.data.remote.auth.OneDriveTokenProvider
import com.gallery.sync.data.remote.onedrive.GraphApiService
import com.gallery.sync.di.IoDispatcher
import com.gallery.sync.domain.model.DataResult
import com.gallery.sync.domain.model.RemoteError
import com.gallery.sync.domain.repository.OneDriveDeletionRepository
import com.gallery.sync.util.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The only class in this app that can remove anything from OneDrive.
 *
 * Kept small and separate on purpose: everything it can do is visible in one screen of code, and
 * nothing else in the data layer holds the capability.
 */
@Singleton
class OneDriveDeletionRepositoryImpl @Inject constructor(
    private val api: GraphApiService,
    private val tokenProvider: OneDriveTokenProvider,
    @param:IoDispatcher private val dispatcher: CoroutineDispatcher
) : OneDriveDeletionRepository {

    override suspend fun moveToRecycleBin(itemId: String): DataResult<Unit> =
        withContext(dispatcher) {
            if (itemId.isBlank()) {
                // A row recorded before ids were captured. Nothing identifies the remote file, so
                // there is nothing safe to delete — and guessing by name would be a way to remove
                // the wrong photo.
                Logger.w(TAG, "refusing to delete: no remote item id")
                return@withContext DataResult.Failure(
                    RemoteError.Unknown(IllegalArgumentException("no remote item id"))
                )
            }

            if (tokenProvider.getAccessToken() == null) {
                return@withContext DataResult.Failure(RemoteError.NoToken)
            }

            try {
                val response = api.deleteItem(itemId)

                when {
                    response.isSuccessful -> {
                        Logger.i(TAG, "moved an item to the OneDrive recycle bin")
                        DataResult.Success(Unit)
                    }

                    // Already gone is the state the caller wanted. Treating it as a failure would
                    // strand a ledger row for a file that exists nowhere.
                    response.code() == HTTP_NOT_FOUND -> {
                        Logger.i(TAG, "item was already absent from the drive")
                        DataResult.Success(Unit)
                    }

                    else -> DataResult.Failure(
                        RemoteError.Http(response.code(), response.errorBody()?.string())
                    )
                }
            } catch (e: IOException) {
                Logger.w(TAG, "delete: network failure")
                DataResult.Failure(RemoteError.Network)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                Logger.e(TAG, "delete: unexpected failure", e)
                DataResult.Failure(RemoteError.Unknown(e))
            }
        }

    private companion object {
        const val TAG = "OneDriveDelete"
        const val HTTP_NOT_FOUND = 404
    }
}
