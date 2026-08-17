package com.gallery.sync.data.remote.onedrive.dto

import kotlinx.serialization.Serializable

/** Present on `driveItem`s that are files. */
@Serializable
data class GraphFileFacetDto(
    val mimeType: String? = null
)

/** Present on `driveItem`s that are folders. */
@Serializable
data class GraphFolderFacetDto(
    val childCount: Int? = null
)

/** Present on `driveItem`s Graph recognises as images. */
@Serializable
data class GraphImageFacetDto(
    val width: Int? = null,
    val height: Int? = null
)

/** Where a `driveItem` sits in the drive. */
@Serializable
data class GraphParentReferenceDto(
    val id: String? = null,
    /** e.g. `/drive/root:/Pictures`. */
    val path: String? = null
)
