package com.gallery.sync.domain.backup

import com.gallery.sync.data.local.dao.BackupEntryDao
import com.gallery.sync.data.local.dao.FolderPreferenceDao
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

    private suspend fun givenAlbums() {
        whenever(scanner.scanAlbums()).thenReturn(
            listOf(
                MediaAlbum("Camera", 1, 1L, topLevelFolder = "DCIM"),
                MediaAlbum("Vacation", 1, 1L, topLevelFolder = "Pictures"),
                MediaAlbum("Trip", 1, 1L, topLevelFolder = "Pictures"),
                MediaAlbum("Odd", 1, 1L, topLevelFolder = null)
            )
        )
    }

    @Test
    fun `stores the choice and re-points only that folder's unsent rows`() = runTest {
        givenAlbums()

        setFolder("Pictures", BackupLocation.GOOGLE_PHOTOS)

        verify(folderDao).setPreferences(listOf(FolderPreferenceEntity("Pictures", BackupLocation.GOOGLE_PHOTOS)))
        verify(entryDao).retargetUnsent(listOf("Vacation", "Trip"), BackupLocation.GOOGLE_PHOTOS)
        verify(entryDao, never()).retargetUnsent(listOf("Camera"), BackupLocation.GOOGLE_PHOTOS)
    }

    @Test
    fun `a folder with no albums on the device is stored without touching any rows`() = runTest {
        givenAlbums()

        setFolder("Movies", BackupLocation.GOOGLE_PHOTOS)

        verify(folderDao).setPreferences(any())
        verify(entryDao, never()).retargetUnsent(any(), any(), any(), any())
    }

    @Test
    fun `an album spread over two top-level folders belongs to both`() = runTest {
        // blaze-test lives in DCIM and in Movies: one album name, two folders. Ian, 25 Sept 2026: Movies went
        // missing from the folder list because only the first folder was counted.
        whenever(scanner.scanAlbums()).thenReturn(
            listOf(
                MediaAlbum(
                    "blaze-test", 15, 3L, topLevelFolder = "DCIM",
                    inFolders = mapOf("DCIM" to FolderShare(3, 1L), "Movies" to FolderShare(12, 2L))
                )
            )
        )

        setFolder("Movies", BackupLocation.BACKBLAZE_B2)

        verify(entryDao).retargetUnsent(listOf("blaze-test"), BackupLocation.BACKBLAZE_B2)
    }
}
