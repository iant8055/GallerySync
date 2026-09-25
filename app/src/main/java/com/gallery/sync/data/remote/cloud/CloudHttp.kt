package com.gallery.sync.data.remote.cloud

import com.gallery.sync.domain.model.DataResult
import com.gallery.sync.domain.model.RemoteError
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Response

/** Small helpers the OAuth cloud adapters share. No provider knowledge lives here. */
internal object CloudHttp {

    val json = Json { ignoreUnknownKeys = true }

    /** Parses a response body as a JSON object, or null if it is empty or not one. */
    fun objectOf(body: String?): JsonObject? =
        try {
            if (body.isNullOrBlank()) null else json.parseToJsonElement(body).jsonObject
        } catch (e: Exception) {
            null
        }

    /**
     * Maps a non-success status to the failure the engine understands. 401 means the token is no good;
     * a quota answer is the one failure the person can fix; a rate limit reads as the network so it
     * ends this provider's pass rather than burning every file's attempts.
     */
    fun failureFor(response: Response, body: String?, quotaMarkers: List<String> = emptyList()): DataResult.Failure =
        when {
            response.code == 401 -> DataResult.Failure(RemoteError.Unauthorized)
            response.code == 507 || quotaMarkers.any { body?.contains(it) == true } ->
                DataResult.Failure(RemoteError.InsufficientStorage)
            response.code == 429 || response.code == 503 -> DataResult.Failure(RemoteError.Network)
            else -> DataResult.Failure(RemoteError.Http(response.code, body))
        }

    /** A JSON string with every non-ASCII character escaped, for headers (Dropbox-API-Arg). */
    fun asciiJsonString(value: String): String = buildString {
        append('"')
        for (c in value) {
            when {
                c == '"' -> append("\\\"")
                c == '\\' -> append("\\\\")
                c == '\n' -> append("\\n")
                c == '\r' -> append("\\r")
                c == '\t' -> append("\\t")
                c.code < 0x20 || c.code > 0x7E -> append("\\u%04x".format(c.code))
                else -> append(c)
            }
        }
        append('"')
    }
}

/** A number, whether the provider sent it as a JSON number or as a string (Drive sends sizes as strings). */
internal fun JsonObject.longOrNull(name: String): Long? = stringOrNull(name)?.toLongOrNull()

// The .content of a JsonPrimitive that is the literal null reads "null"; treat it as absent.
internal fun JsonObject.stringOrNull(name: String): String? {
    val p = this[name] ?: return null
    val prim = try { p.jsonPrimitive } catch (e: Exception) { return null }
    return if (!prim.isString && prim.content == "null") null else prim.content
}
