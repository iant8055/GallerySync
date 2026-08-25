package com.gallery.sync

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.ScrollableTabRow
import com.gallery.sync.domain.backup.BackupEngine
import com.gallery.sync.ui.setup.ReconcileScreen
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gallery.sync.ui.backup.BackupScreen
import com.gallery.sync.ui.settings.SettingsScreen
import com.gallery.sync.ui.signin.SignInScreen
import com.gallery.sync.ui.signin.SignInUiState
import com.gallery.sync.ui.signin.SignInViewModel
import com.gallery.sync.data.local.settings.ThemeMode
import com.gallery.sync.ui.theme.GallerySyncTheme
import com.gallery.sync.ui.theme.ThemeViewModel
import dagger.hilt.android.AndroidEntryPoint

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

    when (val current = signInState) {
        is SignInUiState.SignedIn -> SignedInApp(
            accountName = current.accountName,
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
    modifier: Modifier = Modifier
) {
    var selectedTab by remember { mutableIntStateOf(0) }

    Column(modifier = modifier.fillMaxSize()) {
        // Scrollable, not fixed. A fixed TabRow splits the width evenly, so at 320dp with large
        // text "Album Modes" wrapped onto two lines while "Settings" stayed on one, leaving the row
        // uneven and the selected indicator adrift from its label. Scrollable gives each tab the
        // width its own text needs.
        ScrollableTabRow(selectedTabIndex = selectedTab, edgePadding = 0.dp) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = { Text(stringResource(R.string.tab_backup)) }
            )
            // The OneDrive browser is hidden, not deleted. Browsing cloud files is the thumbnail
            // browser the design principle rules out, and v0.4 needs this screen repurposed as the
            // retrieval list — a plain list of what is not on the phone. Settings offers a "Open
            // OneDrive" button meanwhile, which is a better answer than a browser we should not
            // be building. See .claude/tasks/TASK-012.md.
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = { Text(stringResource(R.string.tab_settings)) }
            )
            // Temporary home for the reconciliation step. It belongs in the guided first run as
            // step 6; until that flow exists it lives here so it can be used and checked on real
            // hardware rather than sitting unreachable the way the debug probes did.
            Tab(
                selected = selectedTab == 2,
                onClick = { selectedTab = 2 },
                text = { Text(stringResource(R.string.tab_setup)) }
            )
        }

        when (selectedTab) {
            0 -> BackupScreen()
            2 -> ReconcileScreen(destinationRoot = BackupEngine.REMOTE_ROOT)
            else -> SettingsScreen(accountName = accountName, onSignOut = onSignOut)
        }
    }
}
