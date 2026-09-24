package com.gallery.sync.domain.backup

import android.content.Context
import com.gallery.sync.data.local.dao.AlbumPreferenceDao
import com.gallery.sync.data.local.dao.BackupEntryDao
import com.gallery.sync.data.local.dao.UnsentDepartureDao
import com.gallery.sync.data.local.media.MediaAccess
import com.gallery.sync.data.local.media.MediaScanner
import com.gallery.sync.data.local.settings.BackupSettings
import com.gallery.sync.domain.billing.BillingRepository
import com.gallery.sync.domain.repository.GooglePhotosUploadRepository
import com.gallery.sync.domain.repository.OneDriveRepository
import com.gallery.sync.domain.repository.OneDriveUploadRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doSuspendableAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/**
 * Two upload runs must never overlap. Found 20 Sept 2026 on the Moto G: pressing *Resume* after *Pause*
 * started the automatic chain and the manual chain within 15 ms of each other, both read the same
 * pending rows and uploaded every one of them, and OneDrive kept the second copy of each as
 * `name 1.jpg`. 24 photos became 30 files. Nothing in the ledger could see it.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class UploadRunExclusionTest {

    private val scanner: MediaScanner = mock()
    private val entryDao: BackupEntryDao = mock()
    private val identity: AlbumIdentityReconciler = mock()

    private val engine = BackupEngine(
        scanner = scanner,
        entryDao = entryDao,
        albumDao = mock<AlbumPreferenceDao>(),
        unsentDao = mock<UnsentDepartureDao>(),
        settings = mock<BackupSettings>(),
        repository = mock<OneDriveRepository>(),
        uploadRepository = mock<OneDriveUploadRepository>(),
        googlePhotosUploadRepository = mock<GooglePhotosUploadRepository>(),
        billing = mock<BillingRepository>(),
        proxyMarker = mock(),
        albumIdentity = identity,
        context = mock<Context>(),
        dispatcher = UnconfinedTestDispatcher()
    )

    @Test
    fun `a second run waits for the first instead of uploading the same files beside it`() = runTest {
        whenever(scanner.access()).thenReturn(MediaAccess.FULL)
        whenever(entryDao.countPendingInSelectedAlbums(any(), any())).thenReturn(0)

        val gate = CompletableDeferred<Unit>()
        var inside = 0
        var mostAtOnce = 0
        var reads = 0
        whenever(entryDao.nextPending(any(), any(), any())).doSuspendableAnswer {
            reads++
            inside++
            mostAtOnce = maxOf(mostAtOnce, inside)
            gate.await()
            inside--
            emptyList()
        }

        val first = launch(UnconfinedTestDispatcher(testScheduler)) { engine.uploadPending() }
        val second = launch(UnconfinedTestDispatcher(testScheduler)) { engine.uploadPending() }

        assertEquals("only the first run has read the pending rows so far", 1, reads)

        gate.complete(Unit)
        joinAll(first, second)

        assertEquals("the second run went on to read them once the first was done", 2, reads)
        assertEquals("never two runs inside at once", 1, mostAtOnce)
    }
}
