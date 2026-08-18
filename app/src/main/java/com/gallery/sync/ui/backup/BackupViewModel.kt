package com.gallery.sync.ui.backup

import android.content.IntentSender
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gallery.sync.data.local.media.LocalCopyRemover
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

/** One album, and whether the user wants it backed up. */
data class AlbumRow(
    val name: String,
    val itemCount: Int,
    val totalBytes: Long,
    val isEnabled: Boolean
)

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
    val canRemoveLocalCopies: Boolean = false
) {
    /** Files that would be sent if a run started now. */
    val enabledItemCount: Int get() = albums.filter { it.isEnabled }.sumOf { it.itemCount }

    val enabledBytes: Long get() = albums.filter { it.isEnabled }.sumOf { it.totalBytes }

    /** Something selected, and all of it already in OneDrive. */
    val isSelectionFullyBackedUp: Boolean get() = enabledItemCount > 0 && pendingCount == 0

    /** A run would do something. Drives whether the button is worth pressing. */
    val canRunBackup: Boolean get() = !isRunning && pendingCount > 0
}

@HiltViewModel
class BackupViewModel @Inject constructor(
    private val scanner: MediaScanner,
    private val albumDao: AlbumPreferenceDao,
    private val entryDao: BackupEntryDao,
    private val engine: BackupEngine,
    private val localCopyRemover: LocalCopyRemover
) : ViewModel() {

    private val _state = MutableStateFlow(BackupUiState())
    val state: StateFlow<BackupUiState> = _state.asStateFlow()

    init {
        refresh()
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
            val albums = scanner.scanAlbums().map { album ->
                AlbumRow(
                    name = album.name,
                    itemCount = album.itemCount,
                    totalBytes = album.totalBytes,
                    isEnabled = album.name !in disabled
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
        _state.value = _state.value.copy(
            uploadedCount = entryDao.countInState(BackupState.UPLOADED),
            pendingCount = entryDao.countPendingInSelectedAlbums(),
            redundantCount = redundant.size,
            redundantBytes = redundant.sumOf { it.sizeBytes },
            canRemoveLocalCopies = localCopyRemover.isSupported()
        )
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
                    remaining = result.remaining,
                    stoppedBecause = result.stoppedBecause
                )
            )
            refreshCounts()
        }
    }
}
