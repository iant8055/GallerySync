package com.gallery.sync.data.remote.cloud

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.gallery.sync.data.remote.auth.GoogleSignInResultBridge
import com.gallery.sync.data.remote.auth.SignInResult
import com.gallery.sync.util.Logger
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Catches pCloud's redirect (`com.gallery.sync.pcloud:/callback#access_token=...&hostname=...`).
 *
 * Not a screen. The token arrives in the URL fragment, which AppAuth's receiver does not read, so
 * pCloud has its own. The `state` value must match the one this app sent — otherwise anything able to
 * open that URL could plant a token — and the stored copy is cleared once used.
 */
@AndroidEntryPoint
class PCloudRedirectActivity : ComponentActivity() {

    @Inject lateinit var secrets: EncryptedCloudSecretsStore

    @Inject lateinit var bridge: GoogleSignInResultBridge

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val uri = intent?.data
        lifecycleScope.launch {
            try {
                bridge.deliver(handle(uri?.fragment, uri?.query))
            } finally {
                finish()
            }
        }
    }

    private suspend fun handle(fragment: String?, query: String?): SignInResult {
        val params = PCloudCloud.parseParams(fragment) + PCloudCloud.parseParams(query)
        val expected = secrets.read(PCloudCloud.KEY_PENDING_STATE)
        secrets.clearPrefix(PCloudCloud.KEY_PENDING_STATE)

        if (params["error"] != null) {
            Logger.w(TAG, "pCloud sign-in refused")
            return SignInResult.Cancelled
        }
        if (expected == null || params["state"] != expected) {
            Logger.w(TAG, "pCloud redirect had no matching state; ignored")
            return SignInResult.Failed("state_mismatch")
        }
        val token = params["access_token"] ?: return SignInResult.Failed("no_token")

        secrets.write(PCloudCloud.KEY_TOKEN, token)
        secrets.write(PCloudCloud.KEY_HOST, params["hostname"] ?: PCloudCloud.DEFAULT_HOST)
        Logger.i(TAG, "signed in to pCloud")
        return SignInResult.Success(PCloudCloud.LABEL)
    }

    private companion object {
        const val TAG = "PCloudRedirect"
    }
}
