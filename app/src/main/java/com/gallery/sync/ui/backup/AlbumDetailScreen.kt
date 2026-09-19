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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
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
import com.gallery.sync.domain.backup.FilePin
import com.gallery.sync.domain.backup.FileSort
import com.gallery.sync.ui.common.HeroOutlinedButton
import com.gallery.sync.ui.common.SignalIcons
import com.gallery.sync.ui.common.formatBytes
import com.gallery.sync.ui.help.HelpButton
import com.gallery.sync.ui.help.HelpTopic
import com.gallery.sync.ui.help.WithHelp
import com.gallery.sync.ui.theme.LocalGallerySyncColors

/** Two columns of file cards from here up, as on the Restore tab: unfolded, more rows rather than wider ones. */
private val WideBreakpoint = 600.dp

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
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var sort by rememberSaveable { mutableStateOf(FileSort.NAME) }
    val shown = remember(entries, sort) { AlbumFileSort.sorted(entries, sort) }

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
                showKeepColumn = restoredIds.isNotEmpty(),
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

                    // Sort by and Keep at full size on one line. The heading is two lines, and only
                    // there when some file has a box.
                    Row(
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
    onSetPinned: (Boolean) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
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
                val marks = entry.statusLines()
                Text(
                    text = buildAnnotatedString {
                        append(size)
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
