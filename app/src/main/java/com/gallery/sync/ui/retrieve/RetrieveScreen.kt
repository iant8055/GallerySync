package com.gallery.sync.ui.retrieve

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gallery.sync.R
import com.gallery.sync.domain.backup.RestorableFile
import com.gallery.sync.ui.common.LabelWithAction
import com.gallery.sync.ui.common.formatBytes

/**
 * What OneDrive holds, and a button to bring any of it back.
 *
 * Two levels: the folders in the backup roots, and the files in one of them. Every file in a folder
 * is listed whether or not the phone still has it, because a ledger-driven list cannot answer the
 * question a restore feature promises to answer — on a new handset the ledger is empty and OneDrive
 * is full. Files already here are labelled rather than hidden, so a duplicate is something the user
 * chooses rather than something that happens to them.
 *
 * **Deliberately not a photo browser.** No thumbnails, no grid, no search, no sort — the design
 * principle rules all of that out, and this is the screen most likely to attract them. Real browsing
 * stays with the Open OneDrive button in Settings; looking *through* your photos is a different
 * activity from getting specific ones back.
 *
 * It is also the only route back. Android offers no hydration hook for media, so nothing in Samsung
 * Gallery can reach this app when a file is missing; the list is the entire interface rather than a
 * shortcut to one.
 */
@Composable
fun RetrieveScreen(
    modifier: Modifier = Modifier,
    viewModel: RetrieveViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Column(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = state.selectedFolder ?: stringResource(R.string.retrieve_title),
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = stringResource(
                    if (state.selectedFolder == null) {
                        R.string.retrieve_pick_folder
                    } else {
                        R.string.retrieve_explain
                    }
                ),
                style = MaterialTheme.typography.bodySmall
            )
            if (state.selectedFolder != null) {
                Text(
                    text = stringResource(R.string.retrieve_where),
                    style = MaterialTheme.typography.bodySmall
                )
                TextButton(onClick = viewModel::closeFolder) {
                    Text(stringResource(R.string.retrieve_all_folders))
                }
            }

            // Never rendered as an empty list. "You have no backups" because the network dropped is
            // the most alarming thing this screen could say, and it would not be true.
            if (state.couldNotList) {
                Text(
                    text = stringResource(R.string.retrieve_could_not_list),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
                TextButton(onClick = viewModel::loadFolders) {
                    Text(stringResource(R.string.retrieve_try_again))
                }
            }
        }

        if (state.loading) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }

        // Outlives the row it describes. Each name is a file that was on this list a moment ago and
        // is not any more, which without a word for it looks like the app losing things.
        if (state.droppedFromCloud.isNotEmpty()) {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                state.droppedFromCloud.forEach { name ->
                    Text(
                        text = stringResource(R.string.retrieve_gone_named, name),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }

        HorizontalDivider()

        LazyColumn(modifier = Modifier.fillMaxWidth()) {
            if (state.selectedFolder == null) {
                items(state.folders, key = { it }) { folder ->
                    FolderRow(name = folder, onOpen = { viewModel.openFolder(folder) })
                    HorizontalDivider()
                }
            } else {
                items(state.files, key = { it.remoteItemId }) { file ->
                    RetrieveRow(
                        file = file,
                        status = state.statuses[file.remoteItemId],
                        onRetrieve = { viewModel.retrieve(file) }
                    )
                    HorizontalDivider()
                }
            }

            // Two halves of one question: what happened to the files that are no longer here. The
            // list above offers them back; this offers to let them go.
            item {
                HorizontalDivider()
                DeletionSection()
            }
        }
    }
}

/** One cloud folder. Names only — what is inside is a tap away, not a thumbnail. */
@Composable
private fun FolderRow(name: String, onOpen: () -> Unit) {
    Text(
        text = name,
        style = MaterialTheme.typography.bodyLarge,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
            .padding(horizontal = 16.dp, vertical = 14.dp)
    )
}

/** One cloud file: what it is called, how big, whether it is already here, and a button. */
@Composable
private fun RetrieveRow(
    file: RestorableFile,
    status: RetrieveStatus?,
    onRetrieve: () -> Unit
) {
    val context = LocalContext.current

    LabelWithAction(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
        action = {
            OutlinedButton(
                onClick = onRetrieve,
                // Disabled only while this row is working — never because the file is already on
                // the phone. Fetching one anyway is the user's call to make.
                enabled = status !is RetrieveStatus.Working
            ) {
                Text(
                    text = if (status is RetrieveStatus.Working) {
                        stringResource(R.string.retrieve_working)
                    } else {
                        stringResource(R.string.retrieve_action)
                    },
                    maxLines = 1
                )
            }
        }
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = file.displayName,
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = formatBytes(context, file.sizeBytes),
                style = MaterialTheme.typography.bodySmall
            )
            if (file.alreadyOnDevice) {
                Text(
                    text = stringResource(R.string.retrieve_already_here),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            status?.let { StatusLine(it, file.sizeBytes) }
        }
    }
}

@Composable
private fun StatusLine(status: RetrieveStatus, total: Long) {
    val text = when (status) {
        is RetrieveStatus.Working -> {
            val percent = if (total > 0) {
                ((status.bytesWritten * 100) / total).toInt().coerceIn(0, 100)
            } else {
                0
            }
            "$percent%"
        }

        RetrieveStatus.Done -> stringResource(R.string.retrieve_done)
        RetrieveStatus.Unsupported -> stringResource(R.string.retrieve_unsupported)
        RetrieveStatus.GoneFromCloud -> stringResource(R.string.retrieve_gone)
        is RetrieveStatus.Failed -> stringResource(R.string.retrieve_failed, status.reason)
    }

    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = if (status is RetrieveStatus.Failed || status is RetrieveStatus.GoneFromCloud) {
            MaterialTheme.colorScheme.error
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        }
    )
}
