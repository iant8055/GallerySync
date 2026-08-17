package com.gallery.sync.data.local.converter

import com.gallery.sync.data.local.entity.MediaSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * Unit tests for the Room type converter.
 *
 * The behaviour that matters is the failure mode: an unrecognised string must throw rather than
 * default to a provider, because a silent default would let a corrupted index masquerade as
 * legitimate OneDrive data.
 */
class MediaSourceConverterTest {

    private val converter = MediaSourceConverter()

    @Test
    fun `onedrive round-trips`() {
        val stored = converter.fromMediaSource(MediaSource.ONEDRIVE)

        assertEquals("ONEDRIVE", stored)
        assertEquals(MediaSource.ONEDRIVE, converter.toMediaSource(stored))
    }

    @Test
    fun `google photos round-trips`() {
        val stored = converter.fromMediaSource(MediaSource.GOOGLE_PHOTOS)

        assertEquals("GOOGLE_PHOTOS", stored)
        assertEquals(MediaSource.GOOGLE_PHOTOS, converter.toMediaSource(stored))
    }

    @Test
    fun `every enum constant round-trips`() {
        MediaSource.entries.forEach { source ->
            assertEquals(source, converter.toMediaSource(converter.fromMediaSource(source)))
        }
    }

    @Test
    fun `an unknown string throws with the offending value in the message`() {
        try {
            converter.toMediaSource("DROPBOX")
            fail("expected IllegalArgumentException for an unknown MediaSource value")
        } catch (e: IllegalArgumentException) {
            assertTrue(
                "message did not name the offending value: ${e.message}",
                e.message.orEmpty().contains("DROPBOX")
            )
        }
    }

    @Test
    fun `an empty string throws`() {
        try {
            converter.toMediaSource("")
            fail("expected IllegalArgumentException for an empty MediaSource value")
        } catch (e: IllegalArgumentException) {
            assertTrue(
                "message should identify the converter and the value: ${e.message}",
                e.message.orEmpty().contains("MediaSource")
            )
        }
    }

    @Test
    fun `matching is case sensitive so a lowercase name is rejected`() {
        // Storage writes MediaSource.name, which is uppercase. Accepting "onedrive" here would
        // paper over a writer that stored the wrong casing.
        try {
            converter.toMediaSource("onedrive")
            fail("expected IllegalArgumentException for a lowercase MediaSource value")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message.orEmpty().contains("onedrive"))
        }
    }
}
