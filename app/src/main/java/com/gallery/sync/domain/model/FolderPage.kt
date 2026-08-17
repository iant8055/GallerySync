package com.gallery.sync.domain.model

/**
 * One page of a folder listing.
 *
 * Cloud providers page large folders, so a full listing is the concatenation of pages walked by
 * feeding [nextPageToken] back into the repository until it comes back `null`.
 */
data class FolderPage(
    val nodes: List<RemoteMediaNode>,
    /** Opaque continuation token; `null` means this was the last page. */
    val nextPageToken: String?
)
