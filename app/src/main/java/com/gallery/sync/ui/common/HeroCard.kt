package com.gallery.sync.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) { HeroFigure(label, figure) }
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        detail()
                        actions()
                    }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    HeroFigure(label, figure)
                    detail()
                    actions()
                }
            }
        }
    }
}

/** The label and the number. The one thing on a tab that is not a detail of something else. */
@Composable
private fun HeroFigure(label: String, figure: String) {
    Text(text = label, style = MaterialTheme.typography.labelMedium)
    Text(text = figure, style = MaterialTheme.typography.displaySmall)
}

/** Where a phone screen stops being one narrow column. Matches the album and folder grids. */
val HeroWideBreakpoint = 600.dp
