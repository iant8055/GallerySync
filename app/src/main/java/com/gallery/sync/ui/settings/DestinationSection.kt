package com.gallery.sync.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gallery.sync.R
import com.gallery.sync.domain.backup.FirstBackupHold
import com.gallery.sync.domain.backup.FirstBackupWindow
import com.gallery.sync.domain.backup.RemoteRoots
import com.gallery.sync.ui.common.LabelWithAction
import com.gallery.sync.ui.setup.ReconcileViewModel
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/**
 * Where backups go, and when the first one is allowed to run.
 *
 * ### Why these are settings
 *
 * Moved here 26 Aug 2026 when the Cloud check tab became Archive. Both pass the same test the source
 * folders passed before them: chosen once, changed rarely, consequential when changed. They had been
 * sharing a screen with the reconciliation readout, which answers a question asked exactly once at
 * setup — a different kind of thing entirely, and now TASK-014's wizard owns it.
 *
 * The alternative was to let them go dark until that wizard exists. Ian chose otherwise, and the
 * reasoning is worth keeping: a build being tested every day should not lose working controls to a
 * screen nobody has written yet.
 */
@Composable
fun DestinationSection(
    modifier: Modifier = Modifier,
    viewModel: ReconcileViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Naming the folder is not the point — "OneDrive/Samsung Gallery/DCIM" means nothing on its
        // own, while "change it and those already-found files stop being found" is a real decision.
        LabelWithAction(
            action = {
                OutlinedButton(onClick = viewModel::openDestinationChooser) {
                    Text(stringResource(R.string.destination_change), maxLines = 1)
                }
            }
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = stringResource(R.string.destination_current, state.destinationRoot),
                    style = MaterialTheme.typography.bodyMedium
                )
                // Only true while the destination still is where Samsung put things. Said once the
                // user has moved it, this would be a lie about why the numbers exist.
                if (state.destinationRoot == RemoteRoots.SAMSUNG_GALLERY) {
                    Text(
                        text = stringResource(R.string.destination_why),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }

    if (state.choosingDestination) {
        DestinationDialog(
            current = state.destinationRoot,
            alreadyBackedUp = state.alreadyFoundHere,
            rejected = state.destinationRejected,
            onConfirm = viewModel::setDestination,
            onDismiss = viewModel::dismissDestinationChooser
        )
    }
}

/** The first-backup window, hosted in Settings and driven by the same state as before. */
@Composable
fun FirstBackupSettings(
    modifier: Modifier = Modifier,
    viewModel: ReconcileViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Column(modifier = modifier.fillMaxWidth()) {
        FirstBackupSection(
            startHour = state.firstBackupStartHour,
            requiresCharging = state.firstBackupRequiresCharging,
            done = state.hasCompletedFirstBackup,
            hold = state.firstBackupHold,
            onHourSelected = viewModel::setFirstBackupStartHour,
            onChargingChanged = viewModel::setFirstBackupRequiresCharging
        )
    }
}

/**
 * Chooses the folder new uploads go into.
 *
 * A text field rather than a folder browser, deliberately. Browsing OneDrive is the thumbnail
 * browser the design principle rules out, and the default is right for almost everyone — the field
 * exists for the few who want somewhere else, not as the main path through setup.
 *
 * The body text is the point of the dialog. Changing a backup destination *sounds* like it should
 * strand what is already uploaded, and saying plainly that it does not — with the count — is what
 * turns a frightening setting into an ordinary one.
 */
@Composable
fun DestinationDialog(
    current: String,
    alreadyBackedUp: Int,
    rejected: Boolean,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var typed by rememberSaveable(current) { mutableStateOf(current) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.destination_dialog_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = typed,
                    onValueChange = { typed = it },
                    label = { Text(stringResource(R.string.destination_label)) },
                    singleLine = true,
                    isError = rejected,
                    // The 280dp default minimum is what broke the album rows; a dialog is narrower
                    // still, so it is relaxed here too.
                    modifier = Modifier.fillMaxWidth().widthIn(min = 0.dp)
                )
                Text(
                    text = if (alreadyBackedUp > 0) {
                        stringResource(R.string.destination_dialog_body, alreadyBackedUp)
                    } else {
                        stringResource(R.string.destination_dialog_body_unknown)
                    },
                    style = MaterialTheme.typography.bodySmall
                )
                if (rejected) {
                    Text(
                        text = stringResource(R.string.destination_invalid),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                if (typed != RemoteRoots.DEFAULT_DESTINATION) {
                    TextButton(onClick = { typed = RemoteRoots.DEFAULT_DESTINATION }) {
                        Text(stringResource(R.string.destination_reset), maxLines = 1)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(typed) }) {
                Text(stringResource(R.string.destination_save), maxLines = 1)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.destination_cancel), maxLines = 1)
            }
        }
    )
}

/** One media kind: how much is on the phone, how much is safe, how much is left. */
@Composable
private fun FirstBackupSection(
    startHour: Int,
    requiresCharging: Boolean,
    done: Boolean,
    hold: FirstBackupHold?,
    onHourSelected: (Int) -> Unit,
    onChargingChanged: (Boolean) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = stringResource(R.string.first_backup_title),
            style = MaterialTheme.typography.titleSmall
        )

        if (done) {
            Text(
                text = stringResource(R.string.first_backup_done),
                style = MaterialTheme.typography.bodySmall
            )
            return@Column
        }

        Text(
            text = stringResource(R.string.first_backup_explain),
            style = MaterialTheme.typography.bodySmall
        )

        LabelWithAction(
            action = { stacked ->
                HourDropdown(
                    hour = startHour,
                    onHourSelected = onHourSelected,
                    stacked = stacked
                )
            }
        ) {
            Text(
                text = stringResource(R.string.first_backup_start_label),
                style = MaterialTheme.typography.bodyMedium
            )
        }

        LabelWithAction(
            action = {
                Switch(checked = requiresCharging, onCheckedChange = onChargingChanged)
            }
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = stringResource(R.string.first_backup_charging),
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = stringResource(R.string.first_backup_charging_detail),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        Text(
            text = when (hold) {
                FirstBackupHold.OUTSIDE_WINDOW ->
                    stringResource(R.string.first_backup_waiting_time, formatHour(startHour))
                FirstBackupHold.NOT_CHARGING ->
                    stringResource(R.string.first_backup_waiting_charging)
                null -> stringResource(R.string.first_backup_ready)
            },
            style = MaterialTheme.typography.bodyMedium
        )

        // The escape hatch, said plainly. The window stops the app choosing a bad moment on its
        // own; it was never meant to stop the user choosing one the app disagrees with.
        Text(
            text = stringResource(R.string.first_backup_manual_note),
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HourDropdown(hour: Int, onHourSelected: (Int) -> Unit, stacked: Boolean) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = formatHour(hour),
            onValueChange = {},
            readOnly = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            // Same 280dp default minimum that collapsed the album rows; relaxed for the same reason.
            modifier = Modifier
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                .then(
                    if (stacked) Modifier.fillMaxWidth()
                    else Modifier.widthIn(min = 0.dp, max = 150.dp)
                ),
            textStyle = MaterialTheme.typography.bodyMedium,
            singleLine = true
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            FirstBackupWindow.SELECTABLE_HOURS.forEach { candidate ->
                DropdownMenuItem(
                    text = { Text(formatHour(candidate)) },
                    onClick = {
                        onHourSelected(candidate)
                        expanded = false
                    },
                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                )
            }
        }
    }
}

/**
 * An hour as the user's own locale writes it.
 *
 * Formatted rather than hardcoded to "1am": half the world reads 01:00, and a setup screen that
 * tells someone their backup starts at a time they do not recognise is worse than one that says
 * nothing.
 */
@Composable
private fun formatHour(hour: Int): String {
    val formatter = remember {
        DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(Locale.getDefault())
    }
    return remember(hour) { LocalTime.of(hour, 0).format(formatter) }
}

