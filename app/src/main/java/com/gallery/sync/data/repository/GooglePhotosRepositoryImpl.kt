package com.gallery.sync.data.repository

import com.gallery.sync.data.remote.auth.GooglePhotosTokenProvider
import com.gallery.sync.data.remote.googlephotos.GooglePhotosApiService
import com.gallery.sync.data.remote.googlephotos.toGooglePhotosMediaItem
import com.gallery.sync.di.IoDispatcher
import com.gallery.sync.domain.model.DataResult
import com.gallery.sync.domain.model.RemoteError
import com.gallery.sync.domain.repository.GooglePhotosPage
import com.gallery.sync.domain.repository.GooglePhotosRepository
import com.gallery.sync.util.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Google Photos Library API implementation of [GooglePhotosRepository].
 *
 * Mirrors [OneDriveRepositoryImpl]'s shape: this is the network boundary, no Retrofit or OkHttp
 * type escapes the data layer, and nothing here logs a token.
 */
@Singleton
class GooglePhotosRepositoryImpl @Inject constructor(
    private val api: GooglePhotosApiService,
    private val tokenProvider: GooglePhotosTokenProvider,
    @param:IoDispatcher private val dispatcher: CoroutineDispatcher
) : GooglePhotosRepository {

    override suspend fun listAppCreatedItems(pageToken: String?): DataResult<GooglePhotosPage> =
        withContext(dispatcher) {
            if (tokenProvider.getAccessToken() == null) {
                Logger.w(TAG, "listAppCreatedItems: no access token, skipping network call")
                return@withContext DataResult.Failure(RemoteError.NoToken)
            }

            try {
                val response = api.listMediaItems(pageToken = pageToken)
                when {
                    response.code() == HTTP_UNAUTHORIZED -> {
                        Logger.w(TAG, "listAppCreatedItems: Library API returned 401, invalidating stored token")
                        tokenProvider.invalidateAccessToken()
                        DataResult.Failure(RemoteError.Unauthorized)
                    }

                    !response.isSuccessful -> {
                        val code = response.code()
                        Logger.w(TAG, "listAppCreatedItems: Library API returned HTTP $code")
                        DataResult.Failure(RemoteError.Http(code, response.errorBody()?.string()))
                    }

                    else -> {
                        val body = response.body()
                        val items = body?.mediaItems.orEmpty().mapNotNull { it.toGooglePhotosMediaItem() }
                        Logger.d(
                            TAG,
                            "listAppCreatedItems: ${items.size} items, more pages: " +
                                "${body?.nextPageToken != null}"
                        )
                        DataResult.Success(GooglePhotosPage(items = items, nextPageToken = body?.nextPageToken))
                    }
                }
            } catch (e: IOException) {
                Logger.w(TAG, "listAppCreatedItems: network failure", e)
                DataResult.Failure(RemoteError.Network)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                Logger.e(TAG, "listAppCreatedItems: unexpected failure", e)
                DataResult.Failure(RemoteError.Unknown(e))
            }
        }

    private companion object {
        const val TAG = "GooglePhotosRepo"
        const val HTTP_UNAUTHORIZED = 401
    }
}
