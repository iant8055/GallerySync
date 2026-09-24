package com.gallery.sync.domain.backup

import android.content.Context
import com.gallery.sync.data.local.dao.AlbumPreferenceDao
import com.gallery.sync.data.local.dao.FolderPreferenceDao
import com.gallery.sync.data.local.dao.BackupEntryDao
import com.gallery.sync.data.local.dao.UnsentDepartureDao
import com.gallery.sync.data.local.media.MediaScanner
import com.gallery.sync.data.local.settings.BackupSettings
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
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * What counts as "left to do".
 *
 * The worker chains another batch whenever this is above zero. It used to count files that had failed five
 * times, which the queue never picks again, so a single permanently failed file kept the worker re-running
 * about once a second for ever, each run rescanning the library. Found on the Moto G, 21 Sept 2026, with a
 * file OneDrive refused the name of. The count has to be asked the same way the queue picks: with the
 * attempt limit.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RemainingWorkTest {

    private val entryDao: BackupEntryDao = mock()

    private val engine = BackupEngine(
        scanner = mock<MediaScanner>(),
        entryDao = entryDao,
        albumDao = mock<AlbumPreferenceDao>(),
        folderDao = mock<FolderPreferenceDao>(),
        unsentDao = mock<UnsentDepartureDao>(),
        settings = mock<BackupSettings>(),
        repository = mock<OneDriveRepository>(),
        uploadRepository = mock<OneDriveUploadRepository>(),
        googlePhotosUploadRepository = mock<GooglePhotosUploadRepository>(),
        billing = mock<BillingRepository>(),
        proxyMarker = mock(),
        albumIdentity = mock(),
        context = mock<Context>(),
        dispatcher = UnconfinedTestDispatcher()
    )

    @Test
    fun `the count for the albums the user chose ignores files that have used up their attempts`() = runTest {
        whenever(entryDao.countPendingInSelectedAlbums(any(), any())).thenReturn(2)

        assertEquals(2, engine.outstandingCount())

        verify(entryDao).countPendingInSelectedAlbums(BackupEngine.MAX_ATTEMPTS, com.gallery.sync.data.local.entity.BackupState.UPLOADED)
    }

    @Test
    fun `the count across every album ignores them too`() = runTest {
        whenever(entryDao.countPendingAll(any(), any())).thenReturn(7)

        assertEquals(7, engine.outstandingCountAll())

        verify(entryDao).countPendingAll(BackupEngine.MAX_ATTEMPTS, com.gallery.sync.data.local.entity.BackupState.UPLOADED)
    }
}
