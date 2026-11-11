package com.gallery.sync.ui.common

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.gallery.sync.R
import com.gallery.sync.data.local.entity.BackupEntryEntity
import com.gallery.sync.data.local.settings.BackupSettings
import com.gallery.sync.data.repository.OpenFileRepository
import com.gallery.sync.data.repository.TemporaryOpen
import com.gallery.sync.domain.backup.BackupLocation
import com.gallery.sync.domain.model.DataResult
import com.gallery.sync.domain.open.OpenFilePlan
import com.gallery.sync.domain.open.OpenStep
import com.gallery.sync.domain.open.RefuseReason
import com.gallery.sync.util.Logger
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * A long-press on a file, on the Albums, Restore and Archive tabs (TASK-028, Ian, 11 Nov 2026).
 *
 * Hands the file to another app and steps out of the way: the phone's default app for a file on the phone, the
 * Cloud's app or page for one that is not, or a temporary copy when the user chose that. Every decision is
 * [OpenFilePlan]; this only carries it out and asks the questions it calls for. **No in-app viewer.**
 */
@HiltViewModel
class OpenFileViewModel @Inject constructor(
    private val files: OpenFileRepository,
    private val settings: BackupSettings
) : ViewModel() {

    private val _state = MutableStateFlow(OpenFileUiState())
    val state: StateFlow<OpenFileUiState> = _state.asStateFlow()

    private val _launches = Channel<OpenTarget>(Channel.BUFFERED)

    /** What to hand to another app. Collected by [FileOpenerHost], which owns the Activity. */
    val launches = _launches.receiveAsFlow()

    private var work: Job? = null

    /** A file known to be on the phone: the Archive tab's rows, which are read from MediaStore. */
    fun openOnPhone(uri: Uri, mimeType: String) {
        _launches.trySend(OpenTarget.File(uri, mimeType))
    }

    /** A ledger row, which may or may not still be on the phone. */
    fun open(entry: BackupEntryEntity) {
        if (work?.isActive == true) return
        work = viewModelScope.launch {
            val onPhone = files.uriIfOnPhone(entry)
            if (onPhone != null) {
                _launches.send(OpenTarget.File(onPhone, entry.mimeType))
                return@launch
            }
            val prefs = settings.current()
            val step = OpenFilePlan.firstStep(
                onPhone = false,
                setting = prefs.openNotOnPhone,
                cloudHasLinks = files.cloudHasLinks(entry.location),
                canDownload = files.canDownload(entry.location),
                network = files.network(),
                mobileDataAllowed = prefs.allowMeteredNetwork,
                sizeBytes = files.expectedBytes(entry)
            )
            carryOut(step, entry)
        }
    }

    /** *Download*, after a Cloud link could not be fetched (decision 8). Under the same rules as any download. */
    fun downloadInstead(entry: BackupEntryEntity) {
        dismiss()
        work = viewModelScope.launch {
            val prefs = settings.current()
            carryOut(
                OpenFilePlan.downloadStep(
                    canDownload = files.canDownload(entry.location),
                    network = files.network(),
                    mobileDataAllowed = prefs.allowMeteredNetwork,
                    sizeBytes = files.expectedBytes(entry)
                ),
                entry
            )
        }
    }

    /** *Download* on the 750 MB question. */
    fun confirmLargeDownload(entry: BackupEntryEntity) {
        dismiss()
        work = viewModelScope.launch { download(entry) }
    }

    fun cancelDownload() {
        work?.cancel()
        _state.update { it.copy(downloading = null) }
    }

    fun dismiss() {
        _state.update { it.copy(prompt = null) }
    }

    /** Android found no app for the file. Said, never a crash. */
    fun noAppFound() {
        _state.update { it.copy(prompt = OpenPrompt.NoApp) }
    }

    private suspend fun carryOut(step: OpenStep, entry: BackupEntryEntity) {
        when (step) {
            OpenStep.OpenOnPhone -> Unit // Handled before the plan is asked.

            OpenStep.FetchLink -> when (val link = files.linkFor(entry)) {
                is DataResult.Success -> _launches.send(OpenTarget.Link(link.value))
                is DataResult.Failure -> {
                    Logger.w(TAG, "no link for ${entry.displayName}: ${link.error}")
                    _state.update {
                        it.copy(prompt = OpenPrompt.LinkFailed(entry, canDownload = files.canDownload(entry.location)))
                    }
                }
            }

            is OpenStep.Download ->
                if (step.askFirst) {
                    _state.update {
                        it.copy(prompt = OpenPrompt.LargeOnMobile(entry, files.expectedBytes(entry)))
                    }
                } else {
                    download(entry)
                }

            is OpenStep.Refuse -> _state.update { it.copy(prompt = OpenPrompt.Refused(step.reason, entry.location)) }
        }
    }

    private suspend fun download(entry: BackupEntryEntity) {
        _state.update { it.copy(downloading = Downloading(entry.displayName, 0f)) }
        try {
            val outcome = files.downloadTemporary(entry) { written, total ->
                val fraction = if (total > 0) (written.toFloat() / total).coerceIn(0f, 1f) else 0f
                _state.update { it.copy(downloading = it.downloading?.copy(fraction = fraction)) }
            }
            when (outcome) {
                is TemporaryOpen.Ready -> _launches.send(OpenTarget.File(outcome.uri, outcome.mimeType))
                TemporaryOpen.NoSpace -> _state.update { it.copy(prompt = OpenPrompt.NoSpace) }
                TemporaryOpen.GoneFromCloud -> _state.update { it.copy(prompt = OpenPrompt.Gone(entry.location)) }
                TemporaryOpen.Failed -> _state.update { it.copy(prompt = OpenPrompt.DownloadFailed) }
            }
        } finally {
            _state.update { it.copy(downloading = null) }
        }
    }

    private companion object {
        const val TAG = "OpenFile"
    }
}

data class OpenFileUiState(
    val prompt: OpenPrompt? = null,
    val downloading: Downloading? = null
)

data class Downloading(val name: String, val fraction: Float)

sealed interface OpenPrompt {
    /** The Cloud could not give a link this time. Cancel, or Download when the Cloud can send the file. */
    data class LinkFailed(val entry: BackupEntryEntity, val canDownload: Boolean) : OpenPrompt
    data class LargeOnMobile(val entry: BackupEntryEntity, val bytes: Long) : OpenPrompt
    data class Refused(val reason: RefuseReason, val location: BackupLocation) : OpenPrompt
    data class Gone(val location: BackupLocation) : OpenPrompt
    data object NoSpace : OpenPrompt
    data object DownloadFailed : OpenPrompt
    data object NoApp : OpenPrompt
}

sealed interface OpenTarget {
    data class File(val uri: Uri, val mimeType: String) : OpenTarget
    data class Link(val url: String) : OpenTarget
}

/**
 * Long-press to open, for a file card. A tap and a swipe keep what they do: the long-press fires only on a finger
 * held still, and a swipe that moves past the touch slop cancels it. TalkBack gets the same as a named action.
 */
@Composable
fun Modifier.longPressToOpen(onOpen: () -> Unit): Modifier {
    val haptics = LocalHapticFeedback.current
    val current by rememberUpdatedState(onOpen)
    val label = stringResource(R.string.open_file_action)
    return this
        .semantics {
            onLongClick(label) {
                current()
                true
            }
        }
        .pointerInput(Unit) {
            detectTapGestures(
                onLongPress = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    current()
                }
            )
        }
}

/**
 * Starts the other app and draws the questions. One per tab, beside the list. The phone's default app opens the
 * file; Android shows its chooser only when no default is set (decision 2), so no chooser is forced here.
 */
@Composable
fun FileOpenerHost(viewModel: OpenFileViewModel) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel) {
        viewModel.launches.collect { target ->
            val intent = when (target) {
                is OpenTarget.File -> Intent(Intent.ACTION_VIEW)
                    .setDataAndType(target.uri, target.mimeType)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                is OpenTarget.Link -> Intent(Intent.ACTION_VIEW, Uri.parse(target.url))
            }
            try {
                context.startActivity(intent)
            } catch (e: ActivityNotFoundException) {
                viewModel.noAppFound()
            } catch (e: SecurityException) {
                Logger.w("OpenFile", "could not hand the file over: ${e.message}")
                viewModel.noAppFound()
            }
        }
    }

    state.downloading?.let { downloading ->
        AlertDialog(
            onDismissRequest = {},
            title = { Text(stringResource(R.string.open_downloading_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(downloading.name)
                    LinearProgressIndicator(progress = { downloading.fraction }, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = viewModel::cancelDownload) { Text(stringResource(R.string.open_cancel)) }
            }
        )
    }

    val prompt = state.prompt ?: return
    val okOnly: @Composable (String) -> Unit = { message ->
        AlertDialog(
            onDismissRequest = viewModel::dismiss,
            text = { Text(message) },
            confirmButton = { TextButton(onClick = viewModel::dismiss) { Text(stringResource(R.string.open_ok)) } }
        )
    }
    when (prompt) {
        is OpenPrompt.LinkFailed -> {
            val cloud = stringResource(prompt.entry.location.labelRes())
            AlertDialog(
                onDismissRequest = viewModel::dismiss,
                title = { Text(stringResource(R.string.open_link_failed_title, cloud)) },
                text = {
                    Text(
                        stringResource(
                            if (prompt.canDownload) R.string.open_link_failed_body else R.string.open_link_failed_body_no_download
                        )
                    )
                },
                confirmButton = {
                    if (prompt.canDownload) {
                        TextButton(onClick = { viewModel.downloadInstead(prompt.entry) }) {
                            Text(stringResource(R.string.open_download))
                        }
                    }
                },
                dismissButton = {
                    TextButton(onClick = viewModel::dismiss) { Text(stringResource(R.string.open_cancel)) }
                }
            )
        }

        is OpenPrompt.LargeOnMobile -> AlertDialog(
            onDismissRequest = viewModel::dismiss,
            title = { Text(stringResource(R.string.open_large_title)) },
            text = { Text(stringResource(R.string.open_large_body, formatBytes(context, prompt.bytes))) },
            confirmButton = {
                TextButton(onClick = { viewModel.confirmLargeDownload(prompt.entry) }) {
                    Text(stringResource(R.string.open_download))
                }
            },
            dismissButton = { TextButton(onClick = viewModel::dismiss) { Text(stringResource(R.string.open_cancel)) } }
        )

        is OpenPrompt.Refused -> okOnly(
            when (prompt.reason) {
                RefuseReason.NO_NETWORK -> stringResource(R.string.open_refused_no_network)
                RefuseReason.MOBILE_DATA_OFF -> stringResource(R.string.open_refused_mobile_data)
                RefuseReason.CANNOT_DOWNLOAD ->
                    stringResource(R.string.open_refused_cannot_download, stringResource(prompt.location.labelRes()))
            }
        )

        is OpenPrompt.Gone -> okOnly(stringResource(R.string.open_gone, stringResource(prompt.location.labelRes())))
        OpenPrompt.NoSpace -> okOnly(stringResource(R.string.open_no_space))
        OpenPrompt.DownloadFailed -> okOnly(stringResource(R.string.open_download_failed))
        OpenPrompt.NoApp -> okOnly(stringResource(R.string.open_no_app))
    }
}
