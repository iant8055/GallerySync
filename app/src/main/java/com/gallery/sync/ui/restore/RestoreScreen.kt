package com.gallery.sync.ui.restore

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gallery.sync.R
import com.gallery.sync.ui.common.HeroCard
import com.gallery.sync.ui.common.HeroOutlinedButton
import com.gallery.sync.ui.common.SignalIcons
import com.gallery.sync.ui.common.formatBytes
import com.gallery.sync.ui.theme.LocalGallerySyncColors
import kotlin.math.abs

/** Where a phone layout stops being the right answer. Matches the album and folder grids. */
private val WideBreakpoint = 600.dp

/** Far enough sideways to mean it, in pixels. Short of this the list keeps its scroll. */
private const val SwipeThresholdPx = 90f

/**
 * What this app did to this phone, and undoing it.
 *
 * **Folders first, always** — Ian, 27 Aug 2026, even when only one folder has anything in it. Swipe
 * a folder to take all of it, tap to open and choose, ↵ to come back out.
 *
 * **Deliberately not a photo browser**, the same constraint the tab it replaces carried: no
 * thumbnails, no grid, no search, no sort.
 */
@Composable
fun RestoreScreen(
    modifier: Modifier = Modifier,
    viewModel: RestoreViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // Re-read on entering the tab: an optimise or archive run since the app started changes this
    // list, and there is no other moment the screen would learn about it.
    LaunchedEffect(Unit) { viewModel.refresh() }

    Column(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            HeroCard(
                label = stringResource(
                    if (state.openFolder == null) {
                        R.string.restore_hero_label_folders
                    } else {
                        R.string.restore_hero_label_files
                    }
                ),
                figure = when {
                    state.loading -> "—"
                    state.openFolder == null -> state.folders.size.toString()
                    else -> state.visibleRows.size.toString()
                },
                figureFooter = if (state.hasSelection) {
                    {
                        Text(
                            text = stringResource(
                                R.string.restore_selected_summary,
                                state.selection.size,
                                formatBytes(context, state.bytesToRecover)
                            ),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                } else {
                    null
                },
                detail = {
                    Text(
                        text = state.summary ?: stringResource(
                            when {
                                state.rows.isEmpty() && !state.loading -> R.string.restore_empty
                                state.openFolder == null -> R.string.restore_intro_folders
                                else -> R.string.restore_intro_files
                            }
                        ),
                        style = MaterialTheme.typography.bodyMedium
                    )
                },
                actions = {
                    // Two fixed half-width slots, weighted as Albums weights Sync now and Rescan so
                    // a button here is the same size as a button there.
                    //
                    // **Nothing here disappears.** Ian, 27 Aug 2026: don't have the Clear button
                    // vanish. A control that comes and goes makes the card twitch as the selection
                    // changes and the user has to find it again each time; greyed out it holds its
                    // place and says plainly that there is nothing to undo. Same for the first slot
                    // while a run is going.
                    //
                    // The disabled colours come off the hero's own content colour rather than the
                    // scheme — see HeroOutlinedButton. Material's defaults would paint
                    // dark-green-on-dark-green here and read as a hole rather than a control.
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Slot one changes with the level: Refresh belongs to the folder list,
                        // Select all to a folder. Refresh re-reads the ledger, which is the only
                        // way to notice an optimise or archive run that happened while this tab
                        // was open.
                        if (state.openFolder == null) {
                            HeroOutlinedButton(
                                onClick = viewModel::refresh,
                                label = stringResource(R.string.retrieve_refresh),
                                modifier = Modifier.weight(1f),
                                enabled = !state.running
                            )
                        } else {
                            HeroOutlinedButton(
                                onClick = viewModel::selectAllHere,
                                label = stringResource(R.string.retrieve_select_all),
                                modifier = Modifier.weight(1f),
                                enabled = !state.running
                            )
                        }

                        HeroOutlinedButton(
                            onClick = viewModel::clearSelection,
                            label = stringResource(R.string.retrieve_clear_selection),
                            modifier = Modifier.weight(1f),
                            enabled = state.hasSelection && !state.running
                        )
                    }
                }
            )

            Breadcrumb(folder = state.openFolder, onUp = viewModel::closeFolder)

            if (state.loading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
        }

        HorizontalDivider()

        // Two columns unfolded, for the same reason Albums has them: what a folding screen wants
        // from a long list is more rows rather than wider ones.
        BoxWithConstraints(modifier = Modifier.fillMaxWidth().weight(1f)) {
            val columns = if (maxWidth >= WideBreakpoint) 2 else 1
            val count = if (state.openFolder == null) state.folders.size else state.visibleRows.size
            val half = if (count == 0) 0 else (count + columns - 1) / columns

            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(half) { index ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        for (column in 0 until columns) {
                            val position = index + column * half
                            Box(modifier = Modifier.weight(1f)) {
                                if (state.openFolder == null) {
                                    state.folders.getOrNull(position)?.let { folder ->
                                        FolderCard(
                                            folder = folder,
                                            enabled = !state.running,
                                            onOpen = { viewModel.openFolder(folder.name) },
                                            onSetSelected = { wanted ->
                                                viewModel.setFolderSelected(folder.name, wanted)
                                            }
                                        )
                                    }
                                } else {
                                    state.visibleRows.getOrNull(position)?.let { row ->
                                        FileCard(
                                            row = row,
                                            selected = row.id in state.selection,
                                            enabled = !state.running,
                                            onToggle = { viewModel.toggle(row) }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (state.hasSelection || state.running) {
            RestoreBar(
                running = state.running,
                onRestore = viewModel::restoreSelected,
                onStop = viewModel::stop
            )
        }
    }
}

/** The path, and the way back out of a folder. Absent at the top, where there is nowhere to go. */
@Composable
private fun Breadcrumb(folder: String?, onUp: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = folder ?: stringResource(R.string.restore_all_folders),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .weight(1f)
                .clickable(enabled = folder != null, onClick = onUp)
                .padding(vertical = 6.dp)
        )
        if (folder != null) {
            IconButton(onClick = onUp) {
                Icon(
                    imageVector = SignalIcons.Back,
                    contentDescription = stringResource(R.string.retrieve_back),
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

/**
 * One album: how much of it can come back, and how.
 *
 * Tap opens, a horizontal drag takes the whole folder. Directional — right selects, left deselects,
 * repeating either is a no-op — so swiping through several folders cannot silently unpick one
 * already chosen. `selected` is in the pointerInput key, or the lambda tests a stale value.
 */
@Composable
private fun FolderCard(
    folder: RestoreFolder,
    enabled: Boolean,
    onOpen: () -> Unit,
    onSetSelected: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val allSelected = folder.selectedHere == folder.total && folder.total > 0

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(folder.name, allSelected, enabled) {
                if (!enabled) return@pointerInput
                var travelled = 0f
                detectHorizontalDragGestures(
                    onDragStart = { travelled = 0f },
                    onDragEnd = {
                        val wants = when {
                            travelled > SwipeThresholdPx -> true
                            travelled < -SwipeThresholdPx -> false
                            else -> allSelected
                        }
                        if (wants != allSelected) onSetSelected(wants)
                    }
                ) { change, amount ->
                    travelled += amount
                    if (abs(travelled) > SwipeThresholdPx) change.consume()
                }
            },
        shape = RoundedCornerShape(22.dp),
        color = if (allSelected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surface
        },
        contentColor = if (allSelected) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onSurface
        },
        border = BorderStroke(
            if (allSelected) 2.dp else 1.dp,
            if (allSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
        ),
        enabled = enabled,
        onClick = onOpen
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(text = folder.name, style = MaterialTheme.typography.headlineSmall)
                Text(
                    text = stringResource(
                        R.string.restore_folder_detail,
                        folder.total,
                        formatBytes(context, folder.bytesToRecover)
                    ),
                    style = MaterialTheme.typography.bodyLarge
                )
                // The two populations named separately, because they are different operations on
                // the user's phone and a single total would hide that.
                Text(
                    text = stringResource(
                        R.string.restore_folder_split,
                        folder.restorable,
                        folder.downloadable
                    ),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (folder.selectedHere > 0 && !allSelected) {
                    Text(
                        text = stringResource(R.string.retrieve_picked_here, folder.selectedHere),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            Icon(
                imageVector = if (allSelected) SignalIcons.Check else SignalIcons.ChevronRight,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = if (allSelected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
        }
    }
}

/** One file: what it is now, what it would become, and how far along it is. */
@Composable
private fun FileCard(
    row: RestoreRow,
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
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(text = row.displayName, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = when (row.kind) {
                        RowKind.Restore -> stringResource(
                            R.string.restore_sizes,
                            formatBytes(context, row.localBytes),
                            formatBytes(context, row.fullBytes)
                        )

                        RowKind.Download -> stringResource(
                            R.string.restore_missing_size,
                            formatBytes(context, row.fullBytes)
                        )
                    },
                    style = MaterialTheme.typography.bodyMedium
                )

                when (val rowState = row.state) {
                    RowState.Waiting -> Unit

                    is RowState.Working -> {
                        Text(
                            text = stringResource(R.string.restore_downloading, rowState.percent),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        LinearProgressIndicator(
                            progress = { rowState.percent / 100f },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    is RowState.Done -> Text(
                        text = stringResource(
                            if (row.kind == RowKind.Restore) {
                                R.string.restore_done_row
                            } else {
                                R.string.restore_downloaded_row
                            }
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )

                    // Says the file is unchanged, because it is. Not softened into an apology: this
                    // is the sentence that tells the user a failure here costs them nothing.
                    is RowState.Failed -> Text(
                        text = stringResource(R.string.restore_failed_row, rowState.reason),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
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

/** The single action. A control while it runs, not a label. */
@Composable
private fun RestoreBar(running: Boolean, onRestore: () -> Unit, onStop: () -> Unit) {
    val signal = LocalGallerySyncColors.current

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = if (running) onStop else onRestore,
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = signal.accent,
                    contentColor = signal.onAccent,
                    disabledContainerColor = LocalContentColor.current.copy(alpha = 0.14f),
                    disabledContentColor = LocalContentColor.current.copy(alpha = 0.55f)
                )
            ) {
                Text(
                    text = stringResource(
                        if (running) R.string.retrieve_stop else R.string.retrieve_action
                    ),
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1
                )
            }
        }
    }
}
