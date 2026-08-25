package com.gallery.sync.ui.retrieve

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gallery.sync.domain.backup.BackupEngine
import com.gallery.sync.domain.backup.RestorableFile
import com.gallery.sync.domain.backup.RestoreFromCloud
import com.gallery.sync.domain.backup.RestoreResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** How one file's retrieval is going. */
sealed interface RetrieveStatus {

    data class Working(val bytesWritten: Long, val total: Long) : RetrieveStatus

    data object Done : RetrieveStatus

    data class Failed(val reason: String) : RetrieveStatus

    data object Unsupported : RetrieveStatus

    /** The cloud copy has gone too. The row is dropped rather than offered again. */
    data object GoneFromCloud : RetrieveStatus
}

data class RetrieveUiState(
    /** Folders OneDrive holds under the backup roots, or empty until they have been read. */
    val folders: List<String> = emptyList(),
    val selectedFolder: String? = null,
    val files: List<RestorableFile> = emptyList(),
    val loading: Boolean = false,
    /**
     * The listing failed, which is **not** the same as there being nothing there.
     *
     * Rendered as its own message rather than as an empty list. "You have no backups" is the single
     * most alarming thing this screen could say, and saying it because the network dropped would be
     * a lie told at the worst possible moment.
     */
    val couldNotList: Boolean = false,
    /** Keyed on the remote item id, so several files can be fetched at once without confusion. */
    val statuses: Map<String, RetrieveStatus> = emptyMap(),
    /**
     * Files dropped because OneDrive no longer holds them, by name.
     *
     * Held at the screen rather than on the row, because the row is exactly what disappears. Putting
     * the explanation on it meant the reason vanished with the thing it was explaining, leaving a
     * row that evaporated for no visible cause — the failure this app spends most of its wording
     * avoiding elsewhere.
     */
    val droppedFromCloud: List<String> = emptyList()
)

/**
 * What OneDrive holds, folder by folder, and a button to bring any of it back.
 *
 * ### Why this reads the drive and not the ledger
 *
 * It used to read `observeRetrievable()`, which offers a file only when our ledger knows it **and**
 * it has already left the phone. That answers "what have I lost from this device?", which is not
 * what a restore feature promises.
 *
 * The case that settles it is a **new phone**: the ledger records what left *this* device, so on a
 * fresh install it is empty by construction, and a ledger-driven list offers nothing at all while
 * the user's entire library sits in OneDrive. Ian, 25 Aug 2026: *if we are going to offer restore
 * then we need to be able to restore any file, not just the ones we backed up, synced or archived.*
 *
 * Observed on the Fold 4 the same day: `DCIM/12345clips` holds seven videos, all seven backed up,
 * and the ledger-driven list offered exactly one — the only one that had left the device.
 *
 * ### Not a photo browser, and it must not become one
 *
 * The design principle rules out thumbnails, grids and search; this is folder names, file names,
 * sizes and a button. A cloud folder listing is precisely the screen that grows a thumbnail grid if
 * nobody says no, so: no. Real browsing stays with the Open OneDrive button in Settings — looking
 * *through* your photos is a different activity from getting specific ones back.
 */
@HiltViewModel
class RetrieveViewModel @Inject constructor(
    private val engine: BackupEngine,
    private val restore: RestoreFromCloud
) : ViewModel() {

    private val _state = MutableStateFlow(RetrieveUiState())
    val state: StateFlow<RetrieveUiState> = _state.asStateFlow()

    init {
        loadFolders()
    }

    fun loadFolders() {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, couldNotList = false)
            val folders = engine.cloudFolders()
            _state.value = _state.value.copy(
                folders = folders.orEmpty(),
                loading = false,
                couldNotList = folders == null
            )
        }
    }

    fun openFolder(album: String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(
                selectedFolder = album,
                files = emptyList(),
                loading = true,
                couldNotList = false
            )
            val files = engine.restorableFilesIn(album)
            _state.value = _state.value.copy(
                files = files.orEmpty(),
                loading = false,
                couldNotList = files == null
            )
        }
    }

    /** Back to the folder list. Statuses are kept, so a fetch started here survives the trip. */
    fun closeFolder() {
        _state.value = _state.value.copy(
            selectedFolder = null,
            files = emptyList(),
            couldNotList = false
        )
    }

    /**
     * Fetches one file back.
     *
     * Allowed for a file that is still on the phone. That is the point of a per-file button: the
     * user asked for this one, and a second copy under a `_restored` name is a cost they can see.
     */
    fun retrieve(file: RestorableFile) {
        if (_state.value.statuses[file.remoteItemId] is RetrieveStatus.Working) return

        viewModelScope.launch {
            setStatus(file.remoteItemId, RetrieveStatus.Working(0, file.sizeBytes))

            val result = restore.restore(
                remoteItemId = file.remoteItemId,
                displayName = file.displayName,
                mimeType = file.mimeType,
                isVideo = file.isVideo,
                sizeBytes = file.sizeBytes,
                onProgress = { written, total ->
                    setStatus(file.remoteItemId, RetrieveStatus.Working(written, total))
                }
            )

            setStatus(
                file.remoteItemId,
                when (result) {
                    is RestoreResult.Restored -> RetrieveStatus.Done
                    is RestoreResult.Unsupported -> RetrieveStatus.Unsupported
                    is RestoreResult.Failed -> RetrieveStatus.Failed(result.reason)

                    // The listing said it was there and the fetch says it is not, so the listing is
                    // stale. Drop the row and say so by name — a row that simply vanished would look
                    // like the app losing things.
                    is RestoreResult.GoneFromCloud -> {
                        _state.value = _state.value.copy(
                            files = _state.value.files.filterNot {
                                it.remoteItemId == file.remoteItemId
                            },
                            droppedFromCloud = _state.value.droppedFromCloud + file.displayName
                        )
                        RetrieveStatus.GoneFromCloud
                    }
                }
            )
        }
    }

    private fun setStatus(remoteItemId: String, status: RetrieveStatus) {
        _state.value = _state.value.copy(
            statuses = _state.value.statuses + (remoteItemId to status)
        )
    }
}
