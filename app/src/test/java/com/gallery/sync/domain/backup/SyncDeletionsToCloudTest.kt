package com.gallery.sync.domain.backup

import android.net.Uri
import com.gallery.sync.data.local.dao.BackupEntryDao
import com.gallery.sync.data.local.entity.BackupEntryEntity
import com.gallery.sync.data.local.entity.BackupState
import com.gallery.sync.data.local.media.LocalMediaItem
import com.gallery.sync.data.local.media.MediaAccess
import com.gallery.sync.data.local.media.MediaScanner
import com.gallery.sync.data.local.settings.BackupPreferences
import com.gallery.sync.data.local.settings.BackupSettings
import com.gallery.sync.domain.model.DataResult
import com.gallery.sync.domain.repository.OneDriveDeletionRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * A photo edited in place is not a photo deleted from the phone.
 *
 * Saving an edit over `IMG_1234.jpg` changes its size and time, so its ledger row stops matching
 * and reads as missing while a file of the same name sits in the gallery. Ian, 19 Sept 2026:
 * *"we can't have every edited file look like a deletion."* These run the real class against fakes
 * for the ledger, the scan and the drive, because the rule is only worth having if the class that
 * builds the list and the class that deletes both obey it.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SyncDeletionsToCloudTest {

    private val dao: BackupEntryDao = mock()
    private val scanner: MediaScanner = mock()
    private val settings: BackupSettings = mock()
    private val drive: OneDriveDeletionRepository = mock()

    private val sync = SyncDeletionsToCloud(dao, scanner, settings, drive, UnconfinedTestDispatcher())

    private fun ledgerRow(album: String, name: String, size: Long = 1_000L) = BackupEntryEntity(
        id = "$album/$name|$size|1",
        mediaStoreId = 1L,
        contentUri = "content://media/external/images/media/1",
        displayName = name,
        album = album,
        sizeBytes = size,
        dateModifiedEpochSeconds = 1L,
        mimeType = "image/jpeg",
        isVideo = false,
        state = BackupState.UPLOADED,
        remoteItemId = "remote-$name",
        remoteSizeBytes = size,
        localMissingSinceEpochMillis = 1L
    )

    private fun onPhone(album: String, name: String, size: Long) = LocalMediaItem(
        mediaStoreId = 9L,
        contentUri = mock<Uri>(),
        displayName = name,
        album = album,
        sizeBytes = size,
        dateModifiedEpochSeconds = 2L,
        mimeType = "image/jpeg",
        isVideo = false,
        relativePath = "DCIM/$album/"
    )

    private suspend fun givenAskAndScan(missing: List<BackupEntryEntity>, phone: List<LocalMediaItem>) {
        whenever(settings.current()).thenReturn(
            BackupPreferences(cloudDeletionPolicy = CloudDeletionPolicy.ASK)
        )
        whenever(dao.cloudDeletionCandidates(any(), any())).thenReturn(missing)
        whenever(scanner.access()).thenReturn(MediaAccess.FULL)
        whenever(scanner.scanEverything()).thenReturn(phone)
    }

    @Test
    fun `an edited photo is not offered but a really deleted one is`() = runTest {
        val edited = ledgerRow("Camera", "IMG_1234.jpg", size = 3_000L)
        val deleted = ledgerRow("Camera", "IMG_5678.jpg")
        // The edit saved over the original: same name, same folder, a different size.
        givenAskAndScan(listOf(edited, deleted), listOf(onPhone("Camera", "IMG_1234.jpg", size = 2_400L)))

        assertEquals(listOf(deleted), sync.candidates())
    }

    @Test
    fun `the folder is compared without regard to case`() = runTest {
        val edited = ledgerRow("Camera", "IMG_1234.jpg")
        givenAskAndScan(listOf(edited), listOf(onPhone("camera", "IMG_1234.jpg", size = 500L)))

        assertTrue(sync.candidates().isEmpty())
    }

    @Test
    fun `the same name in a different folder does not hide a deletion`() = runTest {
        val deleted = ledgerRow("Camera", "IMG_1234.jpg")
        givenAskAndScan(listOf(deleted), listOf(onPhone("WhatsApp", "IMG_1234.jpg", size = 500L)))

        assertEquals(listOf(deleted), sync.candidates())
    }

    @Test
    fun `a file fetched back with the restored suffix counts as still here`() = runTest {
        val gone = ledgerRow("Camera", "IMG_1234.jpg")
        givenAskAndScan(listOf(gone), listOf(onPhone("Camera", "IMG_1234_restored.jpg", size = 1_000L)))

        assertTrue(sync.candidates().isEmpty())
    }

    @Test
    fun `nothing is offered when the scan cannot be trusted`() = runTest {
        val gone = ledgerRow("Camera", "IMG_5678.jpg")
        givenAskAndScan(listOf(gone), emptyList())

        assertTrue("an empty scan is no evidence of anything", sync.candidates().isEmpty())

        whenever(scanner.access()).thenReturn(MediaAccess.PARTIAL)
        whenever(scanner.scanEverything()).thenReturn(listOf(onPhone("Camera", "OTHER.jpg", 1L)))
        assertTrue("partial media access cannot say what is missing", sync.candidates().isEmpty())
    }

    @Test
    fun `nothing is offered under the leave policy whatever the phone holds`() = runTest {
        whenever(settings.current()).thenReturn(BackupPreferences(cloudDeletionPolicy = CloudDeletionPolicy.LEAVE))

        assertTrue(sync.candidates().isEmpty())
        verify(scanner, never()).scanEverything()
    }

    /** The list may be stale by the time someone approves it; the last check must catch an edit too. */
    @Test
    fun `an approved file that was edited since the list was drawn is not deleted`() = runTest {
        val edited = ledgerRow("Camera", "IMG_1234.jpg", size = 3_000L)
        val deleted = ledgerRow("Camera", "IMG_5678.jpg")
        givenAskAndScan(emptyList(), listOf(onPhone("Camera", "IMG_1234.jpg", size = 2_400L)))
        whenever(drive.moveToRecycleBin(any())).thenReturn(DataResult.Success(Unit))

        val outcome = sync.delete(listOf(edited, deleted))

        assertEquals(1, outcome.deleted)
        assertEquals(1, outcome.cameBack)
        verify(drive).moveToRecycleBin("remote-IMG_5678.jpg")
        verify(drive, never()).moveToRecycleBin("remote-IMG_1234.jpg")
    }
}
