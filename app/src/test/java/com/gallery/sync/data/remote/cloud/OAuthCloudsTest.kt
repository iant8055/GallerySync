package com.gallery.sync.data.remote.cloud

import com.gallery.sync.domain.backup.BackupLocation
import com.gallery.sync.domain.model.DataResult
import com.gallery.sync.domain.model.RemoteError
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/** Google Drive, Dropbox and pCloud against a local server. No account is involved. */
@OptIn(ExperimentalCoroutinesApi::class)
class OAuthCloudsTest {

    private val server = MockWebServer()
    private val tokens: AppAuthCloudTokens = mock()
    private val configs: CloudOAuthConfigs = mock()
    private val signIn: AppAuthCloudSignIn = mock()
    private val secrets: EncryptedCloudSecretsStore = mock()
    private val dispatcher = UnconfinedTestDispatcher()
    private val client = OkHttpClient()
    private val bytes = ByteArray(3_000) { (it % 200).toByte() }
    private val source = TestUploadSource("Café 1.jpg", bytes)

    @Before
    fun setUp() = runTest {
        server.start()
        whenever(tokens.accessToken(any())).thenReturn("TOKEN")
    }

    @After
    fun tearDown() = server.shutdown()

    private fun base() = "http://${server.hostName}:${server.port}"

    // ---- Google Drive ----

    private fun drive() = GoogleDriveCloud(configs, signIn, tokens, client, dispatcher).also { it.apiBase = base() }

    @Test
    fun `drive finds or creates GallerySync and the album, then uploads in a resumable session`() = runTest {
        server.enqueue(MockResponse().setBody("""{"files":[]}"""))
        server.enqueue(MockResponse().setBody("""{"id":"ROOT"}"""))
        server.enqueue(MockResponse().setBody("""{"files":[{"id":"ALBUM"}]}"""))
        server.enqueue(MockResponse().setHeader("Location", "${base()}/session/1"))
        server.enqueue(MockResponse().setBody("""{"id":"FILE1","size":"3000"}"""))

        val result = drive().upload(source, "Car Show")

        assertTrue(result.toString(), result is DataResult.Success)
        assertEquals("FILE1", (result as DataResult.Success).value.id)

        server.takeRequest()
        val createRoot = server.takeRequest()
        assertTrue(createRoot.body.readUtf8().contains("\"name\":\"GallerySync\""))
        server.takeRequest()
        val open = server.takeRequest()
        assertEquals("Bearer TOKEN", open.getHeader("Authorization"))
        assertEquals("3000", open.getHeader("X-Upload-Content-Length"))
        val metadata = open.body.readUtf8()
        assertTrue(metadata, metadata.contains("\"parents\":[\"ALBUM\"]"))
        assertTrue(metadata, metadata.contains("Caf\\u00e9 1.jpg"))
        val put = server.takeRequest()
        assertEquals("PUT", put.method)
        assertEquals("/session/1", put.path)
        assertEquals(3_000L, put.bodySize)
    }

    @Test
    fun `drive maps 401 to Unauthorized`() = runTest {
        server.enqueue(MockResponse().setResponseCode(401))
        assertEquals(DataResult.Failure(RemoteError.Unauthorized), drive().upload(source, "A"))
    }

    @Test
    fun `drive maps a full Drive to InsufficientStorage`() = runTest {
        server.enqueue(MockResponse().setBody("""{"files":[{"id":"R"}]}"""))
        server.enqueue(MockResponse().setBody("""{"files":[{"id":"A"}]}"""))
        server.enqueue(
            MockResponse().setResponseCode(403)
                .setBody("""{"error":{"errors":[{"reason":"storageQuotaExceeded"}]}}""")
        )
        assertEquals(DataResult.Failure(RemoteError.InsufficientStorage), drive().upload(source, "A"))
    }

    // ---- Dropbox ----

    private fun dropbox() = DropboxCloud(configs, signIn, tokens, client, dispatcher).also { it.contentBase = base() }

    @Test
    fun `dropbox sends a small file in one request with an ASCII-safe API arg`() = runTest {
        server.enqueue(MockResponse().setBody("""{"id":"id:abc","size":3000}"""))

        val result = dropbox().upload(source, "Car Show")

        assertTrue(result is DataResult.Success)
        val request = server.takeRequest()
        assertEquals("/2/files/upload", request.path)
        assertEquals("Bearer TOKEN", request.getHeader("Authorization"))
        val arg = request.getHeader("Dropbox-API-Arg")!!
        assertTrue(arg, arg.contains("/GallerySync/Car Show/Caf\\u00e9 1.jpg"))
        assertTrue(arg, arg.contains("\"mode\":\"overwrite\""))
        assertEquals(3_000L, request.bodySize)
    }

    @Test
    fun `dropbox sends a large file through an upload session in chunks`() = runTest {
        val cloud = dropbox().also {
            it.singleRequestLimit = 1_000L
            it.chunkBytes = 1_024
        }
        server.enqueue(MockResponse().setBody("""{"session_id":"SID"}"""))
        repeat(3) { server.enqueue(MockResponse().setBody("null")) }
        server.enqueue(MockResponse().setBody("""{"id":"id:big","size":3000}"""))

        val result = cloud.upload(source, "Car Show")

        assertTrue(result.toString(), result is DataResult.Success)
        assertEquals("/2/files/upload_session/start", server.takeRequest().path)
        val offsets = (1..3).map {
            val request = server.takeRequest()
            assertEquals("/2/files/upload_session/append_v2", request.path)
            request.getHeader("Dropbox-API-Arg")!!
        }
        assertTrue(offsets[0].contains("\"offset\":0"))
        assertTrue(offsets[1].contains("\"offset\":1024"))
        assertTrue(offsets[2].contains("\"offset\":2048"))
        val finish = server.takeRequest()
        assertEquals("/2/files/upload_session/finish", finish.path)
        assertTrue(finish.getHeader("Dropbox-API-Arg")!!.contains("\"offset\":3000"))
    }

    @Test
    fun `dropbox maps insufficient space to InsufficientStorage`() = runTest {
        server.enqueue(MockResponse().setResponseCode(409).setBody("""{"error_summary":"insufficient_space/.."}"""))
        assertEquals(DataResult.Failure(RemoteError.InsufficientStorage), dropbox().upload(source, "A"))
    }

    // ---- pCloud ----

    private fun pcloud() =
        PCloudCloud(configs, secrets, mock(), client, dispatcher).also { it.baseOverride = base() }

    @Test
    fun `pcloud creates the folders then uploads a multipart file`() = runTest {
        whenever(secrets.read(PCloudCloud.KEY_TOKEN)).thenReturn("PTOKEN")
        server.enqueue(MockResponse().setBody("""{"result":0}"""))
        server.enqueue(MockResponse().setBody("""{"result":0}"""))
        server.enqueue(MockResponse().setBody("""{"result":0,"fileids":[42],"metadata":[]}"""))

        val result = pcloud().upload(source, "Car Show")

        assertTrue(result.toString(), result is DataResult.Success)
        assertEquals("42", (result as DataResult.Success).value.id)
        assertTrue(server.takeRequest().path!!.startsWith("/createfolderifnotexists?path=%2FGallerySync&access_token=PTOKEN"))
        assertTrue(server.takeRequest().path!!.contains("path=%2FGallerySync%2FCar%20Show"))
        val upload = server.takeRequest()
        assertTrue(upload.path!!.startsWith("/uploadfile?"))
        assertTrue(upload.path!!.contains("nopartial=1"))
        assertTrue(upload.getHeader("Content-Type")!!.startsWith("multipart/form-data"))
    }

    @Test
    fun `pcloud needs a token`() = runTest {
        whenever(secrets.read(PCloudCloud.KEY_TOKEN)).thenReturn(null)
        assertEquals(DataResult.Failure(RemoteError.NoToken), pcloud().upload(source, "A"))
    }

    @Test
    fun `pcloud maps its login-required result code to Unauthorized`() = runTest {
        whenever(secrets.read(PCloudCloud.KEY_TOKEN)).thenReturn("PTOKEN")
        server.enqueue(MockResponse().setBody("""{"result":2000,"error":"Log in required."}"""))
        assertEquals(DataResult.Failure(RemoteError.Unauthorized), pcloud().upload(source, "A"))
    }

    @Test
    fun `pcloud parses a redirect fragment`() {
        val params = PCloudCloud.parseParams("access_token=abc%2B1&token_type=bearer&state=s1&hostname=eapi.pcloud.com")
        assertEquals("abc+1", params["access_token"])
        assertEquals("eapi.pcloud.com", params["hostname"])
        assertEquals(BackupLocation.PCLOUD, pcloud().location)
    }

    // ---- shared ----

    @Test
    fun `a provider with no app id is not offered`() {
        whenever(configs.clientFor(any())).thenReturn(null)
        assertFalse(drive().isOfferedInThisBuild)
        assertFalse(dropbox().isOfferedInThisBuild)
        assertFalse(pcloud().isOfferedInThisBuild)
    }

    @Test
    fun `non-ASCII is escaped for a header`() {
        assertEquals("\"Caf\\u00e9 \\\"1\\\"\"", CloudHttp.asciiJsonString("Café \"1\""))
    }
}
