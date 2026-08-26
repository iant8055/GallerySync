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
sealed interface RestoreBatchStatus {

    data class Working(val done: Int, val total: Int) : RestoreBatchStatus

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
     * Remote item ids the user has picked.
     *
     * Selection replaced a Restore button on every row. Ian, 25 Aug 2026: select the file itself
     * rather than click a button on it. Three things fall out of that — the rows lose the control
     * that was fighting them for width, empty folders stop carrying a disabled button that means
     * nothing, and **several files can be restored in one go**, which the app could not do at all.
     * Tapping four buttons started four unrelated transfers at once.
     */
    val selectedIds: Set<String> = emptySet(),
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
    val selectedFiles: List<RestorableFile> get() = files.filter { it.remoteItemId in selectedIds }

    val chosenFolders: List<RestorableFolder> get() = folders.filter { it.name in selectedFolderNames }

    /** What the bar is holding, whichever level the user is on. */
    val selectionCount: Int
        get() = if (selectedFolder == null) chosenFolders.sumOf { it.fileCount } else selectedFiles.size

    val selectedBytes: Long
        get() = if (selectedFolder == null) {
            chosenFolders.sumOf { it.sizeBytes }
        } else {
            selectedFiles.sumOf { it.sizeBytes }
        }

    val hasSelection: Boolean
        get() = if (selectedFolder == null) selectedFolderNames.isNotEmpty() else selectedIds.isNotEmpty()

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

    init {
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
                // A selection belongs to the folder it was made in. Carrying it across would mean
                // acting on files the user can no longer see.
                selectedIds = emptySet(),
                selectedFolderNames = emptySet(),
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
            selectedIds = emptySet(),
            selectedFolderNames = emptySet(),
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
        val current = _state.value.selectedIds
        _state.value = _state.value.copy(
            selectedIds = if (file.remoteItemId in current) {
                current - file.remoteItemId
            } else {
                current + file.remoteItemId
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
        _state.value = _state.value.copy(
            selectedIds = _state.value.files.map { it.remoteItemId }.toSet(),
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
            selectedIds = emptySet(),
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

        viewModelScope.launch {
            val chosen = if (_state.value.selectedFolder == null) {
                filesInChosenFolders() ?: return@launch
            } else {
                _state.value.selectedFiles
            }
            if (chosen.isEmpty()) {
                _state.value = _state.value.copy(
                    selectedFolderNames = emptySet(),
                    batchStatus = RestoreBatchStatus.Done(restored = 0, failed = 0)
                )
                return@launch
            }

            var restored = 0
            var failed = 0
            var unsupported = false
            val gone = mutableListOf<String>()

            chosen.forEachIndexed { index, file ->
                _state.value = _state.value.copy(
                    batchStatus = RestoreBatchStatus.Working(index, chosen.size)
                )

                when (
                    restore.restore(
                        remoteItemId = file.remoteItemId,
                        displayName = file.displayName,
                        mimeType = file.mimeType,
                        isVideo = file.isVideo,
                        sizeBytes = file.sizeBytes
                    )
                ) {
                    is RestoreResult.Restored -> restored++

                    // The listing said it was there and the fetch says it is not, so the listing is
                    // stale. Drop the row and say so by name — a row that simply vanished would look
                    // like the app losing things.
                    is RestoreResult.GoneFromCloud -> {
                        gone += file.displayName
                        failed++
                    }

                    // Below API 29 there is no way to publish a new media file at all, so the whole
                    // batch is impossible rather than this one file being unlucky.
                    is RestoreResult.Unsupported -> unsupported = true

                    is RestoreResult.Failed -> failed++
                }
            }

            _state.value = _state.value.copy(
                files = _state.value.files.filterNot { it.displayName in gone },
                droppedFromCloud = _state.value.droppedFromCloud + gone,
                selectedIds = emptySet(),
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
