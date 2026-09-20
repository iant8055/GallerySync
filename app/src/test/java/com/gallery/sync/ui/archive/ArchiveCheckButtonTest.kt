package com.gallery.sync.ui.archive

import android.net.Uri
import com.gallery.sync.data.local.media.LocalMediaItem
import com.gallery.sync.domain.backup.ArchiveEntry
import com.gallery.sync.domain.backup.ArchivePlan
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.mock

/**
 * When *Check these files* is on the screen. Ian, 19 Sept 2026: *"how to restart Archive after
 * cancel??"*. After a refused removal the tab reported "Nothing was removed" with the files still
 * waiting and no button to ask again.
 */
class ArchiveCheckButtonTest {

    private val file = LocalMediaItem(
        mediaStoreId = 1L,
        contentUri = mock<Uri>(),
        displayName = "a.jpg",
        album = "Temp 9",
        sizeBytes = 1_000L,
        dateModifiedEpochSeconds = 1L,
        mimeType = "image/jpeg",
        isVideo = false,
        relativePath = "DCIM/Temp 9/"
    )

    private fun state(phase: ArchivePhase, files: Boolean = true, supported: Boolean = true) = ArchiveUiState(
        phase = phase,
        isSupported = supported,
        plan = ArchivePlan(entries = if (files) listOf(ArchiveEntry(file)) else emptyList())
    )

    @Test
    fun `it is offered before the first check`() {
        assertTrue(state(ArchivePhase.IDLE).offersCheck())
    }

    @Test
    fun `it is offered again after a removal reported and files are still waiting`() {
        assertTrue("a refused or part-allowed removal must not strand the files", state(ArchivePhase.DONE).offersCheck())
    }

    @Test
    fun `it is not offered when a removal took everything`() {
        assertFalse(state(ArchivePhase.DONE, files = false).offersCheck())
    }

    @Test
    fun `it is not offered while a check or a removal is running, or while the question is up`() {
        for (phase in listOf(ArchivePhase.VALIDATING, ArchivePhase.REMOVING, ArchivePhase.READY)) {
            assertFalse("$phase", state(phase).offersCheck())
        }
    }

    @Test
    fun `it is not offered where archiving is not supported`() {
        assertFalse(state(ArchivePhase.IDLE, supported = false).offersCheck())
        assertFalse(state(ArchivePhase.DONE, supported = false).offersCheck())
    }
}
