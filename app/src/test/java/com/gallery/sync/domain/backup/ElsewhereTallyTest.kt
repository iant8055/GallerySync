package com.gallery.sync.domain.backup

import com.gallery.sync.data.local.dao.SentElsewhereRow
import com.gallery.sync.domain.repository.RemoteCheck
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/** The per-Cloud "verified" line's comparison (Ian, 16 Nov 2026). See [ElsewhereTally]. */
class ElsewhereTallyTest {

    private fun row(id: String, size: Long) = SentElsewhereRow("Camera", BackupLocation.DROPBOX, id, size)

    @Test
    fun `a listed file at the local size is verified, at another size it is missing`() = runTest {
        val outcome = ElsewhereTally.of(
            listOf(row("a", 10), row("b", 20)),
            mapOf("a" to 10L, "b" to 21L)
        ) { error("nothing should be asked") }
        assertEquals(ElsewhereTally.Outcome(verified = 1, missing = 1, unchecked = 0), outcome)
    }

    @Test
    fun `a file not in the listing is asked about by id, because the folder may have changed`() = runTest {
        val asked = mutableListOf<String>()
        val answers = mapOf(
            "moved" to RemoteCheck.Present(5),
            "gone" to RemoteCheck.Gone,
            "resized" to RemoteCheck.Present(6),
            "unknown" to RemoteCheck.Unknown
        )
        val outcome = ElsewhereTally.of(
            listOf(row("moved", 5), row("gone", 5), row("resized", 5), row("unknown", 5)),
            emptyMap()
        ) { asked += it; answers.getValue(it) }
        assertEquals(listOf("moved", "gone", "resized", "unknown"), asked)
        assertEquals(ElsewhereTally.Outcome(verified = 1, missing = 2, unchecked = 1), outcome)
    }

    @Test
    fun `failing to ask is never counted as missing`() = runTest {
        val outcome = ElsewhereTally.of(listOf(row("x", 1)), emptyMap()) { RemoteCheck.Unknown }
        assertEquals(0, outcome.missing)
        assertEquals(1, outcome.unchecked)
    }

    @Test
    fun `lookups stop at the cap and the rest are unchecked, not missing`() = runTest {
        var asked = 0
        val rows = (1..ElsewhereTally.MAX_LOOKUPS + 10).map { row("f$it", 1) }
        val outcome = ElsewhereTally.of(rows, emptyMap()) { asked++; RemoteCheck.Gone }
        assertEquals(ElsewhereTally.MAX_LOOKUPS, asked)
        assertEquals(ElsewhereTally.Outcome(verified = 0, missing = ElsewhereTally.MAX_LOOKUPS, unchecked = 10), outcome)
    }
}
