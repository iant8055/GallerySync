package com.gallery.sync.ui.setup

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gallery.sync.data.local.media.DiscoveredDirectory
import com.gallery.sync.data.local.media.MediaScanner
import com.gallery.sync.domain.backup.BackupEngine
import com.gallery.sync.domain.backup.BackupLocation
import com.gallery.sync.domain.backup.SetFolderDestination
import com.gallery.sync.domain.backup.CloudReconciliation
import com.gallery.sync.data.local.settings.BackupSettings
import android.net.Uri
import android.provider.DocumentsContract
import com.gallery.sync.data.local.media.GrantedDirectory
import com.gallery.sync.data.local.media.ScopedDirectories
import com.gallery.sync.util.ChargingState
import com.gallery.sync.util.RecentsCard
import com.gallery.sync.domain.backup.FirstBackupHold
import com.gallery.sync.domain.backup.LibraryChoice
import com.gallery.sync.data.local.entity.AlbumMode
import com.gallery.sync.domain.backup.CloudDeletionPolicy
import com.gallery.sync.domain.backup.FirstBackupWindow
import com.gallery.sync.domain.backup.OptimiseMode
import com.gallery.sync.domain.backup.ReconcileWithCloud
import com.gallery.sync.domain.backup.RemoteRoots
import com.gallery.sync.domain.backup.TreeScope
import com.gallery.sync.domain.backup.VideoQuality
import com.gallery.sync.util.Logger
import com.gallery.sync.worker.BackupScheduling
import androidx.work.WorkManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalTime
import javax.inject.Inject

/**
 * A folder the wizard asked the picker for and did not get.
 *
 * Matters because the grant is not only the write path for optimising: whenever any folder is
 * granted, the scan covers granted folders only (`ScopedDirectories.currentScope`). Moto G, 15 Sept
 * 2026: DCIM and Pictures ticked, DCIM cancelled in the picker, Pictures granted — and DCIM's 247 files
 * left the backup with DCIM still ticked and nothing on screen. So a pick that does not cover the
 * folder asked for stops the walk and is put to the user, never absorbed.
 */
data class SafGrantIssue(
    /** The ticked folder the picker was opened for, e.g. `DCIM`. */
    val requested: String,
    val kind: Kind,
    /** The path the user picked instead, when there was one — e.g. `DCIM/Camera`. */
    val pickedPath: String? = null,
    /**
     * The picked tree, held back rather than granted. Only [Kind.NARROWER] carries it, and it is
     * taken only if the user chooses to keep the narrower folder.
     */
    val pickedUri: String? = null
) {
    enum class Kind {
        /** The picker was backed out of. */
        CANCELLED,

        /** A folder inside the one asked for — granting it would back up that part only. */
        NARROWER,

        /** A folder outside the one asked for — never granted, since it would widen the backup. */
        ELSEWHERE,

        /** A tree the app cannot use as a scope, such as a whole volume, or one it could not keep. */
        UNUSABLE
    }
}

/**
 * What the reconciliation step is showing.
 *
 * [result] is populated while the check is still running, not only at the end: the check makes one
 * network request per album and there are ninety of them, so a screen that waits for the total reads
 * as a hang. The figures climb instead.
 */
/** One cloud's share of the first backup: how many are sent of how many there are for it. */
data class CloudProgress(val location: BackupLocation, val done: Int, val total: Int)

/** The wizard step that watches the first backup. Persisted as the step while that backup is running. */
internal const val WIZARD_BACKUP_STEP = 9

data class ReconcileUiState(
    val running: Boolean = false,
    val result: CloudReconciliation? = null,
    /** Media permission was refused, so nothing can be counted at all. Not the same as zero. */
    val noMediaAccess: Boolean = false,
    /** Where new uploads go. Only the destination — old roots stay searchable. */
    val destinationRoot: String = RemoteRoots.DEFAULT_DESTINATION,
    /** True while the user is choosing a new destination. */
    val choosingDestination: Boolean = false,
    /** Set when a typed path was refused, so the dialog can say so rather than closing silently. */
    val destinationRejected: Boolean = false,
    val firstBackupStartHour: Int = FirstBackupWindow.DEFAULT_START_HOUR,
    val firstBackupRequiresCharging: Boolean = true,
    /** When the wizard's delayed first backup is due, or null when there is no delay pending. */
    val firstBackupStartAtEpochMillis: Long? = null,
    /** The chosen delay's full length, so the countdown ring has a denominator. */
    val firstBackupDelayMillis: Long? = null,
    /** Once true the window no longer applies and the section explains why it is gone. */
    val hasCompletedFirstBackup: Boolean = false,
    /** What is currently holding the first run, or null if nothing is. */
    val firstBackupHold: FirstBackupHold? = null,
    /** Gate 1. Until this has something in it, the engine has nothing correct to do. */
    val directories: List<GrantedDirectory> = emptyList(),
    /** Set when a picked tree could not be used, so the screen can say why. */
    val directoryRefused: Boolean = false,
    /** Gate 2, as currently selected. Not applied until the user says so. */
    val libraryChoice: LibraryChoice = LibraryChoice.BACK_UP_EVERYTHING,
    val hasCompletedSetup: Boolean = false,
    /** The persisted wizard step: [WIZARD_BACKUP_STEP] means the first backup was started. */
    /**
     * Whether stored preferences have been read at least once.
     *
     * Without it, [hasCompletedSetup] reads false for the first frame of every launch, and an
     * install that finished setup months ago would flash the wizard before settling. The wizard is
     * unmissable by design, which makes showing it wrongly worse than usual.
     */
    val settingsLoaded: Boolean = false,
    /**
     * Whether the upgrade backfill has finished deciding.
     *
     * Separate from [settingsLoaded] because they complete at different times, and the gap is
     * visible. Preferences load fast and report `hasCompletedSetup = false`; the backfill then reads
     * the grants and writes `true` a beat later. Gating only on the preferences therefore shows an
     * existing user the wizard for that beat — observed on the Moto G, 28 Aug 2026, and long enough
     * to be caught in a screenshot, which means long enough to be tapped.
     */
    val migrationChecked: Boolean = false,
    /** Whether the granted-directory list has emitted at least once. */
    val sourcesLoaded: Boolean = false,
    /** Defaults the wizard offers to set. Each is also reachable from Settings afterwards. */
    val allowMeteredNetwork: Boolean = false,
    val isAutoOptimiseEnabled: Boolean = false,
    val optimiseVideo: Boolean = false,
    val videoQuality: VideoQuality = VideoQuality.DEFAULT,
    val cloudDeletionPolicy: CloudDeletionPolicy = CloudDeletionPolicy.DEFAULT,
    val backupCompleted: Int = 0,
    val backupTotal: Int = 0,
    val backupCurrentFile: String = "",
    val backupRunning: Boolean = false,
    val backupFinished: Boolean = false,
    /** How the first backup splits across clouds, for the progress card. Empty until counted. */
    val cloudProgress: List<CloudProgress> = emptyList(),
    /** The cloud a file was most recently sent to, or the next one waiting: the one "Uploading" names. */
    val activeCloud: BackupLocation? = null,
    /** Directories found by scanning MediaStore, before any grants. */
    val discoveredDirectories: List<DiscoveredDirectory> = emptyList(),
    /** Whether directory discovery is running. */
    val discoveryRunning: Boolean = false,
    /** Which directories the user has checked. Key = directory name, value = checked. */
    val directoryChecks: Map<String, Boolean> = emptyMap(),
    /** The user's one free cloud, from the app-wide default destination. A folder with no answer goes there. */
    val mainCloud: BackupLocation = BackupLocation.DEFAULT,
    /**
     * Where each folder's backups go, as the wizard's own answer (TASK-026). Deliberately not read from
     * what is already stored: the wizard collects its own answers. A folder with no entry is OneDrive.
     */
    val folderDestinations: Map<String, BackupLocation> = emptyMap(),
    /** Persisted wizard step — non-zero means the wizard was interrupted and should resume here. */
    val wizardStep: Int = 0,
    /** Whether the user selected directories in the wizard (separate from SAF grants). */
    val hasSelectedDirectories: Boolean = false,
    /** Directories still needing SAF grants during the wizard walk. */
    val safGrantQueue: List<String> = emptyList(),
    /**
     * The picker came back without the folder that was asked for, and the walk is paused on it until
     * the user says what to do. Null while the walk is going normally. See [SafGrantIssue].
     */
    val safGrantIssue: SafGrantIssue? = null,
    /** Photos eligible for the wizard's one-time bulk optimise. */
    val optimiseCandidateCount: Int = 0,
    /** Whether the one-time optimise pass is running. */
    val optimiseRunning: Boolean = false,
    /** How many files were optimised in the one-time pass. */
    val optimisedCount: Int = 0,
    /** Bytes reclaimed by the one-time pass. */
    val optimisedBytes: Long = 0L,
    /** Whether the one-time pass finished. */
    val optimiseFinished: Boolean = false,
    /** Videos eligible for the wizard's one-time bulk optimise. */
    val videoCandidateCount: Int = 0,
    /** Whether video optimisation is running. */
    val videoOptimiseRunning: Boolean = false,
    /** How many videos were optimised. */
    val videoOptimisedCount: Int = 0,
    /** Bytes reclaimed by video optimisation. */
    val videoOptimisedBytes: Long = 0L,
    /** Whether video optimisation finished. */
    val videoOptimiseFinished: Boolean = false,
    /**
     * How far through the optimise phase that is currently running, and how many it has to do.
     *
     * One pair rather than two, because photos and video never run at once — the wizard's phase
     * says which of them these numbers describe. Zero total means no count is known yet, and the
     * screen shows the phase without a number rather than "0 of 0".
     */
    val optimiseProgressDone: Int = 0,
    val optimiseProgressTotal: Int = 0
) {
    /**
     * Whether Gate 1 has been answered.
     *
     * The reconciliation is hidden until it has. With nothing granted the scan returns nothing, and
     * a screen reporting zero outstanding files would announce that everything is already backed up
     * — which is false, and false in the direction that stops someone acting.
     */
    /**
     * The first backup has been started and has not finished. **The app is completely unavailable until it
     * has** — Ian, 24 Sept 2026: a core rule since the beginning. Derived from what is stored rather than from
     * the wizard having been left open, so nothing that marks setup complete early (a button, a crash, a
     * process restart) can let the user into a half-backed-up app.
     */
    val firstBackupPending: Boolean get() = wizardStep == WIZARD_BACKUP_STEP && !hasCompletedFirstBackup

    val hasSources: Boolean get() = directories.isNotEmpty() || hasSelectedDirectories || directoryChecks.values.any { it }

    /**
     * Whether enough is known to decide between the wizard and the app.
     *
     * Three independent async sources feed that decision — stored preferences, the upgrade
     * backfill, and the granted-directory list — and each one defaults to the value that means
     * "show the wizard". Gating on them one at a time produced the same flash three times over on
     * 28 Aug 2026: first the preferences, then the backfill, then the directories, each fixed in
     * turn while the next kept the bug alive. They are gathered here so a fourth input cannot
     * reintroduce it quietly.
     */
    val setupDecisionReady: Boolean get() = settingsLoaded && migrationChecked && sourcesLoaded
    /**
     * Whether changing the destination now would leave already-backed-up files behind.
     *
     * It would not — [RemoteRoots] keeps the old root searchable — and this exists so the dialog can
     * say so with a number instead of asking the user to take it on trust.
     */
    val alreadyFoundHere: Int get() = result?.backedUp?.files ?: 0
}

@HiltViewModel
class ReconcileViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val reconcile: ReconcileWithCloud,
    private val settings: BackupSettings,
    private val charging: ChargingState,
    private val sources: ScopedDirectories,
    private val backupEngine: BackupEngine,
    private val scanner: MediaScanner,
    private val proxyApplier: com.gallery.sync.data.local.media.ProxyApplier,
    private val videoOptimiser: com.gallery.sync.data.local.media.VideoOptimiser,
    private val entryDao: com.gallery.sync.data.local.dao.BackupEntryDao,
    private val recentsCard: RecentsCard,
    private val setFolderDestination: SetFolderDestination
) : ViewModel() {

    private val workManager = WorkManager.getInstance(context)

    private val _state = MutableStateFlow(ReconcileUiState())
    val state: StateFlow<ReconcileUiState> = _state.asStateFlow()


    private var job: Job? = null

    /**
     * The progress-polling loop, held so an abort can stop it.
     *
     * Cancelling the WorkManager chain is not enough on its own: this loop keeps reading the ledger
     * and writing counts back into the state, so an abort without it would reset the screen and then
     * watch the old numbers reappear a poll later.
     */
    private var backupObserverJob: Job? = null

    /** The optimise-progress loop, held so it can be replaced or stopped rather than stacked. */
    private var optimiseObserverJob: Job? = null

    init {
        viewModelScope.launch {
            // Backfill for installs that predate guided setup.
            //
            // `hasCompletedSetup` defaults false, so without this every existing user would be
            // dropped into the wizard on upgrade. An install holding a granted tree has already
            // answered Gate 1 by definition, and answering it is the whole reason the wizard is
            // unmissable.
            //
            // Keyed on whether a decision was ever *written*, not on its value. "Run setup again"
            // stores an explicit false, and an earlier version of this checked the value instead —
            // so reopening the app undid the request and returned the user to the tabs.
            //
            // Once per install, recorded on disk. It used to be "one shot, at construction",
            // which is only once until something reconstructs this — and a reinstall, a crash,
            // a force-stop or the system reclaiming memory all do.
            //
            // It also has to skip anyone mid-wizard. "Holds a granted tree" stopped being proof of
            // a pre-existing install once the wizard began taking grants at step 4: a user at
            // step 5 has grants and no setup decision and is indistinguishable from an upgrade.
            // On the Moto G, 3 Sept 2026, that user had their half-finished setup declared
            // complete and was dropped on the tabs with every album Off, never seeing the library
            // choice, the optimise step or the first backup.
            if (!settings.hasCheckedUpgradeBackfill()) {
                val looksLikeUpgrade = !settings.hasSetupDecision() &&
                    !settings.hasStartedWizard() &&
                    sources.directories.first().isNotEmpty()
                if (looksLikeUpgrade) {
                    settings.setSetupCompleted(true)
                }
                settings.markUpgradeBackfillChecked()
            }
            _state.value = _state.value.copy(migrationChecked = true)

            // Grants can be revoked outside the app. Checking once at start keeps the list from
            // claiming a folder is watched when nothing in it is readable any more.
            sources.forgetRevokedGrants()

            // Selected directories (from the wizard) also count as sources. Checked before
            // the first emission of sourcesLoaded so the setup decision has all facts at once
            // and the wizard does not flash for a frame.
            val hasSelected = sources.selectedDirectories.first().isNotEmpty()

            sources.directories.collect { dirs ->
                val changed = _state.value.directories.map { it.treeUri }.toSet() !=
                    dirs.map { it.treeUri }.toSet()
                _state.value = _state.value.copy(
                    directories = dirs,
                    sourcesLoaded = true,
                    hasSelectedDirectories = hasSelected
                )

                if (!changed) return@collect
                if (dirs.isEmpty()) {
                    _state.value = _state.value.copy(result = null, running = false)
                } else {
                    start()
                }
            }
        }
        viewModelScope.launch {
            settings.preferences.collect { prefs ->
                _state.value = _state.value.copy(
                    destinationRoot = prefs.destinationRoot,
                    mainCloud = prefs.backupLocation,
                    firstBackupStartHour = prefs.firstBackupStartHour,
                    firstBackupRequiresCharging = prefs.firstBackupRequiresCharging,
                    firstBackupStartAtEpochMillis = prefs.firstBackupStartAtEpochMillis,
                    firstBackupDelayMillis = prefs.firstBackupDelayMillis,
                    hasCompletedFirstBackup = prefs.hasCompletedFirstBackup,
                    hasCompletedSetup = prefs.hasCompletedSetup,
                    settingsLoaded = true,
                    allowMeteredNetwork = prefs.allowMeteredNetwork,
                    isAutoOptimiseEnabled = prefs.isOptimiseEnabled &&
                        prefs.photoOptimiseMode == OptimiseMode.Auto,
                    optimiseVideo = prefs.optimiseVideo,
                    videoQuality = prefs.videoQuality,
                    libraryChoice = prefs.libraryChoice,
                    cloudDeletionPolicy = prefs.cloudDeletionPolicy,
                    wizardStep = prefs.wizardStep,
                    firstBackupHold = if (prefs.hasCompletedFirstBackup) {
                        null
                    } else {
                        FirstBackupWindow.heldBecause(
                            hourOfDay = LocalTime.now().hour,
                            isCharging = charging.isCharging(),
                            startHour = prefs.firstBackupStartHour,
                            requiresCharging = prefs.firstBackupRequiresCharging
                        )
                    }
                )
            }
        }
    }

    fun setAllowMeteredNetwork(allowed: Boolean) {
        viewModelScope.launch { settings.setAllowMeteredNetwork(allowed) }
    }

    /**
     * The old single photo switch, expressed through the two settings that replaced it.
     *
     * Kept so existing callers keep working while the new Settings section is built. It sets the
     * master switch and puts photos in Auto, which is what this control used to mean - "optimise
     * photos without asking me each time".
     */
    fun setAutoOptimiseEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settings.setOptimiseEnabled(enabled)
            if (enabled) settings.setPhotoOptimiseMode(OptimiseMode.Auto)
        }
    }

    fun setOptimiseVideo(enabled: Boolean) {
        viewModelScope.launch { settings.setOptimiseVideo(enabled) }
    }

    fun setVideoQuality(quality: VideoQuality) {
        viewModelScope.launch { settings.setVideoQuality(quality) }
    }

    fun setCloudDeletionPolicy(policy: CloudDeletionPolicy) {
        viewModelScope.launch { settings.setCloudDeletionPolicy(policy) }
    }

    fun saveWizardStep(step: Int) {
        viewModelScope.launch { settings.setWizardStep(step) }
    }

    private var pendingProxyCandidates: List<com.gallery.sync.data.local.entity.BackupEntryEntity> = emptyList()

    /**
     * Builds a write request for the one-time bulk optimise, or returns null if the SAF grant
     * already covers the files (in which case it applies immediately).
     */
    suspend fun buildWizardProxyRequest(): android.content.IntentSender? {
        pendingProxyCandidates = proxyApplier.candidatesAll()
        if (pendingProxyCandidates.isEmpty()) {
            // Marked finished, not silently skipped. The video twin below always did this; the
            // photo half did not, and it did not matter while the card was the only thing that
            // started a pass. Once the worker could drain the candidates on its own, an empty list
            // here meant the card kept a stale count, showed "Optimising photos" with a "…" ring,
            // and never offered Finish — the user shut out of the app with no work left to do.
            // Moto G, 5 Sept 2026.
            _state.value = _state.value.copy(optimiseFinished = true)
            return null
        }

        if (!proxyApplier.needsWriteRequest(pendingProxyCandidates)) {
            Logger.i(TAG, "optimising ${pendingProxyCandidates.size} files through the tree grant")
            applyWizardProxies()
            return null
        }

        return proxyApplier.createWriteRequest(pendingProxyCandidates)
    }

    /**
     * Hands the photo pass to [OptimiseWorker] and watches the ledger for its progress.
     *
     * The work used to run here, in `viewModelScope`, which dies with the wizard — closing the app
     * mid-pass abandoned it with nothing to resume it. The consent dialog still has to come from the
     * activity, so the wizard asks and the worker acts; the grant is per-URI and persists, which is
     * what lets it carry on once this screen is gone.
     */
    fun applyWizardProxies() {
        _state.value = _state.value.copy(
            optimiseRunning = true
        )
        // Asks rather than appends: the upload chain now starts this pass by itself when a first
        // backup drains with nobody watching, so opening the app mid-optimise would otherwise queue
        // a second photo pass behind the one already running.
        viewModelScope.launch {
            val started = BackupScheduling.enqueueOptimiseIfAbsent(
                workManager,
                BackupScheduling.PHASE_PHOTOS
            )
            Logger.i(
                TAG,
                if (started) "photo optimise handed to the worker"
                else "photo optimise already under way"
            )
        }
        observeOptimise()
    }

    private var pendingVideoCandidates: List<com.gallery.sync.data.local.entity.BackupEntryEntity> = emptyList()

    /**
     * Builds a write request for the wizard's one-time video optimise, or returns null if the
     * SAF grant already covers the files (in which case it runs immediately).
     */
    suspend fun buildWizardVideoRequest(): android.content.IntentSender? {
        pendingVideoCandidates = videoOptimiser.wizardCandidates()
        if (pendingVideoCandidates.isEmpty()) {
            _state.value = _state.value.copy(videoOptimiseFinished = true)
            return null
        }

        if (!proxyApplier.needsWriteRequest(pendingVideoCandidates)) {
            Logger.i(TAG, "optimising ${pendingVideoCandidates.size} videos through the tree grant")
            applyWizardVideoOptimise()
            return null
        }

        return proxyApplier.createWriteRequest(pendingVideoCandidates)
    }

    /** The video pass, on the same footing as the photo one. See [applyWizardProxies]. */
    fun applyWizardVideoOptimise() {
        _state.value = _state.value.copy(
            videoOptimiseRunning = true
        )
        // Same reasoning as the photo pass: the worker chain may have queued this already.
        viewModelScope.launch {
            val started = BackupScheduling.enqueueOptimiseIfAbsent(
                workManager,
                BackupScheduling.PHASE_VIDEO
            )
            Logger.i(
                TAG,
                if (started) "video optimise handed to the worker"
                else "video optimise already under way"
            )
        }
        observeOptimise()
    }

    /**
     * Follows whichever pass is running by reading the ledger, the way the upload is followed.
     *
     * Counts describe the phase rather than the attempt — done plus still-eligible — so reopening
     * the wizard part way through cannot make the number fall backwards.
     */
    fun observeOptimise() {
        optimiseObserverJob?.cancel()
        optimiseObserverJob = viewModelScope.launch {
            while (true) {
                // Which phase is live is read from the ledger, not from flags this class sets.
                //
                // This used to open with `if (!videoOptimiseRunning && !optimiseRunning) return`,
                // which only ever followed a pass the card had started itself. The worker chain now
                // starts both passes when an unattended first backup drains, so those flags are
                // false, the observer returned on its first line, and the wizard sat on "Optimising
                // photos" with every candidate already done.
                val photoRemaining = proxyApplier.candidatesAll().size
                val videoRemaining = videoOptimiser.wizardCandidates().size
                val onVideo = photoRemaining == 0
                val remaining = if (onVideo) videoRemaining else photoRemaining
                val done = entryDao.countProxied(video = onVideo)

                _state.value = _state.value.copy(
                    optimiseCandidateCount = photoRemaining,
                    videoCandidateCount = videoRemaining,
                    optimiseRunning = photoRemaining > 0,
                    videoOptimiseRunning = onVideo && videoRemaining > 0,
                    optimiseProgressDone = done,
                    optimiseProgressTotal = done + remaining
                )

                if (photoRemaining == 0 && videoRemaining == 0) {
                    _state.value = _state.value.copy(
                        optimiseRunning = false,
                        optimiseFinished = true,
                        optimisedCount = entryDao.countProxied(video = false),
                        videoOptimiseRunning = false,
                        videoOptimiseFinished = true,
                        videoOptimisedCount = entryDao.countProxied(video = true)
                    )
                    Logger.i(TAG, "optimising finished: nothing eligible remains")
                    return@launch
                }

                delay(1500)
            }
        }
    }

    /** Ends guided setup, whether it was completed or skipped. */
    fun completeSetup() {
        viewModelScope.launch { settings.setSetupCompleted(true) }
    }

    /**
     * Finishing the wizard after its backup has run to the end. Records the first backup as done in the same
     * breath, so the app is never held on the progress card by the rule that it stays unavailable until that
     * backup has finished (see [ReconcileUiState.firstBackupPending]) a moment before the worker noted it.
     */
    fun completeSetupAfterBackup() {
        viewModelScope.launch {
            settings.markFirstBackupComplete()
            settings.setSetupCompleted(true)
        }
    }

    /**
     * Commits the delay card's choice when the user presses Next, then calls [then] to advance.
     *
     * [minutes] null is "Right now"; otherwise the delayed start is armed [minutes] from **this
     * moment** to the minute. Until 15 Sept 2026 the due time was written the instant a chip was
     * tapped, so time spent reading the card came off the delay — Ian saw a 3-minute choice already
     * at 2:42 when the countdown appeared, and the arms logged 161.8 s, 173.3 s and 176.9 s of 180.
     *
     * **Written before advancing, and the order is the point.** The countdown card decides what to do
     * from the stored due time the moment it appears: find none and it starts the backup at once. So
     * the write completes, and state carries the new due time, before [then] moves the wizard on.
     *
     * Deliberately not routed through `setFirstBackupStartHour`: that stores an hour of day, so a
     * delay chosen at 13:25 would land on 14:00 and be 35 minutes rather than the hour asked for.
     */
    fun commitFirstBackupDelay(minutes: Int?, then: () -> Unit) {
        viewModelScope.launch {
            if (minutes == null) {
                settings.setFirstBackupStartAt(null)
                // Cancels a chain armed by an earlier visit to the countdown card. Without this,
                // coming back and choosing "Right now" would leave the old delayed run queued, and
                // it would fire later on its own.
                BackupScheduling.cancelManualRun(workManager)
                _state.value = _state.value.copy(firstBackupStartAtEpochMillis = null)
                then()
                return@launch
            }
            // Never over the top of a run already moving bytes. Arming re-enqueues the manual chain
            // with REPLACE, so without this a delay chosen after the upload began would cancel it —
            // which is exactly what happened on 4 Sept 2026, stopping a live run at 9 of 155.
            //
            // Withdrawing Back from the progress card closes the route that reached this; the guard
            // stays because a second route would be silent, and what it costs is nothing.
            val current = _state.value
            if (current.backupRunning || current.backupCompleted > 0 || current.backupFinished) {
                Logger.w(TAG, "ignoring delay request: backup already under way")
                then()
                return@launch
            }
            val delayMillis = minutes * 60L * 1000L
            val startAt = System.currentTimeMillis() + delayMillis
            settings.setFirstBackupStartAt(epochMillis = startAt, delayMillis = delayMillis)
            _state.value = _state.value.copy(
                firstBackupStartAtEpochMillis = startAt,
                firstBackupDelayMillis = delayMillis
            )
            then()
        }
    }

    /**
     * Hands the pending delay to WorkManager, so it fires with the app closed or killed.
     *
     * Called when the wizard reaches the progress card with a delay still outstanding, and again on
     * every return to it — the delay is recomputed from the stored due time each time, so a process
     * restart re-arms the correct remainder rather than starting the clock over.
     *
     * The ledger prep happens here rather than at expiry because the countdown card needs the total
     * to have something to say, and because the work has to be queued before the app goes away.
     */
    fun scheduleDelayedBackup() {
        viewModelScope.launch {
            val prefs = settings.current()
            val startAt = prefs.firstBackupStartAtEpochMillis ?: return@launch
            val remaining = startAt - System.currentTimeMillis()
            if (remaining <= 0L) return@launch

            // Armed first, prepared second, and the order is the whole fix.
            //
            // The ledger refresh and cloud reconcile below are seconds of network per album, and
            // they used to sit in front of this call inside `viewModelScope` — which Close clears.
            // Moto G, 5 Sept 2026: a 3-minute delay chosen and then Closed a second later left
            // `first_backup_start_at` written, a countdown drawn on the card when the app reopened,
            // and nothing at all in WorkManager. The failure was silent in both directions, because
            // everything the user could see said it was armed.
            //
            // The enqueue is the promise. The totals underneath it are what the card says while it
            // waits, and losing those to a Close costs a number on a screen rather than the backup.
            BackupScheduling.enqueueDelayedManualRun(
                workManager = workManager,
                allowMeteredNetwork = prefs.allowMeteredNetwork,
                delayMillis = remaining,
                allAlbums = true
            )
            Logger.i(TAG, "delayed first backup armed: ${remaining}ms")

            settings.setWizardStep(TOTAL_STEPS)
            backupEngine.refreshLedger()
            backupEngine.reconcileAndRequeue()
            val grandTotal = backupEngine.outstandingCountAll()
            // Re-entered on every return to the countdown, possibly after a process restart that
            // has not re-run the cloud check yet. Without a check to count from, keep what the
            // first arm recorded rather than falling back to the whole ledger.
            val saved = settings.current()
            val sendTotal = if (_state.value.result?.isComplete == true || saved.wizardRunStartedAt == 0L) {
                sendTotal(grandTotal)
            } else {
                saved.wizardBackupTotal
            }
            val startedAt = saved.wizardRunStartedAt.takeIf { it > 0L } ?: System.currentTimeMillis()
            settings.setWizardRun(sendTotal, startedAt)
            _state.value = _state.value.copy(backupTotal = sendTotal)
            Logger.i(TAG, "delayed first backup: $grandTotal pending, $sendTotal to send")
        }
    }

    /**
     * The progress card's denominator: how many files this run will actually send.
     *
     * Not the ledger's pending count. On a fresh install every file is pending until the run checks
     * it against OneDrive, so that count is the whole library — the card read "Uploading 133 of 256"
     * with two files sent, and Ian took it for the app uploading too much (Moto G, 15 Sept 2026). The
     * cloud check has already answered the real question, and on a complete check its outstanding
     * figure is what the run will send. An incomplete check falls back to the ledger: a floor
     * presented as a total is the mistake [CloudReconciliation.isComplete] exists to prevent.
     */
    /**
     * The card's total. The cloud check discounts what OneDrive already holds, but it knows nothing of
     * any other cloud, so files bound elsewhere are added back in full. Without this the ring read
     * "84 of 84", 100%, while 2,168 files were still to go (Moto G, 24 Sept 2026) and Finish never came.
     */
    private suspend fun sendTotal(grandTotal: Int): Int {
        val elsewhere = backupEngine.outstandingCountElsewhere().coerceAtMost(grandTotal)
        return filesToSend(grandTotal - elsewhere) + elsewhere
    }

    private fun filesToSend(pendingInLedger: Int): Int {
        val checked = _state.value.result?.takeIf { it.isComplete } ?: return pendingInLedger
        return checked.outstanding.files.coerceAtMost(pendingInLedger)
    }

    /**
     * The countdown has run out. Normally the work is already queued, so this starts watching —
     * enqueueing again would replace a chain that may already be uploading.
     *
     * **The due time is not cleared here.** It stays until [observeBackupWorker] sees the backup
     * actually begin, because reaching zero is not starting: the job then waits for the charger, and
     * for up to half an hour of Android's batching on top. The card reads the stored due time to
     * know it is still waiting. Clearing it at zero is what made the card announce "Your backup is
     * running" on a phone that was waiting to be plugged in — Moto G, 15 Sept 2026.
     *
     * The exception is a chain that never got armed. A due time and a queued run are written by two
     * different systems, and only one of them survives the activity going away mid-arm, so this asks
     * WorkManager rather than assuming. Without the check, a lost arm shows as a card watching an
     * upload that will never begin — which is exactly how the 5 Sept defect presented.
     */
    fun onDelayElapsed() {
        viewModelScope.launch {
            if (!BackupScheduling.manualRunLive(workManager)) {
                Logger.w(TAG, "delay elapsed with no chain queued; starting the run now")
                val prefs = settings.current()
                // Still the delayed start, so it still waits for the charger - the countdown card
                // says it will. See BackupScheduling.enqueueDelayedManualRun.
                BackupScheduling.enqueueManualRun(
                    workManager,
                    prefs.allowMeteredNetwork,
                    allAlbums = true,
                    requiresCharging = true
                )
            }

            observeBackupWorker()
        }
    }

    fun startBackupNow() {
        viewModelScope.launch {
            settings.setFirstBackupStartAt(null)
            startBackupWorker()
        }
    }

    /**
     * Stops the run and puts the wizard back where settings can be changed.
     *
     * Deliberate, and reached only through a confirmation — the accidental version of this is the
     * defect it grew out of, where Back re-armed a delay and silently cancelled a live upload.
     *
     * **Nothing uploaded is undone.** Files already in OneDrive stay there and the ledger keeps
     * saying so, so restarting resumes rather than re-sending. Photos already replaced by proxies
     * stay proxied: their originals are in the cloud, which is the same guarantee they had a moment
     * earlier. Aborting stops work, it does not reverse it, and it never removes anything.
     */
    fun abortBackup() {
        backupObserverJob?.cancel()
        backupObserverJob = null
        optimiseObserverJob?.cancel()
        optimiseObserverJob = null
        viewModelScope.launch {
            BackupScheduling.cancelManualRun(workManager)
            // Stops the optimise chain as well. Files already proxied stay proxied — their
            // originals are in OneDrive, which is the guarantee they had a moment earlier — so this
            // halts remaining work rather than undoing finished work.
            BackupScheduling.cancelOptimise(workManager)
            settings.setFirstBackupStartAt(null)
            settings.setWizardRun(total = 0, startedAt = 0L)
            settings.setWizardStep(TOTAL_STEPS - 1)
            _state.value = _state.value.copy(
                backupRunning = false,
                backupFinished = false,
                backupCompleted = 0,
                backupTotal = 0,
                backupCurrentFile = "",
                optimiseRunning = false,
                optimiseFinished = false,
                optimiseCandidateCount = 0,
                optimiseProgressDone = 0,
                optimiseProgressTotal = 0,
                videoOptimiseRunning = false,
                videoOptimiseFinished = false,
                videoCandidateCount = 0
            )
            Logger.i(TAG, "backup aborted by user; wizard returned to settings")
        }
    }

    fun startBackupWorker() {
        viewModelScope.launch {
            // Taken before anything can upload: the card counts files sent since this moment.
            val runStartedAt = System.currentTimeMillis()
            settings.setWizardStep(TOTAL_STEPS)

            _state.value = _state.value.copy(backupRunning = true, backupCurrentFile = "")

            backupEngine.refreshLedger()
            val requeued = backupEngine.reconcileAndRequeue()
            Logger.i(TAG, "reconcileAndRequeue: $requeued files requeued")

            // Whether to run at all still follows the ledger — only the card's count changes.
            val grandTotal = backupEngine.outstandingCountAll()
            val sendTotal = sendTotal(grandTotal)
            settings.setWizardRun(sendTotal, runStartedAt)
            _state.value = _state.value.copy(backupTotal = sendTotal)
            Logger.i(TAG, "total pending: $grandTotal, to send: $sendTotal")

            if (grandTotal == 0) {
                _state.value = _state.value.copy(
                    backupRunning = false,
                    backupFinished = true
                )
                return@launch
            }

            val prefs = settings.current()
            BackupScheduling.enqueueManualRun(workManager, prefs.allowMeteredNetwork, allAlbums = true)
            observeBackupWorker(sendTotal)
        }
    }

    fun observeBackupWorker(knownTotal: Int? = null) {
        backupObserverJob?.cancel()
        backupObserverJob = viewModelScope.launch {
            val saved = settings.current()
            val runStartedAt = saved.wizardRunStartedAt
            // A recorded run carries its own total, zero included: a library already wholly in
            // OneDrive sends nothing, and must not fall back to counting the ledger.
            var total = when {
                knownTotal != null -> knownTotal
                runStartedAt > 0L || saved.wizardBackupTotal > 0 -> saved.wizardBackupTotal
                else -> backupEngine.outstandingCountAll()
            }

            _state.value = _state.value.copy(backupRunning = true, backupTotal = total)

            var highWater = _state.value.backupCompleted
            var lastDoneByCloud = emptyMap<BackupLocation, Int>()
            var activeCloud: BackupLocation? = null
            val pendingAtStart = backupEngine.outstandingCountAll()

            while (true) {
                val remaining = backupEngine.outstandingCountAll()
                // Files actually sent since the run began. A file found already in OneDrive keeps
                // its OneDrive arrival date, so it is not counted — it used to be, because done was
                // "total minus still pending" and a skip leaves the pending count too. Falls back
                // to that for a run recorded before the start time was kept.
                val completed = if (runStartedAt > 0L) {
                    backupEngine.uploadedSince(runStartedAt).coerceAtMost(total)
                } else {
                    (total - remaining).coerceAtLeast(0)
                }

                if (completed > highWater) highWater = completed

                // The ring must never read 100% while files are still queued. The total is an estimate made
                // before the run (what the cloud check says is missing), and it can be short — it was for
                // files bound for a cloud the check knows nothing about, the Moto G showing "84 of 84"
                // with 2,168 to go. When it is exceeded, grow it to what is actually known.
                if (completed >= total && remaining > 0) {
                    total = completed + remaining
                    if (runStartedAt > 0L) settings.setWizardRun(total, runStartedAt)
                }

                // Per cloud, for the card: done since the run began, and what is still waiting.
                val doneByCloud = backupEngine.uploadedSinceByCloud(runStartedAt)
                val waitingByCloud = backupEngine.pendingByCloud()
                val clouds = (doneByCloud.keys + waitingByCloud.keys).sortedBy { it.ordinal }.map {
                    val done = doneByCloud[it] ?: 0
                    CloudProgress(it, done, done + (waitingByCloud[it] ?: 0))
                }.filter { it.total > 0 }
                // "Uploading" names the cloud a file has just gone to; between files it keeps the last
                // one, and when that cloud has nothing left it moves to the next with work waiting.
                val advanced = clouds
                    .maxByOrNull { (doneByCloud[it.location] ?: 0) - (lastDoneByCloud[it.location] ?: 0) }
                    ?.takeIf { (doneByCloud[it.location] ?: 0) > (lastDoneByCloud[it.location] ?: 0) }
                activeCloud = when {
                    advanced != null -> advanced.location
                    activeCloud != null && (waitingByCloud[activeCloud] ?: 0) > 0 -> activeCloud
                    else -> clouds.firstOrNull { (waitingByCloud[it.location] ?: 0) > 0 }?.location
                }
                lastDoneByCloud = doneByCloud

                // A delayed start is over once the backup has visibly begun: a batch executing, a
                // file landed, or the ledger's pending count moving (a batch of skips can finish
                // between two polls and send nothing). Until then the due time stays stored and
                // the card keeps saying it is waiting. See onDelayElapsed.
                if (_state.value.firstBackupStartAtEpochMillis != null &&
                    (remaining == 0 || highWater > 0 || remaining < pendingAtStart ||
                        BackupScheduling.manualRunExecuting(workManager))
                ) {
                    settings.setFirstBackupStartAt(null)
                }

                if (remaining == 0) {
                    val shouldOptimise = _state.value.libraryChoice.optimisesAtInstall
                    val photoCandidates = if (shouldOptimise) {
                        proxyApplier.candidatesAll()
                    } else emptyList()
                    val videoCandidates = if (shouldOptimise) {
                        videoOptimiser.wizardCandidates()
                    } else emptyList()
                    val videoCount = videoCandidates.size
                    _state.value = _state.value.copy(
                        backupRunning = false,
                        backupFinished = true,
                        backupTotal = total,
                        backupCompleted = total,
                        optimiseCandidateCount = photoCandidates.size,
                        videoCandidateCount = videoCount
                    )

                    // Hand over to the optimise observer rather than stopping here. Reopening the
                    // wizard mid-pass used to leave the count frozen — "10 of 128" for minutes on
                    // end while the worker was demonstrably transcoding — because nothing was
                    // watching a pass this screen had not started. It self-terminates when nothing
                    // is eligible, so calling it when there is no work to do costs one poll.
                    if (shouldOptimise) observeOptimise()

                    return@launch
                }

                _state.value = _state.value.copy(
                    backupCompleted = highWater,
                    backupTotal = total,
                    cloudProgress = clouds,
                    activeCloud = activeCloud
                )

                kotlinx.coroutines.delay(3000)
            }
        }
    }

    /**
     * Hides or restores the app's Recents card, following the wizard's own state.
     *
     * Driven from one derived condition rather than sprinkled through the paths that start and stop
     * work, because the failure that matters is a flag left set: an app missing from Recents with
     * nothing running to explain it is worse than the swipe it was protecting against. Entering the
     * backup phase hides it, finishing or aborting restores it, and every launch restores it before
     * this is consulted again.
     *
     * See [RecentsCard] for why the card is worth removing at all.
     */
    fun setRecentsCardHidden(hidden: Boolean) {
        if (hidden) recentsCard.hide() else recentsCard.show()
    }

    /** Selects a Gate 2 option without acting on it. Applying is a separate, deliberate tap. */
    fun setLibraryChoice(choice: LibraryChoice) {
        // Written through, not just held. Closing the wizard mid-backup ends the process, and this
        // is what step 9 reads to decide whether anything gets optimised when the upload finishes.
        //
        // The cutoff goes with it, and until 6 Sept 2026 nothing wrote one. Its only caller was a
        // bulk applier reachable from two screens that nothing rendered, so in the app users
        // actually meet the cutoff stayed at `EVERYTHING` for every choice. That is why #3 behaved
        // exactly like #2: it is the cutoff, and nothing else, that tells them apart. Those screens
        // and that applier were deleted in TASK-022, so this write is now the only source.
        //
        // Recorded now rather than when the run starts, because nothing uploads between answering
        // Gate 2 and the first batch, and answering again with a different option must produce the
        // new cutoff rather than leave the old one standing.
        viewModelScope.launch {
            settings.setLibraryChoice(choice)
            settings.setOptimiseCutoff(choice.cutoffFor(System.currentTimeMillis()))
        }
        _state.value = _state.value.copy(libraryChoice = choice)
    }

    /** Records a folder the user picked. The re-check follows from the grant list changing. */
    fun addSource(treeUri: Uri) {
        viewModelScope.launch {
            // The suspending call must complete *before* the state read, not inside it. Written as
            // `_state.value = _state.value.copy(refused = !sources.add(uri))`, Kotlin evaluates the
            // `.copy` receiver first, suspends in `add()` while the directories collector writes the
            // new folder into state, then applies `.copy` to the stale snapshot and puts it back —
            // silently undoing the grant on screen while the data layer was perfectly correct.
            // Observed on hardware 25 Aug 2026: the folder appeared only after a restart.
            val added = sources.add(treeUri)
            _state.value = _state.value.copy(directoryRefused = !added)
        }
    }

    /**
     * Scans MediaStore for all media directories. Nothing is pre-checked.
     *
     * Run every time the folder card opens, not once. It used to run only while the list was empty,
     * so the counts were whatever they had been on the first visit — Ian added eight screenshots,
     * came back to the card, and it still said two (Moto G, 15 Sept 2026). A folder's tick survives
     * the recount; only a folder never seen before arrives unticked.
     */
    fun discoverDirectories() {
        viewModelScope.launch {
            _state.value = _state.value.copy(discoveryRunning = true)
            val dirs = scanner.discoverDirectories()
            val previous = _state.value.directoryChecks

            // Every folder starts off. Ian, 4 Sept 2026.
            //
            // The old heuristic ticked DCIM and Pictures always, plus anything with 50+ files. On
            // the Moto G that is 17.3 GB across 36 albums — and almost all of it is Pictures, which
            // holds 15 GB to DCIM's 2.3 GB — so the wizard queued the entire library on a default
            // nobody chose. Which folders leave the phone is the user's decision, and a checkbox
            // that arrives already ticked is not one they made.
            //
            // Safe to start empty because `canAdvance()` blocks step 4 until at least one folder is
            // checked, so this asks for a choice rather than silently backing up nothing.
            val checks = dirs.associate { dir -> dir.name to (previous[dir.name] ?: false) }

            _state.value = _state.value.copy(
                discoveredDirectories = dirs,
                directoryChecks = checks,
                discoveryRunning = false
            )
        }
    }

    private var saveJob: Job? = null

    /**
     * Runs the cloud check once the folder choice has actually been written. Sign-in now comes *before*
     * the folders, so the check that fires at sign-in ran against nothing; this is the run that counts.
     */
    fun startWhenFoldersSaved() {
        viewModelScope.launch {
            saveJob?.join()
            start()
        }
    }

    /** Records which cloud a folder goes to. Held here until Next; see [saveSelectedDirectories]. */
    fun setFolderDestinationChoice(name: String, location: BackupLocation) {
        _state.value = _state.value.copy(
            folderDestinations = _state.value.folderDestinations + (name to location)
        )
    }

    /** Toggles a directory's checked state. */
    fun toggleDirectoryCheck(name: String) {
        val current = _state.value.directoryChecks.toMutableMap()
        current[name] = !(current[name] ?: false)
        _state.value = _state.value.copy(directoryChecks = current)
    }

    /**
     * Saves the user's checked directories and marks Gate 1 as answered.
     *
     * No SAF picker needed — the runtime media permission already grants read access to all
     * photos and videos. The selected directory names scope the scan via [TreeScope.isInScope].
     */
    fun saveSelectedDirectories() {
        saveJob = viewModelScope.launch {
            val selected = _state.value.directoryChecks
                .filter { (_, checked) -> checked }
                .keys
            sources.saveSelectedDirectories(selected)
            // The wizard's per-folder cloud answers, for the folders actually chosen. Every one is
            // written, OneDrive included: a folder the user left on OneDrive is an answer too, and
            // SetFolderDestination also re-points rows the first scan has already queued.
            setFolderDestination(
                selected.associateWith { _state.value.folderDestinations[it] ?: _state.value.mainCloud }
            )
        }
    }

    /**
     * Builds the queue of directories that need SAF grants for write access.
     *
     * Returns true if there are directories to walk. False means all checked directories are
     * already covered by existing grants (or none were checked).
     */
    fun buildSafGrantQueue(): Boolean {
        val checked = _state.value.directoryChecks.filter { it.value }.keys
        val covered = _state.value.directories.map { it.relativePath }
        // Covered means granted on that folder or a parent of it. This used a bare `startsWith` both
        // ways, so a held grant on `DCIM/Camera` counted as covering all of `DCIM` — the picker was
        // never shown, and the scan (which follows grants) kept only Camera. `DCIM2` would have
        // matched `DCIM` too. TreeScope has the boundary check for exactly this.
        val needed = checked.filterNot { dir -> TreeScope.isInScope(dir, covered) }
        _state.value = _state.value.copy(safGrantQueue = needed.toList(), safGrantIssue = null)
        return needed.isNotEmpty()
    }

    /**
     * Processes one SAF grant result.
     *
     * The walk advances only when the pick covers the folder asked for — that folder itself, or a
     * parent of it. Anything else pauses the walk on a [SafGrantIssue] for the user to decide,
     * because the grant sets what is backed up, not only what can be optimised. Until 15 Sept 2026
     * this advanced on every result: a cancel skipped the folder without a word, and a subfolder or an
     * unrelated folder was granted as though it were the one asked for.
     */
    fun onSafGrantReceived(uri: Uri?) {
        viewModelScope.launch {
            val requested = _state.value.safGrantQueue.firstOrNull() ?: return@launch
            if (uri == null) {
                pauseWalk(SafGrantIssue(requested, SafGrantIssue.Kind.CANCELLED))
                return@launch
            }

            val picked = runCatching {
                TreeScope.pathFromTreeDocumentId(DocumentsContract.getTreeDocumentId(uri))
            }.getOrNull()

            when {
                picked == null ->
                    pauseWalk(SafGrantIssue(requested, SafGrantIssue.Kind.UNUSABLE))

                // The folder asked for, or a parent that contains it.
                TreeScope.isInScope(requested, listOf(picked)) -> {
                    if (sources.add(uri)) {
                        Logger.i(TAG, "grant for $requested: $picked")
                        advanceWalk()
                    } else {
                        pauseWalk(SafGrantIssue(requested, SafGrantIssue.Kind.UNUSABLE, picked))
                    }
                }

                // Inside the folder asked for. Not granted yet: keeping it narrows the backup to
                // that part, and the user decides that, not the picker.
                TreeScope.isInScope(picked, listOf(requested)) ->
                    pauseWalk(
                        SafGrantIssue(
                            requested,
                            SafGrantIssue.Kind.NARROWER,
                            pickedPath = picked,
                            pickedUri = uri.toString()
                        )
                    )

                // Somewhere else entirely. Never granted: it would add a folder to the backup
                // that the user did not tick.
                else ->
                    pauseWalk(SafGrantIssue(requested, SafGrantIssue.Kind.ELSEWHERE, picked))
            }
        }
    }

    /** Opens the picker again for the same folder. The walk relaunches when the issue clears. */
    fun retrySafGrant() {
        _state.value = _state.value.copy(safGrantIssue = null)
    }

    /**
     * Leaves the folder out: unticked, so the choice on screen and the backup agree, and the walk
     * moves on. Said plainly on the card before the tap — this is the user taking a folder out of the
     * backup, not a formality.
     */
    fun skipSafGrant() {
        val issue = _state.value.safGrantIssue ?: return
        val checks = _state.value.directoryChecks.toMutableMap()
        checks[issue.requested] = false
        _state.value = _state.value.copy(directoryChecks = checks)
        Logger.i(TAG, "grant for ${issue.requested} skipped; unticked")
        saveSelectedDirectories()
        advanceWalk()
    }

    /** Takes the narrower folder the user picked, after they have been told what it leaves out. */
    fun keepNarrowerGrant() {
        val issue = _state.value.safGrantIssue ?: return
        val uri = issue.pickedUri?.let(Uri::parse) ?: return
        viewModelScope.launch {
            if (sources.add(uri)) {
                Logger.i(TAG, "grant for ${issue.requested}: kept narrower ${issue.pickedPath}")
                advanceWalk()
            } else {
                pauseWalk(issue.copy(kind = SafGrantIssue.Kind.UNUSABLE, pickedUri = null))
            }
        }
    }

    private fun pauseWalk(issue: SafGrantIssue) {
        Logger.w(TAG, "grant for ${issue.requested}: ${issue.kind} (${issue.pickedPath})")
        _state.value = _state.value.copy(safGrantIssue = issue)
    }

    private fun advanceWalk() {
        val queue = _state.value.safGrantQueue
        _state.value = _state.value.copy(
            safGrantQueue = if (queue.size > 1) queue.drop(1) else emptyList(),
            safGrantIssue = null
        )
    }

    fun removeSource(treeUri: String) {
        viewModelScope.launch { sources.remove(treeUri) }
    }

    fun setFirstBackupStartHour(hour: Int) {
        viewModelScope.launch { settings.setFirstBackupStartHour(hour) }
    }

    fun setFirstBackupRequiresCharging(required: Boolean) {
        viewModelScope.launch { settings.setFirstBackupRequiresCharging(required) }
    }

    fun openDestinationChooser() {
        _state.value = _state.value.copy(choosingDestination = true, destinationRejected = false)
    }

    fun dismissDestinationChooser() {
        _state.value = _state.value.copy(choosingDestination = false, destinationRejected = false)
    }

    /**
     * Changes where new uploads go.
     *
     * Does **not** re-run the check afterwards. The figures on screen stay true, because the old
     * root remains in the search set — that is the whole point of separating destination from
     * search, and re-running would spend ninety requests to print the same numbers.
     */
    fun setDestination(path: String) {
        viewModelScope.launch {
            if (settings.setDestinationRoot(path)) {
                _state.value = _state.value.copy(
                    choosingDestination = false,
                    destinationRejected = false
                )
            } else {
                _state.value = _state.value.copy(destinationRejected = true)
            }
        }
    }

    /**
     * Starts, or restarts, the check.
     *
     * Cancels any run already in flight rather than letting two overlap — each one issues a request
     * per album, and a user tapping "check again" twice should not double the traffic.
     */
    fun start() {
        job?.cancel()
        job = viewModelScope.launch {
            _state.value = _state.value.copy(running = true, result = null)

            val total = reconcile.run { partial ->
                _state.value = _state.value.copy(result = partial)
            }

            _state.value = _state.value.copy(
                running = false,
                result = total,
                noMediaAccess = total == null
            )
        }
    }

    override fun onCleared() {
        job?.cancel()
        super.onCleared()
    }

    private companion object {
        const val TAG = "ReconcileVM"
        const val TOTAL_STEPS = 9
    }
}
