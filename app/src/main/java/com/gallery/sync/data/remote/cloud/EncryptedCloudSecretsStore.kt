package com.gallery.sync.data.remote.cloud

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
 * At-rest storage for every credential the optional clouds need: access keys, secret keys, and the
 * serialised OAuth state of the providers that sign in through a browser.
 *
 * CLAUDE.md hard rule: credentials are never written to plain SharedPreferences. Same AES256-GCM
 * [MasterKey] / [EncryptedSharedPreferences] shape as `EncryptedTokenStore`, in a file of its own so
 * signing out of one place can never reach another. Values are never logged, only whether one is
 * present. Keys are namespaced `<provider>.<field>` by the callers.
 */
@Singleton
class EncryptedCloudSecretsStore @Inject constructor(
    @param:ApplicationContext private val context: Context,
    @param:IoDispatcher private val dispatcher: CoroutineDispatcher
) {

    private val prefs: SharedPreferences by lazy {
        val masterKey = MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
        EncryptedSharedPreferences.create(
            context,
            FILE_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    suspend fun read(key: String): String? = withContext(dispatcher) { prefs.getString(key, null) }

    suspend fun write(key: String, value: String) = withContext(dispatcher) {
        prefs.edit().putString(key, value).commit()
        Logger.d(TAG, "wrote a secret for $key")
        Unit
    }

    /** Removes every key that starts with [prefix] — one provider's whole set. */
    suspend fun clearPrefix(prefix: String) = withContext(dispatcher) {
        val editor = prefs.edit()
        prefs.all.keys.filter { it.startsWith(prefix) }.forEach { editor.remove(it) }
        editor.commit()
        Logger.d(TAG, "cleared secrets for $prefix")
        Unit
    }

    private companion object {
        const val TAG = "CloudSecrets"
        const val FILE_NAME = "gallery_sync_cloud_secrets"
    }
}
