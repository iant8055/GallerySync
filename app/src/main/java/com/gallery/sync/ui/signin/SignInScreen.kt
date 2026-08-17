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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gallery.sync.R

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
                    text = stringResource(R.string.sign_in_prompt),
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center
                )
                Button(
                    onClick = { activity?.let(viewModel::signIn) },
                    enabled = activity != null
                ) {
                    Text(stringResource(R.string.sign_in_action))
                }
            }

            is SignInUiState.SignedIn -> {
                Text(
                    text = stringResource(R.string.sign_in_signed_in),
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = current.accountName,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center
                )
                OutlinedButton(onClick = viewModel::signOut) {
                    Text(stringResource(R.string.sign_out_action))
                }
            }

            is SignInUiState.Error -> {
                Text(
                    text = stringResource(R.string.sign_in_failed),
                    style = MaterialTheme.typography.titleMedium
                )
                // The MSAL error code is shown deliberately and left untranslated: it is a
                // diagnostic identifier, and translating it would make it unsearchable.
                Text(
                    text = current.errorCode,
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center
                )
                Button(
                    onClick = { activity?.let(viewModel::signIn) },
                    enabled = activity != null
                ) {
                    Text(stringResource(R.string.try_again_action))
                }
            }
        }
    }
}
