package com.gallery.sync.ui.common

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.gallery.sync.R

/**
 * Stops someone walking away from files that are checked, verified, and waiting on one tap.
 *
 * Not a consent dialog. The album mode is the consent and was given once when the mode was set —
 * this says only that the job is unfinished, which is a statement about state rather than a second
 * question about intent. See `ExitWarning` for why the summons exists and what it cannot cover.
 *
 * The buttons carry verbs. An OK against a sentence about leaving is genuinely ambiguous: it reads
 * as both "yes, close it" and "yes, take me there", and this is the wrong dialog to make anyone
 * guess on.
 */
@Composable
fun ExitWarningDialog(
    readyCount: Int,
    onGoToArchive: () -> Unit,
    onLeave: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.exit_warning_title)) },
        text = {
            Text(pluralStringResource(R.plurals.exit_warning_body, readyCount, readyCount))
        },
        confirmButton = {
            TextButton(onClick = onGoToArchive) {
                Text(stringResource(R.string.exit_warning_go))
            }
        },
        dismissButton = {
            TextButton(onClick = onLeave) {
                Text(stringResource(R.string.exit_warning_leave))
            }
        }
    )
}
