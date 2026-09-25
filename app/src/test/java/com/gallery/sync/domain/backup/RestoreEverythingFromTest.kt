package com.gallery.sync.domain.backup

import com.gallery.sync.data.local.dao.BackupEntryDao
import com.gallery.sync.data.local.entity.BackupEntryEntity
import com.gallery.sync.data.local.entity.BackupState
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/** The sign-out question's "restore them to the phone first" (Ian, 25 Sept 2026). */
class RestoreEverythingFromTest {

    private val entryDao: BackupEntryDao = mock()
    private val engine: BackupEngine = mock()
    private val restorer: RestoreProxyInPlace = mock()
    private val downloader: DownloadMissingFile = mock()

    private val subject = RestoreEverythingFrom(entryDao, engine, restorer, downloader)

    private fun row(id: String, location: BackupLocation, size: Long = 1_000) = BackupEntryEntity(
        id = id,
        mediaStoreId = 1,
        contentUri = "content://x",
        displayName = "$id.jpg",
        album = "a",
        sizeBytes = size,
        dateModifiedEpochSeconds = 1,
        mimeType = "image/jpeg",
        isVideo = false,
        state = BackupState.UPLOADED,
        remoteItemId = "r-$id",
        location = location
    )

    @Test
    fun `plan holds only the signing-out cloud's files, each in one list`() = runTest {
        val shrunk = row("s", BackupLocation.DROPBOX, 4_000)
        val gone = row("g", BackupLocation.DROPBOX, 2_000)
        whenever(entryDao.restorableProxies()).thenReturn(
            listOf(shrunk, row("otherShrunk", BackupLocation.GOOGLE_DRIVE))
        )
        // The shrunk file is also "not in its folder by signature"; it must not be counted twice.
        whenever(engine.filesNotOnThePhone()).thenReturn(
            listOf(shrunk, gone, row("otherGone", BackupLocation.BACKBLAZE_B2))
        )

        val plan = subject.plan(BackupLocation.DROPBOX)

        assertEquals(listOf(shrunk), plan.proxies)
        assertEquals(listOf(gone), plan.missing)
        assertEquals(2, plan.total)
        assertEquals(6_000L, plan.bytes)
    }

    @Test
    fun `nothing to bring back is an empty plan`() = runTest {
        whenever(entryDao.restorableProxies()).thenReturn(emptyList())
        whenever(engine.filesNotOnThePhone()).thenReturn(listOf(row("x", BackupLocation.ONEDRIVE)))

        assertEquals(0, subject.plan(BackupLocation.DROPBOX).total)
    }

    @Test
    fun `run restores shrunk files in place, downloads the gone ones and counts failures`() = runTest {
        val shrunk = row("s", BackupLocation.DROPBOX)
        val gone = row("g", BackupLocation.DROPBOX)
        val broken = row("b", BackupLocation.DROPBOX)
        whenever(restorer.restore(any(), any())).thenReturn(RestoreInPlaceResult.Restored(1_000))
        whenever(downloader.download(eq(gone), any())).thenReturn(RestoreInPlaceResult.Restored(1_000))
        whenever(downloader.download(eq(broken), any())).thenReturn(RestoreInPlaceResult.GoneFromCloud)

        val seen = mutableListOf<Int>()
        val outcome = subject.run(RestoreEverythingFrom.Plan(listOf(shrunk), listOf(gone, broken))) { finished, _, _ ->
            seen += finished
        }

        assertEquals(RestoreEverythingFrom.Outcome(restored = 1, downloaded = 1, failed = 1), outcome)
        assertEquals(listOf(0, 1, 2, 3), seen)
    }

    @Test
    fun `an empty plan runs nothing`() = runTest {
        val outcome = subject.run(RestoreEverythingFrom.Plan(emptyList(), emptyList())) { _, _, _ -> }

        assertTrue(outcome.failed == 0 && outcome.restored == 0 && outcome.downloaded == 0)
        verify(restorer, never()).restore(any(), any())
        verify(downloader, never()).download(any(), any())
    }
}
