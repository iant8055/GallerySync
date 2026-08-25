package com.gallery.sync.ui.setup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gallery.sync.domain.backup.CloudReconciliation
import com.gallery.sync.domain.backup.ReconcileWithCloud
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
    val noMediaAccess: Boolean = false
)

@HiltViewModel
class ReconcileViewModel @Inject constructor(
    private val reconcile: ReconcileWithCloud
) : ViewModel() {

    private val _state = MutableStateFlow(ReconcileUiState())
    val state: StateFlow<ReconcileUiState> = _state.asStateFlow()

    private var job: Job? = null

    /**
     * Starts, or restarts, the check.
     *
     * Cancels any run already in flight rather than letting two overlap — each one issues a request
     * per album, and a user tapping "check again" twice should not double the traffic.
     */
    fun start() {
        job?.cancel()
        job = viewModelScope.launch {
            _state.value = ReconcileUiState(running = true, result = null)

            val total = reconcile.run { partial ->
                _state.value = _state.value.copy(result = partial)
            }

            _state.value = ReconcileUiState(
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
