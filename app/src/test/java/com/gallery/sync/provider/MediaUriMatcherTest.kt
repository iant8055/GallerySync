package com.gallery.sync.provider

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Unit tests for the provider's routing table.
 *
 * [MediaUriMatcher] operates on already-decoded path segments rather than on `android.net.Uri`,
 * which is exactly what makes it testable here without a device.
 */
class MediaUriMatcherTest {

    // ---------- match ----------

    @Test
    fun `single media segment routes to the media directory`() {
        assertEquals(MediaRoute.MEDIA_DIRECTORY, MediaUriMatcher.match(listOf("media")))
    }

    @Test
    fun `media plus an id routes to a media item`() {
        assertEquals(MediaRoute.MEDIA_ITEM, MediaUriMatcher.match(listOf("media", "abc123")))
    }

    @Test
    fun `an empty path is unknown`() {
        assertEquals(MediaRoute.UNKNOWN, MediaUriMatcher.match(emptyList()))
    }

    @Test
    fun `a non-media root segment is unknown`() {
        assertEquals(MediaRoute.UNKNOWN, MediaUriMatcher.match(listOf("notmedia")))
    }

    @Test
    fun `a three segment path is unknown`() {
        assertEquals(MediaRoute.UNKNOWN, MediaUriMatcher.match(listOf("media", "a", "b")))
    }

    @Test
    fun `a non-media two segment path is unknown`() {
        assertEquals(MediaRoute.UNKNOWN, MediaUriMatcher.match(listOf("notmedia", "abc123")))
    }

    @Test
    fun `matching is case sensitive on the media segment`() {
        // Authorities are case-insensitive but path segments are not; "Media" is a different path.
        assertEquals(MediaRoute.UNKNOWN, MediaUriMatcher.match(listOf("Media")))
    }

    @Test
    fun `an empty second segment still routes to a media item`() {
        // content://.../media/ can produce a trailing empty segment on some Uri implementations.
        // Documenting the current behaviour: two segments starting with "media" is an item route.
        assertEquals(MediaRoute.MEDIA_ITEM, MediaUriMatcher.match(listOf("media", "")))
    }

    // ---------- itemIdFrom ----------

    @Test
    fun `itemIdFrom returns the id segment for an item route`() {
        assertEquals("abc123", MediaUriMatcher.itemIdFrom(listOf("media", "abc123")))
    }

    @Test
    fun `itemIdFrom returns null for the directory route`() {
        assertNull(MediaUriMatcher.itemIdFrom(listOf("media")))
    }

    @Test
    fun `itemIdFrom returns null for an empty path`() {
        assertNull(MediaUriMatcher.itemIdFrom(emptyList()))
    }

    @Test
    fun `itemIdFrom returns null for an unknown route`() {
        assertNull(MediaUriMatcher.itemIdFrom(listOf("notmedia", "abc123")))
        assertNull(MediaUriMatcher.itemIdFrom(listOf("media", "a", "b")))
    }

    @Test
    fun `itemIdFrom returns the decoded composite id`() {
        // Uri.pathSegments hands the provider DECODED segments, so a URI written as
        // content://com.gallery.sync.provider/media/onedrive%3AABC arrives here as "onedrive:ABC".
        // Asserting on the decoded form is the contract the provider actually sees.
        val segments = listOf("media", "onedrive:ABC")

        assertEquals(MediaRoute.MEDIA_ITEM, MediaUriMatcher.match(segments))
        assertEquals("onedrive:ABC", MediaUriMatcher.itemIdFrom(segments))
    }

    @Test
    fun `itemIdFrom handles a google photos composite id`() {
        val segments = listOf("media", "google_photos:XYZ-789")

        assertEquals("google_photos:XYZ-789", MediaUriMatcher.itemIdFrom(segments))
    }
}
