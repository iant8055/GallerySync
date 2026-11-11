package com.gallery.sync.data.repository

import com.gallery.sync.data.remote.auth.GooglePhotosTokenProvider
import com.gallery.sync.data.remote.auth.OneDriveTokenProvider
import com.gallery.sync.data.remote.googlephotos.GooglePhotosApiService
import com.gallery.sync.data.remote.onedrive.GraphApiService
import com.gallery.sync.di.IoDispatcher
import com.gallery.sync.domain.backup.BackupLocation
import com.gallery.sync.domain.model.DataResult
import com.gallery.sync.domain.model.RemoteError
import com.gallery.sync.domain.repository.CloudFileLink
import com.gallery.sync.util.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import retrofit2.Response
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * OneDrive's link to a file: its `webUrl`, asked of Graph at the moment of the long-press (TASK-028).
 *
 * Documented to open in a browser. Whether the OneDrive app takes it instead is not documented and was not tried
 * before this was written; record what the Moto does in MILESTONES.
 */
@Singleton
class OneDriveFileLink @Inject constructor(
    private val api: GraphApiService,
    private val tokenProvider: OneDriveTokenProvider,
    @param:IoDispatcher private val dispatcher: CoroutineDispatcher
) : CloudFileLink {

    override val location = BackupLocation.ONEDRIVE

    override suspend fun linkFor(remoteItemId: String): DataResult<String> = withContext(dispatcher) {
        if (tokenProvider.getAccessToken() == null) return@withContext DataResult.Failure(RemoteError.NoToken)
        ask(TAG, { api.getItemWebUrl(remoteItemId) }) { it.webUrl }
    }

    private companion object {
        const val TAG = "OneDriveFileLink"
    }
}

/**
 * Google Photos' link to a file: the item's `productUrl` (TASK-028). The Library API answers only for items this
 * app created, which every Google Photos backup of this app is. Whether it opens the Photos app or the browser
 * was not tried before this was written.
 */
@Singleton
class GooglePhotosFileLink @Inject constructor(
    private val api: GooglePhotosApiService,
    private val tokenProvider: GooglePhotosTokenProvider,
    @param:IoDispatcher private val dispatcher: CoroutineDispatcher
) : CloudFileLink {

    override val location = BackupLocation.GOOGLE_PHOTOS

    override suspend fun linkFor(remoteItemId: String): DataResult<String> = withContext(dispatcher) {
        if (tokenProvider.getAccessToken() == null) return@withContext DataResult.Failure(RemoteError.NoToken)
        ask(TAG, { api.getMediaItem(remoteItemId) }) { it.productUrl }
    }

    private companion object {
        const val TAG = "GooglePhotosFileLink"
    }
}

/** One request whose answer carries a link, mapped onto [DataResult]. A missing link is a failure, not a blank. */
private suspend fun <T> ask(tag: String, call: suspend () -> Response<T>, link: (T) -> String?): DataResult<String> =
    try {
        val response = call()
        val url = response.body()?.let(link)?.takeIf { it.isNotBlank() }
        when {
            response.code() == 401 -> DataResult.Failure(RemoteError.Unauthorized)
            !response.isSuccessful -> DataResult.Failure(RemoteError.Http(response.code(), response.errorBody()?.string()))
            url == null -> DataResult.Failure(RemoteError.Unknown(IOException("no link in the reply")))
            else -> DataResult.Success(url)
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: IOException) {
        Logger.w(tag, "link: network failure: ${e.message}")
        DataResult.Failure(RemoteError.Network)
    } catch (e: Throwable) {
        Logger.e(tag, "link: unexpected failure", e)
        DataResult.Failure(RemoteError.Unknown(e))
    }
