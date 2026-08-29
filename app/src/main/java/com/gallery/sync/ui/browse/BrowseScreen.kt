package com.gallery.sync.ui.browse

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gallery.sync.R
import com.gallery.sync.domain.model.RemoteError
import com.gallery.sync.domain.model.RemoteMediaNode
import com.gallery.sync.ui.common.formatBytes

/**
 * Lists the signed-in account's OneDrive tree.
 *
 * Deliberately plain: this is the verification surface for the data layer, not the media browser
 * that ships. Tapping a folder descends into it; system back ascends.
 */
@Composable
fun BrowseScreen(
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: BrowseViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    val canGoBack = (state as? BrowseUiState.Content)?.canGoBack
        ?: (state as? BrowseUiState.Error)?.canGoBack
        ?: false
    BackHandler(enabled = canGoBack) { viewModel.back() }

    Column(modifier = modifier.fillMaxSize()) {
        when (val current = state) {
            BrowseUiState.Loading -> Centered { CircularProgressIndicator() }

            is BrowseUiState.Content -> {
                Header(
                    trail = current.trail,
                    onNavigateTo = viewModel::navigateTo,
                    onSignOut = onSignOut
                )

                SortBar(
                    field = current.sortField,
                    ascending = current.sortAscending,
                    onSelect = viewModel::setSort
                )

                if (current.nodes.isEmpty()) {
                    Centered {
                        Text(
                            text = stringResource(R.string.browse_empty_folder),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(current.nodes, key = { it.id }) { node ->
                            NodeRow(node = node, onOpen = viewModel::open)
                            HorizontalDivider()
                        }
                    }
                }
            }

            is BrowseUiState.Error -> {
                Header(
                    trail = emptyList(),
                    onNavigateTo = viewModel::navigateTo,
                    onSignOut = onSignOut
                )
                Centered {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = current.error.readable(),
                            style = MaterialTheme.typography.bodyLarge,
                            textAlign = TextAlign.Center
                        )
                        OutlinedButton(onClick = viewModel::retry) {
                            Text(stringResource(R.string.retry_action))
                        }
                    }
                }
            }
        }
    }
}

/**
 * Breadcrumbs, with every ancestor tappable.
 *
 * Scrolls horizontally rather than ellipsising: a truncated path hides exactly the levels someone
 * would want to jump back to, and the deeper the folder the more useful those levels become. The
 * scroll is anchored so the current folder stays visible as the path grows.
 */
@Composable
private fun Header(
    trail: List<String>,
    onNavigateTo: (Int) -> Unit,
    onSignOut: () -> Unit
) {
    val scrollState = rememberScrollState()

    // Keep the deepest crumb in view when descending, which is where attention is.
    LaunchedEffect(trail.size) { scrollState.animateScrollTo(scrollState.maxValue) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .horizontalScroll(scrollState),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Crumb(
                label = stringResource(R.string.browse_root),
                isCurrent = trail.isEmpty(),
                onClick = { onNavigateTo(0) }
            )

            trail.forEachIndexed { index, name ->
                Text(
                    text = " / ",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Crumb(
                    label = name,
                    isCurrent = index == trail.lastIndex,
                    // depth is how many folders to keep, so this crumb's own level is index + 1.
                    onClick = { onNavigateTo(index + 1) }
                )
            }
        }

        OutlinedButton(onClick = onSignOut) {
            Text(stringResource(R.string.sign_out_action))
        }
    }
    HorizontalDivider()
}

/**
 * Sort controls, for verifying a backup rather than browsing.
 *
 * Tapping the active field flips direction, as a column header does everywhere else.
 */
@Composable
private fun SortBar(
    field: SortField,
    ascending: Boolean,
    onSelect: (SortField) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SortField.entries.forEach { option ->
            val selected = option == field

            // Resolved out here: stringResource needs a composable context and cannot be called
            // from inside a buildString lambda.
            val label = when (option) {
                SortField.NAME -> stringResource(R.string.sort_name)
                SortField.DATE -> stringResource(R.string.sort_date)
                SortField.SIZE -> stringResource(R.string.sort_size)
            }
            val arrow = stringResource(
                if (ascending) R.string.sort_ascending else R.string.sort_descending
            )

            TextButton(onClick = { onSelect(option) }) {
                Text(
                    text = if (selected) "$label $arrow" else label,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (selected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
        }
    }
    HorizontalDivider()
}

@Composable
private fun Crumb(label: String, isCurrent: Boolean, onClick: () -> Unit) {
    Text(
        text = label,
        style = MaterialTheme.typography.titleMedium,
        maxLines = 1,
        // The current folder is where you already are, so it is not offered as a destination.
        color = if (isCurrent) {
            MaterialTheme.colorScheme.onSurface
        } else {
            MaterialTheme.colorScheme.primary
        },
        modifier = Modifier
            .then(if (isCurrent) Modifier else Modifier.clickable(onClick = onClick))
            .padding(vertical = 8.dp, horizontal = 2.dp)
    )
}

@Composable
private fun NodeRow(node: RemoteMediaNode, onOpen: (RemoteMediaNode.Folder) -> Unit) {
    val clickable = node is RemoteMediaNode.Folder

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (clickable) {
                    Modifier.clickable { onOpen(node as RemoteMediaNode.Folder) }
                } else {
                    Modifier
                }
            )
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            text = if (node is RemoteMediaNode.Folder) "📁" else "🖼",
            modifier = Modifier.size(24.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = node.name,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(text = node.subtitle(), style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun Centered(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) { content() }
}

@Composable
private fun RemoteMediaNode.subtitle(): String = when (this) {
    is RemoteMediaNode.Folder ->
        if (childCount > 0) {
            pluralStringResource(R.plurals.item_count, childCount, childCount)
        } else {
            stringResource(R.string.browse_folder)
        }

    is RemoteMediaNode.File ->
        // A file whose size OneDrive did not report shows its type alone rather than "0 B",
        // which would be a statement about the file rather than about the listing.
        sizeBytes?.let { "${formatBytes(LocalContext.current, it)} · $mimeType" } ?: mimeType
}

@Composable
private fun RemoteError.readable(): String = when (this) {
    RemoteError.NoToken -> stringResource(R.string.error_not_signed_in)
    RemoteError.Unauthorized -> stringResource(R.string.error_unauthorized)
    RemoteError.Network -> stringResource(R.string.error_network)
    RemoteError.LocalFileMissing -> stringResource(R.string.error_local_file_missing)
    RemoteError.InsufficientStorage -> stringResource(R.string.error_drive_full)
    is RemoteError.Http -> stringResource(R.string.error_http, code)
    is RemoteError.Unknown -> stringResource(R.string.error_unknown, cause.javaClass.simpleName)
}
