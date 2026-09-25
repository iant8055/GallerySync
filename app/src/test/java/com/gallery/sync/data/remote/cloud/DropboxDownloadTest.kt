package com.gallery.sync.data.remote.cloud

import com.gallery.sync.domain.model.DataResult
import com.gallery.sync.domain.model.RemoteError
import com.gallery.sync.domain.repository.RemoteCheck
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/** Restore from Dropbox against a local server. No account is involved. */
@OptIn(ExperimentalCoroutinesApi::class)
class DropboxDownloadTest {

    private val server = MockWebServer()
    private val tokens: AppAuthCloudTokens = mock()
    private val configs: CloudOAuthConfigs = mock()
    private val signIn: AppAuthCloudSignIn = mock()
    private val dispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() = runTest {
        server.start()
        whenever(tokens.accessToken(any())).thenReturn("TOKEN")
    }

    @After
    fun tearDown() = server.shutdown()

    private fun dropbox() = DropboxCloud(configs, signIn, tokens, OkHttpClient(), dispatcher)
        .also { it.contentBase = "http://${server.hostName}:${server.port}" }

    @Test
    fun `it opens the file by the id recorded at upload, with the token`() = runTest {
        val bytes = ByteArray(2_000) { (it % 250).toByte() }
        server.enqueue(MockResponse().setBody(okio.Buffer().write(bytes)))

        val result = dropbox().openStream("id:AbC123")

        assertTrue(result.toString(), result is DataResult.Success)
        val read = (result as DataResult.Success).value.use { it.readBytes() }
        assertArrayEquals(bytes, read)

        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/2/files/download", request.path)
        assertEquals("Bearer TOKEN", request.getHeader("Authorization"))
        assertEquals("""{"path":"id:AbC123"}""", request.getHeader("Dropbox-API-Arg"))
    }

    @Test
    fun `a file that is gone from Dropbox is reported as not found`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(409)
                .setBody("""{"error_summary":"path/not_found/..","error":{".tag":"path"}}""")
        )

        val result = dropbox().openStream("id:GONE")

        assertEquals(404, ((result as DataResult.Failure).error as RemoteError.Http).code)
    }

    @Test
    fun `a sign-in without the read scope is reported as Unauthorized, not as a network problem`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(400).setBody(
                """{"error_summary":"other/...","user_message":{"text":"not permitted ... does not have the required scope 'files.content.read'"}}"""
            )
        )
        assertEquals(RemoteError.Unauthorized, (dropbox().openStream("id:X") as DataResult.Failure).error)
    }

    @Test
    fun `Google Drive opens a file by its id with alt media`() = runTest {
        val drive = GoogleDriveCloud(configs, signIn, tokens, OkHttpClient(), dispatcher)
            .also { it.apiBase = "http://${server.hostName}:${server.port}" }
        val bytes = ByteArray(1_500) { (it % 240).toByte() }
        server.enqueue(MockResponse().setBody(okio.Buffer().write(bytes)))

        val result = drive.openStream("FILE1")

        assertTrue(result.toString(), result is DataResult.Success)
        assertArrayEquals(bytes, (result as DataResult.Success).value.use { it.readBytes() })
        val request = server.takeRequest()
        assertEquals("GET", request.method)
        assertEquals("/drive/v3/files/FILE1?alt=media", request.path)
        assertEquals("Bearer TOKEN", request.getHeader("Authorization"))
    }

    @Test
    fun `Google Drive reports a deleted file as 404`() = runTest {
        val drive = GoogleDriveCloud(configs, signIn, tokens, OkHttpClient(), dispatcher)
            .also { it.apiBase = "http://${server.hostName}:${server.port}" }
        server.enqueue(MockResponse().setResponseCode(404).setBody("""{"error":{"code":404}}"""))
        assertEquals(404, ((drive.openStream("GONE") as DataResult.Failure).error as RemoteError.Http).code)
    }

    @Test
    fun `Dropbox reports a stored file's size from the download metadata header`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(206)
                .setHeader("Dropbox-API-Result", """{"name":"a.jpg","id":"id:A","size":4001494}""")
                .setBody("x")
        )

        val check = dropbox().sizeOf("id:A")

        assertEquals(RemoteCheck.Present(4_001_494L), check)
        val request = server.takeRequest()
        assertEquals("bytes=0-0", request.getHeader("Range"))
        assertEquals("""{"path":"id:A"}""", request.getHeader("Dropbox-API-Arg"))
    }

    @Test
    fun `Dropbox says Gone for a deleted file and Unknown for anything unclear`() = runTest {
        val unknown = RemoteCheck.Unknown
        server.enqueue(MockResponse().setResponseCode(409).setBody("""{"error_summary":"path/not_found/.."}"""))
        assertEquals(RemoteCheck.Gone, dropbox().sizeOf("id:GONE"))

        server.enqueue(MockResponse().setResponseCode(500).setBody("oops"))
        assertEquals(unknown, dropbox().sizeOf("id:X"))

        server.enqueue(MockResponse().setResponseCode(206).setBody("x")) // no metadata header at all
        assertEquals(unknown, dropbox().sizeOf("id:X"))

        server.enqueue(MockResponse().setResponseCode(409).setBody("""{"error_summary":"path/restricted_content/.."}"""))
        assertEquals(unknown, dropbox().sizeOf("id:X"))

        whenever(tokens.accessToken(any())).thenReturn(null)
        assertEquals(unknown, dropbox().sizeOf("id:X"))
    }

    @Test
    fun `Google Drive reports size, treats trash as gone, and says Unknown when unsure`() = runTest {
        val drive = GoogleDriveCloud(configs, signIn, tokens, OkHttpClient(), dispatcher)
            .also { it.apiBase = "http://${server.hostName}:${server.port}" }

        server.enqueue(MockResponse().setBody("""{"size":"6807605","trashed":false}"""))
        assertEquals(RemoteCheck.Present(6_807_605L), drive.sizeOf("FILE1"))
        assertEquals("/drive/v3/files/FILE1?fields=size%2Ctrashed", server.takeRequest().path)

        server.enqueue(MockResponse().setBody("""{"size":"6807605","trashed":true}"""))
        assertEquals(RemoteCheck.Gone, drive.sizeOf("FILE1"))

        server.enqueue(MockResponse().setResponseCode(404).setBody("""{"error":{"code":404}}"""))
        assertEquals(RemoteCheck.Gone, drive.sizeOf("FILE1"))

        server.enqueue(MockResponse().setResponseCode(500).setBody("oops"))
        assertEquals(RemoteCheck.Unknown, drive.sizeOf("FILE1"))

        server.enqueue(MockResponse().setBody("""{"trashed":false}""")) // no size reported
        assertEquals(RemoteCheck.Unknown, drive.sizeOf("FILE1"))
    }

    @Test
    fun `an expired token is Unauthorized, and no token means no download`() = runTest {
        server.enqueue(MockResponse().setResponseCode(401).setBody("""{"error_summary":"expired_access_token"}"""))
        assertEquals(RemoteError.Unauthorized, (dropbox().openStream("id:X") as DataResult.Failure).error)

        whenever(tokens.accessToken(any())).thenReturn(null)
        assertEquals(RemoteError.NoToken, (dropbox().openStream("id:X") as DataResult.Failure).error)
    }
}
