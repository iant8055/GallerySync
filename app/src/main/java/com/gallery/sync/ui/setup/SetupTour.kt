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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button

import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RichTooltip

import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import kotlin.math.roundToInt
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.ui.graphics.StrokeCap
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gallery.sync.R
import com.gallery.sync.data.local.media.DiscoveredDirectory
import com.gallery.sync.data.local.media.ProxyGenerator
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
 * Content padding for the three video-quality buttons.
 *
 * `ButtonDefaults.ContentPadding` spends 24dp a side, which is most of the ~90dp each button gets
 * when three of them share a dialog. Trimming it to 4dp is what leaves room for the label; the
 * vertical figure is Material's own.
 */
private val QualityButtonPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp)

/**
 * Content padding for the six delay chips.
 *
 * Zero a side, because six of them share the card's width and the content is one or two digits.
 * Material's default would spend 288dp on padding alone across the row.
 */
private val DelayChipPadding = PaddingValues(horizontal = 0.dp, vertical = 8.dp)

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

    // Resume at persisted step if the wizard was interrupted during backup
    val resumeStep = state.wizardStep
    var step by rememberSaveable { mutableIntStateOf(if (resumeStep == TOTAL_STEPS) TOTAL_STEPS else 1) }

    // On relaunch, if the persisted step is 9, jump there and re-observe the worker
    LaunchedEffect(resumeStep) {
        if (resumeStep == TOTAL_STEPS && step != TOTAL_STEPS) {
            step = TOTAL_STEPS
        }
    }

    // Recorded as it goes, not only at the backup step. The upgrade backfill needs to be able to
    // tell "holds grants because the wizard took them" from "holds grants because this install
    // predates the wizard", and the step is the only thing that says which. Resume is unaffected:
    // anything other than the final step still opens at step 1.
    LaunchedEffect(step) {
        onStepChanged(step)
        viewModel.saveWizardStep(step)
    }

    val treePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? -> viewModel.onSafGrantReceived(uri) }

    var safWalkStarted by rememberSaveable { mutableStateOf(false) }

    val proxyLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            viewModel.applyWizardProxies()
        }
    }

    val videoProxyLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            viewModel.applyWizardVideoOptimise()
        }
    }

    // After backup finishes, trigger one-time photo optimise if the library choice calls for it
    LaunchedEffect(state.backupFinished, state.optimiseCandidateCount) {
        if (state.backupFinished && state.optimiseCandidateCount > 0 && !state.optimiseRunning && !state.optimiseFinished) {
            val sender = viewModel.buildWizardProxyRequest()
            if (sender != null) {
                proxyLauncher.launch(
                    androidx.activity.result.IntentSenderRequest.Builder(sender).build()
                )
            }
        }
    }

    // After photo optimise finishes (or was skipped), trigger video optimise
    LaunchedEffect(state.backupFinished, state.optimiseFinished, state.optimiseCandidateCount, state.videoCandidateCount) {
        val photosDone = state.optimiseCandidateCount == 0 || state.optimiseFinished
        if (state.backupFinished && photosDone && state.videoCandidateCount > 0 && !state.videoOptimiseRunning && !state.videoOptimiseFinished) {
            val sender = viewModel.buildWizardVideoRequest()
            if (sender != null) {
                videoProxyLauncher.launch(
                    androidx.activity.result.IntentSenderRequest.Builder(sender).build()
                )
            }
        }
    }

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

    // Re-check the drive once there is an account to check it with.
    //
    // The reconcile fires from the directories flow, which settles at step 4 — one step *before*
    // sign-in at step 5. With no token every listing fails, so `ReconciliationRules.tallyAlbum`
    // files each album under `unchecked` rather than `outstanding`. That is the correct call on its
    // own terms — "could not check" is not "not backed up" — but it leaves `photosOutstanding` and
    // `videosOutstanding` at zero, and every figure downstream is computed from those two. Step 7
    // then offers optimising with no sizes against it and step 8 quotes an empty backup.
    //
    // Confirmed on the Moto G, 4 Sept 2026: `album_cloud_status` held three rows, all written
    // 12:07:17, all `couldNotCheck = 1`, while the card sat there with both switches on and nothing
    // to show for them.
    //
    // Once per sign-in, not once per recomposition: `start()` cancels the run in flight and blanks
    // the result, so a re-entrant call would keep the wizard permanently mid-check.
    var reconciledAfterSignIn by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(isSignedIn) {
        if (!isSignedIn) {
            // Signing out invalidates the figures as surely as never having signed in. Arming the
            // flag again means a user who switches account mid-setup gets the new drive's answer
            // rather than the old one's.
            reconciledAfterSignIn = false
        } else if (!reconciledAfterSignIn) {
            reconciledAfterSignIn = true
            viewModel.start()
        }
    }

    fun canAdvance(): Boolean = when (step) {
        4 -> state.directoryChecks.values.any { it }
        5 -> isSignedIn
        else -> true
    }

    // Step 9 is three phases, not one. Uploading ends, and then photo proxies and video proxies
    // run — usually with no dialog at all, because the SAF tree grant already covers the files.
    // The screen used to be told only whether the *upload* had finished, so it hit 100%, said
    // "Press Finish", and sat there through an optimise it could not describe and a button that
    // was still labelled Close.
    // Choosing to back up manually makes step 8 the last step: there is no backup to watch, so the
    // wizard ends there. Derived once and used by the button label, the branch that acts on it, and
    // the check for whether Back should still be offered — three places that must agree.
    val skipsBackupStep = !state.libraryChoice.uploads
    val lastStep = if (skipsBackupStep) TOTAL_STEPS - 1 else TOTAL_STEPS

    val photosOptimised = state.optimiseCandidateCount == 0 || state.optimiseFinished
    val videoOptimised = state.videoCandidateCount == 0 || state.videoOptimiseFinished

    // The delayed start, counted in real time.
    //
    // The due instant lives in the DataStore rather than here, so closing the wizard or losing the
    // process does not silently cancel the wait — a delay whose whole purpose is that the user
    // walks away must survive the user walking away.
    val startAt = state.firstBackupStartAtEpochMillis
    var nowMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(startAt) {
        // Ticks only while something is waiting on it, and stops at zero rather than spinning for
        // the rest of the session.
        while (startAt != null && System.currentTimeMillis() < startAt) {
            nowMillis = System.currentTimeMillis()
            delay(1000)
        }
        nowMillis = System.currentTimeMillis()
    }
    val remainingMillis = if (startAt == null) 0L else (startAt - nowMillis).coerceAtLeast(0L)
    val waitingForDelay = startAt != null && remainingMillis > 0L

    val backupPhase = when {
        waitingForDelay -> WizardBackupPhase.WAITING
        !state.backupFinished -> WizardBackupPhase.UPLOADING
        !photosOptimised -> WizardBackupPhase.OPTIMISING_PHOTOS
        !videoOptimised -> WizardBackupPhase.OPTIMISING_VIDEO
        else -> WizardBackupPhase.DONE
    }
    val backupComplete = backupPhase == WizardBackupPhase.DONE

    // A pending delay is handed to WorkManager, which owns it from then on: it fires with the app
    // closed, killed, or sitting on this card. The countdown here only draws what WorkManager is
    // already committed to, which is why expiry watches rather than enqueues — starting again would
    // replace a chain that may already be uploading.
    LaunchedEffect(step, waitingForDelay) {
        if (step != TOTAL_STEPS) return@LaunchedEffect
        when {
            waitingForDelay -> viewModel.scheduleDelayedBackup()
            startAt != null -> viewModel.onDelayElapsed()
            resumeStep == TOTAL_STEPS -> viewModel.observeBackupWorker()
            else -> viewModel.startBackupWorker()
        }
    }

    // Discover once permission is in hand. The request itself is **not** fired on arrival: the
    // system dialog cannot be reworded, so landing on it cold is the whole reason it reads as
    // unexplained. Step 4 states the case first and the user raises the dialog from the card.
    LaunchedEffect(mediaGranted, step) {
        if (mediaGranted && step == 4 && state.discoveredDirectories.isEmpty() && !state.discoveryRunning) {
            viewModel.discoverDirectories()
        }
    }

    // Walk through SAF tree pickers for each checked directory
    LaunchedEffect(state.safGrantQueue, safWalkStarted) {
        if (safWalkStarted && state.safGrantQueue.isNotEmpty()) {
            val dir = state.safGrantQueue.first()
            val initialUri = android.provider.DocumentsContract.buildDocumentUri(
                "com.android.externalstorage.documents",
                "primary:$dir"
            )
            treePicker.launch(initialUri)
        } else if (safWalkStarted && state.safGrantQueue.isEmpty()) {
            safWalkStarted = false
            step = 5
        }
    }

    val onNext: () -> Unit = {
        when {
            step == 4 -> {
                viewModel.saveSelectedDirectories()
                if (viewModel.buildSafGrantQueue()) {
                    safWalkStarted = true
                } else {
                    step = 5
                }
            }
            step == 6 -> {
                step = if (showOptimization) 7 else 8
            }
            step == TOTAL_STEPS -> {
                // Two buttons, two actions, one branch — which is how they got confused.
                //
                // Finish means setup is done: record it and put the wizard away, leaving the user
                // in the app. Close means the backup is still going and the user is leaving; it
                // closes the app, and the WorkManager chain carries on without it.
                //
                // Both used to run through onComplete. aee7125 made that activity.finish(), which
                // was right for Close and wrong for Finish — it killed the app on a completed
                // setup. b6e60f2 fixed Finish by emptying the lambda and took Close with it, so
                // Close did nothing at all until 3 Sept.
                if (backupComplete) {
                    viewModel.completeSetup()
                    onComplete()
                } else {
                    // `finishAndRemoveTask`, not `finish`. Ian, 4 Sept 2026: Close "doesn't
                    // actually close the app — it just minimizes it". Checked on the Moto G and he
                    // is right in the way that matters: `finish()` did destroy the activity, but
                    // the task stayed in Recents as a live-looking card, which is what minimising
                    // looks like. Removing the task is what the word Close promises.
                    //
                    // The process is deliberately left alone. The upload runs inside it, so killing
                    // it here would stop the very thing Close exists to leave running — the whole
                    // point of this button is that the user departs and the backup does not.
                    activity?.finishAndRemoveTask()
                }
            }
            // Choosing to do it manually skips the backup step entirely and opens the app.
            //
            // CHOOSE_PER_ALBUM is "check cloud storage but do not back up any new files", and step 9
            // did it anyway: `outstandingCountAll` counts every unuploaded row with no regard for
            // album mode, and the run it enqueues passes `allAlbums = true`, which routes the worker
            // past mode filtering on purpose. So the one choice that exists to prevent an upload
            // started one. Setup is complete for this user — there is nothing left for the wizard
            // to hold them for.
            step == lastStep && skipsBackupStep -> {
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

    // Back out of a run in progress — deliberately, and only deliberately.
    //
    // Ian, 4 Sept 2026: a user may well want to stop and change a setting once they see what the
    // backup is actually doing. That is a reasonable thing to want, and the earlier defect was never
    // that Back existed — it was that Back silently cancelled the upload as a side effect of
    // re-arming a delay. The difference between a feature and that bug is the confirmation.
    var confirmAbort by rememberSaveable { mutableStateOf(false) }
    val runInProgress = step == TOTAL_STEPS &&
        backupPhase != WizardBackupPhase.WAITING &&
        backupPhase != WizardBackupPhase.DONE
    val onBackRequest: () -> Unit = {
        if (runInProgress) confirmAbort = true else onBack()
    }

    Box(modifier = modifier.fillMaxSize()) {
        // Fully opaque background — the user hasn't set anything up yet so there is
        // nothing worth showing behind the tour.
        //
        // This never comes off. Lifting it for the Help card put the live Settings screen behind
        // a wizard card on 3 Sept 2026: real controls, reachable through the gaps around the card,
        // including the deletion behaviour and the default album mode. A tour shows mockups.
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
                    isLast = step == lastStep,
                    // Finish means "setup is done, open the app". That is true on step 8 for a
                    // manual backup, and on step 9 only once everything has uploaded and optimised.
                    lastButtonLabel = if (backupComplete || skipsBackupStep)
                        stringResource(R.string.wizard_finish_label)
                    else
                        stringResource(R.string.wizard_close_label),
                    // No way back once bytes are moving. Ian, 4 Sept 2026: Back from the progress
                    // card returned to the delay card, where arming a delay re-enqueued the manual
                    // chain under `ExistingWorkPolicy.REPLACE` — which cancels the run in flight.
                    // Observed stopping a live upload dead at 9 of 155 files.
                    //
                    // Waiting is the one progress state Back still makes sense from: nothing has
                    // started, and changing your mind about an hour's delay is a reasonable thing
                    // to want.
                    // Offered right up until the run finishes, where the only thing left is Finish.
                    canGoBack = step != TOTAL_STEPS ||
                        backupPhase != WizardBackupPhase.DONE,
                    backLabel = if (runInProgress) stringResource(R.string.wizard_cancel) else "",
                    onNext = onNext,
                    onBack = onBackRequest
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
                            onDelaySelected = viewModel::setFirstBackupDelay,
                            onStartNowSelected = viewModel::clearFirstBackupDelay
                        )
                        9 -> BackupProgressContent(
                            completed = state.backupCompleted,
                            total = state.backupTotal,
                            currentFile = state.backupCurrentFile,
                            isRunning = state.backupRunning,
                            phase = backupPhase,
                            optimiseDone = state.optimiseProgressDone,
                            optimiseTotal = state.optimiseProgressTotal,
                            remainingMillis = remainingMillis,
                            delayTotalMillis = state.firstBackupDelayMillis ?: 0L,
                            onSyncNow = viewModel::startBackupNow
                        )
                    }
                }
            }
        }

        if (confirmAbort) {
            AlertDialog(
                onDismissRequest = { confirmAbort = false },
                title = { Text(stringResource(R.string.abort_backup_title)) },
                text = { Text(stringResource(R.string.abort_backup_body)) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            confirmAbort = false
                            viewModel.abortBackup()
                            step = TOTAL_STEPS - 1
                        }
                    ) {
                        Text(stringResource(R.string.abort_backup_confirm))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { confirmAbort = false }) {
                        Text(stringResource(R.string.abort_backup_keep))
                    }
                }
            )
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
    /** Only decides whether Back is offered — nothing is drawn from it. */
    stepNumber: Int,
    canAdvance: Boolean,
    isLast: Boolean,
    lastButtonLabel: String = "",
    /** False withdraws Back entirely, for a step there is no going back from. */
    canGoBack: Boolean = true,
    /**
     * What the back control says.
     *
     * "Cancel" while a backup is running, because there the button stops work rather than retracing
     * a step, and a control labelled Back should not be the one that halts an upload.
     */
    backLabel: String = "",
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
            content()

            // Navigation
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (stepNumber > 1 && canGoBack) {
                    TextButton(onClick = onBack) {
                        Text(backLabel.ifEmpty { stringResource(R.string.wizard_back) })
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

@OptIn(ExperimentalMaterial3Api::class)
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

    // Where the mockup put the help button the Help card points at, and where this overlay sits,
    // so the two can be expressed in the same coordinates.
    var helpIconInRoot by remember { mutableStateOf<Rect?>(null) }
    var overlayOrigin by remember { mutableStateOf(Offset.Zero) }
    val spotlight = helpIconInRoot?.translate(-overlayOrigin.x, -overlayOrigin.y)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onGloballyPositioned { overlayOrigin = it.positionInRoot() }
    ) {
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
            // Help sits on the settings mockup, because that is where the help buttons it is
            // describing live. A mockup and not the real tab — see the root background above.
            val isHelp = currentTab.tabIndex < 0

            // Mockup background — shows what the app looks like when populated
            when (tabIndex) {
                0 -> AlbumsMockup()
                1 -> RestoreMockup()
                2 -> ArchiveMockup()
                3 -> SettingsMockup()
                else -> SettingsMockup(onHelpIconPositioned = { helpIconInRoot = it })
            }

            // Scrim so the card pops over the mockup. On the Help card it is punched through at
            // the help button and ringed, so the tooltip below reads as having come from pressing
            // it rather than from nowhere.
            val scrimColor = MaterialTheme.colorScheme.scrim.copy(alpha = 0.4f)
            val stencilColor = MaterialTheme.colorScheme.scrim
            val ringColor = MaterialTheme.colorScheme.primary
            val ringWidth = with(LocalDensity.current) { 3.dp.toPx() }
            Canvas(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
            ) {
                drawRect(scrimColor)
                if (isHelp) {
                    spotlight?.let { rect ->
                        val radius = rect.maxDimension * 0.85f
                        drawCircle(stencilColor, radius, rect.center, blendMode = BlendMode.Clear)
                        drawCircle(ringColor, radius, rect.center, style = Stroke(ringWidth))
                    }
                }
            }

            // One real help bubble, open, anchored on the ringed button so it is obvious what
            // raised it. Same RichTooltip the section headers use, so the tour cannot drift from
            // what the app actually shows.
            if (isHelp && spotlight != null) {
                val helpTooltipState = rememberTooltipState(isPersistent = true)
                LaunchedEffect(Unit) { helpTooltipState.show() }

                Box(
                    modifier = Modifier
                        .offset { IntOffset(spotlight.left.roundToInt(), spotlight.top.roundToInt()) }
                        .size(with(LocalDensity.current) { spotlight.maxDimension.toDp() })
                ) {
                    TooltipBox(
                        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(),
                        tooltip = {
                            RichTooltip(
                                colors = TooltipDefaults.richTooltipColors(
                                    contentColor = MaterialTheme.colorScheme.onSurface
                                )
                            ) {
                                Text(stringResource(R.string.help_backup))
                            }
                        },
                        state = helpTooltipState,
                        enableUserInput = false
                    ) {
                        Spacer(Modifier.fillMaxSize())
                    }
                }
            }

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
                        modifier = Modifier.weight(if (isHelp) 4.5f else 3.95f),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Surface(
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
                            shape = MaterialTheme.shapes.large,
                            color = MaterialTheme.colorScheme.surface,
                            tonalElevation = 6.dp,
                            shadowElevation = 8.dp
                        ) {
                            Column(
                                modifier = Modifier.padding(30.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(15.dp)
                            ) {
                                Icon(
                                    imageVector = currentTab.icon,
                                    contentDescription = null,
                                    modifier = Modifier.size(60.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = stringResource(currentTab.labelRes),
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center
                                )
                                Text(
                                    text = stringResource(currentTab.descRes),
                                    style = MaterialTheme.typography.bodyLarge,
                                    textAlign = TextAlign.Center,
                                    color = MaterialTheme.colorScheme.onSurface
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

                // The arrow keeps the weights the card used to have, so it still points at the tab
                // rather than drifting inward with the wider card above it.
                if (tabIndex <= 3) {
                    Row(modifier = Modifier.fillMaxWidth()) {
                        if (tabIndex > 0) {
                            Spacer(Modifier.weight(tabIndex.toFloat()))
                        }
                        Box(
                            modifier = Modifier.weight(2.5f),
                            contentAlignment = Alignment.TopCenter
                        ) {
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
                        val remaining = 3 - tabIndex
                        if (remaining > 0) {
                            Spacer(Modifier.weight(remaining.toFloat()))
                        }
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

        // Listed in the order they actually happen, and naming both grants separately: the two
        // system dialogs look alike and cannot be reworded, so this is the only place the
        // difference between searching and changing can be made before they appear.
        BulletItem(stringResource(R.string.tour_install_search))
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
            text = stringResource(
                if (hasMediaPermission) R.string.tour_discover_title else R.string.tour_search_title
            ),
            style = MaterialTheme.typography.titleLarge
        )
        Text(
            text = stringResource(
                if (hasMediaPermission) R.string.tour_discover_body else R.string.tour_search_body
            ),
            style = MaterialTheme.typography.bodyMedium
        )

        if (!hasMediaPermission) {
            Button(onClick = onGrantMediaAccess) {
                Text(stringResource(R.string.tour_search_action))
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
                color = MaterialTheme.colorScheme.onSurface
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
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = formatBytes(context, directory.totalBytes),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface
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

            // The path takes the slack and the button keeps its own width. SpaceBetween let a
            // long destination squeeze the button until "Change" rendered as "Chang" — a button
            // narrower than its own label, which is how a truncated label happens at all.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.tour_cloud_destination),
                        style = MaterialTheme.typography.labelMedium
                    )
                    Text(
                        text = state.destinationRoot,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
                // Filled, not outlined. Outlined read as a label with a box round it here, next to
                // two lines of plain text; filled is unambiguous.
                Button(onClick = onChangeDestination) {
                    Text(stringResource(R.string.tour_cloud_change), maxLines = 1)
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

    // What proxying gives back, not what the photos weigh.
    //
    // Until 4 Sept 2026 this quoted `totalPhotoBytes` whole — a claim that every photo comes back
    // as zero bytes. Ian caught it from the outside: the card offered to save 1.2 GB out of a 1.26 GB
    // DCIM, which is the whole library and change. A proxy is a smaller file, not an absent one.
    val photoSavingBytes = totalPhotoBytes * ProxyGenerator.APPROXIMATE_SAVING_PERCENT / 100

    // Why the estimate is missing, when it is missing.
    //
    // Both tallies read zero whether the library is fully backed up or the drive was unreachable,
    // and those want opposite things said about them. Anything the card cannot compute it now
    // names; the one thing it must never do is leave the space blank and let the user decide for
    // themselves what a switch with no figure under it means.
    val stillChecking = state.running || result == null
    val albumsUnchecked = result?.albumsUnchecked ?: 0
    val nothingOutstanding = totalPhotoBytes == 0L && totalVideoBytes == 0L
    val estimateUnavailable = !stillChecking && nothingOutstanding && albumsUnchecked > 0
    val nothingLeftToSend = !stillChecking && nothingOutstanding && albumsUnchecked == 0

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

        if (localPhotoChecked && photoSavingBytes > 0) {
            Text(
                text = stringResource(
                    R.string.tour_optimise_photos_saving,
                    formatBytes(context, photoSavingBytes)
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
                    color = MaterialTheme.colorScheme.onSurface
                )
                // The name alone, never "High — 480p".
                //
                // Three buttons share the dialog's width, so each gets about a third of it — under
                // 100dp on the Fold's 344dp cover screen, and not much more on the Moto G. The
                // combined label does not fit in that, and Compose does not shrink it: it wraps
                // wherever it happens to land, which on 4 Sept 2026 produced "Medi / um / — / 720p"
                // and "Low / — / 1080 / p" stacked four lines high.
                //
                // Ian dropped the resolution from the buttons on 4 Sept 2026, having seen them fixed
                // with it. Worth knowing what that spends: `VideoQuality`'s own note argues no option
                // should be a bare adjective, since "High" on its own reads as high *quality* when it
                // means high *shrinking*. What still answers that is the heading — "Video optimization
                // level" names the axis before the options are read — and the saving line underneath,
                // which gives the selected level a figure. The resolutions remain on the Settings
                // dropdown, where there is a full row to draw them on.
                //
                // `softWrap = false` stays regardless: one word is what fits, and only if nothing is
                // allowed to break it.
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    VideoQuality.entries.forEach { quality ->
                        val isSelected = videoQuality == quality
                        val name = stringResource(
                            when (quality) {
                                VideoQuality.High -> R.string.video_quality_high_name
                                VideoQuality.Medium -> R.string.video_quality_medium_name
                                VideoQuality.Low -> R.string.video_quality_low_name
                            }
                        )
                        if (isSelected) {
                            Button(
                                onClick = { onVideoQualityChanged(quality) },
                                modifier = Modifier.weight(1f),
                                shape = MaterialTheme.shapes.medium,
                                contentPadding = QualityButtonPadding
                            ) {
                                Text(
                                    text = name,
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        } else {
                            OutlinedButton(
                                onClick = { onVideoQualityChanged(quality) },
                                modifier = Modifier.weight(1f),
                                shape = MaterialTheme.shapes.medium,
                                contentPadding = QualityButtonPadding
                            ) {
                                Text(
                                    text = name,
                                    style = MaterialTheme.typography.labelLarge,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }
                    }
                }

                if (totalVideoBytes > 0) {
                    val savingBytes = totalVideoBytes * videoQuality.approximateSavingPercent / 100
                    Text(
                        text = stringResource(
                            R.string.tour_optimise_video_saving,
                            formatBytes(context, savingBytes)
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        // Total savings, or why there is no total to give
        if (localPhotoChecked || localVideoChecked) {
            HorizontalDivider()
            val photoSaving = if (localPhotoChecked) photoSavingBytes else 0L
            val videoSaving = if (localVideoChecked)
                totalVideoBytes * videoQuality.approximateSavingPercent / 100 else 0L
            val total = photoSaving + videoSaving

            when {
                total > 0 -> {
                    Text(
                        text = stringResource(
                            R.string.tour_optimise_total_saving,
                            formatBytes(context, total)
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    // A partial check gives a real figure that is only part of the answer. Say the
                    // figure is a floor rather than quietly under-promising.
                    if (albumsUnchecked > 0) {
                        Text(
                            text = stringResource(
                                R.string.tour_optimise_incomplete,
                                albumsUnchecked
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                stillChecking -> Text(
                    text = stringResource(R.string.tour_optimise_checking),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                estimateUnavailable -> Text(
                    text = stringResource(R.string.tour_optimise_unchecked),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                nothingLeftToSend -> Text(
                    text = stringResource(R.string.tour_optimise_nothing),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Text(
            text = stringResource(R.string.tour_optimise_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

// ── Step 8: Backup Summary ──────────────────────────────────────────────────

@Composable
private fun BackupDelayContent(
    state: ReconcileUiState,
    onDelaySelected: (Int) -> Unit,
    onStartNowSelected: () -> Unit
) {
    val result = state.result
    val context = LocalContext.current

    val totalBackupBytes = result?.outstanding?.bytes ?: 0L

    val photoSaving = if (state.isAutoOptimiseEnabled)
        (result?.photosOutstanding?.bytes ?: 0L) *
            ProxyGenerator.APPROXIMATE_SAVING_PERCENT / 100
    else 0L
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
                onClick = { startNow = true; onStartNowSelected() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.tour_delay_right_now))
            }
        } else {
            OutlinedButton(
                onClick = { startNow = true; onStartNowSelected() },
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
                Text(
                    pluralStringResource(
                        R.plurals.tour_delay_later, delayHours, delayHours
                    )
                )
            }
        } else {
            OutlinedButton(
                onClick = { startNow = false; onDelaySelected(delayHours) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    pluralStringResource(
                        R.plurals.tour_delay_later, delayHours, delayHours
                    )
                )
            }
        }

        AnimatedVisibility(
            visible = !startNow,
            enter = expandVertically(),
            exit = shrinkVertically()
        ) {
            // Six chips need the whole width, so the label sits above them rather than beside.
            //
            // Sharing one row with "Delay" left each chip about 55dp against the 48dp that
            // `ButtonDefaults.ContentPadding` spends on horizontal padding alone. Seen on the Moto G,
            // 4 Sept 2026: "12" and "24" broke across two lines mid-number and the last chip was
            // clipped by the edge of the card. Same failure as the video-quality buttons, one row
            // further down the wizard.
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    stringResource(R.string.tour_delay_hours_label),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    listOf(1, 2, 4, 8, 12, 24).forEach { hours ->
                        val isSelected = delayHours == hours
                        if (isSelected) {
                            Button(
                                onClick = {},
                                modifier = Modifier.weight(1f),
                                shape = MaterialTheme.shapes.medium,
                                contentPadding = DelayChipPadding
                            ) {
                                Text(
                                    text = "$hours",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        } else {
                            OutlinedButton(
                                onClick = { delayHours = hours; onDelaySelected(hours) },
                                modifier = Modifier.weight(1f),
                                shape = MaterialTheme.shapes.medium,
                                contentPadding = DelayChipPadding
                            ) {
                                Text(
                                    text = "$hours",
                                    style = MaterialTheme.typography.labelLarge,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }
                    }
                }
            }
        }

        HorizontalDivider()

        Text(
            text = stringResource(R.string.tour_delay_security),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface
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
    phase: WizardBackupPhase,
    optimiseDone: Int,
    optimiseTotal: Int,
    remainingMillis: Long,
    delayTotalMillis: Long,
    onSyncNow: () -> Unit
) {
    val uploading = phase == WizardBackupPhase.UPLOADING
    val waiting = phase == WizardBackupPhase.WAITING
    // Each pass drives the ring from its own counters, so it fills three times: upload, photos,
    // video.
    //
    // It used to pin at 100% through both optimise passes, on the reasoning that the bytes were
    // already safe in OneDrive and a bar dropping back to zero would read as the backup coming
    // undone. Ian asked for the reset on 4 Sept 2026, after watching a run sit at a full ring for
    // four minutes with no sense of how far through it was.
    //
    // What makes the reset safe now is the label directly beneath it. When the ring says
    // "Optimising photos / 85 of 150", a half-full ring plainly measures the photo pass rather than
    // the backup — the ambiguity the old comment guarded against was created by the bare word
    // "Optimising", and naming the phase is what removed it.
    //
    // `applyWizardProxies` and `applyWizardVideoOptimise` each zero `optimiseProgress*` on entry, so
    // the video pass starts from empty rather than inheriting where the photos finished.
    val percent = when {
        phase == WizardBackupPhase.DONE -> 100
        uploading && total > 0 -> ((completed * 100) / total).coerceIn(0, 100)
        uploading -> 0
        optimiseTotal > 0 -> ((optimiseDone * 100) / optimiseTotal).coerceIn(0, 100)
        // Counted but not started, or started but not yet counted: an empty ring is honest, and it
        // fills within a file or two.
        else -> 0
    }
    // Waiting depletes rather than fills: a countdown that emptied into the start of the upload,
    // which then fills again, reads as a timer running out and a job beginning.
    val countdownFraction =
        if (waiting && delayTotalMillis > 0L) {
            (remainingMillis.toFloat() / delayTotalMillis).coerceIn(0f, 1f)
        } else 0f
    val sweepAngle = if (waiting) countdownFraction * 360f else percent * 3.6f

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
            text = stringResource(
                when (phase) {
                    WizardBackupPhase.WAITING -> R.string.tour_progress_waiting_body
                    WizardBackupPhase.UPLOADING -> R.string.tour_progress_body
                    WizardBackupPhase.OPTIMISING_PHOTOS -> R.string.tour_progress_optimising_photos
                    WizardBackupPhase.OPTIMISING_VIDEO -> R.string.tour_progress_optimising_video
                    WizardBackupPhase.DONE -> R.string.tour_progress_complete
                }
            ),
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
                // A percentage nobody has counted yet is not zero.
                //
                // Reopening mid-phase builds a fresh view model, so `optimiseTotal` is 0 until the
                // first ledger read returns and the ring announced a confident "0%" before jumping
                // to the truth. Ian, 4 Sept 2026. Same rule the count line below already follows:
                // say nothing rather than say zero.
                val countPending = !waiting &&
                    phase != WizardBackupPhase.UPLOADING &&
                    phase != WizardBackupPhase.DONE &&
                    optimiseTotal == 0
                Text(
                    text = when {
                        waiting -> formatCountdown(remainingMillis)
                        countPending -> "…"
                        else -> "$percent%"
                    },
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold
                )
                // What the phase is, and underneath it how far through. Ian, 4 Sept 2026: the
                // count belongs on its own line rather than run together with the name — the ring
                // is 180dp wide and "Optimising photos 85 of 150" would wrap awkwardly inside it.
                val ringLabel = when {
                    waiting -> stringResource(R.string.tour_progress_until_start)
                    phase == WizardBackupPhase.DONE ->
                        stringResource(R.string.wizard_finish_label)
                    phase == WizardBackupPhase.OPTIMISING_PHOTOS ->
                        stringResource(R.string.tour_progress_optimising_photos_label)
                    phase == WizardBackupPhase.OPTIMISING_VIDEO ->
                        stringResource(R.string.tour_progress_optimising_video_label)
                    // Nothing has landed yet: the count would read "0 of 155" through
                    // WorkManager's start-up, the whole first upload and the poll lag behind it,
                    // which looks stuck rather than busy.
                    total > 0 && completed == 0 && isRunning ->
                        stringResource(R.string.tour_progress_starting)
                    total > 0 -> stringResource(
                        R.string.tour_progress_uploading, completed, total
                    )
                    isRunning -> stringResource(R.string.tour_progress_scanning)
                    else -> stringResource(R.string.tour_progress_waiting)
                }

                // No total yet means the phase has started but the batch has not been counted.
                // "0 of 0" would be worse than saying nothing, so the line is simply absent.
                val ringCount = when {
                    phase != WizardBackupPhase.OPTIMISING_PHOTOS &&
                        phase != WizardBackupPhase.OPTIMISING_VIDEO -> null
                    optimiseTotal > 0 -> stringResource(
                        R.string.tour_progress_count, optimiseDone, optimiseTotal
                    )
                    else -> null
                }

                Text(
                    text = ringLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (ringCount != null) {
                    Text(
                        text = ringCount,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        // The escape hatch. A delay is a preference, not a commitment, and the user who set it an
        // hour ago is the same one now looking at the phone deciding they would rather get on with
        // it. Starting clears the stored due time, so the countdown does not fire again behind it.
        if (waiting) {
            Button(
                onClick = onSyncNow,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.tour_progress_sync_now))
            }
        }

        Text(
            text = stringResource(R.string.tour_progress_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
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
private fun SettingsMockup(
    onHelpIconPositioned: ((Rect) -> Unit)? = null
) {
    val signal = LocalGallerySyncColors.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(start = 16.dp, end = 16.dp, top = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // General section
        MockSectionHeader("General")
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
        MockSectionHeader("Backup", onHelpPositioned = onHelpIconPositioned)
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
        MockSectionHeader("Sync")
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

/**
 * A section header as Settings draws it, help button included.
 *
 * Inert — this is a mockup, and the tour must never put a live control behind a wizard card. The
 * button is here because step 2's Help card points at it; [onHelpPositioned] reports where it
 * landed so the tour can ring it and hang the tooltip off it.
 */
@Composable
private fun MockSectionHeader(
    title: String,
    onHelpPositioned: ((Rect) -> Unit)? = null
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.primary
        )
        Icon(
            imageVector = SignalIcons.Help,
            contentDescription = null,
            modifier = Modifier
                .size(24.dp)
                .onGloballyPositioned { coords ->
                    onHelpPositioned?.invoke(coords.boundsInRoot())
                }
        )
    }
}

/** Which of step 9's three phases is running. See where it is derived, in the tour body. */
/**
 * `h:mm:ss` once past an hour, `m:ss` below it.
 *
 * Seconds are shown throughout, because a countdown whose largest unit is minutes looks frozen for
 * a minute at a time — the thing this screen exists to avoid.
 */
private fun formatCountdown(millis: Long): String {
    val totalSeconds = millis / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%d:%02d".format(minutes, seconds)
    }
}

private enum class WizardBackupPhase {
    /** A delay was chosen and has not elapsed. Nothing is enqueued yet. */
    WAITING,
    UPLOADING,
    OPTIMISING_PHOTOS,
    OPTIMISING_VIDEO,
    DONE
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
