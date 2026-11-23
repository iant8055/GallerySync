package com.gallery.sync.domain.backup

import com.gallery.sync.data.local.dao.AlbumPreferenceDao
import com.gallery.sync.data.local.dao.BackupEntryDao
import com.gallery.sync.data.local.dao.FolderPreferenceDao
import com.gallery.sync.data.local.dao.ReverifyRow
import com.gallery.sync.data.local.dao.SentElsewhereRow
import com.gallery.sync.data.local.entity.AlbumMode
import com.gallery.sync.data.local.entity.FolderPreferenceEntity
import com.gallery.sync.data.local.settings.BackupPreferences
import com.gallery.sync.data.local.settings.BackupSettings
import com.gallery.sync.domain.repository.CloudVerifiers
import com.gallery.sync.domain.repository.RemoteCheck
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/** Rescan re-verifying: a file a Cloud definitely no longer holds goes back in the queue (Ian, 22 Nov 2026). */
class RequeueMissingFromCloudTest {

    private val od = BackupLocation.ONEDRIVE

    private fun row(id: String, size: Long = 10, remoteId: String? = "r-$id", location: BackupLocation = od) =
        ReverifyRow(id, "$id.jpg", size, location, remoteId, "DCIM/Trip/")

    private fun ref(id: String, size: Long?) = RemoteFileRef(id, size)

    // ── The OneDrive comparison ─────────────────────────────────────────────

    @Test
    fun `a file listed by its upload id or by its name at the right size is present`() {
        val rows = listOf(row("a"), row("b"))
        val index = mapOf("renamed.jpg" to ref("r-a", 10), "b.jpg" to ref("other", 10))
        assertEquals(emptyList<ReverifyRow>(), MissingFromCloud.inOneDrive(rows, index))
    }

    @Test
    fun `a file not listed, or listed at another size, is missing`() {
        val rows = listOf(row("gone"), row("resized"))
        val index = mapOf("resized.jpg" to ref("r-resized", 11))
        assertEquals(listOf("gone", "resized"), MissingFromCloud.inOneDrive(rows, index).map { it.id })
    }

    @Test
    fun `an entry with no size is unknown, and unknown is not missing`() {
        assertEquals(emptyList<ReverifyRow>(), MissingFromCloud.inOneDrive(listOf(row("a")), mapOf("a.jpg" to ref("x", null))))
    }

    // ── What is counted, and what Resend may put back ──────────────────────

    private val entryDao: BackupEntryDao = mock()
    private val albumDao: AlbumPreferenceDao = mock()
    private val folderDao: FolderPreferenceDao = mock()
    private val settings: BackupSettings = mock()
    private val engine: BackupEngine = mock()
    private val verifiers: CloudVerifiers = mock()
    private val requeue = RequeueMissingFromCloud(entryDao, albumDao, folderDao, settings, engine, verifiers)

    private suspend fun given(mode: AlbumMode?, oneDrive: Map<String, RemoteFileRef>?, vararg rows: ReverifyRow) {
        whenever(albumDao.modeOrNull("Trip")).thenReturn(mode)
        whenever(entryDao.uploadedOnPhone("Trip")).thenReturn(rows.toList())
        whenever(entryDao.sentElsewhere(any(), any())).thenReturn(emptyList())
        whenever(engine.remoteIndexFor(eq("Trip"), any())).thenReturn(oneDrive)
        whenever(folderDao.all()).thenReturn(listOf(FolderPreferenceEntity("DCIM", BackupLocation.DROPBOX)))
        whenever(settings.current()).thenReturn(BackupPreferences())
        whenever(entryDao.requeueMissingFromCloud(any(), any(), any(), any())).thenAnswer { (it.arguments[0] as List<*>).size }
    }

    @Test
    fun `the check only counts, and writes nothing`() = runTest {
        given(AlbumMode.BACKUP, null, row("gone"), row("here"))

        val found = requeue.missingFromOneDrive("Trip", mapOf("here.jpg" to ref("r-here", 10)), complete = true)

        assertEquals(listOf("gone"), found.map { it.id })
        verify(entryDao, never()).requeueMissingFromCloud(any(), any(), any(), any())
    }

    @Test
    fun `a failed or partial listing counts nothing`() = runTest {
        given(AlbumMode.BACKUP, null, row("gone"))
        assertEquals(emptyList<ReverifyRow>(), requeue.missingFromOneDrive("Trip", null, complete = true))
        assertEquals(emptyList<ReverifyRow>(), requeue.missingFromOneDrive("Trip", emptyMap(), complete = false))
    }

    @Test
    fun `OneDrive's listing only decides files sent to OneDrive`() = runTest {
        given(AlbumMode.BACKUP, null, row("dbx", location = BackupLocation.DROPBOX))
        assertEquals(emptyList<ReverifyRow>(), requeue.missingFromOneDrive("Trip", emptyMap(), complete = true))
    }

    @Test
    fun `Resend sends only the selected files the Cloud still lacks, to where their folder goes now`() = runTest {
        given(AlbumMode.BACKUP, mapOf("here.jpg" to ref("r-here", 10)), row("gone"), row("other"), row("here"))

        val n = requeue.resend("Trip", setOf("gone", "here"))

        // "other" is missing too but was not selected; "here" was selected but the Cloud has it.
        assertEquals(1, n)
        verify(entryDao).requeueMissingFromCloud(listOf("gone"), BackupLocation.DROPBOX)
    }

    @Test
    fun `Resend does nothing for an album that does not upload, or with nothing selected`() = runTest {
        given(AlbumMode.OFF, emptyMap(), row("gone"))
        assertEquals(0, requeue.resend("Trip", setOf("gone")))
        given(AlbumMode.BACKUP, emptyMap(), row("gone"))
        assertEquals(0, requeue.resend("Trip", emptySet()))
        verify(entryDao, never()).requeueMissingFromCloud(any(), any(), any(), any())
    }

    @Test
    fun `Resend asks again and puts nothing back when OneDrive cannot be listed`() = runTest {
        given(AlbumMode.BACKUP, null, row("gone"))
        assertEquals(0, requeue.resend("Trip", setOf("gone")))
    }

    @Test
    fun `another Cloud's missing files count only if they could be resent`() = runTest {
        // An optimized file, or one gone from the phone, is not returned by uploadedOnPhone, so it never counts.
        given(AlbumMode.BACKUP, null, row("a", location = BackupLocation.DROPBOX))
        assertEquals(listOf("a"), requeue.missingElsewhere("Trip", listOf("a", "optimized")).map { it.id })
    }

    // ── The other Clouds' answer, by row ────────────────────────────────────

    @Test
    fun `only definite answers are missing, and unknown or past the cap never are`() = runTest {
        fun sent(id: String) = SentElsewhereRow("Trip", BackupLocation.DROPBOX, "r-$id", 5, id)
        val rows = listOf(sent("listed"), sent("gone"), sent("unknown")) +
            (1..ElsewhereTally.MAX_LOOKUPS).map { sent("x$it") }
        val (outcome, missing) = ElsewhereTally.withMissing(rows, mapOf("r-listed" to 5L)) { id ->
            if (id == "r-unknown") RemoteCheck.Unknown else RemoteCheck.Gone
        }

        assertEquals(1, outcome.verified)
        // gone + 23 of the x rows are asked (25 lookups including unknown); the last 2 are past the cap.
        assertEquals(listOf("gone") + (1..ElsewhereTally.MAX_LOOKUPS - 2).map { "x$it" }, missing)
        assertEquals(outcome.missing, missing.size)
        assertEquals(3, outcome.unchecked)
    }
}
