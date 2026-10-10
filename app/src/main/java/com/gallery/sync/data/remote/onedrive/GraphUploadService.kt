package com.gallery.sync.data.remote.onedrive

import com.gallery.sync.data.remote.onedrive.dto.CreateFolderRequestDto
import com.gallery.sync.data.remote.onedrive.dto.CreateUploadSessionRequestDto
import com.gallery.sync.data.remote.onedrive.dto.UploadSessionDto
import com.gallery.sync.data.remote.onedrive.dto.GraphDriveItemDto
import com.gallery.sync.data.remote.onedrive.dto.UploadedItemDto
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.PUT
import retrofit2.http.POST
import retrofit2.http.Path

/**
 * Write endpoints on the signed-in user's drive. **Authenticated** — [GraphAuthInterceptor]
 * attaches the bearer token, as with [GraphApiService].
 *
 * The chunk PUTs are deliberately *not* here: they target an absolute, pre-authorised session URL
 * and must not carry the token. See [UploadChunkService].
 *
 * Paths are `encoded = true` because they are drive paths like `Samsung Gallery/DCIM/Camera` whose
 * slashes are structural. Letting Retrofit escape them to `%2F` addresses one absurdly-named file
 * instead of a folder tree.
 */
interface GraphUploadService {

    /**
     * Creates one folder directly under the drive root.
     *
     * Separate from [createFolderInFolder] because the root has no item id to address, and Graph
     * offers no single form that covers both.
     */
    @POST("me/drive/root/children")
    suspend fun createFolderInRoot(
        @Body body: CreateFolderRequestDto
    ): Response<GraphDriveItemDto>

    /** Creates one folder inside the folder identified by [itemId]. */
    @POST("me/drive/items/{itemId}/children")
    suspend fun createFolderInFolder(
        @Path("itemId") itemId: String,
        @Body body: CreateFolderRequestDto
    ): Response<GraphDriveItemDto>

    /**
     * Opens a resumable session. [ifMatch] is the eTag the item at [path] is expected to have, sent only
     * when filling an empty placeholder (see `UploadablePropertiesDto.CONFLICT_BEHAVIOUR_REPLACE`); null
     * omits the header.
     */
    @POST("me/drive/root:/{path}:/createUploadSession")
    suspend fun createUploadSession(
        @Path(value = "path", encoded = true) path: String,
        @Body body: CreateUploadSessionRequestDto = CreateUploadSessionRequestDto(),
        @Header("If-Match") ifMatch: String? = null
    ): Response<UploadSessionDto>

    /**
     * What is at [path] right now: its id, name and size, or a 404 when nothing is there.
     *
     * Asked after a name clash, to tell "this very file already arrived" from "a different file has
     * this name". A read of one item by its path rather than a listing of the folder, because a listing
     * can lag behind an upload that has only just finished, which is the whole reason it is needed.
     */
    @GET("me/drive/root:/{path}")
    suspend fun itemAtPath(
        @Path(value = "path", encoded = true) path: String
    ): Response<UploadedItemDto>

    /**
     * Single-request upload for small files, **failing if the name is taken**.
     *
     * Graph supports this up to 4 MiB. Opening a resumable session for a 200 KB thumbnail costs an
     * extra round trip and buys nothing, so [ChunkedUploader] routes small files here.
     *
     * **`fail`, so that a clash comes back as a 409 the caller can look into (20 Sept 2026, late).**
     * With `rename` here a file that had in fact arrived before the app was killed, and that the next
     * folder listing did not yet show, was sent again and filed as " 1" beside itself. The caller now
     * asks what is at the path: the same size means it arrived, so nothing is sent; anything else goes
     * up through [uploadSmallFileRenaming]. Never `replace`, in either.
     */
    @PUT("me/drive/root:/{path}:/content?@microsoft.graph.conflictBehavior=fail")
    suspend fun uploadSmallFile(
        @Path(value = "path", encoded = true) path: String,
        @Body body: RequestBody
    ): Response<UploadedItemDto>

    /**
     * The same upload, **filing it beside whatever has its name**.
     *
     * Used only once [uploadSmallFile] has been refused with a 409 and the file at that path turned out to
     * be a different one. **`rename` is in the URL on purpose, and it was missing until 20 Sept 2026.** Graph's default for
     * this endpoint is to **replace** a file of the same name, unlike `createUploadSession`, whose body
     * carries `rename`. Measured on the Moto G: a 133,017-byte file was uploaded, then a different
     * 187,856-byte file under the same name; the folder count did not change and the name read 187,856
     * bytes, so the first was overwritten. Every optimised photo is well under 4 MiB, as is any edited
     * one, so the path an optimised copy would take past every other guard was the one that replaced
     * the full-size original in OneDrive. Written into the annotation rather than passed in, so no
     * caller can leave it out.
     */
    @PUT("me/drive/root:/{path}:/content?@microsoft.graph.conflictBehavior=rename")
    suspend fun uploadSmallFileRenaming(
        @Path(value = "path", encoded = true) path: String,
        @Body body: RequestBody
    ): Response<UploadedItemDto>
}
