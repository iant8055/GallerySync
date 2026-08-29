package com.gallery.sync.data.remote.onedrive

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.create
import java.io.File

/**
 * Transport tests for [ChunkedUploader], driven through MockWebServer.
 *
 * The behaviour worth pinning hardest is the `Content-Range` header. An off-by-one there produces
 * a file Graph accepts and assembles wrongly — nothing fails, and the corruption is only found
 * later when the video will not play.
 */
class ChunkedUploaderTest {

    @get:Rule
    val temp = TemporaryFolder()

    private lateinit var server: MockWebServer
    private lateinit var uploader: ChunkedUploader

    // Must mirror NetworkModule's configuration — encodeDefaults is what puts conflictBehavior
    // on the wire at all, so a test built on a differently-configured Json would prove nothing.
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()

        val retrofit = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()

        uploader = ChunkedUploader(
            uploadApi = retrofit.create<GraphUploadService>(),
            chunkApi = retrofit.create<UploadChunkService>(),
            json = json
        )
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    // ---------- pure header/parsing logic ----------

    @Test
    fun `content range end is inclusive`() {
        // A 10-byte file sent whole is bytes 0-9, not 0-10.
        assertEquals("bytes 0-9/10", ChunkedUploader.contentRange(0, 9, 10))
    }

    @Test
    fun `content range is correct for a middle chunk`() {
        assertEquals(
            "bytes 5242880-10485759/20000000",
            ChunkedUploader.contentRange(5_242_880, 10_485_759, 20_000_000)
        )
    }

    @Test
    fun `chunk size is a multiple of the required alignment`() {
        // Graph rejects any non-final chunk that is not a multiple of 320 KiB.
        assertEquals(0, ChunkedUploader.CHUNK_SIZE_BYTES % ChunkedUploader.CHUNK_ALIGNMENT_BYTES)
    }

    @Test
    fun `next offset is read from the server's expected ranges`() {
        assertEquals(26_214_400L, ChunkedUploader.nextOffsetFrom(listOf("26214400-")))
        assertEquals(100L, ChunkedUploader.nextOffsetFrom(listOf("100-199")))
    }

    @Test
    fun `next offset is null when ranges are absent or unparseable`() {
        assertNull(ChunkedUploader.nextOffsetFrom(null))
        assertNull(ChunkedUploader.nextOffsetFrom(emptyList()))
        assertNull(ChunkedUploader.nextOffsetFrom(listOf("nonsense")))
    }

    // ---------- small-file path ----------

    @Test
    fun `a small file uploads in one request and never opens a session`() = runTest {
        server.enqueue(jsonResponse(200, """{"id":"A1","name":"small.jpg","size":1024}"""))

        val outcome = uploader.upload(fileOfSize("small.jpg", 1024), "DCIM/Camera")

        assertTrue(outcome is UploadOutcome.Success)
        assertEquals(1, server.requestCount)

        val request = server.takeRequest()
        assertEquals("PUT", request.method)
        // The single-shot content endpoint, not createUploadSession.
        assertTrue(request.path!!.contains(":/content"))
    }

    /**
     * Changed 28 Aug 2026, and the old version was asserting the defect.
     *
     * It required an empty file to upload "successfully", which writes a zero-byte file to the drive
     * under that photo's name. `conflictBehavior` is `rename`, so no later upload can repair it —
     * the name is occupied for good and every retry files a sibling beside it. Worse, the empty
     * upload reports size 0, the local file reads 0, `verifiedInCloud()` sees them match, and the
     * photo becomes eligible for removal from the phone.
     *
     * The request count is the real assertion: nothing must reach the network at all.
     */
    @Test
    fun `an empty file is refused rather than uploaded as nothing`() = runTest {
        val outcome = uploader.upload(fileOfSize("empty.jpg", 0), "DCIM/Camera")

        assertEquals(UploadOutcome.EmptySource, outcome)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `a one byte file is still uploaded`() = runTest {
        server.enqueue(jsonResponse(200, """{"id":"A3","name":"tiny.jpg","size":1}"""))

        val outcome = uploader.upload(fileOfSize("tiny.jpg", 1), "DCIM/Camera")

        assertTrue(outcome is UploadOutcome.Success)
        assertEquals(1, server.requestCount)
    }

    // ---------- chunked path ----------

    @Test
    fun `a large file is chunked and the final range closes at the last byte`() = runTest {
        val size = ChunkedUploader.CHUNK_SIZE_BYTES + 1000L
        server.enqueue(jsonResponse(200, """{"uploadUrl":"${server.url("/session")}"}"""))
        server.enqueue(jsonResponse(202, """{"nextExpectedRanges":["${ChunkedUploader.CHUNK_SIZE_BYTES}-"]}"""))
        server.enqueue(jsonResponse(201, """{"id":"B1","name":"big.mp4","size":$size}"""))

        val outcome = uploader.upload(fileOfSize("big.mp4", size), "DCIM/Camera")

        assertTrue(outcome is UploadOutcome.Success)
        server.takeRequest() // createUploadSession

        val first = server.takeRequest()
        assertEquals(
            "bytes 0-${ChunkedUploader.CHUNK_SIZE_BYTES - 1}/$size",
            first.getHeader("Content-Range")
        )

        val second = server.takeRequest()
        assertEquals(
            "bytes ${ChunkedUploader.CHUNK_SIZE_BYTES}-${size - 1}/$size",
            second.getHeader("Content-Range")
        )
    }

    @Test
    fun `a file exactly one chunk long is sent as a single chunk`() = runTest {
        val size = ChunkedUploader.CHUNK_SIZE_BYTES.toLong()
        server.enqueue(jsonResponse(200, """{"uploadUrl":"${server.url("/session")}"}"""))
        server.enqueue(jsonResponse(201, """{"id":"C1","name":"exact.mp4","size":$size}"""))

        val outcome = uploader.upload(fileOfSize("exact.mp4", size), "DCIM/Camera")

        assertTrue(outcome is UploadOutcome.Success)
        // session + exactly one chunk
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `resume follows the server's offset rather than local arithmetic`() = runTest {
        val size = ChunkedUploader.CHUNK_SIZE_BYTES * 2L
        // The server claims it only kept half the first chunk. The next range must start there,
        // not where we assumed the chunk ended.
        val serverOffset = ChunkedUploader.CHUNK_SIZE_BYTES / 2
        server.enqueue(jsonResponse(200, """{"uploadUrl":"${server.url("/session")}"}"""))
        server.enqueue(jsonResponse(202, """{"nextExpectedRanges":["$serverOffset-"]}"""))
        server.enqueue(jsonResponse(201, """{"id":"D1","name":"resume.mp4","size":$size}"""))

        uploader.upload(fileOfSize("resume.mp4", size), "DCIM/Camera")

        server.takeRequest()
        server.takeRequest()
        val resumed = server.takeRequest()
        assertTrue(resumed.getHeader("Content-Range")!!.startsWith("bytes $serverOffset-"))
    }

    @Test
    fun `uploads never request replace on conflict`() = runTest {
        val size = ChunkedUploader.CHUNK_SIZE_BYTES + 10L
        server.enqueue(jsonResponse(200, """{"uploadUrl":"${server.url("/session")}"}"""))
        server.enqueue(jsonResponse(201, """{"id":"E1","name":"x.mp4","size":$size}"""))
        server.enqueue(jsonResponse(201, """{"id":"E1","name":"x.mp4","size":$size}"""))

        uploader.upload(fileOfSize("x.mp4", size), "DCIM/Camera")

        val body = server.takeRequest().body.readUtf8()
        assertTrue(body.contains("rename"))
        assertTrue(!body.contains("replace"))
    }

    // ---------- failures ----------

    @Test
    fun `a full drive surfaces as a 507 failure`() = runTest {
        server.enqueue(jsonResponse(507, """{"error":{"code":"quotaLimitReached"}}"""))

        val outcome = uploader.upload(fileOfSize("small.jpg", 512), "DCIM/Camera")

        assertEquals(507, (outcome as UploadOutcome.HttpFailure).code)
    }

    @Test
    fun `a failed session creation does not attempt any chunks`() = runTest {
        server.enqueue(jsonResponse(403, """{"error":{"code":"accessDenied"}}"""))

        val outcome = uploader.upload(
            fileOfSize("big.mp4", ChunkedUploader.CHUNK_SIZE_BYTES + 1L),
            "DCIM/Camera"
        )

        assertEquals(403, (outcome as UploadOutcome.HttpFailure).code)
        assertEquals(1, server.requestCount)
    }

    // ---------- helpers ----------

    private fun jsonResponse(code: Int, body: String) = MockResponse()
        .setResponseCode(code)
        .setHeader("Content-Type", "application/json")
        .setBody(body)

    private fun fileOfSize(name: String, size: Long): File =
        temp.newFile(name).apply {
            outputStream().use { out ->
                var remaining = size
                val block = ByteArray(64 * 1024) { (it % 251).toByte() }
                while (remaining > 0) {
                    val n = minOf(block.size.toLong(), remaining).toInt()
                    out.write(block, 0, n)
                    remaining -= n
                }
            }
        }
}
