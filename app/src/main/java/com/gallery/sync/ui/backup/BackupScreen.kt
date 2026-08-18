package com.gallery.sync.ui.backup

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gallery.sync.R
import com.gallery.sync.data.local.media.MediaAccess
import com.gallery.sync.domain.backup.StopReason
import com.gallery.sync.ui.common.formatBytes
import kotlinx.coroutines.launch

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
                headline = stringResource(R.string.permission_needed_title),
                detail = stringResource(R.string.permission_needed_detail),
                onGrant = { permissionLauncher.launch(mediaPermissions()) }
            )

            MediaAccess.PARTIAL -> {
                // Never presented as working. Someone believing their library is safe while only
                // hand-picked photos are visible is the exact failure this app exists to prevent.
                PermissionPrompt(
                    headline = stringResource(R.string.permission_partial_title),
                    detail = stringResource(R.string.permission_partial_detail),
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
        Button(onClick = onGrant) { Text(stringResource(R.string.permission_grant_action)) }
    }
    HorizontalDivider()
}

@Composable
private fun AlbumList(state: BackupUiState, viewModel: BackupViewModel) {
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = stringResource(
                R.string.backup_selection_summary,
                pluralStringResource(
                    R.plurals.file_count,
                    state.enabledItemCount,
                    state.enabledItemCount
                ),
                formatBytes(context, state.enabledBytes)
            ),
            style = MaterialTheme.typography.titleSmall
        )
        // Only the two states the file count cannot already convey: nothing chosen, and nothing
        // left to do. The outstanding count used to sit here too, but the run status below now
        // reports progress file by file, and saying it twice just competes with itself.
        val selectionNote = when {
            state.enabledItemCount == 0 -> stringResource(R.string.backup_nothing_selected)
            state.isSelectionFullyBackedUp -> stringResource(R.string.backup_all_done)
            else -> null
        }
        selectionNote?.let {
            Text(text = it, style = MaterialTheme.typography.bodySmall)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { viewModel.setAllAlbums(false) }) {
                Text(stringResource(R.string.backup_deselect_all))
            }
            OutlinedButton(onClick = { viewModel.setAllAlbums(true) }) {
                Text(stringResource(R.string.backup_select_all))
            }
            OutlinedButton(onClick = viewModel::refresh) {
                Text(stringResource(R.string.backup_rescan))
            }
        }

        // Disabled when a run would transfer nothing. A button that can be pressed and then
        // visibly does nothing is worse than one that is plainly unavailable.
        Button(
            onClick = viewModel::runBackupNow,
            enabled = state.canRunBackup
        ) {
            Text(
                stringResource(
                    if (state.isRunning) R.string.backup_running else R.string.backup_run_now
                )
            )
        }

        state.status?.let {
            Text(it.readable(), style = MaterialTheme.typography.bodyMedium)
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
                        text = stringResource(
                            R.string.backup_album_summary,
                            pluralStringResource(
                                R.plurals.file_count,
                                album.itemCount,
                                album.itemCount
                            ),
                            formatBytes(context, album.totalBytes)
                        ),
                        style = MaterialTheme.typography.bodySmall
                    )
                    // Whether the album is safe, said separately from whether it is watched.
                    Text(
                        text = when (album.status) {
                            AlbumStatus.COMPLETE -> stringResource(R.string.album_status_complete)
                            AlbumStatus.PARTIAL ->
                                stringResource(R.string.album_status_partial, album.outstanding)
                            AlbumStatus.NOT_BACKED_UP -> stringResource(R.string.album_status_none)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = when (album.status) {
                            AlbumStatus.COMPLETE -> MaterialTheme.colorScheme.primary
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
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

/** Turns the typed run status into words. */
@Composable
private fun BackupStatus.readable(): String = when (this) {
    BackupStatus.Scanning -> stringResource(R.string.backup_status_scanning)
    is BackupStatus.Uploading -> stringResource(
        R.string.backup_status_uploading_progress,
        // One-based: the file being sent is the (completed + 1)th of the run.
        (completed + 1).coerceAtMost(total),
        total,
        currentFile,
        percentOfCurrent
    )
    BackupStatus.NoPermission -> stringResource(R.string.backup_status_no_permission)

    is BackupStatus.Finished -> {
        val separator = stringResource(R.string.backup_status_separator)
        val parts = buildList {
            add(stringResource(R.string.backup_status_uploaded, uploaded))
            if (skipped > 0) add(stringResource(R.string.backup_status_skipped, skipped))
            if (pruned > 0) add(stringResource(R.string.backup_status_pruned, pruned))
            if (failed > 0) add(stringResource(R.string.backup_status_failed, failed))
            if (remaining > 0) add(stringResource(R.string.backup_status_remaining, remaining))
            stoppedBecause?.let { add(it.readable()) }
        }
        parts.joinToString(separator)
    }
}

@Composable
private fun StopReason.readable(): String = when (this) {
    StopReason.NO_TOKEN -> stringResource(R.string.backup_stopped_no_token)
    StopReason.UNAUTHORIZED -> stringResource(R.string.backup_stopped_unauthorized)
    StopReason.DRIVE_FULL -> stringResource(R.string.backup_stopped_drive_full)
    StopReason.NETWORK -> stringResource(R.string.backup_stopped_network)
    StopReason.NO_MEDIA_ACCESS -> stringResource(R.string.backup_stopped_no_media_access)
}

/** The permissions to ask for on this Android version. */
private fun mediaPermissions(): Array<String> =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO)
    } else {
        arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }
