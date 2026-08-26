package com.gallery.sync.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * The colours Material 3 has no role for.
 *
 * ### Why this exists at all
 *
 * `MaterialTheme.colorScheme` covers primary, surface, error and their companions. It has no role
 * for "the tint that means Archive" — and there are four album modes whose whole job is to be told
 * apart at a glance, plus a hero card that inverts between themes.
 *
 * CLAUDE.md's rule is that no colour is named outside `ui/theme/`. Without somewhere like this, the
 * mode pills would have to hardcode a hex at the call site, which is precisely the shape of the bug
 * that shipped unreadable on the Teleprompter app. So the tokens live here and screens read them,
 * exactly as they read the Material roles.
 *
 * ### Paired, always
 *
 * Every tint carries its own `on` colour rather than leaving the caller to pick one. A container
 * colour without its text colour is an invitation to put `onSurface` on it and discover the
 * contrast failure on a device — and only in one theme.
 */
@Immutable
data class GallerySyncColors(

    /** The hero card. Dark on light, bright on dark — it inverts rather than darkening. */
    val heroContainer: Color,
    val onHero: Color,

    /** The accent used for the progress fill and the selected nav pill. */
    val accent: Color,
    val onAccent: Color,

    /** Album mode: files are copied up and nothing local changes. */
    val backupContainer: Color,
    val onBackupContainer: Color,

    /** Album mode: copied up, and photos may be replaced with smaller local copies. */
    val syncContainer: Color,
    val onSyncContainer: Color,

    /**
     * Album mode: copied up, then removed from the gallery.
     *
     * Warm rather than red, deliberately, and the only mode with a colour of its own consequence.
     * Archive is a choice the user makes, not an error state — but it is the one mode that takes
     * files off the phone, so it is the one that must never look like the other three.
     */
    val archiveContainer: Color,
    val onArchiveContainer: Color,

    /** Album mode: nothing happens. Deliberately the quietest thing on the screen. */
    val offContainer: Color,
    val onOffContainer: Color,

    /**
     * The floating navigation bar.
     *
     * Its own pair rather than `inverseSurface`, which is what it used first — and `inverseSurface`
     * does exactly what it says: dark under a light theme, near-white under a dark one. The bar
     * flipped to white in dark mode, which is not the design and reads as a different component.
     * The bar is dark in both themes; only how far it lifts off the background changes.
     */
    val navContainer: Color,
    val onNavContainer: Color
)

private val SignalLight = GallerySyncColors(
    heroContainer = SignalDeepGreen,
    onHero = SignalDeepGreenText,
    accent = SignalBrightGreen,
    onAccent = SignalBrightGreenText,
    backupContainer = SignalBackupLight,
    onBackupContainer = SignalOnBackupLight,
    syncContainer = SignalSyncLight,
    onSyncContainer = SignalOnSyncLight,
    archiveContainer = SignalArchiveLight,
    onArchiveContainer = SignalOnArchiveLight,
    offContainer = SignalSurfaceVariantLight,
    onOffContainer = SignalOnSurfaceVariantLight,
    navContainer = SignalNavLight,
    onNavContainer = SignalOnNav
)

private val SignalDark = GallerySyncColors(
    // Not inverted after all — see SignalHeroDark. A bright fill at hero size drowned the screen on
    // the Fold, so dark keeps a deep container and the bright green stays the accent.
    heroContainer = SignalHeroDark,
    onHero = SignalOnHeroDark,
    accent = SignalBrightGreen,
    onAccent = SignalBrightGreenText,
    backupContainer = SignalBackupDark,
    onBackupContainer = SignalOnBackupDark,
    syncContainer = SignalSyncDark,
    onSyncContainer = SignalOnSyncDark,
    archiveContainer = SignalArchiveDark,
    onArchiveContainer = SignalOnArchiveDark,
    offContainer = SignalOffDark,
    onOffContainer = SignalOnOffDark,
    // Lifted off the near-black ground so the bar still reads as floating rather than as a hole.
    navContainer = SignalNavDark,
    onNavContainer = SignalOnNav
)

internal fun signalColors(darkTheme: Boolean): GallerySyncColors =
    if (darkTheme) SignalDark else SignalLight

/**
 * Reached as `LocalGallerySyncColors.current`, beside `MaterialTheme.colorScheme`.
 *
 * `staticCompositionLocalOf` rather than `compositionLocalOf`: the value changes only when the theme
 * does, and at that point the whole tree recomposes anyway. Paying for fine-grained invalidation
 * would buy nothing.
 *
 * The default throws rather than returning a palette. A composable reading these outside
 * [GallerySyncTheme] is a wiring mistake, and a plausible-looking fallback would hide it until it
 * reached a device — where it would look like a colour bug rather than a missing theme.
 */
val LocalGallerySyncColors = staticCompositionLocalOf<GallerySyncColors> {
    error("LocalGallerySyncColors used outside GallerySyncTheme")
}
