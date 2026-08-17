package com.gallery.sync.ui.browse

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.Composable
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
                    location = locationLabel(current.trail),
                    onSignOut = onSignOut
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
                Header(location = stringResource(R.string.browse_root), onSignOut = onSignOut)
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

/** Joins the localised root with the folders entered so far. */
@Composable
private fun locationLabel(trail: List<String>): String =
    (listOf(stringResource(R.string.browse_root)) + trail).joinToString(" / ")

@Composable
private fun Header(location: String, onSignOut: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = location,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false)
        )
        OutlinedButton(onClick = onSignOut) {
            Text(stringResource(R.string.sign_out_action))
        }
    }
    HorizontalDivider()
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
        "${formatBytes(LocalContext.current, sizeBytes)} · $mimeType"
}

@Composable
private fun RemoteError.readable(): String = when (this) {
    RemoteError.NoToken -> stringResource(R.string.error_not_signed_in)
    RemoteError.Unauthorized -> stringResource(R.string.error_unauthorized)
    RemoteError.Network -> stringResource(R.string.error_network)
    RemoteError.InsufficientStorage -> stringResource(R.string.error_drive_full)
    is RemoteError.Http -> stringResource(R.string.error_http, code)
    is RemoteError.Unknown -> stringResource(R.string.error_unknown, cause.javaClass.simpleName)
}
