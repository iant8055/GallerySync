package com.gallery.sync.data.local.media

import android.Manifest
import android.os.Build
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the scanner's decisions.
 *
 * MediaStore cannot be queried from a plain JVM, so the judgement calls are extracted here and
 * tested directly — the same reason `MediaUriMatcher` exists apart from the ContentProvider.
 *
 * The partial-access cases carry the most weight: reporting PARTIAL as FULL would let the app tell
 * someone their library is backed up when only a handful of selected photos ever were.
 */
class MediaScanRulesTest {

    // ---------- inclusion ----------

    @Test
    fun `a normal file is included`() {
        assertTrue(MediaScanRules.shouldInclude(sizeBytes = 1_024, isPending = false))
    }

    @Test
    fun `a zero-byte file is skipped because it is still being written`() {
        assertFalse(MediaScanRules.shouldInclude(sizeBytes = 0, isPending = false))
    }

    @Test
    fun `a pending file is skipped even when it already reports a size`() {
        // Mid-save: the size is real but the bytes are not all there yet.
        assertFalse(MediaScanRules.shouldInclude(sizeBytes = 5_000_000, isPending = true))
    }

    // ---------- album naming ----------

    @Test
    fun `the bucket name is used when present`() {
        assertEquals("Camera", MediaScanRules.albumNameOf("Camera", "DCIM/Camera/"))
    }

    @Test
    fun `the relative path is the fallback when the bucket name is missing`() {
        assertEquals("Screenshots", MediaScanRules.albumNameOf(null, "DCIM/Screenshots/"))
    }

    @Test
    fun `a blank bucket name falls through to the path`() {
        assertEquals("Camera", MediaScanRules.albumNameOf("  ", "DCIM/Camera/"))
    }

    @Test
    fun `an item with neither name nor path gets a sensible album rather than throwing`() {
        assertEquals(MediaScanRules.UNKNOWN_ALBUM, MediaScanRules.albumNameOf(null, null))
    }

    // ---------- access resolution ----------

    @Test
    fun `both media permissions on API 33 plus is full access`() {
        val access = MediaScanRules.resolveAccess(Build.VERSION_CODES.TIRAMISU) {
            it == Manifest.permission.READ_MEDIA_IMAGES || it == Manifest.permission.READ_MEDIA_VIDEO
        }
        assertEquals(MediaAccess.FULL, access)
    }

    @Test
    fun `user-selected photos on API 34 is partial, never full`() {
        // The case that matters: the user picked specific photos. Treating this as FULL would let
        // the app claim a complete backup of a library it cannot even see.
        val access = MediaScanRules.resolveAccess(Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            it == MediaScanRules.PERMISSION_USER_SELECTED
        }
        assertEquals(MediaAccess.PARTIAL, access)
    }

    @Test
    fun `images granted but video denied is partial`() {
        // An app backing up both cannot call this complete.
        val access = MediaScanRules.resolveAccess(Build.VERSION_CODES.TIRAMISU) {
            it == Manifest.permission.READ_MEDIA_IMAGES
        }
        assertEquals(MediaAccess.PARTIAL, access)
    }

    @Test
    fun `nothing granted on API 33 plus is no access`() {
        val access = MediaScanRules.resolveAccess(Build.VERSION_CODES.TIRAMISU) { false }
        assertEquals(MediaAccess.NONE, access)
    }

    @Test
    fun `legacy storage permission on API 32 is full access`() {
        val access = MediaScanRules.resolveAccess(Build.VERSION_CODES.S_V2) {
            it == Manifest.permission.READ_EXTERNAL_STORAGE
        }
        assertEquals(MediaAccess.FULL, access)
    }

    @Test
    fun `nothing granted on API 32 is no access`() {
        assertEquals(MediaAccess.NONE, MediaScanRules.resolveAccess(Build.VERSION_CODES.S_V2) { false })
    }

    @Test
    fun `the granular permissions do not grant access on API 32`() {
        // They did not exist yet; only READ_EXTERNAL_STORAGE counts there.
        val access = MediaScanRules.resolveAccess(Build.VERSION_CODES.S_V2) {
            it == Manifest.permission.READ_MEDIA_IMAGES
        }
        assertEquals(MediaAccess.NONE, access)
    }
}
