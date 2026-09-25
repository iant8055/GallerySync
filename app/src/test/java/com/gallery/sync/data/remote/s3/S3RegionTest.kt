package com.gallery.sync.data.remote.s3

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/** One box fewer to get wrong: a Backblaze endpoint already names its region. */
class S3RegionTest {

    private val base = mapOf(
        "bucket" to "b", "access_key" to "a", "secret_key" to "s"
    )

    @Test
    fun `a Backblaze or Amazon endpoint names its region`() {
        assertEquals("us-east-005", S3CompatibleCloud.regionFromEndpoint("https://s3.us-east-005.backblazeb2.com"))
        assertEquals("us-west-004", S3CompatibleCloud.regionFromEndpoint("https://s3.us-west-004.backblazeb2.com/"))
        assertEquals("eu-west-2", S3CompatibleCloud.regionFromEndpoint("https://s3.eu-west-2.amazonaws.com"))
    }

    @Test
    fun `an endpoint that does not carry the region gives none`() {
        assertNull(S3CompatibleCloud.regionFromEndpoint("https://a1b2.va.idrivee2-25.com"))
        assertNull(S3CompatibleCloud.regionFromEndpoint("https://example.com"))
    }

    @Test
    fun `a blank region is filled from the endpoint`() {
        val config = S3CompatibleCloud.configFrom(base + ("endpoint" to "s3.us-east-005.backblazeb2.com"))
        assertNotNull(config)
        assertEquals("us-east-005", config!!.region)
    }

    @Test
    fun `a region that is typed wins over the one in the endpoint`() {
        val config = S3CompatibleCloud.configFrom(
            base + ("endpoint" to "s3.us-east-005.backblazeb2.com") + ("region" to "us-west-004")
        )
        assertEquals("us-west-004", config!!.region)
    }

    @Test
    fun `a blank region with an endpoint that cannot supply one is still incomplete`() {
        assertNull(S3CompatibleCloud.configFrom(base + ("endpoint" to "a1b2.va.idrivee2-25.com")))
    }
}
