package com.gallery.sync.data.remote.s3

import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * AWS Signature Version 4, the request signing every S3-compatible store (IDrive e2, Backblaze B2, and
 * most others) accepts. Pure functions, no Android types, so the signing can be checked against AWS's
 * own published test vectors off-device.
 *
 * Only what this app needs: a signed request with headers in the `Authorization` header (not a
 * presigned URL), for path-style requests with a known payload hash.
 */
internal object SigV4 {

    private const val ALGORITHM = "AWS4-HMAC-SHA256"

    data class Signed(
        /** Every header to add to the request, `Authorization` included. */
        val headers: Map<String, String>
    )

    /**
     * @param path the request path, already percent-encoded per [encodePath]
     * @param query the query string, already canonical (sorted, encoded), or empty
     * @param payloadSha256Hex lowercase hex SHA-256 of the body ("e3b0c442…" for an empty body)
     * @param extraHeaders headers to sign besides `host`, `x-amz-date` and `x-amz-content-sha256`;
     *   names must be lowercase
     */
    fun sign(
        method: String,
        host: String,
        path: String,
        query: String,
        payloadSha256Hex: String,
        region: String,
        accessKey: String,
        secretKey: String,
        now: Date,
        extraHeaders: Map<String, String> = emptyMap()
    ): Signed {
        val amzDate = format(now, "yyyyMMdd'T'HHmmss'Z'")
        val dateStamp = format(now, "yyyyMMdd")

        val headers = sortedMapOf(
            "host" to host,
            "x-amz-content-sha256" to payloadSha256Hex,
            "x-amz-date" to amzDate
        )
        extraHeaders.forEach { (k, v) -> headers[k.lowercase(Locale.ROOT)] = v.trim() }

        val signedHeaders = headers.keys.joinToString(";")
        val canonicalHeaders = headers.entries.joinToString("") { "${it.key}:${it.value}\n" }
        val canonicalRequest = listOf(
            method, path, query, canonicalHeaders, signedHeaders, payloadSha256Hex
        ).joinToString("\n")

        val scope = "$dateStamp/$region/s3/aws4_request"
        val stringToSign = listOf(ALGORITHM, amzDate, scope, sha256Hex(canonicalRequest.toByteArray()))
            .joinToString("\n")

        val kDate = hmac("AWS4$secretKey".toByteArray(), dateStamp)
        val kRegion = hmac(kDate, region)
        val kService = hmac(kRegion, "s3")
        val kSigning = hmac(kService, "aws4_request")
        val signature = hmac(kSigning, stringToSign).toHex()

        val authorization = "$ALGORITHM Credential=$accessKey/$scope, " +
            "SignedHeaders=$signedHeaders, Signature=$signature"

        return Signed(
            headers = mapOf(
                "x-amz-date" to amzDate,
                "x-amz-content-sha256" to payloadSha256Hex,
                "Authorization" to authorization
            )
        )
    }

    /**
     * Percent-encodes an object key for a canonical path: every byte outside RFC 3986's unreserved set
     * is `%XX`, and `/` is kept as the separator. S3 signs the path once-encoded.
     */
    fun encodePath(key: String): String = buildString {
        for (b in key.toByteArray(Charsets.UTF_8)) {
            val c = b.toInt() and 0xFF
            val ch = c.toChar()
            if (ch.isLetterOrDigit() && c < 128 || ch == '-' || ch == '_' || ch == '.' || ch == '~' || ch == '/') {
                append(ch)
            } else {
                append('%').append("0123456789ABCDEF"[c shr 4]).append("0123456789ABCDEF"[c and 0xF])
            }
        }
    }

    fun sha256Hex(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).toHex()

    private fun hmac(key: ByteArray, data: String): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        return mac.doFinal(data.toByteArray())
    }

    private fun format(date: Date, pattern: String): String =
        SimpleDateFormat(pattern, Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }.format(date)

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
}
