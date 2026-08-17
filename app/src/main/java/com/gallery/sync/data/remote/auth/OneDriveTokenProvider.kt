package com.gallery.sync.data.remote.auth

/**
 * Supplies the OAuth access token used for Microsoft Graph calls.
 *
 * This is the single integration seam for OneDrive authentication. Everything downstream — the
 * interceptor, the repository, the tests — depends only on this interface, so introducing a real
 * interactive sign-in flow is an implementation swap and nothing else.
 *
 * Suspending rather than callback-based, per CLAUDE.md.
 */
interface OneDriveTokenProvider {

    /** The current access token, or `null` when the user is not signed in. */
    suspend fun getAccessToken(): String?

    /** Drops the current access token after the provider rejected it (HTTP 401). */
    suspend fun invalidateAccessToken()
}
