package com.gallery.sync.ui.backup

import android.Manifest
import android.app.Activity
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Surface
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
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
import com.gallery.sync.ui.theme.LocalGallerySyncColors
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
        HeroCard(
            state = state,
            context = context,
            onSyncNow = viewModel::runBackupNow,
            onRescan = viewModel::refresh
        )

        // Bulk selection lives here rather than in the hero. Four controls inside the card wrapped
        // to three lines on the cover screen and pushed it past 40% of the display — a lot of room
        // for a card whose content is one number. These act on the list, so they sit above it, and
        // as text buttons they read as secondary to Sync now, which is what they are.
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            TextButton(onClick = { viewModel.setAllAlbums(true) }) {
                Text(stringResource(R.string.backup_select_all), maxLines = 1)
            }
            TextButton(onClick = { viewModel.setAllAlbums(false) }) {
                Text(stringResource(R.string.backup_deselect_all), maxLines = 1)
            }
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

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
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
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        onClick = onTapped
    ) {
    LabelWithAction(
        modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
        spacing = 8.dp,
        action = {
            AlbumModeDropdown(current = album.mode, onModeSelected = onModeSelected)
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
}

/**
 * The mode, as a tinted pill that opens the menu.
 *
 * Replaces a read-only `OutlinedTextField` inside an `ExposedDropdownMenuBox`. That control carries
 * a **280dp minimum width** which no amount of modifier could fully undo: on a 320dp screen it left
 * the album name almost nothing and wrapped it to one character per line, which is the layout bug
 * recorded against this screen. A pill is as wide as its word.
 *
 * It also does the job the old control could not: the four modes are meant to be told apart at a
 * glance, and identical bordered boxes reading different words are not that. The tint carries the
 * meaning and Archive is the one that looks different, because it is the one that removes files.
 */
@Composable
private fun AlbumModeDropdown(
    current: AlbumMode,
    onModeSelected: (AlbumMode) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val (container, onContainer) = current.pillColors()

    // Never full width, stacked or not. A pill that spans the card is the loudest thing in it, and
    // Off — the mode that means nothing happens — was reading louder than the album name on the
    // cover screen. The pill is as wide as its word wherever it lands; stacking only moves it below
    // the name, it does not inflate it.
    Box {
        Surface(
            shape = RoundedCornerShape(percent = 50),
            color = container,
            contentColor = onContainer,
            onClick = { expanded = true }
        ) {
            Text(
                text = current.label(),
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            AlbumMode.entries.forEach { mode ->
                DropdownMenuItem(
                    text = { Text(mode.label()) },
                    onClick = {
                        expanded = false
                        if (mode != current) onModeSelected(mode)
                    }
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

/**
 * The one number that matters, and the controls that change it.
 *
 * Everything on this screen is a detail of this card, so it is the only thing drawn on a filled
 * surface. It replaces what used to be four separate lines and five loose buttons stacked above the
 * list — the information was all there and none of it was ranked.
 *
 * The figure is [BackupUiState.uploadedCount]: files this app has confirmed in OneDrive. It is
 * deliberately not "your library", which the app does not know, and not a percentage of a quota,
 * which it also does not know — Graph's drive total is not read anywhere.
 */
@Composable
private fun HeroCard(
    state: BackupUiState,
    context: android.content.Context,
    onSyncNow: () -> Unit,
    onRescan: () -> Unit
) {
    val signal = LocalGallerySyncColors.current

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = signal.heroContainer,
        contentColor = signal.onHero
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = stringResource(R.string.backup_hero_label),
                style = MaterialTheme.typography.labelMedium
            )
            Text(
                text = state.uploadedCount.toString(),
                style = MaterialTheme.typography.displaySmall
            )

            // What a run would move now. Says nothing when nothing is chosen, because the note
            // below already says that in words.
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
                style = MaterialTheme.typography.bodySmall
            )

            // Only the two states the counts cannot convey: nothing chosen, and nothing left to do.
            val selectionNote = when {
                state.enabledItemCount == 0 -> stringResource(R.string.backup_nothing_selected)
                state.isSelectionFullyBackedUp -> stringResource(R.string.backup_all_done)
                else -> null
            }
            selectionNote?.let { Text(text = it, style = MaterialTheme.typography.bodySmall) }

            // FlowRow, not Row. Three buttons do not fit across 320dp at a large font size: the
            // third was pushed off the screen entirely and the second broke mid-word into
            // "Sele / ct all". Wrapping puts each on the line where it fits, at its natural width.
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Disabled when a run would transfer nothing. A button that can be pressed and
                // then visibly does nothing is worse than one that is plainly unavailable.
                Button(
                    onClick = onSyncNow,
                    enabled = state.canRunBackup,
                    // The disabled pair matters as much as the enabled one here. Material's
                    // defaults derive disabled colours from the SCHEME's surface, which on the
                    // hero's filled container came out dark-green-on-dark-green and read as an
                    // empty hole. Derived from the hero's own content colour instead, so it is
                    // plainly present and plainly unavailable.
                    colors = ButtonDefaults.buttonColors(
                        containerColor = signal.accent,
                        contentColor = signal.onAccent,
                        disabledContainerColor = LocalContentColor.current.copy(alpha = 0.14f),
                        disabledContentColor = LocalContentColor.current.copy(alpha = 0.55f)
                    )
                ) {
                    Text(
                        stringResource(
                            if (state.isRunning) R.string.backup_running else R.string.backup_run_now
                        ),
                        maxLines = 1
                    )
                }
                HeroOutlinedButton(onClick = onRescan, label = stringResource(R.string.backup_rescan))
            }
        }
    }
}

/**
 * An outlined button on the hero's filled surface.
 *
 * Material's own outlined button takes its border and label from the colour scheme, which is
 * correct everywhere except on top of a filled container — there it draws a scheme colour on a
 * surface the scheme knows nothing about. Both are taken from [LocalContentColor] instead, so they
 * follow whichever way the hero is painted in this theme.
 */
@Composable
private fun HeroOutlinedButton(onClick: () -> Unit, label: String) {
    OutlinedButton(
        onClick = onClick,
        colors = ButtonDefaults.outlinedButtonColors(contentColor = LocalContentColor.current),
        border = BorderStroke(1.dp, LocalContentColor.current.copy(alpha = 0.35f))
    ) {
        Text(label, maxLines = 1)
    }
}

/** The tint each mode is recognised by. Paired, so no caller has to choose a text colour. */
@Composable
private fun AlbumMode.pillColors(): Pair<Color, Color> {
    val signal = LocalGallerySyncColors.current
    return when (this) {
        AlbumMode.OFF -> signal.offContainer to signal.onOffContainer
        AlbumMode.BACKUP -> signal.backupContainer to signal.onBackupContainer
        AlbumMode.SYNC -> signal.syncContainer to signal.onSyncContainer
        AlbumMode.ARCHIVE -> signal.archiveContainer to signal.onArchiveContainer
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
