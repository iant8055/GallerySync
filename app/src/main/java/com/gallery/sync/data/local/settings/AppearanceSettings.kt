package com.gallery.sync.data.local.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** Which theme the app uses, regardless of what the phone is set to. */
enum class ThemeMode {

    /** Follow the phone. The default, and what most people expect. */
    SYSTEM,

    LIGHT,

    DARK;

    companion object {
        val DEFAULT = SYSTEM
    }
}

/**
 * Which font the app draws its text in (Ian, 24 Oct 2026). Its own Noto Sans by default, so the app looks the same on
 * every phone however the phone's font style is set; or the phone's own font for anyone who prefers it. Text SIZE always
 * follows the phone's setting either way: that is an accessibility choice and is not the app's to override.
 */
enum class FontChoice {
    APP,
    PHONE;

    companion object {
        val DEFAULT = APP
    }
}

private val Context.appearanceDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "appearance_settings"
)

/**
 * The app's own light/dark choice.
 *
 * Kept apart from `BackupSettings` deliberately: that store is about what happens to the user's
 * files, and mixing an appearance preference into it would make a store whose name stops describing
 * its contents. This one is read before anything is drawn, so it is small on purpose.
 *
 * Stored as the enum's name rather than its ordinal, for the reason the other converters give:
 * inserting a constant later must not silently reinterpret what people already chose.
 */
@Singleton
class AppearanceSettings @Inject constructor(
    @ApplicationContext private val context: Context
) {

    val themeMode: Flow<ThemeMode> = context.appearanceDataStore.data.map { stored ->
        stored[KEY_THEME_MODE]
            ?.let { name -> runCatching { ThemeMode.valueOf(name) }.getOrNull() }
            ?: ThemeMode.DEFAULT
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.appearanceDataStore.edit { it[KEY_THEME_MODE] = mode.name }
    }

    val fontChoice: Flow<FontChoice> = context.appearanceDataStore.data.map { stored ->
        stored[KEY_FONT]
            ?.let { name -> runCatching { FontChoice.valueOf(name) }.getOrNull() }
            ?: FontChoice.DEFAULT
    }

    suspend fun setFontChoice(choice: FontChoice) {
        context.appearanceDataStore.edit { it[KEY_FONT] = choice.name }
    }

    private companion object {
        val KEY_THEME_MODE = stringPreferencesKey("theme_mode")
        val KEY_FONT = stringPreferencesKey("font_choice")
    }
}
