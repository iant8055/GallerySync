package com.gallery.sync.ui.signin

import android.app.Activity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * Sign-in screen for the OneDrive account.
 *
 * Deliberately minimal — this exists so the OneDrive stack can be exercised against a real
 * account. The browsing UI it will eventually sit behind is a later milestone.
 */
@Composable
fun SignInScreen(
    modifier: Modifier = Modifier,
    viewModel: SignInViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    // MSAL needs a foreground Activity to host the sign-in browser tab.
    val activity = LocalContext.current as? Activity

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically)
    ) {
        when (val current = state) {
            SignInUiState.Loading, SignInUiState.Working -> {
                CircularProgressIndicator(modifier = Modifier.size(40.dp))
            }

            SignInUiState.SignedOut -> {
                Text(
                    text = "Connect your OneDrive account to browse your photos and videos.",
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center
                )
                Button(
                    onClick = { activity?.let(viewModel::signIn) },
                    enabled = activity != null
                ) {
                    Text("Sign in with Microsoft")
                }
            }

            is SignInUiState.SignedIn -> {
                Text(
                    text = "Signed in",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = current.accountName,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center
                )
                OutlinedButton(onClick = viewModel::signOut) {
                    Text("Sign out")
                }
            }

            is SignInUiState.Error -> {
                Text(
                    text = "Sign-in failed",
                    style = MaterialTheme.typography.titleMedium
                )
                // The MSAL error code is shown deliberately: this screen is currently a
                // development tool, and the code is what makes a failure diagnosable.
                Text(
                    text = current.errorCode,
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center
                )
                Button(
                    onClick = { activity?.let(viewModel::signIn) },
                    enabled = activity != null
                ) {
                    Text("Try again")
                }
            }
        }
    }
}
