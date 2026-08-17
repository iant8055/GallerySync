package com.gallery.sync.data.local.entity

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * Unit tests for the composite primary-key scheme shared by [MediaItemEntity] and
 * [MediaFolderEntity].
 *
 * The scheme is `"${source.name.lowercase()}:$remoteId"`. It exists so two providers handing out
 * the same native id cannot collide in one table, which would silently overwrite a user's row.
 */
class MediaCompositeIdTest {

    @Test
    fun `item id for onedrive is source prefixed and lowercased`() {
        assertEquals(
            "onedrive:ABC123",
            MediaItemEntity.buildId(MediaSource.ONEDRIVE, "ABC123")
        )
    }

    @Test
    fun `item id for google photos uses the lowercased enum name`() {
        assertEquals(
            "google_photos:ABC123",
            MediaItemEntity.buildId(MediaSource.GOOGLE_PHOTOS, "ABC123")
        )
    }

    @Test
    fun `folder id uses the same scheme as item id`() {
        assertEquals(
            MediaItemEntity.buildId(MediaSource.ONEDRIVE, "ABC123"),
            MediaFolderEntity.buildId(MediaSource.ONEDRIVE, "ABC123")
        )
    }

    @Test
    fun `the same remote id in two sources produces different ids`() {
        val oneDrive = MediaItemEntity.buildId(MediaSource.ONEDRIVE, "SHARED_ID")
        val googlePhotos = MediaItemEntity.buildId(MediaSource.GOOGLE_PHOTOS, "SHARED_ID")

        assertNotEquals(oneDrive, googlePhotos)
    }

    @Test
    fun `no two sources share an id prefix for the same remote id`() {
        val ids = MediaSource.entries.map { MediaItemEntity.buildId(it, "SHARED_ID") }

        assertEquals(
            "composite ids collided across sources: $ids",
            ids.size,
            ids.distinct().size
        )
    }

    @Test
    fun `remote id casing is preserved`() {
        // Only the source segment is lowercased. Graph item ids are case-sensitive, so lowercasing
        // the remote half would make the id unusable for a follow-up API call.
        assertEquals(
            "onedrive:AbC-123_xyz!",
            MediaItemEntity.buildId(MediaSource.ONEDRIVE, "AbC-123_xyz!")
        )
    }

    @Test
    fun `id is built with a single colon separator after the source`() {
        val id = MediaItemEntity.buildId(MediaSource.ONEDRIVE, "01ABC:DEF")

        // Graph ids can themselves contain a colon, so the parse rule is "split on the FIRST colon".
        assertEquals("onedrive", id.substringBefore(':'))
        assertEquals("01ABC:DEF", id.substringAfter(':'))
    }

    @Test
    fun `building the same id twice is stable`() {
        assertEquals(
            MediaItemEntity.buildId(MediaSource.ONEDRIVE, "ABC123"),
            MediaItemEntity.buildId(MediaSource.ONEDRIVE, "ABC123")
        )
    }
}
