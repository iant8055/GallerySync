package com.gallery.sync.ui.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkManager
import com.gallery.sync.data.local.dao.BackupEntryDao
import com.gallery.sync.data.local.settings.BackupSettings
import com.gallery.sync.worker.LocationRepairWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** What Settings shows about the location repair. See `LocationRepair`. */
data class LocationRepairUiState(
    val loaded: Boolean = false,
    /** Location access is held, so a repair can work. */
    val available: Boolean = false,
    /** Files sent before the fix that a repair would look at, and their size. */
    val candidates: Int = 0,
    val candidateBytes: Long = 0L,
    val running: Boolean = false,
    val checked: Int = 0,
    val repaired: Int = 0,
    val noLocation: Int = 0,
    val finished: Boolean = false
)

@HiltViewModel
class LocationRepairViewModel @Inject constructor(
    private val entryDao: BackupEntryDao,
    private val settings: BackupSettings,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _state = MutableStateFlow(LocationRepairUiState())
    val state: StateFlow<LocationRepairUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            settings.preferences.collect { prefs ->
                val totals = if (prefs.locationFixedAt > 0L) entryDao.locationRepairTotals(prefs.locationFixedAt) else null
                _state.value = LocationRepairUiState(
                    loaded = true,
                    available = prefs.locationFixedAt > 0L,
                    candidates = totals?.files ?: 0,
                    candidateBytes = totals?.bytes ?: 0L,
                    running = prefs.locationRepairRunning,
                    checked = prefs.locationRepairChecked,
                    repaired = prefs.locationRepairRepaired,
                    noLocation = prefs.locationRepairNoLocation,
                    finished = prefs.locationRepairFinishedAt > 0L
                )
            }
        }
    }

    /** Yes in the confirmation. The only way a repair starts. */
    fun start() {
        viewModelScope.launch {
            settings.startLocationRepair()
            LocationRepairWorker.enqueue(
                WorkManager.getInstance(context),
                settings.current().allowMeteredNetwork,
                androidx.work.ExistingWorkPolicy.REPLACE
            )
        }
    }
}
