package com.gallery.sync.ui.backup

import android.Manifest
import android.app.Activity
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gallery.sync.R
import com.gallery.sync.data.local.entity.AlbumMode
import com.gallery.sync.data.local.entity.BackupEntryEntity
import com.gallery.sync.data.local.media.MediaAccess
import com.gallery.sync.domain.backup.StopReason
import com.gallery.sync.ui.common.LabelWithAction
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
    val scope = rememberCoroutineScope()

    var detailAlbum by remember { mutableStateOf<AlbumRow?>(null) }
    var detailEntries by remember { mutableStateOf<List<BackupEntryEntity>>(emptyList()) }

    detailAlbum?.let { album ->
        AlbumDetailScreen(
            albumName = album.name,
            mode = album.mode,
            entries = detailEntries,
            onBack = { detailAlbum = null },
            modifier = modifier
        )
        return
    }

    val proxyLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) viewModel.onProxyConsentGranted()
    }

    var hasOfferedAutoProxy by remember { mutableStateOf(false) }
    LaunchedEffect(state.isAutoOptimiseEnabled, state.proxyCandidateCount, state.canProxy) {
        if (state.isAutoOptimiseEnabled && state.canProxy &&
            state.proxyCandidateCount > 0 && !hasOfferedAutoProxy
        ) {
            hasOfferedAutoProxy = true
            viewModel.buildProxyWriteRequest()?.let {
                proxyLauncher.launch(IntentSenderRequest.Builder(it).build())
            }
        }
    }

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
                PermissionPrompt(
                    headline = stringResource(R.string.permission_partial_title),
                    detail = stringResource(R.string.permission_partial_detail),
                    onGrant = { permissionLauncher.launch(mediaPermissions()) }
                )
                AlbumList(
                    state = state,
                    viewModel = viewModel,
                    onAlbumTapped = { album ->
                        scope.launch {
                            detailEntries = viewModel.albumEntries(album.name)
                            detailAlbum = album
                        }
                    }
                )
            }

            MediaAccess.FULL -> AlbumList(
                state = state,
                viewModel = viewModel,
                onAlbumTapped = { album ->
                    scope.launch {
                        detailEntries = viewModel.albumEntries(album.name)
                        detailAlbum = album
                    }
                }
            )
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AlbumList(
    state: BackupUiState,
    viewModel: BackupViewModel,
    onAlbumTapped: (AlbumRow) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val moveLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { viewModel.onMoveToBackupFinished() }

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
        // FlowRow, not Row. Three buttons do not fit across 320dp at a large font size: the third
        // was pushed off the screen entirely and the second broke mid-word into "Sele / ct all".
        // Wrapping puts each button on the line where it fits, at its natural width.
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(onClick = { viewModel.setAllAlbums(false) }) {
                Text(stringResource(R.string.backup_deselect_all), maxLines = 1)
            }
            OutlinedButton(onClick = { viewModel.setAllAlbums(true) }) {
                Text(stringResource(R.string.backup_select_all), maxLines = 1)
            }
            OutlinedButton(onClick = viewModel::refresh) {
                Text(stringResource(R.string.backup_rescan), maxLines = 1)
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

        // The Archive summons. Not a second consent — setting the mode was the consent, and
        // CLAUDE.md forbids mirroring Android's trash dialog with an app-level one. It exists
        // because createTrashRequest can only launch from an Activity, so the user must be brought
        // back; and because it states the one thing Android's dialog cannot, which is that the cloud
        // copy is verified.
        if (state.archiveAlbumsReady.isNotEmpty() && state.canRemoveLocalCopies) {
            ArchiveReadyPrompt(
                albums = state.archiveAlbumsReady,
                count = state.redundantCount,
                bytes = state.redundantBytes,
                onRemove = {
                    scope.launch {
                        viewModel.buildMoveToBackupRequest()?.let {
                            moveLauncher.launch(IntentSenderRequest.Builder(it).build())
                        }
                    }
                }
            )
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

    var archiveConfirmAlbum by remember { mutableStateOf<String?>(null) }

    archiveConfirmAlbum?.let { albumName ->
        ArchiveConfirmDialog(
            albumName = albumName,
            onConfirm = {
                viewModel.setAlbumMode(albumName, AlbumMode.ARCHIVE)
                archiveConfirmAlbum = null
            },
            onDismiss = { archiveConfirmAlbum = null }
        )
    }

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(state.albums, key = { it.name }) { album ->
            AlbumModeRow(
                album = album,
                context = context,
                onTapped = { onAlbumTapped(album) },
                onModeSelected = { mode ->
                    if (mode == AlbumMode.ARCHIVE) {
                        archiveConfirmAlbum = album.name
                    } else {
                        viewModel.setAlbumMode(album.name, mode)
                    }
                }
            )
            HorizontalDivider()
        }
    }
}

@Composable
private fun AlbumModeRow(
    album: AlbumRow,
    context: android.content.Context,
    onTapped: () -> Unit,
    onModeSelected: (AlbumMode) -> Unit
) {
    LabelWithAction(
        modifier = Modifier
            .clickable(onClick = onTapped)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        spacing = 8.dp,
        action = { stacked ->
            AlbumModeDropdown(
                current = album.mode,
                onModeSelected = onModeSelected,
                stacked = stacked
            )
        }
    ) {
        Column {
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
            Text(
                text = album.statusBreakdown(),
                style = MaterialTheme.typography.bodySmall,
                color = if (album.outstanding == 0 && album.backedUpCount > 0)
                    MaterialTheme.colorScheme.primary
                else
                    MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AlbumModeDropdown(
    current: AlbumMode,
    onModeSelected: (AlbumMode) -> Unit,
    stacked: Boolean = false
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it }
    ) {
        OutlinedTextField(
            value = current.label(),
            onValueChange = {},
            readOnly = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            // `widthIn(min = 0.dp)` is the load-bearing part. OutlinedTextField defaults to a
            // 280dp minimum, which on a 320dp screen leaves the album name nothing and wraps it one
            // character per line. Stacked it may have the full width; beside the name it may not.
            modifier = Modifier
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                .then(
                    if (stacked) Modifier.fillMaxWidth()
                    else Modifier.widthIn(min = 0.dp, max = 180.dp)
                ),
            textStyle = MaterialTheme.typography.bodySmall,
            singleLine = true
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            AlbumMode.entries.forEach { mode ->
                DropdownMenuItem(
                    text = { Text(mode.label()) },
                    onClick = {
                        expanded = false
                        if (mode != current) onModeSelected(mode)
                    },
                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                )
            }
        }
    }
}

@Composable
private fun AlbumMode.label(): String = when (this) {
    AlbumMode.OFF -> stringResource(R.string.mode_off)
    AlbumMode.BACKUP -> stringResource(R.string.mode_backup)
    AlbumMode.SYNC -> stringResource(R.string.mode_sync)
    AlbumMode.ARCHIVE -> stringResource(R.string.mode_archive)
}

@Composable
private fun ArchiveConfirmDialog(
    albumName: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.archive_confirm_title)) },
        text = { Text(stringResource(R.string.archive_confirm_body, albumName)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.archive_confirm_accept))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.archive_confirm_cancel))
            }
        }
    )
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
            if (deferred > 0) add(stringResource(R.string.backup_status_deferred, deferred))
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

@Composable
private fun AlbumRow.statusBreakdown(): String {
    if (backedUpCount == 0) return stringResource(R.string.album_status_none)

    val separator = " · "
    return buildString {
        val backupOnly = backedUpOnly
        if (backupOnly > 0) append(stringResource(R.string.album_status_backed_up, backupOnly))
        if (proxiedCount > 0) {
            if (isNotEmpty()) append(separator)
            append(stringResource(R.string.album_status_optimized, proxiedCount))
        }
        val pending = outstanding
        if (pending > 0) {
            if (isNotEmpty()) append(separator)
            append(stringResource(R.string.album_status_pending, pending))
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

/**
 * Says that files are verified in OneDrive and offers to take them off the phone.
 *
 * One prompt, deliberately. Android will ask its own question immediately after, and asking the same
 * thing twice teaches people to tap through both — which is how a confirmation stops being one.
 * What this adds is the fact Android's dialog has no way to state: that the cloud copy has been
 * checked and matches.
 */
@Composable
private fun ArchiveReadyPrompt(
    albums: List<String>,
    count: Int,
    bytes: Long,
    onRemove: () -> Unit
) {
    val context = LocalContext.current

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = stringResource(R.string.archive_ready_title),
            style = MaterialTheme.typography.titleSmall
        )
        Text(
            text = stringResource(
                R.string.archive_ready_body,
                pluralStringResource(R.plurals.file_count, count, count),
                albums.joinToString(", "),
                formatBytes(context, bytes)
            ),
            style = MaterialTheme.typography.bodySmall
        )
        OutlinedButton(onClick = onRemove) {
            Text(stringResource(R.string.archive_ready_action), maxLines = 1)
        }
    }
}
