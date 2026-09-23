package com.gallery.sync.data.remote.auth

/**
 * Supplies the OAuth access token used for Google Photos Library API calls.
 *
 * Mirrors [OneDriveTokenProvider]'s shape and contract exactly — null means signed out, and callers
 * never see AppAuth's own types. See TASK-026.
 */
interface GooglePhotosTokenProvider {

    /** The current access token, or `null` when the user is not signed in. */
    suspend fun getAccessToken(): String?

    /** Drops the current access token after the API rejected it (HTTP 401), forcing a refresh. */
    suspend fun invalidateAccessToken()
}
