package com.gallery.sync.domain.backup

import android.net.Uri
import com.gallery.sync.data.local.dao.BackupEntryDao
import com.gallery.sync.data.local.dao.UnsentDepartureDao
import com.gallery.sync.data.local.entity.BackupEntryEntity
import com.gallery.sync.data.local.entity.BackupState
import com.gallery.sync.data.local.entity.CloudCopyDecision
import com.gallery.sync.data.local.entity.UnsentDepartureEntity
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * What the "files deleted from phone" window has to ask, and how its answers are settled.
 *
 * Ian, 19 Sept 2026: the window covers **all** deleted files, the ones this app never backed up as well
 * as the ones it did, and asks a different question of each half. A photo edited in place is not a
 * photo deleted (*"we can't have every edited file look like a deletion"*). These run the real class
 * against fakes for the ledger, the scan, the trash and the drive, because a rule is only worth having
 * if the class that builds the list and the class that deletes both obey it.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SyncDeletionsToCloudTest {

    private val dao: BackupEntryDao = mock()
    private val unsentDao: UnsentDepartureDao = mock()
    private val scanner: MediaScanner = mock()
    private val settings: BackupSettings = mock()
    private val drive: OneDriveDeletionRepository = mock()
    private val engine: BackupEngine = mock()

    private val sync = SyncDeletionsToCloud(dao, unsentDao, scanner, settings, drive, engine, UnconfinedTestDispatcher())

    private fun ledgerRow(album: String, name: String, size: Long = 1_000L, since: Long = 1L) = BackupEntryEntity(
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
        localMissingSinceEpochMillis = since
    )

    private fun unsentRow(album: String, name: String, id: Long, size: Long = 1_000L, since: Long = 1L) =
        UnsentDepartureEntity(
            id = "$album/$name|$size|1",
            mediaStoreId = id,
            contentUri = "content://media/external/images/media/$id",
            displayName = name,
            album = album,
            sizeBytes = size,
            dateModifiedEpochSeconds = 1L,
            mimeType = "image/jpeg",
            isVideo = false,
            goneSinceEpochMillis = since
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

    private suspend fun givenAskAndScan(
        missing: List<BackupEntryEntity> = emptyList(),
        phone: List<LocalMediaItem> = listOf(onPhone("Camera", "unrelated.jpg", 5L)),
        unsent: List<UnsentDepartureEntity> = emptyList(),
        trashed: Set<Long> = emptySet()
    ) {
        whenever(settings.current()).thenReturn(
            BackupPreferences(cloudDeletionPolicy = CloudDeletionPolicy.ASK)
        )
        whenever(dao.cloudDeletionCandidates(any())).thenReturn(missing)
        whenever(unsentDao.all()).thenReturn(unsent)
        // A library big enough that a handful of missing files is an ordinary tidy-up.
        whenever(dao.totalCount()).thenReturn(2_000)
        whenever(scanner.access()).thenReturn(MediaAccess.FULL)
        whenever(scanner.scanEverything()).thenReturn(phone)
        whenever(scanner.trashedIds()).thenReturn(trashed)
    }

    // ── Files this app sent ─────────────────────────────────────────────────

    @Test
    fun `an edited photo is not offered but a really deleted one is`() = runTest {
        val edited = ledgerRow("Camera", "IMG_1234.jpg", size = 3_000L)
        val deleted = ledgerRow("Camera", "IMG_5678.jpg")
        // The edit saved over the original: same name, same folder, a different size.
        givenAskAndScan(listOf(edited, deleted), listOf(onPhone("Camera", "IMG_1234.jpg", size = 2_400L)))

        assertEquals(listOf(DeletedFile.of(deleted)), sync.offer().inCloud)
    }

    @Test
    fun `the folder is compared without regard to case`() = runTest {
        val edited = ledgerRow("Camera", "IMG_1234.jpg")
        givenAskAndScan(listOf(edited), listOf(onPhone("camera", "IMG_1234.jpg", size = 500L)))

        assertTrue(sync.offer().isEmpty)
    }

    @Test
    fun `the same name in a different folder does not hide a deletion`() = runTest {
        val deleted = ledgerRow("Camera", "IMG_1234.jpg")
        givenAskAndScan(listOf(deleted), listOf(onPhone("WhatsApp", "IMG_1234.jpg", size = 500L)))

        assertEquals(listOf(DeletedFile.of(deleted)), sync.offer().inCloud)
    }

    @Test
    fun `a file fetched back with the restored suffix counts as still here`() = runTest {
        val gone = ledgerRow("Camera", "IMG_1234.jpg")
        givenAskAndScan(listOf(gone), listOf(onPhone("Camera", "IMG_1234_restored.jpg", size = 1_000L)))

        assertTrue(sync.offer().isEmpty)
    }

    @Test
    fun `nothing is offered when the scan cannot be trusted`() = runTest {
        val gone = ledgerRow("Camera", "IMG_5678.jpg")
        givenAskAndScan(listOf(gone), emptyList())

        assertTrue("an empty scan is no evidence of anything", sync.offer().isEmpty)

        whenever(scanner.access()).thenReturn(MediaAccess.PARTIAL)
        whenever(scanner.scanEverything()).thenReturn(listOf(onPhone("Camera", "OTHER.jpg", 1L)))
        assertTrue("partial media access cannot say what is missing", sync.offer().isEmpty)
    }

    @Test
    fun `nothing is offered under the leave policy whatever the phone holds`() = runTest {
        whenever(settings.current()).thenReturn(BackupPreferences(cloudDeletionPolicy = CloudDeletionPolicy.LEAVE))

        assertTrue(sync.offer().isEmpty)
        verify(scanner, never()).scanEverything()
        verify(engine, never()).cloudCopiesOf(any())
    }

    @Test
    fun `nothing is offered when a mass of files went missing at once`() = runTest {
        val many = (1..30).map { ledgerRow("Camera", "IMG_$it.jpg") }
        givenAskAndScan(many)
        whenever(dao.totalCount()).thenReturn(50)

        assertTrue("30 of 50 is more than half, so it reads as a bad scan", sync.offer().isEmpty)

        whenever(dao.totalCount()).thenReturn(2_000)
        assertEquals("30 of 2,000 is an ordinary tidy-up", 30, sync.offer().inCloud.size)
    }

    // ── Files this app never sent ───────────────────────────────────────────

    @Test
    fun `a deleted file with a copy in OneDrive goes to the first window, found not sent`() = runTest {
        val gone = unsentRow("Temp 8", "a.jpg", id = 11L)
        givenAskAndScan(unsent = listOf(gone), trashed = setOf(11L))
        whenever(engine.cloudCopiesOf(any())).thenReturn(CloudLookup(mapOf(gone.id to "found-a"), emptySet()))

        val offer = sync.offer()

        assertEquals(listOf(DeletedFile.of(gone, remoteItemId = "found-a")), offer.inCloud)
        assertTrue(offer.notInCloud.isEmpty())
        assertTrue(offer.complete)
    }

    @Test
    fun `a deleted file with no copy anywhere goes to the second window`() = runTest {
        val gone = unsentRow("Temp 8", "a.jpg", id = 11L)
        givenAskAndScan(unsent = listOf(gone), trashed = setOf(11L))
        whenever(engine.cloudCopiesOf(any())).thenReturn(CloudLookup(emptyMap(), emptySet()))

        val offer = sync.offer()

        assertTrue(offer.inCloud.isEmpty())
        assertEquals(listOf(DeletedFile.of(gone)), offer.notInCloud)
    }

    @Test
    fun `a file OneDrive could not be asked about is offered nowhere and the offer says so`() = runTest {
        val placed = unsentRow("Temp 8", "a.jpg", id = 11L)
        val unplaced = unsentRow("Temp 8", "b.jpg", id = 12L)
        givenAskAndScan(unsent = listOf(placed, unplaced), trashed = setOf(11L, 12L))
        whenever(engine.cloudCopiesOf(any())).thenReturn(CloudLookup(emptyMap(), setOf(unplaced.id)))

        val offer = sync.offer()

        assertEquals("failing to ask is not evidence of absence", listOf(DeletedFile.of(placed)), offer.notInCloud)
        assertTrue(offer.inCloud.isEmpty())
        assertFalse(offer.complete)
    }

    @Test
    fun `a file that is not in the phone's trash cannot be saved so it is not asked about`() = runTest {
        val outright = unsentRow("Temp 8", "a.jpg", id = 11L)
        val inTrash = unsentRow("Temp 8", "b.jpg", id = 12L)
        givenAskAndScan(unsent = listOf(outright, inTrash), trashed = setOf(12L))
        whenever(engine.cloudCopiesOf(any())).thenReturn(CloudLookup(emptyMap(), emptySet()))

        assertEquals(listOf(DeletedFile.of(inTrash)), sync.offer().notInCloud)
    }

    @Test
    fun `an unsent photo edited in place is not a deletion either`() = runTest {
        val edited = unsentRow("Temp 8", "a.jpg", id = 11L)
        givenAskAndScan(
            phone = listOf(onPhone("Temp 8", "a.jpg", size = 9L)),
            unsent = listOf(edited),
            trashed = setOf(11L)
        )

        assertTrue(sync.offer().isEmpty)
        verify(engine, never()).cloudCopiesOf(any())
    }

    @Test
    fun `nothing newer than what was shown means OneDrive is not even asked`() = runTest {
        val old = unsentRow("Temp 8", "a.jpg", id = 11L, since = 100L)
        givenAskAndScan(unsent = listOf(old), trashed = setOf(11L))

        assertTrue(sync.offer(newerThan = 100L).isEmpty)

        verify(engine, never()).cloudCopiesOf(any())
    }

    @Test
    fun `a newer departure lists the older undecided ones with it`() = runTest {
        val old = unsentRow("Temp 8", "a.jpg", id = 11L, since = 100L)
        val new = unsentRow("Temp 8", "b.jpg", id = 12L, since = 300L)
        givenAskAndScan(unsent = listOf(old, new), trashed = setOf(11L, 12L))
        whenever(engine.cloudCopiesOf(any())).thenReturn(CloudLookup(emptyMap(), emptySet()))

        assertEquals(2, sync.offer(newerThan = 200L).notInCloud.size)
    }

    @Test
    fun `a record of a file in neither the phone nor its trash is dropped once it is old`() = runTest {
        val stale = unsentRow("Temp 8", "a.jpg", id = 11L, since = 1L)
        val other = unsentRow("Temp 8", "b.jpg", id = 12L, since = System.currentTimeMillis())
        givenAskAndScan(unsent = listOf(stale, other), trashed = setOf(99L))
        whenever(engine.cloudCopiesOf(any())).thenReturn(CloudLookup(emptyMap(), emptySet()))

        sync.offer()

        verify(unsentDao).forget(listOf(stale.id))
    }

    @Test
    fun `an empty trash answer is not read as an empty trash`() = runTest {
        val old = unsentRow("Temp 8", "a.jpg", id = 11L, since = 1L)
        givenAskAndScan(unsent = listOf(old), trashed = emptySet())

        sync.offer()

        verify(unsentDao, never()).forget(any())
    }

    // ── Settling ────────────────────────────────────────────────────────────

    @Test
    fun `keeping files records the decision in chunks and removes nothing`() = runTest {
        val many = (1..1_200).map { DeletedFile.of(ledgerRow("Camera", "IMG_$it.jpg")) }

        sync.keep(many)

        val ids = org.mockito.kotlin.argumentCaptor<List<String>>()
        verify(dao, times(3)).setCloudDecision(ids.capture(), eq(CloudCopyDecision.KEPT))
        assertEquals("every id, none twice", 1_200, ids.allValues.flatten().toSet().size)
        assertTrue("no chunk over SQLite's limit", ids.allValues.all { it.size <= 500 })
        verify(drive, never()).moveToRecycleBin(any())
    }

    @Test
    fun `leaving a never-sent file in the trash forgets its record and touches nothing else`() = runTest {
        val gone = DeletedFile.of(unsentRow("Temp 8", "a.jpg", id = 11L))

        sync.keep(listOf(gone))

        verify(unsentDao).forget(listOf(gone.id))
        verify(dao, never()).setCloudDecision(any(), any())
        verify(drive, never()).moveToRecycleBin(any())
    }

    /** The list may be stale by the time someone approves it; the last check must catch an edit too. */
    @Test
    fun `an approved file that was edited since the list was drawn is not deleted`() = runTest {
        val edited = ledgerRow("Camera", "IMG_1234.jpg", size = 3_000L)
        val deleted = ledgerRow("Camera", "IMG_5678.jpg")
        givenAskAndScan(phone = listOf(onPhone("Camera", "IMG_1234.jpg", size = 2_400L)))
        whenever(drive.moveToRecycleBin(any())).thenReturn(DataResult.Success(Unit))

        val outcome = sync.delete(listOf(DeletedFile.of(edited), DeletedFile.of(deleted)))

        assertEquals(1, outcome.deleted)
        assertEquals(1, outcome.cameBack)
        verify(drive).moveToRecycleBin("remote-IMG_5678.jpg")
        verify(drive, never()).moveToRecycleBin("remote-IMG_1234.jpg")
        verify(dao).forget(deleted.id)
    }

    @Test
    fun `a copy found in OneDrive rather than sent is deleted by the id the lookup found`() = runTest {
        val gone = unsentRow("Temp 8", "a.jpg", id = 11L)
        givenAskAndScan()
        whenever(drive.moveToRecycleBin(any())).thenReturn(DataResult.Success(Unit))

        val outcome = sync.delete(listOf(DeletedFile.of(gone, remoteItemId = "found-a")))

        assertEquals(1, outcome.deleted)
        verify(drive).moveToRecycleBin("found-a")
        verify(unsentDao).forget(listOf(gone.id))
        verify(dao, never()).forget(any())
    }

    @Test
    fun `a file with no known OneDrive id is never sent to the drive`() = runTest {
        val gone = unsentRow("Temp 8", "a.jpg", id = 11L)
        givenAskAndScan()

        val outcome = sync.delete(listOf(DeletedFile.of(gone, remoteItemId = null)))

        assertEquals(1, outcome.failed)
        verify(drive, never()).moveToRecycleBin(any())
        verify(unsentDao, never()).forget(any())
    }

    @Test
    fun `nothing is deleted under the leave policy even when handed an approved list`() = runTest {
        whenever(settings.current()).thenReturn(BackupPreferences(cloudDeletionPolicy = CloudDeletionPolicy.LEAVE))

        val outcome = sync.delete(listOf(DeletedFile.of(ledgerRow("Camera", "a.jpg"))))

        assertEquals(0, outcome.deleted)
        verify(drive, never()).moveToRecycleBin(any())
    }

    @Test
    fun `a removal makes the window forget what it remembered about that album`() = runTest {
        val gone = ledgerRow("Camera", "IMG_5678.jpg")
        givenAskAndScan()
        whenever(drive.moveToRecycleBin(any())).thenReturn(DataResult.Success(Unit))

        sync.delete(listOf(DeletedFile.of(gone)))

        verify(engine).forgetCachedRemoteIndex("Camera")
    }

    @Test
    fun `a removal that failed changed nothing so nothing is forgotten`() = runTest {
        val gone = ledgerRow("Camera", "IMG_5678.jpg")
        givenAskAndScan()
        whenever(drive.moveToRecycleBin(any())).thenReturn(DataResult.Failure(com.gallery.sync.domain.model.RemoteError.Network))

        sync.delete(listOf(DeletedFile.of(gone)))

        verify(engine, never()).forgetCachedRemoteIndex(any())
    }
}
