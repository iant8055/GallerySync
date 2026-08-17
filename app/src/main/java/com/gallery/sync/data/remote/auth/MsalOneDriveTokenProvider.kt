package com.gallery.sync.data.remote.auth

import com.gallery.sync.util.Logger
import com.microsoft.identity.client.AcquireTokenSilentParameters
import com.microsoft.identity.client.IAccount
import com.microsoft.identity.client.IAuthenticationResult
import com.microsoft.identity.client.ISingleAccountPublicClientApplication
import com.microsoft.identity.client.SilentAuthenticationCallback
import com.microsoft.identity.client.exception.MsalException
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * [OneDriveTokenProvider] backed by MSAL.
 *
 * ### Why MSAL owns the tokens rather than [EncryptedTokenStore]
 *
 * MSAL keeps its own cache, encrypted with a key held in the Android Keystore, and renews access
 * tokens from the refresh token without asking the user again. Copying access tokens out into our
 * own store would leave us holding a value that silently goes stale, and we would have to
 * reimplement refresh to fix it. So the token never leaves MSAL: this class asks for one on each
 * call and MSAL decides whether to return a cached token or quietly mint a fresh one.
 *
 * ### Silent only, by design
 *
 * Interactive sign-in needs an `Activity` to host the browser tab, and this is a singleton with
 * no Activity to hand. So it acquires **silently** and returns `null` when no account exists yet
 * — the same "not signed in" contract [StoredOneDriveTokenProvider] had, leaving the repository's
 * `RemoteError.NoToken` path unchanged. [MsalOneDriveSignIn] is what puts an account in the cache.
 */
@Singleton
class MsalOneDriveTokenProvider @Inject constructor(
    private val clientProvider: MsalClientProvider
) : OneDriveTokenProvider {

    /**
     * Set after Graph rejects a token, and cleared once a refresh has been forced. MSAL returns
     * whatever is cached until told otherwise, so without this a rejected token would be replayed
     * on every retry.
     */
    @Volatile
    private var forceRefresh = false

    override suspend fun getAccessToken(): String? {
        val app = clientProvider.client() ?: return null
        val account = currentAccount(app) ?: run {
            Logger.d(TAG, "no MSAL account; user is not signed in")
            return null
        }

        val shouldForce = forceRefresh
        val result = acquireSilently(app, account, shouldForce)
        if (shouldForce) forceRefresh = false

        return result?.accessToken
    }

    override suspend fun invalidateAccessToken() {
        Logger.w(TAG, "access token rejected; forcing refresh on next acquisition")
        forceRefresh = true
    }

    private suspend fun currentAccount(
        app: ISingleAccountPublicClientApplication
    ): com.microsoft.identity.client.IAccount? = suspendCancellableCoroutine { continuation ->
        app.getCurrentAccountAsync(
            object : ISingleAccountPublicClientApplication.CurrentAccountCallback {
                override fun onAccountLoaded(activeAccount: com.microsoft.identity.client.IAccount?) {
                    continuation.resume(activeAccount)
                }

                override fun onAccountChanged(
                    priorAccount: com.microsoft.identity.client.IAccount?,
                    currentAccount: com.microsoft.identity.client.IAccount?
                ) = Unit

                override fun onError(exception: MsalException) {
                    Logger.e(TAG, "could not load MSAL account: ${exception.errorCode}")
                    continuation.resume(null)
                }
            }
        )
    }

    private suspend fun acquireSilently(
        app: ISingleAccountPublicClientApplication,
        account: IAccount,
        force: Boolean
    ): IAuthenticationResult? = suspendCancellableCoroutine { continuation ->
        val parameters = AcquireTokenSilentParameters.Builder()
            // forAccount is not optional in single-account mode. Without it MSAL has no account
            // to match the cached token against and fails with `current_account_mismatch`, even
            // though exactly one account is signed in.
            .forAccount(account)
            .fromAuthority(account.authority)
            .withScopes(MsalClientProvider.SCOPES)
            .forceRefresh(force)
            .withCallback(object : SilentAuthenticationCallback {
                override fun onSuccess(authenticationResult: IAuthenticationResult) {
                    continuation.resume(authenticationResult)
                }

                override fun onError(exception: MsalException) {
                    // Includes the refresh token itself having expired, which genuinely requires
                    // interactive sign-in again. Null reads as "not signed in" downstream.
                    Logger.e(TAG, "silent token acquisition failed: ${exception.errorCode}")
                    continuation.resume(null)
                }
            })
            .build()

        app.acquireTokenSilentAsync(parameters)
    }

    private companion object {
        const val TAG = "OneDriveToken"
    }
}
