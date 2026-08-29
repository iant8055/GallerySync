package com.gallery.sync.ui.restore

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gallery.sync.data.local.dao.BackupEntryDao
import com.gallery.sync.data.local.entity.BackupEntryEntity
import com.gallery.sync.domain.backup.BackupEngine
import com.gallery.sync.domain.backup.DownloadMissingFile
import com.gallery.sync.domain.backup.RestoreInPlaceResult
import com.gallery.sync.domain.backup.RestoreProxyInPlace
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Which of the two things this row would do. */
enum class RowKind {

    /** Shrunken by us and still here: the original replaces it, in place. */
    Restore,

    /** Gone from the phone: the original comes back into its album, under its own name. */
    Download
}

/** What has happened to one file on this screen. */
sealed interface RowState {

    data object Waiting : RowState

    data class Working(val percent: Int) : RowState

    data class Done(val bytes: Long) : RowState

    /**
     * Any failure, and the wording says the file is unchanged.
     *
     * True on both paths and for different reasons. A restore only overwrites once a complete,
     * size-checked download is in hand; a download creates a new row, and MediaStore renames rather
     * than overwrites when a name is taken. Neither can cost the user a file.
     */
    data class Failed(val reason: String) : RowState
}

/** One file, and what the screen knows about it. */
data class RestoreRow(
    val entry: BackupEntryEntity,
    val kind: RowKind,
    val state: RowState = RowState.Waiting
) {
    val id: String get() = entry.id
    val album: String get() = entry.album
    val displayName: String get() = entry.displayName

    /** What the phone holds now — nothing at all, for a download. */
    val localBytes: Long get() = when (kind) {
        RowKind.Restore -> entry.localProxySizeBytes ?: entry.sizeBytes
        RowKind.Download -> 0L
    }

    val fullBytes: Long get() = entry.remoteSizeBytes ?: entry.sizeBytes
}

/** One album, as a card on the folder view. */
data class RestoreFolder(
    val name: String,
    val restorable: Int,
    val downloadable: Int,
    val bytesToRecover: Long,
    val selectedHere: Int
) {
    val total: Int get() = restorable + downloadable
}

data class RestoreUiState(
    val rows: List<RestoreRow> = emptyList(),
    val openFolder: String? = null,
    val selection: Set<String> = emptySet(),
    val loading: Boolean = false,
    val running: Boolean = false,
    val summary: String? = null
) {
    val hasSelection: Boolean get() = selection.isNotEmpty()

    val selectedRows: List<RestoreRow> get() = rows.filter { it.id in selection }

    val bytesToRecover: Long get() = selectedRows.sumOf { it.fullBytes - it.localBytes }

    /** The rows on screen: one folder's worth, or none while the folder list is showing. */
    val visibleRows: List<RestoreRow>
        get() = openFolder?.let { name -> rows.filter { it.album == name } }.orEmpty()

    val folders: List<RestoreFolder>
        get() = rows.groupBy { it.album }
            .map { (name, inAlbum) ->
                RestoreFolder(
                    name = name,
                    restorable = inAlbum.count { it.kind == RowKind.Restore },
                    downloadable = inAlbum.count { it.kind == RowKind.Download },
                    bytesToRecover = inAlbum.sumOf { it.fullBytes - it.localBytes },
                    selectedHere = inAlbum.count { it.id in selection }
                )
            }
            .sortedBy { it.name.lowercase() }
}

/**
 * The Restore tab: what this app did to this phone, and undoing it.
 *
 * Two populations, one list. A **proxy** is still here and shrunken, so its original replaces it in
 * place. A file the phone has **lost** — most often to an Archive album — comes back into the album
 * it came from. Both are things GallerySync did and only GallerySync can undo, which is what makes
 * this a restore rather than the file browser the old tab was. See TASK-018.
 *
 * **Folders first, always.** Ian, 27 Aug 2026: *"Restore should default to a folder view, even if
 * there is only one folder to access."* Swipe a folder to take all of it, or open it and choose.
 */
@HiltViewModel
class RestoreViewModel @Inject constructor(
    private val entryDao: BackupEntryDao,
    private val engine: BackupEngine,
    private val restorer: RestoreProxyInPlace,
    private val downloader: DownloadMissingFile
) : ViewModel() {

    private val _state = MutableStateFlow(RestoreUiState())
    val state: StateFlow<RestoreUiState> = _state.asStateFlow()

    private var job: Job? = null

    init {
        refresh()
    }

    /**
     * Re-reads what can be brought back. One query plus one device scan, no network.
     *
     * The scan is new as of 28 Aug 2026 and is the price of asking about folders rather than about
     * content — roughly half a second against 3,335 files, paid on entry to the tab.
     */
    fun refresh() {
        if (_state.value.running) return
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true)
            // Two populations, one verb: what this app shrank, and what is in OneDrive but not in
            // its folder here. The second is asked of the engine rather than of a stored column,
            // because "gone" means something stricter here than it does to the deletion guard —
            // see RestoreScope.
            val rows = entryDao.restorableProxies().map { RestoreRow(it, RowKind.Restore) } +
                engine.filesNotOnThePhone().map { RestoreRow(it, RowKind.Download) }
            val ids = rows.mapTo(HashSet()) { it.id }
            _state.value = _state.value.copy(
                rows = rows.sortedWith(compareBy({ it.album.lowercase() }, { it.displayName })),
                // A selection whose row has gone is a selection of nothing.
                selection = _state.value.selection.intersect(ids),
                loading = false
            )
        }
    }

    fun openFolder(name: String) {
        _state.value = _state.value.copy(openFolder = name, summary = null)
    }

    fun closeFolder() {
        _state.value = _state.value.copy(openFolder = null)
    }

    fun toggle(row: RestoreRow) {
        if (_state.value.running) return
        val current = _state.value.selection
        _state.value = _state.value.copy(
            selection = if (row.id in current) current - row.id else current + row.id,
            summary = null
        )
    }

    /**
     * Takes or drops a whole folder.
     *
     * Directional at the call site, the same as the old folder list: right selects, left deselects,
     * and repeating either is a no-op. A toggle would silently unpick a folder already chosen while
     * the user swiped through several.
     */
    fun setFolderSelected(name: String, selected: Boolean) {
        if (_state.value.running) return
        val inFolder = _state.value.rows.filter { it.album == name }.map { it.id }
        val current = _state.value.selection
        _state.value = _state.value.copy(
            selection = if (selected) current + inFolder else current - inFolder.toSet(),
            summary = null
        )
    }

    fun selectAllHere() {
        if (_state.value.running) return
        _state.value = _state.value.copy(
            selection = _state.value.selection + _state.value.visibleRows.map { it.id },
            summary = null
        )
    }

    fun clearSelection() {
        if (_state.value.running) return
        _state.value = _state.value.copy(selection = emptySet(), summary = null)
    }

    /** Both kinds, one at a time, in one run. Parallel transfers compete for one connection. */
    fun restoreSelected() {
        if (_state.value.running) return
        val chosen = _state.value.selectedRows
        if (chosen.isEmpty()) return

        job = viewModelScope.launch {
            _state.value = _state.value.copy(running = true, summary = null)
            var restored = 0
            var downloaded = 0
            var failed = 0

            try {
                chosen.forEach { row ->
                    setRow(row.id, RowState.Working(0))
                    val onProgress: (Long, Long) -> Unit = { written, total ->
                        val percent = if (total > 0) ((written * 100) / total).toInt() else 0
                        setRow(row.id, RowState.Working(percent.coerceIn(0, 100)))
                    }

                    val result = when (row.kind) {
                        RowKind.Restore -> restorer.restore(row.entry, onProgress)
                        RowKind.Download -> downloader.download(row.entry, onProgress)
                    }

                    when (result) {
                        is RestoreInPlaceResult.Restored -> {
                            if (row.kind == RowKind.Restore) restored++ else downloaded++
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
                // In a finally so a Stop lands on a summary rather than freezing the screen mid-run,
                // and so the counts describe what actually completed.
                _state.value = _state.value.copy(
                    running = false,
                    selection = emptySet(),
                    summary = summaryOf(restored, downloaded, failed)
                )
                refresh()
            }
        }
    }

    /** Stops the batch, including the file in flight. What is already back stays back. */
    fun stop() {
        job?.cancel()
    }

    private fun summaryOf(restored: Int, downloaded: Int, failed: Int): String {
        val did = buildList {
            if (restored > 0) add("$restored back to full quality")
            if (downloaded > 0) add("$downloaded back on this phone")
        }
        return when {
            did.isEmpty() && failed > 0 -> "None recovered. $failed unchanged."
            failed == 0 -> did.joinToString(" · ") + "."
            else -> did.joinToString(" · ") + ". $failed unchanged."
        }
    }

    private fun setRow(id: String, state: RowState) {
        _state.value = _state.value.copy(
            rows = _state.value.rows.map { if (it.id == id) it.copy(state = state) else it }
        )
    }
}
