package com.gallery.sync.ui.retrieve

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gallery.sync.R
import com.gallery.sync.domain.backup.CloudDeletionGrace
import com.gallery.sync.domain.backup.CloudDeletionPolicy
import com.gallery.sync.ui.common.LabelWithAction
import com.gallery.sync.ui.common.SingleChoiceControl
import com.gallery.sync.ui.common.formatBytes

/**
 * Deletion sync: the setting, the review list, and the confirmation.
 *
 * Sits beside the retrieval list because they are two halves of the same question — what happened to
 * the files that are no longer on this phone. One offers them back; this one offers to let them go.
 *
 * **Nothing here removes anything without a person reading names and saying yes.** The grace period,
 * the policy check and the pre-delete re-scan all narrow what may be offered; none of them is a
 * substitute for the confirmation, and the confirmation is deliberately a separate tap from the
 * button that opens it.
 */
@Composable
fun DeletionSection(
    modifier: Modifier = Modifier,
    viewModel: DeletionViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(Unit) { viewModel.refresh() }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = stringResource(R.string.deletion_title),
            style = MaterialTheme.typography.bodyLarge
        )

        Column(Modifier.selectableGroup()) {
            PolicyRow(
                label = stringResource(R.string.deletion_leave),
                selected = state.policy == CloudDeletionPolicy.LEAVE,
                onSelect = { viewModel.setPolicy(CloudDeletionPolicy.LEAVE) }
            )
            PolicyRow(
                label = stringResource(R.string.deletion_ask),
                selected = state.policy == CloudDeletionPolicy.ASK,
                onSelect = { viewModel.setPolicy(CloudDeletionPolicy.ASK) }
            )
        }

        // Only worth showing when it governs something. Under LEAVE nothing is ever offered, so a
        // waiting period is a setting with no effect.
        if (state.policy == CloudDeletionPolicy.ASK) {
            Text(
                text = stringResource(R.string.deletion_grace_label),
                style = MaterialTheme.typography.titleSmall
            )
            Text(
                text = stringResource(R.string.deletion_grace_detail),
                style = MaterialTheme.typography.bodySmall
            )
            SingleChoiceControl(
                options = CloudDeletionGrace.SELECTABLE_DAYS,
                selected = state.graceDays,
                onSelected = viewModel::setGraceDays,
                label = { days -> pluralStringResource(R.plurals.deletion_grace_days, days, days) }
            )

            Text(
                text = stringResource(R.string.deletion_candidates_title),
                style = MaterialTheme.typography.titleSmall
            )

            if (state.candidates.isEmpty()) {
                Text(
                    text = stringResource(R.string.deletion_candidates_none),
                    style = MaterialTheme.typography.bodySmall
                )
            } else {
                Text(
                    text = stringResource(
                        R.string.deletion_candidates_summary,
                        pluralStringResource(
                            R.plurals.file_count,
                            state.candidates.size,
                            state.candidates.size
                        ),
                        formatBytes(context, state.totalBytes)
                    ),
                    style = MaterialTheme.typography.bodyMedium
                )
                // The names, because "412 files" is not something anyone can consent to. Capped
                // rather than scrolled: this section is a review, not a file manager.
                state.candidates.take(NAMES_SHOWN).forEach { entry ->
                    Text(
                        text = "${entry.displayName} · ${entry.album}",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                OutlinedButton(
                    onClick = viewModel::askToConfirm,
                    enabled = !state.working
                ) {
                    Text(
                        text = if (state.working) {
                            stringResource(R.string.deletion_working)
                        } else {
                            stringResource(R.string.deletion_review)
                        },
                        maxLines = 1
                    )
                }
            }
        }

        state.lastOutcome?.let { outcome -> OutcomeLines(outcome) }
    }

    if (state.confirming) {
        ConfirmDialog(
            count = state.candidates.size,
            bytes = state.totalBytes,
            onConfirm = viewModel::confirmDeletion,
            onDismiss = viewModel::dismissConfirmation
        )
    }
}

/**
 * The confirmation.
 *
 * Says the count, the size, and — the part that matters — where the files end up. "Removed from
 * OneDrive" sounds final; "in the recycle bin, which you empty yourself" is what actually happens,
 * and it is the difference between a decision someone can make and one they have to guess at.
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
        title = { Text(stringResource(R.string.deletion_confirm_title, files)) },
        text = {
            Text(
                text = stringResource(
                    R.string.deletion_confirm_body,
                    files,
                    formatBytes(context, bytes)
                ),
                style = MaterialTheme.typography.bodyMedium
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.deletion_confirm_action), maxLines = 1)
            }
        },
        // Named rather than "Cancel": the safe choice should say what it does.
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.deletion_confirm_cancel), maxLines = 1)
            }
        }
    )
}

/** What the last pass did, with each outcome said separately. */
@Composable
private fun OutcomeLines(outcome: com.gallery.sync.domain.backup.DeletionOutcome) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (outcome.deleted > 0) {
            Text(
                text = stringResource(
                    R.string.deletion_result,
                    pluralStringResource(R.plurals.file_count, outcome.deleted, outcome.deleted)
                ),
                style = MaterialTheme.typography.bodyMedium
            )
        }
        // Reported, not hidden. A file that came back is the guard working, and seeing it is how
        // someone learns the app checks rather than assumes.
        if (outcome.cameBack > 0) {
            Text(
                text = stringResource(
                    R.string.deletion_result_came_back,
                    pluralStringResource(R.plurals.file_count, outcome.cameBack, outcome.cameBack)
                ),
                style = MaterialTheme.typography.bodySmall
            )
        }
        if (outcome.failed > 0) {
            Text(
                text = stringResource(
                    R.string.deletion_result_failed,
                    pluralStringResource(R.plurals.file_count, outcome.failed, outcome.failed)
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}

@Composable
private fun PolicyRow(
    label: String,
    selected: Boolean,
    onSelect: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onSelect, role = Role.RadioButton)
            .padding(vertical = 8.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}

/** Enough names to make the decision concrete without turning the screen into a list. */
private const val NAMES_SHOWN = 8
