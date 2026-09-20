package com.gallery.sync.ui.retrieve

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gallery.sync.data.local.settings.BackupSettings
import com.gallery.sync.domain.backup.CloudDeletionPolicy
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DeletionUiState(
    val policy: CloudDeletionPolicy = CloudDeletionPolicy.DEFAULT
)

/**
 * The deletion setting, Leave or Ask, and nothing else.
 *
 * The waiting period, the review list and the confirmation that lived here were removed on 19 Sept
 * 2026 (Ian) and replaced by the window that opens with the app (`ui/deleted/DeletedFilesViewModel`),
 * which uses `SyncDeletionsToCloud`.
 */
@HiltViewModel
class DeletionViewModel @Inject constructor(
    private val settings: BackupSettings
) : ViewModel() {

    private val _state = MutableStateFlow(DeletionUiState())
    val state: StateFlow<DeletionUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            settings.preferences.collect { prefs ->
                _state.value = _state.value.copy(policy = prefs.cloudDeletionPolicy)
            }
        }
    }

    fun setPolicy(policy: CloudDeletionPolicy) {
        viewModelScope.launch { settings.setCloudDeletionPolicy(policy) }
    }
}
