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

    // ---------- top-level folder (TASK-026: the destination-choice granularity) ----------

    @Test
    fun `the first segment of the relative path is the top-level folder`() {
        assertEquals("DCIM", MediaScanRules.topLevelFolderOf("DCIM/Camera/"))
        assertEquals("Pictures", MediaScanRules.topLevelFolderOf("Pictures/Screenshots/"))
    }

    @Test
    fun `a folder directly under the root has no nested segment to drop`() {
        assertEquals("Download", MediaScanRules.topLevelFolderOf("Download/"))
    }

    @Test
    fun `a null relative path resolves to null, for the caller to fall back to the app default`() {
        assertEquals(null, MediaScanRules.topLevelFolderOf(null))
    }

    @Test
    fun `an empty relative path resolves to null`() {
        assertEquals(null, MediaScanRules.topLevelFolderOf(""))
        assertEquals(null, MediaScanRules.topLevelFolderOf("/"))
    }

    @Test
    fun `a hidden top-level directory resolves to null, matching discoverDirectories' own filter`() {
        assertEquals(null, MediaScanRules.topLevelFolderOf(".thumbnails/"))
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

    // ---------- one spelling per folder (TASK-023) ----------

    @Test
    fun `a folder key ignores case and slashes`() {
        assertEquals(
            MediaScanRules.folderKeyOf("DCIM/camera/", "camera"),
            MediaScanRules.folderKeyOf("DCIM/Camera/", "Camera")
        )
    }

    @Test
    fun `folders with the same name in different places have different keys`() {
        assertFalse(
            MediaScanRules.folderKeyOf("DCIM/Camera/", "Camera") ==
                MediaScanRules.folderKeyOf("Pictures/camera/", "camera")
        )
    }

    @Test
    fun `below API 29 the key falls back to the name`() {
        assertEquals("name:camera", MediaScanRules.folderKeyOf(null, "Camera"))
    }

    @Test
    fun `a folder with one spelling keeps it`() {
        val names = MediaScanRules.canonicalAlbumNames(
            listOf(
                MediaScanRules.AlbumSighting("dcim/camera", "Camera", 1),
                MediaScanRules.AlbumSighting("dcim/camera", "Camera", 2)
            )
        )
        assertEquals(mapOf("dcim/camera" to "Camera"), names)
    }

    @Test
    fun `a folder with two spellings takes the newest item's`() {
        // 7 Sept 2026 on the Moto G: copied files under camera, later camera-app shots under Camera.
        val names = MediaScanRules.canonicalAlbumNames(
            listOf(
                MediaScanRules.AlbumSighting("dcim/camera", "camera", 5188),
                MediaScanRules.AlbumSighting("dcim/camera", "Camera", 5194),
                MediaScanRules.AlbumSighting("dcim/camera", "camera", 5191)
            )
        )
        assertEquals("Camera", names["dcim/camera"])
    }

    @Test
    fun `a renamed copy is its own folder and keeps its own name`() {
        val names = MediaScanRules.canonicalAlbumNames(
            listOf(
                MediaScanRules.AlbumSighting("dcim/camera", "Camera", 1),
                MediaScanRules.AlbumSighting("dcim/camera (1)", "Camera (1)", 2)
            )
        )
        assertEquals("Camera", names["dcim/camera"])
        assertEquals("Camera (1)", names["dcim/camera (1)"])
    }
}
