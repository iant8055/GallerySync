package com.gallery.sync.data.remote.auth

import kotlinx.coroutines.CompletableDeferred
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Hands the outcome of an in-flight Google sign-in from [GoogleAuthResultActivity] back to whatever
 * suspended call started it.
 *
 * Needed because AppAuth's browser redirect returns through its own
 * `net.openid.appauth.RedirectUriReceiverActivity`, not through the calling Activity's
 * `onActivityResult` — completion arrives as a *separate* Activity launch
 * ([GoogleAuthResultActivity], started via the `completedIntent`/`canceledIntent` PendingIntents
 * `AuthorizationService.performAuthorizationRequest` takes). This is the one shared, mutable seam
 * that bridges the two, the AppAuth-shaped equivalent of MSAL's own internal callback plumbing.
 */
@Singleton
class GoogleSignInResultBridge @Inject constructor() {

    @Volatile
    private var pending: CompletableDeferred<SignInResult>? = null

    /** Registers a new wait, replacing any stale one that was never resolved. */
    fun beginWait(): CompletableDeferred<SignInResult> {
        val deferred = CompletableDeferred<SignInResult>()
        pending = deferred
        return deferred
    }

    /** Delivers [result] to whoever is waiting, if anyone still is. */
    fun deliver(result: SignInResult) {
        pending?.let { if (!it.isCompleted) it.complete(result) }
        pending = null
    }
}
