package com.gallery.sync.ui.settings

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
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
import com.gallery.sync.ui.retrieve.DeletionSection
import com.gallery.sync.data.local.entity.AlbumMode
import com.gallery.sync.ui.backup.BackupViewModel
import com.gallery.sync.ui.backup.ProxyStatus
import com.gallery.sync.data.local.settings.ThemeMode
import com.gallery.sync.ui.common.LabelWithAction
import com.gallery.sync.ui.common.SingleChoiceControl
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
            SingleChoiceControl(
                options = ThemeMode.entries,
                selected = themeMode,
                onSelected = { themeViewModel.setThemeMode(it) },
                label = { mode ->
                    stringResource(
                        when (mode) {
                            ThemeMode.SYSTEM -> R.string.theme_system
                            ThemeMode.LIGHT -> R.string.theme_light
                            ThemeMode.DARK -> R.string.theme_dark
                        }
                    )
                }
            )
        }

        HorizontalDivider()

        // Text left, action right, on one row — the same shape as the switches below, and it
            // keeps the button level with what it acts on instead of stranded beneath it.
        Section(stringResource(R.string.settings_account)) {
            LabelWithAction(
                action = {
                    OutlinedButton(onClick = onSignOut) {
                        Text(stringResource(R.string.sign_out_action))
                    }
                }
            ) {
                accountName?.let {
                    Text(it, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }

        HorizontalDivider()

        // Placed above everything that depends on it. Nothing in this app scans, backs up or
        // optimises outside a granted tree, so this is the choice the rest of the screen rests on.
        Section(stringResource(R.string.settings_sources)) {
            SourcesSection()
        }

        HorizontalDivider()

        Section(stringResource(R.string.settings_cloud_files)) {
            LabelWithAction(
                action = {
                    OutlinedButton(onClick = { OneDriveLauncher.open(context) }) {
                        Text(stringResource(R.string.settings_open_onedrive))
                    }
                }
            ) {
                Text(
                    text = stringResource(R.string.settings_open_onedrive_detail),
                    style = MaterialTheme.typography.bodySmall
                )
            }

            SettingSwitch(
                label = stringResource(R.string.settings_show_empty_folders),
                detail = stringResource(
                    if (state.showEmptyCloudFolders) {
                        R.string.settings_show_empty_folders_on
                    } else {
                        R.string.settings_show_empty_folders_off
                    }
                ),
                checked = state.showEmptyCloudFolders,
                onCheckedChange = viewModel::setShowEmptyCloudFolders
            )
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

        Section(stringResource(R.string.settings_default_mode)) {
            Text(
                text = stringResource(R.string.settings_default_mode_detail),
                style = MaterialTheme.typography.bodySmall
            )
            DefaultModeSelector(
                current = state.defaultAlbumMode,
                onModeSelected = viewModel::setDefaultAlbumMode
            )
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

        // Last, deliberately. This is the only setting on the screen that can cause a file to leave
        // OneDrive, and it is read rather than skimmed when it is not competing with a toggle.
        Section(stringResource(R.string.settings_deletion)) {
            DeletionSection()
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

                    // What the live re-check refused to include, and why. Reported rather than
                    // quietly dropped: "OneDrive no longer has this" means the file is not backed
                    // up at all, which the user needs to know, and "could not check" means the app
                    // declined to guess — a different thing again, and worth saying so.
                    state.removalHeldBack?.let { held ->
                        if (held.confirmed.isEmpty() && held.heldBack > 0) {
                            Text(
                                text = stringResource(R.string.removal_none_confirmed),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                        if (held.missing.isNotEmpty()) {
                            Text(
                                text = stringResource(
                                    R.string.removal_held_missing,
                                    pluralStringResource(
                                        R.plurals.file_count,
                                        held.missing.size,
                                        held.missing.size
                                    )
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                        if (held.unconfirmed.isNotEmpty()) {
                            Text(
                                text = stringResource(
                                    R.string.removal_held_unchecked,
                                    pluralStringResource(
                                        R.plurals.file_count,
                                        held.unconfirmed.size,
                                        held.unconfirmed.size
                                    )
                                ),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    // Full width so a child asking for Alignment.End lands at the edge of the screen. Without it
    // the column is only as wide as its widest child, and "end" means the end of whatever text
    // happens to be longest — which put Sign out under the email rather than against the margin.
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        content()
    }
}

@Composable
private fun DefaultModeSelector(
    current: AlbumMode,
    onModeSelected: (AlbumMode) -> Unit
) {
    SingleChoiceControl(
        options = AlbumMode.canBeDefault,
        selected = current,
        onSelected = onModeSelected,
        label = { it.settingsLabel() }
    )
}

@Composable
private fun AlbumMode.settingsLabel(): String = when (this) {
    AlbumMode.OFF -> stringResource(R.string.mode_off)
    AlbumMode.BACKUP -> stringResource(R.string.mode_backup)
    AlbumMode.SYNC -> stringResource(R.string.mode_sync)
    AlbumMode.ARCHIVE -> stringResource(R.string.mode_archive)
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
