package com.gallery.sync.domain.backup

import android.content.Context
import android.net.Uri
import com.gallery.sync.data.local.dao.AlbumPreferenceDao
import com.gallery.sync.data.local.dao.BackupEntryDao
import com.gallery.sync.data.local.dao.FolderPreferenceDao
import com.gallery.sync.data.local.dao.UnsentDepartureDao
import com.gallery.sync.data.local.entity.BackupEntryEntity
import com.gallery.sync.data.local.entity.FolderPreferenceEntity
import com.gallery.sync.data.local.media.LocalMediaItem
import com.gallery.sync.data.local.media.MediaAccess
import com.gallery.sync.data.local.media.MediaScanner
import com.gallery.sync.data.local.settings.BackupPreferences
import com.gallery.sync.data.local.settings.BackupSettings
import com.gallery.sync.domain.billing.MultiCloudEntitlement
import com.gallery.sync.domain.repository.CloudUploaders
import com.gallery.sync.domain.repository.OneDriveRepository
import com.gallery.sync.domain.repository.OneDriveUploadRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * `refreshLedger`'s folder → destination resolution — the core of TASK-026's 24 Sept 2026 redesign.
 * A file's `location` is decided by which top-level folder it lives under (`DCIM`, `Pictures`...),
 * with the app-wide setting as the fallback for anything with no explicit folder choice.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class FolderDestinationTest {

    private val scanner: MediaScanner = mock()
    private val entryDao: BackupEntryDao = mock()
    private val folderDao: FolderPreferenceDao = mock()
    private val unsentDao: UnsentDepartureDao = mock()
    private val settings: BackupSettings = mock()

    private val engine = BackupEngine(
        scanner = scanner,
        entryDao = entryDao,
        albumDao = mock<AlbumPreferenceDao>(),
        folderDao = folderDao,
        unsentDao = unsentDao,
        settings = settings,
        repository = mock<OneDriveRepository>(),
        uploadRepository = mock<OneDriveUploadRepository>(),
        uploaders = mock<CloudUploaders>(),
        entitlement = mock<MultiCloudEntitlement>(),
        proxyMarker = mock(),
        albumIdentity = mock(),
        context = mock<Context>(),
        dispatcher = UnconfinedTestDispatcher()
    )

    private fun item(album: String, relativePath: String?, id: Long) = LocalMediaItem(
        mediaStoreId = id,
        contentUri = mock<Uri>(),
        displayName = "$album-$id.jpg",
        album = album,
        sizeBytes = 1_000L,
        dateModifiedEpochSeconds = 1L,
        mimeType = "image/jpeg",
        isVideo = false,
        relativePath = relativePath
    )

    private suspend fun givenScanned(vararg items: LocalMediaItem) {
        whenever(scanner.access()).thenReturn(MediaAccess.FULL)
        whenever(scanner.scanAll()).thenReturn(items.toList())
        whenever(scanner.scanEverything()).thenReturn(items.toList())
        whenever(entryDao.proxiedMediaStoreIds()).thenReturn(emptyList())
        whenever(entryDao.uploadedKeys()).thenReturn(emptyList())
        whenever(entryDao.pendingKeys()).thenReturn(emptyList())
        whenever(entryDao.countRetrievableOutsideDevice(any(), any())).thenReturn(0)
        whenever(entryDao.forgetAlbumsNotOnDevice(any(), any())).thenReturn(0)
        whenever(unsentDao.all()).thenReturn(emptyList())
    }

    @Test
    fun `a folder with an explicit choice routes its files there`() = runTest {
        givenScanned(item("Vacation", "Pictures/Vacation/", 1L))
        whenever(folderDao.all()).thenReturn(listOf(FolderPreferenceEntity("Pictures", BackupLocation.GOOGLE_PHOTOS)))
        whenever(settings.current()).thenReturn(BackupPreferences(backupLocation = BackupLocation.ONEDRIVE))

        engine.refreshLedger()

        val entries = argumentCaptor<List<BackupEntryEntity>>()
        verify(entryDao).insertIfNew(entries.capture())
        assertEquals(BackupLocation.GOOGLE_PHOTOS, entries.firstValue.single().location)
    }

    @Test
    fun `a folder with no explicit choice falls back to the app-wide default`() = runTest {
        givenScanned(item("Camera", "DCIM/Camera/", 1L))
        whenever(folderDao.all()).thenReturn(listOf(FolderPreferenceEntity("Pictures", BackupLocation.GOOGLE_PHOTOS)))
        whenever(settings.current()).thenReturn(BackupPreferences(backupLocation = BackupLocation.ONEDRIVE))

        engine.refreshLedger()

        val entries = argumentCaptor<List<BackupEntryEntity>>()
        verify(entryDao).insertIfNew(entries.capture())
        assertEquals(BackupLocation.ONEDRIVE, entries.firstValue.single().location)
    }

    @Test
    fun `two folders can genuinely go to two different providers in the same scan`() = runTest {
        givenScanned(
            item("Camera", "DCIM/Camera/", 1L),
            item("Vacation", "Pictures/Vacation/", 2L)
        )
        whenever(folderDao.all()).thenReturn(
            listOf(
                FolderPreferenceEntity("DCIM", BackupLocation.ONEDRIVE),
                FolderPreferenceEntity("Pictures", BackupLocation.GOOGLE_PHOTOS)
            )
        )
        whenever(settings.current()).thenReturn(BackupPreferences(backupLocation = BackupLocation.ONEDRIVE))

        engine.refreshLedger()

        val entries = argumentCaptor<List<BackupEntryEntity>>()
        verify(entryDao).insertIfNew(entries.capture())
        val byAlbum = entries.firstValue.associate { it.album to it.location }
        assertEquals(BackupLocation.ONEDRIVE, byAlbum["Camera"])
        assertEquals(BackupLocation.GOOGLE_PHOTOS, byAlbum["Vacation"])
    }

    @Test
    fun `an item with no relative path falls back to the app-wide default`() = runTest {
        // API below 29 has no RELATIVE_PATH column at all — relativePath is null, not a folder that
        // merely has no preference.
        givenScanned(item("Camera", relativePath = null, id = 1L))
        whenever(folderDao.all()).thenReturn(emptyList())
        whenever(settings.current()).thenReturn(BackupPreferences(backupLocation = BackupLocation.GOOGLE_PHOTOS))

        engine.refreshLedger()

        val entries = argumentCaptor<List<BackupEntryEntity>>()
        verify(entryDao).insertIfNew(entries.capture())
        assertEquals(BackupLocation.GOOGLE_PHOTOS, entries.firstValue.single().location)
    }

    @Test
    fun `newly discovered folders are seeded with the app-wide default, without disturbing an existing choice`() = runTest {
        givenScanned(
            item("Camera", "DCIM/Camera/", 1L),
            item("Vacation", "Pictures/Vacation/", 2L)
        )
        // Pictures already has an explicit choice; DCIM has never been seen before.
        whenever(folderDao.all()).thenReturn(listOf(FolderPreferenceEntity("Pictures", BackupLocation.GOOGLE_PHOTOS)))
        whenever(settings.current()).thenReturn(BackupPreferences(backupLocation = BackupLocation.ONEDRIVE))

        engine.refreshLedger()

        val seeded = argumentCaptor<List<FolderPreferenceEntity>>()
        verify(folderDao).insertIfNew(seeded.capture())
        val byName = seeded.firstValue.associate { it.folderName to it.backupLocation }
        // Both folders get a row from insertIfNew — IGNORE-on-conflict is what actually protects
        // Pictures' real choice from being overwritten, not omitting it from the call.
        assertEquals(BackupLocation.ONEDRIVE, byName["DCIM"])
        assertEquals(BackupLocation.ONEDRIVE, byName["Pictures"])
    }
}
