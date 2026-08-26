package com.gallery.sync.data.remote.onedrive

import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Streaming

/**
 * File downloads, on a client of their own.
 *
 * ### Why this is not on [GraphApiService]
 *
 * The shared client logs at `BODY` in debug builds, and `HttpLoggingInterceptor` buffers an entire
 * response body into memory in order to print it. `@Streaming` cannot prevent that: it is a Retrofit
 * annotation, and the interceptor is OkHttp, one layer below — it has no idea the caller intends to
 * stream. So the one endpoint that returns gigabytes was the one endpoint guaranteed to be buffered
 * whole.
 *
 * **Observed on the Fold 4, 26 Aug 2026.** Restoring a 2 GB video killed the process twice, the same
 * way both times: `OutOfMemoryError` at the 512 MiB heap ceiling, thrown on OkHttp's HTTP/2 reader
 * inside `okio.Buffer.writableSegment`. Debug builds only — release logs at `NONE` and never
 * buffered — but that is precisely the build large-file restore gets tested on, which is why video
 * retrieval stayed unverified for so long.
 *
 * Separating the client keeps full `BODY` logging everywhere it is useful and cheap, and off the one
 * path where it is neither. It follows [UploadChunkService], which already has its own client for
 * its own reasons.
 */
interface GraphDownloadService {

    /**
     * The bytes of one file.
     *
     * `@Streaming` is still required. It stops *Retrofit* buffering the body before returning, which
     * is a second and independent way to take the process down on a large file. Both have to be
     * right: this annotation, and a client whose interceptors do not read the body.
     *
     * Graph answers this with a redirect to short-lived storage; OkHttp follows it automatically.
     */
    @Streaming
    @GET("me/drive/items/{itemId}/content")
    suspend fun downloadItem(@Path("itemId") itemId: String): Response<ResponseBody>
}
