package com.gallery.sync.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gallery.sync.R
import com.gallery.sync.ui.common.LabelWithAction
import com.gallery.sync.ui.setup.ReconcileViewModel

/**
 * Which folders the app reads from — Gate 1, once the wizard has answered it.
 *
 * A phone reports around ninety albums: WhatsApp thumbnails, screenshots, every app's cache. Almost
 * none of that is what someone means by "my photos", and offering all of it makes the album list
 * unusable and the first upload enormous. So the scan follows granted trees rather than everything
 * MediaStore can see.
 *
 * ### Why this lives in Settings
 *
 * Moved here 26 Aug 2026, at Ian's request, from the Cloud check screen. Choosing source folders is
 * a setting — made once, changed rarely — and it sat above a reconciliation readout that is a
 * different kind of thing entirely. It belongs beside the other choices that shape what the app does,
 * not above a number.
 *
 * The tree picker is also the SAF write grant that lets a background worker proxy a photo with no
 * dialog, so this control does more than it appears to. That is why removing a directory is worded
 * carefully rather than offered as a bare X.
 */
@Composable
fun SourcesSection(
    modifier: Modifier = Modifier,
    viewModel: ReconcileViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    // OpenDocumentTree is the same grant that later lets a background worker rewrite a photo without
    // an Activity, so one pick serves both reading and proxying.
    val pickFolder = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri -> uri?.let(viewModel::addSource) }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = stringResource(R.string.sources_title),
            style = MaterialTheme.typography.titleMedium
        )
        Text(
            text = stringResource(R.string.sources_explain),
            style = MaterialTheme.typography.bodySmall
        )

        if (state.directories.isEmpty()) {
            Text(
                text = stringResource(R.string.sources_empty),
                style = MaterialTheme.typography.bodyMedium
            )
        } else {
            state.directories.forEach { directory ->
                LabelWithAction(
                    action = {
                        TextButton(onClick = { viewModel.removeSource(directory.treeUri) }) {
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

        if (state.directoryRefused) {
            Text(
                text = stringResource(R.string.sources_refused),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }

        OutlinedButton(onClick = { pickFolder.launch(null) }) {
            Text(stringResource(R.string.sources_add), maxLines = 1)
        }
    }
}
