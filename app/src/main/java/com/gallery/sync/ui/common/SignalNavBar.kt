package com.gallery.sync.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.gallery.sync.ui.theme.LocalGallerySyncColors

/** One place to go. */
data class NavDestination(val icon: ImageVector, val label: String)

/**
 * The app's four destinations, as a floating pill.
 *
 * ### Why it moved off the top
 *
 * It was a `ScrollableTabRow`, which had two problems that were not really about styling. Four tabs
 * did not fit, so "Restore" sat off the right edge and was reachable only by scrolling a row most
 * people do not know scrolls — a whole quarter of the app hidden behind an undiscoverable gesture.
 * And a top row on a phone this tall is the hardest part of the screen to reach one-handed.
 *
 * ### Only the selected one carries its label
 *
 * Four icons with four labels does not fit at 320dp with large text — the failure the tab row had.
 * Labelling only the current destination keeps the bar to one line at any font scale, and the label
 * is least needed for the three you are not looking at. It is also what says where you are, which a
 * tinted icon alone says weakly.
 *
 * ### Below the content, not over it
 *
 * The design floats it over a scrolling list. Doing that properly means every screen passing a
 * bottom `contentPadding`, and a screen that forgets leaves its last row permanently under the bar.
 * Sitting below the content costs a little of the effect and cannot hide anything.
 */
@Composable
fun SignalNavBar(
    destinations: List<NavDestination>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val signal = LocalGallerySyncColors.current

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            // Capped and centred rather than stretched. On the Fold's inner screen a full-width bar
            // puts its destinations 800px apart, which is a thumb-reach problem on a device held at
            // the edges.
            .widthIn(max = 480.dp),
        shape = RoundedCornerShape(percent = 50),
        color = MaterialTheme.colorScheme.inverseSurface,
        contentColor = MaterialTheme.colorScheme.inverseOnSurface,
        shadowElevation = 8.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            destinations.forEachIndexed { index, destination ->
                val isSelected = index == selected
                Surface(
                    shape = RoundedCornerShape(percent = 50),
                    color = if (isSelected) signal.accent else MaterialTheme.colorScheme.inverseSurface,
                    contentColor = if (isSelected) {
                        signal.onAccent
                    } else {
                        MaterialTheme.colorScheme.inverseOnSurface
                    },
                    onClick = { onSelect(index) }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = destination.icon,
                            // The label already names the selected destination, and naming it twice
                            // makes a screen reader say it twice.
                            contentDescription = if (isSelected) null else destination.label,
                            modifier = Modifier.size(20.dp)
                        )
                        if (isSelected) {
                            Text(
                                text = destination.label,
                                style = MaterialTheme.typography.labelLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}
