package com.gallery.sync.data.remote.auth

import android.app.Activity
import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import android.os.Build
import com.gallery.sync.util.Logger
import net.openid.appauth.AuthorizationRequest
import net.openid.appauth.AuthorizationService
import net.openid.appauth.AuthorizationServiceConfiguration
import net.openid.appauth.ResponseTypeValues
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [GooglePhotosSignIn] backed by AppAuth.
 *
 * See TASK-026 for why AppAuth rather than Credential Manager's `requestOfflineAccess`: this app
 * has no backend server, and AppAuth is the standard RFC 8252 native-app flow — PKCE against the
 * public Android client, refresh token issued directly to the app, no client secret anywhere on
 * device. Same shape as OneDrive's MSAL flow, one layer more manual because Google has no
 * MSAL-equivalent SDK for this case.
 */
@Singleton
class AppAuthGooglePhotosSignIn @Inject constructor(
    private val config: GoogleAuthConfig,
    private val authStore: EncryptedGoogleAuthStore,
    private val resultBridge: GoogleSignInResultBridge
) : GooglePhotosSignIn {

    override suspend fun currentAccountName(): String? =
        if (authStore.readState() != null) GooglePhotosSignIn.ACCOUNT_LABEL else null

    override suspend fun signIn(activity: Activity): SignInResult {
        val parsed = config.value
        val serviceConfig = AuthorizationServiceConfiguration(
            Uri.parse(parsed.authorizationEndpoint),
            Uri.parse(parsed.tokenEndpoint)
        )
        val request = AuthorizationRequest.Builder(
            serviceConfig,
            parsed.clientId,
            ResponseTypeValues.CODE,
            Uri.parse(parsed.redirectUri)
        )
            .setScopes(parsed.scopes)
            // Google only issues a refresh token on the *first* consent for a client+account pair
            // unless both of these are set. Without them, a user who reinstalls or revokes access
            // and signs in again would silently get an access-token-only grant that stops working
            // in an hour with no way back short of the Google Account permissions page.
            .setAdditionalParameters(mapOf("access_type" to "offline"))
            .setPrompt("consent")
            .build()

        val authService = AuthorizationService(activity.applicationContext)
        val deferred = resultBridge.beginWait()

        val mutabilityFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_MUTABLE
        } else {
            0
        }

        val completedIntent = PendingIntent.getActivity(
            activity,
            REQUEST_CODE_COMPLETE,
            Intent(activity, GoogleAuthResultActivity::class.java)
                .putExtra(GoogleAuthResultActivity.EXTRA_OUTCOME, GoogleAuthResultActivity.OUTCOME_COMPLETED),
            PendingIntent.FLAG_UPDATE_CURRENT or mutabilityFlag
        )
        val canceledIntent = PendingIntent.getActivity(
            activity,
            REQUEST_CODE_CANCEL,
            Intent(activity, GoogleAuthResultActivity::class.java)
                .putExtra(GoogleAuthResultActivity.EXTRA_OUTCOME, GoogleAuthResultActivity.OUTCOME_CANCELLED),
            PendingIntent.FLAG_UPDATE_CURRENT or mutabilityFlag
        )

        Logger.i(TAG, "launching Google sign-in")
        authService.performAuthorizationRequest(request, completedIntent, canceledIntent)

        return try {
            deferred.await()
        } finally {
            // Only tears down this instance's Custom Tabs warm-up connection. The flow itself was
            // already handed off to the PendingIntents above and does not depend on this instance
            // staying alive — see GoogleAuthResultActivity, which creates its own.
            authService.dispose()
        }
    }

    override suspend fun signOut(): Boolean {
        authStore.clear()
        Logger.i(TAG, "signed out")
        return true
    }

    private companion object {
        const val TAG = "GooglePhotosSignIn"
        const val REQUEST_CODE_COMPLETE = 100
        const val REQUEST_CODE_CANCEL = 101
    }
}
