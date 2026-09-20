package com.gallery.sync.ui.backup

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.gallery.sync.R
import com.gallery.sync.data.local.entity.AlbumMode
import com.gallery.sync.data.local.entity.BackupEntryEntity
import com.gallery.sync.data.local.entity.BackupState
import com.gallery.sync.domain.backup.AlbumFileSort
import com.gallery.sync.domain.backup.CameraOptimiseAge
import com.gallery.sync.domain.backup.CameraOptimisePlan
import com.gallery.sync.domain.backup.CameraOptimiseSettings
import com.gallery.sync.domain.backup.FilePin
import com.gallery.sync.domain.backup.FileSort
import com.gallery.sync.ui.common.HeroOutlinedButton
import com.gallery.sync.ui.common.SignalIcons
import com.gallery.sync.ui.common.SwipeChoiceBox
import com.gallery.sync.ui.common.formatBytes
import com.gallery.sync.ui.help.HelpButton
import com.gallery.sync.ui.help.HelpTopic
import com.gallery.sync.ui.help.WithHelp
import com.gallery.sync.ui.theme.LocalGallerySyncColors
import java.time.Instant

/** Choose and Cancel are wider than their words, as a pair of buttons wants to be. Ian, 20 Sept 2026. */
private val CameraButtonMinWidth = 140.dp

/** Two columns of file cards from here up, as on the Restore tab: unfolded, more rows rather than wider ones. */
private val WideBreakpoint = 600.dp

/**
 * What the Camera album's *Only list Photos/Videos older than…* control needs from its owner.
 * Null for every other album, which is what keeps the control off them.
 */
data class CameraOptimiseControls(
    val settings: CameraOptimiseSettings,
    /** A pass is queued or running, so the button and the swipes wait. */
    val running: Boolean,
    /** The tap. Handed the cutoff the list was drawn with, so what is done is what was shown. */
    val onOptimise: (modifiedBeforeEpochSeconds: Long) -> Unit
)

/**
 * An album's files.
 *
 * Laid out like the Restore tab's folder view (Ian, 18 Sept 2026): the same green card at the top the
 * other tabs open with, and one rounded card per file below it, in the same style and the same order
 * of lines. It is still a plain list of names and marks. No thumbnails, no preview: looking at photos
 * is the gallery app's job.
 */
@Composable
fun AlbumDetailScreen(
    albumName: String,
    mode: AlbumMode,
    entries: List<BackupEntryEntity>,
    onBack: () -> Unit,
    /** The tick box: keep one file at full size, or let it follow its album again. See `FilePin`. */
    onSetPinned: (BackupEntryEntity, Boolean) -> Unit,
    modifier: Modifier = Modifier,
    /** Only the Camera album passes this. See [CameraOptimiseControls]. */
    camera: CameraOptimiseControls? = null
) {
    val context = LocalContext.current
    var sort by rememberSaveable { mutableStateOf(FileSort.NAME) }

    // The Camera album's chosen age and the cutoff it made, kept together and not remembered past this
    // screen: it is a manual optimise, not a rule (Ian, 20 Sept 2026), so nothing here is stored.
    var ageName by rememberSaveable(albumName) { mutableStateOf<String?>(null) }
    var before by rememberSaveable(albumName) { mutableLongStateOf(0L) }
    val age = ageName?.let { name -> CameraOptimiseAge.entries.firstOrNull { it.name == name } }
    val plan = remember(entries, age, before, camera?.settings) {
        if (camera != null && age != null) CameraOptimisePlan.of(entries, before, camera.settings) else null
    }

    // With an age chosen the list is the files that age would optimise, greyed ones included, and
    // nothing else. Without one it is the album as it always was.
    //
    // In the Camera album nothing is listed until an age is chosen (Ian, 20 Sept 2026): a list of files
    // that cannot be swiped, above a control that would make them swipeable, only confused.
    val awaitingAge = camera != null && plan == null
    val shown = remember(entries, sort, plan, awaitingAge) {
        if (awaitingAge) emptyList()
        else AlbumFileSort.sorted(plan?.let { it.eligible + it.optedOut } ?: entries, sort)
    }

    // Which files get a tick box: the ones Restore has put back, and only those (Ian, 18 Sept 2026 —
    // the box exists to undo Restore's pin, so a file Restore never touched has nothing to undo).
    //
    // Worked out once when the list opens rather than from what is ticked now. Restore's pin is the
    // only thing that sets the flag, so "ticked when the list opened" is "restored"; and a file the
    // user unticks keeps its box until they leave, so an accidental untick can be put back. Once the
    // list is closed an unticked file has no box: nothing else records that it was ever restored.
    val restoredIds = remember(albumName) {
        entries.filter { FilePin.isPinned(it.modeOverride) }.mapTo(HashSet()) { it.id }
    }

    Column(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.padding(16.dp)) {
            DetailHeader(
                albumName = albumName,
                mode = mode,
                entries = entries,
                sort = sort,
                onSort = { sort = it },
                showKeepColumn = restoredIds.isNotEmpty() && plan == null,
                camera = camera?.let {
                    CameraHeader(
                        controls = it,
                        age = age,
                        before = before,
                        plan = plan,
                        onAge = { picked ->
                            ageName = picked?.name
                            if (picked != null) before = picked.thresholdEpochSeconds(Instant.now())
                        }
                    )
                },
                onBack = onBack
            )
        }

        HorizontalDivider()

        if (entries.isEmpty()) {
            Text(
                text = "No files tracked yet",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(16.dp)
            )
        } else if (shown.isEmpty()) {
            // Either no age is chosen yet, and this says what to do, or one is and nothing that old
            // qualifies, and the header says why. The spacer keeps the bar, if one is showing, at the
            // bottom of the screen rather than under the header.
            if (awaitingAge) {
                Text(
                    text = stringResource(R.string.camera_choose_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(16.dp)
                )
            }
            Spacer(Modifier.weight(1f))
        } else {
            BoxWithConstraints(modifier = Modifier.fillMaxWidth().weight(1f)) {
                val columns = if (maxWidth >= WideBreakpoint) 2 else 1
                val half = if (shown.isEmpty()) 0 else (shown.size + columns - 1) / columns

                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(half) { index ->
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            for (column in 0 until columns) {
                                val position = index + column * half
                                Box(modifier = Modifier.weight(1f)) {
                                    shown.getOrNull(position)?.let { entry ->
                                        if (plan != null && camera != null) {
                                            // Swiped out means pinned, which is all Keep at full size is. The
                                            // same gesture and the same fade as the Archive tab.
                                            val optedOut = FilePin.isPinned(entry.modeOverride)
                                            SwipeChoiceBox(
                                                enabled = !camera.running,
                                                stateKey = optedOut,
                                                onSwipeRight = { if (optedOut) onSetPinned(entry, false) },
                                                onSwipeLeft = { if (!optedOut) onSetPinned(entry, true) },
                                                accessibilityLabel = stringResource(
                                                    if (optedOut) R.string.camera_action_optimise else R.string.camera_action_keep
                                                ),
                                                onAccessibilityAction = { onSetPinned(entry, !optedOut) }
                                            ) { drawn ->
                                                FileCard(
                                                    entry = entry,
                                                    context = context,
                                                    showKeepBox = false,
                                                    onSetPinned = {},
                                                    modifier = drawn,
                                                    optedOut = optedOut
                                                )
                                            }
                                        } else {
                                            FileCard(
                                                entry = entry,
                                                context = context,
                                                showKeepBox = entry.id in restoredIds,
                                                onSetPinned = { pinned -> onSetPinned(entry, pinned) }
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

        // The Optimise button, at the bottom as Restore's is (Ian, 20 Sept 2026). Shown when there is
        // something to do or a run is in progress, exactly as Restore's bar shows for a selection or a run.
        if (camera != null && plan != null && !camera.settings.everythingOff &&
            (plan.eligible.isNotEmpty() || camera.running)
        ) {
            CameraOptimiseBar(
                count = plan.eligible.size,
                running = camera.running,
                onOptimise = { camera.onOptimise(before) }
            )
        }
    }
}

/**
 * The green card: the way back, the folder's name, its mode and counts, and the two controls.
 *
 * The same surface, shape and colours as the card every other tab opens with, so this screen reads as
 * part of the app and not as a page bolted on. Drawn here rather than through `HeroCard` because that
 * card splits itself into two columns when the screen is wide, and this content is one column.
 */
@Composable
private fun DetailHeader(
    albumName: String,
    mode: AlbumMode,
    entries: List<BackupEntryEntity>,
    sort: FileSort,
    onSort: (FileSort) -> Unit,
    showKeepColumn: Boolean,
    camera: CameraHeader?,
    onBack: () -> Unit
) {
    val signal = LocalGallerySyncColors.current
    var sortMenuOpen by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = signal.heroContainer,
        contentColor = signal.onHero
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // The app's own return glyph, in the card's ink: it was the theme's green, which is
                // the card's own colour here and would have vanished.
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = SignalIcons.Back,
                        contentDescription = stringResource(R.string.retrieve_back),
                        modifier = Modifier.size(32.dp),
                        tint = LocalContentColor.current
                    )
                }
                // The folder's name, bold. Ian, 18 Sept 2026.
                Text(
                    text = albumName,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                HelpButton(HelpTopic.ALBUM_DETAIL)
            }

            Column(
                modifier = Modifier.padding(horizontal = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "${mode.label()} · ${entries.size} files",
                    style = MaterialTheme.typography.bodyMedium
                )

                if (entries.isNotEmpty()) {
                    // Plain text in the card's own ink: the coloured counts this line used to have
                    // were the theme's primary and tertiary, which are not made to sit on green.
                    val keptText = stringResource(R.string.album_status_kept, entries.count { FilePin.isPinned(it.modeOverride) })
                    val counts = buildList {
                        val backed = entries.count { it.state == BackupState.UPLOADED }
                        val proxied = entries.count { it.isProxied }
                        val kept = entries.count { FilePin.isPinned(it.modeOverride) }
                        val pending = entries.count { it.state == BackupState.PENDING }
                        val failed = entries.count { it.state == BackupState.FAILED }
                        if (backed > 0) add("$backed backed up")
                        if (proxied > 0) add("$proxied optimised")
                        if (kept > 0) add(keptText)
                        if (pending > 0) add("$pending pending")
                        if (failed > 0) add("$failed failed")
                    }
                    WithHelp(HelpTopic.ALBUM_FILE_STATUS) {
                        Text(
                            text = counts.joinToString(" · "),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }

                    if (camera != null) CameraOptimiseSection(camera)

                    // Sort by and Keep at full size on one line. The heading is two lines, and only
                    // there when some file has a box. Not in the Camera album before an age is chosen:
                    // there is nothing listed to sort.
                    if (camera == null || camera.plan != null) Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.album_sort_label),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Box {
                            HeroOutlinedButton(
                                onClick = { sortMenuOpen = true },
                                label = stringResource(sort.label())
                            )
                            DropdownMenu(
                                expanded = sortMenuOpen,
                                onDismissRequest = { sortMenuOpen = false }
                            ) {
                                FileSort.entries.forEach { option ->
                                    DropdownMenuItem(
                                        text = { Text(stringResource(option.label())) },
                                        onClick = {
                                            onSort(option)
                                            sortMenuOpen = false
                                        }
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.weight(1f))
                        if (showKeepColumn) {
                            HelpButton(HelpTopic.ALBUM_FILE_PIN)
                            Text(
                                text = stringResource(R.string.album_keep_column),
                                style = MaterialTheme.typography.bodySmall,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.width(56.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * One file, as the Restore tab draws one: a rounded card, the name in `titleMedium`, then a line with
 * its size and its marks. The tick box, where there is one, sits at the end.
 */
@Composable
private fun FileCard(
    entry: BackupEntryEntity,
    context: android.content.Context,
    showKeepBox: Boolean,
    onSetPinned: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    /** Swiped out of the Camera optimise list: faded, as Archive fades a file it will not take. */
    optedOut: Boolean = false
) {
    Surface(
        modifier = modifier.fillMaxWidth().alpha(if (optedOut) 0.5f else 1f),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // The same type as the Restore tab's file cards, which is the Albums card's (Ian,
            // 19 Sept 2026): bodyLarge for the name, bodySmall for the line under it.
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = entry.displayName,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                // Size and marks on one line, each mark in its own colour. They were stacked while
                // the header was being moved around; Ian, 19 Sept 2026: now they can share a line.
                val size = buildString {
                    append(formatBytes(context, entry.sizeBytes))
                    if (entry.isVideo) append(" · video")
                }
                val marks = if (optedOut) emptyList() else entry.statusLines()
                Text(
                    text = buildAnnotatedString {
                        append(if (optedOut) stringResource(R.string.camera_opted_out_detail, size) else size)
                        marks.forEach { (text, color) ->
                            append(" · ")
                            withStyle(SpanStyle(color = color)) { append(text) }
                        }
                    },
                    style = MaterialTheme.typography.bodySmall
                )
            }

            if (showKeepBox) {
                // Colours are the theme's own: a checkbox draws itself from the colour scheme, so it
                // reads in both themes without anything being set here.
                val description = stringResource(R.string.album_file_keep_description, entry.displayName)
                Checkbox(
                    checked = FilePin.isPinned(entry.modeOverride),
                    onCheckedChange = onSetPinned,
                    modifier = Modifier.semantics { contentDescription = description }
                )
            }
        }
    }
}

/**
 * The one action, at the foot of the screen: the same bar as the Restore tab's, a full-width accent
 * button on a tinted strip. Disabled and reading how many are left while a run is
 * going, since there is nothing to stop and nothing to press.
 */
@Composable
private fun CameraOptimiseBar(count: Int, running: Boolean, onOptimise: () -> Unit) {
    val signal = LocalGallerySyncColors.current

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = onOptimise,
                enabled = !running && count > 0,
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = signal.accent,
                    contentColor = signal.onAccent,
                    disabledContainerColor = LocalContentColor.current.copy(alpha = 0.14f),
                    disabledContentColor = LocalContentColor.current.copy(alpha = 0.55f)
                )
            ) {
                Text(
                    text = if (running) {
                        stringResource(R.string.camera_optimise_working, count)
                    } else {
                        pluralStringResource(R.plurals.camera_optimise_button, count, count)
                    },
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1
                )
            }
        }
    }
}

/** What the header needs to draw the Camera control: the choice, what it gives, and what to do about it. */
private data class CameraHeader(
    val controls: CameraOptimiseControls,
    val age: CameraOptimiseAge?,
    /** The cutoff the chosen [age] made, which the list and the button both use. */
    val before: Long,
    val plan: CameraOptimisePlan?,
    val onAge: (CameraOptimiseAge?) -> Unit
)

/**
 * *Only list Photos/Videos older than* [choose], and under it what that would do.
 *
 * Ian's design, 20 Sept 2026. Picking an age fills the list below with the files it would touch, and
 * this says how many and about how much space they would give back; nothing is done until the button
 * is pressed. The count and the estimate are the same for the button and for the worker that follows it.
 * Files swiped out of the list below are not in either.
 */
@Composable
private fun CameraOptimiseSection(camera: CameraHeader) {
    val settings = camera.controls.settings
    val context = LocalContext.current
    var menuOpen by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.camera_optimise_heading),
                style = MaterialTheme.typography.bodyMedium
            )
            HelpButton(HelpTopic.ALBUM_CAMERA_OPTIMISE)
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box {
                HeroOutlinedButton(
                    onClick = { menuOpen = true },
                    label = camera.age?.let { stringResource(it.label()) } ?: stringResource(R.string.camera_optimise_choose),
                    modifier = Modifier.widthIn(min = CameraButtonMinWidth),
                    enabled = !camera.controls.running
                )
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    CameraOptimiseAge.entries.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(stringResource(option.label())) },
                            onClick = {
                                camera.onAge(option)
                                menuOpen = false
                            }
                        )
                    }
                }
            }
            // Beside the chooser, and only once there is something to cancel: it drops the chosen age and
            // the list goes back to the whole album. Ian, 20 Sept 2026, in place of a menu item.
            if (camera.age != null) {
                HeroOutlinedButton(
                    onClick = { camera.onAge(null) },
                    label = stringResource(R.string.camera_optimise_cancel),
                    modifier = Modifier.widthIn(min = CameraButtonMinWidth),
                    enabled = !camera.controls.running
                )
            }
        }

        val plan = camera.plan ?: return@Column
        val count = plan.eligible.size

        when {
            settings.everythingOff -> Text(
                text = stringResource(R.string.camera_optimise_all_off),
                style = MaterialTheme.typography.bodyMedium
            )

            // Nothing to do, and either nothing that old qualifies or every one that does was swiped out.
            count == 0 -> Text(
                text = stringResource(
                    if (plan.optedOut.isEmpty()) R.string.camera_optimise_none else R.string.camera_optimise_all_kept
                ),
                style = MaterialTheme.typography.bodyMedium
            )

            else -> {
                Text(
                    text = if (camera.controls.running) {
                        stringResource(R.string.camera_optimise_working, count)
                    } else {
                        pluralStringResource(
                            R.plurals.camera_optimise_summary,
                            count,
                            count,
                            formatBytes(context, plan.estimatedSavedBytes)
                        )
                    },
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = stringResource(R.string.camera_optimise_swipe_hint),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        // Say which kind is missing when only one is switched off, so a shorter list is not a mystery.
        if (!settings.everythingOff) {
            if (!settings.photos) Text(stringResource(R.string.camera_optimise_photos_off), style = MaterialTheme.typography.bodySmall)
            if (!settings.videos) Text(stringResource(R.string.camera_optimise_videos_off), style = MaterialTheme.typography.bodySmall)
        }
    }
}

private fun CameraOptimiseAge.label(): Int = when (this) {
    CameraOptimiseAge.OneDay -> R.string.camera_age_day
    CameraOptimiseAge.OneWeek -> R.string.camera_age_week
    CameraOptimiseAge.OneMonth -> R.string.camera_age_month
    CameraOptimiseAge.SixMonths -> R.string.camera_age_six_months
    CameraOptimiseAge.OneYear -> R.string.camera_age_year
    CameraOptimiseAge.All -> R.string.camera_age_all
}

private fun FileSort.label(): Int = when (this) {
    FileSort.NAME -> R.string.album_sort_name
    FileSort.DATE -> R.string.album_sort_date
    FileSort.STATUS -> R.string.album_sort_status
}

/**
 * The file's marks, in order: backed up, then optimised.
 *
 * Both facts, because optimised never replaces backed up. `isProxied` was tested first and won, so
 * every optimised file read "✓ optimized" and nothing said it was in the cloud — on precisely the
 * files where that matters most, since a proxy is the case where the full-resolution image exists
 * *only* in OneDrive. Ian, 4 Sept 2026, reading a folder of 100 rows: "all the files are labeled as
 * optimized NOT backed up". A proxy is only ever written over a file the ledger has verified in the
 * cloud, so the two are not alternatives.
 *
 * They were two lines for a while (Ian, 18 Sept 2026) and share the size's line now (19 Sept).
 *
 * Safe is one colour. Uploaded reads primary whether or not it was later optimised.
 */
@Composable
private fun BackupEntryEntity.statusLines(): List<Pair<String, Color>> {
    // The safe green, not `primary`: in the light theme primary is nearly black. Ian, 19 Sept 2026.
    val primary = LocalGallerySyncColors.current.safeText
    val optimised = MaterialTheme.colorScheme.tertiary
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val error = MaterialTheme.colorScheme.error
    return when {
        isProxied && state == BackupState.UPLOADED -> listOf("✓ backed up" to primary, "optimised" to optimised)
        isProxied -> listOf("✓ optimised" to optimised)
        state == BackupState.UPLOADED -> listOf("✓ backed up" to primary)
        state == BackupState.PENDING -> listOf("⟳ pending" to muted)
        state == BackupState.FAILED -> listOf("✗ failed" to error)
        else -> listOf("?" to muted)
    }
}

@Composable
private fun AlbumMode.label(): String = when (this) {
    AlbumMode.OFF -> "Off"
    AlbumMode.BACKUP -> "Backup"
    AlbumMode.SYNC -> "Sync"
    AlbumMode.ARCHIVE -> "Archive"
}
