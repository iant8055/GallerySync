package com.gallery.sync.ui.restore

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.IntOffset
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gallery.sync.R
import com.gallery.sync.ui.common.HeroCard
import com.gallery.sync.ui.help.HelpButton
import com.gallery.sync.ui.help.HelpTopic
import com.gallery.sync.ui.help.TitleWithHelp
import com.gallery.sync.ui.help.WithHelp
import com.gallery.sync.ui.common.HeroOutlinedButton
import com.gallery.sync.ui.common.SignalIcons
import com.gallery.sync.ui.common.SwipeChoiceBox
import com.gallery.sync.ui.common.formatBytes
import com.gallery.sync.ui.theme.LocalGallerySyncColors
import kotlin.math.abs

/** Where a phone layout stops being the right answer. Matches the album and folder grids. */
private val WideBreakpoint = 600.dp

/** Far enough sideways to mean it, in pixels. Short of this the list keeps its scroll. */
private const val SwipeThresholdPx = 90f

/** The card moves this fraction of the finger's travel: a pull with some weight to it, not a slide. */
private const val SwipeResistance = 0.55f

/** The furthest the card is drawn aside, in px. */
private const val MaxPullPx = 130f

/** Back to rest after a swipe: a quick spring with a slight overshoot, so it settles rather than stops. */
private val SwipeReturnSpring = spring<Float>(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium)

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
            // One header in two forms (Ian, 19 Sept 2026), both laid out like an album's drill-down on
            // the Albums tab: a label on the left half and the number centred in the right half.
            // Inside a folder the left half is the way back and the folder's name; on the list it is
            // "Folders to" with "Restore" directly under it.
            if (state.openFolder != null) {
                FolderHeader(state = state, viewModel = viewModel, context = context)
            } else {
                ListHeader(state = state, viewModel = viewModel, context = context)
            }

            // No path line, on the list or inside a folder (Ian, 19 Sept 2026: get rid of "All
            // folders" and its (?)). Inside a folder the header names the folder and carries the way
            // back.

            if (state.loading || state.checkingCloud) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            if (state.checkingCloud) {
                Text(
                    text = stringResource(R.string.restore_checking_cloud),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else if (state.cloudUnavailable) {
                Text(
                    text = stringResource(R.string.restore_cloud_unavailable),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            // Says why some files in a folder are greyed out. Only when there are some.
            if (state.openFolder != null && state.visibleRows.any { !it.isActionable }) {
                WithHelp(HelpTopic.RESTORE_GREYED_FILES) {
                    Text(
                        text = stringResource(R.string.restore_greyed_line),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
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
                                        val selectedNow = row.id in state.selection
                                        // The same gesture as the folder cards and every other list that
                                        // selects: right selects, left deselects, repeating either is a
                                        // no-op. Tapping the card still toggles it. Ian, 20 Sept 2026.
                                        SwipeChoiceBox(
                                            enabled = !state.running && row.isActionable,
                                            stateKey = selectedNow,
                                            onSwipeRight = { if (!selectedNow) viewModel.toggle(row) },
                                            onSwipeLeft = { if (selectedNow) viewModel.toggle(row) },
                                            accessibilityLabel = stringResource(
                                                if (selectedNow) R.string.select_action_deselect else R.string.select_action_select
                                            ),
                                            onAccessibilityAction = { viewModel.toggle(row) }
                                        ) { drawn ->
                                            FileCard(
                                                row = row,
                                                selected = selectedNow,
                                                enabled = !state.running,
                                                onToggle = { viewModel.toggle(row) },
                                                modifier = drawn
                                            )
                                        }
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
                progress = state.progress,
                onRestore = viewModel::restoreSelected,
                onStop = viewModel::stop
            )
        }
    }
}

/**
 * The header on the folder list: "Folders to" with "Restore" directly under it on the left half, the
 * number centred in the right half, and the same lower half as the header inside a folder.
 */
@Composable
private fun ListHeader(
    state: RestoreUiState,
    viewModel: RestoreViewModel,
    context: android.content.Context
) {
    val signal = LocalGallerySyncColors.current
    val figure = if (state.loading) "—" else state.folders.size.toString()
    val message = state.summary ?: stringResource(
        if (state.rows.isEmpty() && !state.loading && !state.checkingCloud) {
            R.string.restore_empty
        } else {
            R.string.restore_intro_folders
        }
    )

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = signal.heroContainer,
        contentColor = signal.onHero
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Larger than it was (Ian, 19 Sept 2026): two short lines have room for it.
                Column(modifier = Modifier.weight(1f).padding(start = 8.dp)) {
                    Text(
                        text = stringResource(R.string.restore_hero_label_top),
                        style = MaterialTheme.typography.headlineMedium
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = stringResource(R.string.restore_hero_label_bottom),
                            style = MaterialTheme.typography.headlineMedium
                        )
                        HelpButton(HelpTopic.RESTORE_HERO)
                    }
                }
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Text(text = figure, style = MaterialTheme.typography.displayMedium)
                }
            }

            HeaderLower(
                state = state,
                context = context,
                message = message,
                firstLabel = stringResource(R.string.retrieve_refresh),
                // Forced: the button is how the user says "read OneDrive again".
                onFirst = { viewModel.refresh(force = true) },
                onClear = viewModel::clearSelection
            )
        }
    }
}

/**
 * What is under the number in both headers: what is selected, the one line of instruction or result,
 * and two buttons, the first of which changes with the level (Refresh on the list, Select all in a folder).
 *
 * Nothing here disappears: Clear is greyed out when there is nothing to clear, so the card does not
 * twitch as the selection changes. Ian, 27 Aug 2026.
 */
@Composable
private fun HeaderLower(
    state: RestoreUiState,
    context: android.content.Context,
    message: String,
    firstLabel: String,
    onFirst: () -> Unit,
    onClear: () -> Unit,
    /**
     * The form inside a folder (Ian, 19 Sept 2026): no (?) on the buttons, and the message's (?)
     * sits straight after the words instead of at the far end of the row.
     */
    compact: Boolean = false
) {
    Column(
        modifier = Modifier.padding(horizontal = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Inside a folder the header draws this itself, beside what the number counts.
        if (state.hasSelection && !compact) {
            WithHelp(HelpTopic.RESTORE_SELECTED_SUMMARY) {
                Text(
                    text = stringResource(
                        R.string.restore_selected_summary,
                        state.selection.size,
                        formatBytes(context, state.bytesToRecover)
                    ),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        if (compact) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f, fill = false)
                )
                HelpButton(HelpTopic.RESTORE_MESSAGE_LINE)
            }
        } else {
            WithHelp(HelpTopic.RESTORE_MESSAGE_LINE) {
                Text(text = message, style = MaterialTheme.typography.bodyMedium)
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            HeroOutlinedButton(
                onClick = onFirst,
                label = firstLabel,
                modifier = Modifier.weight(1f),
                enabled = !state.running
            )
            HeroOutlinedButton(
                onClick = onClear,
                label = stringResource(R.string.retrieve_clear_selection),
                modifier = Modifier.weight(1f),
                enabled = state.hasSelection && !state.running
            )
            if (!compact) HelpButton(HelpTopic.RESTORE_BUTTONS)
        }
    }
}

/**
 * The header inside a folder: the same green card as an album's drill-down on the Albums tab.
 *
 * Left half: the way back and the folder's name in large bold type. Right half: the number, centred,
 * with "Files in this folder" under it. Below, across the card: what is selected, the one line of
 * instruction or result, and Select all and Clear. Drawn here rather than through
 * `HeroCard`, which centres one figure under one label; this one is laid out like the Albums header.
 */
@Composable
private fun FolderHeader(
    state: RestoreUiState,
    viewModel: RestoreViewModel,
    context: android.content.Context
) {
    val signal = LocalGallerySyncColors.current
    val folder = state.openFolder ?: return
    val figure = if (state.loading) "—" else state.visibleRows.count { it.isActionable }.toString()

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = signal.heroContainer,
        contentColor = signal.onHero
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // The folder's name and the number share a line and a centre (Ian, 19 Sept 2026): the name
            // on the left half, larger, and the number in the middle of the right half.
            Row(verticalAlignment = Alignment.CenterVertically) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // The app's own return glyph, in the card's ink.
                    IconButton(onClick = viewModel::closeFolder) {
                        Icon(
                            imageVector = SignalIcons.Back,
                            contentDescription = stringResource(R.string.retrieve_back),
                            modifier = Modifier.size(32.dp),
                            tint = LocalContentColor.current
                        )
                    }
                    // Two lines allowed: half the card is not much room for a long folder name, and
                    // wrapping keeps all of it where an ellipsis would not.
                    Text(
                        text = folder,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                }
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Text(text = figure, style = MaterialTheme.typography.displayMedium)
                }
            }

            // What is selected, on the same level as what the number counts. The row keeps room for
            // two lines whether or not anything is selected, so the card does not change height as
            // files are ticked.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 44.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 8.dp)
                ) {
                    if (state.hasSelection) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = stringResource(
                                    R.string.restore_selected_summary_stacked,
                                    state.selection.size,
                                    formatBytes(context, state.bytesToRecover)
                                ),
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            HelpButton(HelpTopic.RESTORE_SELECTED_SUMMARY)
                        }
                    }
                }
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Text(
                        text = stringResource(R.string.restore_hero_label_files),
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center
                    )
                }
            }

            HeaderLower(
                state = state,
                context = context,
                message = state.summary ?: stringResource(
                    if (state.hasSelection) R.string.restore_intro_files_selected else R.string.restore_intro_files
                ),
                firstLabel = stringResource(R.string.retrieve_select_all),
                onFirst = viewModel::selectAllHere,
                onClear = viewModel::clearSelection,
                compact = true
            )
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
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val scheme = MaterialTheme.colorScheme

    // How far the card has been pulled aside, in px. It follows the finger with some resistance and
    // springs back on release, so a swipe is something the card visibly does rather than a change
    // that appears afterwards. Ian, 19 Sept 2026: "add some animation to the swipe".
    val pull = remember { Animatable(0f) }

    // The card eases into and out of its selected look instead of jumping.
    val container by animateColorAsState(
        if (allSelected) scheme.primaryContainer else scheme.surface, tween(220), label = "container"
    )
    val content by animateColorAsState(
        if (allSelected) scheme.onPrimaryContainer else scheme.onSurface, tween(220), label = "content"
    )
    val borderColor by animateColorAsState(
        if (allSelected) scheme.primary else scheme.outline, tween(220), label = "borderColor"
    )
    val borderWidth by animateDpAsState(if (allSelected) 2.dp else 1.dp, tween(220), label = "borderWidth")

    // 0 at rest, 1 once the pull is far enough that letting go will act.
    val progress = (abs(pull.value) / (SwipeThresholdPx * SwipeResistance)).coerceIn(0f, 1f)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(folder.name, allSelected, enabled) {
                if (!enabled) return@pointerInput
                var travelled = 0f
                var crossed = false
                detectHorizontalDragGestures(
                    onDragStart = {
                        travelled = 0f
                        crossed = false
                        scope.launch { pull.stop() }
                    },
                    onDragEnd = {
                        val wants = when {
                            travelled > SwipeThresholdPx -> true
                            travelled < -SwipeThresholdPx -> false
                            else -> allSelected
                        }
                        if (wants != allSelected) onSetSelected(wants)
                        scope.launch { pull.animateTo(0f, SwipeReturnSpring) }
                    },
                    onDragCancel = { scope.launch { pull.animateTo(0f, SwipeReturnSpring) } }
                ) { change, amount ->
                    travelled += amount
                    if (abs(travelled) > SwipeThresholdPx) change.consume()

                    // A short tick the moment the pull is far enough to act, and again if it is
                    // taken back under, so the finger can feel where the line is.
                    val past = abs(travelled) > SwipeThresholdPx
                    if (past != crossed) {
                        crossed = past
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    }

                    val visual = (travelled * SwipeResistance).coerceIn(-MaxPullPx, MaxPullPx)
                    scope.launch { pull.snapTo(visual) }
                }
            }
    ) {
        // Uncovered as the card is pulled aside, and it says what letting go will do: a tick on the
        // right for taking the folder, a cross on the left for putting it down.
        if (pull.value != 0f) {
            val selecting = pull.value > 0f
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clip(RoundedCornerShape(22.dp))
                    .background(
                        (if (selecting) scheme.primaryContainer else scheme.surfaceVariant)
                            .copy(alpha = progress)
                    ),
                contentAlignment = if (selecting) Alignment.CenterStart else Alignment.CenterEnd
            ) {
                Icon(
                    imageVector = if (selecting) SignalIcons.Check else SignalIcons.Cross,
                    contentDescription = null,
                    tint = if (selecting) scheme.primary else scheme.onSurfaceVariant,
                    modifier = Modifier
                        .padding(horizontal = 22.dp)
                        .size(24.dp)
                        .graphicsLayer {
                            alpha = progress
                            scaleX = 0.6f + 0.4f * progress
                            scaleY = 0.6f + 0.4f * progress
                        }
                )
            }
        }

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .offset { IntOffset(pull.value.roundToInt(), 0) },
            shape = RoundedCornerShape(22.dp),
            color = container,
            contentColor = content,
            border = BorderStroke(borderWidth, borderColor),
            enabled = enabled,
            onClick = onOpen
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // The Albums card's own type (Ian, 19 Sept 2026): the folder's name in bold bodyLarge,
                // its detail lines in bodySmall, no gap between them. It was a headline over larger
                // detail lines.
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = folder.name,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = stringResource(
                            R.string.restore_folder_detail,
                            folder.total,
                            formatBytes(context, folder.bytesToRecover)
                        ),
                        style = MaterialTheme.typography.bodySmall
                    )
                    // The two populations named separately, because they are different operations on
                    // the user's phone and a single total would hide that.
                    Text(
                        text = stringResource(
                            R.string.restore_folder_split,
                            folder.restorable,
                            folder.downloadable
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (folder.here > 0) {
                        Text(
                            text = stringResource(R.string.restore_folder_here, folder.here),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (folder.selectedHere > 0 && !allSelected) {
                        Text(
                            text = stringResource(R.string.retrieve_picked_here, folder.selectedHere),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                Crossfade(targetState = allSelected, animationSpec = tween(200), label = "mark") { selectedNow ->
                    Icon(
                        imageVector = if (selectedNow) SignalIcons.Check else SignalIcons.ChevronRight,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = if (selectedNow) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
            }
        }
    }
}

/** One file: what it is now, what it would become, and how far along it is. */
@Composable
private fun FileCard(
    row: RestoreRow,
    selected: Boolean,
    enabled: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    val scheme = MaterialTheme.colorScheme
    val container by animateColorAsState(
        if (selected) scheme.primaryContainer else scheme.surface, tween(220), label = "container"
    )
    val content by animateColorAsState(
        if (selected) scheme.onPrimaryContainer else scheme.onSurface, tween(220), label = "content"
    )
    val borderColor by animateColorAsState(
        if (selected) scheme.primary else scheme.outline, tween(220), label = "borderColor"
    )
    val borderWidth by animateDpAsState(if (selected) 2.dp else 1.dp, tween(220), label = "borderWidth")

    Surface(
        // Greyed out by fading the whole card, so it reads as unavailable in both themes without a
        // colour of its own being chosen.
        modifier = modifier
            .fillMaxWidth()
            .alpha(if (row.isActionable) 1f else 0.5f),
        shape = RoundedCornerShape(22.dp),
        color = container,
        contentColor = content,
        border = BorderStroke(borderWidth, borderColor),
        enabled = enabled && row.isActionable,
        onClick = onToggle
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // The Albums card's type (Ian, 19 Sept 2026): the name in bodyLarge, then bodySmall lines
            // with no gap. A file's name is not bold; a folder's is.
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = row.displayName,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
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

                        RowKind.Here -> stringResource(
                            R.string.restore_here_size,
                            formatBytes(context, row.fullBytes)
                        )
                    },
                    style = MaterialTheme.typography.bodySmall
                )

                when (val rowState = row.state) {
                    RowState.Waiting -> Unit

                    is RowState.Working -> {
                        Text(
                            text = stringResource(R.string.restore_downloading, rowState.percent),
                            style = MaterialTheme.typography.bodySmall
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
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )

                    // Says the file is unchanged, because it is. Not softened into an apology: this
                    // is the sentence that tells the user a failure here costs them nothing.
                    is RowState.Failed -> Text(
                        text = stringResource(R.string.restore_failed_row, rowState.reason),
                        style = MaterialTheme.typography.bodySmall,
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
private fun RestoreBar(
    running: Boolean,
    progress: RestoreProgress?,
    onRestore: () -> Unit,
    onStop: () -> Unit
) {
    val signal = LocalGallerySyncColors.current

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = if (running) onStop else onRestore,
                modifier = Modifier.weight(1f),
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

        // Under the button, where the eye already is: the only sign a restore is under way used to
        // be the button's own label. Ian, 24 Sept 2026.
        if (progress != null) {
            Spacer(Modifier.size(8.dp))
            LinearProgressIndicator(
                progress = { progress.fraction },
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                text = stringResource(
                    R.string.restore_progress,
                    progress.finished,
                    progress.total,
                    (progress.fraction * 100).toInt()
                ),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
        }
    }
}
