package com.gallery.sync.data.remote.auth

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
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
 * Receives AppAuth's completed/cancelled `PendingIntent` once the browser tab closes.
 *
 * Not a UI screen — it exchanges the authorization code for tokens, persists the result, hands it
 * to whoever is waiting via [GoogleSignInResultBridge], and finishes immediately. See
 * [AppAuthGooglePhotosSignIn] for why this indirection exists instead of the calling Activity's own
 * `onActivityResult`: the browser redirect returns through AppAuth's own
 * `RedirectUriReceiverActivity`, not through whichever screen started the flow. Declared with no
 * intent-filter and `exported="false"` in the manifest — only our own `PendingIntent`s ever target
 * it.
 */
@AndroidEntryPoint
class GoogleAuthResultActivity : ComponentActivity() {

    @Inject lateinit var authStore: EncryptedGoogleAuthStore

    @Inject lateinit var resultBridge: GoogleSignInResultBridge

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (intent?.getStringExtra(EXTRA_OUTCOME) == OUTCOME_CANCELLED) {
            Logger.d(TAG, "sign-in cancelled by user")
            resultBridge.deliver(SignInResult.Cancelled)
            finish()
            return
        }

        val response = AuthorizationResponse.fromIntent(intent)
        val authException = AuthorizationException.fromIntent(intent)

        if (response == null) {
            Logger.e(TAG, "sign-in failed: ${authException?.errorDescription ?: "no authorization response"}")
            resultBridge.deliver(SignInResult.Failed(authException?.error ?: "no_response"))
            finish()
            return
        }

        lifecycleScope.launch {
            val authService = AuthorizationService(applicationContext)
            try {
                resultBridge.deliver(exchangeCode(authService, response))
            } finally {
                authService.dispose()
                finish()
            }
        }
    }

    private suspend fun exchangeCode(
        authService: AuthorizationService,
        response: AuthorizationResponse
    ): SignInResult {
        val (tokenResponse, tokenException) = performTokenRequest(authService, response)

        // AppAuth's own documented shape: seed AuthState from the authorization step, then fold
        // the token step in. It is what AuthState uses afterward to decide whether it holds a
        // usable refresh token at all.
        val state = AuthState(response, null)
        state.update(tokenResponse, tokenException)

        if (tokenResponse == null) {
            Logger.e(TAG, "token exchange failed: ${tokenException?.errorDescription}")
            return SignInResult.Failed(tokenException?.error ?: "token_exchange_failed")
        }

        authStore.writeState(state.jsonSerializeString())
        Logger.i(TAG, "signed in")
        return SignInResult.Success(GooglePhotosSignIn.ACCOUNT_LABEL)
    }

    private suspend fun performTokenRequest(
        authService: AuthorizationService,
        response: AuthorizationResponse
    ): Pair<TokenResponse?, AuthorizationException?> = suspendCancellableCoroutine { continuation ->
        authService.performTokenRequest(response.createTokenExchangeRequest()) { tokenResponse, exception ->
            continuation.resume(tokenResponse to exception)
        }
    }

    companion object {
        private const val TAG = "GoogleAuthResult"

        const val EXTRA_OUTCOME = "outcome"
        const val OUTCOME_COMPLETED = "completed"
        const val OUTCOME_CANCELLED = "cancelled"
    }
}
