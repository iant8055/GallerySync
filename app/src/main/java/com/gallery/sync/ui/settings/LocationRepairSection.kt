package com.gallery.sync.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gallery.sync.R
import com.gallery.sync.ui.common.formatBytes

/**
 * Settings → Backup → *Photo locations*: the offer to repair copies sent without their location (Ian, 9 Oct 2026).
 * Shown only when there is something it could do, or while and after it runs. Nothing is sent until the user
 * says Yes in the dialog. See `LocationRepair`.
 */
@Composable
internal fun LocationRepairSection(viewModel: LocationRepairViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    if (!state.loaded || !state.available) return
    if (state.candidates == 0 && !state.running && !state.finished) return

    val context = LocalContext.current
    var asking by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(text = stringResource(R.string.repair_title), style = MaterialTheme.typography.titleSmall)
        Text(
            text = stringResource(R.string.repair_body),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        when {
            state.running -> Text(
                text = stringResource(R.string.repair_running, state.checked, state.candidates, state.repaired),
                style = MaterialTheme.typography.bodyMedium
            )
            state.finished -> Text(
                text = stringResource(R.string.repair_finished, state.repaired, state.noLocation),
                style = MaterialTheme.typography.bodyMedium
            )
        }
        if (!state.running && state.candidates > 0) {
            Button(onClick = { asking = true }, modifier = Modifier.fillMaxWidth()) {
                Text(
                    stringResource(
                        R.string.repair_button, state.candidates, formatBytes(context, state.candidateBytes)
                    )
                )
            }
        }
    }

    if (asking) {
        AlertDialog(
            onDismissRequest = { asking = false },
            title = { Text(stringResource(R.string.repair_confirm_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.repair_confirm_body, state.candidates, formatBytes(context, state.candidateBytes)
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    asking = false
                    viewModel.start()
                }) { Text(stringResource(R.string.repair_confirm_yes)) }
            },
            dismissButton = {
                TextButton(onClick = { asking = false }) { Text(stringResource(R.string.repair_confirm_no)) }
            }
        )
    }
}
