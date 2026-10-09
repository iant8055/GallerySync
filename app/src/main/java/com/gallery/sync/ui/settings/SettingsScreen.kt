package com.gallery.sync.ui.settings

import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gallery.sync.R
import com.gallery.sync.ui.common.labelRes
import com.gallery.sync.data.local.settings.ThemeMode
import com.gallery.sync.domain.backup.ArchiveAge
import com.gallery.sync.domain.backup.BackupLocation
import com.gallery.sync.domain.backup.CameraAlbum
import com.gallery.sync.domain.backup.CameraOptimiseAge
import com.gallery.sync.domain.backup.MediaAge
import com.gallery.sync.domain.backup.OptimiseMode
import com.gallery.sync.domain.backup.VideoQuality
import com.gallery.sync.ui.backup.BackupViewModel
import com.gallery.sync.ui.help.HelpButton
import com.gallery.sync.ui.help.HelpTopic
import com.gallery.sync.ui.help.WithHelp
import com.gallery.sync.ui.retrieve.DeletionSection
import com.gallery.sync.ui.theme.LocalGallerySyncColors
import com.gallery.sync.ui.theme.ThemeViewModel
import com.gallery.sync.worker.ArchiveNotifyPermission
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    accountName: String?,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: BackupViewModel = hiltViewModel(),
    themeViewModel: ThemeViewModel = hiltViewModel(),
    cloudViewModel: CloudProvidersViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val cloudState by cloudViewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var page by remember { mutableStateOf<SupportPage?>(null) }
    var showContact by remember { mutableStateOf(false) }
    var showBugReport by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = SettingsGutter, end = SettingsGutter, top = 16.dp, bottom = 80.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {

        // ── General ──────────────────────────────────────────────────────────
        SectionHeader(
            stringResource(R.string.settings_general),
            help = HelpTopic.SETTINGS_SECTION_GENERAL
        )

        // First in General (Ian, 18 Sept 2026). The setup tour's Help card rings the mock of this card,
        // so the two move together. Language used to follow it and now sits at the foot of the page.
        LinkCard(
            title = stringResource(R.string.settings_how_to_guide),
            detail = stringResource(R.string.settings_how_to_guide_detail),
            onClick = { page = SupportPage.HOW_TO_GUIDE }
        )

        SettingDivider()

        val themeMode by themeViewModel.themeMode.collectAsStateWithLifecycle()
        SettingDropdown(
            label = stringResource(R.string.settings_appearance),
            help = HelpTopic.SETTINGS_APPEARANCE,
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

        SettingDivider()

        SettingSwitch(
            label = stringResource(R.string.backup_allow_metered),
            help = HelpTopic.SETTINGS_MOBILE_DATA,
            detail = stringResource(
                if (state.allowMeteredNetwork) R.string.backup_allow_metered_on
                else R.string.backup_allow_metered_off
            ),
            checked = state.allowMeteredNetwork,
            onCheckedChange = viewModel::setAllowMeteredNetwork
        )

        // ── Backup ───────────────────────────────────────────────────────────
        SectionHeader(
            stringResource(R.string.settings_backup),
            help = HelpTopic.SETTINGS_SECTION_BACKUP
        )

        // The free tier is ONE cloud, the user's own (Ian, 24 Sept 2026), so there is no locked-on OneDrive
        // box: OneDrive is one choice among the others.
        // The clouds come first (Ian, 24 Sept 2026): which clouds are connected is what the pairings below
        // choose between. The account itself is named on its row in the list (no separate Account row), and
        // there is no separate "Current folder location" section either: each pairing below already says
        // where in its cloud the folder goes. `DestinationSection` is kept, unused, for the OneDrive folder
        // chooser if it is wanted back.
        CloudProvidersSection(viewModel = cloudViewModel)

        // A clear boundary between connecting Clouds and routing folders to them (Ian, 5 Oct 2026). They ran
        // together on the Fold 8, where the extra width lets both breathe and the join is harder to read: the
        // last Cloud row and the "Where each folder goes" heading looked like one list. A rule with space either
        // side, not a band — the green section headings mark the sections of Settings, and these two are halves
        // of Backup rather than sections of their own.
        Spacer(modifier = Modifier.size(8.dp))
        SettingDivider()
        Spacer(modifier = Modifier.size(8.dp))

        // Which local folder goes to which cloud — one row each, always shown (Ian, 24 Sept 2026: clean
        // pairing between a local folder and a cloud). Top-level folders only (DCIM, Pictures...), never
        // per album. The menu opens only where there is a real choice: with one cloud, or without Pro or
        // the trial, the pairing is shown and cannot be changed. A folder still paired with a cloud that has
        // lapsed or been disconnected keeps it on the menu, so it reads truthfully and can be moved back.
        if (state.folders.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = stringResource(R.string.backup_folders_heading),
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    text = stringResource(R.string.backup_folders_explainer),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            state.folders.forEach { folder ->
                val options = buildList {
                    addAll(cloudState.destinations)
                    if (folder.location !in this) add(folder.location)
                }
                // Paired with a Cloud that is signed out: the pill turns red and says so (Ian, 29 Sept 2026).
                // The pairing itself is kept, since moving the folder elsewhere would be the app choosing where
                // files go; it backs up again as soon as that Cloud is reconnected.
                val cloudMissing = cloudState.connected.none { it.location == folder.location }
                SettingDropdown(
                    label = pluralStringResource(
                        R.plurals.backup_folder_label, folder.fileCount, folder.name, folder.fileCount
                    ),
                    options = options,
                    selected = folder.location,
                    onSelected = { viewModel.setFolderLocation(folder.name, it) },
                    optionLabel = { location ->
                        if (cloudState.connected.none { it.location == location }) {
                            stringResource(R.string.pairing_option_not_connected, stringResource(location.labelRes()))
                        } else {
                            stringResource(location.labelRes())
                        }
                    },
                    // A red pill must open, so the folder can be moved to a Cloud that is connected.
                    enabled = options.size > 1 || cloudMissing,
                    alarmLabel = if (cloudMissing) stringResource(R.string.pairing_no_cloud) else null,
                    // Where in that cloud the folder's files go, so a row reads as one pairing:
                    // local folder, cloud, place in the cloud.
                    noteIsWarning = cloudMissing || (folder.location != cloudState.main && !cloudState.isEntitled),
                    note = if (cloudMissing) {
                        stringResource(R.string.pairing_not_connected, stringResource(folder.location.labelRes()))
                    } else if (folder.location != cloudState.main && !cloudState.isEntitled) {
                        // Paired with a cloud that is not the free one, with no Pro and no trial: nothing is
                        // sent there. Say so on the row rather than let it sit silent.
                        stringResource(R.string.pairing_needs_pro, stringResource(folder.location.labelRes()))
                    } else when (folder.location) {
                        BackupLocation.ONEDRIVE -> stringResource(
                            R.string.pairing_path, stringResource(folder.location.labelRes()), state.destinationRoot
                        )
                        BackupLocation.GOOGLE_PHOTOS -> stringResource(R.string.pairing_path_library)
                        else -> stringResource(
                            R.string.pairing_path, stringResource(folder.location.labelRes()), "GallerySync"
                        )
                    }
                )
            }
            SettingDivider()
        }

        // Copies sent before the location fix, and the offer to send them again. See LocationRepair.
        LocationRepairSection()


        // ── Albums ───────────────────────────────────────────────────────────
        SectionHeader(
            stringResource(R.string.settings_albums),
            help = HelpTopic.SETTINGS_SECTION_ALBUMS
        )

        SourcesSection()

        SettingDivider()

        DeletionSection()

        // ── Sync ─────────────────────────────────────────────────────────────
        SectionHeader(
            stringResource(R.string.settings_sync),
            help = HelpTopic.SETTINGS_SECTION_SYNC
        )

        SettingSwitch(
            label = stringResource(R.string.settings_optimise_photos),
            help = HelpTopic.SETTINGS_OPTIMISE_PHOTOS,
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
                    help = HelpTopic.SETTINGS_OPTIMISE_MODE,
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
            }
        }

        SettingDivider()

        SettingSwitch(
            label = stringResource(R.string.settings_optimise_videos),
            help = HelpTopic.SETTINGS_OPTIMISE_VIDEO,
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
                    help = HelpTopic.SETTINGS_OPTIMISE_MODE,
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
                    help = HelpTopic.SETTINGS_VIDEO_AGE,
                    options = MediaAge.entries,
                    selected = state.videoOptimiseAge,
                    onSelected = viewModel::setVideoOptimiseAge,
                    optionLabel = { age -> age.label() }
                )

                // Said where the choice is made. Until video optimising ran on its own this option
                // reached nothing, so the cost was theoretical; now it reaches a clip shot this
                // morning, and the one place that can say so is beside the setting.
                if (state.videoOptimiseAge == MediaAge.Immediately) {
                    Text(
                        text = stringResource(R.string.media_age_immediately_warning),
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                // A stronger compression asks whether clips skipped at the weaker one should be tried again
                // (Ian, 9 Oct 2026: "add a card for user choice re-optimise existing files, yes/no").
                var askReoptimise by remember { mutableStateOf(false) }
                SettingDropdown(
                    label = stringResource(R.string.settings_quality),
                    help = HelpTopic.SETTINGS_VIDEO_QUALITY,
                    options = VideoQuality.entries,
                    selected = state.videoQuality,
                    onSelected = { chosen ->
                        val stronger = chosen.targetShortSide < state.videoQuality.targetShortSide
                        viewModel.setVideoQuality(chosen)
                        if (stronger) askReoptimise = true
                    },
                    optionLabel = { quality ->
                        when (quality) {
                            VideoQuality.High -> stringResource(R.string.video_quality_high)
                            VideoQuality.Medium -> stringResource(R.string.video_quality_medium)
                            VideoQuality.Low -> stringResource(R.string.video_quality_low)
                        }
                    }
                )
                if (askReoptimise) {
                    androidx.compose.material3.AlertDialog(
                        onDismissRequest = { askReoptimise = false },
                        title = { Text(stringResource(R.string.reoptimise_title)) },
                        text = { Text(stringResource(R.string.reoptimise_body)) },
                        confirmButton = {
                            androidx.compose.material3.TextButton(onClick = {
                                askReoptimise = false
                                viewModel.reoptimiseExistingVideos()
                            }) { Text(stringResource(R.string.reoptimise_yes)) }
                        },
                        dismissButton = {
                            androidx.compose.material3.TextButton(onClick = { askReoptimise = false }) {
                                Text(stringResource(R.string.reoptimise_no))
                            }
                        }
                    )
                }
            }
        }

        // Nothing else follows the switches. Optimising runs from the modes above (Ian, 19 Sept 2026):
        // Automatic as soon as a file reaches a Sync album or an album is switched to Sync, Manual when
        // Sync now is pressed on the Albums tab. The status line and the button that used to sit here
        // were the old way of asking, and are gone with the guide topics that explained them.
        if (state.optimisePhotos && !state.canProxy) {
            Text(
                text = stringResource(R.string.proxy_unsupported),
                style = MaterialTheme.typography.bodySmall
            )
        }

        // ── Camera Sync ──────────────────────────────────────────────────────
        // The camera folder's own optimise. Off out of the box, and no folder is assumed (Ian, 27 Sept 2026):
        // until it is on and a folder is picked, every album is ordinary. Its own values, never the Optimise
        // switches above, which are for Sync albums only. Not in the wizard, which never writes Settings.
        SectionHeader(
            stringResource(R.string.settings_camera),
            help = HelpTopic.SETTINGS_SECTION_CAMERA
        )

        SettingSwitch(
            label = stringResource(R.string.settings_camera_special),
            checked = state.cameraSpecialEnabled,
            onCheckedChange = viewModel::setCameraSpecialEnabled
        )

        if (state.cameraSpecialEnabled) {
            Column(
                modifier = Modifier.padding(start = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Every album the phone has, plus the picked one if it is not among them right now (emptied, or
                // not yet scanned), so the button never shows a folder the menu cannot offer back. Blank reads
                // Choose: nothing is picked for the person.
                val cameraFolderOptions = remember(state.albums, state.cameraFolderPicked) {
                    (state.albums.map { it.name } + state.cameraFolderPicked)
                        .filter { it.isNotBlank() }
                        .distinctBy { it.lowercase() }
                        .sortedBy { it.lowercase() }
                }
                // Other… opens Android's folder picker for a folder not on the Albums tab yet, usually one the camera
                // has not saved anything to. Only the folder's name is kept: the grant is not persisted and nothing is
                // written there (Ian, 27 Sept 2026: the picker, not a typed name, which could silently match nothing).
                val pickFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
                    val name = uri?.let { runCatching { DocumentsContract.getTreeDocumentId(it) }.getOrNull() }
                        ?.let(CameraAlbum::folderNameFromTreeDocumentId)
                    if (name != null) viewModel.setCameraFolder(name)
                }
                val chooseLabel = stringResource(R.string.settings_camera_folder_choose)
                val otherLabel = stringResource(R.string.settings_camera_folder_other)
                SettingDropdown(
                    label = stringResource(R.string.settings_camera_folder),
                    options = cameraFolderOptions + CameraFolderOther,
                    selected = state.cameraFolderPicked,
                    onSelected = { picked ->
                        if (picked == CameraFolderOther) pickFolder.launch(null) else viewModel.setCameraFolder(picked)
                    },
                    optionLabel = { option ->
                        when {
                            option == CameraFolderOther -> otherLabel
                            option.isBlank() -> chooseLabel
                            else -> option
                        }
                    },
                    note = if (state.cameraFolderPicked.isBlank()) {
                        stringResource(R.string.settings_camera_folder_hint)
                    } else null
                )

                SettingDropdown(
                    label = stringResource(R.string.camera_optimise_heading),
                    options = CameraOptimiseAge.entries,
                    selected = state.cameraDefaults.age,
                    onSelected = viewModel::setCameraDefaultAge,
                    optionLabel = { age -> age.label() }
                )

                SettingSwitch(
                    label = stringResource(R.string.camera_kind_photos),
                    checked = state.cameraDefaults.photos,
                    onCheckedChange = viewModel::setCameraOptimisePhotos
                )

                SettingSwitch(
                    label = stringResource(R.string.camera_kind_videos),
                    checked = state.cameraDefaults.videos,
                    onCheckedChange = viewModel::setCameraOptimiseVideo
                )

                // Only once Videos is on: with it off there is no clip for it to act on (Ian, 27 Sept 2026).
                if (state.cameraDefaults.videos) {
                    SettingDropdown(
                        label = stringResource(R.string.settings_camera_video_quality),
                        options = VideoQuality.entries,
                        selected = state.cameraVideoQuality,
                        onSelected = viewModel::setCameraVideoQuality,
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
        }

        // ── Restore ──────────────────────────────────────────────────────────
        SectionHeader(
            stringResource(R.string.settings_restore),
            help = HelpTopic.SETTINGS_SECTION_RESTORE
        )

        SettingSwitch(
            label = stringResource(R.string.settings_show_empty_folders),
            help = HelpTopic.SETTINGS_SHOW_EMPTY_FOLDERS,
            checked = state.showEmptyCloudFolders,
            onCheckedChange = viewModel::setShowEmptyCloudFolders
        )

        // ── Archive ──────────────────────────────────────────────────────────
        SectionHeader(
            stringResource(R.string.settings_archive),
            help = HelpTopic.SETTINGS_SECTION_ARCHIVE
        )

        SettingDropdown(
            label = stringResource(R.string.archive_age_label),
            help = HelpTopic.SETTINGS_ARCHIVE_DEFAULT_AGE,
            options = ArchiveAge.entries,
            selected = state.archiveDefaultAge,
            onSelected = viewModel::setArchiveDefaultAge,
            optionLabel = { age -> age.label() }
        )

        // Whether the OS actually has the permission right now — checked fresh on every
        // recomposition rather than trusted from the stored preference, so the switch never claims
        // a notification will arrive when Android would silently drop it (denied at the prompt, or
        // turned off for the app afterwards in the phone's own Settings, outside this app entirely).
        val notifyPermitted = ArchiveNotifyPermission.granted(context)
        val requestNotifyPermission = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted -> viewModel.setArchiveNotifyEnabled(granted) }

        SettingSwitch(
            label = stringResource(R.string.settings_archive_notify),
            help = HelpTopic.SETTINGS_ARCHIVE_NOTIFY,
            checked = state.archiveNotifyEnabled && notifyPermitted,
            onCheckedChange = { turnOn ->
                when {
                    !turnOn -> viewModel.setArchiveNotifyEnabled(false)
                    notifyPermitted -> viewModel.setArchiveNotifyEnabled(true)
                    ArchiveNotifyPermission.needsRuntimeRequest() ->
                        requestNotifyPermission.launch(ArchiveNotifyPermission.MANIFEST_NAME)
                    // Below API 33 the permission has no runtime prompt to launch; it is simply held.
                    else -> viewModel.setArchiveNotifyEnabled(true)
                }
            }
        )
        // The preference says on but nothing will arrive — said plainly rather than leaving the
        // switch to read as broken. Turning it off and back on here re-asks Android.
        if (state.archiveNotifyEnabled && !notifyPermitted) {
            Text(
                text = stringResource(R.string.settings_archive_notify_blocked),
                style = MaterialTheme.typography.bodySmall
            )
        }

        // ── Help & Feedback ──────────────────────────────────────────────────
        SectionHeader(stringResource(R.string.settings_help_feedback))

        // Leaves the app for Google Play. That is the whole point of the card, and the only way to
        // reach a rating: Play takes them on its own listing and nowhere else.
        LinkCard(
            title = stringResource(R.string.settings_rate),
            detail = stringResource(R.string.settings_rate_detail),
            onClick = { openPlayListing(context) }
        )

        LinkCard(
            title = stringResource(R.string.settings_bug_report),
            detail = stringResource(R.string.settings_bug_report_detail),
            onClick = { showBugReport = true }
        )

        // The end of the sections proper. Everything below is the foot of the page, not a setting.
        HorizontalDivider()

        // ── About: policy, account deletion, contact ─────────────────────────
        LinkCard(
            title = stringResource(R.string.settings_privacy_policy),
            detail = stringResource(R.string.settings_privacy_policy_detail),
            onClick = { page = SupportPage.PRIVACY_POLICY }
        )

        LinkCard(
            title = stringResource(R.string.settings_delete_account),
            detail = stringResource(R.string.settings_delete_account_detail),
            onClick = { page = SupportPage.DELETE_ACCOUNT }
        )

        // Opens the in-app Contact page. No mail app is launched — Ian's ruling.
        LinkCard(
            title = stringResource(R.string.settings_contact),
            detail = stringResource(R.string.settings_contact_detail, SupportLinks.CONTACT_EMAIL),
            onClick = { showContact = true }
        )

        // Language is the last thing on the page (Ian, 20 Sept 2026). It is a placeholder for now.
        SettingDivider()

        WithHelp(HelpTopic.SETTINGS_LANGUAGE) {
            Column {
                Text(
                    text = stringResource(R.string.settings_language),
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    text = stringResource(R.string.settings_language_detail),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }

    page?.let { InAppPageDialog(page = it, onDismiss = { page = null }) }
    if (showContact) ContactDialog(onDismiss = { showContact = false })
    if (showBugReport) BugReportDialog(onDismiss = { showBugReport = false })
}

/**
 * A tappable "about" card that opens one of the in-app pages.
 *
 * Colours come from the theme's card defaults, so both light and dark follow the app's theme.
 */
@Composable
private fun LinkCard(title: String, detail: String, onClick: () -> Unit) {
    OutlinedCard(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            Text(text = detail, style = MaterialTheme.typography.bodySmall)
        }
    }
}

// ── Layout primitives ───────────────────────────────────────────────────────

/** The screen's side padding. Section headings reach past it to the edges of the screen. */
private val SettingsGutter = 16.dp

/**
 * A section heading: a band in the same green as the heading box at the top of each tab (the
 * [com.gallery.sync.ui.common.HeroCard]), edge to edge, with the title left-aligned and centred top
 * to bottom.
 *
 * The colours are [com.gallery.sync.ui.theme.GallerySyncColors.heroContainer] and its paired
 * `onHero`, exactly what HeroCard uses, so the two are the same green in both themes and the title
 * and help icon are never a colour picked for one theme only. It is deliberately **not** `accent`,
 * the bright green of the selected tab in the nav bar.
 */
@Composable
private fun SectionHeader(title: String, help: HelpTopic? = null) {
    val signal = LocalGallerySyncColors.current

    Surface(
        modifier = Modifier.spanScreenWidth(SettingsGutter),
        color = signal.heroContainer,
        contentColor = signal.onHero
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .padding(horizontal = SettingsGutter),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(text = title, style = MaterialTheme.typography.headlineSmall)

            if (help != null) HelpButton(help)
        }
    }
}

/**
 * Widens a child by [gutter] on each side so it spans the full screen width inside a parent that
 * pads its content by [gutter]. The layout it takes up in the parent is unchanged, so nothing else
 * in the column moves.
 */
private fun Modifier.spanScreenWidth(gutter: Dp): Modifier = layout { measurable, constraints ->
    val bleed = gutter.roundToPx()
    val width = constraints.maxWidth + bleed * 2
    val placeable = measurable.measure(constraints.copy(minWidth = width, maxWidth = width))
    layout(constraints.maxWidth, placeable.height) { placeable.place(-bleed, 0) }
}

/**
 * The light line between one sub-setting and the next (Ian, 20 Sept 2026). The theme's own divider
 * colour, so it is faint in both themes and never a colour picked for one.
 */
@Composable
private fun SettingDivider() {
    HorizontalDivider()
}

@Composable
private fun SettingSwitch(
    label: String,
    help: HelpTopic? = null,
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
            LabelWithHelp(label, help)
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

/** A setting's name with its (?) directly after it, so the button reads as belonging to the words. */
@Composable
private fun LabelWithHelp(label: String, help: HelpTopic?, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f, fill = false)
        )
        if (help != null) HelpButton(help)
    }
}

@Composable
private fun <T> SettingDropdown(
    label: String,
    help: HelpTopic? = null,
    options: List<T>,
    selected: T,
    onSelected: (T) -> Unit,
    optionLabel: @Composable (T) -> String,
    /** False greys the control: it is set by something else and shown for information. */
    enabled: Boolean = true,
    /** A line under the row saying why it is locked, or anything else worth a sentence. */
    note: String? = null,
    /** Draws [note] as a warning: something here is not working. */
    noteIsWarning: Boolean = false,
    /**
     * Replaces the button's text and draws it in the error colours: the selected value cannot work right
     * now (a folder paired with a signed-out Cloud). The menu still opens, so it can be changed.
     */
    alarmLabel: String? = null
) {
    var expanded by remember { mutableStateOf(false) }

    Column {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        LabelWithHelp(label, help, modifier = Modifier.weight(1f))
        Box {
            if (alarmLabel != null) {
                Button(
                    onClick = { expanded = true },
                    enabled = enabled,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                ) {
                    Text(alarmLabel, maxLines = 1)
                }
            } else {
                OutlinedButton(onClick = { expanded = true }, enabled = enabled) {
                    Text(optionLabel(selected), maxLines = 1)
                }
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
    note?.let {
        Text(
            text = it,
            style = MaterialTheme.typography.bodySmall,
            color = if (noteIsWarning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
        )
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

/**
 * The Camera folder list's last entry, which opens the folder picker. A value no album name can be, so it can never
 * be mistaken for one or stored.
 */
private const val CameraFolderOther = "\u0000other"

/** The same wording as the Camera folder's own age button. */
@Composable
private fun CameraOptimiseAge.label(): String = when (this) {
    CameraOptimiseAge.OneDay -> stringResource(R.string.camera_age_day)
    CameraOptimiseAge.OneWeek -> stringResource(R.string.camera_age_week)
    CameraOptimiseAge.OneMonth -> stringResource(R.string.camera_age_month)
    CameraOptimiseAge.SixMonths -> stringResource(R.string.camera_age_six_months)
    CameraOptimiseAge.OneYear -> stringResource(R.string.camera_age_year)
    CameraOptimiseAge.All -> stringResource(R.string.camera_age_all)
}

/** The same wording as the Archive tab's own filter — one vocabulary, one setting. */
@Composable
private fun ArchiveAge.label(): String = when (this) {
    ArchiveAge.OneHour -> stringResource(R.string.archive_age_hour)
    ArchiveAge.OneDay -> stringResource(R.string.archive_age_day)
    ArchiveAge.OneWeek -> stringResource(R.string.archive_age_week)
    ArchiveAge.OneMonth -> stringResource(R.string.archive_age_month)
    ArchiveAge.OneYear -> stringResource(R.string.archive_age_year)
    ArchiveAge.All -> stringResource(R.string.archive_age_all)
}

/**
 * The Settings tab as the setup tour draws it behind its cards.
 *
 * Built from this file's own pieces (section bands, rows, link cards) and the real strings, so it cannot
 * drift when the tab is restyled: it did, twice, while it was a separate drawing. Only the values are
 * invented. Inert: the tour lays a layer over it that swallows touches.
 */
@Composable
fun SettingsTabPreview(onGuideCardPositioned: ((androidx.compose.ui.geometry.Rect) -> Unit)? = null) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(start = SettingsGutter, end = SettingsGutter, top = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        SectionHeader(stringResource(R.string.settings_general), help = HelpTopic.SETTINGS_SECTION_GENERAL)
        // The card the tour's Help step rings.
        Box(
            modifier = Modifier.reportBounds(onGuideCardPositioned)
        ) {
            LinkCard(
                title = stringResource(R.string.settings_how_to_guide),
                detail = stringResource(R.string.settings_how_to_guide_detail),
                onClick = {}
            )
        }
        SettingDivider()
        SettingDropdown(
            label = stringResource(R.string.settings_appearance),
            help = HelpTopic.SETTINGS_APPEARANCE,
            options = listOf(ThemeMode.SYSTEM),
            selected = ThemeMode.SYSTEM,
            onSelected = {},
            optionLabel = { stringResource(R.string.theme_system) }
        )
        SettingDivider()
        SettingSwitch(
            label = stringResource(R.string.backup_allow_metered),
            help = HelpTopic.SETTINGS_MOBILE_DATA,
            detail = stringResource(R.string.backup_allow_metered_off),
            checked = false,
            onCheckedChange = {}
        )

        SectionHeader(stringResource(R.string.settings_backup), help = HelpTopic.SETTINGS_SECTION_BACKUP)
        Text(
            text = stringResource(R.string.cloud_providers_section_title),
            style = MaterialTheme.typography.titleMedium
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = stringResource(BackupLocation.ONEDRIVE.labelRes()) + " · " + stringResource(R.string.cloud_main_tag),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f)
            )
            OutlinedButton(onClick = {}) { Text(stringResource(R.string.sign_out_action)) }
        }
        SettingDivider()
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = stringResource(R.string.backup_folders_heading),
                style = MaterialTheme.typography.titleSmall
            )
            Text(
                text = stringResource(R.string.backup_folders_explainer),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        listOf("DCIM" to 61, "Pictures" to 25).forEach { (name, files) ->
            SettingDropdown(
                label = pluralStringResource(R.plurals.backup_folder_label, files, name, files),
                options = listOf(BackupLocation.ONEDRIVE),
                selected = BackupLocation.ONEDRIVE,
                onSelected = {},
                optionLabel = { location -> stringResource(location.labelRes()) },
                enabled = false,
                note = stringResource(
                    R.string.pairing_path,
                    stringResource(BackupLocation.ONEDRIVE.labelRes()),
                    "Samsung Gallery/DCIM"
                )
            )
        }
        SettingDivider()

        SectionHeader(stringResource(R.string.settings_albums), help = HelpTopic.SETTINGS_SECTION_ALBUMS)
        Text(text = stringResource(R.string.sources_title), style = MaterialTheme.typography.bodyLarge)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Checkbox(checked = true, onCheckedChange = null)
            Text(
                text = stringResource(
                    R.string.sources_full_path,
                    stringResource(R.string.volume_internal),
                    "DCIM"
                ),
                style = MaterialTheme.typography.bodyLarge
            )
        }

        SectionHeader(stringResource(R.string.settings_sync), help = HelpTopic.SETTINGS_SECTION_SYNC)
        SettingSwitch(
            label = stringResource(R.string.settings_optimise_photos),
            help = HelpTopic.SETTINGS_OPTIMISE_PHOTOS,
            checked = false,
            onCheckedChange = {}
        )
        SettingDivider()
        SettingSwitch(
            label = stringResource(R.string.settings_optimise_videos),
            help = HelpTopic.SETTINGS_OPTIMISE_VIDEO,
            checked = false,
            onCheckedChange = {}
        )

        SectionHeader(stringResource(R.string.settings_restore), help = HelpTopic.SETTINGS_SECTION_RESTORE)
        SettingSwitch(
            label = stringResource(R.string.settings_show_empty_folders),
            help = HelpTopic.SETTINGS_SHOW_EMPTY_FOLDERS,
            checked = false,
            onCheckedChange = {}
        )

        SectionHeader(stringResource(R.string.settings_archive), help = HelpTopic.SETTINGS_SECTION_ARCHIVE)
        SettingDropdown(
            label = stringResource(R.string.archive_age_label),
            help = HelpTopic.SETTINGS_ARCHIVE_DEFAULT_AGE,
            options = listOf(ArchiveAge.DEFAULT),
            selected = ArchiveAge.DEFAULT,
            onSelected = {},
            optionLabel = { age -> age.label() }
        )
        SettingSwitch(
            label = stringResource(R.string.settings_archive_notify),
            help = HelpTopic.SETTINGS_ARCHIVE_NOTIFY,
            checked = false,
            onCheckedChange = {}
        )

        SectionHeader(stringResource(R.string.settings_help_feedback))
        LinkCard(
            title = stringResource(R.string.settings_rate),
            detail = stringResource(R.string.settings_rate_detail),
            onClick = {}
        )
        LinkCard(
            title = stringResource(R.string.settings_bug_report),
            detail = stringResource(R.string.settings_bug_report_detail),
            onClick = {}
        )

        SettingDivider()
        LinkCard(
            title = stringResource(R.string.settings_privacy_policy),
            detail = stringResource(R.string.settings_privacy_policy_detail),
            onClick = {}
        )
    }
}

private fun Modifier.reportBounds(
    report: ((androidx.compose.ui.geometry.Rect) -> Unit)?
): Modifier = if (report == null) {
    this
} else {
    this.onGloballyPositioned { report(it.boundsInRoot()) }
}
