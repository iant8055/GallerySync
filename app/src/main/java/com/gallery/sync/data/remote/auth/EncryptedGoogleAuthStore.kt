package com.gallery.sync.data.remote.auth

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.gallery.sync.di.IoDispatcher
import com.gallery.sync.util.Logger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * At-rest storage for Google's AppAuth `AuthState` — the access token, refresh token and expiry
 * AppAuth manages as one serialisable blob.
 *
 * A separate file from OneDrive's [EncryptedTokenStore] rather than a second key in it: MSAL keeps
 * its own encrypted cache and never touches that store at all (see [MsalOneDriveTokenProvider]), so
 * there is nothing to share, and signing out of Google alone can wipe this file with no risk of
 * reaching OneDrive's.
 *
 * CLAUDE.md hard rule: OAuth tokens are never written to plain `SharedPreferences`. Same AES256-GCM
 * [MasterKey] / [EncryptedSharedPreferences] shape as [EncryptedTokenStore]. No method here ever
 * logs the token value itself, only whether one is present.
 */
@Singleton
class EncryptedGoogleAuthStore @Inject constructor(
    @param:ApplicationContext private val context: Context,
    @param:IoDispatcher private val dispatcher: CoroutineDispatcher
) {

    private val prefs: SharedPreferences by lazy { createEncryptedPreferences() }

    /** The serialized `AuthState`, or null when never signed in, or signed out. */
    suspend fun readState(): String? = withContext(dispatcher) {
        val state = prefs.getString(KEY_AUTH_STATE, null)
        Logger.d(TAG, "read Google auth state; present: ${state != null}")
        state
    }

    /** Stores [serialized], or clears it when [serialized] is `null`. */
    suspend fun writeState(serialized: String?) = withContext(dispatcher) {
        prefs.edit().apply {
            if (serialized == null) remove(KEY_AUTH_STATE) else putString(KEY_AUTH_STATE, serialized)
        }.commit()
        Logger.d(TAG, "wrote Google auth state; present: ${serialized != null}")
        Unit
    }

    /** Wipes everything in this secure preferences file. */
    suspend fun clear() = withContext(dispatcher) {
        prefs.edit().clear().commit()
        Logger.d(TAG, "cleared Google auth state")
        Unit
    }

    private fun createEncryptedPreferences(): SharedPreferences {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        return EncryptedSharedPreferences.create(
            context,
            FILE_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    private companion object {
        const val TAG = "GoogleAuthStore"
        const val FILE_NAME = "gallery_sync_google_secure_prefs"
        const val KEY_AUTH_STATE = "google_photos_auth_state"
    }
}
