package com.gallery.sync.ui.settings

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gallery.sync.R
import com.gallery.sync.data.remote.cloud.ConnectionKind
import com.gallery.sync.domain.backup.BackupLocation
import com.gallery.sync.domain.billing.MultiCloudTrial
import com.gallery.sync.ui.common.LabelWithAction
import com.gallery.sync.ui.common.labelRes
import com.gallery.sync.ui.help.HelpTopic
import com.gallery.sync.ui.help.WithHelp

/**
 * Connect, disconnect and unlock every optional cloud — Settings and the setup wizard both use this.
 *
 * Each provider is its own row with its own button, and the trial / Pro block sits below once anything
 * is connected: connecting and paying are different systems with different failure modes, and merging
 * them into one control would hide which one needs retrying.
 *
 * [only] limits the rows to the providers the user ticked in the wizard; null lists every provider
 * this build offers.
 */
@Composable
fun CloudProvidersSection(
    modifier: Modifier = Modifier,
    only: Set<BackupLocation>? = null,
    showTitle: Boolean = true,
    viewModel: CloudProvidersViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // AppAuth and Play Billing both need a foreground Activity to host their own UI — same
    // requirement, same LocalActivity.current seam, as SignInScreen's OneDrive equivalent.
    val activity = LocalActivity.current
    var keysFor by remember { mutableStateOf<ProviderState?>(null) }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (showTitle) {
            // The (?) is the old Account topic: what signing in and out does. The Account row it belonged to
            // is gone, since every cloud's account is named on its own row here.
            WithHelp(HelpTopic.SETTINGS_ACCOUNT) {
                Text(
                    text = stringResource(R.string.cloud_providers_section_title),
                    style = MaterialTheme.typography.titleMedium
                )
            }
        }

        state.providers.filter { only == null || it.location in only }.forEach { provider ->
            LabelWithAction(
                action = {
                    when {
                        state.isBusy -> BusyIndicator()
                        provider.isConnected -> OutlinedButton(onClick = { viewModel.disconnect(provider.location) }) {
                            Text(stringResource(R.string.sign_out_action))
                        }
                        provider.kind == ConnectionKind.ACCESS_KEYS -> Button(onClick = { keysFor = provider }) {
                            Text(stringResource(R.string.cloud_enter_keys_action))
                        }
                        else -> Button(
                            onClick = { activity?.let { viewModel.connect(provider.location, it) } },
                            enabled = activity != null
                        ) {
                            Text(stringResource(R.string.google_photos_connect_action))
                        }
                    }
                }
            ) {
                Column {
                    Text(
                        text = stringResource(provider.location.labelRes()) +
                            if (provider.location == state.main) " · " + stringResource(R.string.cloud_main_tag) else "",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        // The OAuth clouds have no account name to show, and their label is just the
                        // provider's own name — repeating it under itself reads as a bug.
                        text = when (provider.accountLabel) {
                            null -> stringResource(R.string.google_photos_not_connected)
                            stringResource(provider.location.labelRes()) -> stringResource(R.string.google_photos_connected)
                            else -> provider.accountLabel
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Only reachable once something is connected — paying or trialling with nothing to unlock yet
        // would be a control that cannot do anything useful, the same "never offer an action that
        // cannot succeed" rule GooglePhotosDestination follows for Sync and Archive.
        //
        // The terms are on screen *before* the trial button, in plain words: 30 days, then a one-time
        // unlock, and nothing charged automatically (Ian, 24 Sept 2026: plain and upfront, a hard
        // gate). Bought is the only state that shows nothing here at all.
        if (state.extraConnected.isNotEmpty() && !state.isProUnlocked) {
            val trial = state.trial
            Text(
                text = when (trial) {
                    MultiCloudTrial.State.NotStarted -> stringResource(R.string.google_photos_trial_offer)
                    is MultiCloudTrial.State.Active ->
                        stringResource(R.string.google_photos_trial_active_terms, trial.daysLeft)
                    MultiCloudTrial.State.Ended -> stringResource(R.string.google_photos_trial_ended)
                },
                style = MaterialTheme.typography.bodyMedium
            )
            if (state.isBusy) {
                BusyIndicator()
            } else {
                // Stacked, full width: side by side they squeezed "Unlock Pro" down to "Un" on a 360dp card.
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (trial == MultiCloudTrial.State.NotStarted) {
                        Button(onClick = viewModel::startTrial, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.google_photos_trial_start_action))
                        }
                    }
                    val unlock = { activity?.let(viewModel::unlockPro); Unit }
                    if (trial == MultiCloudTrial.State.NotStarted) {
                        OutlinedButton(onClick = unlock, enabled = activity != null, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.google_photos_unlock_pro_action), maxLines = 1)
                        }
                    } else {
                        Button(onClick = unlock, enabled = activity != null, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.google_photos_unlock_pro_action), maxLines = 1)
                        }
                    }
                }
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

    keysFor?.let { provider ->
        KeysDialog(
            provider = provider,
            onConnect = { values ->
                viewModel.connectWithKeys(provider.location, values)
                keysFor = null
            },
            onDismiss = { keysFor = null }
        )
    }
}

/** The "paste your keys" form. Secret fields are hidden as typed and never leave this dialog but to be saved. */
@Composable
private fun KeysDialog(
    provider: ProviderState,
    onConnect: (Map<String, String>) -> Unit,
    onDismiss: () -> Unit
) {
    val values = remember { mutableStateOf(provider.keyFields.associate { it.id to it.defaultValue }) }
    // Secret fields start hidden, and can be shown to check what was typed: a single wrong character in
    // a long key is the usual reason a store rejects it, and dots hide exactly that.
    var showSecret by remember { mutableStateOf(false) }
    var showGuide by remember { mutableStateOf(false) }
    val guideAnchor = keysGuideAnchor(provider.location)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.cloud_keys_dialog_title, stringResource(provider.location.labelRes()))) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                provider.keyFields.forEach { field ->
                    OutlinedTextField(
                        value = values.value[field.id].orEmpty(),
                        onValueChange = { values.value = values.value + (field.id to it) },
                        label = { Text(stringResource(field.labelRes)) },
                        placeholder = field.hintRes?.let { hint -> { Text(stringResource(hint)) } },
                        singleLine = true,
                        visualTransformation = if (field.isSecret && !showSecret) {
                            PasswordVisualTransformation()
                        } else {
                            androidx.compose.ui.text.input.VisualTransformation.None
                        },
                        trailingIcon = if (field.isSecret) {
                            {
                                TextButton(onClick = { showSecret = !showSecret }) {
                                    Text(stringResource(if (showSecret) R.string.cloud_keys_hide else R.string.cloud_keys_show))
                                }
                            }
                        } else {
                            null
                        },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = if (field.isSecret) KeyboardType.Password else KeyboardType.Uri
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                if (guideAnchor != null) {
                    TextButton(onClick = { showGuide = true }) {
                        Text(stringResource(R.string.cloud_keys_guide))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConnect(values.value) }) {
                Text(stringResource(R.string.google_photos_connect_action))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cloud_cancel)) }
        }
    )
    if (showGuide && guideAnchor != null) {
        InAppPageDialog(page = SupportPage.HOW_TO_GUIDE, onDismiss = { showGuide = false }, anchor = guideAnchor)
    }
}

/** The How To Guide topic that walks through getting these keys, for the clouds that need them. */
private fun keysGuideAnchor(location: BackupLocation): String? = when (location) {
    BackupLocation.BACKBLAZE_B2 -> "cloud-key-backblaze"
    BackupLocation.IDRIVE_E2 -> "cloud-key-idrive"
    else -> null
}

@Composable
private fun BusyIndicator() {
    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
}
