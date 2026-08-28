package com.gallery.sync.ui.retrieve

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gallery.sync.data.local.settings.BackupSettings
import com.gallery.sync.domain.backup.BackupEngine
import com.gallery.sync.domain.backup.RestorableFile
import com.gallery.sync.domain.backup.RestorableFolder
import com.gallery.sync.domain.backup.RestoreFromCloud
import com.gallery.sync.domain.backup.RestoreResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * How the batch the user selected is going.
 *
 * One status for the whole selection rather than one per row. The per-row statuses went with the
 * per-row buttons: a queue that runs one file at a time has one position in it, and reporting that
 * once is both truer and quieter than five rows each claiming to be busy.
 */
/** One picked file, and the folder it was picked in. */
data class SelectedFile(val file: RestorableFile, val folderName: String)

sealed interface RestoreBatchStatus {

    /**
     * [currentFile] and [percentOfCurrent] exist because file counts alone are not progress.
     *
     * Restoring one 2 GB video shows "0 of 1" for seven minutes and reads as a hang — observed on
     * the Fold 4, 26 Aug 2026. Backup already learned this and says so on `BackupProgress`: "a
     * three-minute upload with no feedback reads as a hang, and the biggest files are exactly the
     * ones that take longest." Downloads are no different.
     *
     * [percentOfCurrent] is null until the first byte lands, so the bar starts indeterminate rather
     * than claiming a confident 0%.
     */
    data class Working(
        val done: Int,
        val total: Int,
        val currentFile: String? = null,
        val percentOfCurrent: Int? = null
    ) : RestoreBatchStatus

    data class Done(val restored: Int, val failed: Int) : RestoreBatchStatus

    data object Unsupported : RestoreBatchStatus
}

data class RetrieveUiState(
    /** Folders OneDrive holds under the backup roots, or empty until they have been read. */
    val folders: List<RestorableFolder> = emptyList(),
    val selectedFolder: String? = null,
    /** Where new uploads go, e.g. `Samsung Gallery/DCIM`. The breadcrumb is built from it. */
    val destinationPath: String = "",
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
    /**
     * What the user has picked, by remote item id, and which folder each came from.
     *
     * ### It holds the files, not their ids
     *
     * Leaving a folder discards its listing, so a selection kept as bare ids would lose the name,
     * size and mime type needed to fetch anything. The files ride along.
     *
     * ### It survives navigation, deliberately
     *
     * An earlier version cleared this on every open and close, reasoning that a selection belonged
     * to the folder it was made in. Ian, 25 Aug 2026: it does not — the point is to pick files from
     * several folders and bring them back together. The selection belongs to the person, and only
     * a restore or an explicit Clear empties it.
     */
    val selection: Map<String, SelectedFile> = emptyMap(),
    /**
     * Whole folders the user swiped, by name.
     *
     * Ian, 25 Aug 2026: if you are taking the whole folder you never needed to open it — opening is
     * for picking individual files. So the folder list has both intents, and they are different
     * gestures rather than two buttons: tap opens, swipe takes the lot.
     *
     * Selecting, not restoring. A swipe puts the folder in the bar at the foot; the Restore there is
     * what moves a byte. A gesture that started a 4.1 GB download on its own would be the largest
     * unconfirmed action in the app.
     */
    val selectedFolderNames: Set<String> = emptySet(),
    /** Progress of the batch the user asked for, or null when none has run. */
    val batchStatus: RestoreBatchStatus? = null,
    /**
     * Files dropped because OneDrive no longer holds them, by name.
     *
     * Held at the screen rather than on the row, because the row is exactly what disappears. Putting
     * the explanation on it meant the reason vanished with the thing it was explaining, leaving a
     * row that evaporated for no visible cause — the failure this app spends most of its wording
     * avoiding elsewhere.
     */
    val droppedFromCloud: List<String> = emptyList()
) {
    val selectedFiles: List<RestorableFile> get() = selection.values.map { it.file }

    val chosenFolders: List<RestorableFolder> get() = folders.filter { it.name in selectedFolderNames }

    /** How many files are picked, counting a whole swiped folder as everything in it. */
    val selectionCount: Int
        get() = selection.size + chosenFolders.sumOf { it.fileCount }

    val selectedBytes: Long
        get() = selection.values.sumOf { it.file.sizeBytes } + chosenFolders.sumOf { it.sizeBytes }

    val hasSelection: Boolean get() = selection.isNotEmpty() || selectedFolderNames.isNotEmpty()

    /** How many picked files came from one folder, for the count on its row. */
    fun selectedCountIn(folderName: String): Int =
        selection.values.count { it.folderName == folderName }

    val isRestoring: Boolean get() = batchStatus is RestoreBatchStatus.Working
}

/**
 * What OneDrive holds, folder by folder, and a way to bring any of it back.
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
 * ### Not a photo browser, and it must not become one
 *
 * The design principle rules out thumbnails, grids and search; this is folder names, file names,
 * sizes and a selection. Real browsing stays with the Open OneDrive button in Settings — looking
 * *through* your photos is a different activity from getting specific ones back.
 */
@HiltViewModel
class RetrieveViewModel @Inject constructor(
    private val engine: BackupEngine,
    private val restore: RestoreFromCloud,
    private val settings: BackupSettings
) : ViewModel() {

    private val _state = MutableStateFlow(RetrieveUiState())
    val state: StateFlow<RetrieveUiState> = _state.asStateFlow()

    /**
     * The running batch, held so it can be stopped.
     *
     * `viewModelScope.launch` alone gave the user no way back out of a restore they started: the
     * button went to "Restoring…" and disabled itself, so a 2 GB clip fetched by mistake had to run
     * to the end. Albums' Sync now had the same fault and fixed it the same way.
     */
    private var restoreJob: Job? = null

    init {
        loadFolders()
    }

    /**
     * Re-lists the drive when the screen is entered, unless the user is in the middle of something.
     *
     * `loadFolders` used to run only from `init`, and this ViewModel outlives a tab switch — so the
     * screen showed whatever the drive held when the app launched and had no way to be told
     * otherwise. Ian backed up the Anne album on 26 Aug 2026 and the Restore tab did not list it;
     * the only cure was killing the app. There was no refresh control either, because the one that
     * exists appears only after a failed listing.
     *
     * **Guarded, because re-entering is not the same as starting over.** Reloading while the user is
     * inside a folder, holding a selection, or restoring would throw away their position to answer a
     * question they did not ask. Idle is the only safe moment, and it is also the only moment the
     * staleness is visible.
     */
    fun refreshIfIdle() {
        val current = _state.value
        if (current.loading || current.isRestoring) return
        if (current.selectedFolder != null || current.hasSelection) return
        loadFolders()
    }

    fun loadFolders() {
        viewModelScope.launch {
            // Published before the listing starts. This is a DataStore read and the listing is a
            // network call, so making the path wait on the drive leaves the header reading bare
            // "OneDrive" for as long as the request takes.
            _state.value = _state.value.copy(
                loading = true,
                couldNotList = false,
                destinationPath = engine.destinationPath()
            )
            val folders = engine.cloudFolders()
            // Filtered here rather than in the engine: the engine's answer is what the drive holds,
            // and this is a question about what one screen shows. A later caller wanting the whole
            // truth should not have to undo a preference.
            val showEmpty = settings.current().showEmptyCloudFolders
            _state.value = _state.value.copy(
                folders = folders.orEmpty().filter { showEmpty || !it.isEmpty },
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
                // The selection is deliberately NOT cleared here, nor on the way back out. Picking
                // from several folders and restoring them together is the point.
                batchStatus = null,
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

    /** Back to the folder list. */
    fun closeFolder() {
        _state.value = _state.value.copy(
            selectedFolder = null,
            files = emptyList(),
            batchStatus = null,
            couldNotList = false
        )
    }

    /**
     * Picks a file up, or puts it down.
     *
     * Allowed for a file already on the phone. That is the point: the user asked for this one, and a
     * second copy under a `_restored` name is a cost they can see.
     */
    fun toggleSelection(file: RestorableFile) {
        if (_state.value.isRestoring) return
        val folder = _state.value.selectedFolder ?: return
        val current = _state.value.selection
        _state.value = _state.value.copy(
            selection = if (file.remoteItemId in current) {
                current - file.remoteItemId
            } else {
                current + (file.remoteItemId to SelectedFile(file, folder))
            },
            // A finished batch's result described the last selection, not this one.
            batchStatus = null
        )
    }

    /**
     * Selects everything in the open folder.
     *
     * Bulk **is** allowed here, and the difference from the Albums screen is worth being clear
     * about: there, a bulk action granted permission to upload, which is the thing CLAUDE.md's
     * opt-in gate exists to prevent. This copies files **down**. Nothing leaves the phone, nothing
     * is removed, the cloud copy is untouched, and the worst outcome is a duplicate the user can
     * delete. Non-destructive bulk was never the objection.
     */
    fun selectAll() {
        if (_state.value.isRestoring) return
        val folder = _state.value.selectedFolder ?: return
        _state.value = _state.value.copy(
            selection = _state.value.selection +
                _state.value.files.associate { it.remoteItemId to SelectedFile(it, folder) },
            batchStatus = null
        )
    }

    /** Swipe picked up a whole folder, or put it down again. */
    fun toggleFolderSelection(folder: RestorableFolder) {
        if (_state.value.isRestoring || folder.isEmpty) return
        val current = _state.value.selectedFolderNames
        _state.value = _state.value.copy(
            selectedFolderNames = if (folder.name in current) {
                current - folder.name
            } else {
                current + folder.name
            },
            batchStatus = null
        )
    }

    fun clearSelection() {
        if (_state.value.isRestoring) return
        _state.value = _state.value.copy(
            selection = emptyMap(),
            selectedFolderNames = emptySet(),
            batchStatus = null
        )
    }

    /**
     * Every file in the swiped folders that is not already on the phone.
     *
     * The skip is the one difference from picking files by hand. Tapping a file is a decision about
     * that file, and Ian was explicit it should be honoured whatever the gallery holds. A swiped
     * folder is not that: nobody looked at 194 rows, and fetching all of them to duplicate a folder
     * the phone already has is 4.1 GB spent on nothing. What was skipped is reported afterwards.
     *
     * `null` when a folder could not be listed — the batch is abandoned rather than run on a partial
     * answer, because "we could not ask" is not "there was nothing there".
     */
    private suspend fun filesInChosenFolders(): List<RestorableFile>? {
        _state.value = _state.value.copy(
            batchStatus = RestoreBatchStatus.Working(done = 0, total = _state.value.selectionCount)
        )
        val collected = mutableListOf<RestorableFile>()
        for (folder in _state.value.chosenFolders) {
            val listed = engine.restorableFilesIn(folder.name)
            if (listed == null) {
                _state.value = _state.value.copy(
                    batchStatus = null,
                    couldNotList = true
                )
                return null
            }
            collected += listed.filterNot { it.alreadyOnDevice }
        }
        return collected
    }

    /**
     * Fetches everything selected, one file at a time.
     *
     * Sequential on purpose. The old per-row buttons let someone start four transfers at once by
     * tapping four times, which competes for one connection and makes every one of them slower and
     * less legible. One at a time also means the progress figure is a real position in a queue.
     */
    fun restoreSelected() {
        if (_state.value.isRestoring) return

        restoreJob = viewModelScope.launch {
            // Files picked one by one, plus everything in any folder that was swiped. Both at
            // once, because the bar shows one total and the user asked for one action.
            val fromFolders = if (_state.value.selectedFolderNames.isEmpty()) {
                emptyList()
            } else {
                filesInChosenFolders() ?: return@launch
            }
            val alreadyPicked = _state.value.selectedFiles.map { it.remoteItemId }.toSet()
            val chosen = _state.value.selectedFiles +
                fromFolders.filterNot { it.remoteItemId in alreadyPicked }
            if (chosen.isEmpty()) {
                _state.value = _state.value.copy(
                    selection = emptyMap(),
                    selectedFolderNames = emptySet(),
                    batchStatus = RestoreBatchStatus.Done(restored = 0, failed = 0)
                )
                return@launch
            }

            var restored = 0
            var failed = 0
            var unsupported = false
            val gone = mutableListOf<String>()

            // A stop unwinds through here, so the summary is written in a `finally` rather than
            // after the loop. Without it, cancelling would leave the card saying "Restoring 3 of
            // 12" forever — `isRestoring` is derived from that status and nothing else clears it.
            //
            // The counters are only ever incremented by a file that finished, so a stopped batch
            // reports what it actually did rather than blaming the interrupted file.
            try {
                chosen.forEachIndexed { index, file ->
                    _state.value = _state.value.copy(
                        batchStatus = RestoreBatchStatus.Working(
                            done = index,
                            total = chosen.size,
                            currentFile = file.displayName
                        )
                    )

                    when (
                        restore.restore(
                            remoteItemId = file.remoteItemId,
                            displayName = file.displayName,
                            mimeType = file.mimeType,
                            isVideo = file.isVideo,
                            sizeBytes = file.sizeBytes,
                            onProgress = { written, total ->
                                _state.value = _state.value.copy(
                                    batchStatus = RestoreBatchStatus.Working(
                                        done = index,
                                        total = chosen.size,
                                        currentFile = file.displayName,
                                        percentOfCurrent = if (total > 0) {
                                            ((written * 100) / total).toInt().coerceIn(0, 100)
                                        } else {
                                            null
                                        }
                                    )
                                )
                            }
                        )
                    ) {
                        is RestoreResult.Restored -> restored++

                        // The listing said it was there and the fetch says it is not, so the
                        // listing is stale. Drop the row and say so by name — a row that simply
                        // vanished would look like the app losing things.
                        is RestoreResult.GoneFromCloud -> {
                            gone += file.displayName
                            failed++
                        }

                        // Below API 29 there is no way to publish a new media file at all, so the
                        // whole batch is impossible rather than this one file being unlucky.
                        is RestoreResult.Unsupported -> unsupported = true

                        is RestoreResult.Failed -> failed++
                    }
                }
            } finally {
                _state.value = _state.value.copy(
                    files = _state.value.files.filterNot { it.displayName in gone },
                    droppedFromCloud = _state.value.droppedFromCloud + gone,
                    selection = emptyMap(),
                    selectedFolderNames = emptySet(),
                    batchStatus = if (unsupported) {
                        RestoreBatchStatus.Unsupported
                    } else {
                        RestoreBatchStatus.Done(restored, failed)
                    }
                )
            }
        }
    }

    /**
     * Stops the batch, including the file in flight.
     *
     * Cancelling the job unwinds `MediaStoreWriter.write`, which checks `ensureActive()` on every
     * buffer — so a stopped restore stops pulling bytes rather than finishing the file it was on,
     * and the half-written row it was filling is discarded. That row was never visible to any app
     * and holds nothing the user had, so removing it is not a deletion under CLAUDE.md; it is the
     * same cleanup a short read or a dropped connection already triggers.
     *
     * Files already restored stay restored. Stopping is not undoing.
     *
     * **Verified on the Fold 4, 27 Aug 2026** — Ian ran it four ways: the card lands on a summary
     * rather than sticking on "Restoring 3 of 12", the count reflects only files that finished, the
     * interrupted file is not reported as one that could not be restored, and nothing partial is
     * left behind. Immediate, with no delay, on a 200 MB clip a quarter downloaded.
     */
    fun stopRestore() {
        restoreJob?.cancel()
    }
}
