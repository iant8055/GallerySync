package com.gallery.sync.data.remote.googlephotos

import com.gallery.sync.data.remote.onedrive.UploadSource
import okhttp3.MediaType
import okhttp3.RequestBody
import okio.BufferedSink

/**
 * Wraps [source] as a [RequestBody] that reads and writes in fixed-size chunks, rather than
 * materialising the whole file as a `ByteArray` the way OneDrive's small-file path does (which is
 * safe there only because it is capped at OneDrive's 4 MiB single-request ceiling — see
 * `ChunkedUploader.SMALL_FILE_THRESHOLD_BYTES`). Google Photos' raw-upload endpoint has no such
 * ceiling and is expected to carry full-size videos, so loading one whole into memory risks the
 * same OOM this project has already hit streaming OneDrive downloads (see CLAUDE.md's working
 * practices on the Fold 4 download-body incident).
 */
internal fun UploadSource.toStreamingRequestBody(
    mimeType: MediaType,
    onProgress: (bytesSent: Long, total: Long) -> Unit
): RequestBody = object : RequestBody() {

    override fun contentType(): MediaType = mimeType

    override fun contentLength(): Long = sizeBytes

    override fun writeTo(sink: BufferedSink) {
        val total = sizeBytes
        open().use { reader ->
            var offset = 0L
            val buffer = ByteArray(CHUNK_BYTES)
            while (offset < total) {
                val size = minOf(CHUNK_BYTES.toLong(), total - offset).toInt()
                reader.readFully(offset, buffer, size)
                sink.write(buffer, 0, size)
                offset += size
                onProgress(offset, total)
            }
        }
    }
}

/** 1 MiB. Large enough to be efficient, small enough that one chunk is a rounding error. */
private const val CHUNK_BYTES = 1024 * 1024
