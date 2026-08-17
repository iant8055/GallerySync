package com.gallery.sync.data.remote.onedrive

import com.gallery.sync.data.remote.auth.OneDriveTokenProvider
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Attaches `Authorization: Bearer <token>` to every Microsoft Graph request.
 *
 * When no token is available the request proceeds **without** the header and Graph answers 401,
 * which the repository maps to `RemoteError.Unauthorized`. Throwing from here instead would
 * surface as an opaque `IOException` and destroy that distinction.
 *
 * The token value is never logged, and this class never writes the header anywhere but onto the
 * outgoing request.
 */
@Singleton
class GraphAuthInterceptor @Inject constructor(
    private val tokenProvider: OneDriveTokenProvider
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        // SANCTIONED runBlocking — do not "fix" this.
        //
        // OkHttp's Interceptor API is synchronous: `intercept` must return a Response, it cannot
        // suspend. The token provider is a suspend function (CLAUDE.md: coroutines, not callbacks),
        // so the two APIs have to be bridged somewhere, and this is that seam.
        //
        // It is safe here specifically because `intercept` always runs on OkHttp's own dispatcher
        // thread, never on the Android main thread — blocking it stalls one pooled network thread
        // and nothing else. This is the ONE place in the codebase where runBlocking is allowed.
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
