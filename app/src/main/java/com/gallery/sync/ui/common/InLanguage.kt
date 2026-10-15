package com.gallery.sync.ui.common

import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.content.res.Resources
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import java.util.Locale

/**
 * Draws [content] with its text read in [languageTag] (BCP-47, `pt-BR`), while the rest of the app stays English.
 *
 * Ian, 14 Oct 2026: only the setup wizard is in several languages ("that way we can be very precise about the
 * wording"); the app itself has no languages but the guide's. So the Activity is pinned to English
 * (`MainActivity.attachBaseContext`) and the wizard alone is wrapped in this. The wrapper keeps the Activity as its
 * base, so starting an activity or opening a link from inside the wizard behaves exactly as before; only
 * [Context.getResources] answers in the chosen language. A string with no translation falls back to English.
 */
@Composable
fun InLanguage(languageTag: String, content: @Composable () -> Unit) {
    val base = LocalContext.current
    val configuration = LocalConfiguration.current
    val localised = remember(base, configuration, languageTag) {
        val config = Configuration(configuration).apply { setLocale(Locale.forLanguageTag(languageTag)) }
        // From the application context, not the Activity's: the Activity is pinned to English, and a configuration
        // context made from it kept answering in English on the Moto G. (Building `Resources` directly is worse: it
        // shares the AssetManager, so whichever language was set last won for every screen.)
        val resources = base.applicationContext.createConfigurationContext(config).resources
        object : ContextWrapper(base) {
            override fun getResources(): Resources = resources
        } to config
    }
    CompositionLocalProvider(
        LocalContext provides localised.first,
        LocalConfiguration provides localised.second,
        // stringResource reads LocalResources (Compose 1.8+), not LocalContext.resources.
        androidx.compose.ui.platform.LocalResources provides localised.first.resources
    ) {
        content()
    }
}

/**
 * Resources that answer in [languageTag], for text read outside any [InLanguage] wrapper, such as the (?) pop-ups.
 *
 * Ian, 14 Oct 2026: the point of a multilingual guide is that the (?) cards speak the chosen language, while the
 * screens around them stay English. A string with no translation falls back to English.
 */
@Composable
fun rememberResourcesIn(languageTag: String): Resources {
    val base = LocalContext.current
    val configuration = LocalConfiguration.current
    return remember(base, configuration, languageTag) {
        val config = Configuration(configuration).apply { setLocale(Locale.forLanguageTag(languageTag)) }
        base.applicationContext.createConfigurationContext(config).resources
    }
}

/** The app's own text is English whatever the phone's language (Ian, 14 Oct 2026); see [InLanguage]. */
fun Context.pinnedToEnglish(): Context {
    val config = Configuration(resources.configuration).apply { setLocale(Locale.ENGLISH) }
    return createConfigurationContext(config)
}
