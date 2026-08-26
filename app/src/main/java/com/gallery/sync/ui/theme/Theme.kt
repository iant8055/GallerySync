package com.gallery.sync.ui.theme

import android.os.Build
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val LightColorScheme = lightColorScheme(
    primary = SignalDeepGreen,
    onPrimary = SignalDeepGreenText,
    primaryContainer = SignalBackupLight,
    onPrimaryContainer = SignalOnBackupLight,
    secondary = SignalOnSyncLight,
    onSecondary = SignalSurfaceLight,
    secondaryContainer = SignalSyncLight,
    onSecondaryContainer = SignalOnSyncLight,
    tertiary = SignalOnArchiveLight,
    onTertiary = SignalSurfaceLight,
    tertiaryContainer = SignalArchiveLight,
    onTertiaryContainer = SignalOnArchiveLight,
    background = SignalBackgroundLight,
    onBackground = SignalOnSurfaceLight,
    surface = SignalSurfaceLight,
    onSurface = SignalOnSurfaceLight,
    surfaceVariant = SignalSurfaceVariantLight,
    onSurfaceVariant = SignalOnSurfaceVariantLight,
    outline = SignalOutlineLight,
    outlineVariant = SignalOutlineLight,
    error = SignalErrorLight,
    onError = SignalSurfaceLight,
    errorContainer = SignalErrorContainerLight,
    onErrorContainer = SignalErrorLight
)

private val DarkColorScheme = darkColorScheme(
    // In dark the bright green is the primary, not the deep one: on a near-black surface the deep
    // green is nearly invisible, and primary is meant to be the colour you notice.
    primary = SignalBrightGreen,
    onPrimary = SignalBrightGreenText,
    primaryContainer = SignalBackupDark,
    onPrimaryContainer = SignalOnBackupDark,
    secondary = SignalOnSyncDark,
    onSecondary = SignalSurfaceDark,
    secondaryContainer = SignalSyncDark,
    onSecondaryContainer = SignalOnSyncDark,
    tertiary = SignalOnArchiveDark,
    onTertiary = SignalSurfaceDark,
    tertiaryContainer = SignalArchiveDark,
    onTertiaryContainer = SignalOnArchiveDark,
    background = SignalBackgroundDark,
    onBackground = SignalOnSurfaceDark,
    surface = SignalSurfaceDark,
    onSurface = SignalOnSurfaceDark,
    surfaceVariant = SignalSurfaceVariantDark,
    onSurfaceVariant = SignalOnSurfaceVariantDark,
    outline = SignalOutlineDark,
    outlineVariant = SignalOutlineDark,
    error = SignalErrorDark,
    onError = SignalBrightGreenText,
    errorContainer = SignalErrorContainerDark,
    onErrorContainer = SignalErrorDark
)

/**
 * ### Dynamic colour is off, and that is the point
 *
 * It defaulted to **on**, which on Android 12+ discards this palette entirely and paints the app
 * from the user's wallpaper. That was invisible while the scheme was the untouched template purple —
 * the app on the Fold 4 rendered wallpaper blue and nobody noticed the declared colours were never
 * used.
 *
 * With a designed palette it stops being invisible and starts being the whole problem: Archive's
 * warm tint, the hero card's green and the mode pills' relationship to one another would all be
 * replaced by whatever the wallpaper happens to be, on most devices in the world.
 *
 * The parameter stays so it can be turned on deliberately if that is ever wanted. The default does
 * not.
 */
@Composable
fun GallerySyncTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val colorScheme = when {
        // Kept reachable rather than deleted, but never the default. Turning it on means accepting
        // that every colour decision in the design is discarded on Android 12+.
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)

        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    // The status bar icons follow the *system* theme, not ours. Override the app to Dark while the
    // phone is in Light and the clock and battery go dark-on-dark — unreadable, and exactly the
    // class of bug the dark-mode rule in CLAUDE.md exists to prevent. Telling the window which way
    // round we are drawing is what keeps them legible.
    val view = LocalView.current
    val activity = LocalActivity.current
    if (!view.isInEditMode && activity != null) {
        SideEffect {
            WindowCompat.getInsetsController(activity.window, view)
                .isAppearanceLightStatusBars = !darkTheme
        }
    }

    CompositionLocalProvider(LocalGallerySyncColors provides signalColors(darkTheme)) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}
