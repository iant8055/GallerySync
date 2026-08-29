package com.gallery.sync.ui.archive

import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gallery.sync.R
import com.gallery.sync.domain.backup.ArchiveDelay
import com.gallery.sync.domain.backup.ArchiveEntry
import com.gallery.sync.domain.backup.ArchiveFailure
import com.gallery.sync.domain.backup.ArchiveMark
import com.gallery.sync.ui.common.HeroCard
import com.gallery.sync.ui.common.HeroOutlinedButton
import com.gallery.sync.ui.common.SignalIcons
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
    LaunchedEffect(Unit) {
        if (state.phase == ArchivePhase.IDLE) viewModel.load()
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
            // The same card Albums and Restore open with. The figure is the number of files waiting
            // on this tab — which is what someone arriving here wants to know before anything else.
            HeroCard(
                label = stringResource(R.string.archive_hero_label),
                figure = state.plan.entries.size.toString(),
                detail = {
                    when {
                        !state.isSupported -> Text(
                            text = stringResource(R.string.archive_unsupported),
                            style = MaterialTheme.typography.bodySmall
                        )

                        // Two different emptinesses. No Archive album at all means nothing here can
                        // remove anything; Archive albums holding no files means the mode finished
                        // and is still standing. Telling the user the first when the second is true
                        // would be false about the one mode that takes files off the phone.
                        state.plan.isEmpty && state.archiveAlbums.isEmpty() -> {
                            Text(
                                text = stringResource(R.string.archive_empty),
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                text = stringResource(R.string.archive_empty_hint),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }

                        // Archive albums exist and hold nothing: the mode ran to completion, and
                        // the card already says so — "Files to Archive" standing over a zero. The
                        // two sentences that sat here restated the standing-instruction rule on
                        // every visit to a finished tab; that explanation belongs in Help
                        // (TASK-017), not here. Ian, 27 Aug 2026.
                        state.plan.isEmpty -> Unit

                        else -> ArchiveHeroDetail(state)
                    }
                },
                actions = {
                    if (state.isSupported && !state.plan.isEmpty) {
                        ArchiveHeroActions(state = state, onValidate = viewModel::validate)
                    }
                }
            )
        }

        if (state.showPrompt) {
            ArchivePrompt(
                state = state,
                onYes = {
                    scope.launch {
                        viewModel.nextRemovalRequest()?.let {
                            removalLauncher.launch(IntentSenderRequest.Builder(it).build())
                        }
                    }
                },
                onNo = viewModel::dismiss,
                onDelay = viewModel::delay
            )
        }

        if (state.plan.entries.isNotEmpty()) {
            HorizontalDivider()
            // weight(1f) rather than fillMaxWidth alone: the list takes whatever height is left once
            // the header and the question have theirs, so a long album scrolls inside its own space
            // instead of pushing the question off the screen.
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                items(state.plan.entries, key = { it.item.mediaStoreId }) { entry ->
                    ArchiveRow(entry)
                }
            }
        }
    }
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

        ArchivePhase.DONE -> Text(
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

        ArchivePhase.READY -> if (state.delayedUntil != null) {
            Text(
                text = stringResource(R.string.archive_delayed),
                style = MaterialTheme.typography.bodySmall
            )
        }
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
private fun ArchiveRow(entry: ArchiveEntry) {
    val context = LocalContext.current
    val signal = LocalGallerySyncColors.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = entry.name,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1
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
    onNo: () -> Unit,
    onDelay: (ArchiveDelay) -> Unit
) {
    val context = LocalContext.current
    var delayOpen by remember { mutableStateOf(false) }

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
                Text(
                    text = stringResource(R.string.archive_none_confirmed),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
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
                Text(
                    text = stringResource(R.string.archive_prompt_partial_title, confirmedCount),
                    style = MaterialTheme.typography.titleSmall
                )
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
                Text(
                    text = stringResource(R.string.archive_prompt_all_title),
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    text = stringResource(R.string.archive_prompt_all_body, freed),
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
                TextButton(onClick = { delayOpen = !delayOpen }) {
                    Text(stringResource(R.string.archive_prompt_delay), maxLines = 1)
                }
            }

            if (delayOpen) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { onDelay(ArchiveDelay.ONE_HOUR) }) {
                        Text(stringResource(R.string.archive_delay_1h), maxLines = 1)
                    }
                    TextButton(onClick = { onDelay(ArchiveDelay.TWELVE_HOURS) }) {
                        Text(stringResource(R.string.archive_delay_12h), maxLines = 1)
                    }
                    TextButton(onClick = { onDelay(ArchiveDelay.ONE_DAY) }) {
                        Text(stringResource(R.string.archive_delay_1d), maxLines = 1)
                    }
                }
            }
        }
    }
}
