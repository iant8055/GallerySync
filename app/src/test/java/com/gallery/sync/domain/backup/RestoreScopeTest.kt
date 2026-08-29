package com.gallery.sync.domain.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RestoreScopeTest {

    private data class Row(val album: String, val name: String, val size: Long)

    private val sig: (Row) -> String = { RestoreScope.signature(it.album, it.name, it.size) }

    private val archived = Row("PauseTest", "clip.mp4", 117_668_262L)
    private val stillHere = Row("BudgetVideo", "holiday.mp4", 500L)

    @Test
    fun `a file still in its own folder is not offered`() {
        val present = setOf(sig(stillHere))
        assertTrue(RestoreScope.notOnTheDevice(listOf(stillHere), present, sig).isEmpty())
    }

    /** The case Ian hit: gone from its album, byte-identical copy in another. */
    @Test
    fun `a copy in a different album does not count as present`() {
        val duplicateElsewhere = RestoreScope.signature("BudgetVideo", "clip.mp4", 117_668_262L)
        val offered = RestoreScope.notOnTheDevice(
            candidates = listOf(archived),
            presentOnDevice = setOf(duplicateElsewhere),
            signatureOf = sig
        )
        assertEquals(listOf(archived), offered)
    }

    @Test
    fun `the same name at a different size is a different file`() {
        val present = setOf(RestoreScope.signature("PauseTest", "clip.mp4", 999L))
        assertEquals(listOf(archived), RestoreScope.notOnTheDevice(listOf(archived), present, sig))
    }

    /** A failed scan must never be read as "the phone is empty". */
    @Test
    fun `an empty device scan offers nothing rather than everything`() {
        val offered = RestoreScope.notOnTheDevice(listOf(archived, stillHere), emptySet(), sig)
        assertTrue(offered.isEmpty())
    }

    @Test
    fun `several albums are judged independently`() {
        val present = setOf(sig(stillHere))
        assertEquals(
            listOf(archived),
            RestoreScope.notOnTheDevice(listOf(archived, stillHere), present, sig)
        )
    }
}
