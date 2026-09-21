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
 * [SignalDeepGreen] is the light theme's `primary` — buttons, switches, the text-coloured green.
 * It was also the hero card's fill until 18 Sept 2026, when the hero was lifted to [SignalHeroLight]
 * because it read too dark (Ian). The two are separate values on purpose: lightening the hero must
 * not recolour every button in the app.
 *
 * [SignalBrightGreen] is the accent, on the progress fill and the selected nav pill.
 */
val SignalDeepGreen = Color(0xFF003525)

/**
 * Text that says a file is safe: "backed up".
 *
 * A clearly green, not the deep brand green. [SignalDeepGreen] is `primary` in the light theme and is
 * nearly black (`#003525`), so text in it read as dark grey with a hint of green and did not say
 * "safe" at a glance. Ian, 19 Sept 2026: make it more obviously green.
 *
 * `#157F37` measures about 5.1:1 on white and 4.7:1 on the off-white surface, which clears the 4.5:1
 * that 14sp body text needs. A brighter green looked better and failed that. The dark theme keeps
 * [SignalBrightGreen], which is already obviously green on a near-black surface.
 */
val SignalSafeGreenLight = Color(0xFF157F37)

/**
 * The hero container in light: the heading box at the top of each tab, and the Settings section
 * headings that match it.
 *
 * Lifted from [SignalDeepGreen] (`#003525`) after Ian found it came off too dark. Still deep enough
 * that [SignalDeepGreenText] on it is about 8:1, so the card and the mode pills on it stay readable.
 */
val SignalHeroLight = Color(0xFF0A5238)
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
val SignalHeroDark = Color(0xFF0B5039) // lifted from #074231 on 18 Sept 2026, with the light hero.
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

/**
 * The bezel the tour draws its backdrops inside. Near-black in both themes, because that is what a
 * phone's frame is — and the frame is what tells the user they are looking at a picture of the app.
 */
val SignalPhoneFrame = Color(0xFF0B0B0D)

/**
 * The bands that make the bezel read as the edge of a device rather than a line with round corners.
 *
 * Outside in: a thin grey rim, a wider light band catching the light, a dark groove, a light band
 * again, a thin grey rim, then the black body. Ian, 15 Sept 2026, with a crop of a real phone edge.
 */
val SignalPhoneEdgeRim = Color(0xFF6E7278)
val SignalPhoneEdgeHighlight = Color(0xFFD7DADE)
val SignalPhoneEdgeGroove = Color(0xFF3A3D42)

/** The floating nav bar: dark in both themes, lifted in dark so it does not read as a hole. */
val SignalNavLight = Color(0xFF20262D)
val SignalNavDark = Color(0xFF2A3037)
val SignalOnNav = Color(0xFFD5DAE0)

/**
 * The ground the welcome picture sits on. It is the flat colour the picture itself is painted on
 * (`#003322`, sampled from its edges), so on a screen taller or wider than the picture the spare room
 * is the same green and the picture has no visible edge. The phone in it runs off the bottom, which is why
 * the picture is pinned to the bottom of the screen and the spare room is left above it.
 */
val SignalWelcomeGround = Color(0xFF003322)

// --- Error ---
val SignalErrorLight = Color(0xFFA51E24)
val SignalErrorDark = Color(0xFFF47B74)
val SignalErrorContainerLight = Color(0xFFFFDFDA)
val SignalErrorContainerDark = Color(0xFF551F1D)
