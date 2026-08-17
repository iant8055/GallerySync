package com.gallery.sync.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the pure decision logic behind [Logger].
 *
 * [Logger] itself delegates to `android.util.Log`, which is an empty android.jar stub in local unit
 * tests, so the testable surface is deliberately the two `internal` pure functions.
 */
class LoggerTest {

    // ---------- formatTag ----------

    @Test
    fun `formatTag prefixes the tag with GallerySync slash`() {
        assertEquals("GallerySync/Sync", formatTag("Sync"))
    }

    @Test
    fun `formatTag leaves a short tag intact beyond the prefix`() {
        val formatted = formatTag("Abc")

        assertEquals("GallerySync/Abc", formatted)
        assertTrue(formatted.length <= MAX_TAG_LENGTH)
    }

    @Test
    fun `formatTag truncates a long tag to exactly 23 chars`() {
        // "GallerySync/" is 12 chars, so this tag pushes the result to 12 + 40 = 52 chars.
        val formatted = formatTag("A".repeat(40))

        assertEquals(23, formatted.length)
        assertEquals("GallerySync/" + "A".repeat(11), formatted)
    }

    @Test
    fun `formatTag truncation keeps the whole prefix`() {
        val formatted = formatTag("OneDriveRepository")

        assertEquals(MAX_TAG_LENGTH, formatted.length)
        assertTrue(formatted.startsWith(TAG_PREFIX))
    }

    @Test
    fun `formatTag leaves a tag that lands on exactly 23 chars untouched`() {
        // 23 - 12 = 11 characters of tag fit exactly.
        val exactTag = "B".repeat(MAX_TAG_LENGTH - TAG_PREFIX.length)

        val formatted = formatTag(exactTag)

        assertEquals(MAX_TAG_LENGTH, formatted.length)
        assertEquals(TAG_PREFIX + exactTag, formatted)
    }

    @Test
    fun `formatTag of an empty tag is just the prefix`() {
        assertEquals(TAG_PREFIX, formatTag(""))
    }

    // ---------- isLoggable: release build ----------

    @Test
    fun `verbose is not loggable in a release build`() {
        assertFalse(isLoggable(Level.VERBOSE, isDebugBuild = false))
    }

    @Test
    fun `debug is not loggable in a release build`() {
        assertFalse(isLoggable(Level.DEBUG, isDebugBuild = false))
    }

    @Test
    fun `info warn and error are loggable in a release build`() {
        assertTrue(isLoggable(Level.INFO, isDebugBuild = false))
        assertTrue(isLoggable(Level.WARN, isDebugBuild = false))
        assertTrue(isLoggable(Level.ERROR, isDebugBuild = false))
    }

    // ---------- isLoggable: debug build ----------

    @Test
    fun `every level is loggable in a debug build`() {
        Level.entries.forEach { level ->
            assertTrue(
                "expected $level to be loggable in a debug build",
                isLoggable(level, isDebugBuild = true)
            )
        }
    }
}
