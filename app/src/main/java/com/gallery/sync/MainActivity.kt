package com.gallery.sync

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextButton
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import com.gallery.sync.ui.common.labelRes
import androidx.compose.material3.Tab
import androidx.compose.material3.ScrollableTabRow
import com.gallery.sync.domain.backup.ExitWarning
import com.gallery.sync.ui.backup.BackupViewModel
import com.gallery.sync.ui.common.ExitWarningDialog
import com.gallery.sync.domain.backup.CloudFeature
import com.gallery.sync.ui.common.FeatureUnavailableDialog
import androidx.compose.runtime.LaunchedEffect
import com.gallery.sync.ui.common.NavDestination
import com.gallery.sync.ui.common.SignalIcons
import com.gallery.sync.ui.common.SignalNavBar
import com.gallery.sync.ui.deleted.DeletedFilesGate
import com.gallery.sync.ui.help.LocalSetupOnlyGuide
import com.gallery.sync.ui.archive.ArchiveScreen
import com.gallery.sync.ui.restore.RestoreScreen
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gallery.sync.ui.backup.BackupScreen
import com.gallery.sync.ui.settings.SettingsScreen
import com.gallery.sync.ui.setup.ReconcileViewModel
import com.gallery.sync.ui.setup.SetupTour
import com.gallery.sync.ui.signin.SignInScreen
import com.gallery.sync.ui.signin.SignInUiState
import com.gallery.sync.ui.signin.SignInViewModel
import com.gallery.sync.domain.backup.BackupLocation
import com.gallery.sync.domain.backup.capabilities
import com.gallery.sync.ui.settings.CloudProvidersUiState
import com.gallery.sync.ui.settings.CloudProvidersViewModel
import com.gallery.sync.data.local.settings.ThemeMode
import com.gallery.sync.ui.theme.GallerySyncTheme
import com.gallery.sync.ui.theme.ThemeViewModel
import com.gallery.sync.util.RecentsCard
import javax.inject.Inject
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var recentsCard: RecentsCard

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Standard launch mode (no launchMode set in the manifest) always runs onCreate with the
        // Intent that started this instance, whether that is a cold start or a notification tap
        // arriving while the app is already running — so this is read once here rather than needing
        // an onNewIntent override. See ArchiveReadyNotifier.
        val openArchive = intent?.getBooleanExtra(EXTRA_OPEN_ARCHIVE, false) ?: false

        // The net for a flag left set. The wizard hides this app's Recents card while the first
        // backup runs and restores it when that finishes, but a process killed mid-run has nothing
        // left to do the restoring, and an app permanently missing from Recents with no explanation
        // is worse than the swipe it was protecting against. Every launch clears it; the wizard
        // sets it again a moment later if the backup is genuinely still going.
        recentsCard.show()

        enableEdgeToEdge()
        setContent {
            // Read before anything is drawn, so the app opens in the theme the user chose rather
            // than flashing the system one and correcting itself a frame later.
            val themeViewModel: ThemeViewModel = hiltViewModel()
            val themeMode by themeViewModel.themeMode.collectAsStateWithLifecycle()

            GallerySyncTheme(
                darkTheme = when (themeMode) {
                    ThemeMode.SYSTEM -> isSystemInDarkTheme()
                    ThemeMode.LIGHT -> false
                    ThemeMode.DARK -> true
                }
            ) {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    GallerySyncApp(modifier = Modifier.padding(innerPadding), initialTab = if (openArchive) ArchiveTab else 0)
                }
            }
        }
    }

    companion object {
        /** Tells a freshly created [MainActivity] to open on the Archive tab. See [ArchiveReadyNotifier]. */
        const val EXTRA_OPEN_ARCHIVE = "open_archive"
    }
}

/**
 * Which tab index is Archive — shared between [SignedInApp] and the notification's Intent extra
 * above, so the two cannot drift apart.
 */
private const val ArchiveTab = 2

/**
 * Chooses between signing in and the signed-in app.
 *
 * Both branches share one [SignInViewModel] instance, so signing out flows straight back here and
 * swaps the UI without extra plumbing.
 */
@Composable
private fun GallerySyncApp(modifier: Modifier = Modifier, initialTab: Int = 0) {
    val signInViewModel: SignInViewModel = hiltViewModel()
    // Signed in means *a* cloud is connected, not OneDrive in particular: the free tier is one cloud of
    // the user's own choosing (Ian, 24 Sept 2026). The same instance the wizard and Settings use.
    val cloudViewModel: CloudProvidersViewModel = hiltViewModel()
    val cloudState by cloudViewModel.state.collectAsStateWithLifecycle()
    val setupViewModel: ReconcileViewModel = hiltViewModel()
    val setupState by setupViewModel.state.collectAsStateWithLifecycle()

    if (!setupState.setupDecisionReady || !cloudState.loaded) {
        Box(modifier.fillMaxSize())
        return
    }

    val needsSetup = !setupState.hasCompletedSetup || !setupState.hasSources || setupState.firstBackupPending

    when {
        needsSetup || !cloudState.anyConnected -> SignedInApp(
            accountName = cloudAccountName(cloudState),
            onSignOut = { cloudViewModel.disconnect(BackupLocation.ONEDRIVE) },
            showTour = true,
            setupViewModel = setupViewModel,
            signInViewModel = signInViewModel,
            modifier = modifier
            // initialTab not passed here: the wizard overlays every tab while it is visible, and a
            // fresh install cannot have an Archive album ready to notify about in the first place.
        )

        // The window for files deleted from the phone stands in front of the set-up app only, never
        // the wizard: setup is finished and the user is signed in. Ian, 19 Sept 2026.
        // The window about files deleted from the phone asks OneDrive about them, so it only stands in front
        // of the app while OneDrive is connected; a user whose cloud is another one has nothing to be asked.
        else -> {
            val app: @Composable () -> Unit = {
                SignedInApp(
                    accountName = cloudAccountName(cloudState),
                    onSignOut = { cloudViewModel.disconnect(BackupLocation.ONEDRIVE) },
                    initialTab = initialTab,
                    modifier = modifier
                )
            }
            if (cloudState.providers.any { it.location == BackupLocation.ONEDRIVE && it.isConnected }) {
                DeletedFilesGate(modifier = modifier, content = app)
            } else {
                app()
            }
        }
    }
}

/** A name for the header: the OneDrive account if there is one, otherwise the main cloud's label. */
private fun cloudAccountName(state: CloudProvidersUiState): String =
    state.connected.firstOrNull { it.location == BackupLocation.ONEDRIVE }?.accountLabel
        ?: state.connected.firstOrNull { it.location == state.main }?.accountLabel
        ?: state.connected.firstOrNull()?.accountLabel
        ?: ""

/**
 * Backup is first because it is the screen actually used day to day. Settings holds the things
 * set once — which is why they were moved off Backup, where they had crowded the album list
 * down the screen.
 */
@Composable
private fun SignedInApp(
    accountName: String,
    onSignOut: () -> Unit,
    showTour: Boolean = false,
    setupViewModel: ReconcileViewModel? = null,
    signInViewModel: SignInViewModel? = null,
    /** Which tab to open on, e.g. Archive after a "come of age" notification tap. See [MainActivity]. */
    initialTab: Int = 0,
    modifier: Modifier = Modifier
) {
    var selectedTab by remember { mutableIntStateOf(initialTab) }

    // Order is the order of use. Albums is what people open the app for; Cloud check and Settings
    // are things done once. Restore moved second because it was the tab falling off the right edge
    // of the old row — a quarter of the app reachable only by a scroll gesture nobody knew was
    // there.
    // What is live follows the clouds (Ian, 24 Sept 2026: all functionality is based on the cloud service):
    // a tab is live when at least one connected cloud can do what it does, and otherwise stays in the bar
    // greyed, where tapping it says which cloud cannot.
    val cloudViewModel: CloudProvidersViewModel = hiltViewModel()
    val cloudState by cloudViewModel.state.collectAsStateWithLifecycle()
    val connectedClouds = cloudState.providers.filter { it.isConnected }.map { it.location }
    fun featureLocked(feature: CloudFeature?): Boolean =
        feature != null && cloudState.loaded && connectedClouds.none { it.capabilities.supports(feature) }
    var blockedFeature by remember { mutableStateOf<CloudFeature?>(null) }
    fun featureOfTab(tab: Int): CloudFeature? = when (tab) {
        1 -> CloudFeature.RESTORE
        ArchiveTab -> CloudFeature.ARCHIVE
        else -> null
    }

    val destinations = listOf(
        NavDestination(SignalIcons.Albums, stringResource(R.string.tab_backup)),
        NavDestination(SignalIcons.Restore, stringResource(R.string.tab_retrieve), dimmed = featureLocked(CloudFeature.RESTORE)),
        NavDestination(SignalIcons.CloudCheck, stringResource(R.string.tab_setup), dimmed = featureLocked(CloudFeature.ARCHIVE)),
        NavDestination(SignalIcons.Settings, stringResource(R.string.tab_settings))
    )

    // Setting an album to Archive takes the user to the Archive tab. TASK-016 opens its acceptance
    // list with this and it was missed when the tab was built; Ian asked for it on 27 Aug 2026.
    //
    // It follows from what the mode means. Archive is the one mode whose consequence is not
    // immediate — the files stay until they are checked — so leaving the user on the album list
    // after they accept the confirmation tells them nothing about what happens next. The tab is the
    // answer to "and then what?", and arriving there is how the app says the choice was taken
    // seriously.
    val archiveTab = ArchiveTab

    // Leaving with files checked, verified and waiting on one tap.
    //
    // The same BackupViewModel instance the Albums tab uses — both resolve against the Activity's
    // store — so arming this costs no extra scan. ArchiveViewModel is deliberately not touched
    // here: it scans the device on creation, and referencing it at the root would run that on every
    // launch to decide whether to show a dialog that is usually not needed.
    val backupViewModel: BackupViewModel = hiltViewModel()
    val backupState by backupViewModel.state.collectAsStateWithLifecycle()
    val activity = LocalActivity.current

    var showExitWarning by remember { mutableStateOf(false) }
    val warnOnExit = ExitWarning.shouldWarn(readyCount = backupState.redundantCount)

    // Only the back gesture can be caught. Home and a swipe from Recents cannot be, so this is a
    // net rather than a guarantee — see ExitWarning.
    BackHandler(enabled = warnOnExit && !showExitWarning) { showExitWarning = true }

    if (showExitWarning) {
        ExitWarningDialog(
            readyCount = backupState.redundantCount,
            onGoToArchive = {
                showExitWarning = false
                selectedTab = archiveTab
            },
            onLeave = {
                showExitWarning = false
                activity?.finish()
            },
            onDismiss = { showExitWarning = false }
        )
    }

    var tourStep by remember { mutableIntStateOf(1) }

    // Close on the last step puts the wizard away for this session without claiming setup finished.
    // It had nowhere to record that — `onComplete` was an empty lambda, so Close did nothing at all
    // and the only way out of step 9 was to wait for the backup. Finish only appeared to work
    // because completing setup makes the parent stop rendering the tour.
    //
    // Deliberately not persisted: the backup carries on in WorkManager, setup is still unfinished,
    // and the wizard is owed on the next launch — where it resumes at step 9 and re-attaches to the
    // running job.
    var tourDismissed by rememberSaveable { mutableStateOf(false) }
    val tourVisible = showTour && !tourDismissed
    val hideNavBar = tourVisible

    Column(modifier = modifier.fillMaxSize()) {
        Box(modifier = Modifier.weight(1f)) {
            // A locked tab is never drawn — not even when something else lands on it, such as the
            // "come of age" notification opening Archive: the same message stands in its place.
            val lockedFeature = featureOfTab(selectedTab)?.takeIf { featureLocked(it) && !tourVisible }
            if (lockedFeature != null) {
                LaunchedEffect(lockedFeature) { blockedFeature = lockedFeature }
                BackupScreen(onAlbumArchived = {})
            } else when (selectedTab) {
                0 -> BackupScreen(onAlbumArchived = { selectedTab = archiveTab })
                1 -> RestoreScreen()
                2 -> ArchiveScreen()
                else -> SettingsScreen(accountName = accountName, onSignOut = onSignOut)
            }

            if (tourVisible && setupViewModel != null) {
                // Until setup is finished the only part of the guide a person can open is the setup
                // page, so a pop-up shown inside the wizard offers no way into the rest of it.
                CompositionLocalProvider(LocalSetupOnlyGuide provides true) {
                    SetupTour(
                        viewModel = setupViewModel,
                        signInViewModel = signInViewModel,
                        onComplete = { tourDismissed = true },
                        onSwitchTab = { selectedTab = it },
                        onStepChanged = { tourStep = it }
                    )
                }
            }
        }

        if (!hideNavBar) {
            // Hidden for the whole tour since 15 Sept 2026. It used to stay visible because the
            // step 2 cards point at it — but the tour now draws the same bar inside its phone
            // frame, and the real one sat below the frame, outside the picture.
            SignalNavBar(
                destinations = destinations,
                selected = selectedTab,
                onSelect = { tab ->
                    if (!tourVisible) {
                        val locked = featureOfTab(tab)?.takeIf { featureLocked(it) }
                        if (locked != null) blockedFeature = locked else selectedTab = tab
                    }
                },
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
        }

        // The trial has ended with more than one cloud connected: ask once which one stays free. Nothing is
        // moved by the answer, and "Not now" only puts it off until the next launch.
        var keepFreeDeferred by rememberSaveable { mutableStateOf(false) }
        if (cloudState.needsKeepFreeQuestion && !keepFreeDeferred && !tourVisible) {
            AlertDialog(
                onDismissRequest = { keepFreeDeferred = true },
                title = { Text(stringResource(R.string.keep_free_title)) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.keep_free_body))
                        cloudState.connected.forEach { provider ->
                            Button(
                                onClick = { cloudViewModel.keepFree(provider.location) },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(stringResource(R.string.keep_free_keep, stringResource(provider.location.labelRes())))
                            }
                        }
                        OutlinedButton(
                            onClick = { activity?.let(cloudViewModel::unlockPro) },
                            enabled = activity != null,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(stringResource(R.string.google_photos_unlock_pro_action))
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { keepFreeDeferred = true }) { Text(stringResource(R.string.keep_free_not_now)) }
                }
            )
        }

        blockedFeature?.let { feature ->
            FeatureUnavailableDialog(cloud = cloudState.main, feature = feature, onDismiss = { blockedFeature = null })
        }
    }
}
