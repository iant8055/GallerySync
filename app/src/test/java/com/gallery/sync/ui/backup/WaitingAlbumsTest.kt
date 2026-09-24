package com.gallery.sync.ui.backup

import com.gallery.sync.data.local.entity.AlbumMode
import org.junit.Assert.assertEquals
import org.junit.Test

class WaitingAlbumsTest {

    private fun album(name: String, mode: AlbumMode, files: Int = 3) =
        AlbumRow(name = name, itemCount = files, totalBytes = 1L, mode = mode)

    @Test
    fun `only albums at Off that nobody has dealt with are waiting`() {
        val state = BackupUiState(
            albums = listOf(
                album("New", AlbumMode.OFF),
                album("Chosen", AlbumMode.BACKUP),
                album("Dismissed", AlbumMode.OFF),
                album("Empty", AlbumMode.OFF, files = 0)
            ),
            acknowledgedAlbums = setOf("Dismissed")
        )

        assertEquals(listOf("New"), state.waitingAlbums.map { it.name })
    }

    @Test
    fun `an album the user set to Off themselves stops waiting`() {
        val state = BackupUiState(
            albums = listOf(album("A", AlbumMode.OFF)),
            acknowledgedAlbums = setOf("A")
        )
        assertEquals(0, state.waitingAlbums.size)
    }
}
