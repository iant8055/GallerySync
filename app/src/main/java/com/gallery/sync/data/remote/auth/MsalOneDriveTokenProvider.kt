package com.gallery.sync.data.remote.auth

import android.content.Context
import com.gallery.sync.R
import com.gallery.sync.util.Logger
import com.microsoft.identity.client.AcquireTokenSilentParameters
import com.microsoft.identity.client.IAuthenticationResult
import com.microsoft.identity.client.IPublicClientApplication
import com.microsoft.identity.client.ISingleAccountPublicClientApplication
import com.microsoft.identity.client.PublicClientApplication
import com.microsoft.identity.client.SilentAuthenticationCallback
import com.microsoft.identity.client.exception.MsalException
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * [OneDriveTokenProvider] backed by MSAL, the Microsoft identity library.
 *
 * ### Why MSAL owns the tokens rather than [EncryptedTokenStore]
 *
 * MSAL keeps its own cache, encrypted with a key held in the Android Keystore, and refreshes
 * access tokens from the refresh token without asking the user again. Copying access tokens out
 * into our own store would leave us holding a value that silently goes stale, and we would have
 * to reimplement refresh to fix it. So the token never leaves MSAL: this class asks for one on
 * each call and MSAL decides whether to hand back a cached token or quietly mint a fresh one.
 *
 * ### Silent only, by design
 *
 * Interactive sign-in needs an `Activity` to host the browser tab, and this class is a singleton
 * with no Activity to hand. So it acquires **silently** and returns `null` when there is no
 * account yet — the same "not signed in" contract [StoredOneDriveTokenProvider] had, so the
 * repository's `RemoteError.NoToken` path is unchanged. The interactive flow lands with the
 * sign-in UI and writes an account into the shared MSAL cache that this class then reads.
 *
 * No secret appears here or in `msal_config.json`. Public clients authenticate with PKCE, and
 * the redirect URI is bound to this app's signing certificate.
 */
@Singleton
class MsalOneDriveTokenProvider @Inject constructor(
    @ApplicationContext private val context: Context
) : OneDriveTokenProvider {

    private val initLock = Mutex()
    private var client: ISingleAccountPublicClientApplication? = null

    /**
     * Set after Graph rejects a token, and cleared once a refresh has been forced. MSAL hands back
     * whatever is cached until told otherwise, so without this a rejected token would be replayed
     * on every retry.
     */
    @Volatile
    private var forceRefresh = false

    override suspend fun getAccessToken(): String? {
        val app = client() ?: return null
        val account = currentAccount(app) ?: run {
            Logger.d(TAG, "no MSAL account; user is not signed in")
            return null
        }

        val shouldForce = forceRefresh
        val result = acquireSilently(app, account.authority, shouldForce)
        if (shouldForce) forceRefresh = false

        return result?.accessToken
    }

    override suspend fun invalidateAccessToken() {
        Logger.w(TAG, "access token rejected; forcing refresh on next acquisition")
        forceRefresh = true
    }

    // ---------- MSAL callback bridging ----------

    private suspend fun client(): ISingleAccountPublicClientApplication? {
        client?.let { return it }
        return initLock.withLock {
            client?.let { return it }
            val created = createClient()
            client = created
            created
        }
    }

    private suspend fun createClient(): ISingleAccountPublicClientApplication? =
        suspendCancellableCoroutine { continuation ->
            PublicClientApplication.createSingleAccountPublicClientApplication(
                context,
                R.raw.msal_config,
                object : IPublicClientApplication.ISingleAccountApplicationCreatedListener {
                    override fun onCreated(application: ISingleAccountPublicClientApplication) {
                        continuation.resume(application)
                    }

                    override fun onError(exception: MsalException) {
                        // A malformed config or a signature-hash mismatch with the Azure
                        // registration lands here. Returning null degrades to "not signed in"
                        // rather than crashing a background sync.
                        Logger.e(TAG, "MSAL initialisation failed: ${exception.errorCode}")
                        continuation.resume(null)
                    }
                }
            )
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
                ) {
                    // Signed out elsewhere, or a different account signed in. Nothing to do here;
                    // onAccountLoaded still fires with the account that is current now.
                }

                override fun onError(exception: MsalException) {
                    Logger.e(TAG, "could not load MSAL account: ${exception.errorCode}")
                    continuation.resume(null)
                }
            }
        )
    }

    private suspend fun acquireSilently(
        app: ISingleAccountPublicClientApplication,
        authority: String,
        force: Boolean
    ): IAuthenticationResult? = suspendCancellableCoroutine { continuation ->
        val parameters = AcquireTokenSilentParameters.Builder()
            .fromAuthority(authority)
            .withScopes(SCOPES)
            .forceRefresh(force)
            .withCallback(object : SilentAuthenticationCallback {
                override fun onSuccess(authenticationResult: IAuthenticationResult) {
                    continuation.resume(authenticationResult)
                }

                override fun onError(exception: MsalException) {
                    // Includes the case where the refresh token itself expired, which genuinely
                    // requires interactive sign-in again. Null means "not signed in" downstream.
                    Logger.e(TAG, "silent token acquisition failed: ${exception.errorCode}")
                    continuation.resume(null)
                }
            })
            .build()

        app.acquireTokenSilentAsync(parameters)
    }

    private companion object {
        const val TAG = "OneDriveToken"

        /**
         * Read-only, and `offline_access` so MSAL receives a refresh token and can keep working
         * without prompting. Matches the delegated permissions on the Azure registration.
         */
        val SCOPES = listOf("https://graph.microsoft.com/Files.Read", "offline_access")
    }
}
