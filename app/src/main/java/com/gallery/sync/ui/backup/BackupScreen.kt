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
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
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
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
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
import com.gallery.sync.ui.common.SignalIcons
import com.gallery.sync.ui.common.HeroCard
import com.gallery.sync.ui.common.HeroOutlinedButton
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

    // Hoisted to the screen, because the hero sets it and the list below obeys it.
    var modeFilter by rememberSaveable { mutableStateOf<AlbumMode?>(null) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // The shared card, same as Restore and Archive. The figure counts *albums*, because this is
        // the tab where modes are chosen — see backup_hero_label for why it stopped counting files.
        HeroCard(
            label = stringResource(R.string.backup_hero_label),
            figure = "",
            figureContent = {
                ModeFilterGrid(
                    selected = modeFilter,
                    onSelect = { tapped ->
                        // Tapping the active mode again clears it, so every button is its own way
                        // out; All Albums passes null and clears it outright.
                        modeFilter = if (tapped != null && modeFilter == tapped) null else tapped
                    }
                )
            },
            detail = { HeroDetail(state = state, context = context, modeFilter = modeFilter) },
            actions = {
                HeroActions(
                    state = state,
                    onSyncNow = {
                        if (state.isRunning) viewModel.stopBackup() else viewModel.runBackupNow()
                    },
                    onRescan = viewModel::refresh
                )
            }
        )

        // No Select all / Deselect all. Removed 25 Aug 2026 (Ian): a bulk grant across every
        // album is the shape of thing CLAUDE.md's opt-in gate exists to prevent — "consent has to
        // be something granted, not something left un-revoked" — and on a real device most of the
        // ninety albums are app caches and thumbnails, so one tap would start uploading them.
        //
        // Bulk selection is not gone, it belongs to the wizard: TASK-014's Gate 2 offers it once,
        // after the scan, with the count and the consequence stated. This was the same action with
        // the explanation removed and left on the main screen for good.

        state.status?.let {
            Text(it.readable(), style = MaterialTheme.typography.bodyMedium)

            // Under the line that names the file and the percentage, the same order as Restore.
            // The text says which file and how far in; the bar is that number, drawn.
            if (it is BackupStatus.Uploading) {
                LinearProgressIndicator(
                    progress = { it.percentOfCurrent / 100f },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        // The Archive summons used to sit here. Removed 26 Aug 2026 (Ian): removal does not belong
        // on the Albums tab at all, and TASK-016 gives it a screen of its own where the user sees
        // the files before authorising anything.
        //
        // What must survive the move, because it is not decoration: the prompt is a *summons and
        // not a consent* — setting the album mode was the consent, and CLAUDE.md forbids mirroring
        // Android's trash dialog with an app-level one. It exists because createTrashRequest only
        // launches from an Activity, and because it states the one thing Android's dialog cannot,
        // which is that the cloud copy has been re-checked and matches.

        // The held-back reporting moved with it. "OneDrive no longer has this" and "could not
        // check" are answers to a question this tab no longer asks, and TASK-016 puts them where the
        // decision is now made: beside the file they are about, as a red X, in the Archive list.
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

    // Selecting Archive from the list never applies it — it raises the confirmation, which is where
    // the consent is given. Every other mode applies directly.
    val onModeSelected: (AlbumRow, AlbumMode) -> Unit = { album, mode ->
        if (mode == AlbumMode.ARCHIVE) {
            archiveConfirmAlbum = album.name
        } else {
            viewModel.setAlbumMode(album.name, mode)
        }
    }

    // Filtering by mode only. A "Filter albums" text field sat here for about an hour on
    // 27 Aug 2026 before Ian removed it: the four mode buttons above already answer the question
    // this screen is for, and a search box is the first thing that makes an app feel like it has
    // more in it than it does. CLAUDE.md's "no search" comes out intact rather than argued around.
    val visibleAlbums = state.albums.filter { modeFilter == null || it.mode == modeFilter }

    Column(modifier = Modifier.fillMaxSize()) {
        if (visibleAlbums.isEmpty() && state.albums.isNotEmpty()) {
            Text(
                text = stringResource(R.string.albums_filter_none),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
            )
            return@Column
        }

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
        val columns = if (maxWidth >= WideBreakpoint) 2 else 1
        // Ceiling, so an odd list puts the extra row in the left column and the right one ends a
        // row short — rather than the left ending short and the split reading as off-by-one.
        val half = (visibleAlbums.size + columns - 1) / columns

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Split by count into columns that scroll together, not a row-major grid. Each column
            // stays alphabetical top to bottom, so you scan one and ignore the other; row-major
            // would put consecutive albums side by side and make the eye zigzag for every item —
            // worse for finding a name, which is the only thing anyone does on this screen.
            items(count = half, key = { index -> visibleAlbums[index].name }) { index ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    for (column in 0 until columns) {
                        val album = visibleAlbums.getOrNull(index + column * half)
                        if (album == null) {
                            // The odd list's empty slot. Keeps the left column's width honest
                            // rather than letting its last card stretch across both.
                            Spacer(modifier = Modifier.weight(1f))
                        } else {
                            Box(modifier = Modifier.weight(1f)) {
                                AlbumModeRow(
                                    album = album,
                                    context = context,
                                    onTapped = { onAlbumTapped(album) },
                                    onModeSelected = { mode -> onModeSelected(album, mode) }
                                )
                            }
                        }
                    }
                }
            }
        }
        }
    }
}

/**
 * The heading, and the four modes as a 2 x 2 of buttons.
 *
 * Ian, 27 Aug 2026, replacing a count that led the card: this tab is where modes are chosen, so its
 * lead should be the modes themselves. Four equal buttons in a square read as a set of choices;
 * the same four in a scrolling row read as a toolbar, and the fourth falls off a folded screen.
 *
 * Each button carries its count, so the block is still the summary it replaced — it just answers
 * "which ones?" when tapped instead of only stating a number.
 */
@Composable
private fun ModeFilterGrid(
    selected: AlbumMode?,
    onSelect: (AlbumMode?) -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // The heading is also the way back. Ian, 27 Aug 2026. Four buttons that narrow the list
        // need a fifth that widens it again, and the word at the top of the card was already
        // sitting there meaning "all of them" — so it says so and does so rather than being a
        // label above controls that quietly contradict it.
        //
        // Ringed when nothing is filtered, the same mark the mode buttons use for the same fact:
        // this is the set you are looking at.
        Surface(
            modifier = Modifier.fillMaxWidth(),
            onClick = { onSelect(null) },
            shape = RoundedCornerShape(percent = 50),
            color = Color.Transparent,
            contentColor = LocalContentColor.current,
            border = BorderStroke(
                width = if (selected == null) 2.dp else 1.dp,
                color = LocalContentColor.current.copy(alpha = if (selected == null) 0.9f else 0.35f)
            )
        ) {
            Text(
                text = stringResource(R.string.backup_hero_label),
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
                maxLines = 1,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ModeFilterChip(
                AlbumMode.BACKUP, selected, onSelect, Modifier.weight(1f)
            )
            ModeFilterChip(
                AlbumMode.SYNC, selected, onSelect, Modifier.weight(1f)
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ModeFilterChip(
                AlbumMode.ARCHIVE, selected, onSelect, Modifier.weight(1f)
            )
            ModeFilterChip(
                AlbumMode.OFF, selected, onSelect, Modifier.weight(1f)
            )
        }

        // These read as status until you know they are controls — a count beside a word looks like
        // a summary, which is exactly what they were an hour ago. One quiet line rather than an
        // affordance on each button, which would make five loud things out of five calm ones.
        Text(
            text = stringResource(R.string.albums_filter_hint),
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
            color = LocalContentColor.current.copy(alpha = 0.7f),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/**
 * One mode, its count, and whether the list is currently narrowed to it.
 *
 * Wears the mode's own colours — the same pair the badge on every album row uses. Ian, 27 Aug 2026.
 * The link between "the thing I tapped" and "the rows I now see" should not need explaining, and it
 * does not when both are the same warm orange.
 *
 * Selection is a ring in the mode's own content colour rather than a change of fill, for the reason
 * the dropdown gives: a mark drawn ON the pill keeps all four the same size, where a mark beside it
 * makes one of them wider than the rest.
 */
@Composable
private fun ModeFilterChip(
    mode: AlbumMode,
    selected: AlbumMode?,
    onClick: (AlbumMode?) -> Unit,
    modifier: Modifier = Modifier
) {
    val isOn = selected == mode
    val (container, onContainer) = mode.pillColors()

    Surface(
        modifier = modifier,
        onClick = { onClick(mode) },
        shape = RoundedCornerShape(percent = 50),
        color = container,
        contentColor = onContainer,
        border = if (isOn) BorderStroke(2.dp, onContainer) else null
    ) {
        Text(
            text = mode.label(),
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp)
        )
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
    // A plain Row, not LabelWithAction. That helper stacks its action below the label when the row
    // gets tight, which was right when the action was a 280dp-minimum text field. The pill is as
    // wide as its word, so it always fits beside the album — and stacked it read as the loudest
    // thing in the card, which is wrong for Off above all.
    Row(
        modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
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
            Text(
                text = album.statusBreakdown(),
                style = MaterialTheme.typography.bodySmall,
                color = if (album.outstanding == 0 && album.backedUpCount > 0)
                    MaterialTheme.colorScheme.primary
                else
                    MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        AlbumModeDropdown(current = album.mode, onModeSelected = onModeSelected)
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
            // A minimum width so Off and Sync are not visibly smaller buttons than Backup and
            // Archive. Ian, 27 Aug 2026. The note above still holds — this is not full width, which
            // is what made Off shout on the cover screen; it is the same equalising the dropdown
            // already does, for the same reason: four ragged widths read as four unrelated things.
            Row(
                modifier = Modifier
                    .widthIn(min = ModePillMinWidth)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = current.label(),
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    textAlign = TextAlign.Center
                )
                Icon(
                    imageVector = SignalIcons.ChevronDown,
                    // The pill already reads its mode aloud; naming the caret separately would make
                    // a screen reader say the control twice.
                    contentDescription = null,
                    modifier = Modifier
                        .padding(start = 4.dp)
                        .size(18.dp)
                )
            }
        }
        // The menu is the same vocabulary as the pill that opened it: every mode shown in its own
        // tint, so choosing is recognising a colour rather than reading four words. A stock menu of
        // plain rows in a square-cornered surface reads as a different app bolted onto this one.
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            shape = RoundedCornerShape(20.dp),
            containerColor = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        ) {
            AlbumMode.entries.forEach { mode ->
                val (itemContainer, itemContent) = mode.pillColors()
                DropdownMenuItem(
                    text = {
                        // The current mode is ringed in its own content colour rather than
                        // flagged with a tick beside it. The mark then lives ON the option instead
                        // of next to it, so the four pills stay the same width and aligned — which
                        // was the whole point of equalising them.
                        Surface(
                            shape = RoundedCornerShape(percent = 50),
                            color = itemContainer,
                            contentColor = itemContent,
                            border = if (mode == current) {
                                BorderStroke(2.dp, itemContent)
                            } else {
                                null
                            }
                        ) {
                            // A minimum width, not the natural one. In the ROW the pill is as wide
                            // as its word — there it is a label and Off should be small. Stacked in
                            // a menu they are four options being compared, and ragged widths read
                            // as four unrelated things rather than one set. A minimum rather than a
                            // fixed width so a long label at a large font scale grows, not clips.
                            //
                            // No tick on the current mode: the pill that opened the menu already
                            // says which one that is, and the tick sat outside the pill, making
                            // that one row wider than the rest just after equalising them.
                            Text(
                                text = mode.label(),
                                style = MaterialTheme.typography.labelLarge,
                                maxLines = 1,
                                textAlign = TextAlign.Center,
                                modifier = Modifier
                                    .widthIn(min = MenuPillMinWidth)
                                    .padding(horizontal = 16.dp, vertical = 8.dp)
                            )
                        }
                    },
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
 * What the modes add up to, what a run would move, and the promise underneath.
 *
 * The mode breakdown leads, because it is the summary of the list below and the thing the tab is
 * for. The verified count comes last: it was the headline until 27 Aug 2026 and is still the app's
 * core promise, but it describes files rather than the albums it was sitting above.
 */
/**
 * What the filter is currently showing, in its own terms.
 *
 * Per-mode rather than one fixed set of lines, because the interesting number differs: Sync is
 * judged on what optimising saved, Archive on what is not yet verified and so cannot be removed,
 * and Backup on neither since it never touches the local copy.
 *
 * This replaced a line reading "Everything is backed up", which counted only switched-on albums and
 * so said "everything" about a subset — with an Off album sitting in the list holding eleven files
 * that were not backed up at all. A summary that names the slice it counts cannot make that claim.
 */
@Composable
private fun HeroDetail(
    state: BackupUiState,
    context: android.content.Context,
    modeFilter: AlbumMode?
) {
    if (!state.hasLoadedCounts) return
    val summary = state.summaryFor(modeFilter)

    Text(
        text = stringResource(
            R.string.albums_media_summary,
            pluralStringResource(R.plurals.image_count, summary.imageCount, summary.imageCount),
            pluralStringResource(R.plurals.video_count, summary.videoCount, summary.videoCount),
            formatBytes(context, summary.totalBytes)
        ),
        style = MaterialTheme.typography.bodySmall
    )

    Text(
        text = if (modeFilter == null) {
            stringResource(
                R.string.albums_mode_totals,
                state.backupAlbumCount,
                state.syncAlbumCount,
                state.archiveAlbumCount,
                state.offAlbumCount
            )
        } else {
            pluralStringResource(R.plurals.album_count, summary.albumCount, summary.albumCount)
        },
        style = MaterialTheme.typography.bodySmall
    )

    // Sync is the mode that shrinks things, so it is the one judged on what came back.
    if (modeFilter == AlbumMode.SYNC) {
        Text(
            text = stringResource(
                R.string.albums_optimised_summary,
                summary.optimisedCount,
                formatBytes(context, summary.savedBytes)
            ),
            style = MaterialTheme.typography.bodySmall
        )
    }

    // Archive is judged on what cannot leave yet. A file not verified is a file that stays, and
    // that is the number worth knowing before opening the Archive tab.
    if (modeFilter == AlbumMode.ARCHIVE) {
        Text(
            text = if (summary.awaitingVerification == 0) {
                stringResource(R.string.albums_awaiting_none)
            } else {
                stringResource(R.string.albums_awaiting_summary, summary.awaitingVerification)
            },
            style = MaterialTheme.typography.bodySmall
        )
    }

    // The promise, last and always. Follows the em-dash rule above via hasLoadedCounts.
    Text(
        text = stringResource(
            R.string.backup_verified_line,
            pluralStringResource(R.plurals.file_count, state.uploadedCount, state.uploadedCount)
        ),
        style = MaterialTheme.typography.bodySmall
    )
}

/**
 * Sync now and Rescan, sharing one line.
 *
 * A Row that shares the width, not a FlowRow that wraps. Two buttons at their natural width did not
 * both fit on the cover screen and the second dropped to its own line, which made the hero taller
 * than the list it introduces. Equal weights let them shrink together instead — with reduced content
 * padding so the labels still fit at a large font scale rather than truncating.
 */
@Composable
private fun HeroActions(state: BackupUiState, onSyncNow: () -> Unit, onRescan: () -> Unit) {
    val signal = LocalGallerySyncColors.current

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Disabled when a run would transfer nothing. A button that can be pressed and then
        // visibly does nothing is worse than one that is plainly unavailable.
        //
        // The disabled pair matters as much as the enabled one here. Material's defaults derive
        // disabled colours from the SCHEME's surface, which on the hero's filled container came out
        // dark-green-on-dark-green and read as an empty hole. Derived from the hero's own content
        // colour instead, so it is plainly present and plainly unavailable.
        Button(
            onClick = onSyncNow,
            enabled = state.canRunBackup,
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = signal.accent,
                contentColor = signal.onAccent,
                disabledContainerColor = LocalContentColor.current.copy(alpha = 0.14f),
                disabledContentColor = LocalContentColor.current.copy(alpha = 0.55f)
            )
        ) {
            Text(
                stringResource(
                    // A control, not a label. It read "Syncing…" and was disabled, so a run the
                    // user started could not be stopped by the person who started it.
                    if (state.isRunning) R.string.backup_stop else R.string.backup_run_now
                ),
                maxLines = 1
            )
        }
        HeroOutlinedButton(
            onClick = onRescan,
            label = stringResource(R.string.backup_rescan),
            modifier = Modifier.weight(1f)
        )
    }
}

/** Where a phone layout stops being the right answer. The standard expanded-width breakpoint. */
private val WideBreakpoint = 600.dp


/** The tint each mode is recognised by. Paired, so no caller has to choose a text colour. */
/** Enough for "Archive" at a large font scale, so the four row pills match. */
private val ModePillMinWidth = 96.dp

/** Wider again in the menu, where the four are stacked and compared directly. */
private val MenuPillMinWidth = 132.dp

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

// ArchiveReadyPrompt lived here until 26 Aug 2026, when removal left the Albums tab for TASK-016's
// Archive screen. Its four strings are deliberately left in strings.xml — archive_ready_title,
// archive_ready_body, archive_ready_action and backup_move_trash_note — because the new screen needs
// all four and they carry wording that was argued over rather than drafted. The trash note in
// particular is the app's only statement of CLAUDE.md's rule that a local removal must never be
// promised as recoverable; it has now been moved twice and lost neither time.
