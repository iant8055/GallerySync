package com.gallery.sync.ui.browse

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gallery.sync.domain.model.DataResult
import com.gallery.sync.domain.model.RemoteError
import com.gallery.sync.domain.model.RemoteMediaNode
import com.gallery.sync.domain.repository.OneDriveRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * How to order a listing.
 *
 * Exists for verification, not browsing: the question being answered is "did this file arrive, and
 * is it the right size?" — which is why size is a first-class option alongside name and date.
 */
enum class SortField { NAME, DATE, SIZE }

/** What the browse screen is showing. */
sealed interface BrowseUiState {

    data object Loading : BrowseUiState

    data class Content(
        /**
         * Folders entered so far, e.g. `["Pictures", "2024"]`. Empty at the drive root.
         *
         * The root's own name is deliberately absent: it is display text, so the screen supplies
         * it from string resources rather than the ViewModel hardcoding it.
         */
        val trail: List<String>,
        val nodes: List<RemoteMediaNode>,
        val canGoBack: Boolean,
        val sortField: SortField = SortField.NAME,
        val sortAscending: Boolean = true
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
    private val repository: OneDriveRepository
) : ViewModel() {

    private val _state = MutableStateFlow<BrowseUiState>(BrowseUiState.Loading)
    val state: StateFlow<BrowseUiState> = _state.asStateFlow()

    /** Folders entered so far. Empty means the drive root. */
    private val trail = mutableListOf<Crumb>()

    private var sortField = SortField.NAME
    private var sortAscending = true

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

    /**
     * Jumps straight to a level in the breadcrumb.
     *
     * [depth] is how many folders to keep: 0 is the drive root, 1 the first folder entered, and so
     * on. Tapping the level already shown does nothing rather than reloading it — a wasted network
     * round trip and a visible flicker for a tap that asked for no change.
     */
    fun navigateTo(depth: Int) {
        if (depth < 0 || depth >= trail.size) return
        while (trail.size > depth) {
            trail.removeAt(trail.lastIndex)
        }
        load()
    }

    fun retry() = load()

    /**
     * Chooses the sort. Tapping the field already in use flips the direction, which is what a
     * column header does everywhere else.
     */
    fun setSort(field: SortField) {
        if (field == sortField) {
            sortAscending = !sortAscending
        } else {
            sortField = field
            // Names read naturally A-Z; dates and sizes are almost always wanted largest and
            // newest first, which is where an unfamiliar or oversized file will be.
            sortAscending = field == SortField.NAME
        }
        resort()
    }

    /** Re-orders what is already loaded. No network call — the listing has not changed. */
    private fun resort() {
        val current = _state.value as? BrowseUiState.Content ?: return
        _state.value = current.copy(
            nodes = sortNodes(current.nodes),
            sortField = sortField,
            sortAscending = sortAscending
        )
    }

    /**
     * Folders always come first, whatever the sort.
     *
     * Interleaving them by size or date scatters the structure through the list and makes a folder
     * easy to miss, which defeats the point of drilling down to check something.
     */
    private fun sortNodes(nodes: List<RemoteMediaNode>): List<RemoteMediaNode> {
        val comparator: Comparator<RemoteMediaNode> = when (sortField) {
            SortField.NAME -> compareBy { it.name.lowercase() }
            SortField.DATE -> compareBy { it.modifiedAtUtc }
            // Folders report no size; keeping them at 0 leaves them together at one end rather
            // than interleaved arbitrarily.
            SortField.SIZE -> compareBy { (it as? RemoteMediaNode.File)?.sizeBytes ?: 0L }
        }

        return nodes.sortedWith(
            compareBy<RemoteMediaNode> { it !is RemoteMediaNode.Folder }
                .then(if (sortAscending) comparator else comparator.reversed())
        )
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
                    trail = trail.map { it.name },
                    nodes = sortNodes(result.value.nodes),
                    canGoBack = trail.isNotEmpty(),
                    sortField = sortField,
                    sortAscending = sortAscending
                )

                is DataResult.Failure -> BrowseUiState.Error(
                    error = result.error,
                    canGoBack = trail.isNotEmpty()
                )
            }
        }
    }

    private data class Crumb(val id: String, val name: String)
}
