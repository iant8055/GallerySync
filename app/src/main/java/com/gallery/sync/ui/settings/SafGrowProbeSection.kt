package com.gallery.sync.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gallery.sync.data.local.media.SafGrowProbe
import com.gallery.sync.data.local.media.SafGrowResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SafGrowProbeViewModel @Inject constructor(
    private val probe: SafGrowProbe
) : ViewModel() {

    private val _result = MutableStateFlow<String?>(null)
    val result: StateFlow<String?> = _result.asStateFlow()

    fun run() {
        _result.value = "Running…"
        viewModelScope.launch {
            _result.value = describe(probe.run())
        }
    }

    private fun describe(result: SafGrowResult): String = when (result) {
        is SafGrowResult.Grew -> if (result.indexedBytes == result.grewToBytes) {
            "GREW. ${result.fromBytes} → ${result.grewToBytes} bytes on disk, " +
                "and MediaStore agrees. Restore-in-place is viable."
        } else {
            "Grew on disk (${result.fromBytes} → ${result.grewToBytes}) but MediaStore says " +
                "${result.indexedBytes}. The write works; the rescan does not settle in time."
        }

        is SafGrowResult.WrongSize ->
            "WRONG SIZE. Expected ${result.expectedBytes} bytes, found ${result.actualBytes}."

        SafGrowResult.NoGrant ->
            "No granted tree covers DCIM/Camera. Pick that folder under Folders first."

        SafGrowResult.NotSupported -> "Needs Android 11 or newer."

        is SafGrowResult.Failed -> "FAILED: ${result.reason}"
    }
}

/**
 * A button that answers the one question TASK-018 is blocked on.
 *
 * **Debug builds only** — the caller gates this on `BuildConfig.DEBUG`, so it is absent from
 * anything that reaches Play. That is also why the text here is literal rather than in
 * `strings.xml`: no user ever reads it, and translating a diagnostic would be surface for nothing.
 *
 * It has to live in the app rather than in an instrumented test because
 * `connectedDebugAndroidTest` uninstalls the app to install the test APK, which takes the persisted
 * tree grant the question is about. See [SafGrowProbe].
 */
@Composable
fun SafGrowProbeSection(
    modifier: Modifier = Modifier,
    viewModel: SafGrowProbeViewModel = hiltViewModel()
) {
    val result by viewModel.result.collectAsStateWithLifecycle()

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("Debug — SAF grow probe", style = MaterialTheme.typography.titleSmall)
        Text(
            "Writes 512 KB over a 4 KB file it creates in DCIM/Camera, then removes it. " +
                "Touches nothing of yours.",
            style = MaterialTheme.typography.bodySmall
        )
        OutlinedButton(onClick = viewModel::run) { Text("Run probe") }
        result?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
    }
}
