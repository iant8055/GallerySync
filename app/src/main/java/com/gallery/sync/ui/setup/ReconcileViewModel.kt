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
import kotlinx.coroutines.delay
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
    val hasSelectedDirectories: Boolean = false,
    /** Directories still needing SAF grants during the wizard walk. */
    val safGrantQueue: List<String> = emptyList(),
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
    private val scanner: MediaScanner,
    private val proxyApplier: com.gallery.sync.data.local.media.ProxyApplier,
    private val videoOptimiser: com.gallery.sync.data.local.media.VideoOptimiser,
    private val entryDao: com.gallery.sync.data.local.dao.BackupEntryDao
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
                    firstBackupStartHour = prefs.firstBackupStartHour,
                    firstBackupRequiresCharging = prefs.firstBackupRequiresCharging,
                    firstBackupStartAtEpochMillis = prefs.firstBackupStartAtEpochMillis,
                    firstBackupDelayMillis = prefs.firstBackupDelayMillis,
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

    private var pendingProxyCandidates: List<com.gallery.sync.data.local.entity.BackupEntryEntity> = emptyList()

    /**
     * Builds a write request for the one-time bulk optimise, or returns null if the SAF grant
     * already covers the files (in which case it applies immediately).
     */
    suspend fun buildWizardProxyRequest(): android.content.IntentSender? {
        pendingProxyCandidates = proxyApplier.candidatesAll()
        if (pendingProxyCandidates.isEmpty()) return null

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
                val current = _state.value
                val video = current.videoOptimiseRunning
                if (!video && !current.optimiseRunning) return@launch

                val remaining =
                    if (video) videoOptimiser.wizardCandidates().size
                    else proxyApplier.candidatesAll().size
                val done = entryDao.countProxied(video = video)

                _state.value = _state.value.copy(
                    optimiseProgressDone = done,
                    optimiseProgressTotal = done + remaining
                )

                if (remaining == 0) {
                    _state.value = if (video) {
                        _state.value.copy(
                            videoOptimiseRunning = false,
                            videoOptimiseFinished = true,
                            videoOptimisedCount = done
                        )
                    } else {
                        _state.value.copy(
                            optimiseRunning = false,
                            optimiseFinished = true,
                            optimisedCount = done
                        )
                    }
                    Logger.i(TAG, "${if (video) "video" else "photo"} optimise finished: $done")
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
     * Arms the wizard's delayed start, [minutes] from now to the minute.
     *
     * Deliberately not routed through `setFirstBackupStartHour`: that stores an hour of day, so a
     * delay chosen at 13:25 would land on 14:00 and be 35 minutes rather than the hour asked for.
     *
     * Counted in minutes rather than hours since 4 Sept 2026, so the card can offer a delay short
     * enough to sit and watch. The chips still read in hours above the shortest one.
     */
    fun setFirstBackupDelay(minutes: Int) {
        viewModelScope.launch {
            // Never over the top of a run already moving bytes. Arming re-enqueues the manual chain
            // with REPLACE, so without this a delay chosen after the upload began would cancel it —
            // which is exactly what happened on 4 Sept 2026, stopping a live run at 9 of 155.
            //
            // Withdrawing Back from the progress card closes the route that reached this; the guard
            // stays because a second route would be silent, and what it costs is nothing.
            val current = _state.value
            if (current.backupRunning || current.backupCompleted > 0 || current.backupFinished) {
                Logger.w(TAG, "ignoring delay request: backup already under way")
                return@launch
            }
            val delayMillis = minutes * 60L * 1000L
            settings.setFirstBackupStartAt(
                epochMillis = System.currentTimeMillis() + delayMillis,
                delayMillis = delayMillis
            )
        }
    }

    /**
     * Cancels any pending delay and uploads now — what the wizard's "Sync now" does.
     *
     * The delay is cleared before the worker is enqueued, so a process death between the two leaves
     * a run that starts immediately rather than a countdown that has already fired.
     */
    /** Drops a pending delay without starting anything — the wizard's "Right now" choice. */
    fun clearFirstBackupDelay() {
        viewModelScope.launch {
            settings.setFirstBackupStartAt(null)
            // Cancels a chain armed by an earlier visit to this card. Without this, changing your
            // mind back to "Right now" would leave the old delayed run queued and it would fire
            // later on its own.
            BackupScheduling.cancelManualRun(workManager)
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
            val startAt = settings.current().firstBackupStartAtEpochMillis ?: return@launch
            val remaining = startAt - System.currentTimeMillis()
            if (remaining <= 0L) return@launch

            settings.setWizardStep(TOTAL_STEPS)
            backupEngine.refreshLedger()
            backupEngine.reconcileAndRequeue()
            val grandTotal = backupEngine.outstandingCountAll()
            settings.setWizardBackupTotal(grandTotal)
            _state.value = _state.value.copy(backupTotal = grandTotal)

            val prefs = settings.current()
            BackupScheduling.enqueueDelayedManualRun(
                workManager = workManager,
                allowMeteredNetwork = prefs.allowMeteredNetwork,
                delayMillis = remaining,
                allAlbums = true
            )
            Logger.i(TAG, "delayed first backup armed: ${remaining}ms, $grandTotal pending")
        }
    }

    /**
     * The countdown has run out. The work is already queued, so this only clears the due time and
     * starts watching — enqueueing again here would replace a chain that may already be uploading.
     */
    fun onDelayElapsed() {
        viewModelScope.launch {
            settings.setFirstBackupStartAt(null)
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
            settings.setWizardBackupTotal(0)
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
        backupObserverJob?.cancel()
        backupObserverJob = viewModelScope.launch {
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
                    val shouldOptimise = _state.value.libraryChoice.mode?.proxiesPhotos == true
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
        // Written through, not just held. Closing the wizard mid-backup ends the process, and this
        // is what step 9 reads to decide whether anything gets optimised when the upload finishes.
        viewModelScope.launch { settings.setLibraryChoice(choice) }
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

    /** Scans MediaStore for all media directories. Nothing is pre-checked. */
    fun discoverDirectories() {
        viewModelScope.launch {
            _state.value = _state.value.copy(discoveryRunning = true)
            val dirs = scanner.discoverDirectories()

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
            val checks = dirs.associate { dir -> dir.name to false }

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

    /**
     * Builds the queue of directories that need SAF grants for write access.
     *
     * Returns true if there are directories to walk. False means all checked directories are
     * already covered by existing grants (or none were checked).
     */
    fun buildSafGrantQueue(): Boolean {
        val checked = _state.value.directoryChecks.filter { it.value }.keys
        val covered = _state.value.directories.map { it.relativePath }
        val needed = checked.filter { dir ->
            covered.none { it.startsWith(dir) || dir.startsWith(it) }
        }
        _state.value = _state.value.copy(safGrantQueue = needed.toList())
        return needed.isNotEmpty()
    }

    /**
     * Processes one SAF grant result and advances the queue.
     *
     * Called from the treePicker callback after the user picks a folder or cancels.
     * A cancelled pick (null URI) skips that directory — the user can add it later from Settings.
     */
    fun onSafGrantReceived(uri: android.net.Uri?) {
        viewModelScope.launch {
            if (uri != null) {
                val added = sources.add(uri)
                _state.value = _state.value.copy(directoryRefused = !added)
            }
            val queue = _state.value.safGrantQueue
            _state.value = _state.value.copy(
                safGrantQueue = if (queue.size > 1) queue.drop(1) else emptyList()
            )
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
