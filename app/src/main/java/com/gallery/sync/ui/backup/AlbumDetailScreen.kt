package com.gallery.sync.ui.backup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.gallery.sync.R
import com.gallery.sync.data.local.entity.AlbumMode
import com.gallery.sync.data.local.entity.BackupEntryEntity
import com.gallery.sync.data.local.entity.BackupState
import com.gallery.sync.domain.backup.AlbumFileSort
import com.gallery.sync.domain.backup.FilePin
import com.gallery.sync.domain.backup.FileSort
import com.gallery.sync.ui.common.SignalIcons
import com.gallery.sync.ui.common.formatBytes
import com.gallery.sync.ui.help.HelpButton
import com.gallery.sync.ui.help.HelpTopic
import com.gallery.sync.ui.help.TitleWithHelp
import com.gallery.sync.ui.help.WithHelp

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
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // The app's own return glyph, the one the Restore folder view and the OneDrive picker use
            // (Ian's `←┘`), at 32dp. It was a "←" character in a text button, which was small and was
            // the only back control in the app that did not look like the others.
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = SignalIcons.Back,
                    contentDescription = stringResource(R.string.retrieve_back),
                    modifier = Modifier.size(32.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = albumName,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${mode.label()} · ${entries.size} files",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            HelpButton(HelpTopic.ALBUM_DETAIL, Modifier.padding(end = 8.dp))
        }

        HorizontalDivider()

        if (entries.isEmpty()) {
            Text(
                text = "No files tracked yet",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(16.dp)
            )
        } else {
            val backed = entries.count { it.state == BackupState.UPLOADED }
            val pending = entries.count { it.state == BackupState.PENDING }
            val failed = entries.count { it.state == BackupState.FAILED }
            val proxied = entries.count { it.isProxied }
            val kept = entries.count { FilePin.isPinned(it.modeOverride) }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (backed > 0) Text(
                    "$backed backed up",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
                if (proxied > 0) Text(
                    "$proxied optimised",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.tertiary
                )
                if (kept > 0) Text(
                    stringResource(R.string.album_status_kept, kept),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (pending > 0) Text(
                    "$pending pending",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (failed > 0) Text(
                    "$failed failed",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
                Spacer(Modifier.weight(1f))
                HelpButton(HelpTopic.ALBUM_FILE_STATUS)
            }

            // Sorting only changes how the list is drawn. A dropdown, as in Settings (Ian, 18 Sept
            // 2026): the box shows the order in force and the menu offers the three.
            var sortMenuOpen by remember { mutableStateOf(false) }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.album_sort_label),
                    style = MaterialTheme.typography.bodyMedium
                )
                Box {
                    OutlinedButton(onClick = { sortMenuOpen = true }) {
                        Text(stringResource(sort.label()), maxLines = 1)
                    }
                    DropdownMenu(
                        expanded = sortMenuOpen,
                        onDismissRequest = { sortMenuOpen = false }
                    ) {
                        FileSort.entries.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(stringResource(option.label())) },
                                onClick = {
                                    sort = option
                                    sortMenuOpen = false
                                }
                            )
                        }
                    }
                }
            }

            // The heading of the tick column, with the (?) that says what the tick does. Only when
            // some file in this album has a box.
            if (restoredIds.isNotEmpty()) {
                // The label sits over the box column, on two lines, with the (?) to its left. The boxes
                // are 48dp wide with 4dp beside them, so their centre is 28dp from the edge: a 56dp
                // label with no end padding is centred on the same line. The Sort line has no (?):
                // Ian, 18 Sept 2026, the dropdown explains itself.
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    HelpButton(HelpTopic.ALBUM_FILE_PIN)
                    Text(
                        text = stringResource(R.string.album_keep_column),
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.width(56.dp)
                    )
                }
            }

            HorizontalDivider()

            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(shown, key = { it.id }) { entry ->
                    FileRow(
                        entry = entry,
                        context = context,
                        showKeepBox = entry.id in restoredIds,
                        onSetPinned = { pinned -> onSetPinned(entry, pinned) }
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun FileRow(
    entry: BackupEntryEntity,
    context: android.content.Context,
    showKeepBox: Boolean,
    onSetPinned: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 4.dp, top = 2.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = entry.displayName,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = buildString {
                    append(formatBytes(context, entry.sizeBytes))
                    if (entry.isVideo) append(" · video")
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            text = entry.statusLabel(),
            style = MaterialTheme.typography.bodySmall,
            color = entry.statusColor()
        )
        if (showKeepBox) {
            // Colours are the theme's own: a checkbox draws itself from the colour scheme, so it
            // reads in both themes without anything being set here.
            val description = stringResource(R.string.album_file_keep_description, entry.displayName)
            Checkbox(
                checked = FilePin.isPinned(entry.modeOverride),
                onCheckedChange = onSetPinned,
                modifier = Modifier.semantics { contentDescription = description }
            )
        } else {
            // Holds the box's width, so every row's status ends in the same place.
            Spacer(Modifier.size(48.dp))
        }
    }
}

private fun FileSort.label(): Int = when (this) {
    FileSort.NAME -> R.string.album_sort_name
    FileSort.DATE -> R.string.album_sort_date
    FileSort.STATUS -> R.string.album_sort_status
}

/**
 * Both facts, because optimised never replaces backed up.
 *
 * `isProxied` was tested first and won, so every optimised file read "✓ optimized" and nothing said
 * it was in the cloud — on precisely the files where that matters most, since a proxy is the case
 * where the full-resolution image exists *only* in OneDrive. Ian, 4 Sept 2026, reading a folder of
 * 100 rows: "all the files are labeled as optimized NOT backed up".
 *
 * A proxy is only ever written over a file the ledger has verified in the cloud, so the two are not
 * alternatives — an optimised file is a backed-up file that has also been shortened.
 */
@Composable
private fun BackupEntryEntity.statusLabel(): String = when {
    isProxied && state == BackupState.UPLOADED -> "✓ backed up · optimised"
    isProxied -> "✓ optimised"
    state == BackupState.UPLOADED -> "✓ backed up"
    state == BackupState.PENDING -> "⟳ pending"
    state == BackupState.FAILED -> "✗ failed"
    else -> "?"
}

/** Safe is one colour. Uploaded reads primary whether or not it was later optimised. */
@Composable
private fun BackupEntryEntity.statusColor() = when {
    state == BackupState.UPLOADED -> MaterialTheme.colorScheme.primary
    isProxied -> MaterialTheme.colorScheme.tertiary
    state == BackupState.FAILED -> MaterialTheme.colorScheme.error
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}

@Composable
private fun AlbumMode.label(): String = when (this) {
    AlbumMode.OFF -> "Off"
    AlbumMode.BACKUP -> "Backup"
    AlbumMode.SYNC -> "Sync"
    AlbumMode.ARCHIVE -> "Archive"
}
