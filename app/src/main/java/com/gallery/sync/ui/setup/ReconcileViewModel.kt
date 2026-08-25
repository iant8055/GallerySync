package com.gallery.sync.ui.setup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gallery.sync.domain.backup.CloudReconciliation
import com.gallery.sync.data.local.settings.BackupSettings
import android.net.Uri
import com.gallery.sync.data.local.media.GrantedDirectory
import com.gallery.sync.data.local.media.ScopedDirectories
import com.gallery.sync.util.ChargingState
import com.gallery.sync.domain.backup.FirstBackupHold
import com.gallery.sync.domain.backup.FirstBackupWindow
import com.gallery.sync.domain.backup.ReconcileWithCloud
import com.gallery.sync.domain.backup.RemoteRoots
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalTime
import javax.inject.Inject

/**
 * What the reconciliation step is showing.
 *
 * [result] is populated while the check is still running, not only at the end: the check makes one
 * network request per album and there are ninety of them, so a screen that waits for the total reads
 * as a hang. The figures climb instead.
 */
data class ReconcileUiState(
    val running: Boolean = false,
    val result: CloudReconciliation? = null,
    /** Media permission was refused, so nothing can be counted at all. Not the same as zero. */
    val noMediaAccess: Boolean = false,
    /** Where new uploads go. Only the destination — old roots stay searchable. */
    val destinationRoot: String = RemoteRoots.DEFAULT_DESTINATION,
    /** True while the user is choosing a new destination. */
    val choosingDestination: Boolean = false,
    /** Set when a typed path was refused, so the dialog can say so rather than closing silently. */
    val destinationRejected: Boolean = false,
    val firstBackupStartHour: Int = FirstBackupWindow.DEFAULT_START_HOUR,
    val firstBackupRequiresCharging: Boolean = true,
    /** Once true the window no longer applies and the section explains why it is gone. */
    val hasCompletedFirstBackup: Boolean = false,
    /** What is currently holding the first run, or null if nothing is. */
    val firstBackupHold: FirstBackupHold? = null,
    /** Gate 1. Until this has something in it, the engine has nothing correct to do. */
    val directories: List<GrantedDirectory> = emptyList(),
    /** Set when a picked tree could not be used, so the screen can say why. */
    val directoryRefused: Boolean = false
) {
    /**
     * Whether Gate 1 has been answered.
     *
     * The reconciliation is hidden until it has. With nothing granted the scan returns nothing, and
     * a screen reporting zero outstanding files would announce that everything is already backed up
     * — which is false, and false in the direction that stops someone acting.
     */
    val hasSources: Boolean get() = directories.isNotEmpty()
    /**
     * Whether changing the destination now would leave already-backed-up files behind.
     *
     * It would not — [RemoteRoots] keeps the old root searchable — and this exists so the dialog can
     * say so with a number instead of asking the user to take it on trust.
     */
    val alreadyFoundHere: Int get() = result?.backedUp?.files ?: 0
}

@HiltViewModel
class ReconcileViewModel @Inject constructor(
    private val reconcile: ReconcileWithCloud,
    private val settings: BackupSettings,
    private val charging: ChargingState,
    private val sources: ScopedDirectories
) : ViewModel() {

    private val _state = MutableStateFlow(ReconcileUiState())
    val state: StateFlow<ReconcileUiState> = _state.asStateFlow()

    private var job: Job? = null

    init {
        viewModelScope.launch {
            // Grants can be revoked outside the app. Checking once at start keeps the list from
            // claiming a folder is watched when nothing in it is readable any more.
            sources.forgetRevokedGrants()
            sources.directories.collect { dirs ->
                // Any change to the granted set makes the current figures wrong: a new folder brings
                // albums they knew nothing about, and a removed one leaves them overstated. Deciding
                // that here rather than in each caller means no path can forget to re-check.
                val changed = _state.value.directories.map { it.treeUri }.toSet() !=
                    dirs.map { it.treeUri }.toSet()
                _state.value = _state.value.copy(directories = dirs)

                if (!changed) return@collect
                if (dirs.isEmpty()) {
                    _state.value = _state.value.copy(result = null, running = false)
                } else {
                    start()
                }
            }
        }
        viewModelScope.launch {
            settings.preferences.collect { prefs ->
                _state.value = _state.value.copy(
                    destinationRoot = prefs.destinationRoot,
                    firstBackupStartHour = prefs.firstBackupStartHour,
                    firstBackupRequiresCharging = prefs.firstBackupRequiresCharging,
                    hasCompletedFirstBackup = prefs.hasCompletedFirstBackup,
                    // Recomputed whenever a setting changes, so moving the start time updates the
                    // "waiting until" line immediately rather than at the next run.
                    firstBackupHold = if (prefs.hasCompletedFirstBackup) {
                        null
                    } else {
                        FirstBackupWindow.heldBecause(
                            hourOfDay = LocalTime.now().hour,
                            isCharging = charging.isCharging(),
                            startHour = prefs.firstBackupStartHour,
                            requiresCharging = prefs.firstBackupRequiresCharging
                        )
                    }
                )
            }
        }
    }

    /** Records a folder the user picked. The re-check follows from the grant list changing. */
    fun addSource(treeUri: Uri) {
        viewModelScope.launch {
            // The suspending call must complete *before* the state read, not inside it. Written as
            // `_state.value = _state.value.copy(refused = !sources.add(uri))`, Kotlin evaluates the
            // `.copy` receiver first, suspends in `add()` while the directories collector writes the
            // new folder into state, then applies `.copy` to the stale snapshot and puts it back —
            // silently undoing the grant on screen while the data layer was perfectly correct.
            // Observed on hardware 25 Aug 2026: the folder appeared only after a restart.
            val added = sources.add(treeUri)
            _state.value = _state.value.copy(directoryRefused = !added)
        }
    }

    fun removeSource(treeUri: String) {
        viewModelScope.launch { sources.remove(treeUri) }
    }

    fun setFirstBackupStartHour(hour: Int) {
        viewModelScope.launch { settings.setFirstBackupStartHour(hour) }
    }

    fun setFirstBackupRequiresCharging(required: Boolean) {
        viewModelScope.launch { settings.setFirstBackupRequiresCharging(required) }
    }

    fun openDestinationChooser() {
        _state.value = _state.value.copy(choosingDestination = true, destinationRejected = false)
    }

    fun dismissDestinationChooser() {
        _state.value = _state.value.copy(choosingDestination = false, destinationRejected = false)
    }

    /**
     * Changes where new uploads go.
     *
     * Does **not** re-run the check afterwards. The figures on screen stay true, because the old
     * root remains in the search set — that is the whole point of separating destination from
     * search, and re-running would spend ninety requests to print the same numbers.
     */
    fun setDestination(path: String) {
        viewModelScope.launch {
            if (settings.setDestinationRoot(path)) {
                _state.value = _state.value.copy(
                    choosingDestination = false,
                    destinationRejected = false
                )
            } else {
                _state.value = _state.value.copy(destinationRejected = true)
            }
        }
    }

    /**
     * Starts, or restarts, the check.
     *
     * Cancels any run already in flight rather than letting two overlap — each one issues a request
     * per album, and a user tapping "check again" twice should not double the traffic.
     */
    fun start() {
        job?.cancel()
        job = viewModelScope.launch {
            _state.value = _state.value.copy(running = true, result = null)

            val total = reconcile.run { partial ->
                _state.value = _state.value.copy(result = partial)
            }

            _state.value = _state.value.copy(
                running = false,
                result = total,
                noMediaAccess = total == null
            )
        }
    }

    override fun onCleared() {
        job?.cancel()
        super.onCleared()
    }
}
