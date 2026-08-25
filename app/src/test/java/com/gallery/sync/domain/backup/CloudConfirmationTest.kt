package com.gallery.sync.domain.backup

import android.net.Uri
import com.gallery.sync.data.local.media.LocalMediaItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.mock

/**
 * The answer to "is this still in OneDrive?", asked of the drive rather than the ledger.
 *
 * The distinction the whole type exists for is [unconfirmedIsNotAYes]. `verifiedInCloud` reads a
 * *remembered* byte size — it says a copy was confirmed once, which is a different claim from "there
 * is a copy now". Removal is the one operation where only the second will do, and a listing that
 * failed answers neither question.
 *
 * Demonstrated on hardware 25 Aug 2026: a test file was deleted from OneDrive by hand and the ledger
 * went on asserting it was safely backed up, with nothing anywhere to notice.
 */
class CloudConfirmationTest {

    private val uri: Uri = mock()

    private fun item(name: String) = LocalMediaItem(
        mediaStoreId = name.hashCode().toLong(),
        contentUri = uri,
        displayName = name,
        album = "Camera",
        sizeBytes = 1_024L,
        dateModifiedEpochSeconds = 1_700_000_000L,
        mimeType = "image/jpeg",
        isVideo = false,
        relativePath = "DCIM/Camera/"
    )

    @Test
    fun everythingConfirmedIsComplete() {
        val result = CloudConfirmation(confirmed = listOf(item("a.jpg"), item("b.jpg")))

        assertTrue(result.isComplete)
        assertEquals(0, result.heldBack)
    }

    /**
     * The rule this is all for: a listing that could not be made is not permission to delete. It is
     * counted as held back, exactly like a file known to be absent.
     */
    @Test
    fun unconfirmedIsNotAYes() {
        val result = CloudConfirmation(
            confirmed = listOf(item("a.jpg")),
            unconfirmed = listOf(item("b.jpg"))
        )

        assertFalse("an unchecked file leaves the answer incomplete", result.isComplete)
        assertEquals(1, result.heldBack)
    }

    /** Absent is worse than unchecked — the ledger was wrong and the file is not backed up. */
    @Test
    fun aFileNoLongerInTheCloudIsHeldBack() {
        val result = CloudConfirmation(
            confirmed = listOf(item("a.jpg")),
            missing = listOf(item("b.jpg"))
        )

        assertFalse(result.isComplete)
        assertEquals(1, result.heldBack)
    }

    /** Kept apart because they ask different things of the user, and of the app. */
    @Test
    fun missingAndUnconfirmedAreCountedSeparately() {
        val result = CloudConfirmation(
            confirmed = listOf(item("a.jpg")),
            missing = listOf(item("b.jpg")),
            unconfirmed = listOf(item("c.jpg"), item("d.jpg"))
        )

        assertEquals(1, result.missing.size)
        assertEquals(2, result.unconfirmed.size)
        assertEquals(3, result.heldBack)
    }

    @Test
    fun nothingAskedIsTriviallyComplete() {
        assertTrue(CloudConfirmation().isComplete)
        assertEquals(0, CloudConfirmation().heldBack)
    }
}
