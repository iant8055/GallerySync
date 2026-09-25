package com.gallery.sync.domain.backup

import android.content.Context
import android.net.Uri
import com.gallery.sync.data.local.dao.AlbumPreferenceDao
import com.gallery.sync.data.local.dao.BackupEntryDao
import com.gallery.sync.data.local.dao.FolderPreferenceDao
import com.gallery.sync.data.local.dao.UnsentDepartureDao
import com.gallery.sync.data.local.entity.BackupEntryEntity
import com.gallery.sync.data.local.entity.BackupState
import com.gallery.sync.data.local.entity.backupKeyOf
import com.gallery.sync.data.local.media.LocalMediaItem
import com.gallery.sync.data.local.media.MediaScanner
import com.gallery.sync.data.local.settings.BackupSettings
import com.gallery.sync.domain.billing.MultiCloudEntitlement
import com.gallery.sync.domain.repository.CloudUploaders
import com.gallery.sync.domain.repository.CloudVerifier
import com.gallery.sync.domain.repository.CloudVerifiers
import com.gallery.sync.domain.repository.OneDriveRepository
import com.gallery.sync.domain.repository.OneDriveUploadRepository
import com.gallery.sync.domain.repository.RemoteCheck
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/**
 * Archive's live check for a file another cloud holds (TASK-027): the engine asks that cloud about the recorded id,
 * and only a live answer at the phone's size confirms it.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ArchiveElsewhereTest {

    private val entryDao: BackupEntryDao = mock()

    /** The cloud's answers, by remote id. A missing id reads as [RemoteCheck.Unknown], the cautious default. */
    private val answers = mutableMapOf<String, RemoteCheck>()
    private val asked = mutableListOf<String>()

    private val dropbox = object : CloudVerifier {
        override val location = BackupLocation.DROPBOX
        override suspend fun sizeOf(remoteItemId: String): RemoteCheck {
            asked += remoteItemId
            return answers[remoteItemId] ?: RemoteCheck.Unknown
        }
    }

    private fun engine(verifiers: Set<CloudVerifier> = setOf(dropbox)) = BackupEngine(
        scanner = mock<MediaScanner>(),
        entryDao = entryDao,
        albumDao = mock<AlbumPreferenceDao>(),
        folderDao = mock<FolderPreferenceDao>(),
        unsentDao = mock<UnsentDepartureDao>(),
        settings = mock<BackupSettings>(),
        repository = mock<OneDriveRepository>(),
        uploadRepository = mock<OneDriveUploadRepository>(),
        uploaders = mock<CloudUploaders>(),
        entitlement = mock<MultiCloudEntitlement>(),
        proxyMarker = mock(),
        albumIdentity = mock(),
        context = mock<Context>(),
        dispatcher = UnconfinedTestDispatcher(),
        verifiers = CloudVerifiers(verifiers)
    )

    private fun item(name: String, id: Long, size: Long = 1_000L) = LocalMediaItem(
        mediaStoreId = id,
        contentUri = mock<Uri>(),
        displayName = name,
        album = "dropbox",
        sizeBytes = size,
        dateModifiedEpochSeconds = 1L,
        mimeType = "image/jpeg",
        isVideo = false,
        relativePath = null
    )

    private fun rowFor(
        item: LocalMediaItem,
        remoteId: String?,
        state: BackupState = BackupState.UPLOADED,
        location: BackupLocation = BackupLocation.DROPBOX
    ) = BackupEntryEntity(
        id = backupKeyOf(item.album, item.displayName, item.sizeBytes, item.dateModifiedEpochSeconds),
        mediaStoreId = item.mediaStoreId,
        contentUri = "content://x",
        displayName = item.displayName,
        album = item.album,
        sizeBytes = item.sizeBytes,
        dateModifiedEpochSeconds = item.dateModifiedEpochSeconds,
        mimeType = item.mimeType,
        isVideo = false,
        state = state,
        remoteItemId = remoteId,
        location = location
    )

    private suspend fun givenRows(vararg rows: BackupEntryEntity) {
        whenever(entryDao.uploadedKeys()).thenReturn(emptyList())
        whenever(entryDao.entriesByIds(any())).thenReturn(rows.toList())
    }

    @Test
    fun `a file Dropbox confirms at the phone's size is confirmed`() = runTest {
        val a = item("a.jpg", 1)
        givenRows(rowFor(a, "id:A"))
        answers["id:A"] = RemoteCheck.Present(1_000L)

        val result = engine().confirmStillInCloud(listOf(a))

        assertEquals(listOf(a), result.confirmed)
        assertTrue(result.missing.isEmpty() && result.unconfirmed.isEmpty())
        assertEquals(listOf("id:A"), asked)
    }

    @Test
    fun `a wrong size, a deleted file, an unanswered question and a missing adapter all keep the file`() = runTest {
        val wrong = item("wrong.jpg", 1)
        val gone = item("gone.jpg", 2)
        val unknown = item("unknown.jpg", 3)
        givenRows(rowFor(wrong, "id:W"), rowFor(gone, "id:G"), rowFor(unknown, "id:U"))
        answers["id:W"] = RemoteCheck.Present(999L)
        answers["id:G"] = RemoteCheck.Gone

        val result = engine().confirmStillInCloud(listOf(wrong, gone, unknown))

        assertTrue("nothing here may be confirmed", result.confirmed.isEmpty())
        assertEquals(setOf(wrong, gone), result.missing.toSet())
        assertEquals(setOf(wrong.mediaStoreId), result.presentAtWrongSize)
        assertEquals(listOf(unknown), result.unconfirmed)

        // No adapter registered for the cloud at all: also "could not ask".
        val withoutAdapter = engine(verifiers = emptySet()).confirmStillInCloud(listOf(wrong))
        assertTrue(withoutAdapter.confirmed.isEmpty())
        assertEquals(listOf(wrong), withoutAdapter.unconfirmed)
    }

    @Test
    fun `a row not yet uploaded is missing, and the cloud is not asked`() = runTest {
        val pending = item("pending.jpg", 1)
        val noId = item("noid.jpg", 2)
        givenRows(
            rowFor(pending, remoteId = null, state = BackupState.PENDING),
            rowFor(noId, remoteId = "", state = BackupState.UPLOADED)
        )

        val result = engine().confirmStillInCloud(listOf(pending, noId))

        assertEquals(setOf(pending, noId), result.missing.toSet())
        assertTrue(result.confirmed.isEmpty())
        assertTrue("nothing to ask about", asked.isEmpty())
    }
}
