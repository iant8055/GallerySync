package com.gallery.sync.domain.backup

import android.content.Context
import android.net.Uri
import com.gallery.sync.data.local.dao.AlbumPreferenceDao
import com.gallery.sync.data.local.dao.BackupEntryDao
import com.gallery.sync.data.local.dao.UnsentDepartureDao
import com.gallery.sync.data.local.dao.UploadedKey
import com.gallery.sync.data.local.entity.BackupEntryEntity
import com.gallery.sync.data.local.entity.BackupState
import com.gallery.sync.data.local.entity.CloudCopyDecision
import com.gallery.sync.data.local.entity.UnsentDepartureEntity
import com.gallery.sync.data.local.media.LocalMediaItem
import com.gallery.sync.data.local.media.MediaAccess
import com.gallery.sync.data.local.media.MediaScanner
import com.gallery.sync.data.local.settings.BackupPreferences
import com.gallery.sync.data.local.settings.BackupSettings
import com.gallery.sync.data.remote.onedrive.UploadSource
import com.gallery.sync.domain.model.DataResult
import com.gallery.sync.domain.model.FolderPage
import com.gallery.sync.domain.model.RemoteError
import com.gallery.sync.domain.model.RemoteMediaNode
import com.gallery.sync.domain.model.UploadedItem
import com.gallery.sync.domain.repository.OneDriveRepository
import com.gallery.sync.domain.repository.OneDriveUploadRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * The engine's half of the deleted-files window: keeping a record of files that leave the phone
 * unsent, looking for their copies in OneDrive, and backing them up from the trash. Ian, 19 Sept 2026.
 *
 * The rules under test: a file this app never sent is written down **before** its ledger row is
 * forgotten; a file that comes back is dropped from the list; failing to ask OneDrive is never read as
 * "no copy"; a backup adds a copy and removes nothing; and a run stops on a failure that will repeat
 * for every file rather than repeating it.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DeletedFilesEngineTest {

    private val scanner: MediaScanner = mock()
    private val entryDao: BackupEntryDao = mock()
    private val albumDao: AlbumPreferenceDao = mock()
    private val unsentDao: UnsentDepartureDao = mock()
    private val settings: BackupSettings = mock()
    private val repository: OneDriveRepository = mock()
    private val uploadRepository: OneDriveUploadRepository = mock()
    private val identity: AlbumIdentityReconciler = mock()

    private val engine = BackupEngine(
        scanner = scanner,
        entryDao = entryDao,
        albumDao = albumDao,
        unsentDao = unsentDao,
        settings = settings,
        repository = repository,
        uploadRepository = uploadRepository,
        proxyMarker = mock(),
        albumIdentity = identity,
        context = mock<Context>(),
        dispatcher = UnconfinedTestDispatcher()
    )

    @Before
    fun setUp() = runTest {
        whenever(settings.current()).thenReturn(BackupPreferences())
        engine.sourceFor = { file -> FakeSource(file) }
    }

    private class FakeSource(file: DeletedFile) : UploadSource {
        override val displayName = file.displayName
        override val sizeBytes = file.sizeBytes
        override fun open(): UploadSource.Reader = throw UnsupportedOperationException("not read in a unit test")
    }

    private fun deleted(name: String, size: Long = 1_000L, album: String = "Temp 8") = DeletedFile(
        id = "$album/$name|$size|1",
        origin = DepartureOrigin.NEVER_SENT,
        displayName = name,
        album = album,
        sizeBytes = size,
        mediaStoreId = 11L,
        contentUri = "content://media/external/images/media/11",
        mimeType = "image/jpeg",
        isVideo = false,
        dateModifiedEpochSeconds = 1L,
        departedAtEpochMillis = 500L,
        remoteItemId = null
    )

    private fun remoteFile(name: String, size: Long?, id: String = "remote-$name") = RemoteMediaNode.File(
        id = id, name = name, modifiedAtUtc = 0L, mimeType = "image/jpeg", sizeBytes = size,
        widthPx = null, heightPx = null, eTag = null
    )

    private suspend fun givenFolder(album: String, vararg files: RemoteMediaNode.File) {
        whenever(repository.listFolderByPath("${RemoteRootsForTest.ROOT}/$album"))
            .thenReturn(DataResult.Success(FolderPage(files.toList(), null)))
    }

    private object RemoteRootsForTest {
        const val ROOT = RemoteRoots.DEFAULT_DESTINATION
    }

    // ── Looking in OneDrive ─────────────────────────────────────────────────

    @Test
    fun `a copy is found by name and size in the album's own folder`() = runTest {
        givenFolder("Temp 8", remoteFile("a.jpg", 1_000L, id = "found-a"))
        val file = deleted("a.jpg")

        val lookup = engine.cloudCopiesOf(listOf(file))

        assertEquals(mapOf(file.id to "found-a"), lookup.found)
        assertTrue(lookup.unknown.isEmpty())
    }

    @Test
    fun `the same name at another size is not a copy, and not unknown either`() = runTest {
        givenFolder("Temp 8", remoteFile("a.jpg", 9_999L))
        val file = deleted("a.jpg")

        val lookup = engine.cloudCopiesOf(listOf(file))

        assertTrue(lookup.found.isEmpty())
        assertTrue("it is answered: there is no copy of this file", lookup.unknown.isEmpty())
    }

    @Test
    fun `a name that is not there is simply not found`() = runTest {
        givenFolder("Temp 8")

        val lookup = engine.cloudCopiesOf(listOf(deleted("a.jpg")))

        assertTrue(lookup.found.isEmpty())
        assertTrue(lookup.unknown.isEmpty())
    }

    @Test
    fun `an album that cannot be listed leaves its files unknown rather than absent`() = runTest {
        whenever(repository.listFolderByPath(any())).thenReturn(DataResult.Failure(RemoteError.Network))
        val file = deleted("a.jpg")

        val lookup = engine.cloudCopiesOf(listOf(file))

        assertTrue(lookup.found.isEmpty())
        assertEquals(setOf(file.id), lookup.unknown)
    }

    @Test
    fun `a copy that reports no size is unknown, never a match`() = runTest {
        givenFolder("Temp 8", remoteFile("a.jpg", null))
        val file = deleted("a.jpg")

        val lookup = engine.cloudCopiesOf(listOf(file))

        assertTrue(lookup.found.isEmpty())
        assertEquals(setOf(file.id), lookup.unknown)
    }

    @Test
    fun `each album is listed once however many files it has`() = runTest {
        givenFolder("Temp 8", remoteFile("a.jpg", 1_000L))

        engine.cloudCopiesOf(listOf(deleted("a.jpg"), deleted("b.jpg"), deleted("c.jpg")))

        verify(repository, org.mockito.kotlin.times(1)).listFolderByPath(any())
    }

    // ── Backing up from the trash ───────────────────────────────────────────

    private fun uploaded(name: String, size: Long) =
        DataResult.Success(UploadedItem(id = "new-$name", name = name, sizeBytes = size, eTag = null))

    @Test
    fun `a file is sent and then recorded as uploaded, gone from the phone, and settled`() = runTest {
        givenFolder("Temp 8")
        val file = deleted("a.jpg")
        whenever(uploadRepository.upload(any<UploadSource>(), any(), any(), anyOrNull(), any()))
            .thenReturn(uploaded("a.jpg", 1_000L))

        val outcome = engine.backUpFromTrash(listOf(file))

        assertEquals(1, outcome.uploaded)
        val rows = argumentCaptor<List<BackupEntryEntity>>()
        verify(entryDao).insertIfNew(rows.capture())
        val row = rows.firstValue.single()
        assertEquals(BackupState.UPLOADED, row.state)
        assertEquals("new-a.jpg", row.remoteItemId)
        assertEquals("it is on the drive and not on the phone", 500L, row.localMissingSinceEpochMillis)
        assertEquals(
            "so the window does not straight away offer to remove the copy just made",
            CloudCopyDecision.KEPT, row.cloudDecision
        )
        verify(unsentDao).forget(listOf(file.id))
    }

    @Test
    fun `a file already in OneDrive is recorded without being sent again`() = runTest {
        givenFolder("Temp 8", remoteFile("a.jpg", 1_000L, id = "there"))
        val file = deleted("a.jpg")

        val outcome = engine.backUpFromTrash(listOf(file))

        assertEquals(1, outcome.alreadyThere)
        assertEquals(0, outcome.uploaded)
        verify(uploadRepository, never()).upload(any<UploadSource>(), any(), any(), anyOrNull(), any())
        verify(unsentDao).forget(listOf(file.id))
    }

    @Test
    fun `a file whose bytes are gone is dropped and reported as unreadable`() = runTest {
        givenFolder("Temp 8")
        val file = deleted("a.jpg")
        whenever(uploadRepository.upload(any<UploadSource>(), any(), any(), anyOrNull(), any()))
            .thenReturn(DataResult.Failure(RemoteError.LocalFileMissing))

        val outcome = engine.backUpFromTrash(listOf(file))

        assertEquals(1, outcome.unreadable)
        verify(unsentDao).forget(listOf(file.id))
        verify(entryDao, never()).insertIfNew(any())
    }

    @Test
    fun `a size mismatch is a failure and leaves the file undecided`() = runTest {
        givenFolder("Temp 8")
        val file = deleted("a.jpg")
        whenever(uploadRepository.upload(any<UploadSource>(), any(), any(), anyOrNull(), any()))
            .thenReturn(uploaded("a.jpg", 400L))

        val outcome = engine.backUpFromTrash(listOf(file))

        assertEquals(1, outcome.failed)
        verify(unsentDao, never()).forget(any())
        verify(entryDao, never()).insertIfNew(any())
    }

    @Test
    fun `an album that cannot be listed defers its files rather than sending them blind`() = runTest {
        whenever(repository.listFolderByPath(any())).thenReturn(DataResult.Failure(RemoteError.Network))

        val outcome = engine.backUpFromTrash(listOf(deleted("a.jpg"), deleted("b.jpg")))

        assertEquals(2, outcome.failed)
        verify(uploadRepository, never()).upload(any<UploadSource>(), any(), any(), anyOrNull(), any())
        verify(unsentDao, never()).forget(any())
    }

    @Test
    fun `a failure that would repeat for every file stops the run and leaves the rest undecided`() = runTest {
        givenFolder("Temp 8")
        whenever(uploadRepository.upload(any<UploadSource>(), any(), any(), anyOrNull(), any()))
            .thenReturn(DataResult.Failure(RemoteError.InsufficientStorage))
        val files = listOf(deleted("a.jpg"), deleted("b.jpg"), deleted("c.jpg"))

        val outcome = engine.backUpFromTrash(files)

        assertEquals(StopReason.DRIVE_FULL, outcome.stoppedBecause)
        assertEquals("all three are still to be decided", 3, outcome.failed)
        verify(uploadRepository, org.mockito.kotlin.times(1)).upload(any<UploadSource>(), any(), any(), anyOrNull(), any())
        verify(unsentDao, never()).forget(any())
    }

    @Test
    fun `progress is reported file by file`() = runTest {
        givenFolder("Temp 8")
        whenever(uploadRepository.upload(any<UploadSource>(), any(), any(), anyOrNull(), any()))
            .thenReturn(uploaded("a.jpg", 1_000L))
        val seen = mutableListOf<Triple<Int, Int, String>>()

        engine.backUpFromTrash(listOf(deleted("a.jpg"))) { done, total, name -> seen += Triple(done, total, name) }

        assertEquals(Triple(0, 1, "a.jpg"), seen.first())
        assertEquals(1, seen.last().first)
    }

    // ── Keeping a record of what leaves unsent ──────────────────────────────

    private fun scanned(name: String, id: Long, size: Long = 1_000L) = LocalMediaItem(
        mediaStoreId = id,
        contentUri = mock<Uri>(),
        displayName = name,
        album = "Temp 8",
        sizeBytes = size,
        dateModifiedEpochSeconds = 1L,
        mimeType = "image/jpeg",
        isVideo = false,
        relativePath = "DCIM/Temp 8/"
    )

    private fun pendingRow(name: String, id: Long) = BackupEntryEntity(
        id = "Temp 8/$name|1000|1",
        mediaStoreId = id,
        contentUri = "content://media/external/images/media/$id",
        displayName = name,
        album = "Temp 8",
        sizeBytes = 1_000L,
        dateModifiedEpochSeconds = 1L,
        mimeType = "image/jpeg",
        isVideo = false,
        state = BackupState.PENDING
    )

    private suspend fun givenScan(present: List<LocalMediaItem>, pending: List<BackupEntryEntity> = emptyList(), unsent: List<UnsentDepartureEntity> = emptyList()) {
        whenever(scanner.access()).thenReturn(MediaAccess.FULL)
        whenever(scanner.scanAll()).thenReturn(present)
        whenever(scanner.scanEverything()).thenReturn(present)
        whenever(entryDao.proxiedMediaStoreIds()).thenReturn(emptyList())
        whenever(entryDao.uploadedKeys()).thenReturn(emptyList())
        whenever(entryDao.pendingKeys()).thenReturn(
            pending.map { UploadedKey(it.id, it.displayName, it.sizeBytes, it.mediaStoreId, false) }
        )
        whenever(entryDao.entriesByIds(any())).thenReturn(pending)
        whenever(entryDao.forgetPending(any(), any())).thenReturn(pending.size)
        whenever(unsentDao.all()).thenReturn(unsent)
        whenever(entryDao.countRetrievableOutsideDevice(any(), any())).thenReturn(0)
        whenever(entryDao.forgetAlbumsNotOnDevice(any(), any())).thenReturn(0)
    }

    @Test
    fun `a pending file that has left the phone is written down before its row is forgotten`() = runTest {
        val gone = pendingRow("gone.jpg", id = 21L)
        givenScan(present = listOf(scanned("here.jpg", 22L)), pending = listOf(gone))

        engine.refreshLedger()

        val order = inOrder(unsentDao, entryDao)
        val kept = argumentCaptor<List<UnsentDepartureEntity>>()
        order.verify(unsentDao).insertIfNew(kept.capture())
        order.verify(entryDao).forgetPending(eq(listOf(gone.id)), any())
        val record = kept.firstValue.single()
        assertEquals(gone.id, record.id)
        assertEquals(21L, record.mediaStoreId)
        assertEquals("gone.jpg", record.displayName)
        assertEquals("Temp 8", record.album)
    }

    @Test
    fun `a pending file still on the phone is not recorded as gone`() = runTest {
        val here = pendingRow("here.jpg", id = 22L)
        givenScan(present = listOf(scanned("here.jpg", 22L)), pending = listOf(here))

        engine.refreshLedger()

        verify(unsentDao, never()).insertIfNew(any())
        verify(entryDao, never()).forgetPending(any(), any())
    }

    @Test
    fun `a departed file that is back on the phone is dropped from the list`() = runTest {
        val record = UnsentDepartureEntity(
            id = "Temp 8/back.jpg|1000|1", mediaStoreId = 31L, contentUri = "content://x/31",
            displayName = "back.jpg", album = "Temp 8", sizeBytes = 1_000L,
            dateModifiedEpochSeconds = 1L, mimeType = "image/jpeg", isVideo = false, goneSinceEpochMillis = 5L
        )
        val stillGone = record.copy(id = "Temp 8/other.jpg|1000|1", mediaStoreId = 32L, displayName = "other.jpg")
        givenScan(present = listOf(scanned("back.jpg", 31L)), unsent = listOf(record, stillGone))

        engine.refreshLedger()

        verify(unsentDao).forget(listOf(record.id))
    }

    @Test
    fun `nothing is recorded or forgotten when the scan cannot be trusted`() = runTest {
        givenScan(present = emptyList(), pending = listOf(pendingRow("gone.jpg", 21L)))

        engine.refreshLedger()

        verify(unsentDao, never()).insertIfNew(any())
        verify(entryDao, never()).forgetPending(any(), any())
    }
}
