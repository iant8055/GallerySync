package com.gallery.sync.domain.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The rule on its own: an edited photo is not a deleted photo. See [EditedInPlace]. */
class EditedInPlaceTest {

    private val here = EditedInPlace.keysOf(
        listOf("Camera" to "IMG_1234.jpg", "WhatsApp" to "IMG-0001.jpg")
    )

    @Test
    fun `a name still in its folder is still here whatever its size became`() {
        assertTrue(EditedInPlace.isStillHere("Camera", "IMG_1234.jpg", here))
    }

    @Test
    fun `a name that is nowhere is not here`() {
        assertFalse(EditedInPlace.isStillHere("Camera", "IMG_9999.jpg", here))
    }

    @Test
    fun `the same name in another folder is not here`() {
        assertFalse(EditedInPlace.isStillHere("Screenshots", "IMG_1234.jpg", here))
    }

    @Test
    fun `folder case does not matter but name case does`() {
        assertTrue(EditedInPlace.isStillHere("camera", "IMG_1234.jpg", here))
        assertFalse(EditedInPlace.isStillHere("Camera", "img_1234.jpg", here))
    }

    @Test
    fun `a restored suffix is taken off before comparing`() {
        assertEquals(
            EditedInPlace.keyOf("Camera", "IMG_1234.jpg"),
            EditedInPlace.keyOf("Camera", "IMG_1234_restored.jpg")
        )
    }
}
