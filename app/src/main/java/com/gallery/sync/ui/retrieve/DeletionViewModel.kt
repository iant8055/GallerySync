package com.gallery.sync.ui.retrieve

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gallery.sync.data.local.entity.BackupEntryEntity
import com.gallery.sync.data.local.settings.BackupSettings
import com.gallery.sync.domain.backup.CloudDeletionGrace
import com.gallery.sync.domain.backup.CloudDeletionPolicy
import com.gallery.sync.domain.backup.DeletionOutcome
import com.gallery.sync.domain.backup.SyncDeletionsToCloud
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DeletionUiState(
    val policy: CloudDeletionPolicy = CloudDeletionPolicy.DEFAULT,
    val graceDays: Int = CloudDeletionGrace.DEFAULT_DAYS,
    /** Files gone from the phone whose cloud copies could be removed. Empty unless policy is ASK. */
    val candidates: List<BackupEntryEntity> = emptyList(),
    /** True while the confirmation dialog is up. Nothing is deleted until it is answered. */
    val confirming: Boolean = false,
    val working: Boolean = false,
    val lastOutcome: DeletionOutcome? = null
) {
    val totalBytes: Long get() = candidates.sumOf { it.sizeBytes }
}

/**
 * The review list for deletion sync, and the confirmation in front of it.
 *
 * The screen exists so that a person looks at named files and says yes to *those*. That is the whole
 * safeguard: everything underneath — the grace period, the re-scan, the policy check — narrows what
 * may be offered, and none of it is a substitute for someone deciding.
 */
@HiltViewModel
class DeletionViewModel @Inject constructor(
    private val deletions: SyncDeletionsToCloud,
    private val settings: BackupSettings
) : ViewModel() {

    private val _state = MutableStateFlow(DeletionUiState())
    val state: StateFlow<DeletionUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            settings.preferences.collect { prefs ->
                val changed = _state.value.policy != prefs.cloudDeletionPolicy ||
                    _state.value.graceDays != prefs.cloudDeletionGraceDays
                _state.value = _state.value.copy(
                    policy = prefs.cloudDeletionPolicy,
                    graceDays = prefs.cloudDeletionGraceDays
                )
                if (changed) refresh()
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            // `candidates` returns nothing at all under LEAVE, so the list cannot be shown by
            // mistake — the policy is enforced where the data comes from, not where it is drawn.
            _state.value = _state.value.copy(candidates = deletions.candidates())
        }
    }

    fun setPolicy(policy: CloudDeletionPolicy) {
        viewModelScope.launch { settings.setCloudDeletionPolicy(policy) }
    }

    fun setGraceDays(days: Int) {
        viewModelScope.launch { settings.setCloudDeletionGraceDays(days) }
    }

    /** Opens the confirmation. Deliberately separate from acting on it. */
    fun askToConfirm() {
        if (_state.value.candidates.isEmpty()) return
        _state.value = _state.value.copy(confirming = true, lastOutcome = null)
    }

    fun dismissConfirmation() {
        _state.value = _state.value.copy(confirming = false)
    }

    /**
     * Removes the cloud copies the user just confirmed.
     *
     * Hands the engine the exact list that was on screen. Re-deriving it here would mean the user
     * agreed to a set of files and the app acted on a different one, however slightly.
     */
    fun confirmDeletion() {
        val approved = _state.value.candidates
        if (approved.isEmpty()) return

        viewModelScope.launch {
            _state.value = _state.value.copy(confirming = false, working = true)
            val outcome = deletions.delete(approved)
            _state.value = _state.value.copy(
                working = false,
                lastOutcome = outcome,
                candidates = deletions.candidates()
            )
        }
    }
}
