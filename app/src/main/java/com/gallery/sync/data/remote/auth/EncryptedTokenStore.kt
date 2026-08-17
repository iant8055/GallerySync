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
 * At-rest storage for the OneDrive OAuth access token.
 *
 * CLAUDE.md hard rule: OAuth tokens are **never** written to plain `SharedPreferences`. This is
 * backed by [EncryptedSharedPreferences] with an AES256-GCM [MasterKey] held in the Android
 * Keystore, so the on-disk XML holds only ciphertext for both keys and values.
 *
 * No method here ever logs a token value — only whether one is present.
 */
@Singleton
class EncryptedTokenStore @Inject constructor(
    @param:ApplicationContext private val context: Context,
    @param:IoDispatcher private val dispatcher: CoroutineDispatcher
) {

    /**
     * Built lazily because construction does Keystore work and touches disk: doing it eagerly
     * would drag that onto whichever thread first injects this class. Every read of this property
     * happens inside [withContext] on the IO dispatcher.
     */
    private val prefs: SharedPreferences by lazy { createEncryptedPreferences() }

    suspend fun readAccessToken(): String? = withContext(dispatcher) {
        val token = prefs.getString(KEY_ACCESS_TOKEN, null)
        Logger.d(TAG, "read access token; token present: ${token != null}")
        token
    }

    /** Stores [token], or removes the stored token when [token] is `null`. */
    suspend fun writeAccessToken(token: String?) = withContext(dispatcher) {
        prefs.edit().apply {
            if (token == null) remove(KEY_ACCESS_TOKEN) else putString(KEY_ACCESS_TOKEN, token)
        }.commit()
        Logger.d(TAG, "wrote access token; token present: ${token != null}")
        Unit
    }

    /** Wipes everything in the secure preferences file. */
    suspend fun clear() = withContext(dispatcher) {
        prefs.edit().clear().commit()
        Logger.d(TAG, "cleared secure preferences")
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
        const val TAG = "TokenStore"
        const val FILE_NAME = "gallery_sync_secure_prefs"
        const val KEY_ACCESS_TOKEN = "onedrive_access_token"
    }
}
