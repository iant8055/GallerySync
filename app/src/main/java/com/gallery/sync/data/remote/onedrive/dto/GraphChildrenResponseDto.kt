package com.gallery.sync.data.remote.onedrive.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * A page of a Microsoft Graph `children` collection.
 *
 * [nextLink] is Graph's `@odata.nextLink`: an absolute, opaque URL that must be requested verbatim
 * rather than rebuilt from query parameters.
 */
@Serializable
data class GraphChildrenResponseDto(
    val value: List<GraphDriveItemDto> = emptyList(),
    @SerialName("@odata.nextLink") val nextLink: String? = null
)
