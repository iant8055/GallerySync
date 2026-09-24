package com.gallery.sync.ui.settings

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gallery.sync.R
import com.gallery.sync.ui.common.LabelWithAction

/**
 * Connect Google Photos and unlock Pro — the two things [GooglePhotosUiState.isAvailable] needs
 * before it can be chosen as the app-wide destination at all. See TASK-026.
 *
 * Two separate controls rather than one combined "set up Google Photos" flow, because they are
 * genuinely two different systems with two different failure modes (an expired token vs. a declined
 * card), and collapsing them into one button would hide which one actually needs retrying.
 */
@Composable
fun GooglePhotosSection(
    modifier: Modifier = Modifier,
    viewModel: GooglePhotosViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // AppAuth and Play Billing both need a foreground Activity to host their own UI — same
    // requirement, same LocalActivity.current seam, as SignInScreen's OneDrive equivalent.
    val activity = LocalActivity.current

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.google_photos_section_title),
            style = MaterialTheme.typography.titleMedium
        )

        LabelWithAction(
            action = {
                when {
                    state.isBusy -> BusyIndicator()
                    state.isSignedIn -> OutlinedButton(onClick = viewModel::disconnect) {
                        Text(stringResource(R.string.sign_out_action))
                    }

                    else -> Button(
                        onClick = { activity?.let(viewModel::connect) },
                        enabled = activity != null
                    ) {
                        Text(stringResource(R.string.google_photos_connect_action))
                    }
                }
            }
        ) {
            Text(
                text = stringResource(
                    if (state.isSignedIn) R.string.google_photos_connected
                    else R.string.google_photos_not_connected
                ),
                style = MaterialTheme.typography.bodyMedium
            )
        }

        // Only reachable once signed in — purchasing with nothing to unlock yet would be a control
        // that cannot do anything useful, the same "never offer an action that cannot succeed" rule
        // GooglePhotosDestination follows for Sync and Archive.
        if (state.isSignedIn && !state.isProUnlocked) {
            LabelWithAction(
                action = {
                    if (state.isBusy) {
                        BusyIndicator()
                    } else {
                        Button(
                            onClick = { activity?.let(viewModel::unlockPro) },
                            enabled = activity != null
                        ) {
                            Text(stringResource(R.string.google_photos_unlock_pro_action))
                        }
                    }
                }
            ) {
                Text(
                    text = stringResource(R.string.google_photos_unlock_pro_detail),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        state.lastError?.let { error ->
            Text(
                text = error,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}

@Composable
private fun BusyIndicator() {
    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
}
