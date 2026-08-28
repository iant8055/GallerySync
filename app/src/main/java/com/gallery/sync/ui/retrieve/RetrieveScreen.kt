package com.gallery.sync.ui.retrieve

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.gallery.sync.ui.common.HeroCard
import com.gallery.sync.ui.common.HeroOutlinedButton
import com.gallery.sync.ui.common.formatBytes
import com.gallery.sync.ui.theme.LocalGallerySyncColors

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
    val context = LocalContext.current

    // Re-lists the drive each time this tab is entered. The ViewModel outlives a tab switch, so
    // without this the screen keeps showing whatever the drive held at app launch — an album backed
    // up since simply does not appear, with no way to ask. Guarded in the ViewModel so it never
    // discards a folder the user is in or a selection they have made.
    LaunchedEffect(Unit) { viewModel.refreshIfIdle() }

    Column(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // The same card Albums and Archive open with, so the three tabs read as one app.
            // Ian, 26 Aug 2026. The figure is what is in OneDrive and reachable from here: at the
            // top level the folder count, inside a folder the files in it.
            HeroCard(
                label = if (state.selectedFolder == null) {
                    stringResource(R.string.retrieve_hero_label_folders)
                } else {
                    stringResource(R.string.retrieve_hero_label_files)
                },
                figure = if (state.loading) {
                    // Never a confident zero while the drive is still being listed. Albums learned
                    // this as an em dash and the reason carries over unchanged: a count nobody has
                    // read yet is not a count of nothing.
                    "—"
                } else if (state.selectedFolder == null) {
                    state.folders.size.toString()
                } else {
                    state.files.size.toString()
                },
                // Under the figure, not in the detail column. Ian, 27 Aug 2026: it was landing on
                // top of the swipe line, where a count read as another instruction. Stacked under
                // the number it qualifies, the pair says what the drive holds and how much of that
                // is spoken for.
                //
                // Unfolded this costs no height at all — the detail column is the taller of the
                // two, so the card is already that tall. Folded it adds a line to a card that was
                // going to change anyway when Clear appears.
                //
                // Null rather than an empty lambda when nothing is picked, so the card knows there
                // is no footer and leaves out the gap above it as well as the line itself.
                figureFooter = if (state.hasSelection) {
                    {
                        Text(
                            text = stringResource(
                                R.string.retrieve_selected_summary,
                                state.selectionCount,
                                formatBytes(context, state.selectedBytes)
                            ),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                } else {
                    null
                },
                // The instructions hold their place through a selection now that the count has its
                // own. Only a transfer displaces them, and only while it runs.
                detail = {
                    val working = state.batchStatus as? RestoreBatchStatus.Working

                    Text(
                        text = when {
                            // Name the file and its position within it once bytes are moving. A
                            // bare "1 of 1" sat unchanged for seven minutes on a 2 GB video and
                            // read as a hang.
                            working != null -> {
                                val percent = working.percentOfCurrent
                                val name = working.currentFile
                                if (percent != null && name != null) {
                                    stringResource(
                                        R.string.retrieve_batch_working_file,
                                        working.done + 1,
                                        working.total,
                                        name,
                                        percent
                                    )
                                } else {
                                    stringResource(
                                        R.string.retrieve_batch_working,
                                        working.done + 1,
                                        working.total
                                    )
                                }
                            }

                            // Inside a folder it says where the files land instead — a paragraph
                            // of reassurance was cut from here on 26 Aug 2026 at Ian's request.
                            state.selectedFolder == null ->
                                stringResource(R.string.retrieve_pick_folder)

                            else -> stringResource(R.string.retrieve_where)
                        },
                        style = MaterialTheme.typography.bodyMedium
                    )

                    // Its own line under the swipes. Ian, 27 Aug 2026. Sharing a line with them
                    // made three gestures read as one sentence, and the tap is the odd one out —
                    // the swipes pick, the tap goes somewhere.
                    //
                    // Kept up while a selection stands, not swapped away with the instruction
                    // above it: tapping a folder still opens it with files already picked, and
                    // holding the line steady is also what stops the card changing height and
                    // shifting the list, which is why the count moved in here in the first place.
                    //
                    // Dropped only while bytes are moving, where the card is carrying a filename
                    // and a progress bar and nothing should invite a detour.
                    if (state.selectedFolder == null && working == null) {
                        Text(
                            text = stringResource(R.string.retrieve_tap_to_open),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }

                    // Directly under the line it describes, which is the rule that moved it in
                    // here with that line. Determinate only: the indeterminate listing bar stays
                    // outside, where it belongs to an empty list rather than to a transfer.
                    val percentOfCurrent = working?.percentOfCurrent
                    if (percentOfCurrent != null) {
                        LinearProgressIndicator(
                            progress = { percentOfCurrent / 100f },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    // Whatever the last batch did. Outlives the selection, so it is the one thing
                    // here that is read after the fact.
                    when (val status = state.batchStatus) {
                        is RestoreBatchStatus.Done -> Text(
                            text = if (status.failed == 0) {
                                stringResource(R.string.retrieve_batch_done, status.restored)
                            } else {
                                stringResource(
                                    R.string.retrieve_batch_done_failed,
                                    status.restored,
                                    status.failed
                                )
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (status.failed == 0) {
                                LocalContentColor.current
                            } else {
                                MaterialTheme.colorScheme.error
                            }
                        )

                        RestoreBatchStatus.Unsupported -> Text(
                            text = stringResource(R.string.retrieve_unsupported),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error
                        )

                        else -> Unit
                    }
                },
                actions = {
                    // Inside the card, where Albums keeps Sync now and Rescan. It sat below the
                    // card when the hero arrived, leaving a band of empty space between the two and
                    // making the tab look unlike the one beside it.
                    //
                    // Select all and Clear joined them on 27 Aug 2026, for the reason the count
                    // did: appearing below the card, they moved the list.
                    //
                    // HeroOutlinedButton rather than TextButton: on the dark green container
                    // Material derives a TextButton's colour from the scheme and it comes out dim.
                    //
                    // Two fixed half-width slots, weighted exactly as Albums weights Sync now and
                    // Rescan, so a button here is the same size as a button there. Ian, 27 Aug
                    // 2026. Sized to their labels they came out unequal — "Clear" a stub beside
                    // "Refresh" — and neither matched the tab next door.
                    //
                    // The empty slot is a Spacer rather than nothing, so a lone Refresh stays half
                    // the row instead of stretching across it. Never more than two are live at
                    // once: Refresh belongs to the folder list and Select all to a folder, so they
                    // share the first slot and Clear always has the second.
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        when {
                            // Offered on success too, not only after a failure: this list is a
                            // snapshot of a drive that changes underneath it, and the only other
                            // way to re-take it was to kill the app.
                            state.selectedFolder == null && !state.loading -> HeroOutlinedButton(
                                onClick = viewModel::loadFolders,
                                label = stringResource(R.string.retrieve_refresh),
                                modifier = Modifier.weight(1f)
                            )

                            // Nothing to select all of on the folder list, where the equivalent is
                            // swiping the folders you want.
                            state.selectedFolder != null &&
                                state.files.isNotEmpty() &&
                                !state.isRestoring -> HeroOutlinedButton(
                                onClick = viewModel::selectAll,
                                label = stringResource(R.string.retrieve_select_all),
                                modifier = Modifier.weight(1f)
                            )

                            else -> Spacer(modifier = Modifier.weight(1f))
                        }

                        if (state.hasSelection && !state.isRestoring) {
                            HeroOutlinedButton(
                                onClick = viewModel::clearSelection,
                                label = stringResource(R.string.retrieve_clear_selection),
                                modifier = Modifier.weight(1f)
                            )
                        } else {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            )

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

            // The listing bar, and only the listing bar. Indeterminate because a bar claiming a
            // confident 0% is a worse lie than one admitting it does not know. It appears while the
            // list below is empty anyway, so it moves nothing.
            if (state.loading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
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
                }
            }
        }

        // The one action, at the foot of the screen where a thumb is, and only once something is
        // chosen. Nothing on this screen moves a byte until it appears.
        if (state.hasSelection || state.isRestoring) {
            RestoreBar(
                state = state,
                onRestore = viewModel::restoreSelected,
                onStop = viewModel::stopRestore
            )
        }
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
            // Directional since 27 Aug 2026, at Ian's request: right selects, left deselects. It
            // was a toggle in either direction, which meant the gesture's meaning depended on
            // state the thumb could not see — on a screen of six folders, "swipe them" would
            // silently unpick anything already picked. Now each direction has one meaning and
            // repeating it is a no-op, so a swipe can be finished twice without undoing itself.
            //
            // `selected` is in the pointerInput key. Without it the lambda keeps the value it
            // captured when the row first composed, and every swipe after the first would test
            // against a stale answer.
            //
            // `detectHorizontalDragGestures` rather than SwipeToDismissBox: this row is inside a
            // vertically scrolling list and, unfolded, is one of two side-by-side columns. A
            // dismiss box wants to own the whole width and animate the row away, neither of which
            // is what a selection should do. The row stays put; only the ring changes.
            .pointerInput(folder.name, selected) {
                var travelled = 0f
                detectHorizontalDragGestures(
                    onDragStart = { travelled = 0f },
                    onDragEnd = {
                        val wants = when {
                            travelled > SwipeThresholdPx -> true
                            travelled < -SwipeThresholdPx -> false
                            else -> selected
                        }
                        if (wants != selected) onSwipe()
                    }
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
        // Bigger than the file rows below it, deliberately, and raised a second notch on 27 Aug
        // 2026 at Ian's request. This is the level someone lands on and reads at arm's length to
        // decide where to go, so the name carries a headline style and the two lines under it are
        // full body text rather than the caption they started as.
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
                    text = if (folder.isEmpty) {
                        stringResource(R.string.retrieve_folder_empty)
                    } else {
                        stringResource(
                            R.string.retrieve_folder_detail,
                            folder.fileCount,
                            formatBytes(context, folder.sizeBytes)
                        )
                    },
                    style = MaterialTheme.typography.bodyLarge
                )
                // What the standing selection took from this folder. Without it, coming back out
                // to the list leaves no trace of where the files in the bar came from.
                if (pickedHere > 0) {
                    Text(
                        text = stringResource(R.string.retrieve_picked_here, pickedHere),
                        style = MaterialTheme.typography.bodyLarge,
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
                        style = MaterialTheme.typography.bodyMedium,
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

/**
 * The single action, and what it is about to move.
 *
 * Made loud on 27 Aug 2026 at Ian's request. It was `colorScheme.primary` at its natural width,
 * tucked into the right end of a `surfaceVariant` bar — in light mode that is dark green on pale
 * grey, the same weight as the outlined controls up in the card, for the one control on this screen
 * that moves bytes. Three changes, in order of how much they do:
 *
 * - **`signal.accent`**, the colour Albums gives Sync now and the nav bar gives the current tab.
 *   The app already has a colour that means "this is the action"; this button was not using it.
 * - **Full width.** It is the only thing in the bar, and a bar with one control at one end reads as
 *   a footer rather than as a button.
 * - **Taller, with a `titleMedium` label**, so it is a thumb target at the foot of the screen.
 *
 * Disabled colours are derived from the bar's own content colour, not from the scheme. Material's
 * defaults come off `surfaceVariant` here and produced grey-on-grey — the same washed-out failure
 * already fixed on the hero's outlined buttons and on Albums' Sync now.
 */
@Composable
private fun RestoreBar(state: RetrieveUiState, onRestore: () -> Unit, onStop: () -> Unit) {
    val signal = LocalGallerySyncColors.current

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.End
        ) {
            // A control, not a label — the same fix Albums' Sync now already carries. It read
            // "Restoring…" and disabled itself, so a fetch the user started could not be stopped
            // by the person who started it, and a 2 GB clip picked by mistake ran to the end.
            // Ian, 27 Aug 2026.
            //
            // The same button rather than a second one beside it: a permanently disabled Stop is
            // dead weight on a bar that holds one action, and the tab next door already teaches
            // this shape.
            Button(
                onClick = if (state.isRestoring) onStop else onRestore,
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
                        if (state.isRestoring) {
                            R.string.retrieve_stop
                        } else {
                            R.string.retrieve_action
                        }
                    ),
                    style = MaterialTheme.typography.titleMedium,
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

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = text,
            // A step up from the bodySmall the explanatory text uses. It is a control and a
            // location, not a footnote, and at bodySmall it read as one more line of prose.
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .weight(1f)
                // Still the target itself, so the habit of tapping the path keeps working. The
                // button beside it is the discoverable way; this is the fast one.
                .clickable(enabled = folder != null, onClick = onUp)
                .padding(vertical = 6.dp)
        )

        // Ian, 27 Aug 2026: a back button, on the right. The path bar was the only way out of a
        // folder and nothing about a line of grey text says so — a file manager teaches that habit
        // but this screen has two levels and no chrome to teach it with.
        //
        // Absent rather than disabled at the top level, for the reason the path itself is: there is
        // nowhere up from the roots, and a greyed control still asks to be tried.
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
 * Where to go for anything this screen cannot show.
 *
 * The restore list is confined to the backup roots, which is a deliberate scope decision and not a
 * gap to apologise for — but it does mean someone can open this tab, fail to find a folder that
 * lives elsewhere in their drive, and conclude the file was never backed up. That is the wrong
 * conclusion, and it is reachable from a screen that otherwise looks complete.
 *
 * Phrased from the reader's side. "Can't find what you're looking for?" is the thought they are
 * already having; "GallerySync only browses the backup roots" is our implementation detail.
 *
 * The line under it used to explain that scope. Ian replaced it on 27 Aug 2026 with an instruction —
 * "To Download other items from your OneDrive click here" — which trades the explanation for the way
 * out. The heading already carries the problem, so the second sentence was restating it before
 * offering the answer.
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
        // Sentence and button on one line. Ian, 27 Aug 2026. The text now ends "click here", which
        // only means anything while the thing to click is beside it — stacked, "here" pointed at
        // the line below and read as a broken link.
        //
        // The button keeps its intrinsic width and the text takes the rest, so a long line wraps
        // within its half instead of squeezing the label. Centred against it, because the sentence
        // is one or two lines and the button should sit against the middle of either.
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(R.string.retrieve_cant_find_detail),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f)
            )
            OutlinedButton(onClick = { OneDriveLauncher.open(context) }) {
                Text(text = stringResource(R.string.retrieve_open_onedrive), maxLines = 1)
            }
        }
    }
}
