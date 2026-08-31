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
import androidx.compose.material3.Tab
import androidx.compose.material3.ScrollableTabRow
import com.gallery.sync.domain.backup.ExitWarning
import com.gallery.sync.ui.backup.BackupViewModel
import com.gallery.sync.ui.common.ExitWarningDialog
import com.gallery.sync.ui.common.NavDestination
import com.gallery.sync.ui.common.SignalIcons
import com.gallery.sync.ui.common.SignalNavBar
import com.gallery.sync.ui.archive.ArchiveScreen
import com.gallery.sync.ui.restore.RestoreScreen
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.remember
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
import com.gallery.sync.data.local.settings.ThemeMode
import com.gallery.sync.ui.theme.GallerySyncTheme
import com.gallery.sync.ui.theme.ThemeViewModel
import dagger.hilt.android.AndroidEntryPoint
import java.time.Instant

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
                    GallerySyncApp(modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }
}

/**
 * Chooses between signing in and the signed-in app.
 *
 * Both branches share one [SignInViewModel] instance, so signing out flows straight back here and
 * swaps the UI without extra plumbing.
 */
@Composable
private fun GallerySyncApp(modifier: Modifier = Modifier) {
    val signInViewModel: SignInViewModel = hiltViewModel()
    val signInState by signInViewModel.state.collectAsStateWithLifecycle()
    val setupViewModel: ReconcileViewModel = hiltViewModel()
    val setupState by setupViewModel.state.collectAsStateWithLifecycle()

    if (!setupState.setupDecisionReady) {
        Box(modifier.fillMaxSize())
        return
    }

    val needsSetup = !setupState.hasCompletedSetup || !setupState.hasSources

    when {
        needsSetup -> SignedInApp(
            accountName = (signInState as? SignInUiState.SignedIn)?.accountName ?: "",
            onSignOut = signInViewModel::signOut,
            showTour = true,
            setupViewModel = setupViewModel,
            signInViewModel = signInViewModel,
            modifier = modifier
        )

        signInState is SignInUiState.SignedIn -> SignedInApp(
            accountName = (signInState as SignInUiState.SignedIn).accountName,
            onSignOut = signInViewModel::signOut,
            modifier = modifier
        )

        else -> SignInScreen(
            modifier = modifier,
            viewModel = signInViewModel
        )
    }
}

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
    modifier: Modifier = Modifier
) {
    var selectedTab by remember { mutableIntStateOf(0) }

    // Order is the order of use. Albums is what people open the app for; Cloud check and Settings
    // are things done once. Restore moved second because it was the tab falling off the right edge
    // of the old row — a quarter of the app reachable only by a scroll gesture nobody knew was
    // there.
    val destinations = listOf(
        NavDestination(SignalIcons.Albums, stringResource(R.string.tab_backup)),
        NavDestination(SignalIcons.Restore, stringResource(R.string.tab_retrieve)),
        NavDestination(SignalIcons.CloudCheck, stringResource(R.string.tab_setup)),
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
    val archiveTab = 2

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
    val warnOnExit = ExitWarning.shouldWarn(
        readyCount = backupState.redundantCount,
        delayedUntilEpochMillis = backupState.archiveDelayedUntilEpochMillis,
        now = Instant.now()
    )

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

    Column(modifier = modifier.fillMaxSize()) {
        Box(modifier = Modifier.weight(1f)) {
            when (selectedTab) {
                0 -> BackupScreen(onAlbumArchived = { selectedTab = archiveTab })
                1 -> RestoreScreen()
                2 -> ArchiveScreen()
                else -> SettingsScreen(accountName = accountName, onSignOut = onSignOut)
            }

            if (showTour && setupViewModel != null) {
                SetupTour(
                    viewModel = setupViewModel,
                    signInViewModel = signInViewModel,
                    onComplete = { /* state updates drive recomposition — tour disappears */ }
                )
            }
        }

        SignalNavBar(
            destinations = destinations,
            selected = selectedTab,
            onSelect = { selectedTab = it },
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )
    }
}
