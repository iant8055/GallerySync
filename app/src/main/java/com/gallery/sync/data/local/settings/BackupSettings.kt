package com.gallery.sync.data.local.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.gallery.sync.data.local.entity.AlbumMode
import com.gallery.sync.domain.backup.MediaAge
import com.gallery.sync.domain.backup.OptimiseMode
import com.gallery.sync.domain.backup.VideoQuality
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
    /**
     * The master switch: may this app make smaller local copies at all?
     *
     * Ian's first question in the Settings section, 28 Aug 2026 - *"Do you want Gallery Sync to
     * Optimize your photos and video to save space"*. Off until asked, because it is the only
     * setting here that changes files on the phone.
     *
     * Replaces `isAutoOptimiseEnabled`, which was photo-only and conflated two questions: whether to
     * optimise at all, and whether to do it without asking. Those are now this and
     * [photoOptimiseMode] / [videoOptimiseMode].
     */
    val isOptimiseEnabled: Boolean = false,
    /** Whether photos are optimised on their own, or on a tap. */
    val photoOptimiseMode: OptimiseMode = OptimiseMode.DEFAULT,
    /** How old a photo must be before it may be optimised. Per file - see [MediaAge]. */
    val photoOptimiseAge: MediaAge = MediaAge.DEFAULT,
    /** Whether video is optimised on its own, or on a tap. */
    val videoOptimiseMode: OptimiseMode = OptimiseMode.DEFAULT,
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
    val cloudDeletionGraceDays: Int = CloudDeletionGrace.DEFAULT_DAYS,
    /**
     * Whether the restore screen lists folders OneDrive reports as holding nothing.
     *
     * Off by default. On a real drive most of them are empty — four of the first five rows on the
     * Fold 4 — and an empty folder on a RESTORE screen offers nothing to restore, so listing it is
     * noise between the folders that do. Ian asked for it as a choice rather than a decision, 25 Aug
     * 2026, and it is the right shape for one: hiding costs nothing recoverable, and someone who
     * expects a folder to be there needs a way to confirm it is.
     */
    val showEmptyCloudFolders: Boolean = false,
    /**
     * Setup topics whose explanation the user has explicitly acknowledged.
     *
     * Holds [com.gallery.sync.domain.setup.SetupTopic.key] values. This records that the
     * explanation was *put in front of them and deliberately dismissed* — not that they consented
     * to anything, and not that they understood it. Choosing Archive for an album still raises its
     * own confirmation; the two must never be collapsed, because one is "I know what this does" and
     * the other is "do it to this album".
     *
     * Per topic rather than per tour, so that adding an eleventh topic later does not re-run setup
     * for everyone, and someone who read the Archive explanation during the tour is not shown it
     * again at first use.
     */
    val acknowledgedTopics: Set<String> = emptySet(),
    /**
     * Whether guided setup has been finished or deliberately skipped.
     *
     * Separate from having sources granted, because the two answer different questions. A user who
     * skips the tour has completed setup; a user whose grants were later revoked has not lost it.
     * The wizard still runs regardless of this flag while Gate 1 is unanswered — an install with
     * no granted tree can only reach a screen reporting zero albums and offering a Rescan that
     * cannot succeed, which is what two of two fresh installs hit on 26 and 28 Aug 2026.
     */
    val hasCompletedSetup: Boolean = false,
    /**
     * Whether the user has held backing up until they say otherwise.
     *
     * A preference the worker consults, **not** a cancelled job. Backup has three automatic
     * triggers — armed on every launch, content-triggered on new media, and a six-hourly safety
     * net — so cancelling the running chain would last until the next trigger, which is minutes.
     *
     * Set by Pause. Cleared by Resume, which starts a run at once, and by Stop, which does not:
     * Stop means "end this run and go back to normal automatic behaviour", and the hold is part of
     * what it undoes.
     */
    val isPaused: Boolean = false,
    /**
     * When an upload was last interrupted by the user, or 0 if it has not been.
     *
     * Held here rather than on the ledger row because a new column means a Room migration, and
     * there is at most one upload in flight at a time. A crash leaves this stale rather than set,
     * which is the safe direction: a stale timestamp is old, and old means the session is
     * discarded.
     */
    val uploadInterruptedAtEpochMillis: Long = 0L,
    /**
     * Bytes outstanding when the current run began, or 0 when no run is in progress.
     *
     * The denominator for run progress. Without it the only honest percentage is of the whole
     * selected library, which on a mostly-backed-up phone opens a fresh run at 93% — a true
     * statement about the library and a useless one about the run. Observed on the Fold 4,
     * 28 Aug 2026: 7,516 MB already uploaded against 574 MB pending.
     *
     * Persisted rather than held in memory because a run is a chain of worker invocations, and a
     * baseline captured per invocation would reset every batch. That is the defect this replaced.
     */
    val runBaselineBytes: Long = 0L,
    /**
     * When the user's "ask me later" on the Archive summons runs out, or 0 if none was set.
     *
     * Persisted rather than held in the ViewModel, which is where it lived until 28 Aug 2026. In
     * memory it died the moment the app closed — so someone who chose Delay and then left got the
     * exit warning anyway, and the snooze they had just set did nothing. A delay that does not
     * survive leaving is not a delay.
     */
    val archiveDelayedUntilEpochMillis: Long = 0L,
    /**
     * How hard to shrink video. See [com.gallery.sync.domain.backup.VideoQuality].
     *
     * Defaults to High - 480p - because that is what the evidence supports rather than what caution
     * would suggest. Ian compared all four sweep outputs on the Fold's inner display and could not
     * tell them apart.
     */
    val videoQuality: VideoQuality = VideoQuality.DEFAULT,
    /**
     * How old a clip must be before it may be optimised. See [MediaAge].
     *
     * Defaults to a year, the cautious end. Gates the local optimise and **never** the upload - a
     * clip is sent to OneDrive the moment it qualifies whatever its age, because a threshold that
     * held new video out of the cloud would rebuild the founding failure while wearing the name of
     * the fix.
     */
    val videoOptimiseAge: MediaAge = MediaAge.DEFAULT
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
            isOptimiseEnabled = stored[KEY_OPTIMISE_ENABLED] ?: false,
            photoOptimiseMode = OptimiseMode.fromNameOrDefault(stored[KEY_PHOTO_OPTIMISE_MODE]),
            photoOptimiseAge = MediaAge.fromNameOrDefault(stored[KEY_PHOTO_OPTIMISE_AGE]),
            videoOptimiseMode = OptimiseMode.fromNameOrDefault(stored[KEY_VIDEO_OPTIMISE_MODE]),
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
                ?: CloudDeletionGrace.DEFAULT_DAYS,
            showEmptyCloudFolders = stored[KEY_SHOW_EMPTY_FOLDERS] ?: false,
            acknowledgedTopics = stored[KEY_ACKNOWLEDGED_TOPICS] ?: emptySet(),
            hasCompletedSetup = stored[KEY_SETUP_COMPLETE] ?: false,
            isPaused = stored[KEY_PAUSED] ?: false,
            uploadInterruptedAtEpochMillis = stored[KEY_INTERRUPTED_AT] ?: 0L,
            runBaselineBytes = stored[KEY_RUN_BASELINE] ?: 0L,
            archiveDelayedUntilEpochMillis = stored[KEY_ARCHIVE_DELAYED_UNTIL] ?: 0L,
            videoQuality = VideoQuality.fromNameOrDefault(stored[KEY_VIDEO_QUALITY]),
            videoOptimiseAge = MediaAge.fromNameOrDefault(stored[KEY_VIDEO_OPTIMISE_AGE])
        )
    }

    suspend fun current(): BackupPreferences = preferences.first()

    /**
     * Records that a topic's explanation was acknowledged.
     *
     * Additive and idempotent. Nothing removes an acknowledgement, including re-running setup —
     * the record is about what the user has been shown across the life of the install, so clearing
     * it would mean re-teaching someone what they already read.
     */
    /**
     * Whether a setup decision has ever been written.
     *
     * Absent is not the same as false. Absent means this install predates guided setup and should
     * be backfilled; a stored false means the user pressed "Run setup again" and is owed the
     * wizard. Collapsing the two lets the upgrade backfill silently undo an explicit request —
     * observed on the Fold 4, 28 Aug 2026, where reopening the app after asking to re-run setup
     * put the tabs back.
     */
    suspend fun hasSetupDecision(): Boolean =
        context.dataStore.data.first()[KEY_SETUP_COMPLETE] != null

    /**
     * Records what the current run set out to move, so progress can be a proportion of it.
     *
     * Never lowered while a run is live — files added midway raise it, so the reported progress
     * slows rather than jumping backwards.
     */
    suspend fun setRunBaselineBytes(bytes: Long) {
        context.dataStore.edit { it[KEY_RUN_BASELINE] = bytes }
    }

    /** Stamps the moment a run was interrupted, so a later resume can judge the held session. */
    suspend fun setUploadInterruptedAt(millis: Long) {
        context.dataStore.edit { it[KEY_INTERRUPTED_AT] = millis }
    }

    /** How hard to optimise video. See [VideoQuality]. */
    suspend fun setVideoQuality(quality: VideoQuality) {
        context.dataStore.edit { it[KEY_VIDEO_QUALITY] = quality.name }
    }

    /** How old a clip must be before it may be optimised. See [MediaAge]. */
    suspend fun setVideoOptimiseAge(age: MediaAge) {
        context.dataStore.edit { it[KEY_VIDEO_OPTIMISE_AGE] = age.name }
    }

    /** The master switch for making smaller local copies at all. */
    suspend fun setOptimiseEnabled(enabled: Boolean) {
        context.dataStore.edit { it[KEY_OPTIMISE_ENABLED] = enabled }
    }

    suspend fun setPhotoOptimiseMode(mode: OptimiseMode) {
        context.dataStore.edit { it[KEY_PHOTO_OPTIMISE_MODE] = mode.name }
    }

    suspend fun setPhotoOptimiseAge(age: MediaAge) {
        context.dataStore.edit { it[KEY_PHOTO_OPTIMISE_AGE] = age.name }
    }

    suspend fun setVideoOptimiseMode(mode: OptimiseMode) {
        context.dataStore.edit { it[KEY_VIDEO_OPTIMISE_MODE] = mode.name }
    }

    /**
     * Records the user's "ask me later" on the Archive summons.
     *
     * Their choice to make, not the app deciding to stop asking — see [ArchiveDelay].
     */
    suspend fun setArchiveDelayedUntil(millis: Long) {
        context.dataStore.edit { it[KEY_ARCHIVE_DELAYED_UNTIL] = millis }
    }

    /** Holds backing up until Resume or Stop. See [BackupPreferences.isPaused]. */
    suspend fun setPaused(paused: Boolean) {
        context.dataStore.edit { it[KEY_PAUSED] = paused }
    }

    /** Marks guided setup finished. Skipping counts — the tour is optional, the gates are not. */
    suspend fun setSetupCompleted(completed: Boolean) {
        context.dataStore.edit { it[KEY_SETUP_COMPLETE] = completed }
    }

    suspend fun acknowledgeTopic(key: String) {
        context.dataStore.edit {
            it[KEY_ACKNOWLEDGED_TOPICS] = (it[KEY_ACKNOWLEDGED_TOPICS] ?: emptySet()) + key
        }
    }

    suspend fun setAutomaticEnabled(enabled: Boolean) {
        context.dataStore.edit { it[KEY_AUTOMATIC] = enabled }
    }

    suspend fun setAllowMeteredNetwork(allowed: Boolean) {
        context.dataStore.edit { it[KEY_ALLOW_METERED] = allowed }
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

    suspend fun setShowEmptyCloudFolders(show: Boolean) {
        context.dataStore.edit { it[KEY_SHOW_EMPTY_FOLDERS] = show }
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
        // New key rather than reusing auto_optimise_enabled. That one meant "optimise photos
        // without asking", and this means "optimise at all" - a stored true would silently answer a
        // broader question than the user was asked, and now covers video as well.
        val KEY_OPTIMISE_ENABLED = booleanPreferencesKey("optimise_enabled")
        val KEY_PHOTO_OPTIMISE_MODE = stringPreferencesKey("photo_optimise_mode")
        val KEY_PHOTO_OPTIMISE_AGE = stringPreferencesKey("photo_optimise_age")
        val KEY_VIDEO_OPTIMISE_MODE = stringPreferencesKey("video_optimise_mode")
        val KEY_DEFAULT_ALBUM_MODE = stringPreferencesKey("default_album_mode")
        val KEY_DESTINATION_ROOT = stringPreferencesKey("destination_root")
        val KEY_FIRST_BACKUP_HOUR = intPreferencesKey("first_backup_start_hour")
        val KEY_FIRST_BACKUP_CHARGING = booleanPreferencesKey("first_backup_requires_charging")
        val KEY_FIRST_BACKUP_DONE = booleanPreferencesKey("first_backup_completed")
        val KEY_CLOUD_DELETION_POLICY = stringPreferencesKey("cloud_deletion_policy")
        val KEY_CLOUD_DELETION_GRACE = intPreferencesKey("cloud_deletion_grace_days")
        val KEY_SHOW_EMPTY_FOLDERS = booleanPreferencesKey("show_empty_cloud_folders")
        val KEY_ACKNOWLEDGED_TOPICS = stringSetPreferencesKey("acknowledged_topics")
        val KEY_SETUP_COMPLETE = booleanPreferencesKey("setup_complete")
        val KEY_PAUSED = booleanPreferencesKey("backup_paused")
        val KEY_INTERRUPTED_AT = longPreferencesKey("upload_interrupted_at")
        val KEY_RUN_BASELINE = longPreferencesKey("run_baseline_bytes")
        val KEY_ARCHIVE_DELAYED_UNTIL = longPreferencesKey("archive_delayed_until")
        val KEY_VIDEO_QUALITY = stringPreferencesKey("video_quality")
        val KEY_VIDEO_OPTIMISE_AGE = stringPreferencesKey("video_optimise_age")
    }
}
