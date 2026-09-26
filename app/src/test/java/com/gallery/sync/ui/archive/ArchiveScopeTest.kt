package com.gallery.sync.ui.archive

import android.net.Uri
import com.gallery.sync.data.local.media.LocalMediaItem
import com.gallery.sync.domain.backup.ArchiveEntry
import com.gallery.sync.domain.backup.ArchivePlan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.mock

/**
 * What *Check these files* covers. Ian, 25 Sept 2026: the Archive tab gets a folder list, and opened on a
 * folder the check is about that folder only; from the list it is about every Archive folder, as before.
 */
class ArchiveScopeTest {

    private fun file(id: Long, album: String) = LocalMediaItem(
        mediaStoreId = id,
        contentUri = mock<Uri>(),
        displayName = "f$id.jpg",
        album = album,
        sizeBytes = 1_000L,
        dateModifiedEpochSeconds = 1L,
        mimeType = "image/jpeg",
        isVideo = false,
        relativePath = "DCIM/$album/"
    )

    private val plan = ArchivePlan(
        entries = listOf(
            ArchiveEntry(file(1, "dropbox")),
            ArchiveEntry(file(2, "dropbox")),
            ArchiveEntry(file(3, "iDrive"))
        )
    )

    @Test
    fun `from the folder list the check covers every folder`() {
        val state = ArchiveUiState(plan = plan, openAlbum = null)
        assertEquals(3, state.scopedEntries.size)
    }

    @Test
    fun `opened on a folder the check covers only that folder`() {
        val state = ArchiveUiState(plan = plan, openAlbum = "dropbox")
        assertEquals(listOf(1L, 2L), state.scopedEntries.map { it.item.mediaStoreId })
    }

    @Test
    fun `a folder with nothing waiting offers no check, even when another folder has files`() {
        val state = ArchiveUiState(plan = plan, openAlbum = "Camera", phase = ArchivePhase.IDLE)
        assertTrue(state.scopedEntries.isEmpty())
        assertFalse(state.offersCheck())
    }

    @Test
    fun `a folder with files waiting offers the check`() {
        val state = ArchiveUiState(plan = plan, openAlbum = "iDrive", phase = ArchivePhase.IDLE)
        assertTrue(state.offersCheck())
    }
}
