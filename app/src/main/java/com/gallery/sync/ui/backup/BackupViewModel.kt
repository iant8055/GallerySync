package com.gallery.sync.ui.backup

import android.content.Context
import android.content.IntentSender
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.gallery.sync.data.local.entity.BackupEntryEntity
import com.gallery.sync.data.local.media.LocalCopyRemover
import com.gallery.sync.data.local.media.ProxyApplier
import com.gallery.sync.data.local.media.ProxyGenerator
import com.gallery.sync.data.local.media.ProxyOutcome
import com.gallery.sync.data.local.media.VideoOptimiser
import com.gallery.sync.data.local.media.VideoReadiness
import com.gallery.sync.worker.PhotoOptimiseLauncher
import com.gallery.sync.worker.VideoOptimiseLauncher
import com.gallery.sync.data.local.settings.BackupSettings
import com.gallery.sync.domain.backup.AlbumMergeWarning
import com.gallery.sync.worker.BackupScheduling
import com.gallery.sync.worker.BackupWorker
import dagger.hilt.android.qualifiers.ApplicationContext
import com.gallery.sync.data.local.dao.AlbumCloudStatusDao
import com.gallery.sync.data.local.dao.AlbumPreferenceDao
import com.gallery.sync.data.local.dao.FolderPreferenceDao
import com.gallery.sync.data.local.dao.BackupEntryDao
import com.gallery.sync.data.local.entity.AlbumMode
import com.gallery.sync.data.local.entity.AlbumPreferenceEntity
import com.gallery.sync.data.local.entity.BackupState
import com.gallery.sync.data.local.media.MediaAccess
import com.gallery.sync.data.local.media.MediaScanner
import com.gallery.sync.domain.backup.AlbumCloudClaim
import com.gallery.sync.domain.backup.ArchiveAge
import com.gallery.sync.domain.backup.BackupEngine
import com.gallery.sync.domain.backup.BackupLocation
import com.gallery.sync.domain.backup.CameraAlbum
import com.gallery.sync.domain.backup.CameraOptimisePlan
import com.gallery.sync.domain.backup.CameraOptimiseSettings
import com.gallery.sync.domain.backup.CloudConfirmation
import com.gallery.sync.domain.backup.FilePin
import com.gallery.sync.domain.backup.FolderDestination
import com.gallery.sync.domain.backup.GooglePhotosDestination
import com.gallery.sync.domain.backup.ReconcileWithCloud
import com.gallery.sync.domain.backup.SetFolderDestination
import com.gallery.sync.domain.backup.MediaAge
import com.gallery.sync.domain.backup.OptimiseMode
import com.gallery.sync.domain.backup.OptimiseOnSyncNow
import com.gallery.sync.domain.backup.PhotoOptimisePolicy
import com.gallery.sync.domain.backup.StopReason
import com.gallery.sync.domain.backup.VideoQuality
import com.gallery.sync.util.Logger
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * The hero's readout for one slice of the album list.
 *
 * Per-mode rather than global, because the numbers that matter differ by mode: Sync is judged on
 * what optimising saved, Archive on what is still unverified — and asking "how much space did I
 * get back" of a Backup album is a category error, since Backup never removes anything.
 */
data class AlbumsSummary(
    val mode: AlbumMode?,
    val albumCount: Int,
    val imageCount: Int,
    val videoCount: Int,
    val totalBytes: Long,
    val optimisedCount: Int,
    val savedBytes: Long,
    /** In Archive albums, files not yet confirmed in OneDrive — so, not yet removable. */
    val awaitingVerification: Int,

    /**
     * Files this set has put in OneDrive, and what they occupy there.
     *
     * The population an Archive album is *about*. Every other figure here counts what is on the
     * phone, which for a finished archive is nothing — so the Archive filter reported "0 Images · 0
     * Videos" over two albums holding 24 files in the cloud. Ian, 27 Aug 2026: it should list what
     * has been archived from those folders.
     */
    val archivedCount: Int,
    val archivedBytes: Long
)

/**
 * One album: how much of it is safe, and whether it is still being watched.
 *
 * [mode] and [backedUpCount] are deliberately independent. An album that will never gain another
 * photo is *finished*, not *unprotected* — switching it off means "stop spending time on this", and
 * rendering that the same as "not backed up" is alarming and wrong.
 */
/**
 * One top-level folder (`DCIM`, `Pictures`...) and where its new uploads go. TASK-026: the destination
 * is chosen here, one level above the album, and never per album.
 */
data class FolderRow(
    val name: String,
    val albumCount: Int,
    val fileCount: Int,
    val totalBytes: Long,
    val location: BackupLocation
)

data class AlbumRow(
    val name: String,
    val itemCount: Int,
    val totalBytes: Long,
    /** What the user chose for this album. [AlbumMode.OFF] means finished or ignored. */
    val mode: AlbumMode,
    /**
     * How many of this album's files the **ledger** says were uploaded from this phone.
     *
     * Retained for the progress arithmetic and the pending count, and deliberately no longer used to
     * tell the user their files are in OneDrive. That claim is [cloudClaim], which comes from asking
     * the drive. See `AlbumCloudClaim`.
     */
    val backedUpCount: Int = 0,
    /** What the drive itself last said about this album, or NeverChecked. */
    val cloudClaim: AlbumCloudClaim = AlbumCloudClaim.NeverChecked,
    val proxiedCount: Int = 0,
    /** Files here kept at full size. Drawn after the optimised count. See `FilePin`. */
    val pinnedCount: Int = 0,
    val imageCount: Int = 0,
    val videoCount: Int = 0,
    /** What optimising reclaimed in this album. Zero unless something here has been proxied. */
    val savedBytes: Long = 0L,
    /** Files here the queue has given up on after five attempts. See `RetryFailed`. */
    val failedCount: Int = 0,
    /** Uploaded rows including files no longer on the phone. Only for [isArchivedAndEmpty]. */
    val everBackedUpCount: Int = 0,

    /** What those uploaded rows occupy in OneDrive — the only non-zero size an archived album has. */
    val everBackedUpBytes: Long = 0L,

    /** Files here sent to Google Photos. See `AlbumBackupCount.googlePhotosSent` for why this is
     *  kept apart from [backedUpCount] rather than folded into it. */
    val sentElsewhereCount: Int = 0,

    /** Per cloud, how many of this album's files were sent somewhere other than OneDrive. */
    val sentTo: Map<BackupLocation, Int> = emptyMap(),

    /**
     * Where this album's *new* uploads currently go — resolved from its top-level folder
     * (`MediaAlbum.topLevelFolder`) against the folder-destination map, falling back to the app-wide
     * default exactly the way [com.gallery.sync.domain.backup.BackupEngine.refreshLedger] resolves
     * it for real. Not stored on the album itself — there is nothing per-album to store, see
     * TASK-026 — purely a display/restriction value computed fresh on every [refresh].
     */
    val backupLocation: BackupLocation = BackupLocation.DEFAULT,

    /** The top-level folder this album lives under, or null if the scan could not say. */
    val topLevelFolder: String? = null
) {
    val isEnabled: Boolean get() = mode.uploads

    /**
     * Whether this album's cloud line should mention OneDrive at all.
     *
     * Not when everything it has sent went to another cloud: "0 of 46 verified in OneDrive" said of an album
     * that belongs to Dropbox reads as a fault, when OneDrive was never meant to hold it (Ian, 24 Sept 2026).
     * Shown when any of its files went to OneDrive, and when nothing has been sent anywhere yet.
     */
    val showsOneDriveClause: Boolean get() = !(sentElsewhereCount > 0 && (backedUpCount - sentElsewhereCount) <= 0)

    val backedUpOnly: Int get() = (backedUpCount - proxiedCount).coerceAtLeast(0)

    val status: AlbumStatus
        get() = when {
            itemCount > 0 && backedUpCount >= itemCount -> AlbumStatus.COMPLETE
            backedUpCount > 0 -> AlbumStatus.PARTIAL
            else -> AlbumStatus.NOT_BACKED_UP
        }

    val outstanding: Int get() = (itemCount - backedUpCount).coerceAtLeast(0)

    /**
     * Archive ran to completion: the mode is set, the ledger remembers files, none are still here.
     *
     * Rendered as its own line rather than through the usual counts, which would describe files
     * that are no longer on the phone — "1 backed up · 12 optimised" over an album holding nothing.
     */
    val isArchivedAndEmpty: Boolean
        get() = mode == AlbumMode.ARCHIVE && itemCount == 0 && everBackedUpCount > 0
}

enum class AlbumStatus {

    /** Every file is in OneDrive. Safe whether or not it is still being watched. */
    COMPLETE,

    PARTIAL,

    NOT_BACKED_UP
}

/**
 * What a run is doing, or what it did.
 *
 * Typed rather than a pre-built sentence: a ViewModel that assembles "4 uploaded, 2 failed" has
 * baked English into logic, and no amount of translation can reach it. The screen turns this into
 * words using string resources.
 */
sealed interface BackupStatus {

    data object Scanning : BackupStatus

    /** Mid-run, with live position so a long upload does not look like a hang. */
    data class Uploading(
        val completed: Int,
        val total: Int,
        val currentFile: String,
        val percentOfCurrent: Int
    ) : BackupStatus

    data object NoPermission : BackupStatus

    data class Finished(
        val uploaded: Int,
        val skipped: Int,
        val failed: Int,
        /** Skipped this run because their album could not be listed. Nothing is wrong with them. */
        val deferred: Int,
        val pruned: Int,
        val remaining: Int,
        val stoppedBecause: StopReason?
    ) : BackupStatus
}

data class BackupUiState(
    val access: MediaAccess = MediaAccess.NONE,
    val albums: List<AlbumRow> = emptyList(),
    /** Albums merged from two spellings of one folder and set Off, not yet dismissed. TASK-023. */
    val albumMergeWarnings: List<AlbumMergeWarning> = emptyList(),
    val isScanning: Boolean = false,
    val isRunning: Boolean = false,
    val status: BackupStatus? = null,
    /**
     * False until [refreshCounts] has run once.
     *
     * Every count below starts at zero, and zero is a claim — the hero renders
     * "0 files verified in OneDrive" on every cold start, confidently and wrongly, for as long as
     * the first read takes. On a screen whose job is telling someone their photos are safe, that is
     * the worst possible thing to flash. This separates "nothing" from "not known yet".
     */
    val hasLoadedCounts: Boolean = false,
    /** A Rescan is walking OneDrive. Separate from [isScanning], which is the device scan. */
    val isCheckingCloud: Boolean = false,
    val uploadedCount: Int = 0,
    val uploadedBytes: Long = 0L,
    /** Outstanding files **within the selected albums** — not the whole library. */
    val pendingCount: Int = 0,
    val pendingBytes: Long = 0L,
    /** Local copies made redundant by a confirmed cloud copy, and what they occupy. */
    val redundantCount: Int = 0,
    val redundantBytes: Long = 0L,
    /** False below API 30, where Android has no media trash and removal could only be permanent. */
    val canRemoveLocalCopies: Boolean = false,
    /** Back up on its own when new photos appear. */
    val isAutomaticEnabled: Boolean = false,
    val isAutoOptimiseEnabled: Boolean = false,
    /** Allow automatic runs on mobile data, not just Wi-Fi. */
    val allowMeteredNetwork: Boolean = false,
    val isOptimiseEnabled: Boolean = false,
    val optimisePhotos: Boolean = false,
    val photoOptimiseMode: OptimiseMode = OptimiseMode.DEFAULT,
    val optimiseVideo: Boolean = false,
    val videoOptimiseMode: OptimiseMode = OptimiseMode.DEFAULT,
    val videoOptimiseAge: MediaAge = MediaAge.DEFAULT,
    val videoQuality: VideoQuality = VideoQuality.DEFAULT,
    val hasCompletedFirstBackup: Boolean = false,
    /**
     * Albums set to Archive that have files confirmed in OneDrive and still on the phone.
     *
     * Drives the prompt on the main screen. Archive cannot run unattended —
     * `createTrashRequest` only launches from an Activity — so something has to bring the user back
     * when files become eligible. That prompt is a summons rather than a second consent: the mode is
     * the consent, and CLAUDE.md forbids mirroring Android's own dialog with an app-level one.
     *
     * What it does carry is the fact Android's dialog cannot state — that the cloud copy is verified.
     */
    val archiveAlbumsReady: List<String> = emptyList(),
    /** What the last removal attempt refused to remove, and why. Null before any attempt. */
    val removalHeldBack: CloudConfirmation? = null,
    /** Photos whose local copy could be replaced by a proxy, and what they occupy now. */
    val proxyCandidateCount: Int = 0,
    val canProxy: Boolean = false,
    /** Clips ready to optimise: in a Sync album, verified, old enough, and inside a granted folder. */
    val videoCandidateCount: Int = 0,
    /**
     * Something set to Manual is ready, so **Sync now** has work even with nothing to send. Ian,
     * 19 Sept 2026: Manual means through that button. See [OptimiseOnSyncNow].
     */
    val manualOptimiseWaiting: Boolean = false,
    /**
     * Sync now has just finished and photos are Manual, so the screen should offer Android's dialog for
     * any photo outside the granted folders. Consumed by [BackupViewModel.consumeProxyDialogRequest].
     */
    val proxyDialogRequested: Boolean = false,
    /**
     * The fallback destination for a folder with no choice of its own. Not a visible control any more
     * (TASK-026, 24 Sept 2026) — the per-folder [folders] list replaces it. See
     * `BackupPreferences.backupLocation`.
     */
    /** Albums the user has set a mode on or dismissed the notice for. See [BackupPreferences.acknowledgedAlbums]. */
    val acknowledgedAlbums: Set<String> = emptySet(),
    /** The OneDrive folder new uploads go into, for the folder-to-cloud pairing in Settings. */
    val destinationRoot: String = "",
    val backupLocation: BackupLocation = BackupLocation.DEFAULT,
    /** Top-level folders found on the device, each with its own destination. */
    val folders: List<FolderRow> = emptyList(),
    /** The Camera album's manual optimise is queued or running. Drives its button and its list. */
    val cameraOptimising: Boolean = false,
    /** Whether the restore screen lists cloud folders that hold nothing. */
    val showEmptyCloudFolders: Boolean = false,
    /** What the Archive tab's age filter starts at. See `ArchiveAge`. */
    val archiveDefaultAge: ArchiveAge = ArchiveAge.DEFAULT,
    /** Whether a notification is sent when files in an Archive album have come of age. */
    val archiveNotifyEnabled: Boolean = false,
    /**
     * The user has held backing up until they say otherwise.
     *
     * Distinct from "not running". A paused app is deliberately idle and stays that way through
     * every automatic trigger; an idle one is simply waiting for the next.
     */
    val isPaused: Boolean = false,
    /** Bytes outstanding when this run began. The denominator for [runProgress]. */
    val runBaselineBytes: Long = 0L,
    /** Bytes of the file currently uploading that have been sent. Smooths the figure below. */
    val currentBytesSent: Long = 0L,
    /**
     * The highest progress this run has reported, so it never goes backwards.
     *
     * Its two halves update at different moments: `currentBytesSent` drops to zero the instant a
     * file completes, while `pendingBytes` only falls when counts are refreshed a beat later. In
     * that window the sum collapses, and the hero flashed a percentage and fell back to 0% on every
     * file. Fold 4, 28 Aug 2026.
     *
     * Refreshing counts on completion closes most of the gap; this closes the rest, because a
     * progress bar that goes backwards is worse than one that is briefly stale.
     */
    val runProgressFloor: Float = 0f

) {

    /**
     * How much of the selected work is done, by bytes, or null when there is nothing to show.
     *
     * **Both halves are scoped to selected albums, and that is the whole trick.** The first version
     * of this divided every byte ever uploaded by the bytes pending in selected albums — a global
     * numerator over a scoped denominator — and the bar read 99% while the hero underneath it said
     * "Uploading 2 of 22, 8%". Caught on the Fold 4, 26 Aug 2026, by looking at the screen.
     *
     * The bar sits directly under "N files selected, X MB", so it has to be a proportion of exactly
     * that and of nothing else.
     *
     * Null rather than zero when nothing is selected: a bar sitting empty says "none of it is backed
     * up", which is a claim, and the honest answer is that there is no work in progress to report.
     */
    /**
     * How far through *this run* we are, by bytes, or null when no run is in progress.
     *
     * Deliberately not [backedUpFraction], which is a proportion of the whole selected library and
     * so opens a run at whatever the library already was — 93% on the Fold 4, 28 Aug 2026, with
     * 7,516 MB uploaded against 574 MB pending. True about the library, useless about the run, and
     * read as the latter by everyone because it sits on the button they just pressed.
     *
     * Starts at zero, ends at one, and does not reset between the batches of one run because the
     * baseline is persisted rather than recomputed per invocation.
     */
    val runProgress: Float?
        get() {
            if (runBaselineBytes <= 0L) return null

            // Finished work comes from the ledger, which survives a restart and only ever falls;
            // the file in flight comes from the worker, which is the part the ledger cannot see
            // until it completes. Adding them cannot double-count, because a file being uploaded
            // is still PENDING and so still inside pendingBytes.
            //
            // An earlier version divided a per-run byte count by this baseline. The run total
            // resets when the process does while the baseline persists, so the two measured
            // different things and the percentage lurched on every restart — 81% dropping to 1%
            // with nothing having changed. Fold 4, 28 Aug 2026.
            val done = (runBaselineBytes - pendingBytes).coerceAtLeast(0L) + currentBytesSent
            return (done.toFloat() / runBaselineBytes).coerceIn(0f, 1f).coerceAtLeast(runProgressFloor)
        }

    val backedUpFraction: Float?
        get() {
            val total = uploadedBytes + pendingBytes
            return if (total <= 0L) null else (uploadedBytes.toFloat() / total).coerceIn(0f, 1f)
        }

    /** Files that would be sent if a run started now. */
    val enabledItemCount: Int get() = albums.filter { it.isEnabled }.sumOf { it.itemCount }

    val enabledBytes: Long get() = albums.filter { it.isEnabled }.sumOf { it.totalBytes }

    /** Something selected, and all of it already in OneDrive. */
    val isSelectionFullyBackedUp: Boolean get() = enabledItemCount > 0 && pendingCount == 0

    /**
     * How many albums are in each mode.
     *
     * The Albums tab is where modes are chosen, so this is what its hero counts. It used to head the
     * screen with "Files verified in OneDrive", which is a true and reassuring number about a
     * different subject — Ian, 27 Aug 2026: *"This is the tab where we select Album modes."* The
     * verified figure has not been dropped; it moved into the detail beneath, where it reads as
     * context rather than as the headline of a list it does not describe.
     */
    val backupAlbumCount: Int get() = albums.count { it.mode == AlbumMode.BACKUP }
    val syncAlbumCount: Int get() = albums.count { it.mode == AlbumMode.SYNC }
    val archiveAlbumCount: Int get() = albums.count { it.mode == AlbumMode.ARCHIVE }
    val offAlbumCount: Int get() = albums.count { it.mode == AlbumMode.OFF }

    /** Albums doing something. */
    val activeAlbumCount: Int get() = albums.count { it.mode != AlbumMode.OFF }

    /**
     * What the hero says about whatever the filter is showing.
     *
     * Replaces a single line that claimed "Everything is backed up" — true of the switched-on
     * albums and false of the phone, because an album set to Off is excluded from the count and
     * still full of files. Ian caught it on 27 Aug 2026 with `Test` sitting there at 11 files,
     * none of them backed up, under a card saying everything was.
     *
     * A summary of the *filtered* set cannot make that mistake: it describes the albums it is
     * counting, and the filter says which those are.
     */
    fun summaryFor(mode: AlbumMode?): AlbumsSummary {
        val rows = if (mode == null) albums else albums.filter { it.mode == mode }
        return AlbumsSummary(
            mode = mode,
            albumCount = rows.size,
            imageCount = rows.sumOf { it.imageCount },
            videoCount = rows.sumOf { it.videoCount },
            totalBytes = rows.sumOf { it.totalBytes },
            optimisedCount = rows.sumOf { it.proxiedCount },
            savedBytes = rows.sumOf { it.savedBytes },
            awaitingVerification = rows.sumOf { it.outstanding },
            archivedCount = rows.sumOf { it.everBackedUpCount },
            archivedBytes = rows.sumOf { it.everBackedUpBytes }
        )
    }

    /**
     * The button is worth pressing — either to start work, or to stop work already running.
     *
     * While a run is live this is always true, because the button is Stop then. It used to be
     * `!isRunning && pendingCount > 0`, which disabled the one control that could end a run the
     * user had started.
     */
    val canRunBackup: Boolean get() = isRunning || pendingCount > 0

    /** Whether the Sync now button does anything: files to send, or Manual optimising to run. */
    val canSyncNow: Boolean get() = pendingCount > 0 || manualOptimiseWaiting

    /**
     * Albums nobody has chosen a mode for yet: at Off, on the phone, and neither set by the user nor
     * dismissed. Every new album starts at Off (Ian, 24 Sept 2026), so without this a new folder, say a
     * new WhatsApp album, would silently go unbacked-up. The Albums tab says so.
     */
    val waitingAlbums: List<AlbumRow>
        get() = albums.filter { it.mode == AlbumMode.OFF && it.itemCount > 0 && it.name !in acknowledgedAlbums }
}

@HiltViewModel
class BackupViewModel @Inject constructor(
    private val scanner: MediaScanner,
    private val albumDao: AlbumPreferenceDao,
    private val folderDao: FolderPreferenceDao,
    private val setFolderDestination: SetFolderDestination,
    private val entryDao: BackupEntryDao,
    private val cloudStatusDao: AlbumCloudStatusDao,
    private val reconcile: ReconcileWithCloud,
    private val engine: BackupEngine,
    private val localCopyRemover: LocalCopyRemover,
    private val proxyApplier: ProxyApplier,
    private val videoOptimiser: VideoOptimiser,
    private val videoOptimise: VideoOptimiseLauncher,
    private val photoOptimise: PhotoOptimiseLauncher,
    private val settings: BackupSettings,
    @param:ApplicationContext private val context: Context
) : ViewModel() {

    /** Set when Sync now is pressed with photos on Manual, and read when that run finishes. */
    private var offerProxyDialogAfterRun = false

    /** Captured when consent is requested, so exactly that set is what gets rewritten. */
    private var pendingProxyCandidates: List<BackupEntryEntity> = emptyList()

    /**
     * Which backup chains are live, by unique work name.
     *
     * One set rather than one boolean per collector: four flows each writing `isRunning` would
     * race, and the last to emit would win regardless of what the others were doing.
     */
    private val runningWork = MutableStateFlow(emptySet<String>())

    /** Last completed-file count seen from a worker, so counts are re-read once per file. */
    private var lastCompletedSeen = -1

    private val _state = MutableStateFlow(BackupUiState())
    val state: StateFlow<BackupUiState> = _state.asStateFlow()

    init {
        refresh()
        observeManualRun()
        viewModelScope.launch {
            settings.preferences.collect { prefs ->
                _state.value = _state.value.copy(
                    isAutomaticEnabled = prefs.isAutomaticEnabled,
                    isAutoOptimiseEnabled = prefs.isOptimiseEnabled &&
                        prefs.photoOptimiseMode == OptimiseMode.Auto,
                    allowMeteredNetwork = prefs.allowMeteredNetwork,
                    showEmptyCloudFolders = prefs.showEmptyCloudFolders,
                    archiveDefaultAge = prefs.archiveDefaultAge,
                    archiveNotifyEnabled = prefs.archiveNotifyEnabled,
                    isPaused = prefs.isPaused,
                    runBaselineBytes = prefs.runBaselineBytes,
                    backupLocation = prefs.backupLocation,
                    acknowledgedAlbums = prefs.acknowledgedAlbums,
                    destinationRoot = prefs.destinationRoot,
                    isOptimiseEnabled = prefs.isOptimiseEnabled,
                    optimisePhotos = prefs.optimisePhotos,
                    photoOptimiseMode = prefs.photoOptimiseMode,
                    optimiseVideo = prefs.optimiseVideo,
                    videoOptimiseMode = prefs.videoOptimiseMode,
                    videoOptimiseAge = prefs.videoOptimiseAge,
                    videoQuality = prefs.videoQuality,
                    hasCompletedFirstBackup = prefs.hasCompletedFirstBackup
                )
            }
        }
        observeBackgroundWork()
        observeVideoOptimise()
        observeWhatOptimisingWaits()
        observeCameraOptimise()
        observeCloudStatus()
        viewModelScope.launch {
            settings.albumMergeWarnings.collect { warnings ->
                _state.update { it.copy(albumMergeWarnings = warnings) }
            }
        }
    }

    fun dismissAllAlbumMergeWarnings() {
        viewModelScope.launch { settings.dismissAllAlbumMergeWarnings() }
    }

    /**
     * Keeps each album row's cloud claim current as the reconciliation answers for it.
     *
     * The reconciliation writes a row per album while walking the drive, and it runs at launch at
     * the same time this ViewModel is building its list — so reading once caught only the albums
     * that happened to finish first. Rebuilding just the claims, rather than re-running the whole
     * refresh, keeps this cheap enough to fire per album.
     */
    private fun observeCloudStatus() {
        viewModelScope.launch {
            cloudStatusDao.observeAll().collect { statuses ->
                val byAlbum = statuses.associateBy { it.albumName }
                _state.update { current ->
                    current.copy(
                        albums = current.albums.map { album ->
                            album.copy(cloudClaim = AlbumCloudClaim.from(byAlbum[album.name]))
                        }
                    )
                }
            }
        }
    }

    /**
     * Follows the ongoing video chain, so the screen can say it is working, or waiting for the charger.
     *
     * The counts are re-read whenever the chain changes state: each batch that finishes has just made
     * clips smaller, and the number on the button should fall as it happens.
     */
    private fun observeVideoOptimise() {
        viewModelScope.launch {
            BackupScheduling.videoOptimiseWork(WorkManager.getInstance(context)).collectLatest {
                refreshVideoReadiness()
            }
        }
    }

    /**
     * Keeps "Sync now has optimising to run" honest while the app is open.
     *
     * The state that enables the button is worked out from the ledger, and until now it was only
     * re-read when a run reported through a chain this screen watches. A file taken with the app
     * open is uploaded by the content-triggered run, which is replaced by its own successor the moment
     * it finishes, so no "finished" is ever seen: the button stayed grey with a Manual photo waiting
     * (found on the Moto G, 20 Sept 2026), and stayed enabled after Sync now had optimised it.
     *
     * Re-reads whenever the number of uploaded files changes, which is how a file becomes a candidate,
     * or the optimise chain changes state, which is how it stops being one. Debounced, because an
     * upload run changes the first many times a minute and the read is not free.
     */
    @OptIn(FlowPreview::class)
    private fun observeWhatOptimisingWaits() {
        val workManager = WorkManager.getInstance(context)
        viewModelScope.launch {
            merge(
                entryDao.observeCount(BackupState.UPLOADED).map { },
                workManager.getWorkInfosForUniqueWorkFlow(BackupScheduling.OPTIMISE_WORK)
                    .map { infos -> infos.map { it.state } }
                    .distinctUntilChanged()
                    .map { }
            ).debounce(1_500).collect { refreshCounts() }
        }
    }

    /** Keeps [BackupUiState.cameraOptimising] true from the tap until the last batch has finished. */
    private fun observeCameraOptimise() {
        viewModelScope.launch {
            BackupScheduling.cameraOptimiseWork(WorkManager.getInstance(context))
                .map { infos ->
                    infos.any { !it.state.isFinished && BackupScheduling.optimiseTag(BackupScheduling.PHASE_CAMERA) in it.tags }
                }
                .distinctUntilChanged()
                .collect { live ->
                    val wasLive = _state.value.cameraOptimising
                    _state.update { it.copy(cameraOptimising = live) }
                    // The run has just ended: the album cards still show the counts from before it.
                    if (wasLive && !live) refresh()
                }
        }
    }

    private fun observeBackgroundWork() {
        val workManager = WorkManager.getInstance(context)
        val workNames = listOf(
            BackupScheduling.CONTENT_TRIGGER_WORK,
            BackupScheduling.CONTINUATION_WORK,
            BackupScheduling.PERIODIC_WORK
        )

        // isRunning used to be set only from the manual chain, so an automatic run left the button
        // reading "Sync now" throughout — observed on the Moto G, 28 Aug 2026, while 21 GB
        // uploaded. Since the button is also the only way to reach Pause and Stop, the runs a user
        // most wants to interrupt were the ones with no control attached. Every chain now reports
        // into one set, and running means any of them is live.
        viewModelScope.launch {
            runningWork.collect { live ->
                _state.value = _state.value.copy(isRunning = live.isNotEmpty())
            }
        }

        for (name in workNames) {
            viewModelScope.launch {
                workManager.getWorkInfosForUniqueWorkFlow(name).collectLatest { infos ->
                    runningWork.update { live ->
                        if (infos.any { it.state == WorkInfo.State.RUNNING }) live + name
                        else live - name
                    }

                    val running = infos.firstOrNull { it.state == WorkInfo.State.RUNNING }
                    if (running != null) {
                        val data = running.progress
                        val total = data.getInt(BackupWorker.PROGRESS_TOTAL, 0)
                        if (total > 0) {
                            _state.value = _state.value.copy(
                                currentBytesSent =
                                    data.getLong(BackupWorker.PROGRESS_CURRENT_SENT, 0L),
                                status = BackupStatus.Uploading(
                                    completed = data.getInt(BackupWorker.PROGRESS_COMPLETED, 0),
                                    total = total,
                                    currentFile = data.getString(BackupWorker.PROGRESS_FILE) ?: "",
                                    percentOfCurrent = data.getInt(BackupWorker.PROGRESS_PERCENT, 0)
                                )
                            )
                        }
                    } else if (infos.any { it.state == WorkInfo.State.SUCCEEDED }) {
                        _state.value = _state.value.copy(status = null)
                        refresh()
                    }
                }
            }
        }
    }

    /**
     * Turns automatic backup on or off.
     *
     * Off by default and never enabled implicitly — installing a build must not start uploading
     * someone's library on its own.
     */
    fun setAutomaticEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settings.setAutomaticEnabled(enabled)
            val workManager = WorkManager.getInstance(context)
            if (enabled) {
                BackupScheduling.enable(workManager, settings.current().allowMeteredNetwork)
            } else {
                BackupScheduling.disable(workManager)
            }
        }
    }

    /** Dismisses the "new albums are waiting" notice for the albums it is showing now. */
    fun dismissWaitingAlbums() {
        val names = _state.value.waitingAlbums.map { it.name }
        viewModelScope.launch { settings.acknowledgeAlbums(names) }
    }

    fun setAllowMeteredNetwork(allowed: Boolean) {
        viewModelScope.launch {
            settings.setAllowMeteredNetwork(allowed)
            // Constraints are fixed when work is enqueued, so a live schedule has to be rebuilt
            // or the change would not take effect until something else happened to reschedule it.
            if (settings.current().isAutomaticEnabled) {
                BackupScheduling.enable(WorkManager.getInstance(context), allowed)
            }
        }
    }

    /** Re-reads permission state and the album list. Cheap enough to call on every resume. */
    /**
     * What the Rescan button does: re-read the phone, **then ask OneDrive about every album**.
     *
     * Also what entering the Albums tab does. Ian, 28 Aug 2026: *"a move to the Albums tab is ok to
     * trigger a refresh — just so we know the user is getting fresh data."* Weighed against the
     * cost, and the cost loses: a screen whose whole job is telling somebody their photos are safe
     * should not be showing them an answer from an hour ago.
     *
     * Kept out of [refresh] all the same, because that is called from several places that only want
     * the device counts — a rebuild after a mode change has no business walking OneDrive.
     *
     * The cost is real: roughly one listing per album plus one per extra page, measured at about
     * 55 seconds for 3,335 files across six albums on the Moto G. Hence the in-flight guard below,
     * and the button that says what it is doing.
     */
    fun rescan() {
        refresh()

        // One walk at a time. This runs on entry to the tab as well as from the button, so without
        // the guard a few tab switches would stack several full drive walks on top of each other.
        if (_state.value.isCheckingCloud) return

        viewModelScope.launch {
            _state.update { it.copy(isCheckingCloud = true) }
            try {
                reconcile.run()
            } finally {
                // In a finally: a walk abandoned half way still has to clear the flag, or the
                // button stays busy for the life of the process.
                _state.update { it.copy(isCheckingCloud = false) }
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            val access = scanner.access()
            _state.value = _state.value.copy(access = access)

            if (access == MediaAccess.NONE) {
                _state.value = _state.value.copy(albums = emptyList())
                return@launch
            }

            _state.value = _state.value.copy(isScanning = true)

            // Which albums already had a choice recorded, captured **before** the scan — which is
            // what now seeds a row for anything newly discovered. Read this afterwards and every
            // album looks familiar, so a genuinely new one would never arm the continuation below.
            val knownBefore = albumDao.all().mapTo(HashSet()) { it.albumName }

            // Bring the ledger up to date before counting. Without this the counts describe
            // whatever the last run happened to see, and the screen would claim there is nothing
            // to do simply because nothing has scanned yet.
            engine.refreshLedger()

            // Every stored mode, not just which albums are off: the OFF/not-OFF shape predates album
            // modes and cannot render SYNC or ARCHIVE at all. An album with no row takes the
            // factory default until TASK-012 makes that a user setting.
            val storedModes = albumDao.all().associate { it.albumName to it.mode }
            val countsByAlbum = entryDao.albumCounts().associateBy { it.album }
            val cloudByAlbum = cloudStatusDao.all().associateBy { it.albumName }
            val sentToByAlbum = entryDao.sentByLocation().orEmpty()
                .groupBy({ it.album }, { it.location to it.sent })
                .mapValues { it.value.toMap() }
            val prefs = settings.current()
            // Read after refreshLedger, which seeds a row for every newly found folder.
            val folderLocations = folderDao.all().orEmpty().associate { it.folderName to it.backupLocation }

            var hasNewUploadAlbums = false
            val scannedAlbums = scanner.scanAlbums()
            val albums = scannedAlbums.map { album ->
                val counts = countsByAlbum[album.name]
                // No write here any more — [BackupEngine.refreshLedger] seeds the row. The fallback
                // remains only for an album the scanner reports that the ledger has not recorded,
                // which the screen should still render rather than skip.
                val mode = storedModes[album.name] ?: AlbumMode.DEFAULT
                if (album.name !in knownBefore && mode.uploads) hasNewUploadAlbums = true
                AlbumRow(
                    name = album.name,
                    itemCount = album.itemCount,
                    totalBytes = album.totalBytes,
                    mode = mode,
                    backedUpCount = counts?.backedUp ?: 0,
                    cloudClaim = AlbumCloudClaim.from(cloudByAlbum[album.name]),
                    proxiedCount = counts?.proxied ?: 0,
                    pinnedCount = counts?.pinned ?: 0,
                    imageCount = album.imageCount,
                    videoCount = album.videoCount,
                    savedBytes = counts?.savedBytes ?: 0L,
                    failedCount = counts?.failed ?: 0,
                    everBackedUpCount = counts?.everBackedUp ?: 0,
                    everBackedUpBytes = counts?.everBackedUpBytes ?: 0L,
                    sentElsewhereCount = counts?.sentElsewhere ?: 0,
                    sentTo = sentToByAlbum[album.name].orEmpty(),
                    backupLocation = FolderDestination.resolve(
                        album.topLevelFolder, folderLocations, prefs.backupLocation
                    ),
                    topLevelFolder = album.topLevelFolder
                )
            }

            val folders = scannedAlbums
                .filter { it.topLevelFolder != null }
                .groupBy { it.topLevelFolder!! }
                .map { (name, inFolder) ->
                    FolderRow(
                        name = name,
                        albumCount = inFolder.size,
                        fileCount = inFolder.sumOf { it.itemCount },
                        totalBytes = inFolder.sumOf { it.totalBytes },
                        location = FolderDestination.resolve(name, folderLocations, prefs.backupLocation)
                    )
                }
                .sortedByDescending { it.fileCount }

            // Archive albums the scan can no longer see, listed anyway at zero files.
            //
            // Archive's success state is an emptied album, and an emptied album is invisible to the
            // scan: MediaStore excludes trashed files, so a folder whose contents have all been
            // archived returns no items and produces no row. The album leaves this list while its
            // mode is still in force — and CLAUDE.md makes that mode a **standing instruction**
            // covering anything added to the folder later. Undiscoverable and unrevokable is the
            // wrong end state for the one mode that removes files.
            //
            // Recorded as a design hole on 26 Aug 2026, blamed on Samsung deleting the folder, and
            // corrected on 27 Aug when the folder turned out to still be there: the cause is this
            // list being built from MediaStore contents rather than from what the user chose.
            //
            // Archive only. Ian, 27 Aug 2026. An emptied Backup or Sync album has nothing in force
            // and hiding it is right; an emptied Archive album is empty *because the mode worked*,
            // and it has to stay reachable so it can be switched off. Nothing new is invented to do
            // this — the preference row and the verified ledger rows are both kept on purpose.
            val listed = albums.mapTo(HashSet()) { it.name }
            val archivedButEmpty = storedModes
                .filter { (name, mode) -> mode == AlbumMode.ARCHIVE && name !in listed }
                .map { (name, mode) ->
                    val counts = countsByAlbum[name]
                    AlbumRow(
                        name = name,
                        itemCount = 0,
                        totalBytes = 0L,
                        mode = mode,
                        backedUpCount = counts?.backedUp ?: 0,
                        cloudClaim = AlbumCloudClaim.from(cloudByAlbum[name]),
                        proxiedCount = counts?.proxied ?: 0,
                        pinnedCount = counts?.pinned ?: 0,
                        savedBytes = counts?.savedBytes ?: 0L,
                        everBackedUpCount = counts?.everBackedUp ?: 0,
                        everBackedUpBytes = counts?.everBackedUpBytes ?: 0L,
                        sentElsewhereCount = counts?.sentElsewhere ?: 0,
                        sentTo = sentToByAlbum[name].orEmpty(),
                        // Not on the device, so no folder to resolve against — the fallback is right.
                        backupLocation = prefs.backupLocation
                    )
                }

            _state.value = _state.value.copy(
                folders = folders,
                albums = (albums + archivedButEmpty).sortedBy { it.name.lowercase() },
                isScanning = false
            )
            refreshCounts()

            if (hasNewUploadAlbums && prefs.isAutomaticEnabled) {
                BackupScheduling.enqueueContinuation(
                    WorkManager.getInstance(context),
                    prefs.allowMeteredNetwork
                )
            }
        }
    }

    suspend fun albumEntries(album: String) = entryDao.entriesForAlbum(album)

    /**
     * *Retry failed* on one album: its failed files go back in the queue and a backup starts.
     *
     * Returns how many were put back. Adds work and nothing else. It never touches a file on the phone
     * or in OneDrive, and if a run is already going the files are simply picked up by its next batch.
     */
    suspend fun retryFailed(album: String): Int {
        val n = entryDao.resetFailuresInAlbum(album)
        Logger.i("BackupViewModel", "retry failed: $n files in $album put back in the queue")
        refreshCounts()
        if (n > 0) runBackupNow()
        return n
    }

    /**
     * Keeps a file at full size, or lets it follow its album again.
     *
     * The tick in an album's file list. A pin can only make the app do less to a file, so it is
     * applied at once with no confirmation; see `FilePin`. The counts are refreshed so the album card
     * and the Archive figures agree with the tick by the time the user goes back.
     */
    suspend fun setPinned(entry: BackupEntryEntity, pinned: Boolean) {
        entryDao.setModeOverride(entry.id, FilePin.overrideFor(pinned))
        refresh()
    }

    /** What the Camera control would do at [modifiedBeforeEpochSeconds], under Settings as they are now. */
    fun cameraSettings(state: BackupUiState = _state.value) = CameraOptimiseSettings(
        enabled = state.isOptimiseEnabled,
        photos = state.optimisePhotos,
        videos = state.optimiseVideo,
        photoSavingPercent = ProxyGenerator.APPROXIMATE_SAVING_PERCENT,
        videoSavingPercent = state.videoQuality.approximateSavingPercent
    )

    /** The files [prepareCameraOptimise] asked Android about, held until the person answers. */
    private var pendingCamera: PendingCameraRun? = null

    private data class PendingCameraRun(val album: String, val before: Long)

    /** What tapping *Optimise* on the Camera album led to. */
    sealed interface CameraStart {
        /** Queued. Nothing more to ask. */
        data object Started : CameraStart

        /** Some files sit outside a granted folder: Android's own dialog comes first, then [onCameraConsentGranted]. */
        data class NeedsConsent(val sender: IntentSender) : CameraStart

        /** Nothing on the list is still there to optimise. */
        data object NothingToDo : CameraStart
    }

    /**
     * The tap on *Optimise* in the Camera album.
     *
     * [modifiedBeforeEpochSeconds] is the cutoff the list on screen was drawn with, so the files
     * worked on are the ones the person was looking at. Files inside a folder the app was granted at
     * setup are rewritten with no dialog; any outside one need Android's confirmation first, raised
     * by the screen from [CameraStart.NeedsConsent]. Nothing is removed by either route.
     */
    suspend fun prepareCameraOptimise(album: String, modifiedBeforeEpochSeconds: Long): CameraStart {
        if (!CameraAlbum.isCamera(album)) return CameraStart.NothingToDo
        val plan = CameraOptimisePlan.of(entryDao.entriesForAlbum(album), modifiedBeforeEpochSeconds, cameraSettings())
        val ready = proxyApplier.onDevice(plan.eligible)
        if (ready.isEmpty()) {
            refreshCounts()
            return CameraStart.NothingToDo
        }

        val outside = proxyApplier.splitByConsent(ready).outside
        if (outside.isNotEmpty()) {
            val sender = proxyApplier.createWriteRequest(outside.take(CAMERA_WRITE_REQUEST_LIMIT))
            if (sender != null) {
                pendingCamera = PendingCameraRun(album, modifiedBeforeEpochSeconds)
                return CameraStart.NeedsConsent(sender)
            }
            // No dialog could be built. The files inside a granted folder can still be done, so go on.
        }
        return startCameraOptimise(album, modifiedBeforeEpochSeconds)
    }

    /** Android's dialog was confirmed. */
    fun onCameraConsentGranted() {
        val pending = pendingCamera ?: return
        pendingCamera = null
        viewModelScope.launch { startCameraOptimise(pending.album, pending.before) }
    }

    /** The dialog was dismissed. Nothing was consented to, so nothing is done. */
    fun onCameraConsentDeclined() {
        pendingCamera = null
    }

    private suspend fun startCameraOptimise(album: String, before: Long): CameraStart {
        BackupScheduling.enqueueCameraOptimise(WorkManager.getInstance(context), album, before)
        return CameraStart.Started
    }

    fun setAlbumMode(album: String, mode: AlbumMode) {
        // The Camera album has no Sync (Ian, 20 Sept 2026). The menu does not offer it; this is the
        // second lock, for anything that reaches here by another route.
        if (!CameraAlbum.canChoose(album, mode)) return
        // Same second-lock shape for Google Photos: Sync and Archive are never offered for an album
        // whose folder is routed there. See GooglePhotosDestination.
        val albumLocation = _state.value.albums.firstOrNull { it.name == album }?.backupLocation
            ?: _state.value.backupLocation
        if (!GooglePhotosDestination.canChoose(albumLocation, mode)) return
        viewModelScope.launch {
            albumDao.setPreference(AlbumPreferenceEntity(album, mode))
            // Choosing a mode, Off included, is the answer the notice asks for.
            settings.acknowledgeAlbums(listOf(album))
            // Deliberately leaves any duplicate-name warning in place. Only Dismiss removes it (Ian,
            // 16 Sept 2026). Clearing it here also fired when the chosen mode equalled the current
            // one, which removed the card with no visible change and is the likeliest cause of the
            // card vanishing unasked that evening.
            _state.value = _state.value.copy(
                albums = _state.value.albums.map {
                    if (it.name == album) it.copy(mode = mode) else it
                }
            )
            refreshCounts()

            if (mode.uploads && settings.current().isAutomaticEnabled) {
                BackupScheduling.enqueueContinuation(
                    WorkManager.getInstance(context),
                    settings.current().allowMeteredNetwork
                )
            }

            // Ian, 19 Sept 2026: Automatic optimising runs *"as soon as ... an Album mode is switched to
            // SYNC"*. The run queued above will ask again when it finishes, but files already verified
            // in OneDrive need no run, and waiting for one that has nothing to send would leave them
            // at full size.
            if (mode == AlbumMode.SYNC) startOptimisingIfDue()
        }
    }

    /** Asks both kinds to start if their Settings say they should run on their own. Never fails the caller. */
    private suspend fun startOptimisingIfDue() {
        runCatching { photoOptimise.requestAutomatic() }
        runCatching { videoOptimise.requestAutomatic() }
    }

    /**
     * Turns automatic optimising on or off.
     *
     * Cannot make optimising unattended — Android raises a confirmation dialog for every batch and
     * only an Activity can show it. What this changes is that the app offers when there is
     * something to optimise, instead of waiting to be found in Settings.
     */
    fun setShowEmptyCloudFolders(show: Boolean) {
        viewModelScope.launch { settings.setShowEmptyCloudFolders(show) }
    }

    /** What the Archive tab's age filter starts at. See [ArchiveAge]. */
    fun setArchiveDefaultAge(age: ArchiveAge) {
        viewModelScope.launch { settings.setArchiveDefaultAge(age) }
    }

    /**
     * Turns the "come of age" notification on or off.
     *
     * The permission request itself is the caller's job — `SettingsScreen` asks for
     * `POST_NOTIFICATIONS` before calling this with `true`, and calls it with `false` again if the
     * user declines, so this never persists "on" against a permission that was refused.
     */
    fun setArchiveNotifyEnabled(enabled: Boolean) {
        viewModelScope.launch { settings.setArchiveNotifyEnabled(enabled) }
    }

    fun setOptimiseEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settings.setOptimiseEnabled(enabled)
            startPhotosIfDue()
            startVideoIfDue()
        }
    }

    fun setOptimisePhotos(enabled: Boolean) {
        viewModelScope.launch {
            settings.setOptimisePhotos(enabled)
            startPhotosIfDue()
            refreshCounts()
        }
    }

    fun setPhotoOptimiseMode(mode: OptimiseMode) {
        viewModelScope.launch {
            settings.setPhotoOptimiseMode(mode)
            startPhotosIfDue()
            refreshCounts()
        }
    }

    /**
     * A photo switch just changed. If photos are now due to optimise on their own, start the pass
     * rather than waiting for the next backup run to notice, which could be six hours away. Quiet on
     * failure: the next backup run asks again.
     */
    private suspend fun startPhotosIfDue() {
        runCatching { photoOptimise.requestAutomatic() }
    }

    fun setOptimiseVideo(enabled: Boolean) {
        viewModelScope.launch {
            settings.setOptimiseVideo(enabled)
            startVideoIfDue()
        }
    }

    fun setVideoOptimiseMode(mode: OptimiseMode) {
        viewModelScope.launch {
            settings.setVideoOptimiseMode(mode)
            startVideoIfDue()
        }
    }

    fun setVideoOptimiseAge(age: MediaAge) {
        viewModelScope.launch {
            settings.setVideoOptimiseAge(age)
            startVideoIfDue()
            refreshVideoReadiness()
        }
    }

    /**
     * A switch just changed. If video is now due to optimise on its own, start the chain rather than
     * waiting for the next backup run to notice, which could be six hours away.
     *
     * Failure is quiet: this is a convenience, and the next backup run asks again.
     */
    private suspend fun startVideoIfDue() {
        runCatching { videoOptimise.requestAutomatic() }
        refreshVideoReadiness()
    }

    fun setVideoQuality(quality: VideoQuality) {
        viewModelScope.launch { settings.setVideoQuality(quality) }
    }

    /**
     * Restores the automatic triggers Pause cancelled, if the user still wants them.
     *
     * Reads [BackupPreferences.isAutomaticEnabled] rather than assuming: pausing must not switch
     * automatic sync back on for someone who had deliberately turned it off.
     */
    private suspend fun rearmAutomaticSync() {
        val preferences = settings.current()
        if (preferences.isAutomaticEnabled) {
            BackupScheduling.enable(
                WorkManager.getInstance(context),
                preferences.allowMeteredNetwork
            )
        }
    }

    /**
     * Changes where one top-level folder's *new* uploads go. See TASK-026 and [FolderDestination].
     * Nothing already uploaded moves, and nothing is re-uploaded — each file keeps the `location` it
     * was given when its row was created. Whether [location] is actually available right now (signed
     * in, Pro unlocked, for anything but OneDrive) is the caller's job to check; the Settings list
     * only ever offers what `GooglePhotosUiState.isAvailable` allows.
     *
     * Files still waiting to go follow the new choice (see `BackupEntryDao.retargetUnsent`); only
     * what has already been uploaded keeps its history.
     */
    fun setFolderLocation(folder: String, location: BackupLocation) {
        viewModelScope.launch {
            setFolderDestination(folder, location)
            _state.update { current ->
                current.copy(
                    folders = current.folders.map { if (it.name == folder) it.copy(location = location) else it },
                    albums = current.albums.map {
                        if (it.topLevelFolder == folder) it.copy(backupLocation = location) else it
                    }
                )
            }
        }
    }

    /** Switches every discovered album on or off at once. */
    /**
     * Applies one mode to every album.
     *
     * **Kept although nothing calls it today.** The Select all / Deselect all buttons were removed
     * from the Albums screen on 25 Aug 2026, but this is precisely what TASK-014's Gate 2 needs —
     * the one place a bulk apply is legitimate, because it happens once, after the scan, with the
     * count and the consequence in front of the user. Deleting it as dead code would mean writing
     * it again, and getting the default-mode subtlety below wrong a second time.
     *
     * Selecting uses the user's **default mode for new albums**, not a hardcoded Backup. Those are
     * the same intent — "what I want an album to do unless I say otherwise" — and having them
     * disagree meant someone who chose Sync to reclaim space could tap Select all, get Backup on
     * every album, and never have a single photo optimised. Nothing said so; the two settings just
     * differed.
     *
     * Backup is the fallback when the default is Off, because a Select all that selects nothing is
     * not a control. Archive can never arrive here: `AlbumMode.canBeDefault` excludes it, so no
     * bulk action in the app can arm the mode that removes files — which is the property that makes
     * reading the default safe in the first place.
     */
    fun setAllAlbums(enabled: Boolean) {
        viewModelScope.launch {
            val albums = _state.value.albums
            val mode = if (enabled) AlbumMode.BACKUP else AlbumMode.OFF
            // Camera never takes Sync, so Select all gives it Backup where everything else gets Sync.
            // Same clamp for an album whose folder is routed to Google Photos — see
            // GooglePhotosDestination.
            val locationOf = albums.associate { it.name to it.backupLocation }
            val modeFor = { name: String ->
                GooglePhotosDestination.seeded(
                    locationOf[name] ?: _state.value.backupLocation,
                    CameraAlbum.seeded(name, mode)
                )
            }
            albumDao.setPreferences(albums.map { AlbumPreferenceEntity(it.name, modeFor(it.name)) })
            _state.value = _state.value.copy(
                albums = albums.map { it.copy(mode = modeFor(it.name)) }
            )
            refreshCounts()
        }
    }

    private suspend fun refreshCounts() {
        val redundant = engine.redundantLocalCopies()
        val proxyCandidates = proxyApplier.candidates()

        // Close the run's denominator the moment there is nothing left to do.
        //
        // The worker closes it too, at the end of a run, but that depends on a particular exit path
        // executing — and it did not: the Fold finished a run on 28 Aug 2026 with zero outstanding
        // and a 172 MB baseline still stored, which would have opened the next run part-finished.
        // This asks the only question that matters, wherever the app happens to notice: no work
        // outstanding means no run in progress, so the denominator is meaningless.
        if (entryDao.countPendingInSelectedAlbums(BackupEngine.MAX_ATTEMPTS) == 0 &&
            settings.current().runBaselineBytes != 0L
        ) {
            settings.setRunBaselineBytes(0L)
            _state.value = _state.value.copy(runProgressFloor = 0f)
            lastCompletedSeen = -1
        }

        _state.value = _state.value.copy(
            hasLoadedCounts = true,
            uploadedCount = entryDao.countInState(BackupState.UPLOADED),
            uploadedBytes = entryDao.uploadedBytesInSelectedAlbums(),
            pendingCount = entryDao.countPendingInSelectedAlbums(BackupEngine.MAX_ATTEMPTS),
            pendingBytes = entryDao.pendingBytesInSelectedAlbums(),
            redundantCount = redundant.size,
            redundantBytes = redundant.sumOf { it.sizeBytes },
            archiveAlbumsReady = redundant.map { it.album }.distinct().sorted(),
            canRemoveLocalCopies = localCopyRemover.isSupported(),
            proxyCandidateCount = proxyCandidates.size,
            canProxy = proxyApplier.isSupported()
        )
        refreshVideoReadiness()
    }

    /**
     * What the video optimiser could act on, read only while the switches say it may be wanted.
     *
     * Asked separately from the rest of the counts because it does a MediaStore lookup per candidate
     * clip, which is wasted on someone who has video optimising off.
     */
    private suspend fun refreshVideoReadiness() {
        val prefs = settings.current()
        val ready = if (prefs.isOptimiseEnabled && prefs.optimiseVideo) {
            videoOptimiser.readiness()
        } else {
            VideoReadiness()
        }
        _state.update {
            it.copy(
                videoCandidateCount = ready.count,
                manualOptimiseWaiting = OptimiseOnSyncNow.isWaiting(
                    setupComplete = prefs.hasCompletedSetup,
                    optimiseEnabled = prefs.isOptimiseEnabled,
                    optimisePhotos = prefs.optimisePhotos,
                    photoMode = prefs.photoOptimiseMode,
                    optimiseVideo = prefs.optimiseVideo,
                    videoMode = prefs.videoOptimiseMode,
                    photosReady = it.proxyCandidateCount,
                    videoReady = ready.count
                )
            )
        }
    }

    /**
     * Asks Android for permission to rewrite the photos that would be optimised.
     *
     * The candidates are captured here and reused by [onProxyConsentGranted], so the set that was
     * consented to is exactly the set that gets rewritten — re-querying afterwards could act on
     * files the user never saw in the dialog.
     */
    suspend fun buildProxyWriteRequest(): IntentSender? {
        // Only what the background pass cannot do. Photos inside a granted folder are rewritten by
        // `OptimiseWorker` with no dialog, so they are neither asked about nor rewritten here. Ian,
        // 19 Sept 2026: Automatic means as soon as a file arrives, which a dialog can never be.
        val outside = proxyApplier.splitByConsent(proxyApplier.candidates()).outside
        pendingProxyCandidates = outside
        if (outside.isEmpty()) return null

        val sender = proxyApplier.createWriteRequest(outside)

        // The count shown may have been built before files moved underneath it; put the screen
        // back in step with what is actually eligible now.
        if (sender == null) refreshCounts()

        return sender
    }

    /** The screen has seen the request that Sync now finished and is about to ask, so it is not asked twice. */
    fun consumeProxyDialogRequest() {
        _state.update { it.copy(proxyDialogRequested = false) }
    }

    fun onProxyConsentGranted() = applyPendingProxies()

    /**
     * Rewrites the captured candidates.
     *
     * Shared by both routes so the set that was decided on is exactly the set that is rewritten,
     * whether the decision came from Android's dialog or from a tree grant the user gave at Gate 1.
     */
    private fun applyPendingProxies() {
        val candidates = pendingProxyCandidates
        if (candidates.isEmpty()) return

        viewModelScope.launch {
            when (val outcome = proxyApplier.apply(candidates)) {
                is ProxyOutcome.Completed ->
                    Logger.i("BackupViewModel", "optimised ${outcome.proxiedCount} photos, freed ${outcome.bytesReclaimed} bytes")

                is ProxyOutcome.Stopped ->
                    Logger.w("BackupViewModel", "optimising stopped at ${outcome.failedFile}: ${outcome.reason}")

                ProxyOutcome.NothingToDo, ProxyOutcome.NotSupported -> Unit
            }

            pendingProxyCandidates = emptyList()
            refresh()
        }
    }

    /**
     * Builds the system request to move redundant local copies into the gallery's trash.
     *
     * ### OneDrive is asked again, here, every time
     *
     * `redundantLocalCopies` reads the ledger, which records that a copy was confirmed *once*. That
     * is not the same claim as "there is a copy now", and removal is the one place where only the
     * second will do — a file deleted from OneDrive by hand leaves a row insisting it is safe
     * forever, with nothing anywhere to notice.
     *
     * So the drive is re-listed before anything is offered for removal, and only files it confirms
     * right now are included. Files it cannot vouch for are dropped from the request and reported;
     * **being unable to ask is never treated as a yes.**
     *
     * Returns null when nothing survives the check. The caller launches the request, and Android —
     * not this app — asks the user to confirm.
     */
    suspend fun buildMoveToBackupRequest(): IntentSender? {
        val redundant = engine.redundantLocalCopies()
        if (redundant.isEmpty()) return null

        val confirmation = engine.confirmStillInCloud(redundant)
        _state.value = _state.value.copy(removalHeldBack = confirmation)

        if (confirmation.confirmed.isEmpty()) {
            Logger.w("BackupViewModel", "not removing: OneDrive confirmed none of ${redundant.size}")
            return null
        }

        return localCopyRemover.createMoveToBackupRequest(
            confirmation.confirmed.map { it.contentUri }
        )
    }

    /** Called after the system dialog closes, to reflect whatever the user allowed. */
    fun onMoveToBackupFinished() {
        viewModelScope.launch { refresh() }
    }

    /**
     * Runs one backup pass now.
     *
     * Manual rather than scheduled while the feature is being proven: a run should happen because
     * someone asked for it, not as a side effect of installing a build.
     */
    /**
     * Starts the chain the user asked for, and lets it run to completion.
     *
     * Enqueued rather than run here. `viewModelScope` dies with the screen, and a run to completion
     * over a real library is hours — but the deeper reason is that this used to call the engine
     * directly and so skipped everything `BackupWorker` does around a run. It uploaded one
     * byte-budget batch, scheduled nothing, and stopped: 512 MB per press, against a 148 GB library,
     * during exactly the period when automatic runs are gated. See FIX-001.
     *
     * One host now. Chaining and bookkeeping happen once, in the worker, for both callers.
     */
    /**
     * Raises the floor to whatever progress currently reads, and never lowers it.
     *
     * Reset when a run ends or a new baseline is opened — see [runBackupNow] and [stopBackup].
     */
    private fun holdProgressFloor() {
        val now = _state.value.runProgress ?: return
        if (now > _state.value.runProgressFloor) {
            _state.value = _state.value.copy(runProgressFloor = now)
        }
    }

    
    fun runBackupNow() {
        // A new run reports its own progress from zero, so last run's high-water mark must go.
        _state.value = _state.value.copy(runProgressFloor = 0f)
        lastCompletedSeen = -1
        if (_state.value.isRunning) return
        viewModelScope.launch {
            val prefs = settings.current()
            offerProxyDialogAfterRun = PhotoOptimisePolicy.runsOnSyncNow(
                prefs.hasCompletedSetup, prefs.isOptimiseEnabled, prefs.optimisePhotos, prefs.photoOptimiseMode
            )
            BackupScheduling.enqueueManualRun(
                WorkManager.getInstance(context),
                settings.current().allowMeteredNetwork
            )
        }
    }

    /**
     * Stops the chain, including the batch in flight.
     *
     * Cancelling the work cancels the coroutine running the engine, and `ChunkedUploader` checks
     * `ensureActive()` on every chunk — so a stopped upload stops pushing bytes rather than
     * finishing the file it was on. What it has already sent is not wasted: the resumable session
     * is on the ledger row, so the next run continues from that offset.
     */
    /**
     * Holds backing up until Resume or Stop.
     *
     * Cancels what is running *and* records the intent, because cancelling alone lasts until the
     * next trigger. Nothing is lost by stopping mid-file: `ChunkedUploader` resumes from the offset
     * Graph reports accepted, proven at 1,938 MB on the Fold 4.
     */
    fun pauseBackup() {
        viewModelScope.launch {
            settings.setPaused(true)
            val workManager = WorkManager.getInstance(context)

            // Every chain, not just the manual one. Cancelling only MANUAL_WORK left an automatic
            // run going until its current file finished, because nothing was cancelled and the
            // pause took hold only when the worker next declined — observed on the Moto G, 28 Aug
            // 2026, and precisely the "finish the file first" behaviour we decided against.
            BackupScheduling.cancelManualRun(workManager)
            BackupScheduling.disable(workManager)

            // The session is kept, not discarded, and stamped instead. A pause attended to within
            // ten minutes resumes from the accepted offset for free; one walked away from is
            // discarded before the next run. See BackupEngine.discardStaleUploadSessions.
            settings.setUploadInterruptedAt(System.currentTimeMillis())
        }
    }

    /** Lifts the hold and starts a run now, rather than waiting for the next trigger. */
    fun resumeBackup() {
        viewModelScope.launch {
            settings.setPaused(false)
            // Attended to, so the clock stops. The worker still judges the session's age before
            // using it, which is what makes a resume after a reboot behave the same as one now.
            settings.setUploadInterruptedAt(0L)
            rearmAutomaticSync()
            runBackupNow()
        }
    }

    /**
     * Ends this run and hands control back to automatic sync.
     *
     * Clears the paused flag as well, because "go back to normal" is exactly what it means — so
     * pressing Stop while paused releases the hold without starting anything. The next trigger runs
     * as it always would.
     */
    fun stopBackup() {
        viewModelScope.launch {
            settings.setPaused(false)
            settings.setUploadInterruptedAt(0L)
            // Ends the run, so the next opens its own denominator rather than inheriting this one's
            // and appearing to start part-finished.
            settings.setRunBaselineBytes(0L)
            _state.value = _state.value.copy(runProgressFloor = 0f)
            lastCompletedSeen = -1
            // Ends the run but leaves automatic sync armed — that is the whole difference from
            // Pause. Re-arming is needed because Pause may have torn the triggers down.
            rearmAutomaticSync()
        }
        BackupScheduling.cancelManualRun(WorkManager.getInstance(context))
    }

    /**
     * Mirrors the manual chain's work state into [BackupUiState].
     *
     * The worker publishes per-file progress through `setProgress`, so the screen says the same
     * thing whether the run was started by a person or by the scheduler — which it could not do
     * while the two callers drove two different code paths.
     */
    private fun observeManualRun() {
        viewModelScope.launch {
            WorkManager.getInstance(context)
                .getWorkInfosForUniqueWorkFlow(BackupScheduling.MANUAL_WORK)
                .collect { infos ->
                    val info = infos.lastOrNull()
                    val running = info?.state == WorkInfo.State.RUNNING ||
                        info?.state == WorkInfo.State.ENQUEUED

                    val status = when (info?.state) {
                        WorkInfo.State.RUNNING -> {
                            val total = info.progress.getInt(BackupWorker.PROGRESS_TOTAL, 0)
                            if (total > 0) {
                                BackupStatus.Uploading(
                                    completed = info.progress.getInt(
                                        BackupWorker.PROGRESS_COMPLETED, 0
                                    ),
                                    total = total,
                                    currentFile = info.progress.getString(
                                        BackupWorker.PROGRESS_FILE
                                    ).orEmpty(),
                                    percentOfCurrent = info.progress.getInt(
                                        BackupWorker.PROGRESS_PERCENT, 0
                                    )
                                )
                            } else {
                                BackupStatus.Scanning
                            }
                        }

                        // Enqueued but not started is a real state and has to say so: network
                        // constraints still apply to a manual run, so on mobile data with Wi-Fi
                        // only selected this can wait. A button that looks idle while work is
                        // pending is the defect this app kept producing.
                        WorkInfo.State.ENQUEUED -> BackupStatus.Scanning

                        // The outcome, not the last thing we happened to see. Keeping the previous
                        // status here left "Uploading 2 of 16" on screen after a run stopped on a
                        // full drive — a claim that was false, hiding the one fact the user could
                        // act on. Observed 26 Aug 2026.
                        WorkInfo.State.SUCCEEDED,
                        WorkInfo.State.FAILED -> info.outputData.toFinishedStatus()
                            ?: _state.value.status

                        // Stopped by the user. They know why; saying nothing is honest, and the
                        // counts underneath have already updated.
                        WorkInfo.State.CANCELLED -> null

                        else -> _state.value.status
                    }

                    runningWork.update { live ->
                        if (running) live + BackupScheduling.MANUAL_WORK
                        else live - BackupScheduling.MANUAL_WORK
                    }
                    // Zero is meaningful here — it means no file is in flight — but a *finished*
                    // WorkInfo carries empty progress, and reading that would clear the figure the
                    // other chain just published. Only a running one speaks.
                    val sent = info
                        ?.takeIf { it.state == WorkInfo.State.RUNNING }
                        ?.progress
                        ?.getLong(BackupWorker.PROGRESS_CURRENT_SENT, 0L)

                    _state.value = _state.value.copy(
                        status = status,
                        currentBytesSent = sent ?: _state.value.currentBytesSent
                    )
                    holdProgressFloor()

                    // A file finishing is what makes pendingBytes stale, so that is the moment to
                    // re-read it rather than waiting for the run to end.
                    val done = info?.progress?.getInt(BackupWorker.PROGRESS_COMPLETED, 0) ?: 0
                    if (done != lastCompletedSeen) {
                        lastCompletedSeen = done
                        refreshCounts()
                    }
                    if (info?.state?.isFinished == true) {
                        refresh()
                        // Sync now has done what it can without a dialog. Photos outside the granted
                        // folders are asked about now, while the app is on screen to show it.
                        if (info.state == WorkInfo.State.SUCCEEDED && offerProxyDialogAfterRun) {
                            offerProxyDialogAfterRun = false
                            _state.update { it.copy(proxyDialogRequested = true) }
                        }
                    }
                }
        }
    }
}

/**
 * The worker's outcome, or null when this work carried none — a run cancelled before it finished,
 * or an older enqueue from before outcomes were published.
 */
private fun androidx.work.Data.toFinishedStatus(): BackupStatus.Finished? {
    if (!keyValueMap.containsKey(BackupWorker.RESULT_REMAINING)) return null
    return BackupStatus.Finished(
        uploaded = getInt(BackupWorker.RESULT_UPLOADED, 0),
        skipped = getInt(BackupWorker.RESULT_SKIPPED, 0),
        failed = getInt(BackupWorker.RESULT_FAILED, 0),
        deferred = getInt(BackupWorker.RESULT_DEFERRED, 0),
        pruned = getInt(BackupWorker.RESULT_PRUNED, 0),
        remaining = getInt(BackupWorker.RESULT_REMAINING, 0),
        stoppedBecause = getString(BackupWorker.RESULT_STOPPED)?.let {
            runCatching { StopReason.valueOf(it) }.getOrNull()
        }
    )
}

/** MediaStore's own cap on one write request, and so on how many files one Camera dialog can cover. */
private const val CAMERA_WRITE_REQUEST_LIMIT = 2000
