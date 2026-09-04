package com.gallery.sync.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gallery.sync.domain.model.DataResult
import com.gallery.sync.domain.model.RemoteError
import com.gallery.sync.domain.model.RemoteMediaNode
import com.gallery.sync.domain.repository.OneDriveRepository
import com.gallery.sync.domain.repository.OneDriveUploadRepository
import com.gallery.sync.util.Logger
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** One level of the path walked so far. The root itself is not a crumb — it is the empty list. */
data class FolderCrumb(val id: String, val name: String)

data class FolderPickerUiState(
    val crumbs: List<FolderCrumb> = emptyList(),
    val folders: List<RemoteMediaNode.Folder> = emptyList(),
    val loading: Boolean = false,
    val error: RemoteError? = null,
    /** Set when the listing was cut short; there are more children than are shown. */
    val moreToken: String? = null,
    val creating: Boolean = false,
    /** The typed folder name was already taken, or the create failed. */
    val createFailed: Boolean = false
) {
    /** The drive path these crumbs describe, e.g. `Samsung Gallery/DCIM`. Empty at the root. */
    val path: String get() = crumbs.joinToString("/") { it.name }

    val atRoot: Boolean get() = crumbs.isEmpty()
}

/**
 * Browses the signed-in drive so a destination can be picked rather than typed.
 *
 * Only folders are listed. Graph returns files in the same pages and they are dropped here, which
 * is also why a page can come back empty with more still to fetch — a folder holding a thousand
 * photos and one subfolder pages several times before the subfolder appears. That is what
 * [FolderPickerUiState.moreToken] is for: the screen says there is more rather than pretending the
 * folder is empty, and fetching the rest stays the user's choice on a mobile connection.
 */
@HiltViewModel
class FolderPickerViewModel @Inject constructor(
    private val drive: OneDriveRepository,
    private val uploads: OneDriveUploadRepository
) : ViewModel() {

    private val _state = MutableStateFlow(FolderPickerUiState())
    val state: StateFlow<FolderPickerUiState> = _state.asStateFlow()

    /** Starts a fresh browse at the drive root. */
    fun openAtRoot() {
        _state.value = FolderPickerUiState(loading = true)
        viewModelScope.launch { apply(drive.listRoot(), replace = true) }
    }

    fun enter(folder: RemoteMediaNode.Folder) {
        _state.value = _state.value.copy(
            crumbs = _state.value.crumbs + FolderCrumb(folder.id, folder.name),
            folders = emptyList(),
            moreToken = null,
            loading = true,
            error = null
        )
        viewModelScope.launch { apply(drive.listFolder(folder.id), replace = true) }
    }

    /** Jumps back to a crumb; [depth] 0 is the root. */
    fun upTo(depth: Int) {
        val crumbs = _state.value.crumbs.take(depth)
        _state.value = _state.value.copy(
            crumbs = crumbs,
            folders = emptyList(),
            moreToken = null,
            loading = true,
            error = null
        )
        viewModelScope.launch {
            val result = crumbs.lastOrNull()
                ?.let { drive.listFolder(it.id) }
                ?: drive.listRoot()
            apply(result, replace = true)
        }
    }

    fun loadMore() {
        val token = _state.value.moreToken ?: return
        _state.value = _state.value.copy(loading = true, moreToken = null)
        viewModelScope.launch { apply(drive.listNextPage(token), replace = false) }
    }

    fun createFolder(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        _state.value = _state.value.copy(creating = true, createFailed = false)
        viewModelScope.launch {
            when (val result = uploads.createFolder(_state.value.crumbs.lastOrNull()?.id, trimmed)) {
                is DataResult.Success -> {
                    // Shown immediately rather than re-listing: the folder is known to exist and
                    // is known to be empty, and a re-list costs a round trip to learn that.
                    _state.value = _state.value.copy(
                        folders = (_state.value.folders + result.value).sortedBy { it.name.lowercase() },
                        creating = false,
                        createFailed = false
                    )
                }
                is DataResult.Failure -> {
                    Logger.w(TAG, "createFolder failed")
                    _state.value = _state.value.copy(creating = false, createFailed = true)
                }
            }
        }
    }

    fun dismissCreateError() {
        _state.value = _state.value.copy(createFailed = false)
    }

    private fun apply(result: DataResult<com.gallery.sync.domain.model.FolderPage>, replace: Boolean) {
        when (result) {
            is DataResult.Success -> {
                val found = result.value.nodes.filterIsInstance<RemoteMediaNode.Folder>()
                val merged = if (replace) found else _state.value.folders + found
                _state.value = _state.value.copy(
                    folders = merged.sortedBy { it.name.lowercase() },
                    moreToken = result.value.nextPageToken,
                    loading = false,
                    error = null
                )
            }
            is DataResult.Failure -> {
                _state.value = _state.value.copy(loading = false, error = result.error)
            }
        }
    }

    private companion object {
        const val TAG = "FolderPicker"
    }
}
