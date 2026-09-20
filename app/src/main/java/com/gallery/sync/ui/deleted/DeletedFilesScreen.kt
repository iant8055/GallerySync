package com.gallery.sync.ui.deleted

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gallery.sync.R
import com.gallery.sync.data.local.entity.BackupEntryEntity
import com.gallery.sync.domain.backup.DeletionOutcome
import com.gallery.sync.ui.common.HeroOutlinedButton
import com.gallery.sync.ui.common.formatBytes
import com.gallery.sync.ui.help.HelpButton
import com.gallery.sync.ui.help.HelpTopic
import com.gallery.sync.ui.help.TitleWithHelp
import com.gallery.sync.ui.theme.LocalGallerySyncColors

/**
 * Puts the "files deleted from this phone" window in front of the app when there is something new to
 * decide, and lets the app through when there is not. Ian, 19 Sept 2026.
 *
 * It replaces the content rather than covering it, so nothing underneath can be touched or read
 * while it is up, and the Albums tab is not even drawn behind it. The look is done again when the
 * app comes to the front; see [DeletedFilesViewModel.evaluate] for when that shows anything.
 *
 * Placed only around the signed-in, set-up app. The wizard never sees it.
 */
@Composable
fun DeletedFilesGate(
    modifier: Modifier = Modifier,
    viewModel: DeletedFilesViewModel = hiltViewModel(),
    content: @Composable () -> Unit
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LifecycleEventEffect(Lifecycle.Event.ON_START) { viewModel.evaluate() }

    if (state.phase == DeletedFilesPhase.HIDDEN) {
        content()
    } else {
        DeletedFilesScreen(state = state, viewModel = viewModel, modifier = modifier)
    }
}

@Composable
private fun DeletedFilesScreen(
    state: DeletedFilesUiState,
    viewModel: DeletedFilesViewModel,
    modifier: Modifier = Modifier
) {
    // Back never removes anything: it leaves without deciding, or, once the window has reported, just
    // puts it away. While it is working it does nothing, so a half-finished removal is not walked away
    // from.
    BackHandler(enabled = state.phase != DeletedFilesPhase.WORKING) {
        if (state.phase == DeletedFilesPhase.DONE) viewModel.finish() else viewModel.decideLater()
    }

    Column(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            DeletedFilesHeader(state = state, viewModel = viewModel)
        }

        if (state.phase == DeletedFilesPhase.DONE) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                Outcome(state)
            }
            Surface(tonalElevation = 3.dp, modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = viewModel::finish,
                    modifier = Modifier.fillMaxWidth().padding(16.dp)
                ) {
                    Text(stringResource(R.string.deleted_files_continue))
                }
            }
        } else {
            val interactive = state.phase == DeletedFilesPhase.LISTING
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(state.files, key = { it.id }) { file ->
                    DeletedFileCard(
                        file = file,
                        selected = file.id in state.selected,
                        enabled = interactive,
                        onToggle = { viewModel.toggle(file.id) }
                    )
                }
            }
            ActionBar(state = state, viewModel = viewModel)
        }
    }

    if (state.confirming) {
        ConfirmDialog(
            count = state.selected.size,
            bytes = state.selectedBytes,
            onConfirm = viewModel::confirmRemoval,
            onDismiss = viewModel::dismissConfirmation
        )
    }
}

/**
 * The green card the other tabs open with: a label on the left half and the number of files centred
 * in the right half, with what to do under them.
 */
@Composable
private fun DeletedFilesHeader(state: DeletedFilesUiState, viewModel: DeletedFilesViewModel) {
    val signal = LocalGallerySyncColors.current
    val context = LocalContext.current
    val listing = state.phase == DeletedFilesPhase.LISTING

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
                        text = stringResource(R.string.deleted_files_label_top),
                        style = MaterialTheme.typography.headlineMedium
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = stringResource(R.string.deleted_files_label_bottom),
                            style = MaterialTheme.typography.headlineMedium
                        )
                        HelpButton(HelpTopic.DELETED_FILES_WINDOW)
                    }
                }
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Text(
                        text = state.files.size.toString(),
                        style = MaterialTheme.typography.displayMedium
                    )
                }
            }

            Column(
                modifier = Modifier.padding(horizontal = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (state.phase == DeletedFilesPhase.DONE) {
                    Text(
                        text = stringResource(R.string.deleted_files_done),
                        style = MaterialTheme.typography.bodyMedium
                    )
                } else {
                    Text(
                        text = stringResource(
                            R.string.deleted_files_intro,
                            formatBytes(context, state.totalBytes)
                        ),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    // Always there, so the card does not change height as files are ticked.
                    Text(
                        text = if (state.selected.isEmpty()) {
                            stringResource(R.string.deleted_files_none_selected)
                        } else {
                            stringResource(
                                R.string.deleted_files_selected,
                                state.selected.size,
                                formatBytes(context, state.selectedBytes)
                            )
                        },
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        HeroOutlinedButton(
                            onClick = viewModel::selectAll,
                            label = stringResource(R.string.retrieve_select_all),
                            modifier = Modifier.weight(1f),
                            enabled = listing
                        )
                        HeroOutlinedButton(
                            onClick = viewModel::clearSelection,
                            label = stringResource(R.string.retrieve_clear_selection),
                            modifier = Modifier.weight(1f),
                            enabled = listing && state.selected.isNotEmpty()
                        )
                    }
                }
            }
        }
    }
}

/**
 * One file. Tapping it ticks or unticks it. A ticked card is tinted with the error colour and says
 * what will happen, because ticking here means removing something from OneDrive, and a green tick
 * elsewhere in the app means the opposite of alarming.
 */
@Composable
private fun DeletedFileCard(
    file: BackupEntryEntity,
    selected: Boolean,
    enabled: Boolean,
    onToggle: () -> Unit
) {
    val context = LocalContext.current
    val scheme = MaterialTheme.colorScheme

    val container by animateColorAsState(
        if (selected) scheme.errorContainer else scheme.surface, tween(200), label = "container"
    )
    val content by animateColorAsState(
        if (selected) scheme.onErrorContainer else scheme.onSurface, tween(200), label = "content"
    )
    val border by animateColorAsState(
        if (selected) scheme.error else scheme.outline, tween(200), label = "border"
    )

    Surface(
        onClick = onToggle,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = container,
        contentColor = content,
        border = BorderStroke(if (selected) 2.dp else 1.dp, border)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = file.displayName,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = stringResource(
                        R.string.deleted_file_detail,
                        file.album,
                        formatBytes(context, file.sizeBytes)
                    ),
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    text = stringResource(
                        if (selected) R.string.deleted_file_will_remove else R.string.deleted_file_stays
                    ),
                    style = MaterialTheme.typography.bodySmall
                )
            }
            // No handler of its own: the whole card is the target, and this only shows the state.
            Checkbox(checked = selected, onCheckedChange = null)
        }
    }
}

/** The buttons under the list: remove what is ticked, keep everything, or decide another time. */
@Composable
private fun ActionBar(state: DeletedFilesUiState, viewModel: DeletedFilesViewModel) {
    val listing = state.phase == DeletedFilesPhase.LISTING

    Surface(tonalElevation = 3.dp, modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (state.phase == DeletedFilesPhase.WORKING) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    Text(
                        text = stringResource(R.string.deleted_files_working),
                        modifier = Modifier.padding(start = 12.dp),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                return@Column
            }

            Button(
                onClick = viewModel::askToRemove,
                enabled = listing && state.selected.isNotEmpty(),
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError
                )
            ) {
                Text(
                    text = stringResource(R.string.deleted_files_remove, state.selected.size),
                    maxLines = 1
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = viewModel::keepAll,
                    enabled = listing,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.deleted_files_keep_all), maxLines = 1)
                }
                TextButton(
                    onClick = viewModel::decideLater,
                    enabled = listing,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.deleted_files_decide_later), maxLines = 1)
                }
            }
        }
    }
}

/** What the window did, said separately for each outcome. */
@Composable
private fun Outcome(state: DeletedFilesUiState) {
    val outcome: DeletionOutcome? = state.outcome

    Column(
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (outcome != null && outcome.deleted > 0) {
            Text(
                text = stringResource(
                    R.string.deleted_result_removed,
                    pluralStringResource(R.plurals.file_count, outcome.deleted, outcome.deleted)
                ),
                style = MaterialTheme.typography.bodyLarge
            )
        }
        if (state.keptCount > 0) {
            Text(
                text = stringResource(
                    R.string.deleted_result_kept,
                    pluralStringResource(R.plurals.file_count, state.keptCount, state.keptCount)
                ),
                style = MaterialTheme.typography.bodyLarge
            )
        }
        // Reported, not hidden. A file that came back is the check working.
        if (outcome != null && outcome.cameBack > 0) {
            Text(
                text = stringResource(
                    R.string.deleted_result_came_back,
                    pluralStringResource(R.plurals.file_count, outcome.cameBack, outcome.cameBack)
                ),
                style = MaterialTheme.typography.bodyMedium
            )
        }
        if (outcome != null && outcome.failed > 0) {
            Text(
                text = stringResource(
                    R.string.deleted_result_failed,
                    pluralStringResource(R.plurals.file_count, outcome.failed, outcome.failed)
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}

/**
 * The confirmation, and the only place a removal can be started.
 *
 * Says the count, the size and, the part that matters, where the files end up. "Removed from
 * OneDrive" sounds final; "in the recycle bin, which you empty yourself" is what actually happens.
 */
@Composable
private fun ConfirmDialog(
    count: Int,
    bytes: Long,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val files = pluralStringResource(R.plurals.file_count, count, count)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            TitleWithHelp(
                stringResource(R.string.deleted_confirm_title, files),
                HelpTopic.DIALOG_REMOVE_FROM_ONEDRIVE
            )
        },
        text = {
            Text(
                text = stringResource(R.string.deleted_confirm_body, files, formatBytes(context, bytes)),
                style = MaterialTheme.typography.bodyMedium
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.deleted_confirm_action), maxLines = 1)
            }
        },
        // "Cancel" and not "Keep them": it decides nothing, so it must not sound like a decision. The
        // ticks stay where they are.
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.deleted_confirm_cancel), maxLines = 1)
            }
        }
    )
}
