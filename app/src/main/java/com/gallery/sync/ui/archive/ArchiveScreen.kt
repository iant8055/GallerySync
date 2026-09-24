package com.gallery.sync.ui.archive

import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gallery.sync.R
import com.gallery.sync.data.local.media.LocalMediaItem
import com.gallery.sync.domain.backup.ArchiveAge
import com.gallery.sync.domain.backup.ArchiveEntry
import com.gallery.sync.domain.backup.ArchivePlan
import com.gallery.sync.domain.backup.ArchiveFailure
import com.gallery.sync.domain.backup.ArchiveMark
import com.gallery.sync.ui.help.HelpButton
import com.gallery.sync.ui.help.HelpTopic
import com.gallery.sync.ui.help.TitleWithHelp
import com.gallery.sync.ui.help.WithHelp
import com.gallery.sync.ui.common.HeroOutlinedButton
import com.gallery.sync.ui.common.SignalIcons
import com.gallery.sync.ui.common.SwipeChoiceBox
import com.gallery.sync.ui.common.formatBytes
import com.gallery.sync.ui.theme.LocalGallerySyncColors
import kotlinx.coroutines.launch

/**
 * Removal gets its own screen, its own time, and the user's own eyes on the file names.
 *
 * ### Why this is not a two-line prompt on another tab
 *
 * This is the largest irreversible action in the product. Until 26 Aug 2026 it was offered as a
 * count and a button above an unrelated list of albums — authorising a number rather than a list.
 * Here the user sees the files, watches them being checked, and answers one question afterwards.
 *
 * ### It is still one consent, not two
 *
 * CLAUDE.md: the album mode *is* the consent, given once when the mode is set. This screen adds no
 * second approval. What it adds is the **summons**, which exists only because `createTrashRequest`
 * cannot launch without an Activity — made legible instead of terse.
 *
 * ### Not a gallery
 *
 * Names, sizes and a mark. No thumbnails, no grid, no sort, no preview. The design principle rules
 * that out, and it would also make this screen pleasant to linger on, which is the opposite of what
 * it is for.
 */
@Composable
fun ArchiveScreen(
    modifier: Modifier = Modifier,
    viewModel: ArchiveViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    val removalLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { viewModel.onRemovalDialogClosed() }

    // Re-read the album every time the tab is opened.
    //
    // `load()` used to run only from the ViewModel's init, and the ViewModel is scoped to the
    // Activity — so it ran once per app session, at whatever moment the tab was first shown. Open
    // the tab before setting any album to Archive and the empty list it built then never changed:
    // setting an album to Archive afterwards took the user straight here, to a screen still saying
    // "No album is set to Archive". Observed on the Moto G, 28 Aug 2026, with eight files verified
    // and offered by the engine at the same moment the screen said there were none.
    //
    // The other ordering — set the mode first, arrive here with the ViewModel not yet built — runs
    // init with the album already in place and looks perfectly correct, which is why this survived
    // the hardware pass that introduced the summons.
    //
    // Only from IDLE. A reload during VALIDATING or REMOVING would cut across a run, from READY it
    // would throw away the validation the user is being asked about, and from DONE it would wipe
    // the report of what was just removed.
    //
    // Past IDLE it still refreshes the file lists (Ian, 19 Sept 2026: a file put in an Archive album
    // should appear each time the tab is opened), merging new files in and leaving the phase and the
    // report of what was just removed alone. See `ArchiveViewModel.refreshFiles`.
    LaunchedEffect(Unit) {
        if (state.phase == ArchivePhase.IDLE) viewModel.load() else viewModel.refreshFiles()
    }

    // Each finished dialog may be followed by another, because Android caps a trash request at 2000
    // URIs. Driving the next one from the batch index keeps a large album reading as one operation
    // rather than as the app asking again because something went wrong.
    //
    // Keyed on the index rather than the phase alone: the phase stays REMOVING across every batch,
    // so a phase-only key would fire once and stall on album 2001.
    LaunchedEffect(state.phase, state.batchIndex) {
        if (state.phase == ArchivePhase.REMOVING && state.batchIndex > 0) {
            viewModel.nextRemovalRequest()?.let {
                removalLauncher.launch(IntentSenderRequest.Builder(it).build())
            }
        }
    }

    // One Column, and everything is a child of it. The prompt used to be a sibling of this layout,
    // which in Compose means it paints *over* the list rather than beside it — the header and the
    // first rows disappeared underneath it. Seen immediately on the Fold 4, 26 Aug 2026.
    //
    // The order is deliberate now that they share a flow: what is happening, then the question,
    // then the names. The list keeps its own scroll under the question rather than being pushed off,
    // because the whole argument for this screen is that the user can see what they are authorising
    // while they authorise it.
    Column(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // The same header as the Restore list, and laid out like the drill-downs on Albums and
            // Restore (Ian, 19 Sept 2026): "Files to" with "Archive" under it on the left half, the
            // number waiting on this tab centred in the right half.
            ArchiveHeader(state = state, onValidate = viewModel::validate, onSetAgeFilter = viewModel::setAgeFilter)
        }

        if (state.showPrompt()) {
            ArchivePrompt(
                state = state,
                onYes = {
                    scope.launch {
                        viewModel.nextRemovalRequest()?.let {
                            removalLauncher.launch(IntentSenderRequest.Builder(it).build())
                        }
                    }
                },
                onNo = viewModel::dismiss
            )
        }

        // Every file in an Archive album: the ones that will be archived, the ones the user has
        // swiped out, and the ones held back by the age filter — all greyed except the first, in one
        // list in name order (Ian, 19 Sept 2026 for the opt-out rows; 22 Sept for the age ones). A
        // file that arrives in an Archive album later shows up here too, so there is always a chance
        // to opt it out first.
        val rows = remember(state.plan.entries, state.optedOut, state.hiddenByAge) {
            (
                state.plan.entries.map { ArchiveListRow(it.item, it) } +
                    state.optedOut.map { ArchiveListRow(it, null) } +
                    state.hiddenByAge.map { ArchiveListRow(it, null, hiddenByAge = true) }
                )
                .sortedWith(compareBy({ it.item.album }, { it.item.displayName }))
        }
        // Not while a check or a removal is running: they act on the list as it was when they began.
        val canSwipe = state.isSupported &&
            state.phase != ArchivePhase.VALIDATING && state.phase != ArchivePhase.REMOVING

        if (rows.isNotEmpty()) {
            HorizontalDivider()
            // weight(1f) rather than fillMaxWidth alone: the list takes whatever height is left once
            // the header and the question have theirs, so a long album scrolls inside its own space
            // instead of pushing the question off the screen.
            //
            // Two columns from 600dp, as on Restore and Albums: unfolded, more rows rather than wider
            // ones.
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                val columns = if (maxWidth >= WideBreakpoint) 2 else 1
                val half = (rows.size + columns - 1) / columns

                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    item(key = "archive-heading") {
                        WithHelp(HelpTopic.ARCHIVE_FILE_LIST) {
                            Text(
                                text = stringResource(R.string.archive_list_heading),
                                style = MaterialTheme.typography.titleSmall
                            )
                        }
                    }
                    item(key = "archive-swipe-hint") {
                        Text(
                            text = stringResource(R.string.archive_swipe_hint),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    items(half) { index ->
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            for (column in 0 until columns) {
                                Box(modifier = Modifier.weight(1f)) {
                                    rows.getOrNull(index + column * half)?.let { row ->
                                        ArchiveListItem(
                                            row = row,
                                            canSwipe = canSwipe,
                                            onSetOptedOut = viewModel::setOptedOut
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
}

/**
 * One line of the list: a file, and its entry if it is going to be archived.
 *
 * A null entry means the file is not currently in the plan, for one of two reasons: the user swiped
 * it out ([hiddenByAge] false), or it is younger than the current age filter ([hiddenByAge] true).
 * Only the first is a per-file choice; the row still swipes either way, since pinning a file the
 * age filter is already holding back is a strictly more permanent version of the same thing.
 */
private data class ArchiveListRow(val item: LocalMediaItem, val entry: ArchiveEntry?, val hiddenByAge: Boolean = false)

/**
 * A file's card, swipeable. Left keeps a file on this phone, right puts it back in Archive; each does
 * nothing on a file already that way, so a run of swipes cannot undo itself.
 */
@Composable
private fun ArchiveListItem(
    row: ArchiveListRow,
    canSwipe: Boolean,
    onSetOptedOut: (LocalMediaItem, Boolean) -> Unit
) {
    // A file held back by age is not pinned, so it reads as "not opted out" for the swipe state —
    // swiping it left pins it (a stronger, permanent choice); swiping right does nothing, the same
    // as any other file that was never opted out.
    val optedOut = row.entry == null && !row.hiddenByAge
    SwipeChoiceBox(
        enabled = canSwipe,
        stateKey = optedOut,
        onSwipeRight = { if (optedOut) onSetOptedOut(row.item, false) },
        onSwipeLeft = { if (!optedOut) onSetOptedOut(row.item, true) },
        accessibilityLabel = stringResource(
            if (optedOut) R.string.archive_action_archive else R.string.archive_action_keep
        ),
        onAccessibilityAction = { onSetOptedOut(row.item, !optedOut) }
    ) { drawn ->
        when {
            row.entry != null -> ArchiveRow(row.entry, drawn)
            row.hiddenByAge -> HiddenByAgeRow(row.item, drawn)
            else -> OptedOutRow(row.item, drawn)
        }
    }
}

/**
 * The green header: "Files to" with "Archive" directly under it on the left half, the number of files
 * waiting on this tab centred in the right half, and under them what is happening, and the control.
 *
 * The same surface and layout as the Restore list's header, so the three tabs open alike. It was the
 * shared `HeroCard`, which centres one figure under one label; the drill-downs on Albums and Restore
 * moved to this arrangement on 19 Sept 2026 and Archive follows.
 */
@Composable
private fun ArchiveHeader(state: ArchiveUiState, onValidate: () -> Unit, onSetAgeFilter: (ArchiveAge) -> Unit) {
    val signal = LocalGallerySyncColors.current

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
                Column(modifier = Modifier.weight(1f).padding(start = 8.dp)) {
                    Text(
                        text = stringResource(R.string.archive_hero_label_top),
                        style = MaterialTheme.typography.headlineMedium
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = stringResource(R.string.archive_hero_label_bottom),
                            style = MaterialTheme.typography.headlineMedium
                        )
                        HelpButton(HelpTopic.ARCHIVE_HERO)
                    }
                }
                // The number of files waiting on this tab, which is what someone arriving here wants
                // to know before anything else.
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Text(
                        text = state.plan.entries.size.toString(),
                        style = MaterialTheme.typography.displayMedium
                    )
                }
            }

            Column(
                modifier = Modifier.padding(horizontal = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                when {
                    !state.isSupported -> WithHelp(HelpTopic.ARCHIVE_EMPTY) {
                        Text(
                            text = stringResource(R.string.archive_unsupported),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }

                    // Two different emptinesses. No Archive album at all means nothing here can
                    // remove anything; Archive albums holding no files means the mode finished
                    // and is still standing. Telling the user the first when the second is true
                    // would be false about the one mode that takes files off the phone.
                    state.plan.isEmpty && state.archiveAlbums.isEmpty() -> {
                        WithHelp(HelpTopic.ARCHIVE_EMPTY) {
                            Text(
                                text = stringResource(R.string.archive_empty),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        Text(
                            text = stringResource(R.string.archive_empty_hint),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }

                    // Archive albums exist and hold nothing: the mode ran to completion, and the card
                    // already says so, with the number at zero. The two sentences that sat here
                    // restated the standing-instruction rule on every visit to a finished tab; that
                    // explanation belongs in Help (TASK-017), not here. Ian, 27 Aug 2026.
                    state.plan.isEmpty -> Unit

                    else -> ArchiveHeroDetail(state)
                }

                // Shown whenever an Archive album exists, even if the filter currently hides every
                // file in it — otherwise there would be no way back from a filter that empties the
                // list. Ian, 22 Sept 2026.
                if (state.isSupported && state.archiveAlbums.isNotEmpty()) {
                    ArchiveAgeSection(
                        age = state.ageFilter,
                        hiddenCount = state.hiddenByAge.size,
                        // Not while a check or a removal is running, or the question is up: each
                        // describes a list already fixed. Mirrors Camera's own `enabled = !running`.
                        enabled = state.phase == ArchivePhase.IDLE || state.phase == ArchivePhase.DONE,
                        onSetAge = onSetAgeFilter
                    )
                }

                // Where an archived album goes to be brought back. Moved here from the Albums header
                // (Ian, 18 Sept 2026): it is about Archive, so it belongs on this tab.
                Text(
                    text = stringResource(R.string.archive_restore_pointer),
                    style = MaterialTheme.typography.bodySmall,
                    color = LocalContentColor.current.copy(alpha = 0.7f)
                )

                if (state.isSupported && !state.plan.isEmpty) {
                    WithHelp(HelpTopic.ARCHIVE_CHECK_BUTTON) {
                        ArchiveHeroActions(state = state, onValidate = onValidate)
                    }
                }
            }
        }
    }
}

/** Two columns of file cards from here up, as on Restore and Albums. */
private val WideBreakpoint = 600.dp

/**
 * *Only show files older than…*, and, when the filter is holding files back, how many.
 *
 * Always visible once an Archive album exists (see the call site), because the alternative — this
 * control disappearing exactly when the filter empties the list — would leave no way back to a
 * wider view. The dropdown always shows a value; unlike Camera's one-shot picker there is no unset
 * state, so there is no separate cancel control — choosing **All** is how you see everything.
 */
@Composable
private fun ArchiveAgeSection(age: ArchiveAge, hiddenCount: Int, enabled: Boolean, onSetAge: (ArchiveAge) -> Unit) {
    var menuOpen by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(text = stringResource(R.string.archive_age_label), style = MaterialTheme.typography.bodySmall)
            HelpButton(HelpTopic.ARCHIVE_AGE_FILTER)
            Box {
                HeroOutlinedButton(
                    onClick = { menuOpen = true },
                    label = stringResource(age.label()),
                    enabled = enabled
                )
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    ArchiveAge.entries.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(stringResource(option.label())) },
                            onClick = {
                                onSetAge(option)
                                menuOpen = false
                            }
                        )
                    }
                }
            }
        }
        if (hiddenCount > 0) {
            Text(
                text = pluralStringResource(R.plurals.archive_age_hidden_hint, hiddenCount, hiddenCount),
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

private fun ArchiveAge.label(): Int = when (this) {
    ArchiveAge.OneHour -> R.string.archive_age_hour
    ArchiveAge.OneDay -> R.string.archive_age_day
    ArchiveAge.OneWeek -> R.string.archive_age_week
    ArchiveAge.OneMonth -> R.string.archive_age_month
    ArchiveAge.OneYear -> R.string.archive_age_year
    ArchiveAge.All -> R.string.archive_age_all
}

/** The album names and the one-line explanation of what this tab does before it does it. */
@Composable
private fun ArchiveHeroDetail(state: ArchiveUiState) {
    Text(
        text = state.plan.albums.joinToString(", "),
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold
    )
    Text(
        text = stringResource(R.string.archive_intro),
        style = MaterialTheme.typography.bodySmall
    )
}

/**
 * The control, or what is happening instead of one.
 *
 * Sits in the hero's action slot, where Albums keeps Sync now and Rescan — so the thing the user
 * came to press is in the same place on every tab.
 */
@Composable
private fun ArchiveHeroActions(state: ArchiveUiState, onValidate: () -> Unit) {
    val context = LocalContext.current

    when (state.phase) {
        ArchivePhase.IDLE -> HeroOutlinedButton(
            onClick = onValidate,
            label = stringResource(R.string.archive_validate)
        )

        ArchivePhase.VALIDATING -> Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CircularProgressIndicator(modifier = Modifier.size(16.dp))
            Text(
                text = stringResource(R.string.archive_validating),
                style = MaterialTheme.typography.bodySmall
            )
        }

        ArchivePhase.REMOVING -> Text(
            text = stringResource(
                R.string.archive_batch_progress,
                state.batchIndex + 1,
                state.batchTotal
            ),
            style = MaterialTheme.typography.bodySmall
        )

        ArchivePhase.DONE -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = if (state.removedCount == 0) {
                    stringResource(R.string.archive_done_none)
                } else {
                    stringResource(
                        R.string.archive_done,
                        pluralStringResource(
                            R.plurals.file_count,
                            state.removedCount,
                            state.removedCount
                        ),
                        formatBytes(context, state.removedBytes)
                    )
                },
                style = MaterialTheme.typography.bodySmall
            )
            // Files still waiting means the run did not take them all, or was refused. Asking again
            // must not need a restart.
            if (state.offersCheck()) {
                HeroOutlinedButton(
                    onClick = onValidate,
                    label = stringResource(R.string.archive_validate)
                )
            }
        }

        // The question itself is the prompt below; there is nothing to add here.
        ArchivePhase.READY -> Unit
    }
}

/**
 * One file: its name, its size, and one mark.
 *
 * A tick or a cross rather than a per-file bar. Verification is one Graph listing per album, not a
 * request per file, so a bar filling per row would be animation dressed as information — decided by
 * Ian, 26 Aug 2026.
 */
@Composable
private fun ArchiveRow(entry: ArchiveEntry, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val signal = LocalGallerySyncColors.current

    // A rounded card in the Restore file card's style, which is the Albums card's (Ian, 19 Sept 2026):
    // the same shape, outline and padding, the name in bodyLarge and the line under it in bodySmall.
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = entry.name,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val detail = when {
                    entry.failure == ArchiveFailure.COULD_NOT_CHECK ->
                        stringResource(R.string.archive_failed_unchecked)

                    entry.failure == ArchiveFailure.NOT_BACKED_UP ->
                        stringResource(R.string.archive_failed_not_backed_up)

                    entry.failure == ArchiveFailure.WRONG_SIZE_IN_CLOUD ->
                        stringResource(R.string.archive_failed_wrong_size)

                    entry.mark == ArchiveMark.BACKING_UP ->
                        stringResource(R.string.archive_state_backing_up)

                    entry.mark == ArchiveMark.REMOVING ->
                        stringResource(R.string.archive_state_removing)

                    entry.mark == ArchiveMark.REMOVED ->
                        stringResource(R.string.archive_state_removed)

                    else -> formatBytes(context, entry.sizeBytes)
                }
                Text(
                    text = detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (entry.failure != null) {
                        MaterialTheme.colorScheme.error
                    } else {
                        LocalContentColor.current
                    }
                )
            }

            when (entry.mark) {
                ArchiveMark.CHECKING, ArchiveMark.BACKING_UP, ArchiveMark.REMOVING ->
                    CircularProgressIndicator(modifier = Modifier.size(18.dp))

                ArchiveMark.CONFIRMED, ArchiveMark.REMOVED -> Icon(
                    imageVector = SignalIcons.Check,
                    contentDescription = null,
                    tint = signal.accent,
                    modifier = Modifier.size(22.dp)
                )

                ArchiveMark.FAILED -> Icon(
                    imageVector = SignalIcons.Cross,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(22.dp)
                )

                ArchiveMark.WAITING -> Unit
            }
        }
    }
}

/**
 * A file the user has swiped out of Archive: the same card, faded the way Restore fades a file that
 * cannot be chosen, and saying why it is there. No tick, no cross and no spinner: it is not being
 * checked or removed, and swiping it back is how it rejoins the list.
 */
@Composable
private fun OptedOutRow(item: LocalMediaItem, modifier: Modifier = Modifier) {
    val context = LocalContext.current

    Surface(
        // Faded as a whole so it reads as set aside in both themes without a colour of its own.
        modifier = modifier
            .fillMaxWidth()
            .alpha(0.5f),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp)) {
            Text(
                text = item.displayName,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = stringResource(R.string.archive_opted_out_detail, formatBytes(context, item.sizeBytes)),
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

/**
 * A file younger than the current age filter: the same faded card as [OptedOutRow], saying why it
 * is set aside for a different reason. Swiping it left still pins it permanently — see
 * [ArchiveListItem].
 */
@Composable
private fun HiddenByAgeRow(item: LocalMediaItem, modifier: Modifier = Modifier) {
    val context = LocalContext.current

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .alpha(0.5f),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp)) {
            Text(
                text = item.displayName,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = stringResource(R.string.archive_hidden_by_age_detail, formatBytes(context, item.sizeBytes)),
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

/**
 * The one question, asked once per archive operation.
 *
 * Two shapes, because a partial result must never be dressed as a complete one: when some files are
 * red the count and the size describe **only** the green set, and the button says as much. Reporting
 * "All files VALIDATED" over a partial run would be the app claiming a guarantee it does not have.
 */
@Composable
private fun ArchivePrompt(
    state: ArchiveUiState,
    onYes: () -> Unit,
    onNo: () -> Unit
) {
    val context = LocalContext.current

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Nothing survived the check, so there is nothing to offer. Showing a Yes button here
            // would offer an action that cannot succeed.
            if (state.plan.allFailed) {
                WithHelp(HelpTopic.ARCHIVE_PROMPT) {
                    Text(
                        text = stringResource(R.string.archive_none_confirmed),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                // "Continue", not "No". Ian, 27 Aug 2026, on seeing this screen for real: nothing
                // can be archived here and the app is not asking for anything, so a No button was
                // an answer to a question nobody put. This one only dismisses.
                TextButton(onClick = onNo) {
                    Text(stringResource(R.string.archive_prompt_continue))
                }
                return@Column
            }

            val confirmedCount = pluralStringResource(
                R.plurals.file_count,
                state.plan.confirmed.size,
                state.plan.confirmed.size
            )
            val freed = formatBytes(context, state.plan.freeableBytes)

            if (state.plan.isPartial) {
                WithHelp(HelpTopic.ARCHIVE_PROMPT) {
                    Text(
                        text = stringResource(R.string.archive_prompt_partial_title, confirmedCount),
                        style = MaterialTheme.typography.titleSmall
                    )
                }
                Text(
                    text = stringResource(
                        R.string.archive_prompt_partial_body,
                        pluralStringResource(
                            R.plurals.file_count,
                            state.plan.failed.size,
                            state.plan.failed.size
                        ),
                        freed
                    ),
                    style = MaterialTheme.typography.bodyMedium
                )
            } else {
                WithHelp(HelpTopic.ARCHIVE_PROMPT) {
                    Text(
                        text = stringResource(R.string.archive_prompt_all_title),
                        style = MaterialTheme.typography.titleSmall
                    )
                }
                Text(
                    text = stringResource(R.string.archive_prompt_all_body, freed),
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            if (state.batchTotal > 1) {
                Text(
                    text = stringResource(R.string.archive_prompt_batches, state.batchTotal),
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            // The trash caveat that used to sit here is gone, 28 Aug 2026. It warned that a local
            // removal "may be permanent on the phone" — disproved on both handsets, three runs, and
            // finally by Ian finding all eight files in the Moto's Files app. What was worth keeping
            // from it, that the space does not return until the bin is emptied, moved into the
            // sentence above rather than qualifying it from underneath.

            Text(
                text = stringResource(R.string.archive_prompt_question),
                style = MaterialTheme.typography.bodyMedium
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(onClick = onYes) {
                    Text(stringResource(R.string.archive_prompt_yes), maxLines = 1)
                }
                OutlinedButton(onClick = onNo) {
                    Text(stringResource(R.string.archive_prompt_no), maxLines = 1)
                }
            }
        }
    }
}

/**
 * The Archive tab as the setup tour draws it behind its cards: the real header and file cards with sample
 * files, so it follows the tab when it is restyled. Inert: the tour lays a layer over it that swallows
 * touches.
 */
@Composable
fun ArchiveTabPreview() {
    fun file(id: Long, name: String, album: String, mb: Long, isVideo: Boolean = false) = LocalMediaItem(
        mediaStoreId = id,
        contentUri = android.net.Uri.EMPTY,
        displayName = name,
        album = album,
        sizeBytes = mb * 1024L * 1024L,
        dateModifiedEpochSeconds = 0L,
        mimeType = if (isVideo) "video/mp4" else "image/jpeg",
        isVideo = isVideo,
        relativePath = null
    )

    val entries = listOf(
        ArchiveEntry(file(1, "IMG_20250615_142031.jpg", "Camera", 4), ArchiveMark.CONFIRMED),
        ArchiveEntry(file(2, "IMG_20250612_091547.jpg", "Camera", 4), ArchiveMark.CONFIRMED),
        ArchiveEntry(file(3, "VID_20250610_183022.mp4", "Camera", 148, isVideo = true), ArchiveMark.CONFIRMED),
        ArchiveEntry(file(4, "Screenshot_20250608.png", "Downloads", 1), ArchiveMark.FAILED, ArchiveFailure.NOT_BACKED_UP)
    )
    val state = ArchiveUiState(
        plan = ArchivePlan(entries = entries),
        archiveAlbums = listOf("Camera", "Downloads")
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        ArchiveHeader(state = state, onValidate = {}, onSetAgeFilter = {})
        entries.forEach { ArchiveRow(it) }
    }
}
