package com.gallery.sync.data.remote.cloud

import android.content.Context
import com.gallery.sync.domain.backup.BackupLocation
import com.gallery.sync.util.Logger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.suspendCancellableCoroutine
import net.openid.appauth.AuthState
import net.openid.appauth.AuthorizationService
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * Access tokens for the OAuth clouds, refreshed as needed and re-persisted after every call — AppAuth
 * keeps no cache of its own, and a refresh that is not written back is repeated from the same stale
 * point next time. One instance serves every provider; state is keyed by provider.
 */
@Singleton
class AppAuthCloudTokens @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val secrets: EncryptedCloudSecretsStore
) {

    private val forceRefresh = ConcurrentHashMap<BackupLocation, Boolean>()

    suspend fun isSignedIn(location: BackupLocation): Boolean =
        secrets.read(stateKey(location.name)) != null

    /** The current access token, or null when not signed in or the refresh failed. */
    suspend fun accessToken(location: BackupLocation): String? {
        val serialized = secrets.read(stateKey(location.name)) ?: return null
        val state = AuthState.jsonDeserialize(serialized)
        val force = forceRefresh.remove(location) == true
        if (force) state.setNeedsTokenRefresh(true)

        val authService = AuthorizationService(context)
        val token = try {
            suspendCancellableCoroutine<String?> { continuation ->
                state.performActionWithFreshTokens(authService) { accessToken, _, exception ->
                    if (exception != null) Logger.e(TAG, "$location token refresh failed: ${exception.errorDescription}")
                    continuation.resume(accessToken)
                }
            }
        } finally {
            authService.dispose()
        }
        secrets.write(stateKey(location.name), state.jsonSerializeString())
        return token
    }

    /** Called after the API answered 401: the next [accessToken] forces a refresh. */
    fun invalidate(location: BackupLocation) {
        forceRefresh[location] = true
    }

    suspend fun signOut(location: BackupLocation) {
        secrets.clearPrefix("${location.name}.")
    }

    companion object {
        private const val TAG = "CloudTokens"
        fun stateKey(providerName: String) = "$providerName.auth_state"
    }
}
