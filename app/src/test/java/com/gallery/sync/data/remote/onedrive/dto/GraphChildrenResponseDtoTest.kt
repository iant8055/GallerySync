package com.gallery.sync.data.remote.onedrive.dto

import com.gallery.sync.di.NetworkModule
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Deserialization tests for the Graph DTOs, run through the **actual configured** [Json] instance
 * from [NetworkModule] rather than a locally built one — otherwise the test would pass while the
 * shipped configuration was wrong.
 *
 * The two behaviours that matter:
 *  - `@odata.nextLink` reaching `nextLink` via `@SerialName`; a plain field name silently yields
 *    `null` and paging stops after one page.
 *  - `ignoreUnknownKeys`; Microsoft adds `driveItem` fields continuously and strict parsing would
 *    turn any such addition into a crash on the user's device.
 */
class GraphChildrenResponseDtoTest {

    private val json: Json = NetworkModule.provideJson()

    private val realisticPayload = """
        {
          "@odata.context": "https://graph.microsoft.com/v1.0/${'$'}metadata#users('u')/drive/root/children",
          "@odata.count": 2,
          "@odata.nextLink": "https://graph.microsoft.com/v1.0/me/drive/root/children?%24skiptoken=ABC123",
          "value": [
            {
              "id": "01FOLDERID",
              "name": "Pictures",
              "eTag": "\"{11111111-1111-1111-1111-111111111111},1\"",
              "lastModifiedDateTime": "2024-01-15T10:30:00Z",
              "createdDateTime": "2023-11-02T08:00:00Z",
              "webUrl": "https://onedrive.live.com/redir?resid=ABC",
              "cTag": "\"c:{22222222-2222-2222-2222-222222222222},0\"",
              "folder": { "childCount": 12, "view": { "sortBy": "default" } },
              "parentReference": {
                "driveId": "b!xyz",
                "driveType": "personal",
                "id": "01ROOTID",
                "path": "/drive/root:"
              },
              "fileSystemInfo": {
                "createdDateTime": "2023-11-02T08:00:00Z",
                "lastModifiedDateTime": "2024-01-15T10:30:00Z"
              }
            },
            {
              "id": "01FILEID",
              "name": "IMG_0042.jpg",
              "size": 2481152,
              "eTag": "\"{33333333-3333-3333-3333-333333333333},3\"",
              "lastModifiedDateTime": "2024-02-01T18:45:12Z",
              "createdDateTime": "2024-02-01T18:45:12Z",
              "file": {
                "mimeType": "image/jpeg",
                "hashes": { "quickXorHash": "abcdef==" }
              },
              "image": { "width": 4032, "height": 3024 },
              "photo": {
                "takenDateTime": "2024-02-01T18:45:12Z",
                "cameraMake": "Samsung",
                "cameraModel": "SM-S928B"
              },
              "parentReference": {
                "driveId": "b!xyz",
                "id": "01FOLDERID",
                "path": "/drive/root:/Pictures"
              }
            }
          ]
        }
    """.trimIndent()

    @Test
    fun `a realistic children payload deserializes`() {
        val response = json.decodeFromString<GraphChildrenResponseDto>(realisticPayload)

        assertEquals(2, response.value.size)
    }

    @Test
    fun `the odata nextLink serial name maps onto nextLink`() {
        val response = json.decodeFromString<GraphChildrenResponseDto>(realisticPayload)

        assertEquals(
            "https://graph.microsoft.com/v1.0/me/drive/root/children?%24skiptoken=ABC123",
            response.nextLink
        )
    }

    @Test
    fun `unknown top-level and nested fields are ignored`() {
        // "@odata.context", "webUrl", "cTag", "fileSystemInfo", "photo", "hashes", "driveId" and
        // "view" are all absent from the DTOs. Strict parsing would throw here.
        val response = json.decodeFromString<GraphChildrenResponseDto>(realisticPayload)

        assertEquals("Pictures", response.value[0].name)
        assertEquals("IMG_0042.jpg", response.value[1].name)
    }

    @Test
    fun `a brand new unknown field does not break parsing`() {
        val payload = """
            {
              "value": [
                {
                  "id": "01NEW",
                  "name": "future.jpg",
                  "file": { "mimeType": "image/jpeg" },
                  "someFieldMicrosoftAddedLastTuesday": { "nested": [1, 2, 3] }
                }
              ]
            }
        """.trimIndent()

        val response = json.decodeFromString<GraphChildrenResponseDto>(payload)

        assertEquals(1, response.value.size)
        assertEquals("01NEW", response.value[0].id)
    }

    @Test
    fun `the folder facet is populated and the file facet is absent for a folder`() {
        val folder = json.decodeFromString<GraphChildrenResponseDto>(realisticPayload).value[0]

        assertEquals(12, folder.folder?.childCount)
        assertNull(folder.file)
        assertNull(folder.image)
        assertEquals("/drive/root:", folder.parentReference?.path)
    }

    @Test
    fun `the file and image facets are populated and the folder facet is absent for a file`() {
        val file = json.decodeFromString<GraphChildrenResponseDto>(realisticPayload).value[1]

        assertEquals("image/jpeg", file.file?.mimeType)
        assertEquals(4032, file.image?.width)
        assertEquals(3024, file.image?.height)
        assertEquals(2_481_152L, file.size)
        assertNull(file.folder)
    }

    @Test
    fun `a payload with no nextLink yields a null nextLink`() {
        val payload = """{ "value": [] }"""

        val response = json.decodeFromString<GraphChildrenResponseDto>(payload)

        assertNull(response.nextLink)
        assertTrue(response.value.isEmpty())
    }

    @Test
    fun `a payload with no value array yields an empty list`() {
        // Graph omits `value` on some empty responses; the default must absorb that.
        val response = json.decodeFromString<GraphChildrenResponseDto>("""{}""")

        assertTrue(response.value.isEmpty())
        assertNull(response.nextLink)
    }

    @Test
    fun `every absent driveItem field falls back to null`() {
        val response = json.decodeFromString<GraphChildrenResponseDto>(
            """{ "value": [ { "id": "01BARE", "name": "bare" } ] }"""
        )
        val item = response.value.single()

        assertNull(item.size)
        assertNull(item.eTag)
        assertNull(item.lastModifiedDateTime)
        assertNull(item.createdDateTime)
        assertNull(item.file)
        assertNull(item.folder)
        assertNull(item.image)
        assertNull(item.parentReference)
    }
}
