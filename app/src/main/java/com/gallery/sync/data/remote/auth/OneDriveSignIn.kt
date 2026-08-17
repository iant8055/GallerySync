package com.gallery.sync.data.remote.auth

import android.app.Activity

/**
 * Interactive OneDrive sign-in and sign-out.
 *
 * Separate from [OneDriveTokenProvider] because the two have genuinely different requirements:
 * acquiring a token silently needs nothing but application context and happens on background
 * work, while signing in needs a foreground `Activity` to host the browser tab. Keeping them
 * apart is what lets the token provider stay a context-free singleton.
 *
 * This interface exists so the sign-in UI can be unit tested against a fake — MSAL's own types
 * cannot be constructed off-device.
 */
interface OneDriveSignIn {

    /** Display name of the signed-in account, or `null` when signed out. */
    suspend fun currentAccountName(): String?

    /** Runs the interactive sign-in flow, hosting the browser tab on [activity]. */
    suspend fun signIn(activity: Activity): SignInResult

    /** Removes the account and clears MSAL's cache. Returns whether it succeeded. */
    suspend fun signOut(): Boolean
}

/** Outcome of an interactive sign-in attempt. */
sealed interface SignInResult {

    data class Success(val accountName: String) : SignInResult

    /** The user backed out of the browser tab. Not an error — no message should be shown. */
    data object Cancelled : SignInResult

    data class Failed(val errorCode: String) : SignInResult
}
