package com.gallery.sync.ui.backup

import android.content.Context
import android.content.IntentSender
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkManager
import com.gallery.sync.data.local.entity.BackupEntryEntity
import com.gallery.sync.data.local.media.LocalCopyRemover
import com.gallery.sync.data.local.media.ProxyApplier
import com.gallery.sync.data.local.media.ProxyOutcome
import com.gallery.sync.data.local.settings.BackupSettings
import com.gallery.sync.worker.BackupScheduling
import dagger.hilt.android.qualifiers.ApplicationContext
import com.gallery.sync.data.local.dao.AlbumPreferenceDao
import com.gallery.sync.data.local.dao.BackupEntryDao
import com.gallery.sync.data.local.entity.AlbumPreferenceEntity
import com.gallery.sync.data.local.entity.BackupState
import com.gallery.sync.data.local.media.MediaAccess
import com.gallery.sync.data.local.media.MediaScanner
import com.gallery.sync.domain.backup.BackupEngine
import com.gallery.sync.domain.backup.StopReason
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * One album: how much of it is safe, and whether it is still being watched.
 *
 * [isEnabled] and [backedUpCount] are deliberately independent. An archived album that will
 * never gain another photo is *finished*, not *unprotected* — switching it off means "stop
 * spending time on this", and rendering that the same as "not backed up" is alarming and wrong.
 */
data class AlbumRow(
    val name: String,
    val itemCount: Int,
    val totalBytes: Long,
    /** Watched for new files. Off means finished or ignored — [status] says which. */
    val isEnabled: Boolean,
    val backedUpCount: Int = 0
) {
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

    data object Uploading : BackupStatus

    data object NoPermission : BackupStatus

    data class Finished(
        val uploaded: Int,
        val skipped: Int,
        val failed: Int,
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
    val uploadedCount: Int = 0,
    /** Outstanding files **within the selected albums** — not the whole library. */
    val pendingCount: Int = 0,
    /** Local copies made redundant by a confirmed cloud copy, and what they occupy. */
    val redundantCount: Int = 0,
    val redundantBytes: Long = 0L,
    /** False below API 30, where Android has no media trash and removal could only be permanent. */
    val canRemoveLocalCopies: Boolean = false,
    /** Back up on its own when new photos appear. */
    val isAutomaticEnabled: Boolean = false,
    /** Allow automatic runs on mobile data, not just Wi-Fi. */
    val allowMeteredNetwork: Boolean = false,
    /** Photos whose local copy could be replaced by a proxy, and what they occupy now. */
    val proxyCandidateCount: Int = 0,
    val proxyCandidateBytes: Long = 0L,
    val canProxy: Boolean = false,
    val proxyStatus: ProxyStatus? = null
) {
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
                    allowMeteredNetwork = prefs.allowMeteredNetwork
                )
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

            // Bring the ledger up to date before counting. Without this the counts describe
            // whatever the last run happened to see, and the screen would claim there is nothing
            // to do simply because nothing has scanned yet.
            engine.refreshLedger()

            val disabled = albumDao.disabledAlbums().toSet()
            val backedUpByAlbum = entryDao.albumCounts().associate { it.album to it.backedUp }

            val albums = scanner.scanAlbums().map { album ->
                AlbumRow(
                    name = album.name,
                    itemCount = album.itemCount,
                    totalBytes = album.totalBytes,
                    isEnabled = album.name !in disabled,
                    backedUpCount = backedUpByAlbum[album.name] ?: 0
                )
            }

            _state.value = _state.value.copy(albums = albums, isScanning = false)
            refreshCounts()
        }
    }

    fun setAlbumEnabled(album: String, enabled: Boolean) {
        viewModelScope.launch {
            albumDao.setPreference(AlbumPreferenceEntity(album, enabled))
            _state.value = _state.value.copy(
                albums = _state.value.albums.map {
                    if (it.name == album) it.copy(isEnabled = enabled) else it
                }
            )
            // Changing the selection changes what is outstanding, so the counts must follow.
            refreshCounts()
        }
    }

    /** Switches every discovered album on or off at once. */
    fun setAllAlbums(enabled: Boolean) {
        viewModelScope.launch {
            val albums = _state.value.albums
            albumDao.setPreferences(albums.map { AlbumPreferenceEntity(it.name, enabled) })
            _state.value = _state.value.copy(
                albums = albums.map { it.copy(isEnabled = enabled) }
            )
            refreshCounts()
        }
    }

    private suspend fun refreshCounts() {
        val redundant = engine.redundantLocalCopies()
        val proxyCandidates = proxyApplier.candidates()

        _state.value = _state.value.copy(
            uploadedCount = entryDao.countInState(BackupState.UPLOADED),
            pendingCount = entryDao.countPendingInSelectedAlbums(),
            redundantCount = redundant.size,
            redundantBytes = redundant.sumOf { it.sizeBytes },
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
        return proxyApplier.createWriteRequest(pendingProxyCandidates)
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
     * Returns null when there is nothing to move. The caller launches it, and Android asks the
     * user to confirm — this app never removes anything silently.
     */
    suspend fun buildMoveToBackupRequest(): IntentSender? {
        val redundant = engine.redundantLocalCopies()
        return localCopyRemover.createMoveToBackupRequest(redundant.map { it.contentUri })
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

            _state.value = _state.value.copy(status = BackupStatus.Uploading)
            val result = engine.uploadPending()

            _state.value = _state.value.copy(
                isRunning = false,
                status = BackupStatus.Finished(
                    uploaded = result.uploaded,
                    skipped = result.skipped,
                    failed = result.failed,
                    pruned = result.pruned,
                    remaining = result.remaining,
                    stoppedBecause = result.stoppedBecause
                )
            )
            refreshCounts()
        }
    }
}
