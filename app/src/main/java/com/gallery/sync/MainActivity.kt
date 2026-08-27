package com.gallery.sync

import android.os.Bundle
import androidx.activity.ComponentActivity
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
import com.gallery.sync.ui.common.NavDestination
import com.gallery.sync.ui.common.SignalIcons
import com.gallery.sync.ui.common.SignalNavBar
import com.gallery.sync.ui.archive.ArchiveScreen
import com.gallery.sync.ui.retrieve.RetrieveScreen
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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

    Column(modifier = modifier.fillMaxSize()) {
        Box(modifier = Modifier.weight(1f)) {
            when (selectedTab) {
                0 -> BackupScreen()
                1 -> RetrieveScreen()
                2 -> ArchiveScreen()
                else -> SettingsScreen(accountName = accountName, onSignOut = onSignOut)
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
