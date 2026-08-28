package com.gallery.sync.ui.restore

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gallery.sync.R
import com.gallery.sync.ui.common.HeroCard
import com.gallery.sync.ui.common.HeroOutlinedButton
import com.gallery.sync.ui.common.SignalIcons
import com.gallery.sync.ui.common.formatBytes
import com.gallery.sync.ui.theme.LocalGallerySyncColors

/**
 * What on this phone has been shrunk, and putting it back.
 *
 * **Deliberately not a photo browser**, the same constraint the tab it replaces carried: no
 * thumbnails, no grid, no search, no sort. The list is local files this app made smaller, grouped
 * under the album each one lives in, because that is where the user will look for the result.
 */
@Composable
fun RestoreScreen(
    modifier: Modifier = Modifier,
    viewModel: RestoreViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // Re-read on entering the tab: an optimise run since the app started changes this list, and
    // there is no other moment the screen would learn about it.
    LaunchedEffect(Unit) { viewModel.refresh() }

    Column(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            HeroCard(
                label = stringResource(R.string.restore_hero_label),
                figure = if (state.loading) "—" else state.rows.size.toString(),
                figureFooter = if (state.hasSelection) {
                    {
                        Text(
                            text = stringResource(
                                R.string.restore_selected_summary,
                                state.selection.size,
                                formatBytes(context, state.reclaimableBytes)
                            ),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                } else {
                    null
                },
                detail = {
                    Text(
                        text = state.summary
                            ?: stringResource(
                                if (state.rows.isEmpty() && !state.loading) {
                                    R.string.restore_empty
                                } else {
                                    R.string.restore_intro
                                }
                            ),
                        style = MaterialTheme.typography.bodyMedium
                    )
                },
                actions = {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (state.rows.isNotEmpty() && !state.running) {
                            HeroOutlinedButton(
                                onClick = viewModel::selectAll,
                                label = stringResource(R.string.retrieve_select_all),
                                modifier = Modifier.weight(1f)
                            )
                        } else {
                            Spacer(modifier = Modifier.weight(1f))
                        }

                        if (state.hasSelection && !state.running) {
                            HeroOutlinedButton(
                                onClick = viewModel::clearSelection,
                                label = stringResource(R.string.retrieve_clear_selection),
                                modifier = Modifier.weight(1f)
                            )
                        } else {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            )

            if (state.loading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
        }

        HorizontalDivider()

        LazyColumn(
            modifier = Modifier.fillMaxWidth().weight(1f),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            state.byAlbum.forEach { (album, rows) ->
                item(key = "album-$album") {
                    Text(
                        text = album,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(top = 8.dp, bottom = 2.dp)
                    )
                }
                items(rows.size, key = { rows[it].id }) { index ->
                    val row = rows[index]
                    RestoreRowCard(
                        row = row,
                        selected = row.id in state.selection,
                        enabled = !state.running,
                        onToggle = { viewModel.toggle(row) }
                    )
                }
            }
        }

        // The one action, at the foot where a thumb is, and only once something is chosen.
        if (state.hasSelection || state.running) {
            RestoreBar(
                running = state.running,
                onRestore = viewModel::restoreSelected,
                onStop = viewModel::stop
            )
        }
    }
}

/**
 * One shrunken file: what it is now, what it would become, and how far along it is.
 *
 * Selected by tapping the row rather than a checkbox on it — the same decision the old tab made,
 * for the same reason: a control on the row competes with the filename for width.
 */
@Composable
private fun RestoreRowCard(
    row: RestoreRow,
    selected: Boolean,
    enabled: Boolean,
    onToggle: () -> Unit
) {
    val context = LocalContext.current

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surface
        },
        contentColor = if (selected) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onSurface
        },
        border = BorderStroke(
            if (selected) 2.dp else 1.dp,
            if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
        ),
        enabled = enabled,
        onClick = onToggle
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(text = row.displayName, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = stringResource(
                        R.string.restore_sizes,
                        formatBytes(context, row.localBytes),
                        formatBytes(context, row.fullBytes)
                    ),
                    style = MaterialTheme.typography.bodyMedium
                )

                when (val rowState = row.state) {
                    RowState.Waiting -> Unit

                    is RowState.Downloading -> {
                        Text(
                            text = stringResource(R.string.restore_downloading, rowState.percent),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        LinearProgressIndicator(
                            progress = { rowState.percent / 100f },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    RowState.Overwriting -> Text(
                        text = stringResource(R.string.restore_overwriting),
                        style = MaterialTheme.typography.bodyMedium
                    )

                    is RowState.Done -> Text(
                        text = stringResource(R.string.restore_done_row),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )

                    // Says the file is unchanged, because it is. Not softened into an apology: this
                    // is the sentence that tells the user a failure here costs them nothing.
                    is RowState.Failed -> Text(
                        text = stringResource(R.string.restore_failed_row, rowState.reason),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }

            if (selected) {
                Icon(
                    imageVector = SignalIcons.Check,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

/** The single action. A control while it runs, not a label — the same fix Albums and the old tab carry. */
@Composable
private fun RestoreBar(running: Boolean, onRestore: () -> Unit, onStop: () -> Unit) {
    val signal = LocalGallerySyncColors.current

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = if (running) onStop else onRestore,
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = signal.accent,
                    contentColor = signal.onAccent,
                    disabledContainerColor = LocalContentColor.current.copy(alpha = 0.14f),
                    disabledContentColor = LocalContentColor.current.copy(alpha = 0.55f)
                )
            ) {
                Text(
                    text = stringResource(
                        if (running) R.string.retrieve_stop else R.string.retrieve_action
                    ),
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1
                )
            }
        }
    }
}
