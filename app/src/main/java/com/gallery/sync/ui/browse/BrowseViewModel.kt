package com.gallery.sync.ui.browse

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gallery.sync.domain.model.DataResult
import com.gallery.sync.domain.model.RemoteError
import com.gallery.sync.domain.model.RemoteMediaNode
import com.gallery.sync.domain.repository.OneDriveRepository
import com.gallery.sync.domain.repository.OneDriveUploadRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** What the browse screen is showing. */
sealed interface BrowseUiState {

    data object Loading : BrowseUiState

    data class Content(
        /** Human-readable location, e.g. `OneDrive / Pictures`. */
        val location: String,
        val nodes: List<RemoteMediaNode>,
        val canGoBack: Boolean
    ) : BrowseUiState

    data class Error(
        val error: RemoteError,
        val canGoBack: Boolean
    ) : BrowseUiState
}

/**
 * Browses the OneDrive folder tree.
 *
 * This exists to prove the data layer end to end against a real account: a real token reaching
 * Graph through the interceptor, a real response, and real [RemoteMediaNode]s out of the mapper.
 * The polished media browser is a later milestone.
 */
@HiltViewModel
class BrowseViewModel @Inject constructor(
    private val repository: OneDriveRepository,
    private val uploadRepository: OneDriveUploadRepository
) : ViewModel() {

    private val _state = MutableStateFlow<BrowseUiState>(BrowseUiState.Loading)
    val state: StateFlow<BrowseUiState> = _state.asStateFlow()

    /**
     * TEMPORARY — verification scaffolding for TASK-006, remove once the backup worker exists.
     * Null when no test upload has run in this session.
     */
    private val _uploadStatus = MutableStateFlow<String?>(null)
    val uploadStatus: StateFlow<String?> = _uploadStatus.asStateFlow()

    /** Folders entered so far. Empty means the drive root. */
    private val trail = mutableListOf<Crumb>()

    init {
        load()
    }

    fun open(folder: RemoteMediaNode.Folder) {
        trail.add(Crumb(folder.id, folder.name))
        load()
    }

    /** Returns false when already at the root, so the caller can let the system handle back. */
    fun back(): Boolean {
        if (trail.isEmpty()) return false
        trail.removeAt(trail.lastIndex)
        load()
        return true
    }

    fun retry() = load()

    /**
     * TEMPORARY — verification scaffolding for TASK-006, remove once the backup worker exists.
     *
     * Writes a file of [sizeBytes] into the app's own cache and uploads it to the folder currently
     * being browsed, then reports whether OneDrive's returned size matches byte for byte. Using a
     * generated file rather than a real photo keeps this free of any media-read permission, which
     * would need its own decision before being added to the manifest.
     */
    fun uploadTestFile(cacheDir: File, sizeBytes: Long) {
        viewModelScope.launch {
            _uploadStatus.value = "Uploading ${sizeBytes / 1024} KB…"

            val file = File(cacheDir, "gs_upload_test_${sizeBytes}.bin")
            runCatching {
                file.outputStream().use { out ->
                    val block = ByteArray(64 * 1024) { (it % 251).toByte() }
                    var remaining = sizeBytes
                    while (remaining > 0) {
                        val n = minOf(block.size.toLong(), remaining).toInt()
                        out.write(block, 0, n)
                        remaining -= n
                    }
                }
            }.onFailure {
                _uploadStatus.value = "Could not create test file: ${it.javaClass.simpleName}"
                return@launch
            }

            val result = uploadRepository.upload(
                localFile = file,
                remoteFolderPath = trail.joinToString("/") { it.name },
                onProgress = { sent, total ->
                    _uploadStatus.value = "Uploading… ${sent * 100 / total.coerceAtLeast(1)}%"
                }
            )

            _uploadStatus.value = when (result) {
                is DataResult.Success -> {
                    val item = result.value
                    // Size equality is the actual proof. "A file appeared" would also be true of
                    // a truncated or corrupt upload.
                    if (item.sizeBytes == sizeBytes) {
                        "OK — ${item.name} stored, ${item.sizeBytes} bytes, size matches"
                    } else {
                        "MISMATCH — sent $sizeBytes, OneDrive reports ${item.sizeBytes}"
                    }
                }

                is DataResult.Failure -> "FAILED — ${result.error}"
            }

            file.delete()
            load()
        }
    }

    fun clearUploadStatus() {
        _uploadStatus.value = null
    }

    private fun load() {
        val current = trail.lastOrNull()
        viewModelScope.launch {
            _state.value = BrowseUiState.Loading

            val result = if (current == null) {
                repository.listRoot()
            } else {
                repository.listFolder(current.id)
            }

            _state.value = when (result) {
                is DataResult.Success -> BrowseUiState.Content(
                    location = locationLabel(),
                    // Folders first, then files, each alphabetical — a raw provider ordering is
                    // close to useless for confirming a listing looks right.
                    nodes = result.value.nodes.sortedWith(
                        compareBy(
                            { it !is RemoteMediaNode.Folder },
                            { it.name.lowercase() }
                        )
                    ),
                    canGoBack = trail.isNotEmpty()
                )

                is DataResult.Failure -> BrowseUiState.Error(
                    error = result.error,
                    canGoBack = trail.isNotEmpty()
                )
            }
        }
    }

    private fun locationLabel(): String =
        (listOf(ROOT_LABEL) + trail.map { it.name }).joinToString(" / ")

    private data class Crumb(val id: String, val name: String)

    private companion object {
        const val ROOT_LABEL = "OneDrive"
    }
}
