package com.gallery.sync.ui.backup

import com.gallery.sync.data.local.entity.AlbumMode
import org.junit.Assert.assertEquals
import org.junit.Test

/** The album card's red "N files not backed up" (Ian, 23 Nov 2026): files that need a decision, nothing else. */
class AlbumNeedsDecisionTest {

    private fun row(mode: AlbumMode = AlbumMode.BACKUP) = AlbumRow(name = "A", itemCount = 10, totalBytes = 1L, mode = mode)

    @Test
    fun `gone from the Cloud, failed and unable to finish are all counted`() {
        assertEquals(6, row().copy(missingFromCloud = 1, failedCount = 2, stalledCount = 3).needsDecisionCount)
    }

    @Test
    fun `files kept out by choice are not counted`() {
        assertEquals(0, row().copy(skippedCount = 4).needsDecisionCount)
    }

    @Test
    fun `failed files in an Off album send nothing, so they are not counted`() {
        assertEquals(1, row(AlbumMode.OFF).copy(missingFromCloud = 1, failedCount = 2).needsDecisionCount)
    }
}
