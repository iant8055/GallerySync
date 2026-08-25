package com.gallery.sync.data.remote.onedrive

import com.gallery.sync.data.remote.onedrive.dto.GraphDriveItemDto
import com.gallery.sync.data.remote.onedrive.dto.GraphFileFacetDto
import com.gallery.sync.data.remote.onedrive.dto.GraphFolderFacetDto
import com.gallery.sync.data.remote.onedrive.dto.GraphImageFacetDto
import com.gallery.sync.data.remote.onedrive.dto.GraphParentReferenceDto
import com.gallery.sync.domain.model.RemoteMediaNode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the Graph `driveItem` -> [RemoteMediaNode] mapper.
 *
 * The mapper is a pure function, so there is not a single mock in this file.
 *
 * Epoch-millis expectations are hardcoded literals rather than recomputed with `Instant.parse`;
 * asserting with the same API the production code uses would prove nothing.
 */
class GraphDriveItemMapperTest {

    // 2024-01-15T10:30:00Z
    private val jan15 = 1_705_314_600_000L

    // ---------- folder discrimination ----------

    @Test
    fun `a dto with a folder facet maps to a Folder`() {
        val dto = GraphDriveItemDto(
            id = "01FOLDER",
            name = "Holiday 2024",
            lastModifiedDateTime = "2024-01-15T10:30:00Z",
            // Graph reports a folder's total content size here. Set so this test proves the mapper
            // carries it: the restore screen reads it to say what a folder holds without listing it.
            size = 1_024L,
            folder = GraphFolderFacetDto(childCount = 42),
            parentReference = GraphParentReferenceDto(id = "01ROOT", path = "/drive/root:/Pictures")
        )

        val node = dto.toRemoteMediaNode()

        assertEquals(
            RemoteMediaNode.Folder(
                id = "01FOLDER",
                name = "Holiday 2024",
                modifiedAtUtc = jan15,
                childCount = 42,
                sizeBytes = 1_024L,
                parentPath = "/drive/root:/Pictures"
            ),
            node
        )
    }

    @Test
    fun `a folder with no childCount defaults to zero`() {
        val dto = GraphDriveItemDto(
            id = "01FOLDER",
            name = "Empty",
            folder = GraphFolderFacetDto(childCount = null)
        )

        val node = dto.toRemoteMediaNode() as RemoteMediaNode.Folder

        assertEquals(0, node.childCount)
    }

    @Test
    fun `a folder with no parentReference has a null parentPath`() {
        val dto = GraphDriveItemDto(
            id = "01FOLDER",
            name = "Root Level",
            folder = GraphFolderFacetDto(childCount = 1),
            parentReference = null
        )

        val node = dto.toRemoteMediaNode() as RemoteMediaNode.Folder

        assertNull(node.parentPath)
    }

    @Test
    fun `a folder whose parentReference has a null path has a null parentPath`() {
        val dto = GraphDriveItemDto(
            id = "01FOLDER",
            name = "Root Level",
            folder = GraphFolderFacetDto(childCount = 1),
            parentReference = GraphParentReferenceDto(id = "01ROOT", path = null)
        )

        val node = dto.toRemoteMediaNode() as RemoteMediaNode.Folder

        assertNull(node.parentPath)
    }

    // ---------- file discrimination ----------

    @Test
    fun `a dto with a file facet maps to a File`() {
        val dto = GraphDriveItemDto(
            id = "01FILE",
            name = "IMG_0042.jpg",
            size = 2_481_152L,
            eTag = "\"{GUID},3\"",
            lastModifiedDateTime = "2024-01-15T10:30:00Z",
            file = GraphFileFacetDto(mimeType = "image/jpeg"),
            image = GraphImageFacetDto(width = 4032, height = 3024)
        )

        val node = dto.toRemoteMediaNode()

        assertEquals(
            RemoteMediaNode.File(
                id = "01FILE",
                name = "IMG_0042.jpg",
                modifiedAtUtc = jan15,
                mimeType = "image/jpeg",
                sizeBytes = 2_481_152L,
                widthPx = 4032,
                heightPx = 3024,
                eTag = "\"{GUID},3\""
            ),
            node
        )
    }

    @Test
    fun `a file with no mimeType falls back to application octet-stream`() {
        val dto = GraphDriveItemDto(
            id = "01FILE",
            name = "mystery.bin",
            file = GraphFileFacetDto(mimeType = null)
        )

        val node = dto.toRemoteMediaNode() as RemoteMediaNode.File

        assertEquals("application/octet-stream", node.mimeType)
        assertEquals(DEFAULT_MIME_TYPE, node.mimeType)
    }

    @Test
    fun `a file with no size defaults to zero bytes`() {
        val dto = GraphDriveItemDto(
            id = "01FILE",
            name = "no-size.jpg",
            size = null,
            file = GraphFileFacetDto(mimeType = "image/jpeg")
        )

        val node = dto.toRemoteMediaNode() as RemoteMediaNode.File

        assertEquals(0L, node.sizeBytes)
    }

    @Test
    fun `a file with no image facet has null dimensions`() {
        val dto = GraphDriveItemDto(
            id = "01FILE",
            name = "clip.mp4",
            size = 10L,
            file = GraphFileFacetDto(mimeType = "video/mp4"),
            image = null
        )

        val node = dto.toRemoteMediaNode() as RemoteMediaNode.File

        assertNull(node.widthPx)
        assertNull(node.heightPx)
    }

    @Test
    fun `a file with a partial image facet carries whichever dimension is present`() {
        val dto = GraphDriveItemDto(
            id = "01FILE",
            name = "half.jpg",
            file = GraphFileFacetDto(mimeType = "image/jpeg"),
            image = GraphImageFacetDto(width = 1920, height = null)
        )

        val node = dto.toRemoteMediaNode() as RemoteMediaNode.File

        assertEquals(1920, node.widthPx)
        assertNull(node.heightPx)
    }

    @Test
    fun `a file with no eTag maps to a null eTag`() {
        val dto = GraphDriveItemDto(
            id = "01FILE",
            name = "no-etag.jpg",
            eTag = null,
            file = GraphFileFacetDto(mimeType = "image/jpeg")
        )

        val node = dto.toRemoteMediaNode() as RemoteMediaNode.File

        assertNull(node.eTag)
    }

    // ---------- items that must be dropped ----------

    @Test
    fun `a dto with neither facet maps to null`() {
        // Graph mixes package and bundle items into children collections. Dropping them is correct.
        val dto = GraphDriveItemDto(
            id = "01PACKAGE",
            name = "OneNote Notebook",
            lastModifiedDateTime = "2024-01-15T10:30:00Z"
        )

        assertNull(dto.toRemoteMediaNode())
    }

    @Test
    fun `a dto with a null id maps to null`() {
        val dto = GraphDriveItemDto(
            id = null,
            name = "IMG_0042.jpg",
            file = GraphFileFacetDto(mimeType = "image/jpeg")
        )

        assertNull(dto.toRemoteMediaNode())
    }

    @Test
    fun `a dto with a null name maps to null`() {
        val dto = GraphDriveItemDto(
            id = "01FILE",
            name = null,
            file = GraphFileFacetDto(mimeType = "image/jpeg")
        )

        assertNull(dto.toRemoteMediaNode())
    }

    @Test
    fun `a folder dto with a null id maps to null`() {
        val dto = GraphDriveItemDto(
            id = null,
            name = "Pictures",
            folder = GraphFolderFacetDto(childCount = 3)
        )

        assertNull(dto.toRemoteMediaNode())
    }

    @Test
    fun `an entirely empty dto maps to null`() {
        assertNull(GraphDriveItemDto().toRemoteMediaNode())
    }

    @Test
    fun `a dto carrying both facets is treated as a folder`() {
        // Graph should never send both, but the mapper's `when` checks folder first and the
        // resulting behaviour is deterministic. Pinning it so a reorder is a visible change.
        val dto = GraphDriveItemDto(
            id = "01BOTH",
            name = "ambiguous",
            folder = GraphFolderFacetDto(childCount = 2),
            file = GraphFileFacetDto(mimeType = "image/jpeg")
        )

        assertTrue(dto.toRemoteMediaNode() is RemoteMediaNode.Folder)
    }

    // ---------- date parsing ----------

    @Test
    fun `an ISO-8601 timestamp becomes the correct epoch millis`() {
        assertEquals(jan15, parseIso8601ToEpochMillis("2024-01-15T10:30:00Z"))
    }

    @Test
    fun `the unix epoch itself parses to zero`() {
        assertEquals(0L, parseIso8601ToEpochMillis("1970-01-01T00:00:00Z"))
    }

    @Test
    fun `fractional seconds are preserved to millisecond precision`() {
        // Graph routinely returns sub-second precision.
        assertEquals(
            1_705_314_600_123L,
            parseIso8601ToEpochMillis("2024-01-15T10:30:00.123Z")
        )
    }

    @Test
    fun `a pre-epoch timestamp parses to a negative value`() {
        assertEquals(-1000L, parseIso8601ToEpochMillis("1969-12-31T23:59:59Z"))
    }

    @Test
    fun `a null timestamp becomes zero`() {
        assertEquals(0L, parseIso8601ToEpochMillis(null))
    }

    @Test
    fun `a blank timestamp becomes zero`() {
        assertEquals(0L, parseIso8601ToEpochMillis(""))
        assertEquals(0L, parseIso8601ToEpochMillis("   "))
    }

    @Test
    fun `a garbage timestamp becomes zero`() {
        assertEquals(0L, parseIso8601ToEpochMillis("not-a-date"))
    }

    @Test
    fun `a date without a time component becomes zero`() {
        assertEquals(0L, parseIso8601ToEpochMillis("2024-01-15"))
    }

    @Test
    fun `a local date-time with no zone designator becomes zero`() {
        assertEquals(0L, parseIso8601ToEpochMillis("2024-01-15T10:30:00"))
    }

    @Test
    fun `an out of range timestamp becomes zero`() {
        assertEquals(0L, parseIso8601ToEpochMillis("2024-13-45T99:99:99Z"))
    }

    @Test
    fun `a mapped node with a missing timestamp carries zero`() {
        val dto = GraphDriveItemDto(
            id = "01FILE",
            name = "undated.jpg",
            lastModifiedDateTime = null,
            file = GraphFileFacetDto(mimeType = "image/jpeg")
        )

        assertEquals(0L, dto.toRemoteMediaNode()!!.modifiedAtUtc)
    }

    @Test
    fun `a mapped node with a malformed timestamp carries zero rather than failing`() {
        // One bad timestamp must not fail an entire folder listing.
        val dto = GraphDriveItemDto(
            id = "01FILE",
            name = "broken-date.jpg",
            lastModifiedDateTime = "15/01/2024 10:30",
            file = GraphFileFacetDto(mimeType = "image/jpeg")
        )

        assertEquals(0L, dto.toRemoteMediaNode()!!.modifiedAtUtc)
    }
}
