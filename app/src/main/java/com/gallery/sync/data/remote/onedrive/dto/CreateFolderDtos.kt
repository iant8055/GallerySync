package com.gallery.sync.data.remote.onedrive.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Body of a "make me a folder here" request.
 *
 * Graph decides an item is a folder from the presence of the `folder` facet, which is why an empty
 * object has to be sent rather than omitted.
 */
@Serializable
data class CreateFolderRequestDto(
    val name: String,
    val folder: GraphFolderMarkerDto = GraphFolderMarkerDto(),
    /**
     * **`fail`, not `rename`.**
     *
     * The opposite choice to [UploadablePropertiesDto], and for the opposite reason. An upload
     * renames because two cameras can honestly produce the same filename and losing one is
     * unrecoverable. A folder the user just typed a name for is different: silently creating
     * `Backups 1` beside their existing `Backups` and then pointing the destination at the empty
     * one is a trap. Failing lets the picker say the name is taken.
     */
    @SerialName("@microsoft.graph.conflictBehavior")
    val conflictBehavior: String = CONFLICT_BEHAVIOUR_FAIL
) {
    companion object {
        const val CONFLICT_BEHAVIOUR_FAIL = "fail"
    }
}

/** The empty `folder` facet. Serialises to `{}`, which is all Graph wants. */
@Serializable
class GraphFolderMarkerDto
