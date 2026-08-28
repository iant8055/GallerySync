package com.gallery.sync.ui.restore

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gallery.sync.data.local.dao.BackupEntryDao
import com.gallery.sync.data.local.entity.BackupEntryEntity
import com.gallery.sync.domain.backup.RestoreInPlaceResult
import com.gallery.sync.domain.backup.RestoreProxyInPlace
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/** What has happened to one file on this screen. */
sealed interface RowState {

    data object Waiting : RowState

    data class Downloading(val percent: Int) : RowState

    data object Overwriting : RowState

    data class Done(val bytes: Long) : RowState

    /**
     * Any failure, and the wording that goes with it says the file is unchanged.
     *
     * True here in a way it is not on the Archive tab: a restore reads from OneDrive and writes to
     * the phone, so nothing remote is touched and the proxy is only replaced once a full, size-
     * checked download is in hand. Verified on the Fold 4, 27 Aug 2026.
     */
    data class Failed(val reason: String) : RowState
}

/** One proxy, and what the screen knows about it. */
data class RestoreRow(
    val entry: BackupEntryEntity,
    val state: RowState = RowState.Waiting
) {
    val id: String get() = entry.id
    val album: String get() = entry.album
    val displayName: String get() = entry.displayName

    /** What the phone holds now, and what it would hold after. */
    val localBytes: Long get() = entry.localProxySizeBytes ?: entry.sizeBytes
    val fullBytes: Long get() = entry.remoteSizeBytes ?: entry.sizeBytes
}

data class RestoreUiState(
    val rows: List<RestoreRow> = emptyList(),
    val selection: Set<String> = emptySet(),
    val loading: Boolean = false,
    val running: Boolean = false,
    /** Set once a run finishes, so the screen can say what it did rather than just going quiet. */
    val summary: String? = null
) {
    val hasSelection: Boolean get() = selection.isNotEmpty()

    /** Grouped for display, because "in their original folders" is where the user will look. */
    val byAlbum: Map<String, List<RestoreRow>> get() = rows.groupBy { it.album }

    val selectedRows: List<RestoreRow> get() = rows.filter { it.id in selection }

    val reclaimableBytes: Long
        get() = selectedRows.sumOf { it.fullBytes - it.localBytes }
}

/**
 * The Restore tab: which local files are shrunken, and putting them back.
 *
 * Lists proxies rather than a OneDrive folder. The old tab listed the drive and fetched copies into
 * `DCIM/Restored`, which is a worse version of a file browser the user already has and never
 * restored anything — see TASK-018. This one answers "what have I shrunk on this device?" and the
 * answer comes from files carrying the proxy marker, grouped under the folders they live in.
 *
 * **Not yet built here:** files absent from the phone are not listed, so the download half of
 * TASK-018 is missing, and files already at full size are not shown greyed. Both are specified and
 * neither changes what is here.
 */
@HiltViewModel
class RestoreViewModel @Inject constructor(
    private val entryDao: BackupEntryDao,
    private val restorer: RestoreProxyInPlace
) : ViewModel() {

    private val _state = MutableStateFlow(RestoreUiState())
    val state: StateFlow<RestoreUiState> = _state.asStateFlow()

    /** The running batch, held so Stop can reach it. */
    private var job: Job? = null

    init {
        refresh()
    }

    /**
     * Re-reads the ledger. Cheap — one query, no network, no scan.
     *
     * Guarded while a run is going, so a tab switch mid-restore does not discard the row states the
     * user is watching.
     */
    fun refresh() {
        if (_state.value.running) return
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true)
            val proxies = entryDao.restorableProxies()
            _state.value = _state.value.copy(
                rows = proxies.map { RestoreRow(it) },
                // A selection whose row has gone is a selection of nothing.
                selection = _state.value.selection.intersect(proxies.map { it.id }.toSet()),
                loading = false
            )
        }
    }

    fun toggle(row: RestoreRow) {
        if (_state.value.running) return
        val current = _state.value.selection
        _state.value = _state.value.copy(
            selection = if (row.id in current) current - row.id else current + row.id,
            summary = null
        )
    }

    fun selectAll() {
        if (_state.value.running) return
        _state.value = _state.value.copy(
            selection = _state.value.rows.map { it.id }.toSet(),
            summary = null
        )
    }

    fun clearSelection() {
        if (_state.value.running) return
        _state.value = _state.value.copy(selection = emptySet(), summary = null)
    }

    /**
     * Restores everything selected, one file at a time.
     *
     * Sequential for the reason the old tab was: parallel transfers compete for one connection and
     * make a progress figure meaningless.
     */
    fun restoreSelected() {
        if (_state.value.running) return
        val chosen = _state.value.selectedRows
        if (chosen.isEmpty()) return

        job = viewModelScope.launch {
            _state.value = _state.value.copy(running = true, summary = null)
            var restored = 0
            var failed = 0

            try {
                chosen.forEach { row ->
                    setRow(row.id, RowState.Downloading(0))
                    val result = restorer.restore(row.entry) { written, total ->
                        val percent = if (total > 0) ((written * 100) / total).toInt() else 0
                        setRow(row.id, RowState.Downloading(percent.coerceIn(0, 100)))
                    }
                    when (result) {
                        is RestoreInPlaceResult.Restored -> {
                            restored++
                            setRow(row.id, RowState.Done(result.bytesWritten))
                        }

                        RestoreInPlaceResult.GoneFromCloud -> {
                            failed++
                            setRow(row.id, RowState.Failed("no longer in OneDrive"))
                        }

                        RestoreInPlaceResult.NotCovered -> {
                            failed++
                            setRow(row.id, RowState.Failed("this folder is not granted for writing"))
                        }

                        is RestoreInPlaceResult.Failed -> {
                            failed++
                            setRow(row.id, RowState.Failed(result.reason))
                        }
                    }
                }
            } finally {
                // In a finally so a Stop lands on a summary rather than leaving the screen frozen
                // mid-run, and so the counts describe what actually completed.
                _state.value = _state.value.copy(
                    running = false,
                    selection = emptySet(),
                    summary = summaryOf(restored, failed)
                )
                refresh()
            }
        }
    }

    /** Stops the batch, including the file in flight. Files already restored stay restored. */
    fun stop() {
        job?.cancel()
    }

    private fun summaryOf(restored: Int, failed: Int): String = when {
        failed == 0 -> "$restored back to full quality."
        restored == 0 -> "None restored. $failed unchanged."
        else -> "$restored back to full quality. $failed unchanged."
    }

    private fun setRow(id: String, state: RowState) {
        _state.value = _state.value.copy(
            rows = _state.value.rows.map { if (it.id == id) it.copy(state = state) else it }
        )
    }
}
