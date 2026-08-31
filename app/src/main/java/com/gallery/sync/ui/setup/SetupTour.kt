package com.gallery.sync.ui.setup

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gallery.sync.R
import com.gallery.sync.domain.backup.LibraryChoice
import com.gallery.sync.ui.common.formatBytes

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
    onComplete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var step by rememberSaveable { mutableIntStateOf(1) }

    val treePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? -> uri?.let(viewModel::addSource) }

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

    fun canAdvance(): Boolean = when (step) {
        4 -> state.hasSources
        else -> true
    }

    Box(modifier = modifier.fillMaxSize()) {
        // Scrim
        Box(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.4f))
        )

        // Bubble
        Popup(
            alignment = Alignment.Center,
            properties = PopupProperties(focusable = true)
        ) {
            TourBubble(
                stepNumber = step,
                canAdvance = canAdvance(),
                isLast = step == TOTAL_STEPS,
                onNext = {
                    if (step == TOTAL_STEPS) {
                        viewModel.completeSetup()
                        onComplete()
                    } else {
                        var next = step + 1
                        // Skip optimization step if not applicable
                        if (next == 7 && !showOptimization) next = 8
                        step = next
                    }
                },
                onBack = {
                    var prev = step - 1
                    if (prev == 7 && !showOptimization) prev = 6
                    if (prev >= 1) step = prev
                }
            ) {
                when (step) {
                    1 -> WelcomeContent()
                    2 -> DescriptionContent()
                    3 -> InstallationStepsContent()
                    4 -> LocalGalleryContent(
                        state = state,
                        hasMediaPermission = mediaGranted,
                        onPickFolder = { treePicker.launch(null) },
                        onGrantMediaAccess = ::requestMediaPermission,
                        onRemove = viewModel::removeSource
                    )
                    5 -> CloudStorageContent(
                        state = state,
                        onChangeDestination = viewModel::openDestinationChooser
                    )
                    6 -> BackupOptionsContent(
                        selected = state.libraryChoice,
                        onSelect = viewModel::setLibraryChoice
                    )
                    7 -> OptimizationContent(
                        optimisePhotos = state.isAutoOptimiseEnabled,
                        onOptimisePhotosChanged = viewModel::setAutoOptimiseEnabled
                    )
                    8 -> BackupDelayContent(state = state)
                    9 -> BackupProgressContent()
                }
            }
        }
    }
}

@Composable
private fun TourBubble(
    stepNumber: Int,
    canAdvance: Boolean,
    isLast: Boolean,
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
                        if (isLast) stringResource(R.string.wizard_finish)
                        else stringResource(R.string.wizard_next)
                    )
                }
            }
        }
    }
}

// ── Step 1: Welcome ─────────────────────────────────────────────────────────

@Composable
private fun WelcomeContent() {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.tour_welcome_title),
            style = MaterialTheme.typography.titleLarge
        )
        Text(
            text = stringResource(R.string.tour_welcome_body),
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

// ── Step 2: Description ─────────────────────────────────────────────────────

@Composable
private fun DescriptionContent() {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.tour_description_title),
            style = MaterialTheme.typography.titleLarge
        )
        Text(
            text = stringResource(R.string.tour_description_body),
            style = MaterialTheme.typography.bodyMedium
        )

        BulletItem(stringResource(R.string.tour_description_tabs))
        BulletItem(stringResource(R.string.tour_description_navigation))
        BulletItem(stringResource(R.string.tour_description_settings))
        BulletItem(stringResource(R.string.tour_description_help))
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
            style = MaterialTheme.typography.bodyMedium
        )

        BulletItem(stringResource(R.string.tour_install_select_gallery))
        BulletItem(stringResource(R.string.tour_install_grant_gallery))
        BulletItem(stringResource(R.string.tour_install_select_cloud))
        BulletItem(stringResource(R.string.tour_install_grant_cloud))
    }
}

// ── Step 4: Local Gallery Access ────────────────────────────────────────────

@Composable
private fun LocalGalleryContent(
    state: ReconcileUiState,
    hasMediaPermission: Boolean,
    onPickFolder: () -> Unit,
    onGrantMediaAccess: () -> Unit,
    onRemove: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = stringResource(R.string.tour_local_title),
            style = MaterialTheme.typography.titleLarge
        )
        Text(
            text = stringResource(R.string.tour_local_body),
            style = MaterialTheme.typography.bodyMedium
        )

        // Folder list
        state.directories.forEach { directory ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(directory.displayName, style = MaterialTheme.typography.bodyLarge)
                TextButton(onClick = { onRemove(directory.treeUri) }) {
                    Text(stringResource(R.string.wizard_sources_remove))
                }
            }
        }

        if (state.directoryRefused) {
            Text(
                text = stringResource(R.string.wizard_sources_refused),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }

        Button(onClick = onPickFolder) {
            Text(stringResource(R.string.tour_local_select_folder))
        }

        if (!hasMediaPermission) {
            OutlinedButton(onClick = onGrantMediaAccess) {
                Text(stringResource(R.string.permission_grant_action))
            }
        } else if (!state.hasSources) {
            Text(
                text = stringResource(R.string.tour_local_media_granted),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary
            )
        }

        if (state.hasSources && hasMediaPermission) {
            Text(
                text = stringResource(R.string.tour_local_granted),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

// ── Step 5: Cloud Storage ───────────────────────────────────────────────────

@Composable
private fun CloudStorageContent(
    state: ReconcileUiState,
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
    onOptimisePhotosChanged: (Boolean) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = stringResource(R.string.tour_optimise_title),
            style = MaterialTheme.typography.titleLarge
        )
        Text(
            text = stringResource(R.string.tour_optimise_body),
            style = MaterialTheme.typography.bodyMedium
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                stringResource(R.string.settings_optimise_photos),
                style = MaterialTheme.typography.bodyLarge
            )
            Switch(
                checked = optimisePhotos,
                onCheckedChange = onOptimisePhotosChanged,
                colors = SwitchDefaults.colors(
                    uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    uncheckedBorderColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
        }

        Text(
            text = stringResource(R.string.tour_optimise_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// ── Step 8: Backup Delay ────────────────────────────────────────────────────

@Composable
private fun BackupDelayContent(state: ReconcileUiState) {
    val result = state.result
    val context = LocalContext.current

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.tour_delay_title),
            style = MaterialTheme.typography.titleLarge
        )

        if (result != null) {
            Text(
                text = stringResource(
                    R.string.tour_delay_summary,
                    result.outstanding.files,
                    formatBytes(context, result.outstanding.bytes)
                ),
                style = MaterialTheme.typography.bodyMedium
            )
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
        } else {
            Text(
                text = stringResource(R.string.tour_delay_no_data),
                style = MaterialTheme.typography.bodyMedium
            )
        }

        Text(
            text = stringResource(R.string.tour_delay_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// ── Step 9: Backup Progress ─────────────────────────────────────────────────

@Composable
private fun BackupProgressContent() {
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
            text = stringResource(R.string.tour_progress_body),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.fillMaxWidth()
        )

        // Circular progress placeholder — the actual progress will come from the backup worker.
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(180.dp)
        ) {
            val trackColor = MaterialTheme.colorScheme.surfaceVariant
            val progressColor = MaterialTheme.colorScheme.primary
            Canvas(modifier = Modifier.size(180.dp)) {
                val stroke = Stroke(width = 12.dp.toPx(), cap = StrokeCap.Round)
                drawArc(trackColor, 0f, 360f, false, style = stroke)
                drawArc(progressColor, -90f, 0f, false, style = stroke)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "0%",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = stringResource(R.string.tour_progress_waiting),
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

// ── Helpers ─────────────────────────────────────────────────────────────────

@Composable
private fun BulletItem(text: String) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(start = 8.dp)
    ) {
        Text("•", style = MaterialTheme.typography.bodyMedium)
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

private fun mediaPermissions(): Array<String> =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO)
    } else {
        arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }
