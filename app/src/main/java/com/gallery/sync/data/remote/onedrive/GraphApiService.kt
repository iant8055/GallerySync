package com.gallery.sync.data.remote.onedrive

import com.gallery.sync.data.remote.onedrive.dto.GraphChildrenResponseDto
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Url

/**
 * Read-only Microsoft Graph endpoints for browsing the signed-in user's drive.
 *
 * Base URL: `https://graph.microsoft.com/v1.0/`.
 *
 * Every method returns `Response<T>` rather than a bare body so the repository can see the HTTP
 * status code and map it onto a typed `RemoteError` — a bare body would surface a 401 as an
 * exception indistinguishable from a 500.
 *
 * The `Authorization` header is not declared here; [GraphAuthInterceptor] attaches it.
 */
interface GraphApiService {

    @GET("me/drive/root/children")
    suspend fun listRootChildren(
        @Query("\$top") top: Int = DEFAULT_PAGE_SIZE,
        @Query("\$select") select: String = DEFAULT_SELECT
    ): Response<GraphChildrenResponseDto>

    @GET("me/drive/items/{itemId}/children")
    suspend fun listChildren(
        @Path("itemId") itemId: String,
        @Query("\$top") top: Int = DEFAULT_PAGE_SIZE,
        @Query("\$select") select: String = DEFAULT_SELECT
    ): Response<GraphChildrenResponseDto>

    /**
     * Lists a folder addressed by path rather than id, e.g. `Samsung Gallery/DCIM/Camera`.
     *
     * The backup needs this to ask "is this file already here?" before uploading, and it only ever
     * knows the destination as a path. A 404 is the normal answer for a folder that does not exist
     * yet, not an error.
     *
     * `encoded = true` because the slashes are structural; escaping them to `%2F` would address one
     * absurdly-named file instead of a folder tree.
     */
    @GET("me/drive/root:/{path}:/children")
    suspend fun listChildrenByPath(
        @Path(value = "path", encoded = true) path: String,
        @Query("\$top") top: Int = DEFAULT_PAGE_SIZE,
        @Query("\$select") select: String = DEFAULT_SELECT
    ): Response<GraphChildrenResponseDto>

    /**
     * Follows an `@odata.nextLink` verbatim.
     *
     * Graph's continuation links are absolute and opaque — they already carry `$skiptoken` and the
     * original `$select`, so nothing may be appended to or rebuilt from them.
     */
    @GET
    suspend fun listNextPage(@Url nextLink: String): Response<GraphChildrenResponseDto>

    companion object {

        const val DEFAULT_PAGE_SIZE = 100

        /**
         * Restricts each `driveItem` to the fields the mapper actually reads. Graph returns a very
         * wide item by default; narrowing it keeps listings small on mobile connections.
         */
        const val DEFAULT_SELECT =
            "id,name,size,eTag,lastModifiedDateTime,createdDateTime,file,folder,image,photo," +
                "parentReference"
    }
}
