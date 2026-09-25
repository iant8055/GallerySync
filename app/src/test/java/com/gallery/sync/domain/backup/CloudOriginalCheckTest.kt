package com.gallery.sync.domain.backup

import com.gallery.sync.data.local.entity.BackupEntryEntity
import com.gallery.sync.data.local.entity.BackupState
import com.gallery.sync.domain.repository.CloudVerifier
import com.gallery.sync.domain.repository.CloudVerifiers
import com.gallery.sync.domain.repository.RemoteCheck
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The gate before Sync overwrites a file on the phone: the cloud must confirm the original, or nothing is touched. */
class CloudOriginalCheckTest {

    private var answer: RemoteCheck = RemoteCheck.Unknown
    private var asked = 0

    private val dropbox = object : CloudVerifier {
        override val location = BackupLocation.DROPBOX
        override suspend fun sizeOf(remoteItemId: String): RemoteCheck {
            asked++
            return answer
        }
    }

    private var now = 1_000L
    private fun check(vararg verifiers: CloudVerifier = arrayOf(dropbox)) =
        CloudOriginalCheck(CloudVerifiers(verifiers.toSet())).also { it.clock = { now } }

    private fun row(
        location: BackupLocation = BackupLocation.DROPBOX,
        state: BackupState = BackupState.UPLOADED,
        remoteId: String? = "id:A",
        size: Long = 1_000L
    ) = BackupEntryEntity(
        id = "k", mediaStoreId = 1, contentUri = "content://x", displayName = "a.jpg", album = "a",
        sizeBytes = size, dateModifiedEpochSeconds = 1, mimeType = "image/jpeg", isVideo = false,
        state = state, remoteItemId = remoteId, location = location
    )

    @Test
    fun `OneDrive rows pass on their recorded size without asking anyone`() = runTest {
        assertTrue(check().confirms(row(location = BackupLocation.ONEDRIVE)))
        assertEquals(0, asked)
    }

    @Test
    fun `another cloud must say the original is there at exactly its size`() = runTest {
        answer = RemoteCheck.Present(1_000L)
        assertTrue(check().confirms(row()))
        assertEquals(1, asked)
    }

    @Test
    fun `a wrong size, a deleted file, no answer, no adapter and an unfinished upload all leave the file alone`() = runTest {
        val cases = listOf(
            RemoteCheck.Present(999L) to row(),
            RemoteCheck.Gone to row(),
            RemoteCheck.Unknown to row(),
        )
        cases.forEach { (reply, entry) ->
            answer = reply
            assertFalse("$reply", check().confirms(entry))
        }
        assertFalse(check(*emptyArray()).confirms(row()))
        assertFalse(check().confirms(row(state = BackupState.PENDING)))
        assertFalse(check().confirms(row(remoteId = "")))
        // A cloud with no Sync at all is never confirmed, whatever a verifier might say.
        assertFalse(check().confirms(row(location = BackupLocation.GOOGLE_PHOTOS)))
    }

    @Test
    fun `a file that fails is held for a while so a chain cannot pick it first for ever, then offered again`() = runTest {
        val gate = check()
        answer = RemoteCheck.Unknown
        assertFalse(gate.isHeld("k"))

        assertFalse(gate.confirms(row()))
        assertTrue(gate.isHeld("k"))

        now += 5 * 60 * 60 * 1000L
        assertTrue("still held after five hours", gate.isHeld("k"))
        now += 2 * 60 * 60 * 1000L
        assertFalse("offered again after six", gate.isHeld("k"))
    }
}
