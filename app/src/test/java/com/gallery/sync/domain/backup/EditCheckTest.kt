package com.gallery.sync.domain.backup

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The rule found on the Moto G, 26 Sept 2026: a shrunk file whose size has moved was edited. */
class EditCheckTest {

    @Test
    fun `a shrunk file that is a different size now was edited`() {
        // 744,869 was the copy the app wrote; the edit made it 744,886.
        assertTrue(EditCheck.isEdited(isProxied = true, recordedProxySizeBytes = 744_869, currentSizeBytes = 744_886))
    }

    @Test
    fun `a shrunk file at the size the app wrote was not edited`() {
        assertFalse(EditCheck.isEdited(true, 744_869, 744_869))
    }

    @Test
    fun `a file that is not a proxy is never edited here`() {
        assertFalse(EditCheck.isEdited(false, 744_869, 900_000))
    }

    @Test
    fun `an unknown size is not treated as edited`() {
        assertFalse(EditCheck.isEdited(true, null, 900_000))
        assertFalse(EditCheck.isEdited(true, 744_869, null))
    }

    @Test
    fun `the scan calls a size that is neither the copy nor the original an edit`() {
        assertTrue(EditCheck.isEditedNow(currentSizeBytes = 744_886, originalSizeBytes = 3_845_655, proxySizeBytes = 744_869))
    }

    @Test
    fun `the scan does not call the copy or a stale index an edit`() {
        assertFalse(EditCheck.isEditedNow(744_869, 3_845_655, 744_869))
        // MediaStore still reporting the original's size just after a shrink.
        assertFalse(EditCheck.isEditedNow(3_845_655, 3_845_655, 744_869))
    }

    @Test
    fun `a row with no recorded copy size is never called edited by the scan`() {
        assertFalse(EditCheck.isEditedNow(900_000, 3_845_655, null))
    }
}
