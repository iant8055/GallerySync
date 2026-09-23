package com.gallery.sync.data.remote.auth

import android.app.Activity

/**
 * Interactive Google sign-in and sign-out, mirroring [OneDriveSignIn]'s shape and split for the
 * same reason: a fake for the sign-in UI to be unit tested against, since AppAuth's real types
 * cannot be constructed off-device either.
 *
 * ### Why [currentAccountName] cannot return an email
 *
 * The two scopes Ian registered — `photoslibrary.readonly.appcreateddata`,
 * `photoslibrary.appendonly` — carry no profile information, no `openid`, no `email`. Requesting
 * either would widen the consent screen beyond what was actually set up in Google Cloud Console,
 * which is a decision for Ian, not this class. So a signed-in account reads as [ACCOUNT_LABEL]
 * rather than a real name.
 */
interface GooglePhotosSignIn {

    /** [ACCOUNT_LABEL] when signed in, `null` when signed out. */
    suspend fun currentAccountName(): String?

    /** Runs the interactive sign-in flow, hosting the browser tab from [activity]. */
    suspend fun signIn(activity: Activity): SignInResult

    /** Clears the stored auth state. Always succeeds — there is no server-side session to fail. */
    suspend fun signOut(): Boolean

    companion object {
        const val ACCOUNT_LABEL = "Google Photos"
    }
}
