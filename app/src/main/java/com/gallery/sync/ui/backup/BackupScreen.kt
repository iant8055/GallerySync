package com.gallery.sync.ui.backup

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gallery.sync.data.local.media.MediaAccess

/**
 * Backup control: which albums, and a manual run.
 *
 * Running is deliberately manual while the feature is being proven. Nothing schedules itself, so a
 * build can never start uploading someone's library on its own.
 */
@Composable
fun BackupScreen(
    modifier: Modifier = Modifier,
    viewModel: BackupViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { viewModel.refresh() }

    Column(modifier = modifier.fillMaxSize()) {

        when (state.access) {
            MediaAccess.NONE -> PermissionPrompt(
                headline = "Gallery Sync needs access to your photos",
                detail = "It reads the albums you choose so it can back them up to OneDrive. " +
                    "It never deletes anything.",
                onGrant = { permissionLauncher.launch(mediaPermissions()) }
            )

            MediaAccess.PARTIAL -> {
                // Never presented as working. Someone believing their library is safe when only a
                // handful of selected photos are visible is the exact failure this app exists to
                // prevent.
                PermissionPrompt(
                    headline = "Only some photos are shared",
                    detail = "Gallery Sync can currently see just the photos you picked, so a " +
                        "backup would be incomplete. Grant access to all photos to back up " +
                        "whole albums.",
                    onGrant = { permissionLauncher.launch(mediaPermissions()) }
                )
                AlbumList(state = state, viewModel = viewModel)
            }

            MediaAccess.FULL -> AlbumList(state = state, viewModel = viewModel)
        }
    }
}

@Composable
private fun PermissionPrompt(headline: String, detail: String, onGrant: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(headline, style = MaterialTheme.typography.titleMedium)
        Text(detail, style = MaterialTheme.typography.bodyMedium)
        Button(onClick = onGrant) { Text("Grant access") }
    }
    HorizontalDivider()
}

@Composable
private fun AlbumList(state: BackupUiState, viewModel: BackupViewModel) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "${state.enabledItemCount} files selected · ${formatBytes(state.enabledBytes)}",
            style = MaterialTheme.typography.titleSmall
        )
        Text(
            text = "${state.uploadedCount} backed up · ${state.pendingCount} outstanding",
            style = MaterialTheme.typography.bodySmall
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { viewModel.setAllAlbums(false) }) { Text("Deselect all") }
            OutlinedButton(onClick = { viewModel.setAllAlbums(true) }) { Text("Select all") }
            OutlinedButton(onClick = viewModel::refresh) { Text("Rescan") }
        }

        Button(
            onClick = viewModel::runBackupNow,
            enabled = !state.isRunning && state.enabledItemCount > 0
        ) {
            Text(if (state.isRunning) "Backing up…" else "Back up now")
        }

        state.status?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium)
        }
    }

    HorizontalDivider()

    if (state.isScanning) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) { CircularProgressIndicator() }
        return
    }

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(state.albums, key = { it.name }) { album ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = album.name,
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${album.itemCount} files · ${formatBytes(album.totalBytes)}",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Switch(
                    checked = album.isEnabled,
                    onCheckedChange = { viewModel.setAlbumEnabled(album.name, it) }
                )
            }
            HorizontalDivider()
        }
    }
}

/** The permissions to ask for on this Android version. */
private fun mediaPermissions(): Array<String> =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO)
    } else {
        arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }

internal fun formatBytes(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "${bytes / 1024} KB"
    bytes < 1024L * 1024 * 1024 -> "${bytes / (1024 * 1024)} MB"
    else -> String.format("%.1f GB", bytes / (1024.0 * 1024 * 1024))
}
