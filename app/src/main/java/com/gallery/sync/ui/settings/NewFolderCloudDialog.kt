package com.gallery.sync.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.gallery.sync.R
import com.gallery.sync.domain.backup.BackupLocation
import com.gallery.sync.domain.backup.RemoteRoots
import com.gallery.sync.ui.common.labelRes
import com.gallery.sync.ui.help.HelpTopic
import com.gallery.sync.ui.help.TitleWithHelp

/**
 * Asked right after a folder is added in Settings and scanned: which Cloud it goes to, and where in that Cloud
 * (Ian, 31 Oct 2026). Offers the same Clouds as the folder's row under *Where each folder goes*.
 *
 * The place in a Cloud is one value per Cloud (Ian, 12 Oct 2026), not per phone folder, so Change here changes it for
 * everything sent to that Cloud, and the dialog says so. *Decide later* and dismissing change nothing: the folder stays
 * on the Cloud it already resolves to, and its row can still be changed.
 */
@Composable
fun NewFolderCloudDialog(
    folder: String,
    current: BackupLocation?,
    cloudState: CloudProvidersUiState,
    onChangeCloudFolder: (BackupLocation) -> Unit,
    onAnswer: (BackupLocation?) -> Unit
) {
    val options = cloudState.destinations
    var chosen by remember(folder) { mutableStateOf(current?.takeIf { it in options } ?: cloudState.main) }

    AlertDialog(
        onDismissRequest = { onAnswer(null) },
        title = { TitleWithHelp(stringResource(R.string.new_folder_cloud_title, folder), HelpTopic.DIALOG_NEW_FOLDER_CLOUD) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                options.forEach { location ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(selected = location == chosen, role = Role.RadioButton) { chosen = location }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        RadioButton(selected = location == chosen, onClick = null)
                        Text(stringResource(location.labelRes()), style = MaterialTheme.typography.bodyLarge)
                    }
                }

                if (RemoteRoots.hasFolders(chosen)) {
                    Text(
                        text = stringResource(
                            R.string.pairing_path,
                            stringResource(chosen.labelRes()),
                            cloudState.folders[chosen] ?: RemoteRoots.defaultFor(chosen)
                        ),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    OutlinedButton(onClick = { onChangeCloudFolder(chosen) }) {
                        Text(stringResource(R.string.destination_change), maxLines = 1)
                    }
                    Text(
                        text = stringResource(R.string.new_folder_cloud_shared),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Text(stringResource(R.string.pairing_path_library), style = MaterialTheme.typography.bodyMedium)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onAnswer(chosen) }) { Text(stringResource(R.string.tour_ok)) }
        },
        dismissButton = {
            TextButton(onClick = { onAnswer(null) }) { Text(stringResource(R.string.new_folder_cloud_later)) }
        }
    )
}
