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
 * *Only show files older than*: a number from 1 to 9 and a unit, on the Archive tab and in Settings
 * (Ian, 2 Nov 2026). All is the unit's last entry and has no number, so the number's menu is not drawn
 * for it. [button] draws each menu's button, because the two screens draw theirs differently.
 */
@Composable
fun ArchiveAgePicker(
    age: ArchiveAge,
    onSetAge: (ArchiveAge) -> Unit,
    button: @Composable (label: String, onClick: () -> Unit) -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (age.unit != ArchiveAgeUnit.All) {
            PickerMenu(
                selected = age.count,
                options = ArchiveAge.COUNTS.toList(),
                optionLabel = { it.toString() },
                onSelected = { onSetAge(age.copy(count = it)) },
                button = button
            )
        }
        PickerMenu(
            selected = age.unit,
            options = ArchiveAgeUnit.entries,
            optionLabel = { it.label(age.count) },
            onSelected = { onSetAge(age.copy(unit = it)) },
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
    button: @Composable (label: String, onClick: () -> Unit) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        button(optionLabel(selected)) { expanded = true }
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

@Composable
private fun ArchiveAgeUnit.label(count: Int): String = when (this) {
    ArchiveAgeUnit.Hour -> pluralStringResource(R.plurals.archive_age_hour, count)
    ArchiveAgeUnit.Day -> pluralStringResource(R.plurals.archive_age_day, count)
    ArchiveAgeUnit.Week -> pluralStringResource(R.plurals.archive_age_week, count)
    ArchiveAgeUnit.Month -> pluralStringResource(R.plurals.archive_age_month, count)
    ArchiveAgeUnit.Year -> pluralStringResource(R.plurals.archive_age_year, count)
    ArchiveAgeUnit.All -> stringResource(R.string.archive_age_all)
}
