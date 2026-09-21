package com.gallery.sync.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
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
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gallery.sync.R
import com.gallery.sync.ui.help.HelpButton
import com.gallery.sync.ui.help.HelpTopic
import com.gallery.sync.ui.setup.ReconcileViewModel

@Composable
fun SourcesSection(
    modifier: Modifier = Modifier,
    viewModel: ReconcileViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    var tickedForRemoval by remember { mutableStateOf(emptySet<String>()) }

    val pickFolder = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri -> uri?.let(viewModel::addSource) }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = stringResource(R.string.sources_title),
                style = MaterialTheme.typography.titleMedium
            )
            HelpButton(HelpTopic.SETTINGS_FOLDERS)
        }

        if (state.directories.isEmpty()) {
            Text(
                text = stringResource(R.string.sources_empty),
                style = MaterialTheme.typography.bodyMedium
            )
        } else {
            state.directories.forEach { directory ->
                val volumeLabel = if (directory.volume == "primary")
                    stringResource(R.string.volume_internal)
                else
                    directory.volume
                val ticked = directory.treeUri in tickedForRemoval

                // A box beside each folder, and one Remove below for the ticked ones (Ian, 20 Sept 2026).
                // The whole row is the target, so it is not a fiddly 24dp square.
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .toggleable(
                            value = ticked,
                            role = Role.Checkbox,
                            onValueChange = {
                                tickedForRemoval =
                                    if (it) tickedForRemoval + directory.treeUri
                                    else tickedForRemoval - directory.treeUri
                            }
                        ),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Checkbox(checked = ticked, onCheckedChange = null)
                    Text(
                        text = stringResource(R.string.sources_full_path, volumeLabel, directory.relativePath),
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }
        }

        if (state.directoryRefused) {
            Text(
                text = stringResource(R.string.sources_refused),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = { pickFolder.launch(null) }) {
                Text(stringResource(R.string.sources_add), maxLines = 1)
            }
            // Only for what is ticked. A folder that has gone from the list since it was ticked is
            // simply not there to remove.
            OutlinedButton(
                enabled = state.directories.any { it.treeUri in tickedForRemoval },
                onClick = {
                    state.directories
                        .filter { it.treeUri in tickedForRemoval }
                        .forEach { viewModel.removeSource(it.treeUri) }
                    tickedForRemoval = emptySet()
                }
            ) {
                Text(stringResource(R.string.sources_remove), maxLines = 1)
            }
        }
    }
}
