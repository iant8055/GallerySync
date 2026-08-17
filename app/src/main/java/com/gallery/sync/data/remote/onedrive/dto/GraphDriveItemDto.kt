package com.gallery.sync.data.remote.onedrive.dto

import kotlinx.serialization.Serializable

/**
 * A Microsoft Graph `driveItem`.
 *
 * Every field is nullable with a default because Graph models item kinds as *facets*: a folder
 * carries [folder] and no [file], a photo carries [file] and [image], and package/bundle items
 * carry neither. Requiring any of them would make deserialization fail on perfectly valid drives.
 */
@Serializable
data class GraphDriveItemDto(
    val id: String? = null,
    val name: String? = null,
    val size: Long? = null,
    val eTag: String? = null,
    /** ISO-8601, e.g. `2024-01-15T10:30:00Z`. */
    val lastModifiedDateTime: String? = null,
    /** ISO-8601, e.g. `2024-01-15T10:30:00Z`. */
    val createdDateTime: String? = null,
    val file: GraphFileFacetDto? = null,
    val folder: GraphFolderFacetDto? = null,
    val image: GraphImageFacetDto? = null,
    val parentReference: GraphParentReferenceDto? = null
)
