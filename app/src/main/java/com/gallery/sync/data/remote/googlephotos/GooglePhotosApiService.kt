package com.gallery.sync.data.remote.googlephotos

import com.gallery.sync.data.remote.googlephotos.dto.BatchCreateRequestDto
import com.gallery.sync.data.remote.googlephotos.dto.BatchCreateResponseDto
import com.gallery.sync.data.remote.googlephotos.dto.MediaItemsListResponseDto
import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Query

/**
 * Google Photos Library API v1 endpoints. Base URL: `https://photoslibrary.googleapis.com/`.
 *
 * Every method returns `Response<T>` rather than a bare body, same reason as [com.gallery.sync
 * .data.remote.onedrive.GraphApiService]: the repository needs the HTTP status to map onto a typed
 * `RemoteError`, and a 401 must stay distinguishable from a 500.
 *
 * The `Authorization` header is not declared here; [GooglePhotosAuthInterceptor] attaches it.
 */
interface GooglePhotosApiService {

    /**
     * Step one of an upload: raw bytes in, an opaque upload token out as **plain text**, not JSON —
     * the only endpoint here that isn't. [ResponseBody.string] is what reads it.
     *
     * `X-Goog-Upload-Protocol: raw` selects the simple, non-resumable variant. See TASK-026 and
     * [com.gallery.sync.domain.repository.GooglePhotosUploadRepository] for why v1 does not attempt
     * the chunked/resumable protocol the way OneDrive's uploads do.
     */
    @POST("v1/uploads")
    suspend fun uploadBytes(
        @Header("X-Goog-Upload-Content-Type") contentType: String,
        @Header("X-Goog-Upload-Protocol") protocol: String = "raw",
        @Body body: RequestBody
    ): Response<ResponseBody>

    /**
     * Step two: turns an upload token from [uploadBytes] into a real library item. v1 always sends
     * exactly one item per call — see [com.gallery.sync.domain.repository.GooglePhotosUploadRepository].
     */
    @POST("v1/mediaItems:batchCreate")
    suspend fun batchCreate(@Body request: BatchCreateRequestDto): Response<BatchCreateResponseDto>

    /**
     * Lists media items this app created — the `appcreateddata` scope means that's all this ever
     * returns, with no query parameter needed to ask for it. See
     * [com.gallery.sync.domain.repository.GooglePhotosRepository].
     */
    @GET("v1/mediaItems")
    suspend fun listMediaItems(
        @Query("pageSize") pageSize: Int = DEFAULT_PAGE_SIZE,
        @Query("pageToken") pageToken: String? = null
    ): Response<MediaItemsListResponseDto>

    companion object {
        /** Google's documented default and recommendation; 100 is the API's maximum. */
        const val DEFAULT_PAGE_SIZE = 25
    }
}
