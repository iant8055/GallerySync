package com.gallery.sync.data.remote.cloud

import com.gallery.sync.data.remote.onedrive.UploadSource

/** An in-memory [UploadSource] for adapter tests. */
class TestUploadSource(
    override val displayName: String,
    private val bytes: ByteArray
) : UploadSource {
    override val sizeBytes: Long = bytes.size.toLong()

    override fun open() = object : UploadSource.Reader {
        override fun readFully(offset: Long, buffer: ByteArray, length: Int) {
            System.arraycopy(bytes, offset.toInt(), buffer, 0, length)
        }

        override fun close() = Unit
    }
}
