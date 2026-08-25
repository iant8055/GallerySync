package com.gallery.sync.ui.retrieve

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gallery.sync.data.local.dao.BackupEntryDao
import com.gallery.sync.data.local.entity.BackupEntryEntity
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
    val items: List<BackupEntryEntity> = emptyList(),
    /** Keyed on ledger id, so several files can be fetched at once without confusing their rows. */
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
 * The list of what is in OneDrive but no longer on the phone.
 *
 * **Not a photo browser**, and it must not become one. The design principle rules out thumbnails,
 * grids and search; this is names, sizes and a button, which is everything needed to get a file back
 * and nothing more.
 *
 * It is also the only place a fetch can be triggered. Android has no hydration hook for media, so
 * tapping a missing item in Samsung Gallery cannot reach this app — the list is not a convenience,
 * it is the whole interface.
 */
@HiltViewModel
class RetrieveViewModel @Inject constructor(
    private val entryDao: BackupEntryDao,
    private val restore: RestoreFromCloud
) : ViewModel() {

    private val _state = MutableStateFlow(RetrieveUiState())
    val state: StateFlow<RetrieveUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            entryDao.observeRetrievable().collect { rows ->
                _state.value = _state.value.copy(items = rows)
            }
        }
    }

    /**
     * Fetches one file back.
     *
     * The row stays in the list until the next scan clears its flag, which is honest: the file is
     * not really back until MediaStore holds it and the ledger has noticed.
     */
    fun retrieve(entry: BackupEntryEntity) {
        val remoteId = entry.remoteItemId ?: return
        if (_state.value.statuses[entry.id] is RetrieveStatus.Working) return

        viewModelScope.launch {
            setStatus(entry.id, RetrieveStatus.Working(0, entry.sizeBytes))

            val result = restore.restore(
                remoteItemId = remoteId,
                displayName = entry.displayName,
                mimeType = entry.mimeType,
                isVideo = entry.isVideo,
                sizeBytes = entry.sizeBytes,
                onProgress = { written, total ->
                    setStatus(entry.id, RetrieveStatus.Working(written, total))
                }
            )

            setStatus(
                entry.id,
                when (result) {
                    is RestoreResult.Restored -> RetrieveStatus.Done
                    is RestoreResult.Unsupported -> RetrieveStatus.Unsupported
                    is RestoreResult.Failed -> RetrieveStatus.Failed(result.reason)

                    // Neither on the phone nor in the drive: the row describes nothing. Forgetting
                    // it is bookkeeping — it removes our record and no file anywhere, since there
                    // is no longer a file anywhere to remove.
                    is RestoreResult.GoneFromCloud -> {
                        entryDao.forget(entry.id)
                        _state.value = _state.value.copy(
                            droppedFromCloud = _state.value.droppedFromCloud + entry.displayName
                        )
                        RetrieveStatus.GoneFromCloud
                    }
                }
            )
        }
    }

    private fun setStatus(id: String, status: RetrieveStatus) {
        _state.value = _state.value.copy(statuses = _state.value.statuses + (id to status))
    }
}
