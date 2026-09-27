package com.gallery.sync.domain.backup

import android.content.Context
import android.net.Uri
import com.gallery.sync.data.local.dao.AlbumPreferenceDao
import com.gallery.sync.data.local.dao.BackupEntryDao
import com.gallery.sync.data.local.dao.FolderPreferenceDao
import com.gallery.sync.data.local.dao.ProxiedSizes
import com.gallery.sync.data.local.dao.UnsentDepartureDao
import com.gallery.sync.data.local.entity.BackupEntryEntity
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
 * A file this app shrank is skipped by the ledger scan, so it is not sent again as a new photo. Measured on the
 * Moto G, 26 Sept 2026: skipping by MediaStore id alone meant an edit saved over the shrunk file was never
 * uploaded. Ian: "treat the edited optimized file as a new file". So the skip holds only while the file is still
 * one of the sizes this app knows, and anything else is scanned, ledgered and uploaded.
 *
 * Just as important is what must **not** happen: a shrunk file whose index is stale, or a scan that suddenly
 * finds a dozen "edits", must not send a copy of the library to the Cloud.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class EditedFileLedgerTest {

    private val scanner: MediaScanner = mock()
    private val entryDao: BackupEntryDao = mock()
    private val settings: BackupSettings = mock()
    private val unsentDao: UnsentDepartureDao = mock()

    private val engine = BackupEngine(
        scanner = scanner,
        entryDao = entryDao,
        albumDao = mock<AlbumPreferenceDao>(),
        folderDao = mock<FolderPreferenceDao>(),
        unsentDao = unsentDao,
        settings = settings,
        repository = mock<OneDriveRepository>(),
        uploadRepository = mock<OneDriveUploadRepository>(),
        uploaders = mock<CloudUploaders>(),
        entitlement = mock<MultiCloudEntitlement>(),
        proxyMarker = mock(),
        albumIdentity = mock<AlbumIdentityReconciler>(),
        context = mock<Context>(),
        dispatcher = UnconfinedTestDispatcher()
    )

    private val original = 3_845_655L
    private val shrunk = 744_869L

    private fun onPhone(id: Long, size: Long) = LocalMediaItem(
        mediaStoreId = id,
        contentUri = mock<Uri>(),
        displayName = "photo-$id.jpg",
        album = "Temp01",
        sizeBytes = size,
        dateModifiedEpochSeconds = 100L + size,
        mimeType = "image/jpeg",
        isVideo = false,
        relativePath = "DCIM/Temp01/"
    )

    private suspend fun scan(items: List<LocalMediaItem>, proxied: List<ProxiedSizes>): List<BackupEntryEntity> {
        whenever(settings.current()).thenReturn(BackupPreferences())
        whenever(scanner.access()).thenReturn(MediaAccess.FULL)
        whenever(scanner.scanAll()).thenReturn(items)
        whenever(scanner.scanEverything()).thenReturn(items)
        whenever(entryDao.proxiedSizes()).thenReturn(proxied)
        whenever(entryDao.uploadedKeys()).thenReturn(emptyList())
        whenever(entryDao.pendingKeys()).thenReturn(emptyList())
        whenever(unsentDao.all()).thenReturn(emptyList())
        whenever(entryDao.countRetrievableOutsideDevice(any(), any())).thenReturn(0)
        whenever(entryDao.forgetAlbumsNotOnDevice(any(), any())).thenReturn(0)

        engine.refreshLedger()

        val inserted = argumentCaptor<List<BackupEntryEntity>>()
        verify(entryDao).insertIfNew(inserted.capture())
        return inserted.firstValue
    }

    @Test
    fun `a shrunk file at the size the app wrote is skipped`() = runTest {
        val inserted = scan(listOf(onPhone(1, shrunk)), listOf(ProxiedSizes(1, original, shrunk)))
        assertEquals(emptyList<BackupEntryEntity>(), inserted)
    }

    @Test
    fun `a shrunk file whose index still reports the original size is skipped`() = runTest {
        val inserted = scan(listOf(onPhone(1, original)), listOf(ProxiedSizes(1, original, shrunk)))
        assertEquals(emptyList<BackupEntryEntity>(), inserted)
    }

    @Test
    fun `an edit saved over a shrunk file is scanned as a new file`() = runTest {
        val inserted = scan(listOf(onPhone(1, 744_886)), listOf(ProxiedSizes(1, original, shrunk)))

        assertEquals(1, inserted.size)
        assertEquals(744_886L, inserted.single().sizeBytes)
        assertEquals(1L, inserted.single().mediaStoreId)
    }

    @Test
    fun `an unshrunk file is unaffected`() = runTest {
        val inserted = scan(listOf(onPhone(2, 500_000)), listOf(ProxiedSizes(1, original, shrunk)))
        assertEquals(listOf(2L), inserted.map { it.mediaStoreId })
    }

    @Test
    fun `a dozen edits at once are treated as a bad index, not as edits`() = runTest {
        val proxied = (1L..12L).map { ProxiedSizes(it, original, shrunk) }
        val items = (1L..12L).map { onPhone(it, 800_000 + it) }

        val inserted = scan(items, proxied)

        assertEquals(emptyList<BackupEntryEntity>(), inserted)
    }

    @Test
    fun `a single edit among many shrunk files is still an edit`() = runTest {
        val proxied = (1L..40L).map { ProxiedSizes(it, original, shrunk) }
        val items = (1L..40L).map { onPhone(it, if (it == 7L) 744_886 else shrunk) }

        val inserted = scan(items, proxied)

        assertEquals(listOf(7L), inserted.map { it.mediaStoreId })
    }
}
