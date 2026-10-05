package com.gallery.sync.domain.backup

import android.content.Context
import com.gallery.sync.data.local.dao.AlbumPreferenceDao
import com.gallery.sync.data.local.dao.BackupEntryDao
import com.gallery.sync.data.local.dao.FolderPreferenceDao
import com.gallery.sync.data.local.dao.UnsentDepartureDao
import com.gallery.sync.data.local.entity.BackupEntryEntity
import com.gallery.sync.data.local.entity.BackupState
import com.gallery.sync.data.local.media.MediaAccess
import com.gallery.sync.data.local.media.MediaScanner
import com.gallery.sync.data.local.settings.BackupPreferences
import com.gallery.sync.data.local.settings.BackupSettings
import com.gallery.sync.domain.billing.MultiCloudEntitlement
import com.gallery.sync.domain.model.FolderPage
import com.gallery.sync.domain.model.RemoteMediaNode
import com.gallery.sync.domain.repository.CloudUploaders
import com.gallery.sync.domain.model.DataResult
import com.gallery.sync.domain.model.RemoteError
import com.gallery.sync.domain.repository.OneDriveRepository
import com.gallery.sync.domain.repository.OneDriveUploadRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * Step one of the three the backup has: **verification**. Ian, 5 Oct 2026 — *verification, then backup, then
 * optimise* — which the Backup Plans have promised in their own wording since they were written.
 *
 * Verification asks the Cloud what it already holds and writes the answer to the ledger. Before it existed the
 * check ran, told the wizard the truth (*8,620 already in OneDrive, 22 outstanding* on the Fold 8) and threw it
 * away: the ledger kept all 8,642 rows pending, the card read *1 of 8642*, and the upload path re-derived the
 * same answer file by file for hundreds of batches.
 *
 * **The invariant these tests exist for: verification never sends anything.** It is implemented as the upload
 * pass with the sending switched off, so there is no second matcher to drift — but that also means one wrong
 * branch would turn a check into an upload, which is why it is pinned here rather than trusted.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class VerifyAgainstCloudTest {

    private val scanner: MediaScanner = mock()
    private val entryDao: BackupEntryDao = mock()
    private val settings: BackupSettings = mock()
    private val repository: OneDriveRepository = mock()
    private val uploadRepository: OneDriveUploadRepository = mock()

    private val engine = BackupEngine(
        scanner = scanner,
        entryDao = entryDao,
        albumDao = mock<AlbumPreferenceDao>(),
        folderDao = mock<FolderPreferenceDao>(),
        unsentDao = mock<UnsentDepartureDao>(),
        settings = settings,
        repository = repository,
        uploadRepository = uploadRepository,
        uploaders = mock<CloudUploaders>(),
        entitlement = mock<MultiCloudEntitlement>(),
        proxyMarker = mock(),
        albumIdentity = mock<AlbumIdentityReconciler>(),
        context = mock<Context>(),
        dispatcher = UnconfinedTestDispatcher()
    )

    private val row = BackupEntryEntity(
        id = "key-1",
        mediaStoreId = 1L,
        contentUri = "content://media/external/images/media/1",
        displayName = "photo.jpg",
        album = "Temp01",
        sizeBytes = 2_048L,
        dateModifiedEpochSeconds = 100L,
        mimeType = "image/jpeg",
        isVideo = false,
        state = BackupState.PENDING,
        location = BackupLocation.ONEDRIVE
    )

    private suspend fun given(remote: List<RemoteMediaNode>) {
        whenever(scanner.access()).thenReturn(MediaAccess.FULL)
        whenever(settings.current()).thenReturn(BackupPreferences())
        whenever(entryDao.nextPendingAll(any(), any(), any())).thenReturn(listOf(row))
        whenever(entryDao.nextPending(any(), any(), any())).thenReturn(listOf(row))
        whenever(entryDao.countProxiedSiblings(any(), any())).thenReturn(0)
        whenever(entryDao.countPendingAll(any(), any())).thenReturn(0)
        whenever(entryDao.countPendingInSelectedAlbums(any(), any())).thenReturn(0)
        whenever(repository.listFolderByPath(any()))
            .thenReturn(DataResult.Success(FolderPage(nodes = remote, nextPageToken = null)))
    }

    private fun inCloud(name: String, size: Long) = RemoteMediaNode.File(
        id = "remote-1",
        name = name,
        modifiedAtUtc = 1_700_000_000_000L,
        mimeType = "image/jpeg",
        sizeBytes = size,
        widthPx = null,
        heightPx = null,
        eTag = null,
        createdAtUtc = 1_700_000_000_000L
    )

    @Test
    fun `a file the cloud already has is marked uploaded and not sent`() = runTest {
        given(listOf(inCloud("photo.jpg", 2_048L)))

        engine.verifyAgainstCloud(allAlbums = true)

        verify(entryDao).markUploaded(
            id = eq("key-1"),
            remoteItemId = eq("remote-1"),
            remoteSizeBytes = eq(2_048L),
            uploadedAt = any(),
            state = any()
        )
        verify(uploadRepository, never()).upload(any(), any(), any(), any(), any())
    }

    // Two more cases belong here and are not yet written: a file the Cloud does not have, and a file there at
    // a different size. Both fall through to the proxy-recovery branch, which calls `Uri.parse` — null under
    // plain JUnit, where `android.jar` is stubbed — so they need Robolectric, which this project does not use.
    // The invariant they would add is partly covered below and by the test above, which proves nothing is sent
    // even when the file is found. **Still to prove: that a file the Cloud lacks is left pending and unsent.**

    /** A listing that failed is not evidence of absence, and is certainly not evidence of presence. */
    @Test
    fun `a cloud that could not be listed marks nothing`() = runTest {
        whenever(scanner.access()).thenReturn(MediaAccess.FULL)
        whenever(settings.current()).thenReturn(BackupPreferences())
        whenever(entryDao.nextPendingAll(any(), any(), any())).thenReturn(listOf(row))
        whenever(entryDao.countProxiedSiblings(any(), any())).thenReturn(0)
        whenever(entryDao.countPendingAll(any(), any())).thenReturn(1)
        whenever(repository.listFolderByPath(any()))
            .thenReturn(DataResult.Failure(RemoteError.Network))

        val result = engine.verifyAgainstCloud(allAlbums = true)

        verify(entryDao, never()).markUploaded(any(), any(), any(), any(), any())
        verify(uploadRepository, never()).upload(any(), any(), any(), any(), any())
        assertEquals(1, result.deferred)
    }
}
