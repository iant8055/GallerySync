package com.gallery.sync.domain.backup

import android.content.Context
import android.net.Uri
import com.gallery.sync.data.local.dao.AlbumPreferenceDao
import com.gallery.sync.data.local.dao.BackupEntryDao
import com.gallery.sync.data.local.dao.PinnedKey
import com.gallery.sync.data.local.entity.AlbumMode
import com.gallery.sync.data.local.entity.BackupEntryEntity
import com.gallery.sync.data.local.entity.BackupState
import com.gallery.sync.data.local.entity.CloudCopyDecision
import com.gallery.sync.data.local.entity.backupKeyOf
import com.gallery.sync.data.local.media.LocalMediaItem
import com.gallery.sync.data.local.media.MediaAccess
import com.gallery.sync.data.local.media.MediaScanner
import com.gallery.sync.data.local.settings.BackupSettings
import com.gallery.sync.domain.repository.GooglePhotosUploadRepository
import com.gallery.sync.domain.repository.OneDriveRepository
import com.gallery.sync.domain.repository.OneDriveUploadRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * Swiping a file out of Archive: what is remembered, and that an opted-out file can never be offered
 * for removal. Ian, 19 Sept 2026: *"we need to allow a user to select certain files to opt out of
 * archiving in a folder - the database to remember that choice."*
 *
 * The choice is the file's pin (see [FilePin]) and these run the real engine against fakes for the
 * ledger, the albums and the scan, because the safety here is not in any one function: it is that the
 * only list anything removes from is [BackupEngine.filesInArchiveAlbums], and that list never holds
 * a pinned file.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ArchiveOptOutTest {

    private val scanner: MediaScanner = mock()
    private val entryDao: BackupEntryDao = mock()
    private val albumDao: AlbumPreferenceDao = mock()
    private val identity: AlbumIdentityReconciler = mock()

    private val engine = BackupEngine(
        scanner = scanner,
        entryDao = entryDao,
        albumDao = albumDao,
        unsentDao = mock(),
        settings = mock<BackupSettings>(),
        repository = mock<OneDriveRepository>(),
        uploadRepository = mock<OneDriveUploadRepository>(),
        googlePhotosUploadRepository = mock<GooglePhotosUploadRepository>(),
        proxyMarker = mock(),
        albumIdentity = identity,
        context = mock<Context>(),
        dispatcher = UnconfinedTestDispatcher()
    )

    private fun file(album: String, name: String, mediaStoreId: Long, size: Long = 1_000L, modified: Long = 5L) =
        LocalMediaItem(
            mediaStoreId = mediaStoreId,
            contentUri = mock<Uri>(),
            displayName = name,
            album = album,
            sizeBytes = size,
            dateModifiedEpochSeconds = modified,
            mimeType = "image/jpeg",
            isVideo = false,
            relativePath = "DCIM/$album/"
        )

    private fun keyOf(item: LocalMediaItem) =
        backupKeyOf(item.album, item.displayName, item.sizeBytes, item.dateModifiedEpochSeconds)

    private fun row(item: LocalMediaItem) = BackupEntryEntity(
        id = keyOf(item),
        mediaStoreId = item.mediaStoreId,
        contentUri = "content://media/external/images/media/${item.mediaStoreId}",
        displayName = item.displayName,
        album = item.album,
        sizeBytes = item.sizeBytes,
        dateModifiedEpochSeconds = item.dateModifiedEpochSeconds,
        mimeType = item.mimeType,
        isVideo = false,
        state = BackupState.UPLOADED
    )

    private suspend fun givenArchiveAlbum(vararg files: LocalMediaItem, pinned: List<PinnedKey> = emptyList()) {
        whenever(scanner.access()).thenReturn(MediaAccess.FULL)
        whenever(scanner.scanAll()).thenReturn(files.toList())
        whenever(albumDao.albumsInMode(AlbumMode.ARCHIVE)).thenReturn(listOf("Temp 9"))
        whenever(entryDao.pinnedKeys(any())).thenReturn(pinned)
    }

    @Test
    fun `an opted-out file is never in the list that archiving removes from`() = runTest {
        val kept = file("Temp 9", "a_kept.jpg", 1L)
        val goes = file("Temp 9", "b_goes.jpg", 2L)
        givenArchiveAlbum(kept, goes, pinned = listOf(PinnedKey(keyOf(kept), 1L)))

        val files = engine.archiveFiles()

        assertEquals(listOf(goes), files.toArchive)
        assertEquals(listOf(kept), files.optedOut)
        assertEquals("the removal list is exactly toArchive", files.toArchive, engine.filesInArchiveAlbums())
        assertFalse(kept in engine.filesInArchiveAlbums())
    }

    @Test
    fun `an opted-out file is found by its MediaStore id when its key has drifted`() = runTest {
        val kept = file("Temp 9", "a_kept.jpg", 1L)
        // The row was pinned under an older key, as after a restore rewrites the modification time.
        givenArchiveAlbum(kept, pinned = listOf(PinnedKey("Temp 9/a_kept.jpg|1000|1", 1L)))

        assertEquals(listOf(kept), engine.archiveFiles().optedOut)
        assertTrue(engine.filesInArchiveAlbums().isEmpty())
    }

    @Test
    fun `files outside Archive albums are in neither list`() = runTest {
        givenArchiveAlbum(file("Camera", "elsewhere.jpg", 3L), file("Temp 9", "in.jpg", 4L))

        val files = engine.archiveFiles()

        assertEquals(listOf("in.jpg"), files.toArchive.map { it.displayName })
        assertTrue(files.optedOut.isEmpty())
    }

    @Test
    fun `both lists are in album and name order`() = runTest {
        givenArchiveAlbum(
            file("Temp 9", "c.jpg", 1L), file("Temp 9", "a.jpg", 2L), file("Temp 9", "b.jpg", 3L),
            pinned = listOf(PinnedKey("x", 1L), PinnedKey("y", 3L))
        )

        val files = engine.archiveFiles()

        assertEquals(listOf("a.jpg"), files.toArchive.map { it.displayName })
        assertEquals(listOf("b.jpg", "c.jpg"), files.optedOut.map { it.displayName })
    }

    @Test
    fun `opting a file out pins its ledger row`() = runTest {
        val item = file("Temp 9", "a.jpg", 1L)
        whenever(entryDao.find(keyOf(item))).thenReturn(row(item))

        assertTrue(engine.setArchiveOptOut(item, optedOut = true))

        verify(entryDao).setModeOverride(keyOf(item), FilePin.MODE)
    }

    @Test
    fun `opting out saves nothing and says so when there is no ledger row to write to`() = runTest {
        val item = file("Temp 9", "new.jpg", 1L)
        // No row before or after the refresh: the answer must be false and nothing written.
        whenever(scanner.access()).thenReturn(MediaAccess.NONE)
        whenever(entryDao.find(any())).thenReturn(null)

        assertFalse(engine.setArchiveOptOut(item, optedOut = true))

        verify(entryDao, never()).setModeOverride(any(), any())
    }

    @Test
    fun `putting a file back clears its pin, including a row pinned under an older key`() = runTest {
        val item = file("Temp 9", "a.jpg", 1L)
        val olderKey = "Temp 9/a.jpg|1000|1"
        whenever(entryDao.pinnedKeys(any())).thenReturn(listOf(PinnedKey(olderKey, 1L), PinnedKey("other", 99L)))

        assertTrue(engine.setArchiveOptOut(item, optedOut = false))

        verify(entryDao).setModeOverride(olderKey, null)
        verify(entryDao).setModeOverride(keyOf(item), null)
        verify(entryDao, never()).setModeOverride(eq("other"), any())
    }

    // ── Archive marks what it removes, so the deleted-files window never offers it ───────────

    @Test
    fun `files Archive removed are marked archived by key and by MediaStore id`() = runTest {
        val gone = file("Temp 9", "a.jpg", 1L)
        val also = file("Temp 9", "b.jpg", 2L)

        engine.markRemovedByArchive(listOf(gone, also))

        verify(entryDao).setCloudDecision(listOf(keyOf(gone), keyOf(also)), CloudCopyDecision.ARCHIVED)
        verify(entryDao).setCloudDecisionByMediaStoreId(listOf(1L, 2L), CloudCopyDecision.ARCHIVED)
    }

    @Test
    fun `marking nothing writes nothing`() = runTest {
        engine.markRemovedByArchive(emptyList())

        verify(entryDao, never()).setCloudDecision(any(), any())
        verify(entryDao, never()).setCloudDecisionByMediaStoreId(any(), any())
    }
}
