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
import com.gallery.sync.domain.backup.ArchiveAge
import com.gallery.sync.domain.backup.BackupLocation
import com.gallery.sync.domain.backup.LibraryChoice
import com.gallery.sync.domain.backup.MediaAge
import com.gallery.sync.domain.backup.OptimiseCutoff
import com.gallery.sync.domain.backup.OptimiseMode
import com.gallery.sync.domain.backup.VideoQuality
import com.gallery.sync.domain.backup.CloudDeletionPolicy
import com.gallery.sync.domain.backup.AlbumIdentityRules
import com.gallery.sync.domain.backup.AlbumMergeWarning
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
    /**
     * Optimise photos at all?
     *
     * Ian's structure, and it is deliberately a plain yes/no rather than a third value on
     * [OptimiseMode]: *"do you want to opt video - Y / N. If Y how do you want - Man / Aut. Simple
     * as that."* One question per line, each only asked when the one above it was answered yes.
     */
    /**
     * **Off by default**, like every other setting that rewrites a file. Corrected 7 Sept 2026
     * after Ian found it reading On out of the box.
     *
     * It defaulting On was not merely cosmetic. `isOptimiseEnabled` is off by default, so nothing
     * optimised — but the Settings screen showed the switch On while nothing was happening, and
     * the video row's handler clears the master only `if (!optimisePhotos)`. So turning video on
     * and off again left the master on with photos still marked wanted, and photo optimising
     * began having never been asked for. See DEFAULTS.md.
     */
    val optimisePhotos: Boolean = false,
    /** Whether photos are optimised on their own, or on a tap. Only asked when [optimisePhotos]. */
    val photoOptimiseMode: OptimiseMode = OptimiseMode.DEFAULT,
    // No photo age. Ian, 19 Aug 2026, TASK-011: "Photos are proxied whatever their age ... There is
    // no photo age setting and none is wanted." One was added on 28 Aug anyway, governed nothing,
    // and was removed in TASK-022 Part B. Only video has an age - see [videoOptimiseAge].
    /** Optimise video at all? See [optimisePhotos]. */
    val optimiseVideo: Boolean = false,
    /** Whether video is optimised on its own, or on a tap. Only asked when [optimiseVideo]. */
    val videoOptimiseMode: OptimiseMode = OptimiseMode.DEFAULT,
    /**
     * Folder in OneDrive that **new** uploads go into.
     *
     * Only the destination. `Samsung Gallery/DCIM` stays searchable whatever this is set to, so
     * changing it redirects new files without stranding what is already backed up — see
     * [RemoteRoots].
     */
    val destinationRoot: String = RemoteRoots.DEFAULT_DESTINATION,
    /**
     * Where **new** uploads go, app-wide — one setting, not per-album. Ian, 23 Sept 2026, after
     * walking through the per-album alternative: the split-history problem (files already uploaded
     * stay wherever they are; only new ones follow a destination change) exists either way, and a
     * single global setting means one timeline to explain instead of one per album, for a capability
     * — genuinely simultaneous per-album routing — nobody had actually asked for. See TASK-026.
     *
     * Exactly [destinationRoot]'s own shape: changing this redirects new files without moving or
     * re-checking anything already sent. [com.gallery.sync.data.local.entity.BackupEntryEntity
     * .location] is what remembers where each individual file actually went, frozen at upload time.
     */
    val backupLocation: BackupLocation = BackupLocation.DEFAULT,
    /**
     * When the user started the 30-day multi-cloud trial, or null if they never have. Set once and never
     * moved: starting the trial again after it ended would make it not a trial. See
     * [com.gallery.sync.domain.billing.MultiCloudTrial].
     */
    val multiCloudTrialStartedAtEpochMillis: Long? = null,
    /**
     * Albums the user has dealt with: set a mode on, or dismissed the "new albums are waiting" notice for.
     * An album at Off that is not in here is one nobody has chosen for yet, which is what the Albums tab
     * counts as waiting. Names only, so no schema change; an album that disappears leaves a harmless entry.
     */
    val acknowledgedAlbums: Set<String> = emptySet(),
    /** The user has answered "which cloud do you want to keep free?" after the trial ended. */
    val keepFreeAnswered: Boolean = false,
    /** Hour of day the first whole-library backup may begin. */
    val firstBackupStartHour: Int = FirstBackupWindow.DEFAULT_START_HOUR,
    /** Whether that first run waits for the phone to be plugged in. On by default. */
    val firstBackupRequiresCharging: Boolean = true,
    /**
     * Exact instant the wizard's delayed first backup is due, or null when it starts immediately.
     *
     * An absolute timestamp rather than [firstBackupStartHour]'s hour-of-day, because the wizard
     * counts down in real minutes. "Start in 1 hour" chosen at 13:25 means 14:25, where the
     * hour-of-day form could only say "14:00" and would have meant 35 minutes.
     *
     * Persisted rather than held in the composable so the countdown survives the wizard being
     * closed and the process being killed — the whole point of a delay is that the user goes away.
     */
    val firstBackupStartAtEpochMillis: Long? = null,
    /**
     * How long the delay was when it was chosen, so the countdown ring has a denominator.
     *
     * Kept beside the due time rather than derived from it: the ring needs to know it is showing
     * one hour of sixty minutes remaining, not merely that sixty minutes remain.
     */
    val firstBackupDelayMillis: Long? = null,
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
    /**
     * The newest departure the "files deleted from this phone" window has already been shown for,
     * as the moment that file was first seen missing.
     *
     * The window shows only when a file has left the phone since this. "Decide later" sets it to the
     * newest file's time, so the same files do not bring the window back on every open; only a
     * file that goes after it does. Ian, 19 Sept 2026: *"shows only when new files."*
     */
    val deletionPromptSeenUpToEpochMillis: Long = 0L,
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
     * How hard to shrink video. See [com.gallery.sync.domain.backup.VideoQuality].
     *
     * Defaults to High - 480p - because that is what the evidence supports rather than what caution
     * would suggest. Ian compared all four sweep outputs on the Fold's inner display and could not
     * tell them apart.
     */
    val videoQuality: VideoQuality = VideoQuality.DEFAULT,
    /**
     * The one-time install choice, kept so the wizard survives its own process ending.
     *
     * It used to live only in `ReconcileUiState`. Closing the wizard mid-backup — which is the
     * ordinary way to leave a long first run — took the process with it, and the choice came back
     * as the default. The optimise pass at the end of step 9 then found nothing to do and said
     * nothing about it. Moto G, 4 Sept 2026, closed at 40%%: no photo and no video was optimised.
     *
     * This is Area 1 and nothing else. It does not touch album modes, which only the user sets,
     * and it does not touch the ongoing optimise settings.
     */
    val libraryChoice: LibraryChoice = LibraryChoice.DEFAULT,
    /**
     * Files backed up before this moment are left at full size on the phone.
     *
     * Zero means no cutoff, which is the ordinary case. Set only by Gate 2's
     * "optimise only the new" - see [com.gallery.sync.domain.backup.OptimiseCutoff].
     */
    val optimiseCutoffEpochMillis: Long = OptimiseCutoff.EVERYTHING,
    /**
     * How old a clip must be before it may be optimised. See [MediaAge].
     *
     * Defaults to a year, the cautious end. Gates the local optimise and **never** the upload - a
     * clip is sent to OneDrive the moment it qualifies whatever its age, because a threshold that
     * held new video out of the cloud would rebuild the founding failure while wearing the name of
     * the fix.
     */
    val videoOptimiseAge: MediaAge = MediaAge.DEFAULT,
    /**
     * The wizard step the user was on when the app last closed, or 0 if no wizard is in progress.
     *
     * Persisted so that relaunching the app during a Step 9 backup resumes at Step 9 rather than
     * restarting the wizard from scratch. Cleared when the user finishes or skips setup.
     */
    val wizardStep: Int = 0,
    /** How many files the wizard's run will actually send — the card's denominator. */
    val wizardBackupTotal: Int = 0,
    /**
     * When the wizard's run began, or 0. The card counts files uploaded since this moment as done,
     * so a file found already in OneDrive — which keeps its OneDrive arrival date — is not counted
     * as an upload. Persisted so reopening mid-run keeps counting the same way.
     */
    val wizardRunStartedAt: Long = 0L,
    /**
     * What the Archive tab's age filter starts at when the tab is opened. Ian, 22 Sept 2026.
     *
     * A Settings default, not a standing rule the tab enforces on its own — changing the filter on
     * the Archive tab itself is a session choice and does not write this back. See [ArchiveAge].
     */
    val archiveDefaultAge: ArchiveAge = ArchiveAge.DEFAULT,
    /**
     * Whether a notification is sent when files in an Archive album have come of age. Ian, 22 Sept
     * 2026. **Off by default**, like every other setting that was not asked for — this one doubly
     * so, since turning it on requests a runtime permission (`POST_NOTIFICATIONS`) the user has not
     * been asked about yet on first run.
     */
    val archiveNotifyEnabled: Boolean = false,
    /**
     * The ready-to-archive count [ArchiveReadyNotice] last judged, so a notification fires only when
     * the count has **grown** past it, never merely because it is still above zero. Bookkeeping, not
     * a user-facing setting — there is no screen that shows this number.
     */
    val archiveReadyLastSeenCount: Int = 0
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
            optimisePhotos = stored[KEY_OPTIMISE_PHOTOS] ?: false,
            photoOptimiseMode = OptimiseMode.fromNameOrDefault(stored[KEY_PHOTO_OPTIMISE_MODE]),
            optimiseVideo = stored[KEY_OPTIMISE_VIDEO] ?: false,
            videoOptimiseMode = OptimiseMode.fromNameOrDefault(stored[KEY_VIDEO_OPTIMISE_MODE]),
            // Validated on the way out, not only on the way in. A stored value that is somehow
            // unusable must fall back to the default rather than sending uploads to a path Graph
            // will reject on every file, forever.
            destinationRoot = stored[KEY_DESTINATION_ROOT]
                ?.takeIf { RemoteRoots.isValidDestination(it) }
                ?: RemoteRoots.DEFAULT_DESTINATION,
            backupLocation = BackupLocation.fromNameOrDefault(stored[KEY_BACKUP_LOCATION]),
            multiCloudTrialStartedAtEpochMillis = stored[KEY_MULTI_CLOUD_TRIAL_STARTED_AT],
            acknowledgedAlbums = stored[KEY_ACKNOWLEDGED_ALBUMS] ?: emptySet(),
            keepFreeAnswered = stored[KEY_KEEP_FREE_ANSWERED] ?: false,
            firstBackupStartHour = stored[KEY_FIRST_BACKUP_HOUR]
                ?.takeIf { it in FirstBackupWindow.SELECTABLE_HOURS }
                ?: FirstBackupWindow.DEFAULT_START_HOUR,
            firstBackupRequiresCharging = stored[KEY_FIRST_BACKUP_CHARGING] ?: true,
            firstBackupStartAtEpochMillis = stored[KEY_FIRST_BACKUP_START_AT],
            firstBackupDelayMillis = stored[KEY_FIRST_BACKUP_DELAY],
            hasCompletedFirstBackup = stored[KEY_FIRST_BACKUP_DONE] ?: false,
            // An unreadable value falls back to LEAVE, never to ASK. A corrupt preference must not
            // be able to arm the one feature that removes a user's last copy.
            cloudDeletionPolicy = stored[KEY_CLOUD_DELETION_POLICY]
                ?.let { runCatching { CloudDeletionPolicy.valueOf(it) }.getOrNull() }
                ?: CloudDeletionPolicy.DEFAULT,
            deletionPromptSeenUpToEpochMillis = stored[KEY_DELETION_PROMPT_SEEN] ?: 0L,
            showEmptyCloudFolders = stored[KEY_SHOW_EMPTY_FOLDERS] ?: false,
            hasCompletedSetup = stored[KEY_SETUP_COMPLETE] ?: false,
            isPaused = stored[KEY_PAUSED] ?: false,
            uploadInterruptedAtEpochMillis = stored[KEY_INTERRUPTED_AT] ?: 0L,
            runBaselineBytes = stored[KEY_RUN_BASELINE] ?: 0L,
            videoQuality = VideoQuality.fromNameOrDefault(stored[KEY_VIDEO_QUALITY]),
            libraryChoice = LibraryChoice.fromNameOrDefault(stored[KEY_LIBRARY_CHOICE]),
            optimiseCutoffEpochMillis = stored[KEY_OPTIMISE_CUTOFF] ?: OptimiseCutoff.EVERYTHING,
            videoOptimiseAge = MediaAge.fromNameOrDefault(stored[KEY_VIDEO_OPTIMISE_AGE]),
            wizardStep = stored[KEY_WIZARD_STEP] ?: 0,
            wizardBackupTotal = stored[KEY_WIZARD_BACKUP_TOTAL] ?: 0,
            wizardRunStartedAt = stored[KEY_WIZARD_RUN_STARTED_AT] ?: 0L,
            archiveDefaultAge = ArchiveAge.fromNameOrDefault(stored[KEY_ARCHIVE_DEFAULT_AGE]),
            archiveNotifyEnabled = stored[KEY_ARCHIVE_NOTIFY_ENABLED] ?: false,
            archiveReadyLastSeenCount = stored[KEY_ARCHIVE_READY_LAST_SEEN] ?: 0
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
     * Whether a wizard has ever been started on this install.
     *
     * The upgrade backfill needs this because "holds a granted tree" stopped being proof of a
     * pre-existing install the moment the wizard began taking grants of its own. Someone at step 5
     * has grants and no setup decision, and looks identical to an upgrading user — so the backfill
     * declared their half-finished setup complete and dropped them on the tabs with every album
     * Off. Seen on the Moto G, 3 Sept 2026, after the app was reinstalled mid-wizard; a crash,
     * a force-stop or the system reclaiming memory does the same thing.
     */
    suspend fun hasStartedWizard(): Boolean =
        (context.dataStore.data.first()[KEY_WIZARD_STEP] ?: 0) > 0

    /**
     * Whether the one-time upgrade backfill has already run on this install.
     *
     * Persisted rather than held in memory because the original was "one shot, at construction"
     * — true only until something reconstructed the ViewModel, which is exactly what a restart
     * does. A flag on disk is the only version of "once" that survives the process dying.
     */
    suspend fun hasCheckedUpgradeBackfill(): Boolean =
        context.dataStore.data.first()[KEY_BACKFILL_CHECKED] == true

    suspend fun markUpgradeBackfillChecked() {
        context.dataStore.edit { it[KEY_BACKFILL_CHECKED] = true }
    }

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

    /** Records the install choice so it outlives the wizard's own process. */
    suspend fun setLibraryChoice(choice: LibraryChoice) {
        context.dataStore.edit { it[KEY_LIBRARY_CHOICE] = choice.name }
    }

    /** How old a clip must be before it may be optimised. See [MediaAge]. */
    suspend fun setVideoOptimiseAge(age: MediaAge) {
        context.dataStore.edit { it[KEY_VIDEO_OPTIMISE_AGE] = age.name }
    }

    /** Files backed up before this are left alone. See [OptimiseCutoff]. */
    suspend fun setOptimiseCutoff(millis: Long) {
        context.dataStore.edit { it[KEY_OPTIMISE_CUTOFF] = millis }
    }

    /** The master switch for making smaller local copies at all. */
    suspend fun setOptimiseEnabled(enabled: Boolean) {
        context.dataStore.edit { it[KEY_OPTIMISE_ENABLED] = enabled }
    }

    suspend fun setOptimisePhotos(enabled: Boolean) {
        context.dataStore.edit { it[KEY_OPTIMISE_PHOTOS] = enabled }
    }

    suspend fun setOptimiseVideo(enabled: Boolean) {
        context.dataStore.edit { it[KEY_OPTIMISE_VIDEO] = enabled }
    }

    suspend fun setPhotoOptimiseMode(mode: OptimiseMode) {
        context.dataStore.edit { it[KEY_PHOTO_OPTIMISE_MODE] = mode.name }
    }

    suspend fun setVideoOptimiseMode(mode: OptimiseMode) {
        context.dataStore.edit { it[KEY_VIDEO_OPTIMISE_MODE] = mode.name }
    }

    /** Holds backing up until Resume or Stop. See [BackupPreferences.isPaused]. */
    suspend fun setPaused(paused: Boolean) {
        context.dataStore.edit { it[KEY_PAUSED] = paused }
    }

    /** Marks guided setup finished. Skipping counts — the tour is optional, the gates are not. */
    suspend fun setSetupCompleted(completed: Boolean) {
        context.dataStore.edit {
            it[KEY_SETUP_COMPLETE] = completed
            if (completed) {
                it[KEY_WIZARD_STEP] = 0
                it[KEY_WIZARD_BACKUP_TOTAL] = 0
                it[KEY_WIZARD_RUN_STARTED_AT] = 0L
            }
        }
    }

    suspend fun setWizardStep(step: Int) {
        context.dataStore.edit { it[KEY_WIZARD_STEP] = step }
    }

    suspend fun setWizardBackupTotal(total: Int) {
        context.dataStore.edit { it[KEY_WIZARD_BACKUP_TOTAL] = total }
    }

    /** Records the wizard run's denominator and start together, so the two cannot disagree. */
    suspend fun setWizardRun(total: Int, startedAt: Long) {
        context.dataStore.edit {
            it[KEY_WIZARD_BACKUP_TOTAL] = total
            it[KEY_WIZARD_RUN_STARTED_AT] = startedAt
        }
    }

    suspend fun setAutomaticEnabled(enabled: Boolean) {
        context.dataStore.edit { it[KEY_AUTOMATIC] = enabled }
    }

    suspend fun setAllowMeteredNetwork(allowed: Boolean) {
        context.dataStore.edit { it[KEY_ALLOW_METERED] = allowed }
    }

    /**
     * Albums merged from two spellings of one folder and set Off, kept until the user presses Dismiss.
     * TASK-023.
     *
     * Here rather than in Room, so the warning needs no schema change. Oldest first.
     */
    val albumMergeWarnings: Flow<List<AlbumMergeWarning>> = context.dataStore.data.map { stored ->
        stored[KEY_ALBUM_MERGE_WARNINGS].orEmpty()
            .mapNotNull(AlbumIdentityRules::decode)
            .sortedBy { it.atEpochMillis }
    }

    /** Records warnings, replacing any earlier one for the same album so a card is never shown twice. */
    suspend fun addAlbumMergeWarnings(warnings: List<AlbumMergeWarning>) {
        if (warnings.isEmpty()) return
        val replaced = warnings.mapTo(HashSet()) { AlbumIdentityRules.foldCase(it.albumName) }
        context.dataStore.edit { prefs ->
            val kept = prefs[KEY_ALBUM_MERGE_WARNINGS].orEmpty().filter { stored ->
                val name = AlbumIdentityRules.decode(stored)?.albumName ?: return@filter false
                AlbumIdentityRules.foldCase(name) !in replaced
            }
            prefs[KEY_ALBUM_MERGE_WARNINGS] = (kept + warnings.map(AlbumIdentityRules::encode)).toSet()
        }
    }

    /** Clears every warning. The card's Dismiss is the only caller, and the only way a warning goes. */
    suspend fun dismissAllAlbumMergeWarnings() {
        context.dataStore.edit { it.remove(KEY_ALBUM_MERGE_WARNINGS) }
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

    /** What the Archive tab's age filter starts at. See [ArchiveAge]. */
    suspend fun setArchiveDefaultAge(age: ArchiveAge) {
        context.dataStore.edit { it[KEY_ARCHIVE_DEFAULT_AGE] = age.name }
    }

    /** Whether a notification is sent when files in an Archive album have come of age. */
    suspend fun setArchiveNotifyEnabled(enabled: Boolean) {
        context.dataStore.edit { it[KEY_ARCHIVE_NOTIFY_ENABLED] = enabled }
    }

    /** See [ArchiveReadyNotice]. Called after every check, whether or not it notified. */
    suspend fun setArchiveReadyLastSeenCount(count: Int) {
        context.dataStore.edit { it[KEY_ARCHIVE_READY_LAST_SEEN] = count }
    }

    suspend fun setDeletionPromptSeenUpTo(epochMillis: Long) {
        context.dataStore.edit { it[KEY_DELETION_PROMPT_SEEN] = epochMillis }
    }

    suspend fun setFirstBackupStartHour(hour: Int) {
        if (hour !in FirstBackupWindow.SELECTABLE_HOURS) return
        context.dataStore.edit { it[KEY_FIRST_BACKUP_HOUR] = hour }
    }

    suspend fun setFirstBackupRequiresCharging(required: Boolean) {
        context.dataStore.edit { it[KEY_FIRST_BACKUP_CHARGING] = required }
    }

    /**
     * Arms, or clears, the wizard's delayed start.
     *
     * Null removes the key rather than storing a sentinel, so "no delay pending" and "delay due at
     * epoch zero" cannot be confused.
     */
    suspend fun setFirstBackupStartAt(epochMillis: Long?, delayMillis: Long? = null) {
        context.dataStore.edit { prefs ->
            if (epochMillis == null) {
                prefs.remove(KEY_FIRST_BACKUP_START_AT)
                prefs.remove(KEY_FIRST_BACKUP_DELAY)
            } else {
                prefs[KEY_FIRST_BACKUP_START_AT] = epochMillis
                if (delayMillis != null) prefs[KEY_FIRST_BACKUP_DELAY] = delayMillis
            }
        }
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

    /**
     * Changes where new uploads go, app-wide. Whether [location] is actually choosable right now —
     * signed in, Pro unlocked — is the caller's job to check first; this just writes the choice.
     * Nothing already uploaded moves: see [BackupPreferences.backupLocation].
     */
    suspend fun setBackupLocation(location: BackupLocation) {
        context.dataStore.edit { it[KEY_BACKUP_LOCATION] = location.name }
    }

    suspend fun setKeepFreeAnswered() {
        context.dataStore.edit { it[KEY_KEEP_FREE_ANSWERED] = true }
    }

    /** Marks [names] as dealt with. See [BackupPreferences.acknowledgedAlbums]. */
    suspend fun acknowledgeAlbums(names: Collection<String>) {
        if (names.isEmpty()) return
        context.dataStore.edit { it[KEY_ACKNOWLEDGED_ALBUMS] = (it[KEY_ACKNOWLEDGED_ALBUMS] ?: emptySet()) + names }
    }

    /**
     * Records the start of the multi-cloud trial, once. Returns the start that stands — the one just
     * written, or the earlier one if a trial had already begun. Never overwrites: a second call must not
     * hand out a fresh 30 days.
     */
    suspend fun startMultiCloudTrial(nowEpochMillis: Long): Long {
        var standing = nowEpochMillis
        context.dataStore.edit { prefs ->
            val existing = prefs[KEY_MULTI_CLOUD_TRIAL_STARTED_AT]
            if (existing != null) standing = existing else prefs[KEY_MULTI_CLOUD_TRIAL_STARTED_AT] = nowEpochMillis
        }
        return standing
    }

    private companion object {
        val KEY_AUTOMATIC = booleanPreferencesKey("automatic_backup_enabled")
        val KEY_ALLOW_METERED = booleanPreferencesKey("allow_metered_network")
        // New key rather than reusing auto_optimise_enabled. That one meant "optimise photos
        // without asking", and this means "optimise at all" - a stored true would silently answer a
        // broader question than the user was asked, and now covers video as well.
        val KEY_OPTIMISE_ENABLED = booleanPreferencesKey("optimise_enabled")
        val KEY_OPTIMISE_PHOTOS = booleanPreferencesKey("optimise_photos")
        val KEY_OPTIMISE_VIDEO = booleanPreferencesKey("optimise_video")
        val KEY_PHOTO_OPTIMISE_MODE = stringPreferencesKey("photo_optimise_mode")
        val KEY_VIDEO_OPTIMISE_MODE = stringPreferencesKey("video_optimise_mode")
        val KEY_DESTINATION_ROOT = stringPreferencesKey("destination_root")
        val KEY_BACKUP_LOCATION = stringPreferencesKey("backup_location")
        val KEY_MULTI_CLOUD_TRIAL_STARTED_AT = longPreferencesKey("multi_cloud_trial_started_at")
        val KEY_ACKNOWLEDGED_ALBUMS = stringSetPreferencesKey("acknowledged_albums")
        val KEY_KEEP_FREE_ANSWERED = booleanPreferencesKey("keep_free_answered")
        val KEY_FIRST_BACKUP_HOUR = intPreferencesKey("first_backup_start_hour")
        val KEY_FIRST_BACKUP_CHARGING = booleanPreferencesKey("first_backup_requires_charging")
        val KEY_FIRST_BACKUP_START_AT = longPreferencesKey("first_backup_start_at")
        val KEY_FIRST_BACKUP_DELAY = longPreferencesKey("first_backup_delay_millis")
        val KEY_FIRST_BACKUP_DONE = booleanPreferencesKey("first_backup_completed")
        val KEY_CLOUD_DELETION_POLICY = stringPreferencesKey("cloud_deletion_policy")
        val KEY_DELETION_PROMPT_SEEN = longPreferencesKey("deletion_prompt_seen_up_to")
        val KEY_SHOW_EMPTY_FOLDERS = booleanPreferencesKey("show_empty_cloud_folders")
        val KEY_SETUP_COMPLETE = booleanPreferencesKey("setup_complete")
        val KEY_BACKFILL_CHECKED = booleanPreferencesKey("upgrade_backfill_checked")
        val KEY_PAUSED = booleanPreferencesKey("backup_paused")
        val KEY_INTERRUPTED_AT = longPreferencesKey("upload_interrupted_at")
        val KEY_RUN_BASELINE = longPreferencesKey("run_baseline_bytes")
        val KEY_VIDEO_QUALITY = stringPreferencesKey("video_quality")
        val KEY_LIBRARY_CHOICE = stringPreferencesKey("library_choice")
        val KEY_OPTIMISE_CUTOFF = longPreferencesKey("optimise_cutoff")
        val KEY_VIDEO_OPTIMISE_AGE = stringPreferencesKey("video_optimise_age")
        val KEY_WIZARD_STEP = intPreferencesKey("wizard_step")
        val KEY_WIZARD_BACKUP_TOTAL = intPreferencesKey("wizard_backup_total")
        val KEY_WIZARD_RUN_STARTED_AT = longPreferencesKey("wizard_run_started_at")
        val KEY_ALBUM_MERGE_WARNINGS = stringSetPreferencesKey("album_merge_warnings")
        val KEY_ARCHIVE_DEFAULT_AGE = stringPreferencesKey("archive_default_age")
        val KEY_ARCHIVE_NOTIFY_ENABLED = booleanPreferencesKey("archive_notify_enabled")
        val KEY_ARCHIVE_READY_LAST_SEEN = intPreferencesKey("archive_ready_last_seen_count")
    }
}
