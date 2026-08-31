package com.gallery.sync.ui.setup

import android.Manifest
import android.net.Uri
import android.os.Build
import androidx.annotation.StringRes
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gallery.sync.R
import com.gallery.sync.data.local.entity.AlbumMode
import com.gallery.sync.domain.backup.CloudDeletionPolicy
import com.gallery.sync.domain.backup.FirstBackupWindow
import com.gallery.sync.domain.backup.LibraryChoice
import com.gallery.sync.domain.setup.SetupTopic

/**
 * Guided first run.
 *
 * Exists because a gate the user has to *find* is not a gate. TASK-014 specified the two gates on
 * 19 Aug 2026 and they were built as a reachable tab instead. Both fresh installs anyone has ever
 * run — Fold 4 on 26 Aug, Moto G on 28 Aug — landed on an Albums tab reporting "0 Albums, 0 B" and
 * offering a Rescan that could not possibly succeed. The engine was correct both times, and nobody
 * could tell from the screen.
 *
 * What this adds over the tab is only that it cannot be missed. The gates themselves, and every
 * setting behind them, belong to [ReconcileViewModel] and are shared with Settings — so there is
 * one implementation of "which folders" and "what to do with the library", not two that drift.
 *
 * Two deliberate deviations from TASK-014's step order:
 *
 * - **Language and cloud service are omitted.** Only English and only OneDrive exist, and a picker
 *   with one entry reads as broken. Both slots return when there is a second option.
 * - **Sign-in stays ahead of the wizard** rather than inside it. It is already unmissable, has its
 *   own screen, and nothing here can be verified without it.
 */
@Composable
fun SetupWizardScreen(
    modifier: Modifier = Modifier,
    viewModel: ReconcileViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var stepIndex by rememberSaveable { mutableIntStateOf(0) }

    val steps = wizardSteps(
        hasSources = state.hasSources,
        willUpload = state.libraryChoice != LibraryChoice.CHOOSE_PER_ALBUM
    )
    val step = steps[stepIndex.coerceIn(0, steps.lastIndex)]

    val treePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? -> uri?.let(viewModel::addSource) }

    val mediaPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { /* result observed via MediaAccess check on the Albums tab */ }

    val context = LocalContext.current
    LaunchedEffect(step) {
        if (step is WizardStep.SourceFolders) {
            val perms = mediaPermissions()
            val allGranted = perms.all {
                androidx.core.content.ContextCompat.checkSelfPermission(
                    context, it
                ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            }
            if (!allGranted) mediaPermissionLauncher.launch(perms)
        }
    }

    // Capped and centred rather than stretched edge to edge.
    //
    // At targetSdk 37 a large screen cannot be opted out of, and the first build filled the Fold's
    // inner display with one card at the top and roughly sixty percent empty space beneath it. A
    // paragraph is also unreadable at that measure — a line of text wants about 70 characters,
    // not the 140 a folded-open Fold will happily give it. On a phone nothing changes, because the
    // cap is wider than the screen.
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        LinearProgressIndicator(
            progress = { (stepIndex + 1f) / steps.size },
            modifier = Modifier
                .widthIn(max = ContentMaxWidth)
                .fillMaxWidth()
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .widthIn(max = ContentMaxWidth)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            // Centred in the leftover height so the card sits in the middle of a tall screen
            // instead of clinging to the top edge with nothing under it.
            verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically)
        ) {
            when (step) {
                is WizardStep.Topic -> TopicBubble(step.topic)

                WizardStep.SourceFolders -> SourcesStep(
                    state = state,
                    onPick = { treePicker.launch(null) },
                    onRemove = viewModel::removeSource
                )

                WizardStep.ScanReport -> ScanReportStep(state)

                WizardStep.LibraryChoice -> LibraryChoiceStep(
                    state = state,
                    onSelect = viewModel::setLibraryChoice
                )

                WizardStep.DefaultMode -> DefaultModeStep(
                    current = state.defaultAlbumMode,
                    onSelect = viewModel::setDefaultAlbumMode
                )

                WizardStep.AutoOptimise -> ChoiceStep(
                    title = stringResource(R.string.settings_auto_optimise),
                    body = stringResource(R.string.wizard_auto_optimise_body),
                    options = listOf(
                        false to stringResource(R.string.wizard_optimise_ask_first),
                        true to stringResource(R.string.wizard_optimise_automatic)
                    ),
                    selected = state.isAutoOptimiseEnabled,
                    onSelect = viewModel::setAutoOptimiseEnabled
                )

                WizardStep.DeletionPolicy -> ChoiceStep(
                    title = stringResource(R.string.deletion_title),
                    body = stringResource(R.string.wizard_deletion_body),
                    options = listOf(
                        CloudDeletionPolicy.LEAVE to stringResource(R.string.deletion_leave),
                        CloudDeletionPolicy.ASK to stringResource(R.string.deletion_ask)
                    ),
                    selected = state.cloudDeletionPolicy,
                    onSelect = viewModel::setCloudDeletionPolicy
                )

                WizardStep.MobileData -> ChoiceStep(
                    title = stringResource(R.string.backup_allow_metered),
                    body = stringResource(R.string.wizard_metered_body),
                    options = listOf(
                        false to stringResource(R.string.wizard_metered_wifi_only),
                        true to stringResource(R.string.wizard_metered_allow)
                    ),
                    selected = state.allowMeteredNetwork,
                    onSelect = viewModel::setAllowMeteredNetwork
                )

                WizardStep.FirstBackupWindow -> FirstBackupWindowStep(
                    state = state,
                    onHour = viewModel::setFirstBackupStartHour,
                    onCharging = viewModel::setFirstBackupRequiresCharging
                )
            }
        }

        WizardControls(
            modifier = Modifier
                .widthIn(max = ContentMaxWidth)
                .fillMaxWidth(),
            step = step,
            isFirst = stepIndex == 0,
            isLast = stepIndex == steps.lastIndex,
            state = state,
            onBack = { stepIndex-- },
            onNext = {
                val topic = (step as? WizardStep.Topic)?.topic
                if (topic != null && topic.requiresAcknowledgement) {
                    viewModel.acknowledgeTopic(topic.key)
                }
                if (stepIndex == steps.lastIndex) {
                    if (state.libraryChoice != LibraryChoice.CHOOSE_PER_ALBUM) {
                        viewModel.applyLibraryChoice()
                    }
                    viewModel.completeSetup()
                } else {
                    stepIndex++
                }
            },
            onSkip = {
                if (state.hasSources) {
                    viewModel.completeSetup()
                } else {
                    stepIndex = steps.indexOfFirst { it is WizardStep.SourceFolders }
                        .coerceAtLeast(0)
                }
            }
        )
    }
}

/**
 * The steps, in order.
 *
 * Gate 1 comes before the explanations, and the scan report between the gates is the first honest
 * number the user is given about their own library.
 */
private fun wizardSteps(hasSources: Boolean, willUpload: Boolean): List<WizardStep> =
    listOf(WizardStep.SourceFolders)

/** A readable measure. Wider than any phone, so only large screens ever see the cap. */
private val ContentMaxWidth = 600.dp

sealed interface WizardStep {
    data class Topic(val topic: SetupTopic) : WizardStep
    data object SourceFolders : WizardStep
    data object ScanReport : WizardStep
    data object LibraryChoice : WizardStep
    data object DefaultMode : WizardStep
    data object AutoOptimise : WizardStep
    data object DeletionPolicy : WizardStep
    data object MobileData : WizardStep
    data object FirstBackupWindow : WizardStep
}

@Composable
private fun TopicBubble(topic: SetupTopic, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(stringResource(topic.title), style = MaterialTheme.typography.titleLarge)
            Text(stringResource(topic.body), style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
private fun SourcesStep(
    state: ReconcileUiState,
    onPick: () -> Unit,
    onRemove: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = stringResource(R.string.wizard_sources_title),
            style = MaterialTheme.typography.titleLarge
        )
        Text(
            text = stringResource(R.string.wizard_sources_body),
            style = MaterialTheme.typography.bodyMedium
        )

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
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error
            )
        }

        OutlinedButton(onClick = onPick) {
            Text(stringResource(R.string.wizard_sources_add))
        }
    }
}

@Composable
private fun ScanReportStep(state: ReconcileUiState, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = stringResource(R.string.wizard_scan_title),
            style = MaterialTheme.typography.titleLarge
        )
        val result = state.result
        when {
            state.noMediaAccess -> Text(stringResource(R.string.wizard_scan_no_access))
            state.running || result == null -> Text(stringResource(R.string.wizard_scan_running))
            else -> Text(
                // The first honest number the user is given about their own library. Photos and
                // videos are counted apart because Sync treats them differently, and a single
                // "N files" would hide the half that cannot be shrunk.
                text = pluralStringResource(
                    R.plurals.wizard_scan_found,
                    result.albumsChecked,
                    result.photos.files,
                    result.videos.files,
                    result.albumsChecked
                ),
                style = MaterialTheme.typography.bodyLarge
            )
        }
    }
}

@Composable
private fun LibraryChoiceStep(
    state: ReconcileUiState,
    onSelect: (LibraryChoice) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = stringResource(R.string.wizard_library_title),
            style = MaterialTheme.typography.titleLarge
        )
        Text(
            text = stringResource(R.string.wizard_library_body),
            style = MaterialTheme.typography.bodyMedium
        )

        // Archive is deliberately absent. Setting every album to Archive in a wizard, before the
        // user has watched the app work, is the largest irreversible action the product can take,
        // chosen at the moment they know least about it. It stays a per-album decision with its own
        // confirmation.
        // Numbered, because the wording back-references them: options 2 and 3 read "All #1 - plus
        // ...". Ian wrote them that way and asked for them verbatim, and the references only mean
        // anything if the numbers are on the screen.
        LibraryChoice.entries.forEachIndexed { index, choice ->
            val selected = state.libraryChoice == choice
            val label = "${index + 1}. ${stringResource(labelOf(choice))}"
            if (selected) {
                Button(onClick = { onSelect(choice) }, modifier = Modifier.fillMaxWidth()) {
                    Text(label)
                }
            } else {
                OutlinedButton(onClick = { onSelect(choice) }, modifier = Modifier.fillMaxWidth()) {
                    Text(label)
                }
            }

            // The detail under each option, added 28 Aug 2026.
            //
            // This step showed bare labels until now, so every word of the estimate written on
            // 25 Aug - the wording that stops "free space" over-promising on a video library - lived
            // only in ReconcileScreen, which nothing renders. The screen users actually meet offered
            // "Back up and free space" with no indication of how much, or of what it leaves behind.
            //
            // It matters most for the new middle option, whose whole honesty is the two counts: on a
            // typical install it touches a couple of percent of the library.
            detailOf(choice, state)?.let { detail ->
                Text(
                    text = detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * A titled explanation and a short list of mutually exclusive answers.
 *
 * Generic because four of the wizard's questions have that exact shape, and four hand-rolled
 * versions would drift in spacing and in which option reads as chosen.
 */
@Composable
private fun <T> ChoiceStep(
    title: String,
    body: String,
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Blank when this block is nested under a heading that already named the question.
        if (title.isNotBlank()) {
            Text(text = title, style = MaterialTheme.typography.titleLarge)
        }
        Text(text = body, style = MaterialTheme.typography.bodyMedium)

        options.forEach { (value, label) ->
            if (value == selected) {
                Button(onClick = { onSelect(value) }, modifier = Modifier.fillMaxWidth()) {
                    Text(label)
                }
            } else {
                OutlinedButton(onClick = { onSelect(value) }, modifier = Modifier.fillMaxWidth()) {
                    Text(label)
                }
            }
        }
    }
}

/**
 * What a newly discovered album gets.
 *
 * Archive is absent, and is absent for the same reason it is absent from Gate 2: a default that
 * empties the gallery would apply to albums the user has not seen yet. [AlbumMode.canBeDefault]
 * already encodes that, and this reads it rather than restating it.
 */
@Composable
private fun DefaultModeStep(
    current: AlbumMode,
    onSelect: (AlbumMode) -> Unit,
    modifier: Modifier = Modifier
) {
    ChoiceStep(
        title = stringResource(R.string.settings_default_mode),
        body = stringResource(R.string.wizard_default_mode_body),
        options = AlbumMode.canBeDefault.map { mode ->
            mode to stringResource(
                when (mode) {
                    AlbumMode.OFF -> R.string.mode_off
                    AlbumMode.BACKUP -> R.string.mode_backup
                    AlbumMode.SYNC -> R.string.mode_sync
                    AlbumMode.ARCHIVE -> R.string.mode_archive
                }
            )
        },
        selected = current,
        onSelect = onSelect,
        modifier = modifier
    )
}

/**
 * When the first whole-library upload may begin.
 *
 * Offered only when something was set to upload. The first run is the heaviest thing the app ever
 * does, and a run held until 1am is indistinguishable from a broken one unless it was mentioned.
 */
@Composable
private fun FirstBackupWindowStep(
    state: ReconcileUiState,
    onHour: (Int) -> Unit,
    onCharging: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = stringResource(R.string.first_backup_title),
            style = MaterialTheme.typography.titleLarge
        )
        Text(
            text = stringResource(R.string.wizard_first_backup_body),
            style = MaterialTheme.typography.bodyMedium
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FirstBackupWindow.SELECTABLE_HOURS.forEach { hour ->
                val chosen = state.firstBackupStartHour == hour
                if (chosen) {
                    Button(onClick = { onHour(hour) }) { Text(formatHour(hour)) }
                } else {
                    OutlinedButton(onClick = { onHour(hour) }) { Text(formatHour(hour)) }
                }
            }
        }

        ChoiceStep(
            title = "",
            body = stringResource(R.string.wizard_charging_body),
            options = listOf(
                true to stringResource(R.string.wizard_charging_required),
                false to stringResource(R.string.wizard_charging_anytime)
            ),
            selected = state.firstBackupRequiresCharging,
            onSelect = onCharging
        )
    }
}

private fun formatHour(hour: Int): String = when {
    hour == 0 -> "12am"
    hour < 12 -> "${hour}am"
    hour == 12 -> "12pm"
    else -> "${hour - 12}pm"
}

/**
 * The same strings the Settings route uses. One description of each choice, not two.
 */
/**
 * A number under one option, and nothing under the other three.
 *
 * The labels say what each choice does; only "optimise only the new" needs a figure, because how
 * much it touches is the entire difference between it and the option above. On a typical install
 * most of the library is already in OneDrive - 8,482 files reducing to 206 outstanding, measured -
 * so without the count it reads as the same choice and then does almost nothing.
 *
 * Everything else went, at Ian's instruction: a paragraph under each of four options is verbiage on
 * the screen someone meets before they have seen the app do anything.
 */
@Composable
private fun detailOf(choice: LibraryChoice, state: ReconcileUiState): String? {
    if (choice != LibraryChoice.BACK_UP_AND_OPTIMISE_NEW) return null
    val result = state.result ?: return null

    val outstanding = result.outstanding.files
    return stringResource(
        R.string.library_optimise_new_detail,
        outstanding,
        result.backedUp.files + outstanding
    )
}

@StringRes
private fun labelOf(choice: LibraryChoice): Int = when (choice) {
    LibraryChoice.CHOOSE_PER_ALBUM -> R.string.library_per_album
    LibraryChoice.BACK_UP_EVERYTHING -> R.string.library_back_up_all
    LibraryChoice.BACK_UP_AND_OPTIMISE_NEW -> R.string.library_optimise_new
    LibraryChoice.BACK_UP_AND_FREE_SPACE -> R.string.library_free_space
}

@Composable
private fun WizardControls(
    step: WizardStep,
    isFirst: Boolean,
    isLast: Boolean,
    state: ReconcileUiState,
    onBack: () -> Unit,
    onNext: () -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Gate 1 is the one step that cannot be advanced past unanswered: with nothing granted the scan
    // returns nothing, and every screen after it would be describing an empty library.
    val blocked = step is WizardStep.SourceFolders && !state.hasSources
    val acknowledgeLabel = (step as? WizardStep.Topic)?.topic?.acknowledgeLabel

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isFirst) {
                Spacer(Modifier)
            } else {
                TextButton(onClick = onBack) { Text(stringResource(R.string.wizard_back)) }
            }

            Button(onClick = onNext, enabled = !blocked) {
                Text(
                    when {
                        isLast -> stringResource(R.string.wizard_finish)
                        acknowledgeLabel != null -> stringResource(acknowledgeLabel)
                        else -> stringResource(R.string.wizard_next)
                    }
                )
            }
        }

        if (!isLast) {
            TextButton(onClick = onSkip, modifier = Modifier.align(Alignment.End)) {
                Text(stringResource(R.string.wizard_skip))
            }
        }
    }
}

private fun mediaPermissions(): Array<String> =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO)
    } else {
        arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }
