package com.gallery.sync.data.local.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.gallery.sync.data.local.entity.AlbumMode
import com.gallery.sync.domain.backup.CloudDeletionGrace
import com.gallery.sync.domain.backup.CloudDeletionPolicy
import com.gallery.sync.domain.backup.FirstBackupWindow
import com.gallery.sync.domain.backup.RemoteRoots
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "backup_settings")

/** How the user wants automatic backup to behave. */
data class BackupPreferences(
    val isAutomaticEnabled: Boolean = true,
    val allowMeteredNetwork: Boolean = false,
    val isAutoOptimiseEnabled: Boolean = false,
    val defaultAlbumMode: AlbumMode = AlbumMode.DEFAULT,
    /**
     * Folder in OneDrive that **new** uploads go into.
     *
     * Only the destination. `Samsung Gallery/DCIM` stays searchable whatever this is set to, so
     * changing it redirects new files without stranding what is already backed up — see
     * [RemoteRoots].
     */
    val destinationRoot: String = RemoteRoots.DEFAULT_DESTINATION,
    /** Hour of day the first whole-library backup may begin. */
    val firstBackupStartHour: Int = FirstBackupWindow.DEFAULT_START_HOUR,
    /** Whether that first run waits for the phone to be plugged in. On by default. */
    val firstBackupRequiresCharging: Boolean = true,
    /**
     * Whether the backlog has been cleared once.
     *
     * The window gates the *first* upload, which is the only one large enough to matter. Once the
     * queue has drained, every later run is incremental and the restriction lifts — leaving it on
     * would mean a photo taken at noon waits until 1am for no reason.
     */
    val hasCompletedFirstBackup: Boolean = false,
    /**
     * What happens to the OneDrive copy when a file leaves the phone.
     *
     * Defaults to [CloudDeletionPolicy.LEAVE]. A cloud copy left behind costs storage; a cloud copy
     * removed in error costs the photo, because the local one is already gone.
     */
    val cloudDeletionPolicy: CloudDeletionPolicy = CloudDeletionPolicy.DEFAULT,
    /** How long a file must have been gone before its cloud copy may even be offered. */
    val cloudDeletionGraceDays: Int = CloudDeletionGrace.DEFAULT_DAYS
)

/**
 * Persisted backup preferences.
 *
 * Automatic sync is **on** by default, changed 19 Aug 2026. Nothing can be uploaded before the user
 * signs in, so signing in is the consent moment rather than a separate switch — and an app whose
 * purpose is keeping files safe should not sit idle waiting to be told to start.
 *
 * The other two stay cautious, and for different reasons. Mobile data stays off because uploading
 * gigabytes over a metered connection is an expensive surprise unless it was chosen on purpose.
 * Automatic optimising stays off because it rewrites photos, which is not undoable from the phone.
 */
@Singleton
class BackupSettings @Inject constructor(
    @ApplicationContext private val context: Context
) {

    val preferences: Flow<BackupPreferences> = context.dataStore.data.map { stored ->
        BackupPreferences(
            isAutomaticEnabled = stored[KEY_AUTOMATIC] ?: true,
            allowMeteredNetwork = stored[KEY_ALLOW_METERED] ?: false,
            isAutoOptimiseEnabled = stored[KEY_AUTO_OPTIMISE] ?: false,
            defaultAlbumMode = stored[KEY_DEFAULT_ALBUM_MODE]
                ?.let { runCatching { AlbumMode.valueOf(it) }.getOrNull() }
                ?.takeIf { it in AlbumMode.canBeDefault }
                ?: AlbumMode.DEFAULT,
            // Validated on the way out, not only on the way in. A stored value that is somehow
            // unusable must fall back to the default rather than sending uploads to a path Graph
            // will reject on every file, forever.
            destinationRoot = stored[KEY_DESTINATION_ROOT]
                ?.takeIf { RemoteRoots.isValidDestination(it) }
                ?: RemoteRoots.DEFAULT_DESTINATION,
            firstBackupStartHour = stored[KEY_FIRST_BACKUP_HOUR]
                ?.takeIf { it in FirstBackupWindow.SELECTABLE_HOURS }
                ?: FirstBackupWindow.DEFAULT_START_HOUR,
            firstBackupRequiresCharging = stored[KEY_FIRST_BACKUP_CHARGING] ?: true,
            hasCompletedFirstBackup = stored[KEY_FIRST_BACKUP_DONE] ?: false,
            // An unreadable value falls back to LEAVE, never to ASK. A corrupt preference must not
            // be able to arm the one feature that removes a user's last copy.
            cloudDeletionPolicy = stored[KEY_CLOUD_DELETION_POLICY]
                ?.let { runCatching { CloudDeletionPolicy.valueOf(it) }.getOrNull() }
                ?: CloudDeletionPolicy.DEFAULT,
            cloudDeletionGraceDays = stored[KEY_CLOUD_DELETION_GRACE]
                ?.takeIf { it in CloudDeletionGrace.SELECTABLE_DAYS }
                ?: CloudDeletionGrace.DEFAULT_DAYS
        )
    }

    suspend fun current(): BackupPreferences = preferences.first()

    suspend fun setAutomaticEnabled(enabled: Boolean) {
        context.dataStore.edit { it[KEY_AUTOMATIC] = enabled }
    }

    suspend fun setAllowMeteredNetwork(allowed: Boolean) {
        context.dataStore.edit { it[KEY_ALLOW_METERED] = allowed }
    }

    /**
     * Whether photos may be optimised without being asked each time.
     *
     * Off by default, like the others. Turning it on cannot make optimising fully unattended —
     * Android requires a confirmation dialog for every batch, and that dialog can only be raised
     * from an Activity. What it changes is that the app asks when there is something to do, rather
     * than waiting to be found.
     */
    suspend fun setAutoOptimiseEnabled(enabled: Boolean) {
        context.dataStore.edit { it[KEY_AUTO_OPTIMISE] = enabled }
    }

    suspend fun setDefaultAlbumMode(mode: AlbumMode) {
        context.dataStore.edit { it[KEY_DEFAULT_ALBUM_MODE] = mode.name }
    }

    /**
     * Chooses what happens to cloud copies when files leave the phone.
     *
     * Deliberately has no "automatic" value to set — see [CloudDeletionPolicy].
     */
    suspend fun setCloudDeletionPolicy(policy: CloudDeletionPolicy) {
        context.dataStore.edit { it[KEY_CLOUD_DELETION_POLICY] = policy.name }
    }

    suspend fun setCloudDeletionGraceDays(days: Int) {
        if (days !in CloudDeletionGrace.SELECTABLE_DAYS) return
        context.dataStore.edit { it[KEY_CLOUD_DELETION_GRACE] = days }
    }

    suspend fun setFirstBackupStartHour(hour: Int) {
        if (hour !in FirstBackupWindow.SELECTABLE_HOURS) return
        context.dataStore.edit { it[KEY_FIRST_BACKUP_HOUR] = hour }
    }

    suspend fun setFirstBackupRequiresCharging(required: Boolean) {
        context.dataStore.edit { it[KEY_FIRST_BACKUP_CHARGING] = required }
    }

    /**
     * Records that the backlog has been cleared, lifting the overnight window for good.
     *
     * One-way on purpose. Flipping this back would re-impose an overnight wait on someone whose
     * library is already safe, which is the opposite of what the window is for.
     */
    suspend fun markFirstBackupComplete() {
        context.dataStore.edit { it[KEY_FIRST_BACKUP_DONE] = true }
    }

    /**
     * Changes where new uploads go. Rejects a path that cannot work, leaving the old one in place.
     *
     * Nothing already uploaded moves or is forgotten: the old root stays in the search set, so the
     * next reconciliation still finds everything that is there.
     */
    suspend fun setDestinationRoot(path: String): Boolean {
        val normalised = RemoteRoots.normalise(path)
        if (!RemoteRoots.isValidDestination(normalised)) return false
        context.dataStore.edit { it[KEY_DESTINATION_ROOT] = normalised }
        return true
    }

    private companion object {
        val KEY_AUTOMATIC = booleanPreferencesKey("automatic_backup_enabled")
        val KEY_ALLOW_METERED = booleanPreferencesKey("allow_metered_network")
        val KEY_AUTO_OPTIMISE = booleanPreferencesKey("auto_optimise_enabled")
        val KEY_DEFAULT_ALBUM_MODE = stringPreferencesKey("default_album_mode")
        val KEY_DESTINATION_ROOT = stringPreferencesKey("destination_root")
        val KEY_FIRST_BACKUP_HOUR = intPreferencesKey("first_backup_start_hour")
        val KEY_FIRST_BACKUP_CHARGING = booleanPreferencesKey("first_backup_requires_charging")
        val KEY_FIRST_BACKUP_DONE = booleanPreferencesKey("first_backup_completed")
        val KEY_CLOUD_DELETION_POLICY = stringPreferencesKey("cloud_deletion_policy")
        val KEY_CLOUD_DELETION_GRACE = intPreferencesKey("cloud_deletion_grace_days")
    }
}
