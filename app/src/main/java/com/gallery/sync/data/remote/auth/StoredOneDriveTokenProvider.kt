package com.gallery.sync.data.remote.auth

import com.gallery.sync.util.Logger
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [OneDriveTokenProvider] backed by [EncryptedTokenStore].
 *
 * ### Why this returns `null` today
 *
 * Nothing writes a token into the store yet, so [getAccessToken] returns `null` on every call and
 * the repository short-circuits to `RemoteError.NoToken` without touching the network. That is the
 * correct and expected v0.1.0 behaviour: acquiring a token requires an Azure app registration,
 * which is escalated to Ian per CLAUDE.md ("OAuth app registration is needed").
 *
 * // TODO(TASK-004-AUTH): requires Azure app registration — see escalation in TASK-004.md
 *
 * When the registration lands, the interactive sign-in flow writes the acquired token through
 * [EncryptedTokenStore.writeAccessToken] and everything downstream starts working unchanged. No
 * client ID, tenant ID, secret, redirect URI, or token is present anywhere in this codebase, and
 * none should be added here — this class only ever reads what sign-in stored.
 */
@Singleton
class StoredOneDriveTokenProvider @Inject constructor(
    private val tokenStore: EncryptedTokenStore
) : OneDriveTokenProvider {

    override suspend fun getAccessToken(): String? {
        val token = tokenStore.readAccessToken()
        if (token == null) {
            Logger.d(TAG, "no access token stored; user is not signed in")
        }
        return token
    }

    override suspend fun invalidateAccessToken() {
        Logger.w(TAG, "invalidating stored access token")
        tokenStore.writeAccessToken(null)
    }

    private companion object {
        const val TAG = "OneDriveToken"
    }
}
