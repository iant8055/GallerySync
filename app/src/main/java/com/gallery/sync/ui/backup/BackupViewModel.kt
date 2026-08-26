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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

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
    val proxiedCount: Int = 0
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
    val showEmptyCloudFolders: Boolean = false

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

    /** A run would do something. Drives whether the button is worth pressing. */
    val canRunBackup: Boolean get() = !isRunning && pendingCount > 0
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

    private val _state = MutableStateFlow(BackupUiState())
    val state: StateFlow<BackupUiState> = _state.asStateFlow()

    init {
        refresh()
        viewModelScope.launch {
            settings.preferences.collect { prefs ->
                _state.value = _state.value.copy(
                    isAutomaticEnabled = prefs.isAutomaticEnabled,
                    isAutoOptimiseEnabled = prefs.isAutoOptimiseEnabled,
                    allowMeteredNetwork = prefs.allowMeteredNetwork,
                    showEmptyCloudFolders = prefs.showEmptyCloudFolders,
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
        for (name in workNames) {
            viewModelScope.launch {
                workManager.getWorkInfosForUniqueWorkFlow(name).collectLatest { infos ->
                    if (_state.value.isRunning) return@collectLatest

                    val running = infos.firstOrNull { it.state == WorkInfo.State.RUNNING }
                    if (running != null) {
                        val data = running.progress
                        val total = data.getInt(BackupWorker.PROGRESS_TOTAL, 0)
                        if (total > 0) {
                            _state.value = _state.value.copy(
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
                    proxiedCount = counts?.proxied ?: 0
                )
            }

            _state.value = _state.value.copy(albums = albums, isScanning = false)
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

    fun onProxyConsentGranted() {
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
    fun runBackupNow() {
        if (_state.value.isRunning) return

        viewModelScope.launch {
            _state.value = _state.value.copy(isRunning = true, status = BackupStatus.Scanning)

            val seen = engine.refreshLedger()
            if (seen == null) {
                _state.value = _state.value.copy(
                    isRunning = false,
                    status = BackupStatus.NoPermission
                )
                return@launch
            }

            val result = engine.uploadPending { progress ->
                _state.value = _state.value.copy(
                    status = BackupStatus.Uploading(
                        completed = progress.completed,
                        total = progress.total,
                        currentFile = progress.currentFile,
                        percentOfCurrent = if (progress.currentBytesTotal > 0) {
                            ((progress.currentBytesSent * 100) / progress.currentBytesTotal)
                                .toInt()
                                .coerceIn(0, 100)
                        } else {
                            0
                        }
                    )
                )
            }

            _state.value = _state.value.copy(
                isRunning = false,
                status = BackupStatus.Finished(
                    uploaded = result.uploaded,
                    skipped = result.skipped,
                    failed = result.failed,
                    deferred = result.deferred,
                    pruned = result.pruned,
                    remaining = result.remaining,
                    stoppedBecause = result.stoppedBecause
                )
            )
            refresh()
        }
    }
}
