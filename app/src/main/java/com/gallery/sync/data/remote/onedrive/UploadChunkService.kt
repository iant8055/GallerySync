package com.gallery.sync.data.remote.onedrive

import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.PUT
import retrofit2.http.Url

/**
 * Uploads one chunk to a resumable upload session.
 *
 * ### Why this is separate from [GraphUploadService]
 *
 * The session URL returned by `createUploadSession` is **pre-authorised** — it carries its own
 * credentials in the query string. Attaching an `Authorization` header to it is a documented cause
 * of 401s on otherwise valid sessions. This interface is therefore built on an OkHttp client that
 * has no [GraphAuthInterceptor], which is only possible if it lives on its own Retrofit instance.
 *
 * The response body is left raw because the shape depends on the status code: 202 for an accepted
 * intermediate chunk carries `nextExpectedRanges`, while 200/201 for the final chunk carries the
 * created `driveItem`. [ChunkedUploader] decodes whichever applies.
 */
interface UploadChunkService {

    /**
     * Asks Graph how much of a session it already holds.
     *
     * The response carries `nextExpectedRanges`, and the server's answer is the only trustworthy
     * one — a chunk can be partially received, so a locally remembered offset would leave a hole
     * that Graph would assemble into a corrupt file without complaint.
     *
     * A 404 or 410 here means the session is gone, which is ordinary rather than an error: sessions
     * expire, and the caller simply opens a new one.
     */
    @GET
    suspend fun querySession(@Url uploadUrl: String): Response<ResponseBody>

    /**
     * Abandons a session and releases the chunks Graph is holding for it.
     *
     * Used when the user pauses. Resuming from a half-sent file would read the *current* local
     * bytes at a stored offset, and if that file changed while paused the two halves would be
     * spliced into something Graph accepts and marks complete. The ledger key includes size and
     * modification time, so a changed file normally lands on a different row — but "normally" is
     * not the standard the rest of this app holds itself to, and the cost of ruling it out is
     * bandwidth rather than data.
     *
     * A failure here is not worth reporting. The session expires on its own within about fifteen
     * minutes, nothing partial is ever visible in the drive, and the local row has already been
     * cleared — so the worst case is chunks Microsoft discards slightly later than we asked.
     */
    @DELETE
    suspend fun cancelSession(@Url uploadUrl: String): Response<ResponseBody>

    @PUT
    suspend fun uploadChunk(
        @Url uploadUrl: String,
        @Header("Content-Range") contentRange: String,
        @Body body: RequestBody
    ): Response<ResponseBody>
}
