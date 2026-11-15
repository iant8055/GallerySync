package com.gallery.sync.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.gallery.sync.R
import com.gallery.sync.domain.backup.ArchiveAge
import com.gallery.sync.domain.backup.ArchiveAgeUnit

/**
 * *Only show files older than*: a number from ½ to 9 and a unit, on the Archive tab and in Settings
 * (Ian, 2 Nov 2026). All is the unit's last entry and has no number, so the number's menu is greyed for
 * it, never hidden (Ian, the same day: hidden, it read as missing). [button] draws each menu's button,
 * because the two screens draw theirs differently; its last argument is false when that menu is greyed.
 * Also Sync's video *Older than* (Ian, 14 Nov 2026), whose zero entry reads *Straight away* ([zeroLabel]),
 * and the Camera folder's age, on its screen and in Settings: every place a time limit is set (Ian, same day).
 */
@Composable
fun ArchiveAgePicker(
    age: ArchiveAge,
    onSetAge: (ArchiveAge) -> Unit,
    zeroLabel: @Composable () -> String = { stringResource(R.string.archive_age_all) },
    button: @Composable (label: String, enabled: Boolean, onClick: () -> Unit) -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        PickerMenu(
            selected = age.count,
            options = ArchiveAge.COUNTS,
            optionLabel = { countLabel(it) },
            onSelected = { onSetAge(age.copy(count = it)) },
            enabled = age.unit != ArchiveAgeUnit.All,
            button = button
        )
        PickerMenu(
            selected = age.unit,
            options = ArchiveAgeUnit.entries,
            optionLabel = { if (it == ArchiveAgeUnit.All) zeroLabel() else it.label(age.count) },
            onSelected = { onSetAge(age.copy(unit = it)) },
            enabled = true,
            button = button
        )
    }
}

@Composable
private fun <T> PickerMenu(
    selected: T,
    options: List<T>,
    optionLabel: @Composable (T) -> String,
    onSelected: (T) -> Unit,
    enabled: Boolean,
    button: @Composable (label: String, enabled: Boolean, onClick: () -> Unit) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        button(optionLabel(selected), enabled) { expanded = true }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(optionLabel(option)) },
                    onClick = {
                        onSelected(option)
                        expanded = false
                    }
                )
            }
        }
    }
}

/** ½, or the whole number. */
fun countLabel(count: Double): String = if (count < 1.0) "\u00BD" else count.toInt().toString()

/**
 * The age in words, "3 weeks" or "½ day", for a sentence that names it. [zeroLabel] for the zero entry.
 * ½ takes the singular, as "½ hour" does.
 */
@Composable
fun ArchiveAge.label(zeroLabel: @Composable () -> String = { stringResource(R.string.archive_age_all) }): String =
    if (unit == ArchiveAgeUnit.All) zeroLabel() else countLabel(count) + " " + unit.label(count)

@Composable
private fun ArchiveAgeUnit.label(count: Double): String = labelFor(if (count < 1.0) 1 else count.toInt())

@Composable
private fun ArchiveAgeUnit.labelFor(count: Int): String = when (this) {
    ArchiveAgeUnit.Hour -> pluralStringResource(R.plurals.archive_age_hour, count)
    ArchiveAgeUnit.Day -> pluralStringResource(R.plurals.archive_age_day, count)
    ArchiveAgeUnit.Week -> pluralStringResource(R.plurals.archive_age_week, count)
    ArchiveAgeUnit.Month -> pluralStringResource(R.plurals.archive_age_month, count)
    ArchiveAgeUnit.Year -> pluralStringResource(R.plurals.archive_age_year, count)
    ArchiveAgeUnit.All -> stringResource(R.string.archive_age_all)
}
