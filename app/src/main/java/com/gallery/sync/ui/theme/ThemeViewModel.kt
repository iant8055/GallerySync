package com.gallery.sync.ui.theme

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gallery.sync.data.local.settings.AppearanceSettings
import com.gallery.sync.data.local.settings.ThemeMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * The chosen theme, held where both the activity and the settings screen can reach it.
 *
 * Seeded with [ThemeMode.DEFAULT] rather than a nullable, so the first frame draws following the
 * system instead of flashing one theme and correcting itself.
 */
@HiltViewModel
class ThemeViewModel @Inject constructor(
    private val appearance: AppearanceSettings
) : ViewModel() {

    val themeMode: StateFlow<ThemeMode> = appearance.themeMode.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = ThemeMode.DEFAULT
    )

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { appearance.setThemeMode(mode) }
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
