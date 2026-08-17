package com.gallery.sync.domain.model

/**
 * A file that OneDrive has confirmed it stored.
 *
 * Returned only after Graph responds to the final chunk, so its presence is evidence the upload
 * completed — not that we finished sending. [sizeBytes] is the size **OneDrive reports**, which is
 * what makes it worth carrying: comparing it against the local file's length is how a caller
 * verifies the bytes actually landed rather than assuming they did.
 */
data class UploadedItem(
    val id: String,
    val name: String,
    val sizeBytes: Long,
    val eTag: String?
)
