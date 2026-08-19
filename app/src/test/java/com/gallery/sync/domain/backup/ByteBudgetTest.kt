package com.gallery.sync.domain.backup

import com.gallery.sync.data.local.entity.BackupEntryEntity
import com.gallery.sync.data.local.entity.BackupState
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The batch is bounded by bytes as well as by file count.
 *
 * Twenty-five photos is about 100 MB; twenty-five videos can be four gigabytes, and a run that long
 * gets stopped partway — which throws away the file in flight, because the upload session is not
 * persisted across runs. Confirmed on hardware 19 Aug 2026.
 */
class ByteBudgetTest {

    @Test
    fun `keeps everything when the batch fits`() {
        val files = listOf(entry("a", 10), entry("b", 20), entry("c", 30))

        assertEquals(3, budget(files, maxBytes = 100).size)
    }

    @Test
    fun `stops once the budget is used up`() {
        val files = listOf(entry("a", 40), entry("b", 40), entry("c", 40))

        val kept = budget(files, maxBytes = 100)

        assertEquals(listOf("a", "b"), kept.map { it.displayName })
    }

    @Test
    fun `a single file larger than the whole budget is still attempted`() {
        // The case that matters. Dropping it would mean the largest files never upload at all,
        // silently, forever — worse than one long run.
        val files = listOf(entry("huge.mp4", 5_000), entry("small.jpg", 10))

        val kept = budget(files, maxBytes = 1_000)

        assertEquals(listOf("huge.mp4"), kept.map { it.displayName })
    }

    @Test
    fun `an empty batch stays empty`() {
        assertEquals(0, budget(emptyList(), maxBytes = 100).size)
    }

    @Test
    fun `order is preserved, because largest-first ordering is what makes a capped run useful`() {
        val files = listOf(entry("a", 30), entry("b", 20), entry("c", 10))

        assertEquals(listOf("a", "b"), budget(files, maxBytes = 55).map { it.displayName })
    }

    private fun budget(files: List<BackupEntryEntity>, maxBytes: Long) =
        BackupEngine.withinByteBudget(files, maxBytes)

    private fun entry(name: String, size: Long) = BackupEntryEntity(
        id = name,
        mediaStoreId = 1L,
        contentUri = "content://media/external/images/media/1",
        displayName = name,
        album = "Camera",
        sizeBytes = size,
        dateModifiedEpochSeconds = 0L,
        mimeType = "image/jpeg",
        isVideo = false,
        state = BackupState.PENDING
    )
}
