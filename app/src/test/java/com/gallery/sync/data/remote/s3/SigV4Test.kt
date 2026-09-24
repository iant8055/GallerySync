package com.gallery.sync.data.remote.s3

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Date

class SigV4Test {

    // AWS's own worked example, "GET Object" from the S3 Signature V4 documentation:
    // bucket examplebucket, key test.txt, Range: bytes=0-9, 24 May 2013, us-east-1.
    // Expected signature is the one AWS publishes for exactly these inputs.
    @Test
    fun `matches AWS's published GET Object example`() {
        val now = Date(1_369_353_600_000L) // 2013-05-24T00:00:00Z
        val signed = SigV4.sign(
            method = "GET",
            host = "examplebucket.s3.amazonaws.com",
            path = "/test.txt",
            query = "",
            payloadSha256Hex = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
            region = "us-east-1",
            accessKey = "AKIAIOSFODNN7EXAMPLE",
            secretKey = "wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY",
            now = now,
            extraHeaders = mapOf("range" to "bytes=0-9")
        )
        val auth = signed.headers.getValue("Authorization")
        assertTrue(auth, auth.endsWith("Signature=f0e8bdb87c964420e857bd35b5d6ed310bd44f0170aba48dd91039c6036bdb41"))
        assertTrue(auth, "SignedHeaders=host;range;x-amz-content-sha256;x-amz-date" in auth)
        assertTrue(auth, "Credential=AKIAIOSFODNN7EXAMPLE/20130524/us-east-1/s3/aws4_request" in auth)
    }

    @Test
    fun `encodes a key per RFC 3986 and keeps slashes`() {
        assertEquals("GallerySync/Car%20Show/IMG_1.jpg", SigV4.encodePath("GallerySync/Car Show/IMG_1.jpg"))
        assertEquals("a/%C3%A9%2B.jpg", SigV4.encodePath("a/é+.jpg"))
    }
}
