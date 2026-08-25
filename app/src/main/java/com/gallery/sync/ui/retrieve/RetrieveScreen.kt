package com.gallery.sync.ui.retrieve

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gallery.sync.R
import com.gallery.sync.data.local.entity.BackupEntryEntity
import com.gallery.sync.ui.common.LabelWithAction
import com.gallery.sync.ui.common.formatBytes

/**
 * What is in OneDrive but not on this phone, and a button to bring it back.
 *
 * **Deliberately not a photo browser.** No thumbnails, no grid, no search — the design principle
 * rules all of that out, and the phone's own gallery already does it better for anything that *is*
 * on the device. This screen exists for the files the gallery cannot show at all, because they are
 * not here.
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
            if (state.items.isEmpty()) {
                Text(
                    text = stringResource(R.string.retrieve_empty),
                    style = MaterialTheme.typography.bodyMedium
                )
            } else {
                Text(
                    text = stringResource(R.string.retrieve_explain),
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    text = stringResource(R.string.retrieve_where),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        HorizontalDivider()

        LazyColumn(modifier = Modifier.fillMaxWidth()) {
            items(state.items, key = { it.id }) { entry ->
                RetrieveRow(
                    entry = entry,
                    status = state.statuses[entry.id],
                    onRetrieve = { viewModel.retrieve(entry) }
                )
                HorizontalDivider()
            }
        }
    }
}

/** One missing file: what it was, how big, and a button. */
@Composable
private fun RetrieveRow(
    entry: BackupEntryEntity,
    status: RetrieveStatus?,
    onRetrieve: () -> Unit
) {
    val context = LocalContext.current

    LabelWithAction(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
        action = {
            OutlinedButton(
                onClick = onRetrieve,
                // Disabled only while this row is working. A finished or failed row can be tried
                // again — restores are repeatable, and the cloud copy is untouched either way.
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
                text = entry.displayName,
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = stringResource(
                    R.string.retrieve_detail,
                    entry.album,
                    formatBytes(context, entry.sizeBytes)
                ),
                style = MaterialTheme.typography.bodySmall
            )
            status?.let { StatusLine(it, entry.sizeBytes) }
        }
    }
}

@Composable
private fun StatusLine(status: RetrieveStatus, total: Long) {
    val context = LocalContext.current

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
