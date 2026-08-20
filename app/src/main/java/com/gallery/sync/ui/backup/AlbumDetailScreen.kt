package com.gallery.sync.ui.backup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.gallery.sync.data.local.entity.AlbumMode
import com.gallery.sync.data.local.entity.BackupEntryEntity
import com.gallery.sync.data.local.entity.BackupState
import com.gallery.sync.ui.common.formatBytes

@Composable
fun AlbumDetailScreen(
    albumName: String,
    mode: AlbumMode,
    entries: List<BackupEntryEntity>,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onBack) {
                Text("←", style = MaterialTheme.typography.titleMedium)
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

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (backed > 0) Text(
                    "$backed backed up",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
                if (proxied > 0) Text(
                    "$proxied optimized",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.tertiary
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
            }

            HorizontalDivider()

            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(entries, key = { it.id }) { entry ->
                    FileRow(entry = entry, context = context)
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun FileRow(entry: BackupEntryEntity, context: android.content.Context) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
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
    }
}

@Composable
private fun BackupEntryEntity.statusLabel(): String = when {
    isProxied -> "✓ optimized"
    state == BackupState.UPLOADED -> "✓ backed up"
    state == BackupState.PENDING -> "⟳ pending"
    state == BackupState.FAILED -> "✗ failed"
    else -> "?"
}

@Composable
private fun BackupEntryEntity.statusColor() = when {
    isProxied -> MaterialTheme.colorScheme.tertiary
    state == BackupState.UPLOADED -> MaterialTheme.colorScheme.primary
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
