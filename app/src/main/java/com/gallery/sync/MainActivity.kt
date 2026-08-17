package com.gallery.sync

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gallery.sync.ui.browse.BrowseScreen
import com.gallery.sync.ui.signin.SignInScreen
import com.gallery.sync.ui.signin.SignInUiState
import com.gallery.sync.ui.signin.SignInViewModel
import com.gallery.sync.ui.theme.GallerySyncTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            GallerySyncTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    GallerySyncApp(modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }
}

/**
 * Chooses between signing in and browsing.
 *
 * Both branches share one [SignInViewModel] instance, so signing out from the browse screen
 * flows straight back here and swaps the UI without any extra plumbing.
 */
@Composable
private fun GallerySyncApp(modifier: Modifier = Modifier) {
    val signInViewModel: SignInViewModel = hiltViewModel()
    val signInState by signInViewModel.state.collectAsStateWithLifecycle()

    when (signInState) {
        is SignInUiState.SignedIn -> BrowseScreen(
            onSignOut = signInViewModel::signOut,
            modifier = modifier
        )

        else -> SignInScreen(
            modifier = modifier,
            viewModel = signInViewModel
        )
    }
}
