package com.gallery.sync.data.remote.cloud

import com.gallery.sync.domain.model.DataResult
import com.gallery.sync.domain.model.RemoteError
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
    fun `an expired token is Unauthorized, and no token means no download`() = runTest {
        server.enqueue(MockResponse().setResponseCode(401).setBody("""{"error_summary":"expired_access_token"}"""))
        assertEquals(RemoteError.Unauthorized, (dropbox().openStream("id:X") as DataResult.Failure).error)

        whenever(tokens.accessToken(any())).thenReturn(null)
        assertEquals(RemoteError.NoToken, (dropbox().openStream("id:X") as DataResult.Failure).error)
    }
}
