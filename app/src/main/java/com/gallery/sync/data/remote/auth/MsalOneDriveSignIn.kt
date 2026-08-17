package com.gallery.sync.data.remote.auth

import android.app.Activity
import com.gallery.sync.util.Logger
import com.microsoft.identity.client.AcquireTokenParameters
import com.microsoft.identity.client.AuthenticationCallback
import com.microsoft.identity.client.IAuthenticationResult
import com.microsoft.identity.client.ISingleAccountPublicClientApplication
import com.microsoft.identity.client.exception.MsalException
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * [OneDriveSignIn] backed by MSAL.
 *
 * Writes the signed-in account into the shared MSAL cache held by [MsalClientProvider], which is
 * what makes [MsalOneDriveTokenProvider] start returning tokens instead of `null`.
 */
@Singleton
class MsalOneDriveSignIn @Inject constructor(
    private val clientProvider: MsalClientProvider
) : OneDriveSignIn {

    override suspend fun currentAccountName(): String? {
        val app = clientProvider.client() ?: return null
        return currentAccount(app)?.username
    }

    override suspend fun signIn(activity: Activity): SignInResult {
        val app = clientProvider.client()
            ?: return SignInResult.Failed(MSAL_UNAVAILABLE)

        return suspendCancellableCoroutine { continuation ->
            val parameters = AcquireTokenParameters.Builder()
                .startAuthorizationFromActivity(activity)
                .withScopes(MsalClientProvider.SCOPES)
                .withCallback(object : AuthenticationCallback {
                    override fun onSuccess(authenticationResult: IAuthenticationResult) {
                        val name = authenticationResult.account.username
                        Logger.i(TAG, "signed in")
                        continuation.resume(SignInResult.Success(name))
                    }

                    override fun onError(exception: MsalException) {
                        Logger.e(TAG, "sign-in failed: ${exception.errorCode}")
                        continuation.resume(SignInResult.Failed(exception.errorCode))
                    }

                    override fun onCancel() {
                        Logger.d(TAG, "sign-in cancelled by user")
                        continuation.resume(SignInResult.Cancelled)
                    }
                })
                .build()

            app.acquireToken(parameters)
        }
    }

    override suspend fun signOut(): Boolean {
        val app = clientProvider.client() ?: return false

        return suspendCancellableCoroutine { continuation ->
            app.signOut(object : ISingleAccountPublicClientApplication.SignOutCallback {
                override fun onSignOut() {
                    Logger.i(TAG, "signed out")
                    continuation.resume(true)
                }

                override fun onError(exception: MsalException) {
                    Logger.e(TAG, "sign-out failed: ${exception.errorCode}")
                    continuation.resume(false)
                }
            })
        }
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
                    Logger.e(TAG, "could not load account: ${exception.errorCode}")
                    continuation.resume(null)
                }
            }
        )
    }

    private companion object {
        const val TAG = "OneDriveSignIn"

        /** Reported when MSAL itself would not initialise, so no flow could even be started. */
        const val MSAL_UNAVAILABLE = "msal_unavailable"
    }
}
