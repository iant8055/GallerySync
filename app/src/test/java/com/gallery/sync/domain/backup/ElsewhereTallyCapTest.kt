package com.gallery.sync.domain.backup

import com.gallery.sync.data.local.dao.SentElsewhereRow
import com.gallery.sync.domain.repository.RemoteCheck
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

/** The Restore tab asks past the Albums tab's 25 (`RestoreElsewhereCheck`); every other caller keeps 25. */
class ElsewhereTallyCapTest {

    private val rows = (1..60).map { SentElsewhereRow("Pictures", BackupLocation.DROPBOX, "r$it", 10L, "id$it") }

    @Test
    fun `the default cap leaves files past 25 unchecked, never missing`() = runBlocking {
        val (outcome, missing) = ElsewhereTally.withMissing(rows, emptyMap()) { RemoteCheck.Gone }
        assertEquals(25, missing.size)
        assertEquals(35, outcome.unchecked)
    }

    @Test
    fun `a higher cap reaches every file of a deleted folder`() = runBlocking {
        val (outcome, missing) =
            ElsewhereTally.withMissing(rows, emptyMap(), RestoreElsewhereCheck.MAX_LOOKUPS) { RemoteCheck.Gone }
        assertEquals(60, missing.size)
        assertEquals(0, outcome.unchecked)
    }

    @Test
    fun `an unknown answer is kept whatever the cap`() = runBlocking {
        val (_, missing) =
            ElsewhereTally.withMissing(rows, emptyMap(), RestoreElsewhereCheck.MAX_LOOKUPS) { RemoteCheck.Unknown }
        assertEquals(0, missing.size)
    }
}
