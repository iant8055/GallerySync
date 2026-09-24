package com.gallery.sync.ui.restore

import com.gallery.sync.data.local.entity.BackupEntryEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

/** The bar under the Restore button: shown only while running, weighted by bytes. */
class RestoreProgressTest {

    private fun row(id: String, bytes: Long, state: RowState): RestoreRow {
        val entry = mock(BackupEntryEntity::class.java)
        `when`(entry.id).thenReturn(id)
        `when`(entry.sizeBytes).thenReturn(bytes)
        `when`(entry.remoteSizeBytes).thenReturn(bytes)
        `when`(entry.localProxySizeBytes).thenReturn(null)
        return RestoreRow(entry, RowKind.Download, state)
    }

    @Test
    fun `nothing to show when not running`() {
        val rows = listOf(row("a", 100, RowState.Waiting))
        assertNull(RestoreUiState(rows = rows, selection = setOf("a"), running = false).progress)
    }

    @Test
    fun `counts finished files and weights by bytes`() {
        val rows = listOf(
            row("a", 100, RowState.Done(100)),
            row("b", 300, RowState.Working(50)),
            row("c", 600, RowState.Waiting)
        )
        val p = RestoreUiState(rows = rows, selection = setOf("a", "b", "c"), running = true).progress!!
        assertEquals(1, p.finished)
        assertEquals(3, p.total)
        assertEquals(0.25f, p.fraction, 0.001f)
    }

    @Test
    fun `a failed file counts as passed`() {
        val rows = listOf(row("a", 100, RowState.Failed("x")), row("b", 100, RowState.Waiting))
        val p = RestoreUiState(rows = rows, selection = setOf("a", "b"), running = true).progress!!
        assertEquals(1, p.finished)
        assertEquals(0.5f, p.fraction, 0.001f)
    }
}
