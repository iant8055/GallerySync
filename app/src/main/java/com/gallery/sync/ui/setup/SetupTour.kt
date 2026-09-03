package com.gallery.sync.ui.setup

import android.Manifest
import android.os.Build
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.DocumentsContract
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.LocalTime
import com.gallery.sync.R
import com.gallery.sync.data.local.media.DiscoveredDirectory
import com.gallery.sync.domain.backup.LibraryChoice
import com.gallery.sync.ui.common.formatBytes
import com.gallery.sync.domain.backup.VideoQuality
import androidx.compose.ui.graphics.vector.ImageVector
import com.gallery.sync.ui.common.SignalIcons
import com.gallery.sync.ui.theme.LocalGallerySyncColors
import com.gallery.sync.ui.settings.DestinationDialog
import com.gallery.sync.ui.signin.SignInUiState
import com.gallery.sync.ui.signin.SignInViewModel

private const val TOTAL_STEPS = 9

/**
 * Guided setup as tooltip-style bubbles overlaying the Albums tab.
 *
 * Replaces the old full-screen wizard. The user sees the real app behind a scrim, and each bubble
 * explains or collects one thing. Sign-in has already happened before this point.
 */
@Composable
fun SetupTour(
    viewModel: ReconcileViewModel,
    signInViewModel: SignInViewModel? = null,
    onComplete: () -> Unit,
    onSwitchTab: (Int) -> Unit = {},
    onStepChanged: (Int) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var step by rememberSaveable { mutableIntStateOf(1) }

    LaunchedEffect(step) { onStepChanged(step) }

    val treePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? -> uri?.let(viewModel::addSource) }

    val grantPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? -> viewModel.onDirectoryGrantResult(uri) }

    val context = LocalContext.current
    var mediaGranted by rememberSaveable {
        mutableStateOf(
            mediaPermissions().all {
                ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
            }
        )
    }
    val mediaPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results -> if (results.values.all { it }) mediaGranted = true }

    fun requestMediaPermission() {
        if (!mediaGranted) mediaPermissionLauncher.launch(mediaPermissions())
    }

    // Compute effective step count — step 7 only shows if optimization was chosen
    val showOptimization = state.libraryChoice == LibraryChoice.BACK_UP_AND_FREE_SPACE ||
        state.libraryChoice == LibraryChoice.BACK_UP_AND_OPTIMISE_NEW

    val signInState = signInViewModel?.state?.collectAsStateWithLifecycle()
    val isSignedIn = signInState?.value is SignInUiState.SignedIn
    val activity = LocalActivity.current

    fun canAdvance(): Boolean = when (step) {
        4 -> state.directoryChecks.values.any { it } && !state.grantingDirectories
        5 -> isSignedIn
        else -> true
    }

    val backupComplete = state.hasCompletedFirstBackup

    LaunchedEffect(step) {
        if (step == TOTAL_STEPS) viewModel.startBackupAndObserve()
    }

    // Discover directories when media permission is granted and we're on step 4
    LaunchedEffect(mediaGranted, step) {
        if (mediaGranted && step == 4 && state.discoveredDirectories.isEmpty() && !state.discoveryRunning) {
            viewModel.discoverDirectories()
        }
    }

    // Launch the SAF picker when pendingGrantDirectory changes
    LaunchedEffect(state.pendingGrantDirectory) {
        state.pendingGrantDirectory?.let { dirName ->
            val initialUri = DocumentsContract.buildDocumentUri(
                "com.android.externalstorage.documents",
                "primary:$dirName"
            )
            grantPicker.launch(initialUri)
        }
    }

    // Auto-advance from step 4 when the grant flow finishes and we have sources
    LaunchedEffect(state.grantingDirectories, state.hasSources) {
        if (step == 4 && !state.grantingDirectories && state.hasSources &&
            state.grantedSoFar > 0) {
            step = 5
        }
    }

    val onNext: () -> Unit = {
        when {
            step == 4 -> {
                viewModel.startDirectoryGrants()
            }
            step == TOTAL_STEPS -> {
                viewModel.completeSetup()
                onComplete()
            }
            else -> {
                var next = step + 1
                if (next == 7 && !showOptimization) next = 8
                step = next
            }
        }
    }
    val onBack: () -> Unit = {
        var prev = step - 1
        if (prev == 7 && !showOptimization) prev = 6
        if (prev >= 1) step = prev
    }

    Box(modifier = modifier.fillMaxSize()) {
        // Fully opaque background — the user hasn't set anything up yet so there is
        // nothing worth showing behind the tour.
        Box(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        )

        if (step == 1) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(onClick = onNext),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(R.drawable.welcome_screen),
                    contentDescription = stringResource(R.string.tour_welcome_title),
                    contentScale = ContentScale.FillWidth,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        } else if (step == 2) {
            // Step 2: individual tooltip cards pointing at the nav bar tabs
            TabTooltipsStep(
                stepNumber = step,
                onNext = onNext,
                onBack = onBack,
                onSwitchTab = onSwitchTab
            )
        } else {
            // Steps 3+: Albums mockup behind the bubble
            AlbumsMockup()
            Box(
                Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.4f))
            )
            Popup(
                alignment = Alignment.Center,
                properties = PopupProperties(focusable = true)
            ) {
                TourBubble(
                    stepNumber = step,
                    canAdvance = canAdvance(),
                    isLast = step == TOTAL_STEPS,
                    lastButtonLabel = if (backupComplete)
                        stringResource(R.string.wizard_finish_label)
                    else
                        stringResource(R.string.wizard_close_label),
                    onNext = onNext,
                    onBack = onBack
                ) {
                    when (step) {
                        3 -> InstallationStepsContent()
                        4 -> DirectoryDiscoveryContent(
                            state = state,
                            hasMediaPermission = mediaGranted,
                            onGrantMediaAccess = ::requestMediaPermission,
                            onToggleDirectory = viewModel::toggleDirectoryCheck
                        )
                        5 -> CloudStorageContent(
                            state = state,
                            isSignedIn = isSignedIn,
                            onSignIn = {
                                activity?.let { signInViewModel?.signIn(it) }
                            },
                            onChangeDestination = viewModel::openDestinationChooser
                        )
                        6 -> BackupOptionsContent(
                            selected = state.libraryChoice,
                            onSelect = viewModel::setLibraryChoice
                        )
                        7 -> OptimizationContent(
                            optimisePhotos = state.isAutoOptimiseEnabled,
                            onOptimisePhotosChanged = viewModel::setAutoOptimiseEnabled,
                            optimiseVideo = state.optimiseVideo,
                            onOptimiseVideoChanged = viewModel::setOptimiseVideo,
                            videoQuality = state.videoQuality,
                            onVideoQualityChanged = viewModel::setVideoQuality,
                            state = state
                        )
                        8 -> BackupDelayContent(
                            state = state,
                            onStartHourSelected = viewModel::setFirstBackupStartHour
                        )
                        9 -> BackupProgressContent(
                            completed = state.backupCompleted,
                            total = state.backupTotal,
                            currentFile = state.backupCurrentFile,
                            isRunning = state.backupRunning,
                            isFinished = state.backupFinished
                        )
                    }
                }
            }
        }

        if (state.choosingDestination) {
            DestinationDialog(
                current = state.destinationRoot,
                alreadyBackedUp = state.alreadyFoundHere,
                rejected = state.destinationRejected,
                onConfirm = viewModel::setDestination,
                onDismiss = viewModel::dismissDestinationChooser
            )
        }
    }
}

@Composable
private fun TourBubble(
    stepNumber: Int,
    canAdvance: Boolean,
    isLast: Boolean,
    lastButtonLabel: String = "",
    onNext: () -> Unit,
    onBack: () -> Unit,
    content: @Composable () -> Unit
) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 6.dp,
        shadowElevation = 8.dp,
        modifier = Modifier
            .widthIn(max = 360.dp)
            .padding(horizontal = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Step indicator
            Text(
                text = stringResource(R.string.tour_step_of, stepNumber, TOTAL_STEPS),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            content()

            // Navigation
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (stepNumber > 1) {
                    TextButton(onClick = onBack) {
                        Text(stringResource(R.string.wizard_back))
                    }
                } else {
                    Spacer(Modifier)
                }

                Button(onClick = onNext, enabled = canAdvance) {
                    Text(
                        if (isLast) lastButtonLabel
                        else stringResource(R.string.wizard_next)
                    )
                }
            }
        }
    }
}

// ── Step 1: Welcome ─────────────────────────────────────────────────────────


// ── Step 2: Tab tooltips pointing at the nav bar ───────────────────────────

@Composable
private fun TabTooltipsStep(
    stepNumber: Int,
    onNext: () -> Unit,
    onBack: () -> Unit,
    onSwitchTab: (Int) -> Unit
) {
    var subStep by rememberSaveable { mutableIntStateOf(0) }
    val arrowColor = MaterialTheme.colorScheme.primary
    val totalCards = 5

    data class TabInfo(
        val icon: ImageVector,
        val labelRes: Int,
        val descRes: Int,
        val tabIndex: Int
    )

    val tabs = listOf(
        TabInfo(SignalIcons.Albums, R.string.tab_backup, R.string.tour_description_tabs, 0),
        TabInfo(SignalIcons.Restore, R.string.tab_retrieve, R.string.tour_description_navigation, 1),
        TabInfo(SignalIcons.CloudCheck, R.string.tab_setup, R.string.tour_description_archive, 2),
        TabInfo(SignalIcons.Settings, R.string.tab_settings, R.string.tour_description_settings, 3),
        TabInfo(SignalIcons.Help, R.string.tab_help, R.string.tour_description_help, -1)
    )

    val tabIndex = subStep - 1
    val currentTab = tabs.getOrNull(tabIndex)

    Box(modifier = Modifier.fillMaxSize()) {
        if (subStep == 0) {
            AlbumsMockup()
            Box(
                Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.4f))
            )
            Popup(
                alignment = Alignment.Center,
                properties = PopupProperties(focusable = true)
            ) {
                TourBubble(
                    stepNumber = stepNumber,
                    canAdvance = true,
                    isLast = false,
                    onNext = {
                        subStep = 1
                        onSwitchTab(tabs[0].tabIndex)
                    },
                    onBack = onBack
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = stringResource(R.string.tour_description_title),
                            style = MaterialTheme.typography.titleLarge
                        )
                        Text(
                            text = stringResource(R.string.tour_description_body),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        } else if (currentTab != null) {
            // Mockup background — shows what the app looks like when populated
            when (tabIndex) {
                0 -> AlbumsMockup()
                1 -> RestoreMockup()
                2 -> ArchiveMockup()
                3 -> SettingsMockup()
                else -> AlbumsMockup()
            }

            // Scrim so the card pops over the mockup
            Box(
                Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.4f))
            )

            // Card positioned above its nav tab (or centred for Help)
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.Bottom
            ) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    if (tabIndex <= 3) {
                        if (tabIndex > 0) {
                            Spacer(Modifier.weight(tabIndex.toFloat()))
                        }
                    } else {
                        Spacer(Modifier.weight(1f))
                    }
                    Column(
                        modifier = Modifier.weight(2.5f),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        OutlinedCard(
                            onClick = {
                                if (subStep < totalCards) {
                                    subStep++
                                    val nextTab = tabs[subStep - 1]
                                    if (nextTab.tabIndex >= 0) onSwitchTab(nextTab.tabIndex)
                                } else {
                                    subStep = 0
                                    onSwitchTab(0)
                                    onNext()
                                }
                            },
                            border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary),
                            colors = CardDefaults.outlinedCardColors(
                                containerColor = MaterialTheme.colorScheme.surface
                            )
                        ) {
                            Column(
                                modifier = Modifier.padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Text(
                                    text = stringResource(
                                        R.string.tour_step_of, stepNumber, TOTAL_STEPS
                                    ) + "  ($subStep/$totalCards)",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Icon(
                                    imageVector = currentTab.icon,
                                    contentDescription = null,
                                    modifier = Modifier.size(48.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = stringResource(currentTab.labelRes),
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center
                                )
                                Text(
                                    text = stringResource(currentTab.descRes),
                                    style = MaterialTheme.typography.bodyLarge,
                                    textAlign = TextAlign.Center,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Button(
                                    onClick = {
                                        if (subStep < totalCards) {
                                            subStep++
                                            val nextTab = tabs[subStep - 1]
                                            if (nextTab.tabIndex >= 0) onSwitchTab(nextTab.tabIndex)
                                        } else {
                                            subStep = 0
                                            onSwitchTab(0)
                                            onNext()
                                        }
                                    }
                                ) {
                                    Text(stringResource(R.string.wizard_next))
                                }
                            }
                        }
                        if (tabIndex <= 3) {
                            Canvas(modifier = Modifier.size(20.dp)) {
                                val path = androidx.compose.ui.graphics.Path().apply {
                                    moveTo(0f, 0f)
                                    lineTo(size.width, 0f)
                                    lineTo(size.width / 2, size.height)
                                    close()
                                }
                                drawPath(path, color = arrowColor)
                            }
                        }
                    }
                    if (tabIndex <= 3) {
                        val remaining = 3 - tabIndex
                        if (remaining > 0) {
                            Spacer(Modifier.weight(remaining.toFloat()))
                        }
                    } else {
                        Spacer(Modifier.weight(1f))
                    }
                }

                Spacer(Modifier.height(60.dp))
            }
        }
    }
}

// ── Step 3: Installation Steps ──────────────────────────────────────────────

@Composable
private fun InstallationStepsContent() {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.tour_install_title),
            style = MaterialTheme.typography.titleLarge
        )
        Text(
            text = stringResource(R.string.tour_install_body),
            style = MaterialTheme.typography.bodyLarge
        )

        BulletItem(stringResource(R.string.tour_install_select_gallery))
        BulletItem(stringResource(R.string.tour_install_grant_gallery))
        BulletItem(stringResource(R.string.tour_install_grant_cloud))
        BulletItem(stringResource(R.string.tour_install_select_cloud))
    }
}

// ── Step 4: Local Gallery Access ────────────────────────────────────────────

@Composable
private fun DirectoryDiscoveryContent(
    state: ReconcileUiState,
    hasMediaPermission: Boolean,
    onGrantMediaAccess: () -> Unit,
    onToggleDirectory: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = stringResource(R.string.tour_discover_title),
            style = MaterialTheme.typography.titleLarge
        )
        Text(
            text = stringResource(R.string.tour_discover_body),
            style = MaterialTheme.typography.bodyMedium
        )

        if (!hasMediaPermission) {
            OutlinedButton(onClick = onGrantMediaAccess) {
                Text(stringResource(R.string.permission_grant_action))
            }
        } else if (state.discoveryRunning) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                Text(
                    text = stringResource(R.string.tour_discover_scanning),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        } else if (state.discoveredDirectories.isEmpty()) {
            Text(
                text = stringResource(R.string.tour_discover_none_found),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            state.discoveredDirectories.forEach { dir ->
                val checked = state.directoryChecks[dir.name] ?: false
                DirectoryRow(
                    directory = dir,
                    checked = checked,
                    onToggle = { onToggleDirectory(dir.name) }
                )
            }

            if (state.grantingDirectories) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 4.dp)
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    Text(
                        text = stringResource(
                            R.string.tour_discover_granting,
                            state.grantedSoFar + 1,
                            state.grantTotal
                        ),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}

@Composable
private fun DirectoryRow(
    directory: DiscoveredDirectory,
    checked: Boolean,
    onToggle: () -> Unit
) {
    val context = LocalContext.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = { onToggle() }
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = directory.name,
                style = MaterialTheme.typography.bodyLarge
            )
            val line1 = buildString {
                append(directory.albumCount)
                append(if (directory.albumCount == 1) " folder, " else " folders, ")
                append(formatFileCount(directory.photoCount, directory.videoCount))
            }
            Text(
                text = line1,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = formatBytes(context, directory.totalBytes),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ── Step 5: Cloud Storage ───────────────────────────────────────────────────

@Composable
private fun CloudStorageContent(
    state: ReconcileUiState,
    isSignedIn: Boolean,
    onSignIn: () -> Unit,
    onChangeDestination: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.tour_cloud_title),
            style = MaterialTheme.typography.titleLarge
        )
        Text(
            text = stringResource(R.string.tour_cloud_body),
            style = MaterialTheme.typography.bodyMedium
        )

        if (!isSignedIn) {
            Button(onClick = onSignIn) {
                Text(stringResource(R.string.sign_in_action))
            }
        } else {
            Text(
                text = stringResource(R.string.tour_cloud_signed_in),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = stringResource(R.string.tour_cloud_destination),
                        style = MaterialTheme.typography.labelMedium
                    )
                    Text(
                        text = state.destinationRoot,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
                TextButton(onClick = onChangeDestination) {
                    Text(stringResource(R.string.tour_cloud_change))
                }
            }
        }
    }
}

// ── Step 6: Initial Backup Options ──────────────────────────────────────────

@Composable
private fun BackupOptionsContent(
    selected: LibraryChoice,
    onSelect: (LibraryChoice) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.tour_backup_title),
            style = MaterialTheme.typography.titleLarge
        )

        LibraryChoice.entries.forEachIndexed { index, choice ->
            val isSelected = selected == choice
            if (isSelected) {
                Button(
                    onClick = { onSelect(choice) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        "${index + 1}. ${stringResource(labelOf(choice))}",
                        textAlign = TextAlign.Start,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            } else {
                OutlinedButton(
                    onClick = { onSelect(choice) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        "${index + 1}. ${stringResource(labelOf(choice))}",
                        textAlign = TextAlign.Start,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

@androidx.annotation.StringRes
private fun labelOf(choice: LibraryChoice): Int = when (choice) {
    LibraryChoice.CHOOSE_PER_ALBUM -> R.string.library_per_album
    LibraryChoice.BACK_UP_EVERYTHING -> R.string.library_back_up_all
    LibraryChoice.BACK_UP_AND_OPTIMISE_NEW -> R.string.library_optimise_new
    LibraryChoice.BACK_UP_AND_FREE_SPACE -> R.string.library_free_space
}

// ── Step 7: Optimization ────────────────────────────────────────────────────

@Composable
private fun OptimizationContent(
    optimisePhotos: Boolean,
    onOptimisePhotosChanged: (Boolean) -> Unit,
    optimiseVideo: Boolean,
    onOptimiseVideoChanged: (Boolean) -> Unit,
    videoQuality: VideoQuality,
    onVideoQualityChanged: (VideoQuality) -> Unit,
    state: ReconcileUiState
) {
    var localPhotoChecked by rememberSaveable { mutableStateOf(optimisePhotos) }
    if (localPhotoChecked != optimisePhotos) localPhotoChecked = optimisePhotos
    var localVideoChecked by rememberSaveable { mutableStateOf(optimiseVideo) }
    if (localVideoChecked != optimiseVideo) localVideoChecked = optimiseVideo

    val context = LocalContext.current
    val result = state.result
    val totalPhotoBytes = result?.photosOutstanding?.bytes ?: 0L
    val totalVideoBytes = result?.videosOutstanding?.bytes ?: 0L

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = stringResource(R.string.tour_optimise_title),
            style = MaterialTheme.typography.titleLarge
        )
        Text(
            text = stringResource(R.string.tour_optimise_body),
            style = MaterialTheme.typography.bodyMedium
        )

        HorizontalDivider()

        // Photos toggle
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                stringResource(R.string.settings_optimise_photos),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f)
            )
            Switch(
                checked = localPhotoChecked,
                onCheckedChange = { value ->
                    localPhotoChecked = value
                    onOptimisePhotosChanged(value)
                },
                colors = SwitchDefaults.colors(
                    uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    uncheckedBorderColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
        }

        if (localPhotoChecked && totalPhotoBytes > 0) {
            Text(
                text = stringResource(
                    R.string.tour_optimise_photos_saving,
                    formatBytes(context, totalPhotoBytes)
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary
            )
        }

        HorizontalDivider()

        // Videos toggle
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                stringResource(R.string.settings_optimise_videos),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f)
            )
            Switch(
                checked = localVideoChecked,
                onCheckedChange = { value ->
                    localVideoChecked = value
                    onOptimiseVideoChanged(value)
                },
                colors = SwitchDefaults.colors(
                    uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    uncheckedBorderColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
        }

        // Video quality selector — shown when video optimization is on
        AnimatedVisibility(
            visible = localVideoChecked,
            enter = expandVertically(),
            exit = shrinkVertically()
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.tour_optimise_video_quality),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    VideoQuality.entries.forEach { quality ->
                        val isSelected = videoQuality == quality
                        val label = stringResource(
                            when (quality) {
                                VideoQuality.High -> R.string.video_quality_high
                                VideoQuality.Medium -> R.string.video_quality_medium
                                VideoQuality.Low -> R.string.video_quality_low
                            }
                        )
                        if (isSelected) {
                            Button(
                                onClick = { onVideoQualityChanged(quality) },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(label, fontWeight = FontWeight.Bold)
                            }
                        } else {
                            OutlinedButton(
                                onClick = { onVideoQualityChanged(quality) },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(label)
                            }
                        }
                    }
                }

                if (totalVideoBytes > 0) {
                    val savingBytes = totalVideoBytes * videoQuality.approximateSavingPercent / 100
                    Text(
                        text = stringResource(
                            R.string.tour_optimise_video_saving,
                            formatBytes(context, savingBytes),
                            videoQuality.approximateSavingPercent
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        // Total savings
        if ((localPhotoChecked || localVideoChecked) && result != null) {
            HorizontalDivider()
            val photoSaving = if (localPhotoChecked) totalPhotoBytes else 0L
            val videoSaving = if (localVideoChecked)
                totalVideoBytes * videoQuality.approximateSavingPercent / 100 else 0L
            val total = photoSaving + videoSaving
            if (total > 0) {
                Text(
                    text = stringResource(
                        R.string.tour_optimise_total_saving,
                        formatBytes(context, total)
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        Text(
            text = stringResource(R.string.tour_optimise_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// ── Step 8: Backup Summary ──────────────────────────────────────────────────

@Composable
private fun BackupDelayContent(
    state: ReconcileUiState,
    onStartHourSelected: (Int) -> Unit
) {
    val result = state.result
    val context = LocalContext.current

    val totalBackupBytes = result?.outstanding?.bytes ?: 0L

    val photoSaving = if (state.isAutoOptimiseEnabled)
        (result?.photosOutstanding?.bytes ?: 0L) else 0L
    val videoSaving = if (state.optimiseVideo)
        (result?.videosOutstanding?.bytes ?: 0L) * state.videoQuality.approximateSavingPercent / 100
    else 0L
    val totalSaving = photoSaving + videoSaving

    var startNow by rememberSaveable { mutableStateOf(true) }
    var delayHours by rememberSaveable { mutableIntStateOf(1) }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = stringResource(R.string.tour_delay_title),
            style = MaterialTheme.typography.titleLarge
        )

        if (result != null) {
            Text(
                text = stringResource(
                    R.string.tour_delay_backup_size,
                    formatBytes(context, totalBackupBytes)
                ),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold
            )

            if (totalSaving > 0) {
                Text(
                    text = stringResource(
                        R.string.tour_delay_saving_size,
                        formatBytes(context, totalSaving)
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        } else if (state.running) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                Text(
                    text = stringResource(R.string.tour_delay_scanning),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        HorizontalDivider()

        Text(
            text = stringResource(R.string.tour_delay_when),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold
        )

        if (startNow) {
            Button(
                onClick = {
                    startNow = true
                    val currentHour = LocalTime.now().hour
                    onStartHourSelected(currentHour)
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.tour_delay_right_now))
            }
        } else {
            OutlinedButton(
                onClick = {
                    startNow = true
                    val currentHour = LocalTime.now().hour
                    onStartHourSelected(currentHour)
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.tour_delay_right_now))
            }
        }

        if (!startNow) {
            Button(
                onClick = {},
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.tour_delay_later, delayHours))
            }
        } else {
            OutlinedButton(
                onClick = {
                    startNow = false
                    val targetHour = (LocalTime.now().hour + delayHours) % 24
                    onStartHourSelected(targetHour)
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.tour_delay_later, delayHours))
            }
        }

        AnimatedVisibility(
            visible = !startNow,
            enter = expandVertically(),
            exit = shrinkVertically()
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    stringResource(R.string.tour_delay_hours_label),
                    style = MaterialTheme.typography.bodyMedium
                )
                listOf(1, 2, 4, 8, 12, 24).forEach { hours ->
                    val isSelected = delayHours == hours
                    if (isSelected) {
                        Button(
                            onClick = {},
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("$hours")
                        }
                    } else {
                        OutlinedButton(
                            onClick = {
                                delayHours = hours
                                val targetHour = (LocalTime.now().hour + hours) % 24
                                onStartHourSelected(targetHour)
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("$hours")
                        }
                    }
                }
            }
        }

        HorizontalDivider()

        Text(
            text = stringResource(R.string.tour_delay_security),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// ── Step 9: Backup Progress ─────────────────────────────────────────────────

@Composable
private fun BackupProgressContent(
    completed: Int,
    total: Int,
    currentFile: String,
    isRunning: Boolean,
    isFinished: Boolean
) {
    val percent = if (total > 0) ((completed * 100) / total).coerceIn(0, 100)
    else if (isFinished) 100 else 0
    val sweepAngle = percent * 3.6f

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = stringResource(R.string.tour_progress_title),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.fillMaxWidth()
        )

        Text(
            text = if (isFinished) stringResource(R.string.tour_progress_complete)
            else stringResource(R.string.tour_progress_body),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.fillMaxWidth()
        )

        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(180.dp)
        ) {
            val trackColor = MaterialTheme.colorScheme.surfaceVariant
            val progressColor = MaterialTheme.colorScheme.primary
            Canvas(modifier = Modifier.size(180.dp)) {
                val stroke = Stroke(width = 12.dp.toPx(), cap = StrokeCap.Round)
                drawArc(trackColor, 0f, 360f, false, style = stroke)
                drawArc(progressColor, -90f, sweepAngle, false, style = stroke)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "$percent%",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = when {
                        isFinished -> stringResource(R.string.wizard_finish_label)
                        total > 0 -> stringResource(
                            R.string.tour_progress_uploading, completed, total
                        )
                        isRunning -> stringResource(R.string.tour_progress_scanning)
                        else -> stringResource(R.string.tour_progress_waiting)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Text(
            text = stringResource(R.string.tour_progress_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

// ── Tab Mockups (decorative backgrounds for Step 2 cards) ──────────────────

@Composable
private fun AlbumsMockup() {
    val signal = LocalGallerySyncColors.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Surface(
            color = signal.heroContainer,
            contentColor = signal.onHero,
            shape = RoundedCornerShape(28.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // "All Albums" heading pill
                Surface(
                    color = signal.onHero.copy(alpha = 0.0f),
                    shape = RoundedCornerShape(50),
                    border = BorderStroke(2.dp, signal.onHero.copy(alpha = 0.35f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "All Albums",
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(vertical = 12.dp),
                        style = MaterialTheme.typography.headlineSmall
                    )
                }
                // Mode filter chips — 2x2 grid
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Surface(
                        color = signal.backupContainer,
                        contentColor = signal.onBackupContainer,
                        shape = RoundedCornerShape(50),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = "Backup",
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                    Surface(
                        color = signal.syncContainer,
                        contentColor = signal.onSyncContainer,
                        shape = RoundedCornerShape(50),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = "Sync",
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Surface(
                        color = signal.archiveContainer,
                        contentColor = signal.onArchiveContainer,
                        shape = RoundedCornerShape(50),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = "Archive",
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                    Surface(
                        color = signal.offContainer,
                        contentColor = signal.onOffContainer,
                        shape = RoundedCornerShape(50),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = "Off",
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
                Text(
                    text = "Tap to filter by mode",
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                    style = MaterialTheme.typography.bodySmall,
                    color = signal.onHero.copy(alpha = 0.7f)
                )
                Text(
                    text = "5 Albums · 22.6 GB",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "1 Backup · 2 Sync · 1 Archive · 1 Off",
                    style = MaterialTheme.typography.bodyMedium
                )
                // Action buttons
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Surface(
                        color = signal.accent,
                        contentColor = signal.onAccent,
                        shape = RoundedCornerShape(50),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = "Sync now",
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                    Surface(
                        color = signal.onHero.copy(alpha = 0.0f),
                        shape = RoundedCornerShape(50),
                        border = BorderStroke(1.dp, signal.onHero.copy(alpha = 0.35f)),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = "Rescan",
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            color = signal.onHero,
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
            }
        }
        MockAlbumRow("Camera", "2,847 files · 18.2 GB", "Backup")
        MockAlbumRow("Screenshots", "943 files · 1.8 GB", "Sync")
        MockAlbumRow("Downloads", "156 files · 2.4 GB", "Archive")
        MockAlbumRow("WhatsApp", "1,205 files · 3.1 GB", "Sync")
    }
}

@Composable
private fun MockAlbumRow(name: String, details: String, mode: String) {
    val signal = LocalGallerySyncColors.current
    val (modeColor, modeTextColor) = when (mode) {
        "Backup" -> signal.backupContainer to signal.onBackupContainer
        "Sync" -> signal.syncContainer to signal.onSyncContainer
        "Archive" -> signal.archiveContainer to signal.onArchiveContainer
        else -> signal.offContainer to signal.onOffContainer
    }

    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(22.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1
                )
                Text(
                    text = details,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Surface(
                color = modeColor,
                contentColor = modeTextColor,
                shape = RoundedCornerShape(50)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = mode, style = MaterialTheme.typography.labelLarge)
                    Icon(
                        imageVector = SignalIcons.ChevronDown,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun RestoreMockup() {
    val signal = LocalGallerySyncColors.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Surface(
            color = signal.heroContainer,
            contentColor = signal.onHero,
            shape = RoundedCornerShape(28.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Folders to Restore",
                        style = MaterialTheme.typography.titleLarge,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = "6",
                        style = MaterialTheme.typography.displaySmall
                    )
                }
                Text(
                    text = "Swipe right to select · left to deselect\nTap to open",
                    style = MaterialTheme.typography.bodyMedium
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Surface(
                        color = signal.onHero.copy(alpha = 0.0f),
                        shape = RoundedCornerShape(50),
                        border = BorderStroke(1.dp, signal.onHero.copy(alpha = 0.35f)),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = "Refresh",
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                    Surface(
                        color = signal.onHero.copy(alpha = 0.0f),
                        shape = RoundedCornerShape(50),
                        border = BorderStroke(1.dp, signal.onHero.copy(alpha = 0.35f)),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = "Clear",
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
            }
        }
        HorizontalDivider()
        MockRestoreRow("Vacation 2025", "324 files · 2.1 GB", true)
        MockRestoreRow("Family Reunion", "156 files · 890 MB", false)
        MockRestoreRow("Old Screenshots", "89 files · 245 MB", false)
        MockRestoreRow("Work Documents", "43 files · 120 MB", false)
    }
}

@Composable
private fun MockRestoreRow(name: String, details: String, selected: Boolean) {
    Surface(
        color = if (selected) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(22.dp),
        border = BorderStroke(
            if (selected) 2.dp else 1.dp,
            if (selected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.outline
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 18.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(name, style = MaterialTheme.typography.headlineSmall)
                Text(
                    text = details,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                imageVector = if (selected) SignalIcons.Check else SignalIcons.ChevronRight,
                contentDescription = null,
                tint = if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun ArchiveMockup() {
    val signal = LocalGallerySyncColors.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Surface(
            color = signal.heroContainer,
            contentColor = signal.onHero,
            shape = RoundedCornerShape(28.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Files to Archive",
                        style = MaterialTheme.typography.titleLarge,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = "8",
                        style = MaterialTheme.typography.displaySmall
                    )
                }
                Text(
                    text = "Camera, Downloads",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "Every file below is checked against OneDrive first. Nothing leaves the phone until you say so.",
                    style = MaterialTheme.typography.bodySmall
                )
                Surface(
                    color = signal.onHero.copy(alpha = 0.0f),
                    shape = RoundedCornerShape(50),
                    border = BorderStroke(1.dp, signal.onHero.copy(alpha = 0.35f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Check these files",
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        }
        HorizontalDivider()
        MockArchiveRow("IMG_20250615_142031.jpg", "4.2 MB", true)
        MockArchiveRow("IMG_20250612_091547.jpg", "3.8 MB", true)
        MockArchiveRow("VID_20250610_183022.mp4", "148 MB", true)
        MockArchiveRow("Screenshot_20250608.png", "1.2 MB", false)
    }
}

@Composable
private fun MockArchiveRow(name: String, size: String, confirmed: Boolean) {
    val signal = LocalGallerySyncColors.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 0.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = name,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1
            )
            Text(
                text = size,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Icon(
            imageVector = if (confirmed) SignalIcons.Check else SignalIcons.Cross,
            contentDescription = null,
            tint = if (confirmed) signal.accent
            else MaterialTheme.colorScheme.error,
            modifier = Modifier.size(22.dp)
        )
    }
}

@Composable
private fun SettingsMockup() {
    val signal = LocalGallerySyncColors.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(start = 16.dp, end = 16.dp, top = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // General section
        Text(
            text = "General",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.primary
        )
        Row(
            Modifier.fillMaxWidth(),
            Arrangement.SpaceBetween,
            Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Use mobile data", style = MaterialTheme.typography.bodyLarge)
                Text(
                    text = "Sync over Wi-Fi only",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = false,
                onCheckedChange = null,
                colors = SwitchDefaults.colors(
                    uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    uncheckedBorderColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
        }
        HorizontalDivider()

        // Backup section
        Text(
            text = "Backup",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.primary
        )
        Row(
            Modifier.fillMaxWidth(),
            Arrangement.SpaceBetween,
            Alignment.CenterVertically
        ) {
            Text("user@outlook.com", style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f))
            OutlinedButton(onClick = {}) {
                Text("Sign out")
            }
        }
        Row(
            Modifier.fillMaxWidth(),
            Arrangement.SpaceBetween,
            Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Current folder location", style = MaterialTheme.typography.bodyLarge)
                Text(
                    text = "OneDrive / GallerySync",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            OutlinedButton(onClick = {}) {
                Text("Change")
            }
        }
        HorizontalDivider()

        // Sync section
        Text(
            text = "Sync",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.primary
        )
        Row(
            Modifier.fillMaxWidth(),
            Arrangement.SpaceBetween,
            Alignment.CenterVertically
        ) {
            Text("Optimise photos", style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f))
            Switch(
                checked = true,
                onCheckedChange = null,
                colors = SwitchDefaults.colors(
                    uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    uncheckedBorderColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
        }
        Row(
            Modifier.fillMaxWidth(),
            Arrangement.SpaceBetween,
            Alignment.CenterVertically
        ) {
            Text("Optimise video", style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f))
            Switch(
                checked = false,
                onCheckedChange = null,
                colors = SwitchDefaults.colors(
                    uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    uncheckedBorderColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
        }
    }
}

// ── Helpers ─────────────────────────────────────────────────────────────────

@Composable
private fun BulletItem(text: String) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(start = 8.dp)
    ) {
        Text("•", style = MaterialTheme.typography.bodyLarge)
        Text(text, style = MaterialTheme.typography.bodyLarge)
    }
}

private fun formatFileCount(photos: Int, videos: Int): String = when {
    photos > 0 && videos > 0 -> "${pluralCount(photos, "photo")}, ${pluralCount(videos, "video")}"
    videos == 0 -> pluralCount(photos, "photo")
    else -> pluralCount(videos, "video")
}

private fun pluralCount(n: Int, singular: String): String =
    if (n == 1) "1 $singular" else "$n ${singular}s"

private fun mediaPermissions(): Array<String> =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO)
    } else {
        arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }
