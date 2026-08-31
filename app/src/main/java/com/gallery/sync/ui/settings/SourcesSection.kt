package com.gallery.sync.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RichTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gallery.sync.R
import com.gallery.sync.ui.common.LabelWithAction
import com.gallery.sync.ui.common.SignalIcons
import com.gallery.sync.ui.setup.ReconcileViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SourcesSection(
    modifier: Modifier = Modifier,
    viewModel: ReconcileViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val tooltipState = rememberTooltipState(isPersistent = true)
    val scope = rememberCoroutineScope()

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
            TooltipBox(
                positionProvider = TooltipDefaults.rememberTooltipPositionProvider(),
                tooltip = {
                    RichTooltip(title = { Text(stringResource(R.string.sources_help_title)) }) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(stringResource(R.string.sources_explain))
                            Text(stringResource(R.string.sources_remove_note))
                        }
                    }
                },
                state = tooltipState
            ) {
                IconButton(onClick = { scope.launch { tooltipState.show() } }) {
                    Icon(
                        imageVector = SignalIcons.Help,
                        contentDescription = stringResource(R.string.sources_help_title),
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
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

                LabelWithAction(
                    action = {
                        OutlinedButton(onClick = { viewModel.removeSource(directory.treeUri) }) {
                            Text(stringResource(R.string.sources_remove), maxLines = 1)
                        }
                    }
                ) {
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

        Button(onClick = { pickFolder.launch(null) }) {
            Text(stringResource(R.string.sources_add), maxLines = 1)
        }
    }
}
