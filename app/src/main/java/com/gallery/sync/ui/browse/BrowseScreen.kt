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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gallery.sync.BuildConfig
import com.gallery.sync.domain.model.RemoteError
import com.gallery.sync.domain.model.RemoteMediaNode

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
            BrowseUiState.Loading -> Box_Centered { CircularProgressIndicator() }

            is BrowseUiState.Content -> {
                Header(location = current.location, onSignOut = onSignOut)

                if (current.nodes.isEmpty()) {
                    Box_Centered {
                        Text(
                            text = "This folder is empty.",
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
                Header(location = "OneDrive", onSignOut = onSignOut)
                Box_Centered {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = current.error.readable(),
                            style = MaterialTheme.typography.bodyLarge,
                            textAlign = TextAlign.Center
                        )
                        OutlinedButton(onClick = viewModel::retry) { Text("Retry") }
                    }
                }
            }
        }
    }
}

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
        OutlinedButton(onClick = onSignOut) { Text("Sign out") }
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
            Text(
                text = node.subtitle(),
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun Box_Centered(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) { content() }
}

private fun RemoteMediaNode.subtitle(): String = when (this) {
    is RemoteMediaNode.Folder ->
        if (childCount > 0) "$childCount items" else "Folder"

    is RemoteMediaNode.File ->
        "${formatBytes(sizeBytes)} · $mimeType"
}

/** Compact human-readable size. Binary units, matching what file managers show. */
internal fun formatBytes(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "${bytes / 1024} KB"
    bytes < 1024L * 1024 * 1024 -> "${bytes / (1024 * 1024)} MB"
    else -> String.format("%.1f GB", bytes / (1024.0 * 1024 * 1024))
}

private fun RemoteError.readable(): String = when (this) {
    RemoteError.NoToken -> "Not signed in."
    RemoteError.Unauthorized -> "OneDrive rejected the sign-in. Try signing out and back in."
    RemoteError.Network -> "Can't reach OneDrive. Check your connection."
    RemoteError.InsufficientStorage -> "Your OneDrive is full. Free up space to continue."
    is RemoteError.Http -> "OneDrive returned an error ($code)."
    is RemoteError.Unknown -> "Something went wrong: ${cause.javaClass.simpleName}"
}
