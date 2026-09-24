package com.gallery.sync.data.remote.cloud

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.gallery.sync.data.remote.auth.GoogleSignInResultBridge
import com.gallery.sync.data.remote.auth.SignInResult
import com.gallery.sync.util.Logger
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import net.openid.appauth.AuthState
import net.openid.appauth.AuthorizationException
import net.openid.appauth.AuthorizationResponse
import net.openid.appauth.AuthorizationService
import net.openid.appauth.TokenResponse
import javax.inject.Inject
import kotlin.coroutines.resume

/**
 * Receives AppAuth's completed/cancelled `PendingIntent` for the OAuth clouds (Google Drive, Dropbox),
 * exchanges the code, stores the state under the provider's own key, hands the result to whoever is
 * waiting and finishes. The twin of `GoogleAuthResultActivity`, which stays as it is for Google Photos.
 * Not a screen: `exported="false"`, no intent filter.
 */
@AndroidEntryPoint
class CloudAuthResultActivity : ComponentActivity() {

    @Inject lateinit var secrets: EncryptedCloudSecretsStore

    @Inject lateinit var resultBridge: GoogleSignInResultBridge

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val provider = intent?.getStringExtra(EXTRA_PROVIDER)
        if (provider == null || intent?.getStringExtra(EXTRA_OUTCOME) == OUTCOME_CANCELLED) {
            resultBridge.deliver(SignInResult.Cancelled)
            finish()
            return
        }

        val response = AuthorizationResponse.fromIntent(intent)
        val exception = AuthorizationException.fromIntent(intent)
        if (response == null) {
            Logger.e(TAG, "sign-in failed: ${exception?.errorDescription ?: "no authorization response"}")
            resultBridge.deliver(SignInResult.Failed(exception?.error ?: "no_response"))
            finish()
            return
        }

        lifecycleScope.launch {
            val authService = AuthorizationService(applicationContext)
            try {
                resultBridge.deliver(exchange(authService, response, provider))
            } finally {
                authService.dispose()
                finish()
            }
        }
    }

    private suspend fun exchange(
        authService: AuthorizationService,
        response: AuthorizationResponse,
        provider: String
    ): SignInResult {
        val (tokenResponse, tokenException) =
            suspendCancellableCoroutine<Pair<TokenResponse?, AuthorizationException?>> { continuation ->
                authService.performTokenRequest(response.createTokenExchangeRequest()) { token, ex ->
                    continuation.resume(token to ex)
                }
            }
        val state = AuthState(response, null)
        state.update(tokenResponse, tokenException)
        if (tokenResponse == null) {
            Logger.e(TAG, "token exchange failed: ${tokenException?.errorDescription}")
            return SignInResult.Failed(tokenException?.error ?: "token_exchange_failed")
        }
        secrets.write(AppAuthCloudTokens.stateKey(provider), state.jsonSerializeString())
        Logger.i(TAG, "signed in to $provider")
        return SignInResult.Success(provider)
    }

    companion object {
        private const val TAG = "CloudAuthResult"
        const val EXTRA_OUTCOME = "outcome"
        const val EXTRA_PROVIDER = "provider"
        const val OUTCOME_COMPLETED = "completed"
        const val OUTCOME_CANCELLED = "cancelled"
    }
}
