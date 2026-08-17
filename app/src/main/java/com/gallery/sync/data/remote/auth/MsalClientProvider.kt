package com.gallery.sync.data.remote.auth

import android.content.Context
import com.gallery.sync.R
import com.gallery.sync.util.Logger
import com.microsoft.identity.client.IPublicClientApplication
import com.microsoft.identity.client.ISingleAccountPublicClientApplication
import com.microsoft.identity.client.PublicClientApplication
import com.microsoft.identity.client.exception.MsalException
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * Owns the one and only MSAL client instance.
 *
 * Both the silent path ([MsalOneDriveTokenProvider]) and the interactive path
 * ([MsalOneDriveSignIn]) must talk to the *same* client, because the account that interactive
 * sign-in writes is only visible to silent acquisition through that shared cache. Two clients
 * would mean signing in successfully and then still reading `null` tokens.
 *
 * Creation is asynchronous and happens at most once: the mutex makes concurrent first-callers
 * wait rather than each building a client.
 */
@Singleton
class MsalClientProvider @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val initLock = Mutex()
    private var cached: ISingleAccountPublicClientApplication? = null

    /** The shared client, or `null` if MSAL could not be initialised at all. */
    suspend fun client(): ISingleAccountPublicClientApplication? {
        cached?.let { return it }
        return initLock.withLock {
            cached?.let { return it }
            create().also { cached = it }
        }
    }

    private suspend fun create(): ISingleAccountPublicClientApplication? =
        suspendCancellableCoroutine { continuation ->
            PublicClientApplication.createSingleAccountPublicClientApplication(
                context,
                R.raw.msal_config,
                object : IPublicClientApplication.ISingleAccountApplicationCreatedListener {
                    override fun onCreated(application: ISingleAccountPublicClientApplication) {
                        continuation.resume(application)
                    }

                    override fun onError(exception: MsalException) {
                        // Malformed config, or the signing certificate does not match the
                        // signature hash on the Azure registration. Returning null degrades to
                        // "not signed in" rather than crashing a background sync.
                        Logger.e(TAG, "MSAL initialisation failed: ${exception.errorCode}")
                        continuation.resume(null)
                    }
                }
            )
        }

    companion object {
        private const val TAG = "MsalClient"

        /**
         * Read-only access plus `offline_access`, so MSAL is issued a refresh token and can keep
         * renewing without prompting. Must stay in step with the delegated permissions granted on
         * the Azure app registration.
         */
        val SCOPES = listOf("https://graph.microsoft.com/Files.Read", "offline_access")
    }
}
