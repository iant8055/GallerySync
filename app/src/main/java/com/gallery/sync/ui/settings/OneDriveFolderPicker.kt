package com.gallery.sync.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gallery.sync.R
import com.gallery.sync.ui.common.SignalIcons

/**
 * Browses OneDrive so the backup destination can be picked instead of typed.
 *
 * Deliberately shaped like the SAF picker the wizard shows for local folders — descend, then
 * confirm the folder you are standing in — so the two read as one idea rather than two unrelated
 * chores.
 *
 * Folders only. Files are not shown because a destination cannot be one, and listing them would
 * bury the folders inside a camera roll.
 */
@Composable
fun OneDriveFolderPicker(
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
    viewModel: FolderPickerViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var naming by rememberSaveable { mutableStateOf(false) }
    var typedName by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(Unit) { viewModel.openAtRoot() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.picker_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {

                Breadcrumbs(state = state, onUpTo = viewModel::upTo)

                when {
                    state.error != null -> {
                        Text(
                            text = stringResource(R.string.picker_failed),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                        TextButton(onClick = { viewModel.upTo(state.crumbs.size) }) {
                            Text(stringResource(R.string.picker_retry))
                        }
                    }

                    state.folders.isEmpty() && state.loading ->
                        CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)

                    state.folders.isEmpty() && state.moreToken == null ->
                        Text(
                            text = stringResource(R.string.picker_empty),
                            style = MaterialTheme.typography.bodySmall
                        )
                }

                if (state.folders.isNotEmpty()) {
                    LazyColumn(modifier = Modifier.heightIn(max = 240.dp)) {
                        items(state.folders, key = { it.id }) { folder ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { viewModel.enter(folder) }
                                    .padding(vertical = 10.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = SignalIcons.Albums,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(folder.name, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }

                // A page can come back holding only files, so "there is more" and "nothing here"
                // are both true at once until it is followed. Saying so beats an empty list that
                // is a lie.
                if (state.moreToken != null && !state.loading) {
                    TextButton(onClick = viewModel::loadMore) {
                        Text(stringResource(R.string.picker_show_more))
                    }
                }
                if (state.loading && state.folders.isNotEmpty()) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                }

                if (naming) {
                    OutlinedTextField(
                        value = typedName,
                        onValueChange = {
                            typedName = it
                            viewModel.dismissCreateError()
                        },
                        label = { Text(stringResource(R.string.picker_new_folder_name)) },
                        singleLine = true,
                        isError = state.createFailed,
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(min = 0.dp)
                    )
                    if (state.createFailed) {
                        Text(
                            text = stringResource(R.string.picker_create_failed),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    TextButton(
                        enabled = typedName.isNotBlank() && !state.creating,
                        onClick = {
                            viewModel.createFolder(typedName)
                            typedName = ""
                            naming = false
                        }
                    ) {
                        Text(stringResource(R.string.picker_create))
                    }
                } else {
                    TextButton(onClick = { naming = true }) {
                        Text(stringResource(R.string.picker_new_folder))
                    }
                }
            }
        },
        confirmButton = {
            // Disabled at the root: an empty path is not a valid destination, and "all of OneDrive"
            // is not something the backup can be pointed at.
            TextButton(
                enabled = !state.atRoot,
                onClick = { onPick(state.path) }
            ) {
                Text(stringResource(R.string.picker_use), maxLines = 1)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.destination_cancel), maxLines = 1)
            }
        }
    )
}

/** `OneDrive > Pictures > 2026`, each level tappable to jump back to it. */
@Composable
private fun Breadcrumbs(
    state: FolderPickerUiState,
    onUpTo: (Int) -> Unit
) {
    // The path scrolls rather than wrapping. A dialog is narrow and a drive path is not bounded,
    // so wrapping broke folder names mid-word — "DCIM" rendered as "DCI / M" — and got worse with
    // every level. Scrolled to the end on each move, because the folder you are standing in is the
    // one you need to see.
    val scroll = rememberScrollState()
    LaunchedEffect(state.crumbs.size) { scroll.animateScrollTo(scroll.maxValue) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(scroll),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Up one level. The crumbs already allow jumping anywhere above, but the common move is
        // "back out of the folder I just opened", and hunting for the right crumb to hit is a
        // worse way to do it than the control every file browser has.
        IconButton(
            onClick = { onUpTo(state.crumbs.size - 1) },
            enabled = !state.atRoot,
            modifier = Modifier.size(32.dp)
        ) {
            Icon(
                imageVector = SignalIcons.Back,
                contentDescription = stringResource(R.string.picker_back),
                modifier = Modifier.size(20.dp)
            )
        }
        Text(
            text = stringResource(R.string.picker_root),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (state.atRoot) FontWeight.Bold else FontWeight.Normal,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.clickable(enabled = !state.atRoot) { onUpTo(0) }
        )
        state.crumbs.forEachIndexed { index, crumb ->
            val last = index == state.crumbs.lastIndex
            Text(">", style = MaterialTheme.typography.labelLarge, maxLines = 1)
            Text(
                text = crumb.name,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (last) FontWeight.Bold else FontWeight.Normal,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier.clickable(enabled = !last) { onUpTo(index + 1) }
            )
        }
    }
}
