package com.gallery.sync.data.remote.auth

import android.content.Context
import com.gallery.sync.util.Logger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.suspendCancellableCoroutine
import net.openid.appauth.AuthState
import net.openid.appauth.AuthorizationService
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * [GooglePhotosTokenProvider] backed by AppAuth's `AuthState`.
 *
 * Unlike MSAL, AppAuth keeps no cache of its own — this class owns persistence, reading the stored
 * state, asking AppAuth to refresh if the access token has expired (or if [invalidateAccessToken]
 * forced it), and writing the result straight back: a refresh mutates the tokens inside `AuthState`,
 * and AppAuth's own docs are explicit that the caller is responsible for re-persisting after every
 * change, or the next call refreshes again from the same stale point. See [EncryptedGoogleAuthStore].
 */
@Singleton
class AppAuthGooglePhotosTokenProvider @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val authStore: EncryptedGoogleAuthStore
) : GooglePhotosTokenProvider {

    /** Set after the Photos Library API rejects a token, forcing a refresh on the next call. */
    @Volatile
    private var forceRefresh = false

    override suspend fun getAccessToken(): String? {
        val serialized = authStore.readState() ?: run {
            Logger.d(TAG, "no stored Google auth state; user is not signed in")
            return null
        }
        val state = AuthState.jsonDeserialize(serialized)

        val shouldForce = forceRefresh
        if (shouldForce) state.setNeedsTokenRefresh(true)

        val authService = AuthorizationService(context)
        val token = try {
            performActionWithFreshTokens(authService, state)
        } finally {
            authService.dispose()
        }
        if (shouldForce) forceRefresh = false

        // Persist regardless of whether a refresh actually happened inside the call above:
        // AuthState mutates itself either way, and the cheap write is simpler than tracking
        // whether it was needed.
        authStore.writeState(state.jsonSerializeString())

        return token
    }

    override suspend fun invalidateAccessToken() {
        Logger.w(TAG, "access token rejected; forcing refresh on next acquisition")
        forceRefresh = true
    }

    private suspend fun performActionWithFreshTokens(
        authService: AuthorizationService,
        state: AuthState
    ): String? = suspendCancellableCoroutine { continuation ->
        state.performActionWithFreshTokens(authService) { accessToken, _, exception ->
            if (exception != null) {
                Logger.e(TAG, "token refresh failed: ${exception.errorDescription}")
            }
            continuation.resume(accessToken)
        }
    }

    private companion object {
        const val TAG = "GooglePhotosToken"
    }
}
