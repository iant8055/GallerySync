package com.gallery.sync.domain.backup

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The net for a scan that came back short. See [MassAbsence]. */
class MassAbsenceTest {

    @Test
    fun `a few files gone from a big library is an ordinary tidy-up`() {
        assertFalse(MassAbsence.looksLikeABadScan(missing = 15, uploaded = 2_000))
    }

    @Test
    fun `a small library is never judged, however much of it is gone`() {
        assertFalse(MassAbsence.looksLikeABadScan(missing = MassAbsence.MIN_FILES, uploaded = 25))
    }

    @Test
    fun `more than half the library going at once reads as a bad scan`() {
        assertTrue(MassAbsence.looksLikeABadScan(missing = 1_200, uploaded = 2_000))
    }

    @Test
    fun `exactly half is not more than half`() {
        assertFalse(MassAbsence.looksLikeABadScan(missing = 1_000, uploaded = 2_000))
    }

    @Test
    fun `many files but a small share of a large library is fine`() {
        assertFalse(MassAbsence.looksLikeABadScan(missing = 300, uploaded = 2_079))
    }
}
