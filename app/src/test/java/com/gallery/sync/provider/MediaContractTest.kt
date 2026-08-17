package com.gallery.sync.provider

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Unit tests for the published provider contract.
 *
 * These constants are effectively released API: changing [MediaContract.AUTHORITY] or one of the
 * `OpenableColumns` names after shipping breaks every third-party integration, so they are pinned
 * here as literals on purpose rather than compared against themselves.
 *
 * [MediaContract.CONTENT_URI] is deliberately NOT touched: it is built from `Uri.parse`, an
 * android.jar stub that returns null in local unit tests. It is covered by the instrumented
 * `MediaContentProviderTest` instead.
 */
class MediaContractTest {

    @Test
    fun `authority is the published constant`() {
        assertEquals("com.gallery.sync.provider", MediaContract.AUTHORITY)
    }

    @Test
    fun `authority is byte-identical to the manifest declaration`() {
        val manifest = locateManifest()
        val declared = AUTHORITIES_PATTERN.find(manifest.readText())?.groupValues?.get(1)

        assertNotNull(
            "no android:authorities found in ${manifest.absolutePath}",
            declared
        )
        assertEquals(
            "MediaContract.AUTHORITY and AndroidManifest.xml android:authorities have drifted",
            declared,
            MediaContract.AUTHORITY
        )
    }

    @Test
    fun `media path segment is the published constant`() {
        assertEquals("media", MediaContract.PATH_MEDIA)
    }

    @Test
    fun `openable column names use the load-bearing spellings`() {
        // CapCut and friends read these through android.provider.OpenableColumns. Do not tidy up.
        assertEquals("_id", MediaContract.Columns.ID)
        assertEquals("_display_name", MediaContract.Columns.DISPLAY_NAME)
        assertEquals("_size", MediaContract.Columns.SIZE)
    }

    @Test
    fun `remaining column names are stable`() {
        assertEquals("mime_type", MediaContract.Columns.MIME_TYPE)
        assertEquals("date_modified", MediaContract.Columns.DATE_MODIFIED)
        assertEquals("source", MediaContract.Columns.SOURCE)
    }

    @Test
    fun `default projection contains the openable columns`() {
        val projection = MediaContract.DEFAULT_PROJECTION.toList()

        assertTrue("_id missing from DEFAULT_PROJECTION", projection.contains("_id"))
        assertTrue("_display_name missing from DEFAULT_PROJECTION", projection.contains("_display_name"))
        assertTrue("_size missing from DEFAULT_PROJECTION", projection.contains("_size"))
    }

    @Test
    fun `default projection contains every declared column exactly once`() {
        val projection = MediaContract.DEFAULT_PROJECTION.toList()

        assertEquals(
            listOf(
                MediaContract.Columns.ID,
                MediaContract.Columns.DISPLAY_NAME,
                MediaContract.Columns.SIZE,
                MediaContract.Columns.MIME_TYPE,
                MediaContract.Columns.DATE_MODIFIED,
                MediaContract.Columns.SOURCE
            ),
            projection
        )
        assertEquals(projection.size, projection.distinct().size)
    }

    @Test
    fun `mime types follow the android cursor dir and item conventions`() {
        assertEquals(
            "vnd.android.cursor.dir/vnd.com.gallery.sync.media",
            MediaContract.MIME_TYPE_DIR
        )
        assertEquals(
            "vnd.android.cursor.item/vnd.com.gallery.sync.media",
            MediaContract.MIME_TYPE_ITEM
        )
    }

    private companion object {

        val AUTHORITIES_PATTERN = Regex("""android:authorities\s*=\s*"([^"]*)"""")

        /**
         * Finds `AndroidManifest.xml` without depending on the JVM working directory, which differs
         * between a Gradle run and an IDE run.
         */
        fun locateManifest(): File {
            var dir: File? = File("").absoluteFile
            while (dir != null) {
                val candidates = listOf(
                    File(dir, "src/main/AndroidManifest.xml"),
                    File(dir, "app/src/main/AndroidManifest.xml")
                )
                candidates.firstOrNull { it.isFile }?.let { return it }
                dir = dir.parentFile
            }
            throw AssertionError(
                "could not locate AndroidManifest.xml starting from ${File("").absolutePath}"
            )
        }
    }
}
