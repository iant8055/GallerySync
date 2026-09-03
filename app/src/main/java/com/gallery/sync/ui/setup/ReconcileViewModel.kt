package com.gallery.sync.ui.setup

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gallery.sync.data.local.media.DiscoveredDirectory
import com.gallery.sync.data.local.media.MediaScanner
import com.gallery.sync.domain.backup.BackupEngine
import com.gallery.sync.domain.backup.CloudReconciliation
import com.gallery.sync.data.local.settings.BackupSettings
import android.net.Uri
import com.gallery.sync.data.local.media.GrantedDirectory
import com.gallery.sync.data.local.media.ScopedDirectories
import com.gallery.sync.util.ChargingState
import com.gallery.sync.domain.backup.ApplyLibraryChoice
import com.gallery.sync.domain.backup.FirstBackupHold
import com.gallery.sync.domain.backup.LibraryChoice
import com.gallery.sync.data.local.entity.AlbumMode
import com.gallery.sync.domain.backup.CloudDeletionPolicy
import com.gallery.sync.domain.backup.FirstBackupWindow
import com.gallery.sync.domain.backup.OptimiseMode
import com.gallery.sync.domain.backup.ReconcileWithCloud
import com.gallery.sync.domain.backup.RemoteRoots
import com.gallery.sync.domain.backup.VideoQuality
import com.gallery.sync.util.Logger
import com.gallery.sync.worker.BackupScheduling
import androidx.work.WorkManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalTime
import javax.inject.Inject

/**
 * What the reconciliation step is showing.
 *
 * [result] is populated while the check is still running, not only at the end: the check makes one
 * network request per album and there are ninety of them, so a screen that waits for the total reads
 * as a hang. The figures climb instead.
 */
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
    /** Albums changed by the last apply, for the confirmation line. Null before any apply. */
    val libraryApplied: Int? = null,
    val applyingLibraryChoice: Boolean = false,
    /** Setup topics already acknowledged. Survives a skip, and is never cleared. */
    val acknowledgedTopics: Set<String> = emptySet(),
    val hasCompletedSetup: Boolean = false,
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
    val defaultAlbumMode: AlbumMode = AlbumMode.DEFAULT,
    val isAutoOptimiseEnabled: Boolean = false,
    val optimiseVideo: Boolean = false,
    val videoQuality: VideoQuality = VideoQuality.DEFAULT,
    val cloudDeletionPolicy: CloudDeletionPolicy = CloudDeletionPolicy.DEFAULT,
    val backupCompleted: Int = 0,
    val backupTotal: Int = 0,
    val backupCurrentFile: String = "",
    val backupRunning: Boolean = false,
    val backupFinished: Boolean = false,
    /** Directories found by scanning MediaStore, before any grants. */
    val discoveredDirectories: List<DiscoveredDirectory> = emptyList(),
    /** Whether directory discovery is running. */
    val discoveryRunning: Boolean = false,
    /** Which directories the user has checked. Key = directory name, value = checked. */
    val directoryChecks: Map<String, Boolean> = emptyMap(),
    /** Persisted wizard step — non-zero means the wizard was interrupted and should resume here. */
    val wizardStep: Int = 0,
    /** Whether the user selected directories in the wizard (separate from SAF grants). */
    val hasSelectedDirectories: Boolean = false
) {
    /**
     * Whether Gate 1 has been answered.
     *
     * The reconciliation is hidden until it has. With nothing granted the scan returns nothing, and
     * a screen reporting zero outstanding files would announce that everything is already backed up
     * — which is false, and false in the direction that stops someone acting.
     */
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
    private val applyChoice: ApplyLibraryChoice,
    private val backupEngine: BackupEngine,
    private val scanner: MediaScanner
) : ViewModel() {

    private val workManager = WorkManager.getInstance(context)

    private val _state = MutableStateFlow(ReconcileUiState())
    val state: StateFlow<ReconcileUiState> = _state.asStateFlow()


    private var job: Job? = null

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
            // One shot, at construction. A fresh install has no grants at this moment, so it is not
            // backfilled — and when that user later grants a folder from inside the wizard, this
            // has long since run and cannot cut the tour short.
            if (!settings.hasSetupDecision() && sources.directories.first().isNotEmpty()) {
                settings.setSetupCompleted(true)
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
                    firstBackupStartHour = prefs.firstBackupStartHour,
                    firstBackupRequiresCharging = prefs.firstBackupRequiresCharging,
                    hasCompletedFirstBackup = prefs.hasCompletedFirstBackup,
                    acknowledgedTopics = prefs.acknowledgedTopics,
                    hasCompletedSetup = prefs.hasCompletedSetup,
                    settingsLoaded = true,
                    allowMeteredNetwork = prefs.allowMeteredNetwork,
                    defaultAlbumMode = prefs.defaultAlbumMode,
                    isAutoOptimiseEnabled = prefs.isOptimiseEnabled &&
                        prefs.photoOptimiseMode == OptimiseMode.Auto,
                    optimiseVideo = prefs.optimiseVideo,
                    videoQuality = prefs.videoQuality,
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

    /**
     * Records that a topic's explanation was acknowledged.
     *
     * Not consent to anything. Choosing Archive for an album still raises its own confirmation.
     */
    fun acknowledgeTopic(key: String) {
        viewModelScope.launch { settings.acknowledgeTopic(key) }
    }

    fun setAllowMeteredNetwork(allowed: Boolean) {
        viewModelScope.launch { settings.setAllowMeteredNetwork(allowed) }
    }

    fun setDefaultAlbumMode(mode: AlbumMode) {
        viewModelScope.launch { settings.setDefaultAlbumMode(mode) }
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

    /** Ends guided setup, whether it was completed or skipped. */
    fun completeSetup() {
        viewModelScope.launch { settings.setSetupCompleted(true) }
    }

    fun startBackupWorker() {
        viewModelScope.launch {
            settings.setWizardStep(TOTAL_STEPS)

            _state.value = _state.value.copy(backupRunning = true, backupCurrentFile = "")

            backupEngine.refreshLedger()
            val requeued = backupEngine.reconcileAndRequeue()
            Logger.i(TAG, "reconcileAndRequeue: $requeued files requeued")

            val grandTotal = backupEngine.outstandingCountAll()
            settings.setWizardBackupTotal(grandTotal)
            _state.value = _state.value.copy(backupTotal = grandTotal)
            Logger.i(TAG, "total pending: $grandTotal")

            if (grandTotal == 0) {
                _state.value = _state.value.copy(
                    backupRunning = false,
                    backupFinished = true
                )
                return@launch
            }

            val prefs = settings.current()
            BackupScheduling.enqueueManualRun(workManager, prefs.allowMeteredNetwork, allAlbums = true)
            observeBackupWorker(grandTotal)
        }
    }

    fun observeBackupWorker(knownTotal: Int = 0) {
        viewModelScope.launch {
            val savedTotal = settings.current().wizardBackupTotal
            val total = when {
                knownTotal > 0 -> knownTotal
                savedTotal > 0 -> savedTotal
                else -> backupEngine.outstandingCountAll()
            }

            _state.value = _state.value.copy(backupRunning = true, backupTotal = total)

            var highWater = _state.value.backupCompleted

            while (true) {
                val remaining = backupEngine.outstandingCountAll()
                val completed = (total - remaining).coerceAtLeast(0)

                if (completed > highWater) highWater = completed

                if (remaining == 0) {
                    _state.value = _state.value.copy(
                        backupRunning = false,
                        backupFinished = true,
                        backupTotal = total,
                        backupCompleted = total
                    )
                    return@launch
                }

                _state.value = _state.value.copy(
                    backupCompleted = highWater,
                    backupTotal = total
                )

                kotlinx.coroutines.delay(3000)
            }
        }
    }

    /** Selects a Gate 2 option without acting on it. Applying is a separate, deliberate tap. */
    fun setLibraryChoice(choice: LibraryChoice) {
        _state.value = _state.value.copy(libraryChoice = choice, libraryApplied = null)
    }

    /**
     * Applies the selected option to every in-scope album.
     *
     * The suspending call completes before the state is read — see the note on [addSource] for what
     * happens when it does not.
     */
    fun applyLibraryChoice() {
        viewModelScope.launch {
            _state.value = _state.value.copy(applyingLibraryChoice = true)
            val changed = applyChoice.apply(_state.value.libraryChoice)
            _state.value = _state.value.copy(
                applyingLibraryChoice = false,
                libraryApplied = changed
            )
        }
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

    /** Scans MediaStore for all media directories and pre-checks the obvious ones. */
    fun discoverDirectories() {
        viewModelScope.launch {
            _state.value = _state.value.copy(discoveryRunning = true)
            val dirs = scanner.discoverDirectories()

            // Pre-check heuristic: DCIM and Pictures always, plus anything with 50+ files
            val defaultChecked = setOf("DCIM", "Pictures")
            val checks = dirs.associate { dir ->
                dir.name to (dir.name in defaultChecked || dir.totalFiles >= 50)
            }

            _state.value = _state.value.copy(
                discoveredDirectories = dirs,
                directoryChecks = checks,
                discoveryRunning = false
            )
        }
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
        viewModelScope.launch {
            val selected = _state.value.directoryChecks
                .filter { (_, checked) -> checked }
                .keys
            sources.saveSelectedDirectories(selected)
        }
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
