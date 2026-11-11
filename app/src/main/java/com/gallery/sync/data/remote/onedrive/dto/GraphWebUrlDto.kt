package com.gallery.sync.data.remote.onedrive.dto

import kotlinx.serialization.Serializable

/** A `driveItem` narrowed to its link, from `GraphApiService.getItemWebUrl`. */
@Serializable
data class GraphWebUrlDto(
    val id: String? = null,
    val webUrl: String? = null
)
