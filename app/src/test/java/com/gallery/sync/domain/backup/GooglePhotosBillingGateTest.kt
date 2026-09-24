package com.gallery.sync.domain.backup

import android.content.Context
import com.gallery.sync.data.local.dao.AlbumPreferenceDao
import com.gallery.sync.data.local.dao.FolderPreferenceDao
import com.gallery.sync.data.local.dao.BackupEntryDao
import com.gallery.sync.data.local.dao.UnsentDepartureDao
import com.gallery.sync.data.local.entity.BackupEntryEntity
import com.gallery.sync.data.local.entity.BackupState
import com.gallery.sync.data.local.media.MediaAccess
import com.gallery.sync.data.local.media.MediaScanner
import com.gallery.sync.data.local.settings.BackupSettings
import com.gallery.sync.data.remote.onedrive.UploadSource
import com.gallery.sync.domain.billing.BillingRepository
import com.gallery.sync.domain.repository.GooglePhotosUploadRepository
import com.gallery.sync.domain.repository.OneDriveRepository
import com.gallery.sync.domain.repository.OneDriveUploadRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * The actual upload boundary must not trust `backupLocation` alone — see the comment in
 * `BackupEngine.uploadPendingWhileHolding` right above the check this tests. CLAUDE.md: Google Photos
 * features are gated behind `BillingRepository.isPurchased()`, and a stored setting can outlive the
 * purchase it depended on (a refund, most plausibly).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class GooglePhotosBillingGateTest {

    private val scanner: MediaScanner = mock()
    private val entryDao: BackupEntryDao = mock()
    private val googlePhotosUploadRepository: GooglePhotosUploadRepository = mock()
    private val billing: BillingRepository = mock()
    private val context: Context = mock {
        on { contentResolver } doReturn mock()
    }

    private val engine = BackupEngine(
        scanner = scanner,
        entryDao = entryDao,
        albumDao = mock<AlbumPreferenceDao>(),
        folderDao = mock<FolderPreferenceDao>(),
        unsentDao = mock<UnsentDepartureDao>(),
        settings = mock<BackupSettings>(),
        repository = mock<OneDriveRepository>(),
        uploadRepository = mock<OneDriveUploadRepository>(),
        googlePhotosUploadRepository = googlePhotosUploadRepository,
        billing = billing,
        proxyMarker = mock(),
        albumIdentity = mock(),
        context = context,
        dispatcher = UnconfinedTestDispatcher()
    )

    private val pendingRow = BackupEntryEntity(
        id = "k1",
        mediaStoreId = 1L,
        contentUri = "content://media/external/images/media/1",
        displayName = "IMG_1.jpg",
        album = "Vacation",
        sizeBytes = 1_000L,
        dateModifiedEpochSeconds = 5L,
        mimeType = "image/jpeg",
        isVideo = false,
        state = BackupState.PENDING,
        location = BackupLocation.GOOGLE_PHOTOS
    )

    private suspend fun givenOnePendingGooglePhotosRow() {
        whenever(scanner.access()).thenReturn(MediaAccess.FULL)
        whenever(entryDao.nextPending(any(), any(), any())).thenReturn(listOf(pendingRow))
        whenever(entryDao.countPendingInSelectedAlbums(any(), any())).thenReturn(0)
    }

    @Test
    fun `not purchased means the row is left untouched, not uploaded`() = runTest {
        givenOnePendingGooglePhotosRow()
        whenever(billing.isPurchased()).thenReturn(false)

        val result = engine.uploadPending()

        verify(googlePhotosUploadRepository, never()).upload(any<UploadSource>(), any())
        assertEquals(0, result.uploaded)
    }

    // The "purchased" case would need to reach ContentUriUploadSource's Uri.parse call, which does
    // not run off-device — the same accepted limitation OneDriveSignIn's own doc comment notes
    // ("a seam for tests only, because Uri.parse does not run off a device"). The gate above is what
    // actually needed protecting against regression; the happy path is exercised on hardware instead.
}
