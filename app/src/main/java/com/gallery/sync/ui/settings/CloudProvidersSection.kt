package com.gallery.sync.ui.settings

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gallery.sync.R
import com.gallery.sync.data.remote.cloud.ConnectionKind
import com.gallery.sync.domain.backup.BackupLocation
import com.gallery.sync.domain.backup.RemoteRoots
import com.gallery.sync.domain.billing.MultiCloudTrial
import com.gallery.sync.ui.common.FitText
import com.gallery.sync.ui.common.LabelWithAction
import com.gallery.sync.ui.common.formatBytes
import com.gallery.sync.ui.common.labelRes
import com.gallery.sync.ui.help.HelpButton
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

    state.signOut?.let { question ->
        SignOutDialog(
            question = question,
            onContinue = viewModel::continueSignOut,
            onLeave = viewModel::signOutLeavingFiles,
            onRestore = viewModel::restoreThenSignOut,
            onOverwriteEdited = viewModel::overwriteEditedThenSignOut,
            onCancel = viewModel::cancelSignOut
        )
    }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        val shown = state.providers.filter { only == null || it.location in only }

        if (showTitle) {
            // The (?) is the old Account topic: what signing in and out does. The Account row it belonged to
            // is gone, since every cloud's account is named on its own row here.
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(CloudColumnGap)
            ) {
                Row(modifier = Modifier.weight(CloudColumnWeight), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.cloud_providers_section_title),
                        style = MaterialTheme.typography.titleMedium
                    )
                    HelpButton(HelpTopic.SETTINGS_ACCOUNT)
                }
                if (shown.any { it.isConnected && state.folders[it.location] != null }) {
                    // One word (German's Backup-Ordner) shrinks to fit instead of breaking mid-word at large text;
                    // two words (French's Dossier de sauvegarde) wrap between them as usual.
                    val heading = stringResource(R.string.cloud_backup_folder)
                    if (heading.contains(' ')) {
                        Text(
                            text = heading,
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.weight(FolderColumnWeight)
                        )
                    } else {
                        FitText(
                            text = heading,
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.weight(FolderColumnWeight)
                        )
                    }
                }
            }
            HorizontalDivider()
        }

        shown.forEachIndexed { index, provider ->
            // A light line between one Cloud and the next (Ian, 31 Oct 2026).
            if (index > 0) HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            val connectionControl: @Composable () -> Unit = {
                // The spinner goes on the one cloud being connected; every other button stays in place,
                // greyed, so one sign-in at a time is still enforced without six rows looking busy.
                when {
                    state.busyWith == provider.location -> BusyIndicator()
                    provider.isConnected -> OutlinedButton(
                        onClick = { viewModel.requestSignOut(provider.location) },
                        enabled = !state.isBusy
                    ) {
                        // In the Settings column it shrinks rather than breaking mid-word at large text
                        // ("Se déconnecte / r"); the wizard's card has the full width.
                        if (showTitle) FitText(stringResource(R.string.sign_out_action))
                        else Text(stringResource(R.string.sign_out_action))
                    }
                    provider.kind == ConnectionKind.ACCESS_KEYS -> Button(
                        onClick = { keysFor = provider },
                        enabled = !state.isBusy
                    ) {
                        Text(stringResource(R.string.cloud_enter_keys_action))
                    }
                    else -> Button(
                        onClick = { activity?.let { viewModel.connect(provider.location, it) } },
                        enabled = activity != null && !state.isBusy
                    ) {
                        Text(stringResource(R.string.google_photos_connect_action))
                    }
                }
            }
            val description: @Composable () -> Unit = {
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
            val changeFolder: @Composable () -> Unit = {
                OutlinedButton(onClick = { viewModel.chooseFolder(provider.location) }, enabled = !state.isBusy) {
                    Text(stringResource(R.string.destination_change), maxLines = 1)
                }
            }
            // Where in this Cloud the backups go, once it is connected. Every Cloud with folders (Ian, 12 Oct 2026).
            val folder = state.folders[provider.location]

            when {
                // The wizard's Connect card, one Cloud at a time, keeps its own stacked rows.
                !showTitle -> {
                    LabelWithAction(action = { connectionControl() }) { description() }
                    if (provider.isConnected && folder != null) {
                        LabelWithAction(action = { changeFolder() }) {
                            Column {
                                Text(
                                    text = stringResource(R.string.cloud_backup_folder),
                                    style = MaterialTheme.typography.labelMedium
                                )
                                Text(text = folder, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }

                // Settings, connected: the Cloud and Sign out on the left, its folder and Change on the right,
                // under the Backup folder heading, with the two buttons level (Ian, 31 Oct 2026).
                provider.isConnected -> Row(
                    modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                    horizontalArrangement = Arrangement.spacedBy(CloudColumnGap)
                ) {
                    Column(
                        modifier = Modifier.weight(CloudColumnWeight).fillMaxHeight(),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        description()
                        connectionControl()
                    }
                    if (folder != null) {
                        Column(
                            modifier = Modifier.weight(FolderColumnWeight).fillMaxHeight(),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(text = folder, style = MaterialTheme.typography.bodyLarge)
                            changeFolder()
                        }
                    }
                }

                // Settings, not connected: the Cloud and its Connect button on one line, at any text size
                // (Ian, 31 Oct 2026).
                else -> Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(CloudColumnGap)
                ) {
                    Column(modifier = Modifier.weight(1f)) { description() }
                    connectionControl()
                }
            }
        }

        state.choosingFolderFor?.let { location ->
            DestinationDialog(
                current = state.folders[location] ?: RemoteRoots.defaultFor(location),
                alreadyBackedUp = 0,
                rejected = state.folderRejected,
                onConfirm = { viewModel.setFolder(location, it) },
                onDismiss = viewModel::dismissFolderChooser,
                cloudName = stringResource(location.labelRes()),
                defaultFolder = RemoteRoots.defaultFor(location),
                canBrowse = location == BackupLocation.ONEDRIVE,
                searchesOldFolders = location == BackupLocation.ONEDRIVE
            )
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
            // A purchase belongs to no cloud row, so its spinner is here; a cloud's sign-in keeps its spinner
            // on that row and only greys these buttons.
            if (state.isBusy && state.busyWith == null) {
                BusyIndicator()
            } else {
                // Stacked, full width: side by side they squeezed "Unlock Pro" down to "Un" on a 360dp card.
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (trial == MultiCloudTrial.State.NotStarted) {
                        Button(onClick = viewModel::startTrial, enabled = !state.isBusy, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.google_photos_trial_start_action))
                        }
                    }
                    val unlock = { activity?.let(viewModel::unlockPro); Unit }
                    if (trial == MultiCloudTrial.State.NotStarted) {
                        OutlinedButton(onClick = unlock, enabled = activity != null && !state.isBusy, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.google_photos_unlock_pro_action), maxLines = 1)
                        }
                    } else {
                        Button(onClick = unlock, enabled = activity != null && !state.isBusy, modifier = Modifier.fillMaxWidth()) {
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

/**
 * What to do with a cloud's files when signing out of it (Ian, 25 Sept 2026): leave them, or bring them back to the
 * phone first. Nothing else is offered, and nothing here moves a folder's pairing or removes a file anywhere.
 */
@Composable
private fun SignOutDialog(
    question: SignOutState,
    onContinue: () -> Unit,
    onLeave: () -> Unit,
    onRestore: () -> Unit,
    onOverwriteEdited: () -> Unit,
    onCancel: () -> Unit
) {
    val cloud = stringResource(question.location.labelRes())
    when (question) {
        is SignOutState.Warning -> AlertDialog(
            onDismissRequest = onCancel,
            title = { Text(stringResource(R.string.cloud_signout_title, cloud)) },
            text = { Text(stringResource(R.string.cloud_signout_warning, cloud)) },
            confirmButton = {
                TextButton(onClick = onContinue) { Text(stringResource(R.string.sign_out_action)) }
            },
            dismissButton = {
                TextButton(onClick = onCancel) { Text(stringResource(R.string.cloud_cancel)) }
            }
        )

        is SignOutState.Asking -> AlertDialog(
            onDismissRequest = onCancel,
            title = { Text(stringResource(R.string.cloud_signout_files_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.cloud_signout_body,
                        question.files,
                        formatBytes(LocalContext.current, question.bytes)
                    )
                )
            },
            confirmButton = {
                Column(horizontalAlignment = androidx.compose.ui.Alignment.End) {
                    TextButton(onClick = onRestore) { Text(stringResource(R.string.cloud_signout_restore)) }
                    TextButton(onClick = onLeave) { Text(stringResource(R.string.cloud_signout_leave)) }
                    TextButton(onClick = onCancel) { Text(stringResource(R.string.cloud_cancel)) }
                }
            }
        )

        // No dismiss: a tap outside must not look like a stop. The button says what it does.
        is SignOutState.Restoring -> AlertDialog(
            onDismissRequest = {},
            title = { Text(stringResource(R.string.cloud_signout_restoring_title, cloud)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    LinearProgressIndicator(
                        progress = { if (question.total == 0) 0f else question.finished.toFloat() / question.total },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(stringResource(R.string.cloud_signout_restoring_progress, question.finished, question.total))
                    if (question.current.isNotEmpty()) {
                        Text(question.current, style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = onCancel) { Text(stringResource(R.string.cloud_signout_stop)) }
            }
        )

        // The person edited these after the app shrank them. Keeping the edits is the safe answer and is first;
        // overwriting throws the edits away, so it is a separate, plainly named button.
        is SignOutState.Edited -> AlertDialog(
            onDismissRequest = onCancel,
            title = { Text(stringResource(R.string.cloud_signout_edited_title)) },
            text = {
                Text(
                    androidx.compose.ui.res.pluralStringResource(
                        R.plurals.cloud_signout_edited_body,
                        question.files.size,
                        question.files.size
                    )
                )
            },
            confirmButton = {
                Column(horizontalAlignment = androidx.compose.ui.Alignment.End) {
                    TextButton(onClick = onLeave) { Text(stringResource(R.string.restore_edited_keep)) }
                    TextButton(onClick = onOverwriteEdited) { Text(stringResource(R.string.cloud_signout_edited_overwrite)) }
                    TextButton(onClick = onCancel) { Text(stringResource(R.string.cloud_signout_stay)) }
                }
            }
        )

        is SignOutState.Incomplete -> AlertDialog(
            onDismissRequest = onCancel,
            title = { Text(stringResource(R.string.cloud_signout_incomplete_title)) },
            text = {
                Text(stringResource(R.string.cloud_signout_incomplete_body, question.restored, question.failed, cloud))
            },
            confirmButton = {
                Column(horizontalAlignment = androidx.compose.ui.Alignment.End) {
                    TextButton(onClick = onCancel) { Text(stringResource(R.string.cloud_signout_stay)) }
                    TextButton(onClick = onLeave) { Text(stringResource(R.string.cloud_signout_anyway)) }
                }
            }
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

private val CloudColumnGap = 12.dp

/** A little more for the Cloud's side than the folder's: it holds the account's email address. */
private const val CloudColumnWeight = 1.15f
private const val FolderColumnWeight = 1f
