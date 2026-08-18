package com.gallery.sync.data.local.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "backup_settings")

/** How the user wants automatic backup to behave. */
data class BackupPreferences(
    val isAutomaticEnabled: Boolean = false,
    val allowMeteredNetwork: Boolean = false
)

/**
 * Persisted backup preferences.
 *
 * Both defaults are deliberately the cautious ones. Automatic backup stays off until the user
 * turns it on, so installing a build never starts uploading a library on its own. Mobile data
 * stays off because uploading several gigabytes over a metered connection is an expensive
 * surprise unless it was chosen on purpose.
 */
@Singleton
class BackupSettings @Inject constructor(
    @ApplicationContext private val context: Context
) {

    val preferences: Flow<BackupPreferences> = context.dataStore.data.map { stored ->
        BackupPreferences(
            isAutomaticEnabled = stored[KEY_AUTOMATIC] ?: false,
            allowMeteredNetwork = stored[KEY_ALLOW_METERED] ?: false
        )
    }

    suspend fun current(): BackupPreferences = preferences.first()

    suspend fun setAutomaticEnabled(enabled: Boolean) {
        context.dataStore.edit { it[KEY_AUTOMATIC] = enabled }
    }

    suspend fun setAllowMeteredNetwork(allowed: Boolean) {
        context.dataStore.edit { it[KEY_ALLOW_METERED] = allowed }
    }

    private companion object {
        val KEY_AUTOMATIC = booleanPreferencesKey("automatic_backup_enabled")
        val KEY_ALLOW_METERED = booleanPreferencesKey("allow_metered_network")
    }
}
