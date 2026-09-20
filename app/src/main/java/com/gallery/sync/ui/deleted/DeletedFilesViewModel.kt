package com.gallery.sync.ui.deleted

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gallery.sync.data.local.settings.BackupSettings
import com.gallery.sync.domain.backup.BackupEngine
import com.gallery.sync.domain.backup.CloudDeletionPolicy
import com.gallery.sync.domain.backup.DeletedFile
import com.gallery.sync.domain.backup.DeletionOutcome
import com.gallery.sync.domain.backup.SyncDeletionsToCloud
import com.gallery.sync.domain.backup.TrashBackupOutcome
import com.gallery.sync.util.Logger
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Where the window is: not showing, asking, acting, or reporting what it did. */
enum class DeletedFilesPhase { HIDDEN, LISTING, WORKING, DONE }

/**
 * Which of the two windows is up.
 *
 * - [IN_CLOUD]: *these files have been deleted but are backed up on the Cloud. What do you want to do
 *   with the Cloud copies?* **Keep** or **Delete**.
 * - [NOT_IN_CLOUD]: *these files have been deleted but no backup can be found. What do you want to do
 *   with these files?* **Remain in Trash** or **Back up to Cloud**.
 */
enum class DeletedFilesStep { IN_CLOUD, NOT_IN_CLOUD }

data class DeletedFilesUiState(
    val phase: DeletedFilesPhase = DeletedFilesPhase.HIDDEN,
    /**
     * True until the first look has finished, so the app is not drawn before it is known whether the
     * window has to come first. Only the first look holds the app back; later ones, when the app comes
     * back to the front, are silent.
     */
    val checking: Boolean = false,
    /** True while OneDrive is being asked, which can take a while for a large album. */
    val askingOneDrive: Boolean = false,
    val step: DeletedFilesStep = DeletedFilesStep.IN_CLOUD,
    /** Everything undecided that has a copy in OneDrive. All of it is listed, old and new. */
    val inCloud: List<DeletedFile> = emptyList(),
    /** Everything undecided, in the trash, with no copy found. */
    val notInCloud: List<DeletedFile> = emptyList(),
    /** The ids ticked in the window that is up. **Empty to start with, and again on each window.** */
    val selected: Set<String> = emptySet(),
    /** True while the removal confirmation is up. Nothing is removed until it is answered. */
    val confirming: Boolean = false,

    // What has been done so far, kept until the window reports at the end.
    val removal: DeletionOutcome? = null,
    val cloudCopiesKept: Int = 0,
    val backup: TrashBackupOutcome? = null,
    val leftInTrash: Int = 0,

    /** While backing up: how far along, and what is being sent. */
    val progressDone: Int = 0,
    val progressTotal: Int = 0,
    val progressName: String = ""
) {
    /** The files of the window that is up. */
    val files: List<DeletedFile>
        get() = if (step == DeletedFilesStep.IN_CLOUD) inCloud else notInCloud
    val selectedFiles: List<DeletedFile> get() = files.filter { it.id in selected }
    val selectedBytes: Long get() = selectedFiles.sumOf { it.sizeBytes }
    val totalBytes: Long get() = files.sumOf { it.sizeBytes }

    /** Whether there is a second window still to come after the first. */
    val hasSecondWindow: Boolean
        get() = step == DeletedFilesStep.IN_CLOUD && notInCloud.isNotEmpty()

    /** Whether anything was decided in this session, so leaving is a report rather than a vanishing. */
    val hasResult: Boolean
        get() = removal != null || cloudCopiesKept > 0 || backup != null || leftInTrash > 0
}

/**
 * The window that opens with the app when files have left the phone since it last appeared.
 * Ian, 19 Sept 2026.
 *
 * ### The rules, as he gave them
 *
 * - **It covers all deleted files**, not only the ones this app backed up, in two windows one after
 *   the other, each shown only if it has files: first the files with a copy in OneDrive (keep or delete
 *   that copy), then the files with none (leave them in the trash or back them up).
 * - **Shows only when there are new files**: something has left the phone since the window was last
 *   shown. Opening the app with nothing new does nothing. Shown at most once for the same files; the
 *   moment it appears its "seen up to" stamp is set, so leaving without deciding does not bring it
 *   back on every open. **Unless OneDrive could not be asked**: then some files could not be placed
 *   and the stamp is not set, so they are offered again next time.
 * - **Every file that has not been decided stays in it until a decision is made.** It lists all of
 *   them, old and new, not only the latest.
 * - **Nothing is ticked to start with.** A ticked file gets the active choice (its OneDrive copy is
 *   deleted; the file is backed up) and an unticked one gets the passive one (its copy is kept; the
 *   file stays in the trash), so not ticking is a decision too.
 * - **No delay.** A file is offered the next time the window can show.
 * - Only under **Ask**, and only once setup is finished (the gate is not placed in the wizard).
 *
 * Showing the window is never consent to a removal. That needs the user to tick a file and then
 * confirm ([askToRemove], then [confirmRemoval]); nothing on a timeout, a back press or a dismissal
 * removes anything. Backing a file up adds a copy and removes nothing, so it needs no second step.
 */
@HiltViewModel
class DeletedFilesViewModel @Inject constructor(
    private val engine: BackupEngine,
    private val deletions: SyncDeletionsToCloud,
    private val settings: BackupSettings
) : ViewModel() {

    private val _state = MutableStateFlow(DeletedFilesUiState(checking = true))
    val state: StateFlow<DeletedFilesUiState> = _state.asStateFlow()

    private var lastLookedAt = 0L
    private var lookJob: Job? = null

    /**
     * Looks for files that have left the phone since the window was last shown, and shows it if there
     * are any. Called when the app comes to the front.
     *
     * Never disturbs a window that is already up or working, and looks at most once a minute so that
     * switching away and straight back is not a reason to scan again.
     */
    fun evaluate(now: Long = System.currentTimeMillis()) {
        if (_state.value.phase != DeletedFilesPhase.HIDDEN) return
        if (lookJob?.isActive == true) return
        if (now - lastLookedAt < MIN_GAP_MILLIS) {
            stopChecking()
            return
        }
        lastLookedAt = now

        lookJob = viewModelScope.launch {
            try {
                look()
            } finally {
                // However it ended: nothing to show, something to show, skipped, or failed. The app must
                // never be left waiting on a look that has finished.
                stopChecking()
            }
        }
    }

    /**
     * Stops waiting for OneDrive and lets the app through. Nothing is decided and nothing is marked as
     * seen, so the files are offered again the next time the window can show.
     */
    fun skipCheck() {
        lookJob?.cancel()
        stopChecking()
    }

    private fun stopChecking() {
        val current = _state.value
        if (current.checking || current.askingOneDrive) {
            _state.value = current.copy(checking = false, askingOneDrive = false)
        }
    }

    private suspend fun look() {
        val prefs = settings.current()
        // Under Leave nothing is offered and nothing is even scanned for.
        if (prefs.cloudDeletionPolicy != CloudDeletionPolicy.ASK) return

        // Stamps files that have left the phone since the last scan, so this sees them.
        engine.refreshLedger()

        val seen = prefs.deletionPromptSeenUpToEpochMillis
        val offer = deletions.offer(newerThan = seen) {
            _state.value = _state.value.copy(askingOneDrive = true)
        }
        if (offer.isEmpty || offer.newestDeparture <= seen) return

        // Stamped as it is shown, not only when the user decides, so leaving without deciding
        // does not bring the window back for the same files. Not stamped when OneDrive could not
        // be asked about some of them: those are still to be placed, and the next open tries again.
        if (offer.complete) settings.setDeletionPromptSeenUpTo(offer.newestDeparture)
        Logger.i(
            TAG,
            "showing ${offer.inCloud.size} with a copy in OneDrive, ${offer.notInCloud.size} without" +
                if (offer.complete) "" else " (some could not be placed)"
        )

        if (_state.value.phase == DeletedFilesPhase.HIDDEN) {
            _state.value = DeletedFilesUiState(
                phase = DeletedFilesPhase.LISTING,
                step = if (offer.inCloud.isNotEmpty()) DeletedFilesStep.IN_CLOUD else DeletedFilesStep.NOT_IN_CLOUD,
                inCloud = offer.inCloud,
                notInCloud = offer.notInCloud
            )
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

    /**
     * Leaves this window without deciding anything. Its files stay undecided and stay in the list for
     * next time. From the first window it goes on to the second, if there is one, and from the last it
     * reports what was done, or puts the window away if nothing was.
     */
    fun decideLater() {
        val current = _state.value
        if (current.phase != DeletedFilesPhase.LISTING) return
        _state.value = next(current)
    }

    /** Settles every file in this window the passive way, whatever is ticked. Bookkeeping only. */
    fun keepAll() {
        val current = _state.value
        if (current.phase != DeletedFilesPhase.LISTING) return

        viewModelScope.launch {
            _state.value = current.copy(phase = DeletedFilesPhase.WORKING, confirming = false)
            deletions.keep(current.files)
            _state.value = next(withKept(current, current.files.size))
        }
    }

    /** Opens the removal confirmation. Deliberately separate from acting on it, and needs something ticked. */
    fun askToRemove() {
        val current = _state.value
        if (current.phase != DeletedFilesPhase.LISTING || current.step != DeletedFilesStep.IN_CLOUD) return
        if (current.selected.isEmpty()) return
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
        if (current.phase != DeletedFilesPhase.LISTING || current.step != DeletedFilesStep.IN_CLOUD) return
        if (!current.confirming) return
        val approved = current.selectedFiles
        if (approved.isEmpty()) return
        val left = current.files - approved.toSet()

        viewModelScope.launch {
            _state.value = current.copy(phase = DeletedFilesPhase.WORKING, confirming = false)
            val outcome = deletions.delete(approved)
            // A file that could not be removed is not decided: it stays undecided. The unticked ones
            // are the user's decision to leave them.
            deletions.keep(left)
            _state.value = next(withKept(current.copy(removal = outcome), left.size))
        }
    }

    /**
     * Backs up the ticked files from the phone's trash, and leaves the unticked ones in it. Adds a
     * copy and removes nothing, so unlike a removal it needs no confirmation.
     *
     * A file that could not be sent keeps its record and is offered again next time; the outcome says
     * how many.
     */
    fun backUpSelected() {
        val current = _state.value
        if (current.phase != DeletedFilesPhase.LISTING || current.step != DeletedFilesStep.NOT_IN_CLOUD) return
        val approved = current.selectedFiles
        if (approved.isEmpty()) return
        val left = current.files - approved.toSet()

        viewModelScope.launch {
            _state.value = current.copy(
                phase = DeletedFilesPhase.WORKING,
                progressDone = 0,
                progressTotal = approved.size
            )
            val outcome = engine.backUpFromTrash(approved) { done, total, name ->
                _state.value = _state.value.copy(progressDone = done, progressTotal = total, progressName = name)
            }
            deletions.keep(left)
            _state.value = next(withKept(current.copy(backup = outcome), left.size))
        }
    }

    /** Puts the window away once it has reported. */
    fun finish() {
        if (_state.value.phase == DeletedFilesPhase.DONE) _state.value = DeletedFilesUiState()
    }

    /** Counts files settled the passive way against the window they were in. */
    private fun withKept(state: DeletedFilesUiState, count: Int): DeletedFilesUiState =
        if (state.step == DeletedFilesStep.IN_CLOUD) {
            state.copy(cloudCopiesKept = state.cloudCopiesKept + count)
        } else {
            state.copy(leftInTrash = state.leftInTrash + count)
        }

    /** What comes after a window: the second one, or the report, or nothing if nothing was decided. */
    private fun next(state: DeletedFilesUiState): DeletedFilesUiState = when {
        state.hasSecondWindow -> state.copy(
            phase = DeletedFilesPhase.LISTING,
            step = DeletedFilesStep.NOT_IN_CLOUD,
            selected = emptySet(),
            confirming = false
        )

        state.hasResult -> state.copy(phase = DeletedFilesPhase.DONE, selected = emptySet(), confirming = false)
        else -> DeletedFilesUiState()
    }

    private companion object {
        const val TAG = "DeletedFiles"
        const val MIN_GAP_MILLIS = 60_000L
    }
}
