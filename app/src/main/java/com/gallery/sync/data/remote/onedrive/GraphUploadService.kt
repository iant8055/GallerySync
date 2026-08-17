package com.gallery.sync.data.remote.onedrive

import com.gallery.sync.data.remote.onedrive.dto.CreateUploadSessionRequestDto
import com.gallery.sync.data.remote.onedrive.dto.UploadSessionDto
import com.gallery.sync.data.remote.onedrive.dto.UploadedItemDto
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.Body
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

    @POST("me/drive/root:/{path}:/createUploadSession")
    suspend fun createUploadSession(
        @Path(value = "path", encoded = true) path: String,
        @Body body: CreateUploadSessionRequestDto = CreateUploadSessionRequestDto()
    ): Response<UploadSessionDto>

    /**
     * Single-request upload for small files.
     *
     * Graph supports this up to 4 MiB. Opening a resumable session for a 200 KB thumbnail costs an
     * extra round trip and buys nothing, so [ChunkedUploader] routes small files here.
     */
    @PUT("me/drive/root:/{path}:/content")
    suspend fun uploadSmallFile(
        @Path(value = "path", encoded = true) path: String,
        @Body body: RequestBody
    ): Response<UploadedItemDto>
}
