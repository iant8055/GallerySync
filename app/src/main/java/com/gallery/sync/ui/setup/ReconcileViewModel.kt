package com.gallery.sync.ui.setup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gallery.sync.domain.backup.CloudReconciliation
import com.gallery.sync.data.local.settings.BackupSettings
import com.gallery.sync.domain.backup.ReconcileWithCloud
import com.gallery.sync.domain.backup.RemoteRoots
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
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
    val destinationRejected: Boolean = false
) {
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
    private val settings: BackupSettings
) : ViewModel() {

    private val _state = MutableStateFlow(ReconcileUiState())
    val state: StateFlow<ReconcileUiState> = _state.asStateFlow()

    private var job: Job? = null

    init {
        viewModelScope.launch {
            settings.preferences.collect { prefs ->
                _state.value = _state.value.copy(destinationRoot = prefs.destinationRoot)
            }
        }
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
