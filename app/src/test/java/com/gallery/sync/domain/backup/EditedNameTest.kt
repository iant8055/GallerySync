package com.gallery.sync.domain.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class EditedNameTest {

    @Test
    fun `an edit is named for its own modified time and keeps its extension`() {
        // 1790464537 is 2026-09-26 23:15:37 UTC.
        assertEquals(
            "20171014_122728 (edited 20260926-231537).jpg",
            EditedName.of("20171014_122728.jpg", 1_790_464_537)
        )
    }

    @Test
    fun `the same edit always gets the same name, and a later edit a different one`() {
        val first = EditedName.of("a.jpg", 1_790_464_537)
        assertEquals(first, EditedName.of("a.jpg", 1_790_464_537))
        assertNotEquals(first, EditedName.of("a.jpg", 1_790_500_000))
    }

    @Test
    fun `a name with no extension still gets a suffix`() {
        assertEquals("clip (edited 20260926-231537)", EditedName.of("clip", 1_790_464_537))
    }
}
