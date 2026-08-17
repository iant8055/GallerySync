package com.gallery.sync.data.repository

import com.gallery.sync.data.remote.auth.OneDriveTokenProvider
import com.gallery.sync.data.remote.onedrive.GraphApiService
import com.gallery.sync.data.remote.onedrive.dto.GraphChildrenResponseDto
import com.gallery.sync.data.remote.onedrive.toRemoteMediaNode
import com.gallery.sync.di.IoDispatcher
import com.gallery.sync.domain.model.DataResult
import com.gallery.sync.domain.model.FolderPage
import com.gallery.sync.domain.model.RemoteError
import com.gallery.sync.domain.repository.OneDriveRepository
import com.gallery.sync.util.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import retrofit2.Response
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Microsoft Graph implementation of [OneDriveRepository].
 *
 * This class is the app's only network boundary for OneDrive. Failures are translated into typed
 * [RemoteError]s here so that no Retrofit or OkHttp type ever escapes the data layer.
 *
 * Nothing in this file logs a token or an `Authorization` header.
 */
@Singleton
class OneDriveRepositoryImpl @Inject constructor(
    private val api: GraphApiService,
    private val tokenProvider: OneDriveTokenProvider,
    @param:IoDispatcher private val dispatcher: CoroutineDispatcher
) : OneDriveRepository {

    override suspend fun listRoot(): DataResult<FolderPage> = withContext(dispatcher) {
        request(operation = "listRoot") { api.listRootChildren() }
    }

    override suspend fun listFolder(folderId: String): DataResult<FolderPage> =
        withContext(dispatcher) {
            request(operation = "listFolder") { api.listChildren(folderId) }
        }

    override suspend fun listNextPage(nextPageToken: String): DataResult<FolderPage> =
        withContext(dispatcher) {
            request(operation = "listNextPage") { api.listNextPage(nextPageToken) }
        }

    /**
     * Runs one Graph children call and maps its outcome onto [DataResult].
     *
     * Checks for a token first and returns [RemoteError.NoToken] without calling [call] — issuing a
     * request we already know is unauthenticated wastes the user's data and earns a guaranteed 401.
     */
    private suspend fun request(
        operation: String,
        call: suspend () -> Response<GraphChildrenResponseDto>
    ): DataResult<FolderPage> {
        val hasToken = tokenProvider.getAccessToken() != null
        Logger.d(TAG, "$operation: token present: $hasToken")
        if (!hasToken) {
            Logger.w(TAG, "$operation: no access token, skipping network call")
            return DataResult.Failure(RemoteError.NoToken)
        }

        return try {
            val response = call()
            when {
                response.code() == HTTP_UNAUTHORIZED -> {
                    Logger.w(TAG, "$operation: Graph returned 401, invalidating stored token")
                    tokenProvider.invalidateAccessToken()
                    DataResult.Failure(RemoteError.Unauthorized)
                }

                !response.isSuccessful -> {
                    val code = response.code()
                    Logger.w(TAG, "$operation: Graph returned HTTP $code")
                    DataResult.Failure(RemoteError.Http(code, response.errorBody()?.string()))
                }

                else -> {
                    // A 2xx with no body (e.g. 204) is an empty page, not a failure.
                    val body = response.body() ?: GraphChildrenResponseDto()
                    val nodes = body.value.mapNotNull { it.toRemoteMediaNode() }
                    Logger.d(
                        TAG,
                        "$operation: ${body.value.size} items received, ${nodes.size} mapped, " +
                            "more pages: ${body.nextLink != null}"
                    )
                    DataResult.Success(FolderPage(nodes = nodes, nextPageToken = body.nextLink))
                }
            }
        } catch (e: IOException) {
            Logger.w(TAG, "$operation: network failure", e)
            DataResult.Failure(RemoteError.Network)
        } catch (e: CancellationException) {
            // Cancellation is cooperative control flow, never a repository failure.
            throw e
        } catch (e: Throwable) {
            Logger.e(TAG, "$operation: unexpected failure", e)
            DataResult.Failure(RemoteError.Unknown(e))
        }
    }

    private companion object {
        const val TAG = "OneDriveRepo"
        const val HTTP_UNAUTHORIZED = 401
    }
}
