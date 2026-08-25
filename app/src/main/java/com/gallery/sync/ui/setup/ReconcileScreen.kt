package com.gallery.sync.ui.setup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gallery.sync.R
import com.gallery.sync.data.local.media.GrantedDirectory
import androidx.compose.ui.semantics.Role
import com.gallery.sync.domain.backup.FirstBackupHold
import com.gallery.sync.domain.backup.LibraryChoice
import com.gallery.sync.domain.backup.LibraryEstimate
import com.gallery.sync.domain.backup.FirstBackupWindow
import com.gallery.sync.domain.backup.MediaTally
import com.gallery.sync.domain.backup.RemoteRoots
import com.gallery.sync.ui.common.LabelWithAction
import com.gallery.sync.ui.common.formatBytes
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/**
 * The first honest number in setup: how much of the library OneDrive already holds.
 *
 * Deliberately not a gallery or a file list — it is three counts and a total, which is everything
 * needed to answer "should I back everything up?" and nothing more.
 *
 * ### The unchecked line is not decoration
 *
 * When albums could not be listed, this says so and says nothing about their contents. Reporting
 * them as "not backed up" would tell someone their library is unprotected when it may be entirely
 * safe, and the natural response — upload everything — costs hours of transfer and duplicate quota.
 * That exact mistake was made on 19 Aug 2026 with 8,177 files.
 */
@Composable
fun ReconcileScreen(
    modifier: Modifier = Modifier,
    viewModel: ReconcileViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // Gate 1's picker. OpenDocumentTree is the same grant that later lets a background worker
    // rewrite a photo without an Activity, so one pick serves both reading and proxying.
    val pickFolder = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri -> uri?.let(viewModel::addSource) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        SourcesSection(
            directories = state.directories,
            refused = state.directoryRefused,
            onAdd = { pickFolder.launch(null) },
            onRemove = viewModel::removeSource
        )

        // Nothing below here means anything yet. With no folders granted the scan returns nothing,
        // and a reconciliation reporting zero outstanding would announce that the whole library is
        // already backed up — false, and false in the direction that stops someone acting.
        if (!state.hasSources) return@Column

        HorizontalDivider()

        Text(
            text = stringResource(R.string.reconcile_title),
            style = MaterialTheme.typography.titleMedium
        )

        if (state.noMediaAccess) {
            Text(
                text = stringResource(R.string.reconcile_no_access),
                style = MaterialTheme.typography.bodyMedium
            )
            return@Column
        }

        if (state.running) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }

        val result = state.result
        if (result == null) {
            CircularProgressIndicator()
            return@Column
        }

        MediaLine(
            label = stringResource(R.string.reconcile_photos),
            backedUp = result.photosBackedUp,
            outstanding = result.photosOutstanding
        )
        MediaLine(
            label = stringResource(R.string.reconcile_videos),
            backedUp = result.videosBackedUp,
            outstanding = result.videosOutstanding
        )

        // The number the "back up everything" decision actually turns on.
        //
        // `!state.running` is not cosmetic. Mid-run the totals cover only the albums checked so far,
        // and `isComplete` is trivially true because nothing has failed yet — so after one album of
        // ninety this claimed the whole library was already safe. Announcing that before the check
        // finishes is the same false reassurance the unchecked category exists to prevent, pointed
        // the other way.
        val outstanding = result.outstanding
        val allSafe = !state.running && outstanding.files == 0 && result.isComplete
        Text(
            text = if (allSafe) {
                stringResource(R.string.reconcile_all_safe)
            } else {
                stringResource(
                    R.string.reconcile_outstanding,
                    formatBytes(context, outstanding.bytes)
                )
            },
            style = MaterialTheme.typography.bodyLarge
        )

        // Said plainly, and never folded into either other figure.
        if (!result.isComplete) {
            Text(
                text = stringResource(
                    R.string.reconcile_incomplete,
                    result.albumsUnchecked,
                    formatBytes(context, result.unchecked.bytes)
                ),
                style = MaterialTheme.typography.bodySmall
            )
            if (!state.running) {
                OutlinedButton(onClick = viewModel::start) {
                    Text(stringResource(R.string.reconcile_retry), maxLines = 1)
                }
            }
        }

        HorizontalDivider()

        LibrarySection(
            selected = state.libraryChoice,
            applied = state.libraryApplied,
            applying = state.applyingLibraryChoice,
            photoBytes = result.photos.bytes,
            videoBytes = result.videos.bytes,
            onSelected = viewModel::setLibraryChoice,
            onApply = viewModel::applyLibraryChoice
        )

        HorizontalDivider()

        // Asked after the numbers, because that is what makes it answerable: "change the folder"
        // means nothing on its own, while "change it and those already-found files stop being
        // found" is a real decision.
        Text(
            text = stringResource(R.string.destination_title),
            style = MaterialTheme.typography.titleSmall
        )
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
                // user has moved it, this would be a lie about why the numbers above exist.
                if (state.destinationRoot == RemoteRoots.SAMSUNG_GALLERY) {
                    Text(
                        text = stringResource(R.string.destination_why),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }

        HorizontalDivider()

        FirstBackupSection(
            startHour = state.firstBackupStartHour,
            requiresCharging = state.firstBackupRequiresCharging,
            done = state.hasCompletedFirstBackup,
            hold = state.firstBackupHold,
            onHourSelected = viewModel::setFirstBackupStartHour,
            onChargingChanged = viewModel::setFirstBackupRequiresCharging
        )
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
private fun DestinationDialog(
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
private fun MediaLine(label: String, backedUp: MediaTally, outstanding: MediaTally) {
    val context = LocalContext.current
    val total = backedUp.files + outstanding.files

    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.titleSmall)
        Text(
            text = stringResource(
                R.string.reconcile_line,
                pluralStringResource(R.plurals.file_count, total, total),
                pluralStringResource(R.plurals.file_count, backedUp.files, backedUp.files),
                pluralStringResource(
                    R.plurals.file_count,
                    outstanding.files,
                    outstanding.files
                )
            ),
            style = MaterialTheme.typography.bodySmall
        )
        if (outstanding.bytes > 0) {
            Text(
                text = formatBytes(context, outstanding.bytes),
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

/**
 * When the first whole-library upload will run, and what it is waiting for.
 *
 * The first backup is measured in hours, not seconds — 148 GB on a real device — so the useful thing
 * to show is *when it will happen*, not a progress bar someone has to sit and watch. Once the backlog
 * clears this section says so and stops offering settings that no longer do anything.
 *
 * The hold is named rather than reduced to "waiting". "Waiting until 1am" and "waiting for you to
 * plug in" ask different things of the user, and a phone that appears to be doing nothing for an
 * unexplained reason is the thing this is trying to avoid.
 */
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

/**
 * Gate 1: which folders the app looks in.
 *
 * The engine has nothing correct to do until this is answered, which is why it sits above everything
 * else and why the rest of the screen is hidden while it is empty.
 *
 * A phone reports around ninety albums — WhatsApp thumbnails, screenshots, every app's cache. Almost
 * none of that is what someone means by "my photos", and offering all of it makes the album list
 * unusable and the first upload enormous.
 */
@Composable
private fun SourcesSection(
    directories: List<GrantedDirectory>,
    refused: Boolean,
    onAdd: () -> Unit,
    onRemove: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = stringResource(R.string.sources_title),
            style = MaterialTheme.typography.titleMedium
        )
        Text(
            text = stringResource(R.string.sources_explain),
            style = MaterialTheme.typography.bodySmall
        )

        if (directories.isEmpty()) {
            Text(
                text = stringResource(R.string.sources_empty),
                style = MaterialTheme.typography.bodyMedium
            )
        } else {
            directories.forEach { directory ->
                LabelWithAction(
                    action = {
                        TextButton(onClick = { onRemove(directory.treeUri) }) {
                            Text(stringResource(R.string.sources_remove), maxLines = 1)
                        }
                    }
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = directory.displayName,
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                            text = directory.relativePath,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
            // Said where the Remove buttons are, because that is where the worry is.
            Text(
                text = stringResource(R.string.sources_remove_note),
                style = MaterialTheme.typography.bodySmall
            )
        }

        if (refused) {
            Text(
                text = stringResource(R.string.sources_refused),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }

        OutlinedButton(onClick = onAdd) {
            Text(stringResource(R.string.sources_add), maxLines = 1)
        }
    }
}

/**
 * Gate 2: what happens to the library already on the phone.
 *
 * One choice applied to thousands of files, made by someone who has not yet watched the app do
 * anything — so the safest option is the default, and the other two say plainly what they cost.
 *
 * Archive is not offered here and must not be added. Setting every album at once to the only mode
 * that removes files, before v0.4 retrieval exists to undo it, is the largest irreversible action
 * this product can take at the moment the user knows least about it.
 *
 * Selecting does nothing; applying is a separate tap. A radio list that acted on touch would make
 * the most consequential screen in the app the easiest one to trigger by accident.
 */
@Composable
private fun LibrarySection(
    selected: LibraryChoice,
    applied: Int?,
    applying: Boolean,
    photoBytes: Long,
    videoBytes: Long,
    onSelected: (LibraryChoice) -> Unit,
    onApply: () -> Unit
) {
    val context = LocalContext.current
    val freed = LibraryEstimate.spaceFreedBySync(photoBytes)
    val marginal = LibraryEstimate.isSavingMarginal(photoBytes, videoBytes)

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = stringResource(R.string.library_title),
            style = MaterialTheme.typography.titleSmall
        )

        Column(Modifier.selectableGroup()) {
            LibraryChoice.entries.forEach { choice ->
                val detail = when (choice) {
                    LibraryChoice.CHOOSE_PER_ALBUM ->
                        stringResource(R.string.library_per_album_detail)

                    LibraryChoice.BACK_UP_EVERYTHING ->
                        stringResource(R.string.library_back_up_all_detail)

                    // Only photos shrink. On a library that is mostly video, saying "frees space"
                    // without saying how little invites someone to expect most of it back — so the
                    // wording leads with what stays instead.
                    LibraryChoice.BACK_UP_AND_FREE_SPACE -> if (marginal) {
                        stringResource(
                            R.string.library_free_space_detail_marginal,
                            formatBytes(context, freed),
                            formatBytes(context, photoBytes + videoBytes)
                        )
                    } else {
                        stringResource(
                            R.string.library_free_space_detail,
                            formatBytes(context, freed)
                        )
                    }
                }

                ChoiceRow(
                    label = when (choice) {
                        LibraryChoice.CHOOSE_PER_ALBUM -> stringResource(R.string.library_per_album)
                        LibraryChoice.BACK_UP_EVERYTHING -> stringResource(R.string.library_back_up_all)
                        LibraryChoice.BACK_UP_AND_FREE_SPACE -> stringResource(R.string.library_free_space)
                    },
                    detail = detail,
                    selected = choice == selected,
                    onSelect = { onSelected(choice) }
                )
            }
        }

        // Only where it is true. Saying it beside "choose album by album" would be a warning about
        // something that is not going to happen.
        if (selected.uploads) {
            Text(
                text = stringResource(R.string.library_first_run_warning),
                style = MaterialTheme.typography.bodySmall
            )
        }

        applied?.let { count ->
            Text(
                text = stringResource(
                    R.string.library_applied,
                    pluralStringResource(R.plurals.album_count, count, count)
                ),
                style = MaterialTheme.typography.bodyMedium
            )
        }

        OutlinedButton(onClick = onApply, enabled = !applying) {
            Text(stringResource(R.string.library_apply), maxLines = 1)
        }
    }
}

/** One radio option with its consequence written underneath it. */
@Composable
private fun ChoiceRow(
    label: String,
    detail: String,
    selected: Boolean,
    onSelect: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onSelect, role = Role.RadioButton)
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        RadioButton(selected = selected, onClick = null)
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            Text(detail, style = MaterialTheme.typography.bodySmall)
        }
    }
}
