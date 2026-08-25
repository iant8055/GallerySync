package com.gallery.sync.ui.setup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import com.gallery.sync.domain.backup.MediaTally
import com.gallery.sync.domain.backup.RemoteRoots
import com.gallery.sync.ui.common.LabelWithAction
import com.gallery.sync.ui.common.formatBytes

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

    LaunchedEffect(Unit) { viewModel.start() }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
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
