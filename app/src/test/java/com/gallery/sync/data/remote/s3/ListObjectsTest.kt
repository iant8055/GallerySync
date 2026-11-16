package com.gallery.sync.data.remote.s3

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Reading a ListObjectsV2 answer, for the per-Cloud "verified" line on B2 and IDrive e2 (16 Nov 2026). */
class ListObjectsTest {

    @Test
    fun `keys and sizes are read, with XML escapes undone`() {
        val page = parseListObjects(
            """<?xml version="1.0" encoding="UTF-8"?>
            <ListBucketResult xmlns="http://s3.amazonaws.com/doc/2006-03-01/">
              <Name>b</Name><Prefix>GallerySync/Camera/</Prefix><IsTruncated>false</IsTruncated>
              <Contents><Key>GallerySync/Camera/a.jpg</Key><Size>10</Size></Contents>
              <Contents><Key>GallerySync/Camera/Tom &amp; Jo.mp4</Key><Size>2048</Size></Contents>
            </ListBucketResult>"""
        )!!
        assertEquals(mapOf("GallerySync/Camera/a.jpg" to 10L, "GallerySync/Camera/Tom & Jo.mp4" to 2048L), page.sizes)
        assertNull(page.nextToken)
    }

    @Test
    fun `a truncated page carries the token to go on with`() {
        val page = parseListObjects(
            "<ListBucketResult><IsTruncated>true</IsTruncated><NextContinuationToken>abc/=</NextContinuationToken>" +
                "</ListBucketResult>"
        )!!
        assertEquals("abc/=", page.nextToken)
    }

    @Test
    fun `a truncated page with no token, or an error document, is no answer at all`() {
        assertNull(parseListObjects("<ListBucketResult><IsTruncated>true</IsTruncated></ListBucketResult>"))
        assertNull(parseListObjects("<Error><Code>AccessDenied</Code></Error>"))
    }

    @Test
    fun `the canonical query is sorted and encodes the slash`() {
        assertEquals("list-type=2&prefix=GallerySync%2FMy%20Album%2F", canonicalListQuery("GallerySync/My Album/", null))
        assertEquals(
            "continuation-token=a%2Bb%3D&list-type=2&prefix=p%2F",
            canonicalListQuery("p/", "a+b=")
        )
    }
}
