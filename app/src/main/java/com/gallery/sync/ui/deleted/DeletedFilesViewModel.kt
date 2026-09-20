package com.gallery.sync.ui.deleted

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gallery.sync.data.local.entity.BackupEntryEntity
import com.gallery.sync.data.local.settings.BackupSettings
import com.gallery.sync.domain.backup.BackupEngine
import com.gallery.sync.domain.backup.CloudDeletionPolicy
import com.gallery.sync.domain.backup.DeletionOutcome
import com.gallery.sync.domain.backup.SyncDeletionsToCloud
import com.gallery.sync.util.Logger
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Where the window is: not showing, asking, acting, or reporting what it did. */
enum class DeletedFilesPhase { HIDDEN, LISTING, WORKING, DONE }

data class DeletedFilesUiState(
    val phase: DeletedFilesPhase = DeletedFilesPhase.HIDDEN,
    /** Everything undecided that has left the phone. All of it is listed, old and new. */
    val files: List<BackupEntryEntity> = emptyList(),
    /** The ids the user has ticked for removal from OneDrive. **Empty to start with.** */
    val selected: Set<String> = emptySet(),
    /** True while the confirmation is up. Nothing is removed until it is answered. */
    val confirming: Boolean = false,
    /** What the removal did, once it has. Null after Keep all. */
    val outcome: DeletionOutcome? = null,
    /** How many files were left in OneDrive by the user's choice this time. */
    val keptCount: Int = 0
) {
    val selectedFiles: List<BackupEntryEntity> get() = files.filter { it.id in selected }
    val selectedBytes: Long get() = selectedFiles.sumOf { it.sizeBytes }
    val totalBytes: Long get() = files.sumOf { it.sizeBytes }
}

/**
 * The window that opens with the app when files have left the phone since it last appeared.
 * Ian, 19 Sept 2026.
 *
 * ### The rules, as he gave them
 *
 * - **Shows only when there are new files**: something has left the phone since the window was last
 *   shown. Opening the app with nothing new does nothing. Shown at most once for the same files; the
 *   moment it appears its "seen up to" stamp is set, so leaving without deciding does not bring it
 *   back on every open.
 * - **Every file that has not been decided stays in it until a decision is made.** It lists all of
 *   them, old and new, not only the latest.
 * - **Nothing is ticked to start with.** A file is removed from OneDrive only if the user ticks it
 *   and then confirms. Not ticking is a choice too: **unticked files are left in OneDrive for good**,
 *   until they come back to the phone and go again.
 * - **No delay.** A file is offered the next time the window can show.
 * - Only under **Ask**, and only once setup is finished (the gate is not placed in the wizard).
 *
 * Showing the window is never consent. Removal needs the user to tick a file and then confirm
 * ([askToRemove], then [confirmRemoval]); nothing on a timeout, a back press or a dismissal removes
 * anything.
 */
@HiltViewModel
class DeletedFilesViewModel @Inject constructor(
    private val engine: BackupEngine,
    private val deletions: SyncDeletionsToCloud,
    private val settings: BackupSettings
) : ViewModel() {

    private val _state = MutableStateFlow(DeletedFilesUiState())
    val state: StateFlow<DeletedFilesUiState> = _state.asStateFlow()

    private var lastLookedAt = 0L

    /**
     * Looks for files that have left the phone since the window was last shown, and shows it if there
     * are any. Called when the app comes to the front.
     *
     * Never disturbs a window that is already up or working, and looks at most once a minute so that
     * switching away and straight back is not a reason to scan again.
     */
    fun evaluate(now: Long = System.currentTimeMillis()) {
        if (_state.value.phase != DeletedFilesPhase.HIDDEN) return
        if (now - lastLookedAt < MIN_GAP_MILLIS) return
        lastLookedAt = now

        viewModelScope.launch {
            val prefs = settings.current()
            // Under Leave nothing is offered and nothing is even scanned for.
            if (prefs.cloudDeletionPolicy != CloudDeletionPolicy.ASK) return@launch

            // Stamps files that have left the phone since the last scan, so this sees them.
            engine.refreshLedger()

            val offer = deletions.candidates()
            val newest = offer.maxOfOrNull { it.localMissingSinceEpochMillis ?: 0L } ?: return@launch
            if (newest <= prefs.deletionPromptSeenUpToEpochMillis) return@launch

            // Stamped as it is shown, not only when the user decides, so leaving without deciding
            // does not bring the window back for the same files.
            settings.setDeletionPromptSeenUpTo(newest)
            Logger.i(TAG, "showing ${offer.size} files that have left the phone")

            if (_state.value.phase == DeletedFilesPhase.HIDDEN) {
                _state.value = DeletedFilesUiState(phase = DeletedFilesPhase.LISTING, files = offer)
            }
        }
    }

    fun toggle(id: String) {
        val current = _state.value
        if (current.phase != DeletedFilesPhase.LISTING) return
        _state.value = current.copy(
            selected = if (id in current.selected) current.selected - id else current.selected + id
        )
    }

    fun selectAll() {
        val current = _state.value
        if (current.phase != DeletedFilesPhase.LISTING) return
        _state.value = current.copy(selected = current.files.mapTo(HashSet()) { it.id })
    }

    fun clearSelection() {
        val current = _state.value
        if (current.phase != DeletedFilesPhase.LISTING) return
        _state.value = current.copy(selected = emptySet())
    }

    /** Leaves without deciding anything. The files stay undecided and stay in the list for next time. */
    fun decideLater() {
        if (_state.value.phase != DeletedFilesPhase.LISTING) return
        _state.value = DeletedFilesUiState()
    }

    /** Leaves every file's OneDrive copy alone, whatever is ticked. Bookkeeping only. */
    fun keepAll() {
        val current = _state.value
        if (current.phase != DeletedFilesPhase.LISTING) return

        viewModelScope.launch {
            _state.value = current.copy(phase = DeletedFilesPhase.WORKING, confirming = false)
            deletions.keep(current.files)
            _state.value = current.copy(
                phase = DeletedFilesPhase.DONE,
                selected = emptySet(),
                confirming = false,
                outcome = null,
                keptCount = current.files.size
            )
        }
    }

    /** Opens the confirmation. Deliberately separate from acting on it, and needs something ticked. */
    fun askToRemove() {
        val current = _state.value
        if (current.phase != DeletedFilesPhase.LISTING || current.selected.isEmpty()) return
        _state.value = current.copy(confirming = true)
    }

    /** Closes the confirmation and changes nothing: the ticks stay where they were. */
    fun dismissConfirmation() {
        _state.value = _state.value.copy(confirming = false)
    }

    /**
     * Removes the OneDrive copies of the ticked files, which the user has just confirmed, and leaves
     * the unticked ones alone for good.
     *
     * Hands the engine the exact list that was confirmed. Re-deriving it here would mean the user
     * agreed to a set of files and the app acted on a different one, however slightly.
     */
    fun confirmRemoval() {
        val current = _state.value
        if (current.phase != DeletedFilesPhase.LISTING || !current.confirming) return
        val approved = current.selectedFiles
        if (approved.isEmpty()) return
        val left = current.files - approved.toSet()

        viewModelScope.launch {
            _state.value = current.copy(phase = DeletedFilesPhase.WORKING, confirming = false)
            val outcome = deletions.delete(approved)
            // A file that could not be removed is not decided: it stays undecided. The unticked ones
            // are the user's decision to leave them.
            deletions.keep(left)
            _state.value = current.copy(
                phase = DeletedFilesPhase.DONE,
                confirming = false,
                outcome = outcome,
                keptCount = left.size
            )
        }
    }

    /** Puts the window away once it has reported. */
    fun finish() {
        if (_state.value.phase == DeletedFilesPhase.DONE) _state.value = DeletedFilesUiState()
    }

    private companion object {
        const val TAG = "DeletedFiles"
        const val MIN_GAP_MILLIS = 60_000L
    }
}
