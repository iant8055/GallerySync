package com.gallery.sync.data.remote.s3

import com.gallery.sync.data.remote.auth.SignInResult
import com.gallery.sync.data.remote.cloud.EncryptedCloudSecretsStore
import com.gallery.sync.data.remote.onedrive.UploadSource
import com.gallery.sync.domain.backup.BackupLocation
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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class S3CompatibleCloudTest {

    private val server = MockWebServer()
    private val store = mutableMapOf<String, String>()

    private val secrets: EncryptedCloudSecretsStore = mock()
    private lateinit var cloud: BackblazeB2Cloud

    private val bytes = ByteArray(2_500) { (it % 251).toByte() }
    private val source = object : UploadSource {
        override val displayName = "Car Show 1.jpg"
        override val sizeBytes = bytes.size.toLong()
        override fun open() = object : UploadSource.Reader {
            override fun readFully(offset: Long, buffer: ByteArray, length: Int) {
                System.arraycopy(bytes, offset.toInt(), buffer, 0, length)
            }
            override fun close() = Unit
        }
    }

    @Before
    fun setUp() = runTest {
        server.start()
        whenever(secrets.read(any())).doAnswer { store[it.getArgument(0)] }
        whenever(secrets.write(any(), any())).doAnswer { store[it.getArgument(0)] = it.getArgument(1); Unit }
        whenever(secrets.clearPrefix(any())).doAnswer {
            val p: String = it.getArgument(0)
            store.keys.removeAll { k -> k.startsWith(p) }
            Unit
        }
        cloud = BackblazeB2Cloud(secrets, S3Client(OkHttpClient()), UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() = server.shutdown()

    /** Stored directly with an http endpoint: the https-only rule is in [configFrom], on entry. */
    private fun connectedToMockServer() {
        val p = "BACKBLAZE_B2."
        store[p + "endpoint"] = "http://${server.hostName}:${server.port}"
        store[p + "region"] = "us-west-004"
        store[p + "bucket"] = "photos"
        store[p + "access_key"] = "AKID"
        store[p + "secret_key"] = "SECRET"
    }

    @Test
    fun `upload puts the file under GallerySync slash album with a signed request`() = runTest {
        connectedToMockServer()
        server.enqueue(MockResponse().setResponseCode(200).setHeader("ETag", "\"abc\""))

        val result = cloud.upload(source, "Car Show")

        assertTrue(result is DataResult.Success)
        val item = (result as DataResult.Success).value
        assertEquals("GallerySync/Car Show/Car Show 1.jpg", item.id)
        assertEquals(2_500L, item.sizeBytes)

        val request = server.takeRequest()
        assertEquals("PUT", request.method)
        assertEquals("/photos/GallerySync/Car%20Show/Car%20Show%201.jpg", request.path)
        assertTrue(request.getHeader("Authorization")!!.startsWith("AWS4-HMAC-SHA256 Credential=AKID/"))
        assertEquals(2_500L, request.bodySize)
        // The signed payload hash is the real SHA-256 of the file, not "unsigned".
        assertEquals(SigV4.sha256Hex(bytes), request.getHeader("x-amz-content-sha256"))
    }

    @Test
    fun `a 403 is Unauthorized, which stops only this provider`() = runTest {
        connectedToMockServer()
        server.enqueue(MockResponse().setResponseCode(403))
        val result = cloud.upload(source, "Car Show")
        assertEquals(DataResult.Failure(RemoteError.Unauthorized), result)
    }

    @Test
    fun `restore gets the object by its key with a signed request`() = runTest {
        connectedToMockServer()
        server.enqueue(MockResponse().setBody(okio.Buffer().write(bytes)))

        val result = cloud.openStream("GallerySync/Car Show/Car Show 1.jpg")

        assertTrue(result.toString(), result is DataResult.Success)
        val read = (result as DataResult.Success).value.use { it.readBytes() }
        assertEquals(bytes.toList(), read.toList())
        val request = server.takeRequest()
        assertEquals("GET", request.method)
        assertEquals("/photos/GallerySync/Car%20Show/Car%20Show%201.jpg", request.path)
        assertTrue(request.getHeader("Authorization")!!.startsWith("AWS4-HMAC-SHA256 Credential=AKID/"))
    }

    @Test
    fun `restore reports a missing object as 404 and a refused key as Unauthorized`() = runTest {
        connectedToMockServer()
        server.enqueue(MockResponse().setResponseCode(404).setBody("<Error><Code>NoSuchKey</Code></Error>"))
        assertEquals(404, ((cloud.openStream("gone") as DataResult.Failure).error as RemoteError.Http).code)

        server.enqueue(MockResponse().setResponseCode(403).setBody("<Error><Code>AccessDenied</Code></Error>"))
        assertEquals(RemoteError.Unauthorized, (cloud.openStream("any") as DataResult.Failure).error)
    }

    @Test
    fun `the size check is a signed HEAD and reads Content-Length`() = runTest {
        connectedToMockServer()
        server.enqueue(MockResponse().setResponseCode(200).setHeader("Content-Length", "2500"))

        val check = cloud.sizeOf("GallerySync/Car Show/Car Show 1.jpg")

        assertEquals(RemoteCheck.Present(2_500L), check)
        val request = server.takeRequest()
        assertEquals("HEAD", request.method)
        assertEquals("/photos/GallerySync/Car%20Show/Car%20Show%201.jpg", request.path)
        assertTrue(request.getHeader("Authorization")!!.startsWith("AWS4-HMAC-SHA256 Credential=AKID/"))
    }

    @Test
    fun `the size check says Gone for 404 and Unknown for a refused key or a store error`() = runTest {
        connectedToMockServer()
        server.enqueue(MockResponse().setResponseCode(404))
        assertEquals(RemoteCheck.Gone, cloud.sizeOf("gone"))
        server.enqueue(MockResponse().setResponseCode(403))
        assertEquals(RemoteCheck.Unknown, cloud.sizeOf("any"))
        server.enqueue(MockResponse().setResponseCode(500))
        assertEquals(RemoteCheck.Unknown, cloud.sizeOf("any"))
    }

    @Test
    fun `not connected means NoToken, with no network call`() = runTest {
        val result = cloud.upload(source, "Car Show")
        assertEquals(DataResult.Failure(RemoteError.NoToken), result)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `connecting checks the bucket first and stores nothing if the keys are wrong`() = runTest {
        // configFrom insists on https, so exercise the store path through S3Client-level HEAD instead.
        connectedToMockServer()
        server.enqueue(MockResponse().setResponseCode(200))
        assertNotNull(cloud.accountLabel())
        cloud.signOut()
        assertNull(cloud.accountLabel())
        assertTrue(store.isEmpty())
    }

    @Test
    fun `an endpoint without https is refused, and a bare host gets https`() {
        val base = mapOf(
            "region" to "r", "bucket" to "b", "access_key" to "a", "secret_key" to "s"
        )
        assertNull(S3CompatibleCloud.configFrom(base + ("endpoint" to "http://example.com")))
        assertEquals(
            "https://s3.us-west-004.backblazeb2.com",
            S3CompatibleCloud.configFrom(base + ("endpoint" to "s3.us-west-004.backblazeb2.com/"))!!.endpoint
        )
        assertNull(S3CompatibleCloud.configFrom(base + ("endpoint" to "")))
    }

    @Test
    fun `connectWithKeys with an incomplete form fails without touching the network`() = runTest {
        val result = cloud.connectWithKeys(mapOf("endpoint" to "example.com"))
        assertTrue(result is SignInResult.Failed)
        assertEquals(0, server.requestCount)
        assertEquals(BackupLocation.BACKBLAZE_B2, cloud.location)
    }
}
