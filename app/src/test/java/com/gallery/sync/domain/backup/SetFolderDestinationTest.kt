package com.gallery.sync.domain.backup

import com.gallery.sync.data.local.dao.BackupEntryDao
import com.gallery.sync.data.local.dao.FolderPreferenceDao
import com.gallery.sync.data.local.dao.UnsentRoute
import com.gallery.sync.data.local.entity.FolderPreferenceEntity
import com.gallery.sync.data.local.media.FolderShare
import com.gallery.sync.data.local.media.MediaAlbum
import com.gallery.sync.data.local.media.MediaScanner
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class SetFolderDestinationTest {

    private val scanner: MediaScanner = mock()
    private val folderDao: FolderPreferenceDao = mock()
    private val entryDao: BackupEntryDao = mock()
    private val setFolder = SetFolderDestination(scanner, folderDao, entryDao)

    private val od = BackupLocation.ONEDRIVE

    private suspend fun given(stored: Map<String, BackupLocation>, vararg rows: UnsentRoute) {
        whenever(folderDao.all()).thenReturn(stored.map { FolderPreferenceEntity(it.key, it.value) })
        whenever(entryDao.unsentRoutes(any(), any())).thenReturn(rows.toList())
    }

    private fun row(id: String, path: String?, album: String = "A", location: BackupLocation = od) =
        UnsentRoute(id, album, path, location)

    @Test
    fun `stores the choice and re-points only that folder's unsent rows`() = runTest {
        given(
            mapOf("DCIM" to od, "Pictures" to BackupLocation.GOOGLE_PHOTOS),
            row("cam", "DCIM/Camera/"), row("vac", "Pictures/Vacation/"), row("trip", "Pictures/Trip/")
        )

        setFolder("Pictures", BackupLocation.GOOGLE_PHOTOS)

        verify(folderDao).setPreferences(listOf(FolderPreferenceEntity("Pictures", BackupLocation.GOOGLE_PHOTOS)))
        verify(entryDao).retargetUnsentRows(listOf("vac", "trip"), BackupLocation.GOOGLE_PHOTOS)
        verify(entryDao, never()).retargetUnsentRows(listOf("cam"), BackupLocation.GOOGLE_PHOTOS)
    }

    @Test
    fun `a folder with nothing waiting is stored without touching any rows`() = runTest {
        given(mapOf("DCIM" to od), row("cam", "DCIM/Camera/"))

        setFolder("Movies", BackupLocation.GOOGLE_PHOTOS)

        verify(folderDao).setPreferences(any())
        verify(entryDao, never()).retargetUnsentRows(any(), any(), any(), any())
    }

    @Test
    fun `a folder inside DCIM takes only its own files, not the rest of DCIM`() = runTest {
        // Ian, 22 Nov 2026: choosing pCloud for DCIM/Funny stuff moved the whole of DCIM to pCloud.
        given(
            mapOf("DCIM" to od, "DCIM/Funny stuff" to BackupLocation.PCLOUD),
            row("cam", "DCIM/Camera/"), row("fun", "DCIM/Funny stuff/"), row("deep", "DCIM/Funny stuff/Cats/")
        )

        setFolder("DCIM/Funny stuff", BackupLocation.PCLOUD)

        verify(entryDao).retargetUnsentRows(listOf("fun", "deep"), BackupLocation.PCLOUD)
        verify(entryDao, never()).retargetUnsentRows(listOf("cam"), BackupLocation.PCLOUD)
    }

    @Test
    fun `choosing for DCIM leaves a folder inside it that has its own Cloud alone`() = runTest {
        given(
            mapOf("DCIM" to BackupLocation.DROPBOX, "DCIM/Funny stuff" to BackupLocation.PCLOUD),
            row("cam", "DCIM/Camera/", location = BackupLocation.PCLOUD),
            row("fun", "DCIM/Funny stuff/", location = BackupLocation.PCLOUD)
        )

        setFolder("DCIM", BackupLocation.DROPBOX)

        verify(entryDao).retargetUnsentRows(listOf("cam"), BackupLocation.DROPBOX)
        verify(entryDao, never()).retargetUnsentRows(listOf("cam", "fun"), BackupLocation.DROPBOX)
    }

    @Test
    fun `an album spread over two top-level folders moves only the files in the chosen one`() = runTest {
        // blaze-test lives in DCIM and in Movies: one album name, two folders (Ian, 25 Sept 2026).
        given(
            mapOf("DCIM" to od, "Movies" to BackupLocation.BACKBLAZE_B2),
            row("d", "DCIM/blaze-test/", "blaze-test"), row("m", "Movies/blaze-test/", "blaze-test")
        )

        setFolder("Movies", BackupLocation.BACKBLAZE_B2)

        verify(entryDao).retargetUnsentRows(listOf("m"), BackupLocation.BACKBLAZE_B2)
    }

    @Test
    fun `a row with no recorded folder goes by where its album is now, and one with none at all stays put`() = runTest {
        whenever(scanner.scanAlbums()).thenReturn(
            listOf(
                MediaAlbum("Trip", 1, 1L, topLevelFolder = "Pictures", inPaths = mapOf("Pictures/Trip" to FolderShare(1, 1L))),
                MediaAlbum("Odd", 1, 1L, topLevelFolder = null)
            )
        )
        given(mapOf("Pictures" to BackupLocation.DROPBOX), row("t", null, "Trip"), row("o", null, "Odd"))

        setFolder("Pictures", BackupLocation.DROPBOX)

        verify(entryDao).retargetUnsentRows(listOf("t"), BackupLocation.DROPBOX)
    }

    @Test
    fun `a row already headed there is not rewritten`() = runTest {
        given(mapOf("Pictures" to BackupLocation.DROPBOX), row("t", "Pictures/Trip/", location = BackupLocation.DROPBOX))

        setFolder("Pictures", BackupLocation.DROPBOX)

        verify(entryDao, never()).retargetUnsentRows(any(), any(), any(), any())
    }
}
