package com.gallery.sync.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.ui.semantics.Role
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Width below which a label and its control stop fitting side by side.
 *
 * Not a device size. It is scaled by the user's font setting at the point of use, because the thing
 * that actually runs out is room for *words*, and a larger font needs proportionally more of it.
 */
val CompactRowThreshold: Dp = 360.dp

/**
 * Lays a label and its action out on one row, or stacked when there is not room for both.
 *
 * ### Why a plain `Row` with `weight(1f)` is not enough
 *
 * Weight distributes what is **left over** after unweighted children measure at their preferred
 * width — so a control with a large minimum takes it first and the label receives the remainder,
 * however small that is. Material's `OutlinedTextField` defaults to a 280dp minimum, which on a
 * 320dp screen leaves a text column narrower than one word. The text then wraps a character at a
 * time and the row grows downwards until it is clipped.
 *
 * Observed on a folded Galaxy Z Fold 8, 24 Aug 2026: 320dp wide at `font_scale` 1.7, both of the
 * app's screens unusable. Those are the owner's own settings, not a stress case, and a user who
 * needs large text is exactly the user who hits this.
 *
 * So the fix is a layout decision rather than a weight: below the threshold the control moves under
 * the label, where it has the full width to itself and the label has the full width to itself.
 *
 * @param minRowWidth width needed at the default font size; scaled by the current font setting.
 */
@Composable
fun LabelWithAction(
    modifier: Modifier = Modifier,
    minRowWidth: Dp = CompactRowThreshold,
    spacing: Dp = 12.dp,
    action: @Composable (stacked: Boolean) -> Unit,
    label: @Composable () -> Unit
) {
    // Font scale matters as much as screen width: a 400dp screen at 2x has the same problem as a
    // 320dp screen at 1.6x, and keying only on width would call the second one comfortable.
    val fontScale = LocalDensity.current.fontScale.coerceAtLeast(1f)
    val needed = minRowWidth * fontScale

    BoxWithConstraints(modifier.fillMaxWidth()) {
        if (maxWidth >= needed) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(spacing)
            ) {
                Row(modifier = Modifier.weight(1f)) { label() }
                action(false)
            }
        } else {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(spacing)
            ) {
                label()
                action(true)
            }
        }
    }
}

/**
 * Whether the current width and font size leave room for a label beside its control.
 *
 * For callers that need to choose between two different controls rather than rearrange one — a
 * segmented button with three options cannot simply be stacked, it has to become something else.
 */
@Composable
fun isCompactWidth(availableWidth: Dp, minRowWidth: Dp = CompactRowThreshold): Boolean {
    val fontScale = LocalDensity.current.fontScale.coerceAtLeast(1f)
    return availableWidth < minRowWidth * fontScale
}

/**
 * A single-choice control that stops being a segmented row when the options cannot fit.
 *
 * A segmented button divides the available width between its options, so each one gets a fraction
 * of an already-narrow screen. At 320dp with 1.7x text, "System" wrapped onto two lines and swelled
 * that segment out of the pill, deforming the whole control — a shape no amount of rearranging
 * fixes, because the control itself is the wrong one at that size.
 *
 * Below the threshold this becomes a vertical list of radio options, which reads correctly at any
 * width and any font size.
 */
@Composable
fun <T> SingleChoiceControl(
    options: List<T>,
    selected: T,
    onSelected: (T) -> Unit,
    label: @Composable (T) -> String,
    modifier: Modifier = Modifier,
    minRowWidth: Dp = CompactRowThreshold
) {
    BoxWithConstraints(modifier.fillMaxWidth()) {
        if (isCompactWidth(maxWidth, minRowWidth)) {
            Column(Modifier.selectableGroup()) {
                options.forEach { option ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = option == selected,
                                onClick = { onSelected(option) },
                                role = Role.RadioButton
                            )
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        RadioButton(selected = option == selected, onClick = null)
                        Text(label(option))
                    }
                }
            }
        } else {
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                options.forEachIndexed { index, option ->
                    SegmentedButton(
                        selected = option == selected,
                        onClick = { onSelected(option) },
                        shape = SegmentedButtonDefaults.itemShape(index, options.size)
                    ) {
                        Text(label(option))
                    }
                }
            }
        }
    }
}
