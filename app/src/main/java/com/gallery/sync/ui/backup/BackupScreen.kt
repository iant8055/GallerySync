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
import androidx.compose.ui.platform.LocalConfiguration
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
import com.gallery.sync.domain.backup.AlbumCloudClaim
import com.gallery.sync.domain.backup.StopReason
import com.gallery.sync.ui.common.LabelWithAction
import com.gallery.sync.ui.common.SignalIcons
import com.gallery.sync.ui.common.HeroCard
import com.gallery.sync.ui.common.HeroOutlinedButton
import com.gallery.sync.ui.common.formatBytes
import com.gallery.sync.ui.theme.LocalGallerySyncColors
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.IconButton
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.vector.ImageVector
import com.gallery.sync.ui.common.isCompactWidth
import kotlin.math.roundToInt

/**
 * Backup control: which albums, and a manual run.
 *
 * Running is deliberately manual while the feature is being proven. Nothing schedules itself, so a
 * build can never start uploading someone's library on its own.
 */
@Composable
fun BackupScreen(
    modifier: Modifier = Modifier,
    /** Called once the user accepts the Archive confirmation, to show them where it now lives. */
    onAlbumArchived: () -> Unit = {},
    viewModel: BackupViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    // Re-read on entering the tab, OneDrive included. Archiving happens on another screen with its
    // own ViewModel, so nothing here hears about it: after a removal this tab went on saying "13
    // Scheduled to leave this phone" about files already in the trash. Ian, 27 Aug 2026. The
    // ViewModel outlives a tab switch, which is what makes the staleness survive.
    //
    // `rescan` rather than `refresh` since 28 Aug 2026, so the "verified in OneDrive" lines are
    // answered by the drive on arrival rather than inherited from whenever the app last launched.
    // It guards against overlapping walks itself.
    LaunchedEffect(Unit) { viewModel.rescan() }

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
                    onAlbumArchived = onAlbumArchived,
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
                onAlbumArchived = onAlbumArchived,
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
    onAlbumArchived: () -> Unit,
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
            actionsAtBottom = true,
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
                    onSyncNow = viewModel::runBackupNow,
                    onRescan = viewModel::rescan,
                    onPause = viewModel::pauseBackup,
                    onResume = viewModel::resumeBackup,
                    onStop = viewModel::stopBackup
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
            onConfirm = {
                viewModel.setAlbumMode(albumName, AlbumMode.ARCHIVE)
                archiveConfirmAlbum = null
                // Only on accept. Cancelling leaves the user exactly where they were, which is the
                // whole point of a confirmation having two answers.
                onAlbumArchived()
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

    // Read the HERO's content colour, before the Surface below replaces it with the pill's own.
    // A ring drawn in the pill's ink is pale in dark theme and dark in light, and on the dark green
    // hero the light-theme one all but vanished — Backup's was dark green on dark green.
    val heroContent = LocalContentColor.current

    // A ring alone was not enough, twice. Ian could still not tell which mode was selected: a pale
    // ring around a pale pill on a dark card reads as a slightly larger pill, because nothing about
    // the figure changes, only its edge.
    //
    // So the unselected ones lose their fill and become outlines. Filled-versus-ghost is a change
    // to the figure itself and cannot be missed at a glance, which is the whole job.
    //
    // The first attempt at this faded the pill's own colours instead, and measured 1.24:1 in light
    // theme — illegible. Fading a dark ink toward a dark-tinted container moves both ends of the
    // contrast the same way. The hero's ink is the colour already chosen to be read on this card,
    // so the ghost uses that: 7.7:1 in light, 6.7:1 in dark.
    //
    // With no filter every pill keeps its fill. That is a real state — "all albums" — and greying
    // all four to say "none chosen" would misreport four modes that do exist.
    val dimmed = selected != null && !isOn

    Surface(
        modifier = modifier,
        onClick = { onClick(mode) },
        shape = RoundedCornerShape(percent = 50),
        color = if (dimmed) Color.Transparent else container,
        contentColor = if (dimmed) heroContent.copy(alpha = 0.75f) else onContainer,
        border = when {
            isOn -> BorderStroke(3.dp, heroContent)
            dimmed -> BorderStroke(1.dp, heroContent.copy(alpha = 0.35f))
            else -> null
        }
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
            if (album.isArchivedAndEmpty) {
                // One line. The usual counts would describe files that are not here any more, and
                // the row's own Archive badge already says the mode stands and can be changed.
                Text(
                    text = stringResource(R.string.album_archived_empty),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            } else {
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
                // Only when there is something to say. With the upload count gone this is empty for
                // an ordinary album, and an empty Text still takes a line's height — which would
                // leave a ragged gap between the file count and the cloud line.
                val breakdown = album.statusBreakdown()
                if (breakdown.isNotEmpty()) {
                    Text(
                        text = breakdown,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                // The cloud line, on its own and sourced from the drive rather than the ledger. It
                // is only tinted as good news when the drive actually said so — an unchecked album
                // gets the ordinary colour, because a reassuring green on an unverified claim is
                // the same lie in a different medium.
                Text(
                    text = album.cloudClaim.sentence(),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (album.cloudClaim is AlbumCloudClaim.AllPresent)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
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
// The album name is no longer a parameter: the body was rewritten on 28 Aug 2026 into three steps
// that describe the mode rather than the album, and the title already says "this album" against the
// row the user just tapped.
private fun ArchiveConfirmDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.archive_confirm_title)) },
        text = { Text(stringResource(R.string.archive_confirm_body)) },
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

/**
 * What the row says about OneDrive, and it only says what the drive was actually asked.
 *
 * The counts below this line describe the phone. This one describes the cloud, and until
 * 28 Aug 2026 it was drawn from the ledger like all the others — so an album whose OneDrive folder
 * had been deleted by hand went on reading "8 backed up". See `AlbumCloudClaim`.
 */
@Composable
private fun AlbumCloudClaim.sentence(): String = when (this) {
    AlbumCloudClaim.NeverChecked -> stringResource(R.string.album_cloud_never_checked)
    is AlbumCloudClaim.Unreachable -> stringResource(R.string.album_cloud_unreachable)
    is AlbumCloudClaim.AllPresent ->
        stringResource(R.string.album_cloud_all_present, verified)
    is AlbumCloudClaim.SomeMissing ->
        stringResource(R.string.album_cloud_some_missing, verified, verified + missing)
}

/**
 * What is true of the files on this phone. Empty when there is nothing to say.
 *
 * **The upload count is gone**, removed by Ian on 28 Aug 2026: *"it can get confusing as files are
 * moved, added, deleted."* He is describing a real drift. The ledger counts rows this phone once
 * sent, keyed on content, while the file count beside it comes from a live device scan — so moving
 * a file between albums, deleting one, or adding one already in the cloud moves the two numbers
 * independently, and the row ends up showing a pair nobody can reconcile by looking. The cloud line
 * underneath now carries the claim that actually matters, and it is sourced from the drive.
 *
 * What is left describes the phone in the present tense and cannot drift: how many files here are
 * optimised, and how many are still waiting to go.
 */
@Composable
private fun AlbumRow.statusBreakdown(): String {
    val separator = " · "
    return buildString {
        if (proxiedCount > 0) {
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

    // Ian's order, 27 Aug 2026: how many albums and how big, then — for All Albums — the split by
    // mode, then the media breakdown, then whatever that mode is judged on.
    Text(
        text = stringResource(
            R.string.albums_size_summary,
            pluralStringResource(
                R.plurals.albums_hero_albums,
                summary.albumCount,
                summary.albumCount
            ),
            formatBytes(context, summary.totalBytes)
        ),
        style = MaterialTheme.typography.titleSmall
    )
    if (modeFilter == null) {
        Text(
            text = stringResource(
                R.string.albums_mode_totals,
                state.backupAlbumCount,
                state.syncAlbumCount,
                state.archiveAlbumCount,
                state.offAlbumCount
            ),
            style = MaterialTheme.typography.bodyMedium
        )

        // The hairline that sat here went with the media counts on 28 Aug 2026. It was added on
        // 27 Aug to separate what the albums are set to from what is actually in them; with the
        // second half gone it had nothing to divide and left a rule floating above a gap.
    }

    // The "N Images · M Videos" line lived here until 28 Aug 2026. Removed because the number
    // could not be read correctly without knowing three separate filters: it counted only files
    // inside the granted trees, excluded the Restored album (already in OneDrive, so counting it
    // would mean uploading a second copy), and could not see trashed files at all. On the Fold 4
    // that made it 86 while the phone held 150 files and Samsung Gallery showed 45 — every figure
    // correct about a different question.
    //
    // Ian, 28 Aug 2026: *"each folder has a count and that should be enough"*. The album rows carry
    // per-album counts, scoped the same way and next to the album they describe, where the scope is
    // obvious rather than needing a Help entry.

    // Gated on the shown set rather than on albums that merely upload, and it used to read as a
    // continuation of the counts above it. Standing alone now, it has to be a sentence.
    //
    // The claim must be true of everything shown, including Off albums — a phone with two unsynced
    // photos in Camera must not announce that everything is safe.
    val shown = state.albums.filter { modeFilter == null || it.mode == modeFilter }
    // `all` over empty albums is vacuously true, which put "0 Images · 0 Videos / are backed up" on
    // the Archive filter — where every album is empty by design. The claim needs something to be
    // true of, so the counts it sits under must be non-zero.
    if (shown.sumOf { it.itemCount } > 0 && shown.all { it.outstanding == 0 }) {
        Text(
            text = stringResource(R.string.albums_all_backed_up),
            style = MaterialTheme.typography.bodyMedium
        )
    }

    // Sync is the mode that shrinks things, so it is judged on what came back.
    if (modeFilter == AlbumMode.SYNC) {
        Text(
            text = stringResource(
                R.string.albums_optimised_summary,
                summary.optimisedCount,
                formatBytes(context, summary.savedBytes)
            ),
            style = MaterialTheme.typography.bodyMedium
        )
    }

    // Archive is the one filter whose subject is not on the phone. Every other figure on this card
    // counts local files, which for a finished archive is nothing — so this view reported "0 Images
    // · 0 Videos" over two albums holding 24 files in OneDrive. Ian, 27 Aug 2026: it should list
    // what has been archived from those folders.
    //
    // Two different questions, both answered, because a half-archived album needs both: what has
    // already gone, and what is still waiting to go.
    if (modeFilter == AlbumMode.ARCHIVE) {
        if (summary.archivedCount > 0) {
            Text(
                text = stringResource(
                    R.string.albums_archived_summary,
                    summary.archivedCount,
                    formatBytes(context, summary.archivedBytes)
                ),
                style = MaterialTheme.typography.bodyMedium
            )
        }

        val scheduled = summary.imageCount + summary.videoCount
        Text(
            text = if (scheduled == 0) {
                // Archive finished. "0 Scheduled to leave this phone" is arithmetic, not an answer.
                stringResource(R.string.albums_scheduled_none)
            } else {
                stringResource(R.string.albums_scheduled_summary, scheduled)
            },
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

/**
 * What the app is doing, and the controls for it.
 *
 * A Row that shares the width, not a FlowRow that wraps. Two buttons at their natural width did not
 * both fit on the cover screen and the second dropped to its own line, which made the hero taller
 * than the list it introduces. Equal weights let them shrink together instead — with reduced
 * content padding so the labels still fit at a large font scale rather than truncating.
 *
 * **The left control reports; it does not act.** It used to be Sync now and relabel to "Stop sync"
 * while running, which is why Stop appeared to have nowhere to live once Pause wanted a slot. It is
 * a button only when there is a run to start.
 *
 * Three controls do not fit the cover screen at 344dp — about 85dp each against a
 * [ModePillMinWidth] of 96dp — so Pause and Stop drop to icons there. [isCompactWidth] decides,
 * and it keys on font scale as well as width: a 400dp screen at 2x has the same problem as a 320dp
 * one at 1.6x.
 */
@Composable
private fun HeroActions(
    state: BackupUiState,
    onSyncNow: () -> Unit,
    onRescan: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onStop: () -> Unit
) {
    val signal = LocalGallerySyncColors.current
    val active = state.isRunning || state.isPaused

    // Window width, not BoxWithConstraints.
    //
    // BoxWithConstraints is a SubcomposeLayout, and something above this row measures intrinsics —
    // which throws "Asking for intrinsic measurements of SubcomposeLayout layouts is not supported"
    // during draw. It crashed the app on every launch of the Albums tab, four times in a row on the
    // Fold 4, 28 Aug 2026, before it was caught.
    //
    // The window is the right measure anyway: this row spans the hero card, which spans the screen,
    // so the only difference is padding — and the threshold has far more slack than that. Cover
    // screen 344dp, Moto G 443dp, Fold inner 690dp, against a 360dp threshold.
    val compact = isCompactWidth(LocalConfiguration.current.screenWidthDp.dp)

    Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (active) {
                // A report, not a control. The percentage is of bytes within the selected albums,
                // because by file count a video-heavy library crawls and then leaps: on the Moto G
                // the same moment read 5% by files and 37% by bytes.
                //
                // No number until the counts have actually been read. Defaulting the fraction to
                // zero rendered a confident "Syncing 0%" on every cold start — observed on the
                // Moto G, 28 Aug 2026, while four files were already uploaded. Zero is a claim, and
                // this is the same trap [BackupUiState.hasLoadedCounts] was added for.
                val fraction = state.runProgress.takeIf { state.hasLoadedCounts }
                Text(
                    text = if (fraction == null) {
                        stringResource(
                            if (state.isPaused) R.string.backup_paused else R.string.backup_syncing
                        )
                    } else {
                        stringResource(
                            if (state.isPaused) {
                                R.string.backup_paused_at
                            } else {
                                R.string.backup_syncing_at
                            },
                            (fraction * 100).roundToInt()
                        )
                    },
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    modifier = Modifier
                        .weight(1f)
                        .alpha(if (state.isPaused) 0.6f else 1f)
                )
            } else {
                Button(
                    onClick = onSyncNow,
                    enabled = state.pendingCount > 0,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = signal.accent,
                        contentColor = signal.onAccent,
                        disabledContainerColor = LocalContentColor.current.copy(alpha = 0.14f),
                        disabledContentColor = LocalContentColor.current.copy(alpha = 0.55f)
                    )
                ) {
                    Text(stringResource(R.string.backup_run_now), maxLines = 1)
                }
            }

            when {
                // Paused is tested first, and the order is load-bearing: cancelling the work takes
                // a moment, so both flags are true in between. Testing isRunning first left the
                // screen reading "Paused at 95%" beside a Pause button — observed on the Moto G,
                // 28 Aug 2026, on the first press.
                //
                // Pause holds until told otherwise; Stop ends the run and lets automatic sync pick
                // up at the next trigger. Two different answers about the *next* run, which is why
                // they are two controls rather than one.
                state.isPaused -> {
                    HeroControl(
                        compact = compact,
                        icon = SignalIcons.Resume,
                        label = stringResource(R.string.backup_resume),
                        onClick = onResume
                    )
                    HeroControl(
                        compact = compact,
                        icon = SignalIcons.Stop,
                        label = stringResource(R.string.backup_stop_run),
                        onClick = onStop
                    )
                }

                state.isRunning -> {
                    HeroControl(
                        compact = compact,
                        icon = SignalIcons.Pause,
                        label = stringResource(R.string.backup_pause),
                        onClick = onPause
                    )
                    HeroControl(
                        compact = compact,
                        icon = SignalIcons.Stop,
                        label = stringResource(R.string.backup_stop_run),
                        onClick = onStop
                    )
                }

            // Says what it is doing while it does it. The drive walk takes tens of seconds on a
            // real library, and a button that looks idle throughout invites a second press.
            else -> HeroOutlinedButton(
                onClick = onRescan,
                label = if (state.isCheckingCloud) {
                    stringResource(R.string.backup_checking_cloud)
                } else {
                    stringResource(R.string.backup_rescan)
                },
                enabled = !state.isCheckingCloud,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

/**
 * One hero control, worded or drawn depending on the room available.
 *
 * The label is the content description in both forms, so the icon and the words cannot drift and
 * TalkBack says the same thing either way.
 */
@Composable
private fun RowScope.HeroControl(
    compact: Boolean,
    icon: ImageVector,
    label: String,
    onClick: () -> Unit
) {
    if (compact) {
        // Outlined, not a bare IconButton. A plain icon draws no container, so on the cover screen
        // Pause and Stop floated beside the text with nothing marking them as controls — Ian, on
        // seeing them folded: *"they were not buttons"*. The border is the same one
        // [HeroOutlinedButton] uses, derived from LocalContentColor so it follows the hero in
        // either theme rather than being a fixed white.
        val content = LocalContentColor.current
        OutlinedIconButton(
            onClick = onClick,
            colors = IconButtonDefaults.outlinedIconButtonColors(contentColor = content),
            border = BorderStroke(1.dp, content.copy(alpha = 0.35f))
        ) {
            Icon(imageVector = icon, contentDescription = label)
        }
    } else {
        HeroOutlinedButton(onClick = onClick, label = label, modifier = Modifier.weight(1f))
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
