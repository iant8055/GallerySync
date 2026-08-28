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
import com.gallery.sync.data.local.media.ProxyOutcome
import com.gallery.sync.data.local.settings.BackupSettings
import com.gallery.sync.worker.BackupScheduling
import com.gallery.sync.worker.BackupWorker
import dagger.hilt.android.qualifiers.ApplicationContext
import com.gallery.sync.data.local.dao.AlbumPreferenceDao
import com.gallery.sync.data.local.dao.BackupEntryDao
import com.gallery.sync.data.local.entity.AlbumMode
import com.gallery.sync.data.local.entity.AlbumPreferenceEntity
import com.gallery.sync.data.local.entity.BackupState
import com.gallery.sync.data.local.media.MediaAccess
import com.gallery.sync.data.local.media.MediaScanner
import com.gallery.sync.domain.backup.BackupEngine
import com.gallery.sync.domain.backup.CloudConfirmation
import com.gallery.sync.domain.backup.StopReason
import com.gallery.sync.util.Logger
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
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
data class AlbumRow(
    val name: String,
    val itemCount: Int,
    val totalBytes: Long,
    /** What the user chose for this album. [AlbumMode.OFF] means finished or ignored. */
    val mode: AlbumMode,
    val backedUpCount: Int = 0,
    val proxiedCount: Int = 0,
    val imageCount: Int = 0,
    val videoCount: Int = 0,
    /** What optimising reclaimed in this album. Zero unless something here has been proxied. */
    val savedBytes: Long = 0L,
    /** Uploaded rows including files no longer on the phone. Only for [isArchivedAndEmpty]. */
    val everBackedUpCount: Int = 0,

    /** What those uploaded rows occupy in OneDrive — the only non-zero size an archived album has. */
    val everBackedUpBytes: Long = 0L
) {
    val isEnabled: Boolean get() = mode.uploads

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
     * that are no longer on the phone — "1 backed up · 12 optimized" over an album holding nothing.
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
    val proxyCandidateBytes: Long = 0L,
    val canProxy: Boolean = false,
    val proxyStatus: ProxyStatus? = null,
    val defaultAlbumMode: AlbumMode = AlbumMode.DEFAULT,
    /** Whether the restore screen lists cloud folders that hold nothing. */
    val showEmptyCloudFolders: Boolean = false,
    /**
     * The user has held backing up until they say otherwise.
     *
     * Distinct from "not running". A paused app is deliberately idle and stays that way through
     * every automatic trigger; an idle one is simply waiting for the next.
     */
    val isPaused: Boolean = false,
    /** Bytes outstanding when this run began. The denominator for [runProgress]. */
    val runBaselineBytes: Long = 0L,
    /** Bytes moved so far by the live run, published by the worker as it goes. */
    val runBytesSent: Long = 0L

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

            // The worker's live figure while it is running, because the ledger-derived one only
            // moves when counts are refreshed — which is between runs, so the percentage sat
            // still through an entire transfer and jumped on Pause.
            //
            // The ledger is still the answer when nothing is publishing: after a pause, a crash or
            // a restart there is no live figure, and what has actually been recorded is the honest
            // fallback.
            val moved = if (isRunning && runBytesSent > 0L) {
                runBytesSent
            } else {
                (runBaselineBytes - pendingBytes).coerceAtLeast(0L)
            }
            return (moved.toFloat() / runBaselineBytes).coerceIn(0f, 1f)
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
}

/** What optimising photos is doing, or what it did. */
sealed interface ProxyStatus {

    data object Working : ProxyStatus

    data class Done(val proxiedCount: Int, val bytesReclaimed: Long) : ProxyStatus

    /** Stopped at a file that would not replace even after retries. */
    data class Stopped(
        val proxiedCount: Int,
        val bytesReclaimed: Long,
        val failedFile: String,
        val reason: String
    ) : ProxyStatus

    /**
     * Android refused to produce the consent dialog, so nothing was even asked.
     *
     * Worth its own state: without it the button is pressed, no dialog appears, no message
     * appears, and the app looks broken with nothing to act on.
     */
    data object CouldNotAsk : ProxyStatus
}

@HiltViewModel
class BackupViewModel @Inject constructor(
    private val scanner: MediaScanner,
    private val albumDao: AlbumPreferenceDao,
    private val entryDao: BackupEntryDao,
    private val engine: BackupEngine,
    private val localCopyRemover: LocalCopyRemover,
    private val proxyApplier: ProxyApplier,
    private val settings: BackupSettings,
    @param:ApplicationContext private val context: Context
) : ViewModel() {

    /** Captured when consent is requested, so exactly that set is what gets rewritten. */
    private var pendingProxyCandidates: List<BackupEntryEntity> = emptyList()

    /**
     * Which backup chains are live, by unique work name.
     *
     * One set rather than one boolean per collector: four flows each writing `isRunning` would
     * race, and the last to emit would win regardless of what the others were doing.
     */
    private val runningWork = MutableStateFlow(emptySet<String>())

    private val _state = MutableStateFlow(BackupUiState())
    val state: StateFlow<BackupUiState> = _state.asStateFlow()

    init {
        refresh()
        observeManualRun()
        viewModelScope.launch {
            settings.preferences.collect { prefs ->
                _state.value = _state.value.copy(
                    isAutomaticEnabled = prefs.isAutomaticEnabled,
                    isAutoOptimiseEnabled = prefs.isAutoOptimiseEnabled,
                    allowMeteredNetwork = prefs.allowMeteredNetwork,
                    showEmptyCloudFolders = prefs.showEmptyCloudFolders,
                    isPaused = prefs.isPaused,
                    runBaselineBytes = prefs.runBaselineBytes,
                    defaultAlbumMode = prefs.defaultAlbumMode
                )
            }
        }
        observeBackgroundWork()
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
                                runBytesSent = data.getLong(BackupWorker.PROGRESS_RUN_BYTES, 0L),
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
            val prefs = settings.current()
            val defaultMode = prefs.defaultAlbumMode

            var hasNewUploadAlbums = false
            val albums = scanner.scanAlbums().map { album ->
                val counts = countsByAlbum[album.name]
                // No write here any more — [BackupEngine.refreshLedger] seeds the row. The fallback
                // remains only for an album the scanner reports that the ledger has not recorded,
                // which the screen should still render rather than skip.
                val mode = storedModes[album.name] ?: defaultMode
                if (album.name !in knownBefore && mode.uploads) hasNewUploadAlbums = true
                AlbumRow(
                    name = album.name,
                    itemCount = album.itemCount,
                    totalBytes = album.totalBytes,
                    mode = mode,
                    backedUpCount = counts?.backedUp ?: 0,
                    proxiedCount = counts?.proxied ?: 0,
                    imageCount = album.imageCount,
                    videoCount = album.videoCount,
                    savedBytes = counts?.savedBytes ?: 0L,
                    everBackedUpCount = counts?.everBackedUp ?: 0,
                    everBackedUpBytes = counts?.everBackedUpBytes ?: 0L
                )
            }

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
                        proxiedCount = counts?.proxied ?: 0,
                        savedBytes = counts?.savedBytes ?: 0L,
                        everBackedUpCount = counts?.everBackedUp ?: 0,
                        everBackedUpBytes = counts?.everBackedUpBytes ?: 0L,
                    )
                }

            _state.value = _state.value.copy(
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

    fun setAlbumMode(album: String, mode: AlbumMode) {
        viewModelScope.launch {
            albumDao.setPreference(AlbumPreferenceEntity(album, mode))
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
        }
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

    fun setAutoOptimiseEnabled(enabled: Boolean) {
        viewModelScope.launch { settings.setAutoOptimiseEnabled(enabled) }
    }

    /**
     * Reopens guided setup.
     *
     * Clears only the completion flag, which is what [com.gallery.sync.MainActivity] reads to
     * decide between the wizard and the tabs. Deliberately touches nothing else, per TASK-014:
     *
     * - **Current values, not defaults.** The wizard reads live preferences, so it opens on what is
     *   set today. A setup flow that reset the configuration it exists to adjust would be a trap,
     *   and the destructive settings are the ones it would reset.
     * - **No silent re-apply.** Gate 2's bulk mode change is UI state starting at "choose per
     *   album", so walking the flow again changes no album unless the user picks again.
     * - **Acknowledgements survive.** The record is additive and nothing clears it. Making someone
     *   re-acknowledge Archive to change a folder would devalue the acknowledgement, which works
     *   only while it stays rare.
     */
    fun restartSetup() {
        viewModelScope.launch { settings.setSetupCompleted(false) }
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

    fun setDefaultAlbumMode(mode: AlbumMode) {
        viewModelScope.launch { settings.setDefaultAlbumMode(mode) }
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
            val preferred = _state.value.defaultAlbumMode
                .takeIf { it != AlbumMode.OFF }
                ?: AlbumMode.BACKUP
            val mode = if (enabled) preferred else AlbumMode.OFF
            albumDao.setPreferences(albums.map { AlbumPreferenceEntity(it.name, mode) })
            _state.value = _state.value.copy(
                albums = albums.map { it.copy(mode = mode) }
            )
            refreshCounts()
        }
    }

    private suspend fun refreshCounts() {
        val redundant = engine.redundantLocalCopies()
        val proxyCandidates = proxyApplier.candidates()

        _state.value = _state.value.copy(
            hasLoadedCounts = true,
            uploadedCount = entryDao.countInState(BackupState.UPLOADED),
            uploadedBytes = entryDao.uploadedBytesInSelectedAlbums(),
            pendingCount = entryDao.countPendingInSelectedAlbums(),
            pendingBytes = entryDao.pendingBytesInSelectedAlbums(),
            redundantCount = redundant.size,
            redundantBytes = redundant.sumOf { it.sizeBytes },
            archiveAlbumsReady = redundant.map { it.album }.distinct().sorted(),
            canRemoveLocalCopies = localCopyRemover.isSupported(),
            proxyCandidateCount = proxyCandidates.size,
            proxyCandidateBytes = proxyCandidates.sumOf { it.sizeBytes },
            canProxy = proxyApplier.isSupported()
        )
    }

    /**
     * Asks Android for permission to rewrite the photos that would be optimised.
     *
     * The candidates are captured here and reused by [onProxyConsentGranted], so the set that was
     * consented to is exactly the set that gets rewritten — re-querying afterwards could act on
     * files the user never saw in the dialog.
     */
    suspend fun buildProxyWriteRequest(): IntentSender? {
        pendingProxyCandidates = proxyApplier.candidates()

        // No dialog when every candidate sits inside a granted SAF tree — the grant already carries
        // write permission, verified on hardware 19 Aug 2026. Returning null here means the caller
        // proceeds straight to the rewrite, which is what makes "optimise automatically" mean what
        // its name says rather than "ask me about it automatically".
        if (!proxyApplier.needsWriteRequest(pendingProxyCandidates)) {
            Logger.i("BackupViewModel", "optimising ${pendingProxyCandidates.size} files through the tree grant")
            applyPendingProxies()
            return null
        }

        val sender = proxyApplier.createWriteRequest(pendingProxyCandidates)

        // Clears any earlier result on success, so the dialog is not shown over a stale message.
        _state.value = _state.value.copy(
            proxyStatus = if (sender == null) ProxyStatus.CouldNotAsk else null
        )

        // The count shown may have been built before files moved underneath it; put the screen
        // back in step with what is actually eligible now.
        if (sender == null) refreshCounts()

        return sender
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
            _state.value = _state.value.copy(proxyStatus = ProxyStatus.Working)

            val status = when (val outcome = proxyApplier.apply(candidates)) {
                is ProxyOutcome.Completed ->
                    ProxyStatus.Done(outcome.proxiedCount, outcome.bytesReclaimed)

                is ProxyOutcome.Stopped -> ProxyStatus.Stopped(
                    proxiedCount = outcome.proxiedCount,
                    bytesReclaimed = outcome.bytesReclaimed,
                    failedFile = outcome.failedFile,
                    reason = outcome.reason
                )

                ProxyOutcome.NothingToDo, ProxyOutcome.NotSupported ->
                    ProxyStatus.Done(0, 0L)
            }

            pendingProxyCandidates = emptyList()
            _state.value = _state.value.copy(proxyStatus = status)
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
    fun runBackupNow() {
        if (_state.value.isRunning) return
        viewModelScope.launch {
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
                    _state.value = _state.value.copy(
                        status = status,
                        runBytesSent = info?.progress
                            ?.getLong(BackupWorker.PROGRESS_RUN_BYTES, 0L)
                            ?: _state.value.runBytesSent
                    )
                    if (info?.state?.isFinished == true) refresh()
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
