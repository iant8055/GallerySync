package com.gallery.sync.data.remote.onedrive

import android.content.ContentResolver
import android.net.Uri
import java.io.Closeable
import java.io.File
import java.io.FileInputStream
import java.io.RandomAccessFile

/**
 * Something that can be uploaded: a name, a size, and positioned reads.
 *
 * This exists because the two ends of the app disagree about what a file is. The scanner yields
 * MediaStore **content URIs** — the only reliable way to read media under scoped storage — while
 * the upload transport was written against `java.io.File`. Rather than force one to pretend to be
 * the other, both implement this.
 *
 * Positioned reads rather than a stream, because resuming an interrupted upload means starting
 * again from an arbitrary offset the server chose.
 */
interface UploadSource {

    val displayName: String

    val sizeBytes: Long

    /** Opens for reading. Closing the reader releases every underlying handle. */
    fun open(): Reader

    interface Reader : Closeable {

        /**
         * Reads exactly [length] bytes starting at [offset].
         *
         * Must fill the buffer or throw. A short read silently uploaded as a full chunk would
         * corrupt the assembled file while every request still returned success.
         */
        fun readFully(offset: Long, buffer: ByteArray, length: Int)
    }
}

/** An ordinary file on disk. Used by tests and by anything already holding a [File]. */
class FileUploadSource(private val file: File) : UploadSource {

    override val displayName: String get() = file.name

    override val sizeBytes: Long get() = file.length()

    override fun open(): UploadSource.Reader = object : UploadSource.Reader {
        private val raf = RandomAccessFile(file, "r")

        override fun readFully(offset: Long, buffer: ByteArray, length: Int) {
            raf.seek(offset)
            raf.readFully(buffer, 0, length)
        }

        override fun close() = raf.close()
    }
}

/**
 * A MediaStore item, read through its content URI.
 *
 * This is what the backup actually uses. Opening by content URI works on every API level the app
 * supports, whereas the `DATA` path is restricted under scoped storage and behaves differently
 * across Android versions.
 */
class ContentUriUploadSource(
    private val resolver: ContentResolver,
    private val uri: Uri,
    override val displayName: String,
    override val sizeBytes: Long
) : UploadSource {

    override fun open(): UploadSource.Reader = object : UploadSource.Reader {
        private val descriptor = resolver.openFileDescriptor(uri, "r")
            ?: throw java.io.FileNotFoundException("could not open $uri")

        private val channel = FileInputStream(descriptor.fileDescriptor).channel

        override fun readFully(offset: Long, buffer: ByteArray, length: Int) {
            val target = java.nio.ByteBuffer.wrap(buffer, 0, length)
            var position = offset
            while (target.hasRemaining()) {
                val read = channel.read(target, position)
                if (read <= 0) throw java.io.EOFException("unexpected end of $displayName at $position")
                position += read
            }
        }

        override fun close() {
            runCatching { channel.close() }
            runCatching { descriptor.close() }
        }
    }
}
