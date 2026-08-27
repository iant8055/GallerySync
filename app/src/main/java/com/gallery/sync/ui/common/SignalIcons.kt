package com.gallery.sync.ui.common

import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * The four navigation icons, drawn rather than depended on.
 *
 * The project has no icon library on its classpath and has never used one. Adding a dependency to
 * draw four glyphs is a poor trade, and the ready-made sets do not contain the two that matter here
 * anyway — "cloud with a tick" and "cloud with a down arrow" are the app's own vocabulary.
 *
 * These are the exact paths from the design, on the same 24-unit grid with the same 2dp stroke, so
 * the built app and the canvas cannot drift apart.
 *
 * Stroked, never filled. A filled icon set would need each glyph redrawn as a solid silhouette, and
 * at nav-bar size the stroked versions carry the cloud shapes far more legibly.
 */
object SignalIcons {

    val Albums: ImageVector = stroked("albums") {
        listOf("M3 7a2 2 0 0 1 2-2h4l2 2h8a2 2 0 0 1 2 2v8a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z")
    }

    /**
     * Built from [CloudCheck]'s cloud, not its own.
     *
     * The first attempt used a different cloud outline — one open arc rather than a closed shape —
     * and on the device its arcs collapsed to a stray hook while the tick icon beside it drew
     * correctly. Sharing the proven outline fixes it and is better anyway: restore and cloud check
     * are both operations on the cloud copy, so they should read as siblings.
     */
    val Restore: ImageVector = stroked("restore") {
        listOf(
            "M17.5 19a4.5 4.5 0 0 0 .5-9 6 6 0 0 0-11.6-1.6A4.2 4.2 0 0 0 7 19z",
            "M12 10v6",
            "m9.5 13.5 2.5 2.5 2.5-2.5"
        )
    }

    val CloudCheck: ImageVector = stroked("cloudCheck") {
        listOf(
            "M17.5 19a4.5 4.5 0 0 0 .5-9 6 6 0 0 0-11.6-1.6A4.2 4.2 0 0 0 7 19z",
            "m9 13 2 2 4-4"
        )
    }

    /** Marks a selected row. */
    val Check: ImageVector = stroked("check") {
        listOf("M20 6 9 17l-5-5")
    }

    /**
     * A file that could not be confirmed, and so is staying on the phone.
     *
     * Paired with [Check] on the Archive screen, where the two marks are the whole vocabulary: a
     * tick means the full version is in OneDrive, a cross means we could not establish that. Drawn
     * at the same weight and size as the tick deliberately — the cross is not an error the user
     * caused, it is the app declining to remove something it cannot vouch for.
     */
    val Cross: ImageVector = stroked("cross") {
        listOf("M18 6 6 18", "m6 6 12 12")
    }

    /**
     * This pill shows a value *and* changes it.
     *
     * The album row's mode pill reads "Sync", which is the state, not what tapping does — so
     * nothing about it says it can be tapped at all. Ian, 27 Aug 2026. A caret is the standard way
     * out of that: the label stays the current value, because that is the thing worth reading at a
     * glance down a list, and the caret carries "and you can change it".
     */
    val ChevronDown: ImageVector = stroked("chevronDown") {
        listOf("m6 9 6 6 6-6")
    }

    /** A folder row leads somewhere. */
    val ChevronRight: ImageVector = stroked("chevronRight") {
        listOf("m9 18 6-6-6-6")
    }

    val Settings: ImageVector = stroked("settings") {
        listOf(
            "M12 9a3 3 0 1 0 0 6 3 3 0 0 0 0-6z",
            "M19.4 15a1.7 1.7 0 0 0 .3 1.9l.1.1a2 2 0 1 1-2.8 2.8l-.1-.1a1.7 1.7 0 0 0-2.9 1.2v.2a2 2 0 1 1-4 0v-.1a1.7 1.7 0 0 0-1.1-1.6 1.7 1.7 0 0 0-1.9.4l-.1.1a2 2 0 1 1-2.8-2.8l.1-.1a1.7 1.7 0 0 0-1.2-2.9H3a2 2 0 1 1 0-4h.1a1.7 1.7 0 0 0 1.6-1.1 1.7 1.7 0 0 0-.4-1.9l-.1-.1a2 2 0 1 1 2.8-2.8l.1.1a1.7 1.7 0 0 0 1.9.3H10a1.7 1.7 0 0 0 1-1.5V3a2 2 0 1 1 4 0v.1a1.7 1.7 0 0 0 2.9 1.2l.1-.1a2 2 0 1 1 2.8 2.8l-.1.1a1.7 1.7 0 0 0-.3 1.9V10a1.7 1.7 0 0 0 1.5 1H21a2 2 0 1 1 0 4h-.1a1.7 1.7 0 0 0-1.5 1z"
        )
    }

    /**
     * Builds a stroked 24dp icon from SVG path data.
     *
     * `tintColor = SolidColor(Color.Black)` is a placeholder the caller always overrides: `Icon`
     * applies its own tint, and leaving the stroke unspecified would draw nothing at all.
     */
    private fun stroked(name: String, paths: () -> List<String>): ImageVector =
        ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            paths().forEach { data ->
                addPath(
                    pathData = addPathNodes(data),
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = 2f,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round
                )
            }
        }.build()
}
