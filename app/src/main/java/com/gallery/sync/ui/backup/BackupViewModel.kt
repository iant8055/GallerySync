package com.gallery.sync.ui.backup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gallery.sync.data.local.dao.AlbumPreferenceDao
import com.gallery.sync.data.local.dao.BackupEntryDao
import com.gallery.sync.data.local.entity.AlbumPreferenceEntity
import com.gallery.sync.data.local.entity.BackupState
import com.gallery.sync.data.local.media.MediaAccess
import com.gallery.sync.data.local.media.MediaScanner
import com.gallery.sync.domain.backup.BackupEngine
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

data class BackupUiState(
    val access: MediaAccess = MediaAccess.NONE,
    val albums: List<AlbumRow> = emptyList(),
    val isScanning: Boolean = false,
    val isRunning: Boolean = false,
    val status: String? = null,
    val uploadedCount: Int = 0,
    val pendingCount: Int = 0
) {
    /** Files that would be sent if a run started now. */
    val enabledItemCount: Int get() = albums.filter { it.isEnabled }.sumOf { it.itemCount }

    val enabledBytes: Long get() = albums.filter { it.isEnabled }.sumOf { it.totalBytes }
}

@HiltViewModel
class BackupViewModel @Inject constructor(
    private val scanner: MediaScanner,
    private val albumDao: AlbumPreferenceDao,
    private val entryDao: BackupEntryDao,
    private val engine: BackupEngine
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

            val disabled = albumDao.disabledAlbums().toSet()
            val albums = scanner.scanAlbums().map { album ->
                AlbumRow(
                    name = album.name,
                    itemCount = album.itemCount,
                    totalBytes = album.totalBytes,
                    isEnabled = album.name !in disabled
                )
            }

            _state.value = _state.value.copy(
                albums = albums,
                isScanning = false,
                uploadedCount = entryDao.countInState(BackupState.UPLOADED),
                pendingCount = entryDao.countNotInState(BackupState.UPLOADED)
            )
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
        }
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
            _state.value = _state.value.copy(isRunning = true, status = "Scanning…")

            val seen = engine.refreshLedger()
            if (seen == null) {
                _state.value = _state.value.copy(
                    isRunning = false,
                    status = "No permission to read media."
                )
                return@launch
            }

            _state.value = _state.value.copy(status = "Uploading…")
            val result = engine.uploadPending()

            _state.value = _state.value.copy(
                isRunning = false,
                status = buildString {
                    append("${result.uploaded} uploaded")
                    if (result.failed > 0) append(", ${result.failed} failed")
                    if (result.remaining > 0) append(", ${result.remaining} still to go")
                    result.stoppedBecause?.let { append(" — stopped: $it") }
                },
                uploadedCount = entryDao.countInState(BackupState.UPLOADED),
                pendingCount = entryDao.countNotInState(BackupState.UPLOADED)
            )
        }
    }
}
