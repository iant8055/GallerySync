package com.gallery.sync.data.remote.googlephotos

import com.gallery.sync.data.remote.onedrive.FileUploadSource
import okhttp3.MediaType.Companion.toMediaType
import okio.Buffer
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File
import kotlin.random.Random

/**
 * Unit tests for [toStreamingRequestBody].
 *
 * No mocks: a real temp file goes in, a real [okio.Buffer] catches what gets written. The file is
 * sized past one internal chunk deliberately, so these tests actually exercise the loop rather than
 * a single pass through it.
 */
class GooglePhotosStreamingRequestBodyTest {

    @Test
    fun `every byte written matches every byte read, across multiple internal chunks`() {
        // A little over 2 chunks (1 MiB each internally), so writeTo must loop at least three times.
        val bytes = Random(seed = 42).nextBytes(2_500_000)
        val file = File.createTempFile("streaming-body-test", ".bin").apply {
            writeBytes(bytes)
            deleteOnExit()
        }

        val body = FileUploadSource(file).toStreamingRequestBody(
            mimeType = "application/octet-stream".toMediaType(),
            onProgress = { _, _ -> }
        )
        val sink = Buffer()
        body.writeTo(sink)

        assertArrayEquals(bytes, sink.readByteArray())
    }

    @Test
    fun `contentLength reports the source's size`() {
        val file = File.createTempFile("streaming-body-test", ".bin").apply {
            writeBytes(ByteArray(12_345))
            deleteOnExit()
        }

        val body = FileUploadSource(file).toStreamingRequestBody(
            mimeType = "application/octet-stream".toMediaType(),
            onProgress = { _, _ -> }
        )

        assertEquals(12_345L, body.contentLength())
    }

    @Test
    fun `progress is reported cumulatively and ends at the total`() {
        val total = 2_500_000
        val file = File.createTempFile("streaming-body-test", ".bin").apply {
            writeBytes(ByteArray(total))
            deleteOnExit()
        }

        val reported = mutableListOf<Long>()
        val body = FileUploadSource(file).toStreamingRequestBody(
            mimeType = "application/octet-stream".toMediaType(),
            onProgress = { sent, _ -> reported.add(sent) }
        )
        body.writeTo(Buffer())

        // Strictly increasing, and the last call is the whole file — a caller tracking a progress
        // bar from this callback must never see it go backwards or stall short of 100%.
        assertEquals(reported.sorted(), reported)
        assertEquals(total.toLong(), reported.last())
    }
}
