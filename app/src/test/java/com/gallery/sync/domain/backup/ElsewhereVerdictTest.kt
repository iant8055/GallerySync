package com.gallery.sync.domain.backup

import com.gallery.sync.domain.repository.RemoteCheck
import org.junit.Assert.assertEquals
import org.junit.Test

/** The rule that decides whether a file held by another cloud may leave the phone. */
class ElsewhereVerdictTest {

    private fun verdict(uploaded: Boolean = true, hasVerifier: Boolean = true, check: RemoteCheck?, expected: Long = 1_000L) =
        ElsewhereVerdict.of(uploaded, hasVerifier, check, expected)

    @Test
    fun `only a live answer at the phone's size permits removal`() {
        assertEquals(ElsewhereVerdict.CONFIRMED, verdict(check = RemoteCheck.Present(1_000L)))
    }

    @Test
    fun `a file present at another size protects nothing`() {
        assertEquals(ElsewhereVerdict.WRONG_SIZE, verdict(check = RemoteCheck.Present(999L)))
        assertEquals(ElsewhereVerdict.WRONG_SIZE, verdict(check = RemoteCheck.Present(1_001L)))
    }

    @Test
    fun `a zero-byte match is never confirmation`() {
        assertEquals(ElsewhereVerdict.WRONG_SIZE, verdict(check = RemoteCheck.Present(0L), expected = 0L))
    }

    @Test
    fun `gone from the cloud is missing, which the Archive tab answers by backing it up`() {
        assertEquals(ElsewhereVerdict.MISSING, verdict(check = RemoteCheck.Gone))
    }

    @Test
    fun `not uploaded yet is missing without asking the cloud anything`() {
        assertEquals(ElsewhereVerdict.MISSING, verdict(uploaded = false, check = null))
    }

    @Test
    fun `if we could not ask, we do not remove`() {
        assertEquals(ElsewhereVerdict.UNCONFIRMED, verdict(check = RemoteCheck.Unknown))
        assertEquals(ElsewhereVerdict.UNCONFIRMED, verdict(check = null))
        assertEquals(ElsewhereVerdict.UNCONFIRMED, verdict(hasVerifier = false, check = null))
    }

    @Test
    fun `no verdict but the confirming one lets a file go`() {
        val others = listOf(
            verdict(check = RemoteCheck.Present(5L)),
            verdict(check = RemoteCheck.Gone),
            verdict(check = RemoteCheck.Unknown),
            verdict(uploaded = false, check = null),
            verdict(hasVerifier = false, check = null)
        )
        others.forEach { assertEquals(false, it == ElsewhereVerdict.CONFIRMED) }
    }
}
