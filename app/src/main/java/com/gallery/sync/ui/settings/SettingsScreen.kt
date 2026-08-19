package com.gallery.sync.ui.settings

import android.app.Activity
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
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
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
import com.gallery.sync.ui.backup.ProxyStatus
import com.gallery.sync.data.local.settings.ThemeMode
import com.gallery.sync.ui.common.OneDriveLauncher
import com.gallery.sync.ui.theme.ThemeViewModel
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
    viewModel: BackupViewModel = hiltViewModel(),
    themeViewModel: ThemeViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val moveLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { viewModel.onMoveToBackupFinished() }

    val proxyLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        // Only rewrite anything if Android reports the user actually granted it. Treating a
        // dismissed dialog as consent would overwrite photos nobody agreed to.
        if (result.resultCode == Activity.RESULT_OK) viewModel.onProxyConsentGranted()
    }

    // With automatic optimising on, offer the consent dialog when there is something to do rather
    // than waiting to be found in Settings. Once per visit: Android needs a tap for every batch, so
    // re-raising a dialog the user dismissed would be nagging rather than helping.
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

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {

        Section(stringResource(R.string.settings_appearance)) {
            val themeMode by themeViewModel.themeMode.collectAsStateWithLifecycle()
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                ThemeMode.entries.forEachIndexed { index, mode ->
                    SegmentedButton(
                        selected = themeMode == mode,
                        onClick = { themeViewModel.setThemeMode(mode) },
                        shape = SegmentedButtonDefaults.itemShape(index, ThemeMode.entries.size)
                    ) {
                        Text(
                            stringResource(
                                when (mode) {
                                    ThemeMode.SYSTEM -> R.string.theme_system
                                    ThemeMode.LIGHT -> R.string.theme_light
                                    ThemeMode.DARK -> R.string.theme_dark
                                }
                            )
                        )
                    }
                }
            }
        }

        HorizontalDivider()

        Section(stringResource(R.string.settings_account)) {
            accountName?.let {
                Text(it, style = MaterialTheme.typography.bodyLarge)
            }
            OutlinedButton(onClick = onSignOut) {
                Text(stringResource(R.string.sign_out_action))
            }
        }

        HorizontalDivider()

        Section(stringResource(R.string.settings_cloud_files)) {
            Text(
                text = stringResource(R.string.settings_open_onedrive_detail),
                style = MaterialTheme.typography.bodySmall
            )
            OutlinedButton(onClick = { OneDriveLauncher.open(context) }) {
                Text(stringResource(R.string.settings_open_onedrive))
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

        Section(stringResource(R.string.settings_optimise)) {
            if (state.canProxy) {
                SettingSwitch(
                    label = stringResource(R.string.settings_auto_optimise),
                    detail = stringResource(
                        if (state.isAutoOptimiseEnabled) {
                            R.string.settings_auto_optimise_on
                        } else {
                            R.string.settings_auto_optimise_off
                        }
                    ),
                    checked = state.isAutoOptimiseEnabled,
                    onCheckedChange = viewModel::setAutoOptimiseEnabled
                )
            }

            // What optimising actually does, said before any of the state messages. Without it the
            // section is a switch and a status line for a word nobody has defined.
            Text(
                text = stringResource(R.string.settings_optimise_explainer),
                style = MaterialTheme.typography.bodySmall
            )

            when {
                !state.canProxy -> Text(
                    text = stringResource(R.string.proxy_unsupported),
                    style = MaterialTheme.typography.bodySmall
                )

                // Two very different reasons for having nothing to do, and the difference is the
                // only actionable thing on this screen: one needs a sync run first, the other is
                // finished. "Nothing to optimise" alone reads as a broken button.
                state.proxyCandidateCount == 0 -> Text(
                    text = stringResource(
                        if (state.uploadedCount == 0) {
                            R.string.proxy_none_nothing_synced
                        } else {
                            R.string.proxy_none_all_done
                        }
                    ),
                    style = MaterialTheme.typography.bodySmall
                )

                else -> {
                    Text(
                        text = pluralStringResource(
                            R.plurals.proxy_explainer,
                            state.proxyCandidateCount,
                            state.proxyCandidateCount
                        ),
                        style = MaterialTheme.typography.bodySmall
                    )
                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                viewModel.buildProxyWriteRequest()?.let {
                                    proxyLauncher.launch(IntentSenderRequest.Builder(it).build())
                                }
                            }
                        }
                    ) {
                        Text(
                            stringResource(
                                R.string.proxy_action,
                                formatBytes(context, state.proxyCandidateBytes)
                            )
                        )
                    }
                    // Said out loud because their absence would otherwise read as a bug.
                    Text(
                        text = stringResource(R.string.proxy_videos_excluded),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            state.proxyStatus?.let { status ->
                Text(
                    text = when (status) {
                        ProxyStatus.Working -> stringResource(R.string.proxy_working)

                        is ProxyStatus.Done -> stringResource(
                            R.string.proxy_done,
                            status.proxiedCount,
                            formatBytes(context, status.bytesReclaimed)
                        )

                        is ProxyStatus.Stopped -> stringResource(
                            R.string.proxy_stopped,
                            status.proxiedCount,
                            status.failedFile,
                            status.reason
                        )

                        ProxyStatus.CouldNotAsk -> stringResource(R.string.proxy_could_not_ask)
                    },
                    style = MaterialTheme.typography.bodyMedium
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
