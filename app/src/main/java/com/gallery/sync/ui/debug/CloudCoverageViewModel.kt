package com.gallery.sync.ui.debug

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gallery.sync.data.local.media.MediaScanner
import com.gallery.sync.domain.backup.BackupEngine
import com.gallery.sync.domain.model.DataResult
import com.gallery.sync.domain.model.RemoteMediaNode
import com.gallery.sync.domain.repository.OneDriveRepository
import com.gallery.sync.util.Logger
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** One album's answer to "is this already in OneDrive?". */
data class AlbumCoverage(
    val album: String,
    val localFiles: Int,
    val remoteFirstPage: Int,
    val remoteAllPages: Int,
    val matchedFirstPageOnly: Int,
    val matchedAllPages: Int,
    val error: String? = null
)

data class CoverageState(
    val running: Boolean = false,
    val rows: List<AlbumCoverage> = emptyList(),
    val log: List<String> = emptyList()
)

/**
 * Read-only dry run of the skip-existing check, across the whole library.
 *
 * Answers whether Ian's assumption holds — that most users already have their photos in OneDrive,
 * so first run is mostly reconciliation rather than a full upload. `BackupEngine.REMOTE_ROOT` is
 * `Samsung Gallery/DCIM`, deliberately mirroring the layout Samsung's own sync created, so the
 * match *should* hold. Whether it does across 90 albums is an empirical question and this measures
 * it without uploading a byte.
 *
 * **It also verifies the paging fix of 19 Aug 2026.** `remoteIndexFor` used to call `listFolderByPath` and read
 * `result.value.nodes` directly — one page. Graph pages large folders, so on an album with more
 * files than fit in a page the check can only see the first page and every file beyond it looks
 * absent. Each row reports the match both ways — page one, and the engine's real function — so the
 * gap between them is the number of duplicate uploads the fix prevents.
 *
 * Debug builds only. Delete with `StorageAccessProbe` once both questions are answered.
 */
@HiltViewModel
class CloudCoverageViewModel @Inject constructor(
    private val scanner: MediaScanner,
    private val repository: OneDriveRepository,
    private val engine: BackupEngine
) : ViewModel() {

    private val _state = MutableStateFlow(CoverageState())
    val state: StateFlow<CoverageState> = _state.asStateFlow()

    fun run() {
        viewModelScope.launch {
            _state.value = CoverageState(running = true)
            say("Scanning device…")

            val albums = scanner.scanAlbums()
            say("${albums.size} albums on device")

            val rows = mutableListOf<AlbumCoverage>()
            for (album in albums) {
                val local = scanner.scanAlbum(album.name)
                val path = "${BackupEngine.REMOTE_ROOT}/${album.name}"

                val first = firstPage(path)
                if (first == null) {
                    rows += AlbumCoverage(album.name, local.size, 0, 0, 0, 0, "not found remotely")
                    _state.value = _state.value.copy(rows = rows.toList())
                    continue
                }

                // The engine's own function, not a copy: this probe exists to verify that code path.
                // null now means the listing failed. Recording it as an error rather than as zero
                // matches is the whole point: the 19:08 run reported 8,177 files "not in OneDrive"
                // when the truth was that 81 albums never got listed.
                val all = engine.remoteIndexFor(album.name)
                if (all == null) {
                    rows += AlbumCoverage(album.name, local.size, first.size, 0, 0, 0, "LISTING FAILED")
                    _state.value = _state.value.copy(rows = rows.toList())
                    continue
                }
                val firstIndex = first.associate { it.name to it.sizeBytes }
                val allIndex = all

                rows += AlbumCoverage(
                    album = album.name,
                    localFiles = local.size,
                    remoteFirstPage = first.size,
                    remoteAllPages = all.size,
                    matchedFirstPageOnly = local.count { firstIndex[it.displayName] == it.sizeBytes },
                    matchedAllPages = local.count { allIndex[it.displayName] == it.sizeBytes }
                )
                _state.value = _state.value.copy(rows = rows.toList())
            }

            val usable = rows.filter { it.error == null }
            val failedAlbums = rows.count { it.error == "LISTING FAILED" }
            val localTotal = usable.sumOf { it.localFiles }
            val onePage = usable.sumOf { it.matchedFirstPageOnly }
            val everyPage = usable.sumOf { it.matchedAllPages }

            say("── totals ──")
            if (failedAlbums > 0) say("!! $failedAlbums albums failed to list — totals below exclude them")
            say("local files in listable albums: $localTotal")
            say("matched, page 1 only (the old behaviour): $onePage")
            say("matched, engine remoteIndexFor (the fix): $everyPage")
            say("duplicates the fix prevents: ${everyPage - onePage}")
            say("genuinely not in OneDrive: ${localTotal - everyPage}")

            _state.value = _state.value.copy(running = false)
        }
    }

    private suspend fun firstPage(path: String): List<RemoteMediaNode.File>? =
        when (val result = repository.listFolderByPath(path)) {
            is DataResult.Success -> result.value.nodes.filterIsInstance<RemoteMediaNode.File>()
            is DataResult.Failure -> null
        }

    private fun say(line: String) {
        Logger.d(TAG, line)
        _state.value = _state.value.copy(log = _state.value.log + line)
    }

    private companion object {
        const val TAG = "CloudCoverage"
        
    }
}
