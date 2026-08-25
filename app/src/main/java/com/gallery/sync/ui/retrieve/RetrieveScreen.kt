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
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gallery.sync.R
import com.gallery.sync.domain.backup.RestorableFile
import com.gallery.sync.domain.backup.RestorableFolder
import com.gallery.sync.ui.common.LabelWithAction
import com.gallery.sync.ui.common.OneDriveLauncher
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
                text = stringResource(R.string.retrieve_title),
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
            }

            // The path, kept last so it sits directly above the list it describes rather than above
            // a paragraph. It is also the way back: tapping it leaves the folder, the way a path bar
            // works in a file manager. Shows the destination root rather than the whole search set —
            // a folder that exists under both roots has two true paths, and this is the one where
            // the next upload would land.
            Breadcrumb(
                destinationPath = state.destinationPath,
                folder = state.selectedFolder,
                onUp = viewModel::closeFolder
            )

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
                items(state.folders, key = { it.name }) { folder ->
                    FolderRow(
                        folder = folder,
                        status = state.folderStatuses[folder.name],
                        onOpen = { viewModel.openFolder(folder.name) },
                        onRestoreAll = { viewModel.restoreFolder(folder) }
                    )
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

            // The way out of this screen's one deliberate limit. Sits directly under the list so it
            // is read at the moment the list disappoints someone, rather than in Settings where the
            // question never occurs to them.
            item {
                HorizontalDivider()
                CantFindSection()
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

/**
 * One cloud folder: what it holds, how much of it is here, and a button for the lot.
 *
 * Names and counts only — no thumbnail, which is the line this screen has to keep holding.
 *
 * The two counts come from different places and are deliberately not presented as a match. See
 * [RestorableFolder]: one is what Graph reports the folder contains, the other is what a local scan
 * found in an album of the same name. Tapping through is what answers which files those are.
 */
@Composable
private fun FolderRow(
    folder: RestorableFolder,
    status: FolderStatus?,
    onOpen: () -> Unit,
    onRestoreAll: () -> Unit
) {
    val context = LocalContext.current

    LabelWithAction(
        modifier = Modifier
            .clickable(onClick = onOpen)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        action = {
            OutlinedButton(
                onClick = onRestoreAll,
                // An empty folder has nothing to offer. Still enabled when the counts match, because
                // they are two counts of same-named things rather than proof the files are the same
                // — see [RestorableFolder]. Only "nothing there at all" is certain enough to refuse.
                enabled = !folder.isEmpty &&
                    status !is FolderStatus.Checking &&
                    status !is FolderStatus.Working
            ) {
                Text(text = stringResource(R.string.retrieve_restore_all), maxLines = 1)
            }
        }
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(text = folder.name, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = if (folder.isEmpty) {
                    stringResource(R.string.retrieve_folder_empty)
                } else {
                    stringResource(
                        R.string.retrieve_folder_detail,
                        folder.fileCount,
                        formatBytes(context, folder.sizeBytes)
                    )
                },
                style = MaterialTheme.typography.bodySmall
            )
            if (!folder.isEmpty) {
                Text(
                    text = if (folder.looksComplete) {
                        stringResource(R.string.retrieve_folder_all_here)
                    } else {
                        stringResource(
                            R.string.retrieve_folder_here_count,
                            folder.onDeviceCount,
                            folder.fileCount
                        )
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            status?.let { FolderStatusLine(it) }
        }
    }
}

/** What the whole-folder button is doing, including what it deliberately left alone. */
@Composable
private fun FolderStatusLine(status: FolderStatus) {
    val text = when (status) {
        FolderStatus.Checking -> stringResource(R.string.retrieve_folder_checking)
        is FolderStatus.Working ->
            stringResource(R.string.retrieve_folder_working, status.done + 1, status.total)

        FolderStatus.AlreadyHere -> stringResource(R.string.retrieve_folder_nothing_missing)
        is FolderStatus.Done -> stringResource(
            R.string.retrieve_folder_done,
            status.restored,
            status.skipped
        )

        is FolderStatus.Failed -> stringResource(R.string.retrieve_folder_failed, status.reason)
    }

    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = if (status is FolderStatus.Failed) {
            MaterialTheme.colorScheme.error
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        }
    )
}

/**
 * `OneDrive/Samsung Gallery/DCIM/12345clips`, and the way back out of it.
 *
 * One [Text] rather than a row of per-segment buttons, deliberately. A path is long, this app is
 * used at large font scales on a folding screen, and a Row of separate labels is exactly the shape
 * that collapsed to one character per line on the Fold 4 before. A single text flows and wraps like
 * any other sentence.
 *
 * The leading path is drawn in the primary colour and the current folder is not, so it reads as
 * "here, inside there" — and the whole line is the target, because there is only one place up to go.
 */
@Composable
private fun Breadcrumb(destinationPath: String, folder: String?, onUp: () -> Unit) {
    val root = stringResource(R.string.retrieve_drive_name)
    val base = if (destinationPath.isBlank()) root else "$root/$destinationPath"

    val text = buildAnnotatedString {
        if (folder == null) {
            append(base)
        } else {
            withStyle(SpanStyle(color = MaterialTheme.colorScheme.primary)) { append("$base/") }
            append(folder)
        }
    }

    Text(
        text = text,
        // A step up from the bodySmall the explanatory text uses. It is a control and a location,
        // not a footnote, and at bodySmall it read as one more line of prose.
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            // Only clickable when there is somewhere to go. At the top of the tree the path is a
            // label, and a control that looks live and does nothing is worse than no control.
            .clickable(enabled = folder != null, onClick = onUp)
            .padding(vertical = 6.dp)
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

/**
 * Where to go for anything this screen cannot show.
 *
 * The restore list is confined to the backup roots, which is a deliberate scope decision and not a
 * gap to apologise for — but it does mean someone can open this tab, fail to find a folder that
 * lives elsewhere in their drive, and conclude the file was never backed up. That is the wrong
 * conclusion, and it is reachable from a screen that otherwise looks complete.
 *
 * Phrased from the reader's side. "Can't find what you're looking for?" is the thought they are
 * already having; "GallerySync only browses the backup roots" is our implementation detail, and
 * nobody arrives at this screen wondering about our implementation.
 *
 * The button leads to OneDrive itself, which does browse the whole drive and does it better than a
 * plain list ever could. The design principle rules out becoming that app; it does not rule out
 * pointing at it.
 */
@Composable
private fun CantFindSection(modifier: Modifier = Modifier) {
    val context = LocalContext.current

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = stringResource(R.string.retrieve_cant_find_title),
            style = MaterialTheme.typography.titleMedium
        )

        LabelWithAction(
            action = {
                OutlinedButton(onClick = { OneDriveLauncher.open(context) }) {
                    Text(text = stringResource(R.string.retrieve_open_onedrive), maxLines = 1)
                }
            }
        ) {
            Text(
                text = stringResource(R.string.retrieve_cant_find_detail),
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}
