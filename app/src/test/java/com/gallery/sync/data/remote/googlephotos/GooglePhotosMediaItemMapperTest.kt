package com.gallery.sync.data.remote.googlephotos

import com.gallery.sync.data.remote.googlephotos.dto.MediaItemDto
import com.gallery.sync.data.remote.googlephotos.dto.MediaMetadataDto
import com.gallery.sync.domain.repository.GooglePhotosMediaItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Unit tests for the Library API `mediaItem` -> [GooglePhotosMediaItem] mapper.
 *
 * The mapper is a pure function, so there is not a single mock in this file — same shape as
 * `GraphDriveItemMapperTest`.
 */
class GooglePhotosMediaItemMapperTest {

    // 2026-09-22T14:03:11Z
    private val sep22 = 1_790_085_791L

    @Test
    fun `a complete dto maps every field`() {
        val dto = MediaItemDto(
            id = "AGjxx01",
            filename = "IMG_0042.jpg",
            mediaMetadata = MediaMetadataDto(creationTime = "2026-09-22T14:03:11Z")
        )

        assertEquals(
            GooglePhotosMediaItem(
                id = "AGjxx01",
                filename = "IMG_0042.jpg",
                creationTimeEpochSeconds = sep22
            ),
            dto.toGooglePhotosMediaItem()
        )
    }

    @Test
    fun `a dto with no id maps to null`() {
        // Nothing downstream can key on an item without one.
        val dto = MediaItemDto(id = null, filename = "IMG_0042.jpg")

        assertNull(dto.toGooglePhotosMediaItem())
    }

    @Test
    fun `an entirely empty dto maps to null`() {
        assertNull(MediaItemDto().toGooglePhotosMediaItem())
    }

    @Test
    fun `a null filename becomes an empty string, never null`() {
        val dto = MediaItemDto(id = "AGjxx01", filename = null)

        assertEquals("", dto.toGooglePhotosMediaItem()!!.filename)
    }

    @Test
    fun `no mediaMetadata leaves the creation time null`() {
        val dto = MediaItemDto(id = "AGjxx01", filename = "IMG_0042.jpg", mediaMetadata = null)

        assertNull(dto.toGooglePhotosMediaItem()!!.creationTimeEpochSeconds)
    }

    @Test
    fun `mediaMetadata with no creationTime leaves it null`() {
        val dto = MediaItemDto(
            id = "AGjxx01",
            filename = "IMG_0042.jpg",
            mediaMetadata = MediaMetadataDto(creationTime = null)
        )

        assertNull(dto.toGooglePhotosMediaItem()!!.creationTimeEpochSeconds)
    }

    // ---------- date parsing ----------

    @Test
    fun `an RFC 3339 timestamp becomes the correct epoch seconds`() {
        assertEquals(sep22, parseCreationTimeEpochSeconds("2026-09-22T14:03:11Z"))
    }

    @Test
    fun `the unix epoch itself parses to zero`() {
        assertEquals(0L, parseCreationTimeEpochSeconds("1970-01-01T00:00:00Z"))
    }

    @Test
    fun `a null timestamp parses to null, not zero`() {
        // Unlike OneDrive's parseIso8601ToEpochMillis, this field has no fallback to a sibling
        // timestamp, so null must mean "unknown" rather than a synthetic zero.
        assertNull(parseCreationTimeEpochSeconds(null))
    }

    @Test
    fun `a blank timestamp parses to null`() {
        assertNull(parseCreationTimeEpochSeconds(""))
        assertNull(parseCreationTimeEpochSeconds("   "))
    }

    @Test
    fun `a garbage timestamp parses to null`() {
        assertNull(parseCreationTimeEpochSeconds("not-a-date"))
    }

    @Test
    fun `a local date-time with no zone designator parses to null`() {
        assertNull(parseCreationTimeEpochSeconds("2026-09-22T14:03:11"))
    }
}
