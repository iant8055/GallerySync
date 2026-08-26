package com.gallery.sync.ui.retrieve

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
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
import kotlin.math.abs
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import com.gallery.sync.domain.backup.RestorableFile
import com.gallery.sync.domain.backup.RestorableFolder
import com.gallery.sync.ui.common.OneDriveLauncher
import com.gallery.sync.ui.common.SignalIcons
import com.gallery.sync.ui.common.formatBytes

/** Where a phone layout stops being the right answer. The standard expanded-width breakpoint. */
private val WideBreakpoint = 600.dp

/** Far enough sideways to mean it, in pixels. Short of this the list keeps its scroll. */
private const val SwipeThresholdPx = 90f

/**
 * What OneDrive holds, and a way to bring any of it back.
 *
 * Two levels: the folders in the backup roots, and the files in one of them. Every file in a folder
 * is listed whether or not the phone still has it, because a ledger-driven list cannot answer the
 * question a restore feature promises to answer — on a new handset the ledger is empty and OneDrive
 * is full.
 *
 * ### Selecting, not buttons
 *
 * Ian, 25 Aug 2026: select the file itself rather than click a button on it. Files are picked by
 * tapping them and fetched by one action at the foot of the screen. Three things fall out of that —
 * the rows lose a control that was competing with the filename for width and wrapping the size onto
 * a second line; empty folders stop carrying a disabled button that means nothing; and restoring
 * four files becomes one action instead of four transfers racing each other for one connection.
 *
 * **Deliberately not a photo browser.** No thumbnails, no grid, no search, no sort — the design
 * principle rules all of that out, and this is the screen most likely to attract them.
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

            if ((state.selectedFolder != null && state.files.isNotEmpty()) || state.hasSelection) {
                SelectionControls(state = state, viewModel = viewModel)
            }
        }

        // Determinate while a file is actually moving, indeterminate while listing or before the
        // first byte — a bar claiming 0% is a worse lie than one that admits it does not know.
        val working = state.batchStatus as? RestoreBatchStatus.Working
        val percentOfCurrent = working?.percentOfCurrent
        when {
            percentOfCurrent != null -> LinearProgressIndicator(
                progress = { percentOfCurrent / 100f },
                modifier = Modifier.fillMaxWidth()
            )

            state.loading || working != null ->
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

        // The same two-column treatment as Albums, and for the same reason: this is the app's other
        // long list, and what it wants from a folding screen is more rows rather than wider ones.
        // Split by count so each column stays alphabetical top to bottom.
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            val columns = if (maxWidth >= WideBreakpoint) 2 else 1
            val rows = if (state.selectedFolder == null) state.folders.size else state.files.size
            val half = if (rows == 0) 0 else (rows + columns - 1) / columns

            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(count = half) { index ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        for (column in 0 until columns) {
                            val position = index + column * half
                            Box(modifier = Modifier.weight(1f)) {
                                if (state.selectedFolder == null) {
                                    state.folders.getOrNull(position)?.let { folder ->
                                        FolderRow(
                                            folder = folder,
                                            selected = folder.name in state.selectedFolderNames,
                                            pickedHere = state.selectedCountIn(folder.name),
                                            onOpen = { viewModel.openFolder(folder.name) },
                                            onSwipe = { viewModel.toggleFolderSelection(folder) }
                                        )
                                    }
                                } else {
                                    state.files.getOrNull(position)?.let { file ->
                                        FileRow(
                                            file = file,
                                            selected = file.remoteItemId in state.selection,
                                            enabled = !state.isRestoring,
                                            onToggle = { viewModel.toggleSelection(file) }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                if (!state.loading) {
                    // The way out of this screen's one deliberate limit. Sits directly under the
                    // list so it is read at the moment the list disappoints someone, rather than in
                    // Settings where the question never occurs to them.
                    //
                    // Held back while the list is still arriving: an empty list under "Can't find
                    // what you're looking for?" reads as an answer rather than as a wait.
                    item { CantFindSection() }

                    // Two halves of one question: what happened to the files that are no longer
                    // here. The list above offers them back; this offers to let them go.
                    item { DeletionSection() }
                }
            }
        }

        // The one action, at the foot of the screen where a thumb is, and only once something is
        // chosen. Nothing on this screen moves a byte until it appears.
        if (state.hasSelection || state.isRestoring) {
            RestoreBar(state = state, onRestore = viewModel::restoreSelected)
        }
    }
}

/** Select all / clear, and whatever the last batch did. */
@Composable
private fun SelectionControls(state: RetrieveUiState, viewModel: RetrieveViewModel) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // Only inside a folder — there is nothing to select all of on the folder list, where the
        // equivalent is swiping the folders you want.
        if (state.selectedFolder != null && state.files.isNotEmpty()) {
            TextButton(onClick = viewModel::selectAll, enabled = !state.isRestoring) {
                Text(stringResource(R.string.retrieve_select_all), maxLines = 1)
            }
        }
        if (state.hasSelection) {
            TextButton(onClick = viewModel::clearSelection, enabled = !state.isRestoring) {
                Text(stringResource(R.string.retrieve_clear_selection), maxLines = 1)
            }
        }
    }

    when (val status = state.batchStatus) {
        is RestoreBatchStatus.Done -> Text(
            text = if (status.failed == 0) {
                stringResource(R.string.retrieve_batch_done, status.restored)
            } else {
                stringResource(R.string.retrieve_batch_done_failed, status.restored, status.failed)
            },
            style = MaterialTheme.typography.bodySmall,
            color = if (status.failed == 0) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.error
            }
        )

        RestoreBatchStatus.Unsupported -> Text(
            text = stringResource(R.string.retrieve_unsupported),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error
        )

        else -> Unit
    }
}

/**
 * One cloud folder: what it holds, and how much of it is here.
 *
 * No button any more. Tapping opens it, which was always the main path — the files inside are the
 * point of this screen — and restoring a whole folder is now open, Select all, Restore.
 *
 * The two counts come from different places and are deliberately not presented as a match. See
 * [RestorableFolder]: one is what Graph reports the folder contains, the other is what a local scan
 * found in an album of the same name.
 */
@Composable
private fun FolderRow(
    folder: RestorableFolder,
    selected: Boolean,
    pickedHere: Int,
    onOpen: () -> Unit,
    onSwipe: () -> Unit
) {
    val context = LocalContext.current

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            // Tap opens, a horizontal drag takes the whole folder. Two intents, two gestures, no
            // button — which is what removed the control that was fighting the name for width.
            //
            // `detectHorizontalDragGestures` rather than SwipeToDismissBox: this row is inside a
            // vertically scrolling list and, unfolded, is one of two side-by-side columns. A
            // dismiss box wants to own the whole width and animate the row away, neither of which
            // is what a selection should do. The row stays put; only the ring changes.
            .pointerInput(folder.name) {
                var travelled = 0f
                detectHorizontalDragGestures(
                    onDragStart = { travelled = 0f },
                    onDragEnd = { if (abs(travelled) > SwipeThresholdPx) onSwipe() }
                ) { change, amount ->
                    travelled += amount
                    // Claimed only once it is clearly sideways, so a diagonal thumb still scrolls
                    // the list rather than selecting something by accident.
                    if (abs(travelled) > SwipeThresholdPx) change.consume()
                }
            },
        shape = RoundedCornerShape(22.dp),
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surface
        },
        contentColor = if (selected) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onSurface
        },
        border = BorderStroke(
            if (selected) 2.dp else 1.dp,
            if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
        ),
        onClick = onOpen
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
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
                // What the standing selection took from this folder. Without it, coming back out
                // to the list leaves no trace of where the files in the bar came from.
                if (pickedHere > 0) {
                    Text(
                        text = stringResource(R.string.retrieve_picked_here, pickedHere),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
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
            }
            Icon(
                imageVector = if (selected) SignalIcons.Check else SignalIcons.ChevronRight,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
        }
    }
}

/**
 * One cloud file, picked by tapping it.
 *
 * The whole card is the target rather than a control inside it, so nothing competes with the
 * filename for width. Selection shows as a ring in the primary colour plus a tick — the same "this
 * one" language the album mode menu uses.
 *
 * A file already on the phone can still be selected. That is the point: the user asked for it, and a
 * second copy under a `_restored` name is a cost they can see.
 */
@Composable
private fun FileRow(
    file: RestorableFile,
    selected: Boolean,
    enabled: Boolean,
    onToggle: () -> Unit
) {
    val context = LocalContext.current

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surface
        },
        contentColor = if (selected) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onSurface
        },
        border = BorderStroke(
            if (selected) 2.dp else 1.dp,
            if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
        ),
        enabled = enabled,
        onClick = onToggle
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(text = file.displayName, style = MaterialTheme.typography.bodyLarge)
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
            }
            if (selected) {
                Icon(
                    imageVector = SignalIcons.Check,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

/** The single action, and what it is about to move. */
@Composable
private fun RestoreBar(state: RetrieveUiState, onRestore: () -> Unit) {
    val context = LocalContext.current
    val status = state.batchStatus

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                modifier = Modifier.weight(1f),
                text = if (status is RestoreBatchStatus.Working) {
                    // Name the file and its position within it once bytes are moving. A bare
                    // "1 of 1" sat unchanged for seven minutes on a 2 GB video and read as a hang.
                    val percent = status.percentOfCurrent
                    val name = status.currentFile
                    if (percent != null && name != null) {
                        stringResource(
                            R.string.retrieve_batch_working_file,
                            status.done + 1,
                            status.total,
                            name,
                            percent
                        )
                    } else {
                        stringResource(
                            R.string.retrieve_batch_working,
                            status.done + 1,
                            status.total
                        )
                    }
                } else {
                    stringResource(
                        R.string.retrieve_selected_summary,
                        state.selectionCount,
                        formatBytes(context, state.selectedBytes)
                    )
                },
                style = MaterialTheme.typography.bodyMedium
            )
            Button(
                onClick = onRestore,
                enabled = !state.isRestoring,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Text(
                    text = stringResource(
                        if (state.isRestoring) {
                            R.string.retrieve_working
                        } else {
                            R.string.retrieve_action
                        }
                    ),
                    maxLines = 1
                )
            }
        }
    }
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

/**
 * Where to go for anything this screen cannot show.
 *
 * The restore list is confined to the backup roots, which is a deliberate scope decision and not a
 * gap to apologise for — but it does mean someone can open this tab, fail to find a folder that
 * lives elsewhere in their drive, and conclude the file was never backed up. That is the wrong
 * conclusion, and it is reachable from a screen that otherwise looks complete.
 *
 * Phrased from the reader's side. "Can't find what you're looking for?" is the thought they are
 * already having; "GallerySync only browses the backup roots" is our implementation detail.
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
        Text(
            text = stringResource(R.string.retrieve_cant_find_detail),
            style = MaterialTheme.typography.bodySmall
        )
        OutlinedButton(onClick = { OneDriveLauncher.open(context) }) {
            Text(text = stringResource(R.string.retrieve_open_onedrive), maxLines = 1)
        }
    }
}
