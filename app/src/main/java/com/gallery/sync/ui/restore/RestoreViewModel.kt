package com.gallery.sync.ui.restore

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gallery.sync.data.local.dao.BackupEntryDao
import com.gallery.sync.data.local.entity.BackupEntryEntity
import com.gallery.sync.data.local.DriveListingStore
import com.gallery.sync.data.local.settings.BackupSettings
import com.gallery.sync.data.remote.cloud.CloudConnections
import com.gallery.sync.domain.backup.BackupLocation
import com.gallery.sync.domain.backup.BackupEngine
import com.gallery.sync.domain.backup.DriveListing
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
    Download,

    /**
     * Already on the phone at full size. Listed greyed out and never selectable, so the tab can say
     * why a file in OneDrive is not on offer instead of leaving the user to wonder. Ian, 18 Sept 2026.
     */
    Here
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
        RowKind.Here -> entry.sizeBytes
    }

    /** Something can be done with it. A greyed-out file cannot be selected. */
    val isActionable: Boolean get() = kind != RowKind.Here

    val fullBytes: Long get() = entry.remoteSizeBytes ?: entry.sizeBytes
}

/** One album, as a card on the folder view. */
data class RestoreFolder(
    val name: String,
    val restorable: Int,
    val downloadable: Int,
    val bytesToRecover: Long,
    val selectedHere: Int,
    /** Files already on the phone at full size: shown greyed out inside the folder. */
    val here: Int = 0
) {
    val total: Int get() = restorable + downloadable
}

/** Files finished out of files picked, and the byte-weighted fraction of the run, 0..1. */
data class RestoreProgress(val finished: Int, val total: Int, val fraction: Float)

data class RestoreUiState(
    val rows: List<RestoreRow> = emptyList(),
    val openFolder: String? = null,
    val selection: Set<String> = emptySet(),
    val loading: Boolean = false,
    val running: Boolean = false,
    val summary: String? = null,
    /** OneDrive is being read. The ledger's rows are already on screen; this adds the rest. */
    val checkingCloud: Boolean = false,
    /** A folder could not be listed, so what is shown may be incomplete. */
    val cloudUnavailable: Boolean = false,
    /** Settings > Restore > Show empty folders: list folders with nothing to bring back too. */
    val showEmptyFolders: Boolean = false
) {
    val hasSelection: Boolean get() = selection.isNotEmpty()

    val selectedRows: List<RestoreRow> get() = rows.filter { it.id in selection }

    val bytesToRecover: Long get() = selectedRows.sumOf { it.fullBytes - it.localBytes }

    /**
     * How far the run has got, or null when nothing is running. Ian, 24 Sept 2026: the Stop button
     * was the only sign a restore was under way.
     *
     * Weighted by bytes rather than counted, so one 2 GB video sitting among fifty photos moves the
     * bar as much as it takes of the time. A file that failed counts as passed: the queue moved on.
     */
    val progress: RestoreProgress?
        get() {
            if (!running) return null
            val picked = selectedRows
            if (picked.isEmpty()) return null
            val weights = picked.map { (it.fullBytes - it.localBytes).coerceAtLeast(1L) }
            val finished = picked.count { it.state is RowState.Done || it.state is RowState.Failed }
            val got = picked.indices.sumOf { i ->
                val share = when (val s = picked[i].state) {
                    is RowState.Done, is RowState.Failed -> 1.0
                    is RowState.Working -> s.percent / 100.0
                    RowState.Waiting -> 0.0
                }
                share * weights[i]
            }
            return RestoreProgress(
                finished = finished,
                total = picked.size,
                fraction = (got / weights.sum()).toFloat().coerceIn(0f, 1f)
            )
        }

    /** The rows on screen: one folder's worth, or none while the folder list is showing. */
    val visibleRows: List<RestoreRow>
        get() = openFolder
            ?.let { name -> rows.filter { it.album == name }.sortedBy { !it.isActionable } }
            .orEmpty()

    val folders: List<RestoreFolder>
        get() = rows.groupBy { it.album }
            .map { (name, inAlbum) ->
                RestoreFolder(
                    name = name,
                    restorable = inAlbum.count { it.kind == RowKind.Restore },
                    downloadable = inAlbum.count { it.kind == RowKind.Download },
                    bytesToRecover = inAlbum.sumOf { it.fullBytes - it.localBytes },
                    selectedHere = inAlbum.count { it.id in selection },
                    here = inAlbum.count { it.kind == RowKind.Here }
                )
            }
            // A folder with nothing to bring back is listed only when the user asked for every
            // folder. The setting existed before this tab could see them; it does something now.
            .filter { it.total > 0 || showEmptyFolders }
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
    private val downloader: DownloadMissingFile,
    private val settings: BackupSettings,
    private val listingStore: DriveListingStore,
    private val connections: CloudConnections
) : ViewModel() {

    private val _state = MutableStateFlow(RestoreUiState())
    val state: StateFlow<RestoreUiState> = _state.asStateFlow()

    private var job: Job? = null
    private var refreshJob: Job? = null

    /**
     * What OneDrive last listed, and when. Held so that leaving this tab and coming back does not read
     * the drive again (Ian, 18 Sept 2026: it reloaded from scratch every time). The ledger and the
     * phone are re-read on every entry because they are cheap and they change; the drive is re-read
     * only when this has gone stale or the user presses Refresh.
     *
     * Also kept on disk ([DriveListingStore]), because memory does not survive the app being closed
     * and Ian found the tab empty on every launch (19 Sept 2026). A listing loaded from disk shows at
     * once and is refreshed behind it if it is older than the time-to-live.
     */
    private var driveListing: DriveListing? = null
    private var driveListedAt = 0L

    init {
        refresh()
    }

    /**
     * Re-reads what can be brought back: the ledger's rows straight away, then OneDrive's.
     *
     * The first part is one query plus one device scan and needs no network (roughly half a second
     * against 3,335 files). The second lists the drive folder by folder and can take much longer, so
     * it is shown as "checking" and added when it arrives.
     */
    fun refresh(force: Boolean = false) {
        if (_state.value.running) return
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            // Nothing is blanked while re-reading: the figure shows a dash and the bar runs only the
            // very first time, when there is nothing yet to show.
            _state.value = _state.value.copy(
                loading = _state.value.rows.isEmpty(),
                showEmptyFolders = settings.current().showEmptyCloudFolders
            )

            // Without OneDrive there is no drive to list, and asking anyway ended in a warning about OneDrive
            // for someone who does not use it (Ian, 24 Sept 2026: move away from OneDrive-centric). The
            // ledger's rows still cover every other cloud that can restore, so show those and stop.
            if (connections.of(BackupLocation.ONEDRIVE)?.accountLabel() == null) {
                driveListing = null
                publishFrom(null, checkingCloud = false)
                return@launch
            }

            // First, straight away and with no network: the ledger's rows, and the last OneDrive
            // listing compared with the phone as it is now. A listing already in hand means the list
            // does not shrink to the ledger's rows while the drive is asked again.
            // After a launch there is nothing in memory yet, so take the last listing from disk.
            if (driveListing == null) {
                listingStore.load(engine.destinationPath())?.let {
                    driveListing = it.listing
                    driveListedAt = it.listedAtMillis
                }
            }

            val held = driveListing
            publishFrom(held, checkingCloud = held == null || isStale() || force)

            // Then OneDrive itself, which is the source of truth for what can be brought back
            // (Ian, 18 Sept 2026), but only when there is a reason: nothing held, what is held has
            // gone stale, or the user pressed Refresh.
            if (held != null && !isStale() && !force) return@launch

            val listing = runCatching { engine.listDriveFolders() }.getOrNull()
            if (listing == null) {
                _state.value = _state.value.copy(checkingCloud = false, cloudUnavailable = true)
                return@launch
            }
            driveListing = listing
            driveListedAt = System.currentTimeMillis()
            // Kept for the next launch, unless the read was partial: a listing with folders missing
            // would greet the next launch with a shorter list than the drive holds.
            if (!listing.couldNotList) {
                listingStore.save(listing, driveListedAt, engine.destinationPath())
            }
            publishFrom(listing, checkingCloud = false)
        }
    }

    private fun isStale(): Boolean = System.currentTimeMillis() - driveListedAt > DRIVE_LISTING_TTL_MILLIS

    /**
     * Ledger rows plus, when there is a listing, what it adds. Comparing the listing with the phone
     * also fills in OneDrive ids the ledger never recorded, so the ledger is read after it.
     */
    private suspend fun publishFrom(listing: DriveListing?, checkingCloud: Boolean) {
        if (listing == null) {
            publish(ledgerRows(), loading = false, checkingCloud = checkingCloud)
            return
        }
        val drive = engine.driveRestoreFiles(listing)
        val ledger = ledgerRows()
        val known = ledger.mapTo(HashSet()) { it.album.lowercase() + "/" + it.displayName }
        val fromDrive = drive.missing.map { RestoreRow(it, RowKind.Download) } +
            drive.here.map { RestoreRow(it, RowKind.Here) }
        publish(
            ledger + fromDrive.filterNot { (it.album.lowercase() + "/" + it.displayName) in known },
            loading = false,
            checkingCloud = checkingCloud,
            cloudUnavailable = drive.couldNotList
        )
    }

    /**
     * The ledger's two lists, which need no network. Two populations, one verb: what this app shrank,
     * and what is in OneDrive but not in its folder here. The second is asked of the engine rather
     * than of a stored column, because "gone" means something stricter here than it does to the
     * deletion guard — see RestoreScope.
     */
    private suspend fun ledgerRows(): List<RestoreRow> {
        val proxies = entryDao.restorableProxies()
        val proxyIds = proxies.mapTo(HashSet()) { it.id }
        // A file is one row or the other, never both: an optimised file still on the phone is a
        // restore in place, and `filesNotOnThePhone` now considers optimised files too.
        return proxies.map { RestoreRow(it, RowKind.Restore) } +
            engine.filesNotOnThePhone()
                .filterNot { it.id in proxyIds }
                .map { RestoreRow(it, RowKind.Download) }
    }

    private fun publish(
        rows: List<RestoreRow>,
        loading: Boolean,
        checkingCloud: Boolean,
        cloudUnavailable: Boolean = _state.value.cloudUnavailable
    ) {
        val ids = rows.mapTo(HashSet()) { it.id }
        _state.value = _state.value.copy(
            rows = rows.sortedWith(compareBy({ it.album.lowercase() }, { it.displayName })),
            // A selection whose row has gone is a selection of nothing.
            selection = _state.value.selection.intersect(ids),
            loading = loading,
            checkingCloud = checkingCloud,
            cloudUnavailable = cloudUnavailable
        )
    }

    fun openFolder(name: String) {
        _state.value = _state.value.copy(openFolder = name, summary = null)
    }

    fun closeFolder() {
        _state.value = _state.value.copy(openFolder = null)
    }

    fun toggle(row: RestoreRow) {
        if (_state.value.running || !row.isActionable) return
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
        val inFolder = _state.value.rows.filter { it.album == name && it.isActionable }.map { it.id }
        val current = _state.value.selection
        _state.value = _state.value.copy(
            selection = if (selected) current + inFolder else current - inFolder.toSet(),
            summary = null
        )
    }

    fun selectAllHere() {
        if (_state.value.running) return
        _state.value = _state.value.copy(
            selection = _state.value.selection +
                _state.value.visibleRows.filter { it.isActionable }.map { it.id },
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
                        // Never selected; here so the `when` is exhaustive.
                        RowKind.Here -> return@forEach
                    }

                    when (result) {
                        is RestoreInPlaceResult.Restored -> {
                            if (row.kind == RowKind.Restore) restored++ else downloaded++
                            setRow(row.id, RowState.Done(result.bytesWritten))
                        }

                        RestoreInPlaceResult.GoneFromCloud -> {
                            failed++
                            setRow(row.id, RowState.Failed("no longer in the cloud"))
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

    private companion object {
        /**
         * How long a OneDrive listing is trusted before the tab reads the drive again by itself.
         *
         * Ten minutes: long enough that moving between tabs never re-reads it, short enough that a
         * file added to OneDrive from a computer turns up without the user having to ask. Refresh
         * always reads it.
         */
        const val DRIVE_LISTING_TTL_MILLIS = 10 * 60 * 1000L
    }
}
