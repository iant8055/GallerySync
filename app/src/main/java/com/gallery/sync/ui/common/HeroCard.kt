package com.gallery.sync.ui.common

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.gallery.sync.ui.theme.LocalGallerySyncColors

/**
 * The card every tab opens with: a label, one figure, some detail, and the tab's own controls.
 *
 * ### Why this is shared rather than copied
 *
 * Albums had it first and the other tabs opened with a plain title, so the app looked like three
 * products wearing one nav bar. Ian, 26 Aug 2026: make Restore and Archive match Albums. Copying the
 * card three times would have matched them *today* — one component means they cannot drift the next
 * time the hero changes, which is the actual request.
 *
 * ### The figure is a string, deliberately
 *
 * Each tab counts something different, and one of them is not always a number: Albums renders an em
 * dash until its counts are read, because a confident `0` on a cold start is a claim and a wrong one.
 * Taking the already-formatted text keeps that decision with the screen that understands it instead
 * of pushing a nullable through here.
 *
 * ### Wide layout
 *
 * Past the expanded-width breakpoint the figure keeps the left and the detail and controls take the
 * right. Unfolded, a single column left two thirds of the card as empty green — the figure and the
 * controls both sat in the narrow column a 390dp screen forces, and the card stretched around a hole.
 */
@Composable
fun HeroCard(
    label: String,
    figure: String,
    modifier: Modifier = Modifier,
    /**
     * Replaces the label-and-figure block entirely.
     *
     * Albums uses it: that tab is where modes are chosen, so its lead is a heading and the four mode
     * buttons rather than a count of anything. The other two tabs pass nothing and get the figure.
     */
    figureContent: (@Composable ColumnScope.() -> Unit)? = null,
    /**
     * A line under the figure, inside the same centred column.
     *
     * For a second number that qualifies the first rather than commenting on it. Restore uses it
     * for the selection: "6" is what the drive holds, "13 selected" is how much of it is spoken
     * for, and the two belong stacked under one label. Ian, 27 Aug 2026 — it had been sitting in
     * [detail], on top of that tab's swipe instructions, where it read as a third gesture.
     *
     * Ignored when [figureContent] is given, which replaces this whole block.
     */
    figureFooter: (@Composable ColumnScope.() -> Unit)? = null,
    /**
     * Pushes [actions] to the foot of the card instead of letting them follow [detail].
     *
     * Only worth it where the other column is the taller one — Albums, whose heading and 2 x 2 of
     * modes set the card's height. There the controls used to float mid-air beside the second row
     * of buttons; sitting on the bottom edge they read as the card's controls rather than as a
     * third thing in the middle of it. Ian, 27 Aug 2026.
     */
    actionsAtBottom: Boolean = false,
    detail: @Composable ColumnScope.() -> Unit = {},
    actions: @Composable ColumnScope.() -> Unit = {}
) {
    val signal = LocalGallerySyncColors.current

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = signal.heroContainer,
        contentColor = signal.onHero
    ) {
        BoxWithConstraints(modifier = Modifier.padding(20.dp)) {
            if (maxWidth >= HeroWideBreakpoint) {
                Row(
                    // IntrinsicSize.Min, so the fillMaxHeight below resolves against the taller
                    // COLUMN rather than against the incoming constraint. Without it the card grew
                    // to the height of the screen and pushed the album list off the bottom.
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(if (actionsAtBottom) Modifier.height(IntrinsicSize.Min) else Modifier),
                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                    // Tops, always. Centred, a column that grows re-centres itself and everything
                    // in it moves: on Restore, selecting files added a line under the figure and
                    // the label above it jumped upward, which is a label moving to report
                    // something that did not happen to it. Ian, 27 Aug 2026. Pinned to the top,
                    // the label and the first line of the detail column start at the same height
                    // and stay there whatever either column does underneath.
                    verticalAlignment = Alignment.Top
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        if (figureContent != null) figureContent() else HeroFigure(label, figure, figureFooter)
                    }
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .then(if (actionsAtBottom) Modifier.fillMaxHeight() else Modifier),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        if (actionsAtBottom) {
                            // Equal spacers above and below, so the text sits in the middle of the
                            // space it has rather than clinging to the top of the card while the
                            // controls hold the bottom. Ian, 27 Aug 2026. Indented too: the column
                            // starts hard against the gap, and a little inset settles it into its
                            // half instead of leaning on the mode buttons.
                            Spacer(modifier = Modifier.weight(1f))
                            Column(
                                modifier = Modifier.padding(start = 16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) { detail() }
                            Spacer(modifier = Modifier.weight(1f))
                        } else {
                            detail()
                        }
                        actions()
                    }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    if (figureContent != null) figureContent() else HeroFigure(label, figure, figureFooter)
                    detail()
                    actions()
                }
            }
        }
    }
}

/**
 * An outlined button that stays legible on the hero's filled container.
 *
 * Material derives an `OutlinedButton`'s content from the **scheme's** primary, not from the
 * surface it is sitting on — which on the dark green hero comes out dim and grey, exactly the
 * washed-out look Ian reported on "Check these files" on 27 Aug 2026. Taking `LocalContentColor`
 * instead means the label is the same brightness as the text beside it, in either theme, because
 * the hero has already set that colour for its own contents.
 *
 * Lived privately in `BackupScreen` first. Shared here when Archive and Restore gained heroes and
 * immediately reproduced the bug it had already solved — which is the argument for the shared card
 * making itself again.
 */
@Composable
fun HeroOutlinedButton(onClick: () -> Unit, label: String, modifier: Modifier = Modifier) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = LocalContentColor.current),
        border = BorderStroke(1.dp, LocalContentColor.current.copy(alpha = 0.35f))
    ) {
        Text(label, maxLines = 1)
    }
}

/**
 * The label and the number. The one thing on a tab that is not a detail of something else.
 *
 * The figure is centred under its label rather than aligned to the start of the column. Ian,
 * 27 Aug 2026. Left-aligned, a short number sat off under the first two or three characters of a
 * long label and read as unrelated to it; centred, the pair reads as one object — which is what it
 * is, a caption and the thing it names.
 *
 * The label is `titleLarge`, not the `labelMedium` a caption would normally take. Ian, 27 Aug 2026,
 * on the Archive tab: with the finished-state sentences gone the card is a label and a number, and a
 * 12sp caption under a 36sp figure read as a footnote to it rather than as its name. Raised again
 * the same day, to sit level with the detail column beside it. Restore takes the same step up —
 * one card, so the tabs cannot drift.
 */
@Composable
private fun HeroFigure(
    label: String,
    figure: String,
    footer: (@Composable ColumnScope.() -> Unit)?
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center
        )
        Text(text = figure, style = MaterialTheme.typography.displaySmall)
        // A gap, not the column's default nothing. The footer is a second number and it was
        // touching the first — "6" and "13 selected" ran together as one block. Ian, 27 Aug 2026.
        // Only when there is a footer: the caller passes null rather than an empty lambda, so an
        // unselected card has no stray space under its figure.
        if (footer != null) {
            Spacer(modifier = Modifier.height(10.dp))
            footer()
        }
    }
}

/** Where a phone screen stops being one narrow column. Matches the album and folder grids. */
val HeroWideBreakpoint = 600.dp
