package com.gallery.sync.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * The Signal palette.
 *
 * ### Where these numbers come from
 *
 * Authored in oklch and converted to sRGB, rather than picked by eye. Within a group the accents
 * share a lightness and a chroma and vary only in hue, which is what keeps Archive, Backup and Sync
 * reading as three members of one set instead of three unrelated colours — a property that survives
 * being converted but cannot be recovered once it is lost to hand-tuning.
 *
 * The source values live beside the design in `design/`; if one of these changes, change it there
 * and re-derive rather than nudging the hex.
 *
 * ### Nothing outside this package may name a colour
 *
 * CLAUDE.md forbids a literal colour anywhere in UI code, and this file is why that costs nothing:
 * every colour a screen needs is either a `MaterialTheme.colorScheme` role or a token on
 * [GallerySyncColors]. If a screen needs a colour that is neither, the answer is a new token here,
 * not a `Color(0xFF…)` at the call site.
 *
 * ### The dark values are not the light ones darkened
 *
 * Each dark tint was chosen against the dark surface it sits on. Status colours in particular lift
 * in lightness rather than deepening — a tint that reads correctly on white goes muddy and then
 * illegible on near-black, which is exactly the failure the dark-mode rule exists to prevent.
 */

// --- Light surfaces ---
val SignalBackgroundLight = Color(0xFFFAF8F3)
val SignalSurfaceLight = Color(0xFFFFFFFF)
val SignalSurfaceVariantLight = Color(0xFFECF1F5)
val SignalOutlineLight = Color(0xFFE0E5EB)
val SignalOnSurfaceLight = Color(0xFF182029)
val SignalOnSurfaceVariantLight = Color(0xFF606A74)

// --- Dark surfaces ---
val SignalBackgroundDark = Color(0xFF101419)
val SignalSurfaceDark = Color(0xFF1A1F24)
val SignalSurfaceVariantDark = Color(0xFF20252A)
val SignalOutlineDark = Color(0xFF292E34)
val SignalOnSurfaceDark = Color(0xFFEBEFF2)
val SignalOnSurfaceVariantDark = Color(0xFF94999E)

/**
 * The brand green, in both its roles.
 *
 * [SignalDeepGreen] is the hero card in light, where it is the darkest thing on the screen.
 * [SignalBrightGreen] is the hero card in dark, where it is the brightest. The card inverts rather
 * than darkening, so that on either theme it keeps its one job: being what the eye lands on first.
 */
val SignalDeepGreen = Color(0xFF003525)
val SignalDeepGreenText = Color(0xFFF0F7F3)
val SignalBrightGreen = Color(0xFF5BE479)

/**
 * The hero container in dark, and the correction to a mockup that did not survive a device.
 *
 * The design inverted the card — deep green on light, bright green on dark — so it would stay the
 * first thing the eye lands on. At 390dp in a picture that reads well. On the Fold's inner screen
 * the same card is nearly half the visible area, and a fill this saturated at that size is a wall
 * of colour that shouts down everything under it, including the album list it is meant to
 * introduce.
 *
 * So dark keeps a deep green container, lifted enough to separate from the near-black ground, and
 * the bright green stays what it is everywhere else: the accent, on the progress fill and the one
 * primary button.
 */
val SignalHeroDark = Color(0xFF074231)
val SignalOnHeroDark = Color(0xFFCCF3DD)
val SignalBrightGreenText = Color(0xFF002315)

// --- Mode tints, light ---
val SignalArchiveLight = Color(0xFFFFE9CB)
val SignalOnArchiveLight = Color(0xFF893C00)
val SignalBackupLight = Color(0xFFCDF6E3)
val SignalOnBackupLight = Color(0xFF005A37)
val SignalSyncLight = Color(0xFFD4F0FF)
val SignalOnSyncLight = Color(0xFF004F8B)

// --- Mode tints, dark ---
val SignalArchiveDark = Color(0xFF4C2905)
val SignalOnArchiveDark = Color(0xFFFFC87E)
val SignalBackupDark = Color(0xFF003825)
val SignalOnBackupDark = Color(0xFF81E8A0)
val SignalSyncDark = Color(0xFF142F4B)
val SignalOnSyncDark = Color(0xFF86CAFF)

/**
 * Off, in dark, needs its own tint rather than `surfaceVariant`.
 *
 * The pill sits on a card that is itself a near-black surface, and surfaceVariant is only a few
 * points away from it — on the device the container vanished and Off read as loose grey text
 * floating at the end of the row, not as one of four modes. Off should be the quietest pill, not an
 * invisible one.
 */
val SignalOffDark = Color(0xFF30363C)
val SignalOnOffDark = Color(0xFFB9BEC4)

/** The floating nav bar: dark in both themes, lifted in dark so it does not read as a hole. */
val SignalNavLight = Color(0xFF20262D)
val SignalNavDark = Color(0xFF2A3037)
val SignalOnNav = Color(0xFFD5DAE0)

// --- Error ---
val SignalErrorLight = Color(0xFFA51E24)
val SignalErrorDark = Color(0xFFF47B74)
val SignalErrorContainerLight = Color(0xFFFFDFDA)
val SignalErrorContainerDark = Color(0xFF551F1D)
