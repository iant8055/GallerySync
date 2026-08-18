package com.gallery.sync.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gallery.sync.R
import com.gallery.sync.ui.backup.BackupViewModel
import com.gallery.sync.ui.common.formatBytes
import kotlinx.coroutines.launch

/**
 * Everything you set once and stop thinking about.
 *
 * Split out because these controls were crowding the album list off the Backup screen — and album
 * selection is the thing actually used day to day, while these are touched once.
 */
@Composable
fun SettingsScreen(
    accountName: String?,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: BackupViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val moveLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { viewModel.onMoveToBackupFinished() }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {

        Section(stringResource(R.string.settings_account)) {
            accountName?.let {
                Text(it, style = MaterialTheme.typography.bodyLarge)
            }
            OutlinedButton(onClick = onSignOut) {
                Text(stringResource(R.string.sign_out_action))
            }
        }

        HorizontalDivider()

        Section(stringResource(R.string.settings_automatic)) {
            SettingSwitch(
                label = stringResource(R.string.backup_automatic),
                detail = stringResource(
                    if (state.isAutomaticEnabled) {
                        R.string.backup_automatic_on
                    } else {
                        R.string.backup_automatic_off
                    }
                ),
                checked = state.isAutomaticEnabled,
                onCheckedChange = viewModel::setAutomaticEnabled
            )

            if (state.isAutomaticEnabled) {
                SettingSwitch(
                    label = stringResource(R.string.backup_allow_metered),
                    detail = stringResource(
                        if (state.allowMeteredNetwork) {
                            R.string.backup_allow_metered_on
                        } else {
                            R.string.backup_allow_metered_off
                        }
                    ),
                    checked = state.allowMeteredNetwork,
                    onCheckedChange = viewModel::setAllowMeteredNetwork
                )
            }
        }

        HorizontalDivider()

        Section(stringResource(R.string.settings_storage)) {
            Text(
                text = pluralStringResource(
                    R.plurals.backup_total_stored,
                    state.uploadedCount,
                    state.uploadedCount
                ),
                style = MaterialTheme.typography.bodyMedium
            )

            when {
                !state.canRemoveLocalCopies -> Text(
                    text = stringResource(R.string.backup_move_unsupported),
                    style = MaterialTheme.typography.bodySmall
                )

                state.redundantCount == 0 -> Text(
                    text = stringResource(R.string.backup_nothing_redundant),
                    style = MaterialTheme.typography.bodySmall
                )

                else -> {
                    Text(
                        text = pluralStringResource(
                            R.plurals.backup_move_explainer,
                            state.redundantCount,
                            state.redundantCount
                        ),
                        style = MaterialTheme.typography.bodySmall
                    )
                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                viewModel.buildMoveToBackupRequest()?.let {
                                    moveLauncher.launch(IntentSenderRequest.Builder(it).build())
                                }
                            }
                        }
                    ) {
                        Text(
                            stringResource(
                                R.string.backup_move_to_backup,
                                formatBytes(context, state.redundantBytes)
                            )
                        )
                    }
                    Text(
                        text = stringResource(R.string.backup_move_trash_note),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        content()
    }
}

@Composable
private fun SettingSwitch(
    label: String,
    detail: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            Text(detail, style = MaterialTheme.typography.bodySmall)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
