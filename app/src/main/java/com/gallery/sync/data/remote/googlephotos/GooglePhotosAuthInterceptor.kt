package com.gallery.sync.data.remote.googlephotos

import com.gallery.sync.data.remote.auth.GooglePhotosTokenProvider
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Attaches `Authorization: Bearer <token>` to every Google Photos Library API request.
 *
 * Mirrors [com.gallery.sync.data.remote.onedrive.GraphAuthInterceptor] exactly, including the
 * SANCTIONED `runBlocking` — see that class's doc comment for why it's safe here too:
 * `intercept` always runs on OkHttp's dispatcher thread, never the main thread.
 *
 * When no token is available the request proceeds without the header and the API answers 401,
 * which the repository maps to `RemoteError.Unauthorized`. The token value is never logged.
 */
@Singleton
class GooglePhotosAuthInterceptor @Inject constructor(
    private val tokenProvider: GooglePhotosTokenProvider
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val token = runBlocking { tokenProvider.getAccessToken() }

        val request = chain.request()
        val authorized = if (token == null) {
            request
        } else {
            request.newBuilder()
                .header(HEADER_AUTHORIZATION, "$BEARER_PREFIX$token")
                .build()
        }
        return chain.proceed(authorized)
    }

    private companion object {
        const val HEADER_AUTHORIZATION = "Authorization"
        const val BEARER_PREFIX = "Bearer "
    }
}
