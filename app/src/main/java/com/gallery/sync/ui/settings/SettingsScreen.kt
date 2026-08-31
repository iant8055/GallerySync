package com.gallery.sync.ui.settings

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RichTooltip
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
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
import com.gallery.sync.data.local.entity.AlbumMode
import com.gallery.sync.data.local.settings.ThemeMode
import com.gallery.sync.domain.backup.MediaAge
import com.gallery.sync.domain.backup.OptimiseMode
import com.gallery.sync.domain.backup.VideoQuality
import com.gallery.sync.ui.backup.BackupUiState
import com.gallery.sync.ui.backup.BackupViewModel
import com.gallery.sync.ui.backup.ProxyStatus
import com.gallery.sync.ui.common.LabelWithAction
import com.gallery.sync.ui.common.SignalIcons
import com.gallery.sync.ui.common.formatBytes
import com.gallery.sync.ui.retrieve.DeletionSection
import com.gallery.sync.ui.theme.ThemeViewModel
import kotlinx.coroutines.launch

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

    val proxyLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) viewModel.onProxyConsentGranted()
    }

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
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {

        // ── General ──────────────────────────────────────────────────────────
        SectionHeader(
            stringResource(R.string.settings_general),
            helpText = stringResource(R.string.help_general)
        )

        Text(
            text = stringResource(R.string.settings_language),
            style = MaterialTheme.typography.bodyLarge
        )
        Text(
            text = stringResource(R.string.settings_language_detail),
            style = MaterialTheme.typography.bodySmall
        )

        val themeMode by themeViewModel.themeMode.collectAsStateWithLifecycle()
        SettingDropdown(
            label = stringResource(R.string.settings_appearance),
            options = ThemeMode.entries,
            selected = themeMode,
            onSelected = { themeViewModel.setThemeMode(it) },
            optionLabel = { mode ->
                stringResource(
                    when (mode) {
                        ThemeMode.SYSTEM -> R.string.theme_system
                        ThemeMode.LIGHT -> R.string.theme_light
                        ThemeMode.DARK -> R.string.theme_dark
                    }
                )
            }
        )

        SettingSwitch(
            label = stringResource(R.string.backup_allow_metered),
            detail = stringResource(
                if (state.allowMeteredNetwork) R.string.backup_allow_metered_on
                else R.string.backup_allow_metered_off
            ),
            checked = state.allowMeteredNetwork,
            onCheckedChange = viewModel::setAllowMeteredNetwork
        )

        HorizontalDivider()

        // ── Albums ───────────────────────────────────────────────────────────
        SectionHeader(
            stringResource(R.string.settings_albums),
            helpText = stringResource(R.string.help_albums)
        )

        SourcesSection()

        DeletionSection()

        SettingDropdown(
            label = stringResource(R.string.settings_default_mode),
            options = AlbumMode.canBeDefault,
            selected = state.defaultAlbumMode,
            onSelected = viewModel::setDefaultAlbumMode,
            optionLabel = { it.settingsLabel() }
        )

        HorizontalDivider()

        // ── Backup ───────────────────────────────────────────────────────────
        SectionHeader(
            stringResource(R.string.settings_backup),
            helpText = stringResource(R.string.help_backup)
        )

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

        DestinationSection()

        // ── Sync ─────────────────────────────────────────────────────────────
        SectionHeader(
            stringResource(R.string.settings_sync),
            helpText = stringResource(R.string.help_sync)
        )

        SettingSwitch(
            label = stringResource(R.string.settings_optimise_photos),
            detail = if (state.optimisePhotos) stringResource(R.string.settings_optimise_photos_on)
                else null,
            checked = state.optimisePhotos,
            onCheckedChange = { enabled ->
                viewModel.setOptimisePhotos(enabled)
                if (enabled) viewModel.setOptimiseEnabled(true)
                else if (!state.optimiseVideo) viewModel.setOptimiseEnabled(false)
            }
        )

        AnimatedVisibility(
            visible = state.optimisePhotos,
            enter = expandVertically(),
            exit = shrinkVertically()
        ) {
            Column(
                modifier = Modifier.padding(start = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                SettingDropdown(
                    label = stringResource(R.string.settings_optimise_mode),
                    options = OptimiseMode.entries,
                    selected = state.photoOptimiseMode,
                    onSelected = viewModel::setPhotoOptimiseMode,
                    optionLabel = { mode ->
                        stringResource(
                            when (mode) {
                                OptimiseMode.Auto -> R.string.settings_optimise_mode_auto
                                OptimiseMode.Manual -> R.string.settings_optimise_mode_manual
                            }
                        )
                    }
                )

                SettingDropdown(
                    label = stringResource(R.string.settings_older_than),
                    options = MediaAge.entries,
                    selected = state.photoOptimiseAge,
                    onSelected = viewModel::setPhotoOptimiseAge,
                    optionLabel = { age -> age.label() }
                )
            }
        }

        SettingSwitch(
            label = stringResource(R.string.settings_optimise_videos),
            checked = state.optimiseVideo,
            onCheckedChange = { enabled ->
                viewModel.setOptimiseVideo(enabled)
                if (enabled) viewModel.setOptimiseEnabled(true)
                else if (!state.optimisePhotos) viewModel.setOptimiseEnabled(false)
            }
        )

        AnimatedVisibility(
            visible = state.optimiseVideo,
            enter = expandVertically(),
            exit = shrinkVertically()
        ) {
            Column(
                modifier = Modifier.padding(start = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                SettingDropdown(
                    label = stringResource(R.string.settings_optimise_mode),
                    options = OptimiseMode.entries,
                    selected = state.videoOptimiseMode,
                    onSelected = viewModel::setVideoOptimiseMode,
                    optionLabel = { mode ->
                        stringResource(
                            when (mode) {
                                OptimiseMode.Auto -> R.string.settings_optimise_mode_auto
                                OptimiseMode.Manual -> R.string.settings_optimise_mode_manual
                            }
                        )
                    }
                )

                SettingDropdown(
                    label = stringResource(R.string.settings_older_than),
                    options = MediaAge.entries,
                    selected = state.videoOptimiseAge,
                    onSelected = viewModel::setVideoOptimiseAge,
                    optionLabel = { age -> age.label() }
                )

                SettingDropdown(
                    label = stringResource(R.string.settings_quality),
                    options = VideoQuality.entries,
                    selected = state.videoQuality,
                    onSelected = viewModel::setVideoQuality,
                    optionLabel = { quality ->
                        when (quality) {
                            VideoQuality.High -> stringResource(R.string.video_quality_high)
                            VideoQuality.Medium -> stringResource(R.string.video_quality_medium)
                            VideoQuality.Low -> stringResource(R.string.video_quality_low)
                        }
                    }
                )
            }
        }

        if (state.optimisePhotos || state.optimiseVideo) {
            if (state.canProxy) {
                OptimiseStatusAndAction(
                    state = state,
                    viewModel = viewModel,
                    proxyLauncher = {
                        scope.launch {
                            viewModel.buildProxyWriteRequest()?.let {
                                proxyLauncher.launch(IntentSenderRequest.Builder(it).build())
                            }
                        }
                    },
                    context = context
                )
            } else {
                Text(
                    text = stringResource(R.string.proxy_unsupported),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        HorizontalDivider()

        // ── Restore ──────────────────────────────────────────────────────────
        SectionHeader(
            stringResource(R.string.settings_restore),
            helpText = stringResource(R.string.help_restore)
        )

        SettingSwitch(
            label = stringResource(R.string.settings_show_empty_folders),
            checked = state.showEmptyCloudFolders,
            onCheckedChange = viewModel::setShowEmptyCloudFolders
        )

        HorizontalDivider()

        // ── Archive ──────────────────────────────────────────────────────────
        SectionHeader(
            stringResource(R.string.settings_archive),
            helpText = stringResource(R.string.help_archive)
        )

        Text(
            text = stringResource(R.string.settings_language_detail),
            style = MaterialTheme.typography.bodySmall
        )

        HorizontalDivider()

        // ── Restart Setup Wizard ─────────────────────────────────────────────
        if (!state.hasCompletedFirstBackup) {
            Text(
                text = stringResource(R.string.settings_setup),
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = stringResource(R.string.settings_setup_detail),
                style = MaterialTheme.typography.bodySmall
            )
            OutlinedButton(onClick = viewModel::restartSetup) {
                Text(stringResource(R.string.settings_setup_action))
            }
        }
    }
}

// ── Proxy status and action ─────────────────────────────────────────────────

@Composable
private fun OptimiseStatusAndAction(
    state: BackupUiState,
    viewModel: BackupViewModel,
    proxyLauncher: () -> Unit,
    context: android.content.Context
) {
    SettingSwitch(
        label = stringResource(R.string.settings_auto_optimise),
        detail = stringResource(
            if (state.isAutoOptimiseEnabled) R.string.settings_auto_optimise_on
            else R.string.settings_auto_optimise_off
        ),
        checked = state.isAutoOptimiseEnabled,
        onCheckedChange = viewModel::setAutoOptimiseEnabled
    )

    when {
        state.proxyCandidateCount == 0 -> Text(
            text = stringResource(
                if (state.uploadedCount == 0) R.string.proxy_none_nothing_synced
                else R.string.proxy_none_all_done
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
            OutlinedButton(onClick = { proxyLauncher() }) {
                Text(
                    stringResource(
                        R.string.proxy_action,
                        formatBytes(context, state.proxyCandidateBytes)
                    )
                )
            }
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

// ── Layout primitives ───────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SectionHeader(title: String, helpText: String? = null) {
    if (helpText != null) {
        val tooltipState = rememberTooltipState(isPersistent = true)
        val scope = rememberCoroutineScope()

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.primary
            )
            TooltipBox(
                positionProvider = TooltipDefaults.rememberTooltipPositionProvider(),
                tooltip = { RichTooltip { Text(helpText) } },
                state = tooltipState
            ) {
                IconButton(onClick = { scope.launch { tooltipState.show() } }) {
                    Icon(
                        imageVector = SignalIcons.Help,
                        contentDescription = title,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    } else {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun SettingSwitch(
    label: String,
    detail: String? = null,
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
            if (detail != null) {
                Text(detail, style = MaterialTheme.typography.bodySmall)
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                uncheckedBorderColor = MaterialTheme.colorScheme.onSurfaceVariant
            )
        )
    }
}

@Composable
private fun <T> SettingDropdown(
    label: String,
    options: List<T>,
    selected: T,
    onSelected: (T) -> Unit,
    optionLabel: @Composable (T) -> String
) {
    var expanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f)
        )
        Box {
            OutlinedButton(onClick = { expanded = true }) {
                Text(optionLabel(selected), maxLines = 1)
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                options.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(optionLabel(option)) },
                        onClick = {
                            onSelected(option)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun MediaAge.label(): String = when (this) {
    MediaAge.Immediately -> stringResource(R.string.media_age_immediately)
    MediaAge.OneHour -> stringResource(R.string.media_age_one_hour)
    MediaAge.TwelveHours -> stringResource(R.string.media_age_twelve_hours)
    MediaAge.OneDay -> stringResource(R.string.media_age_one_day)
    MediaAge.OneWeek -> stringResource(R.string.media_age_one_week)
}

@Composable
private fun AlbumMode.settingsLabel(): String = when (this) {
    AlbumMode.OFF -> stringResource(R.string.mode_off)
    AlbumMode.BACKUP -> stringResource(R.string.mode_backup)
    AlbumMode.SYNC -> stringResource(R.string.mode_sync)
    AlbumMode.ARCHIVE -> stringResource(R.string.mode_archive)
}
