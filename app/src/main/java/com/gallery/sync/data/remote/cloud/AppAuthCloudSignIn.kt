package com.gallery.sync.data.remote.cloud

import android.app.Activity
import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import android.os.Build
import com.gallery.sync.data.remote.auth.GoogleSignInResultBridge
import com.gallery.sync.data.remote.auth.SignInResult
import com.gallery.sync.domain.backup.BackupLocation
import com.gallery.sync.util.Logger
import net.openid.appauth.AuthorizationRequest
import net.openid.appauth.AuthorizationService
import net.openid.appauth.AuthorizationServiceConfiguration
import net.openid.appauth.ResponseTypeValues
import javax.inject.Inject
import javax.inject.Singleton

/** The fixed facts about one OAuth provider's endpoints and what to ask for. */
data class OAuthProvider(
    val location: BackupLocation,
    val authEndpoint: String,
    val tokenEndpoint: String,
    val scopes: List<String>,
    val extraParams: Map<String, String> = emptyMap(),
    /** Google needs `consent` to hand out a refresh token every time; others leave this null. */
    val prompt: String? = null
)

/**
 * The AppAuth browser flow (RFC 8252, PKCE, no client secret anywhere) for any provider that speaks
 * standard OAuth 2 — Google Drive and Dropbox. The finished flow returns through
 * [CloudAuthResultActivity], which stores the state under the provider's own key and hands the result
 * back through the same [GoogleSignInResultBridge] Google Photos uses; only one sign-in is ever in
 * flight, because it needs the person's attention.
 */
@Singleton
class AppAuthCloudSignIn @Inject constructor(
    private val resultBridge: GoogleSignInResultBridge
) {

    suspend fun signIn(activity: Activity, provider: OAuthProvider, client: OAuthClient): SignInResult {
        val serviceConfig = AuthorizationServiceConfiguration(
            Uri.parse(provider.authEndpoint),
            Uri.parse(provider.tokenEndpoint)
        )
        val request = AuthorizationRequest.Builder(
            serviceConfig,
            client.clientId,
            ResponseTypeValues.CODE,
            Uri.parse(client.redirectUri)
        )
            .setScopes(provider.scopes)
            .setAdditionalParameters(provider.extraParams)
            .apply { provider.prompt?.let { setPrompt(it) } }
            .build()

        val authService = AuthorizationService(activity.applicationContext)
        val deferred = resultBridge.beginWait()

        val mutability = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_MUTABLE else 0
        fun pending(code: Int, outcome: String) = PendingIntent.getActivity(
            activity,
            code,
            Intent(activity, CloudAuthResultActivity::class.java)
                .putExtra(CloudAuthResultActivity.EXTRA_OUTCOME, outcome)
                .putExtra(CloudAuthResultActivity.EXTRA_PROVIDER, provider.location.name),
            PendingIntent.FLAG_UPDATE_CURRENT or mutability
        )

        Logger.i(TAG, "launching sign-in for ${provider.location}")
        authService.performAuthorizationRequest(
            request,
            pending(REQUEST_CODE_BASE + provider.location.ordinal * 2, CloudAuthResultActivity.OUTCOME_COMPLETED),
            pending(REQUEST_CODE_BASE + provider.location.ordinal * 2 + 1, CloudAuthResultActivity.OUTCOME_CANCELLED)
        )

        return try {
            deferred.await()
        } finally {
            authService.dispose()
        }
    }

    private companion object {
        const val TAG = "CloudSignIn"
        const val REQUEST_CODE_BASE = 200
    }
}
