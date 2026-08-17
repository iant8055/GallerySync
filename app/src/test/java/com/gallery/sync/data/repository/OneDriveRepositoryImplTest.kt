package com.gallery.sync.data.repository

import com.gallery.sync.data.remote.auth.OneDriveTokenProvider
import com.gallery.sync.data.remote.onedrive.GraphApiService
import com.gallery.sync.data.remote.onedrive.dto.GraphChildrenResponseDto
import com.gallery.sync.data.remote.onedrive.dto.GraphDriveItemDto
import com.gallery.sync.data.remote.onedrive.dto.GraphFileFacetDto
import com.gallery.sync.data.remote.onedrive.dto.GraphFolderFacetDto
import com.gallery.sync.domain.model.DataResult
import com.gallery.sync.domain.model.FolderPage
import com.gallery.sync.domain.model.RemoteError
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import retrofit2.Response
import java.io.IOException

/**
 * Unit tests for the app's only OneDrive network boundary.
 *
 * NOTE on mocking [GraphApiService]: its methods carry Kotlin default arguments, which are resolved
 * at the *call site*, so the mock always sees a fully-applied argument list. Every stub therefore
 * has to use explicit matchers — `whenever(api.listRootChildren(any(), any()))`. A no-arg stub
 * silently fails to match and the mock returns null.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class OneDriveRepositoryImplTest {

    private val api: GraphApiService = mock()
    private val tokenProvider: OneDriveTokenProvider = mock()

    private val folderDto = GraphDriveItemDto(
        id = "01FOLDER",
        name = "Pictures",
        lastModifiedDateTime = "2024-01-15T10:30:00Z",
        folder = GraphFolderFacetDto(childCount = 3)
    )

    private val fileDto = GraphDriveItemDto(
        id = "01FILE",
        name = "IMG_0042.jpg",
        size = 1024L,
        lastModifiedDateTime = "2024-01-15T10:30:00Z",
        file = GraphFileFacetDto(mimeType = "image/jpeg")
    )

    /** A driveItem with neither facet — Graph sends these and the repository must drop them. */
    private val packageDto = GraphDriveItemDto(id = "01PKG", name = "Notebook")

    private fun TestRepository() = OneDriveRepositoryImpl(
        api = api,
        tokenProvider = tokenProvider,
        dispatcher = UnconfinedTestDispatcher()
    )

    // ---------- happy path ----------

    @Test
    fun `listRoot returns Success with the mapped nodes`() = runTest {
        givenToken("valid-token")
        whenever(api.listRootChildren(any(), any()))
            .thenReturn(Response.success(GraphChildrenResponseDto(value = listOf(folderDto, fileDto))))

        val result = TestRepository().listRoot()

        val page = (result as DataResult.Success<FolderPage>).value
        assertEquals(2, page.nodes.size)
        assertEquals(listOf("Pictures", "IMG_0042.jpg"), page.nodes.map { it.name })
    }

    @Test
    fun `listRoot drops driveItems the mapper cannot represent`() = runTest {
        givenToken("valid-token")
        whenever(api.listRootChildren(any(), any())).thenReturn(
            Response.success(
                GraphChildrenResponseDto(value = listOf(folderDto, packageDto, fileDto))
            )
        )

        val page = (TestRepository().listRoot() as DataResult.Success<FolderPage>).value

        assertEquals(2, page.nodes.size)
        assertTrue(page.nodes.none { it.id == "01PKG" })
    }

    @Test
    fun `listFolder passes the folder id through to the api`() = runTest {
        givenToken("valid-token")
        whenever(api.listChildren(any(), any(), any()))
            .thenReturn(Response.success(GraphChildrenResponseDto(value = listOf(fileDto))))

        val result = TestRepository().listFolder("01FOLDER")

        assertTrue(result is DataResult.Success)
        verify(api).listChildren(eq("01FOLDER"), any(), any())
    }

    @Test
    fun `listNextPage passes the continuation link through verbatim`() = runTest {
        val nextLink = "https://graph.microsoft.com/v1.0/me/drive/root/children?%24skiptoken=ABC"
        givenToken("valid-token")
        whenever(api.listNextPage(any()))
            .thenReturn(Response.success(GraphChildrenResponseDto(value = listOf(fileDto))))

        val result = TestRepository().listNextPage(nextLink)

        assertTrue(result is DataResult.Success)
        verify(api).listNextPage(eq(nextLink))
    }

    // ---------- paging cursor ----------

    @Test
    fun `an odata nextLink is surfaced as the nextPageToken`() = runTest {
        val nextLink = "https://graph.microsoft.com/v1.0/me/drive/root/children?%24skiptoken=ABC"
        givenToken("valid-token")
        whenever(api.listRootChildren(any(), any())).thenReturn(
            Response.success(
                GraphChildrenResponseDto(value = listOf(fileDto), nextLink = nextLink)
            )
        )

        val page = (TestRepository().listRoot() as DataResult.Success<FolderPage>).value

        assertEquals(nextLink, page.nextPageToken)
    }

    @Test
    fun `an absent odata nextLink yields a null nextPageToken`() = runTest {
        givenToken("valid-token")
        whenever(api.listRootChildren(any(), any())).thenReturn(
            Response.success(GraphChildrenResponseDto(value = listOf(fileDto), nextLink = null))
        )

        val page = (TestRepository().listRoot() as DataResult.Success<FolderPage>).value

        assertNull(page.nextPageToken)
    }

    @Test
    fun `paging can be walked to completion`() = runTest {
        val nextLink = "https://graph.microsoft.com/v1.0/me/drive/root/children?%24skiptoken=ABC"
        givenToken("valid-token")
        whenever(api.listRootChildren(any(), any())).thenReturn(
            Response.success(GraphChildrenResponseDto(value = listOf(folderDto), nextLink = nextLink))
        )
        whenever(api.listNextPage(eq(nextLink))).thenReturn(
            Response.success(GraphChildrenResponseDto(value = listOf(fileDto), nextLink = null))
        )

        val repository = TestRepository()
        val first = (repository.listRoot() as DataResult.Success<FolderPage>).value
        val second =
            (repository.listNextPage(first.nextPageToken!!) as DataResult.Success<FolderPage>).value

        assertEquals(listOf("Pictures"), first.nodes.map { it.name })
        assertEquals(listOf("IMG_0042.jpg"), second.nodes.map { it.name })
        assertNull(second.nextPageToken)
    }

    // ---------- empty results ----------

    @Test
    fun `an empty value array is a Success with no nodes, not a failure`() = runTest {
        givenToken("valid-token")
        whenever(api.listRootChildren(any(), any()))
            .thenReturn(Response.success(GraphChildrenResponseDto(value = emptyList())))

        val result = TestRepository().listRoot()

        val page = (result as DataResult.Success<FolderPage>).value
        assertTrue(page.nodes.isEmpty())
        assertNull(page.nextPageToken)
    }

    @Test
    fun `a page of only unmappable items is a Success with no nodes`() = runTest {
        givenToken("valid-token")
        whenever(api.listRootChildren(any(), any()))
            .thenReturn(Response.success(GraphChildrenResponseDto(value = listOf(packageDto))))

        val page = (TestRepository().listRoot() as DataResult.Success<FolderPage>).value

        assertTrue(page.nodes.isEmpty())
    }

    // ---------- no token ----------

    @Test
    fun `no token short-circuits to NoToken without touching the network`() = runTest {
        givenToken(null)

        val result = TestRepository().listRoot()

        assertEquals(DataResult.Failure(RemoteError.NoToken), result)
        verify(api, never()).listRootChildren(any(), any())
        verifyNoInteractions(api)
    }

    @Test
    fun `no token short-circuits listFolder without touching the network`() = runTest {
        givenToken(null)

        val result = TestRepository().listFolder("01FOLDER")

        assertEquals(DataResult.Failure(RemoteError.NoToken), result)
        verifyNoInteractions(api)
    }

    @Test
    fun `no token short-circuits listNextPage without touching the network`() = runTest {
        givenToken(null)

        val result = TestRepository().listNextPage("https://graph.microsoft.com/next")

        assertEquals(DataResult.Failure(RemoteError.NoToken), result)
        verifyNoInteractions(api)
    }

    @Test
    fun `no token does not invalidate the stored token`() = runTest {
        // There is nothing to invalidate, and doing so would churn the encrypted store on every
        // call made while signed out.
        givenToken(null)

        TestRepository().listRoot()

        verify(tokenProvider, never()).invalidateAccessToken()
    }

    // ---------- 401 ----------

    @Test
    fun `a 401 maps to Unauthorized`() = runTest {
        givenToken("stale-token")
        whenever(api.listRootChildren(any(), any())).thenReturn(errorResponse(401, """{"error":"invalid"}"""))

        val result = TestRepository().listRoot()

        assertEquals(DataResult.Failure(RemoteError.Unauthorized), result)
    }

    @Test
    fun `a 401 invalidates the stored access token`() = runTest {
        givenToken("stale-token")
        whenever(api.listRootChildren(any(), any())).thenReturn(errorResponse(401, """{"error":"invalid"}"""))

        TestRepository().listRoot()

        verify(tokenProvider).invalidateAccessToken()
    }

    @Test
    fun `a 401 on listNextPage also invalidates the stored access token`() = runTest {
        givenToken("stale-token")
        whenever(api.listNextPage(any())).thenReturn(errorResponse(401, ""))

        val result = TestRepository().listNextPage("https://graph.microsoft.com/next")

        assertEquals(DataResult.Failure(RemoteError.Unauthorized), result)
        verify(tokenProvider).invalidateAccessToken()
    }

    // ---------- other HTTP failures ----------

    @Test
    fun `a 500 maps to Http with the code and the error body`() = runTest {
        givenToken("valid-token")
        whenever(api.listRootChildren(any(), any()))
            .thenReturn(errorResponse(500, """{"error":{"code":"serviceNotAvailable"}}"""))

        val result = TestRepository().listRoot()

        assertEquals(
            DataResult.Failure(
                RemoteError.Http(500, """{"error":{"code":"serviceNotAvailable"}}""")
            ),
            result
        )
    }

    @Test
    fun `a 500 does not invalidate the stored access token`() = runTest {
        // Only a 401 means the token is bad. Clearing it on a server outage would sign the user out.
        givenToken("valid-token")
        whenever(api.listRootChildren(any(), any())).thenReturn(errorResponse(500, "boom"))

        TestRepository().listRoot()

        verify(tokenProvider, never()).invalidateAccessToken()
    }

    @Test
    fun `a 403 maps to Http rather than Unauthorized`() = runTest {
        givenToken("valid-token")
        whenever(api.listRootChildren(any(), any())).thenReturn(errorResponse(403, "forbidden"))

        val result = TestRepository().listRoot()

        assertEquals(DataResult.Failure(RemoteError.Http(403, "forbidden")), result)
    }

    @Test
    fun `a 429 maps to Http so a caller can back off`() = runTest {
        givenToken("valid-token")
        whenever(api.listRootChildren(any(), any())).thenReturn(errorResponse(429, "throttled"))

        val result = TestRepository().listRoot()

        assertEquals(DataResult.Failure(RemoteError.Http(429, "throttled")), result)
    }

    // ---------- transport failures ----------

    @Test
    fun `an IOException maps to Network`() = runTest {
        givenToken("valid-token")
        // thenAnswer, not thenThrow: Mockito rejects a checked exception that the mocked method's
        // Java signature does not declare, and Kotlin declares none.
        whenever(api.listRootChildren(any(), any())).thenAnswer { throw IOException("offline") }

        val result = TestRepository().listRoot()

        assertEquals(DataResult.Failure(RemoteError.Network), result)
    }

    @Test
    fun `a socket timeout maps to Network`() = runTest {
        givenToken("valid-token")
        whenever(api.listRootChildren(any(), any()))
            .thenAnswer { throw java.net.SocketTimeoutException("timeout") }

        val result = TestRepository().listRoot()

        assertEquals(DataResult.Failure(RemoteError.Network), result)
    }

    @Test
    fun `an unexpected throwable maps to Unknown carrying the cause`() = runTest {
        val boom = IllegalStateException("serializer exploded")
        givenToken("valid-token")
        whenever(api.listRootChildren(any(), any())).thenAnswer { throw boom }

        val result = TestRepository().listRoot()

        assertEquals(DataResult.Failure(RemoteError.Unknown(boom)), result)
    }

    // ---------- helpers ----------

    private suspend fun givenToken(token: String?) {
        whenever(tokenProvider.getAccessToken()).thenReturn(token)
    }

    private fun errorResponse(code: Int, body: String): Response<GraphChildrenResponseDto> =
        Response.error(code, body.toResponseBody("application/json".toMediaType()))
}
