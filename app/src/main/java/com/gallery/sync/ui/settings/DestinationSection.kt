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
import com.gallery.sync.ui.help.HelpTopic
import com.gallery.sync.ui.help.TitleWithHelp
import com.gallery.sync.ui.help.WithHelp
import com.gallery.sync.ui.setup.ReconcileViewModel
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

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
    onDismiss: () -> Unit,
    /** The Cloud this folder is in, named in the field's label. Null keeps the OneDrive wording. */
    cloudName: String? = null,
    defaultFolder: String = RemoteRoots.DEFAULT_DESTINATION,
    /** Only OneDrive has a folder browser so far; the others take a typed path. */
    canBrowse: Boolean = true,
    /** Only OneDrive keeps checking earlier folders for files already there; the others have no such check. */
    searchesOldFolders: Boolean = true
) {
    var typed by rememberSaveable(current) { mutableStateOf(current) }
    var browsing by rememberSaveable { mutableStateOf(false) }

    // Browsing is offered beside the field rather than replacing it. Someone who knows the path
    // types it in one go; walking ninety albums to reach a folder you can name is worse, and the
    // field keeps working when the network does not.
    if (browsing) {
        OneDriveFolderPicker(
            onPick = { picked ->
                typed = picked
                browsing = false
            },
            onDismiss = { browsing = false }
        )
        return
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            TitleWithHelp(
                stringResource(R.string.destination_dialog_title),
                HelpTopic.DIALOG_DESTINATION
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = typed,
                    onValueChange = { typed = it },
                    label = {
                        Text(
                            if (cloudName != null) stringResource(R.string.cloud_folder_label, cloudName)
                            else stringResource(R.string.destination_label)
                        )
                    },
                    singleLine = true,
                    isError = rejected,
                    // The 280dp default minimum is what broke the album rows; a dialog is narrower
                    // still, so it is relaxed here too.
                    modifier = Modifier.fillMaxWidth().widthIn(min = 0.dp)
                )
                Text(
                    text = if (!searchesOldFolders) {
                        stringResource(R.string.cloud_folder_body)
                    } else if (alreadyBackedUp > 0) {
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
                if (canBrowse) {
                    OutlinedButton(onClick = { browsing = true }) {
                        Text(stringResource(R.string.destination_browse), maxLines = 1)
                    }
                }
                if (typed != defaultFolder) {
                    TextButton(onClick = { typed = defaultFolder }) {
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

