package com.gallery.sync.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gallery.sync.data.local.settings.BackupSettings
import com.gallery.sync.domain.model.AppLanguage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * The one language choice, made on the Welcome screen and shown and changed in Settings. Ian, 14 Oct 2026: the
 * third deliberate exception to the wizard and Settings being independent (CLAUDE.md): this value, and only this
 * one, is shared by both.
 */
@HiltViewModel
class LanguageViewModel @Inject constructor(
    private val settings: BackupSettings
) : ViewModel() {

    val language: StateFlow<AppLanguage> = settings.preferences
        .map { AppLanguage.fromCode(it.appLanguage) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppLanguage.DEFAULT)

    fun setLanguage(language: AppLanguage) {
        viewModelScope.launch { settings.setAppLanguage(language.code) }
    }
}
