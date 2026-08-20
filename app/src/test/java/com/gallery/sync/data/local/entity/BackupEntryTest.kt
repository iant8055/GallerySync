package com.gallery.sync.data.local.entity

import com.gallery.sync.data.local.converter.BackupStateConverter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * Tests for the ledger's identity rule and state persistence.
 *
 * The identity rule is the piece that decides whether a file gets uploaded twice or never — both
 * failure modes matter, and the second one silently loses someone's photos.
 */
class BackupEntryTest {

    @Test
    fun `the same file produces the same key across scans`() {
        val first = backupKeyOf("Camera", "IMG_1.jpg", 1024, 1_700_000_000)
        val second = backupKeyOf("Camera", "IMG_1.jpg", 1024, 1_700_000_000)

        assertEquals(first, second)
    }

    @Test
    fun `an edited file gets a new key so it is uploaded again`() {
        // Re-saving a photo changes its size. The bytes genuinely differ, so it must be re-sent.
        val original = backupKeyOf("Camera", "IMG_1.jpg", 1024, 1_700_000_000)
        val edited = backupKeyOf("Camera", "IMG_1.jpg", 2048, 1_700_000_000)

        assertNotEquals(original, edited)
    }

    @Test
    fun `a file re-saved at the same size but a later time gets a new key`() {
        val original = backupKeyOf("Camera", "IMG_1.jpg", 1024, 1_700_000_000)
        val touched = backupKeyOf("Camera", "IMG_1.jpg", 1024, 1_700_000_999)

        assertNotEquals(original, touched)
    }

    @Test
    fun `identically named files in different albums are distinct`() {
        // Two cameras, two albums, same filename. Treating them as one would drop a photo.
        val camera = backupKeyOf("Camera", "IMG_1.jpg", 1024, 1_700_000_000)
        val screenshots = backupKeyOf("Screenshots", "IMG_1.jpg", 1024, 1_700_000_000)

        assertNotEquals(camera, screenshots)
    }

    @Test
    fun `backup state survives a round trip through the database`() {
        val converter = BackupStateConverter()

        BackupState.entries.forEach { state ->
            assertEquals(state, converter.toBackupState(converter.fromBackupState(state)))
        }
    }

    @Test
    fun `an unrecognised stored state falls back to pending rather than throwing`() {
        // A row written by a newer build, then downgraded. Retrying it is safe; crashing is not.
        assertEquals(BackupState.PENDING, BackupStateConverter().toBackupState("SOMETHING_NEW"))
    }

    @Test
    fun `state is stored by name so reordering the enum cannot corrupt existing rows`() {
        assertEquals("UPLOADED", BackupStateConverter().fromBackupState(BackupState.UPLOADED))
    }

    @Test
    fun `new albums do nothing until the user chooses`() {
        // Changed 19 Aug 2026. The scan follows granted directories and first run asks outright, so
        // a default that uploaded would override an answer already given.
        assertEquals(AlbumMode.OFF, AlbumMode.DEFAULT)
    }

    @Test
    fun `archive can never be the default for new albums`() {
        // It would apply to albums the user has not seen, which is where the per-album confirmation
        // cannot reach.
        assertEquals(listOf(AlbumMode.OFF, AlbumMode.BACKUP, AlbumMode.SYNC), AlbumMode.canBeDefault)
    }

    @Test
    fun `only sync proxies photos, and only archive removes them`() {
        // The two modes that touch local files must never be reachable by accident. Asserting the
        // whole enum means a mode added later has to make a deliberate choice here.
        assertEquals(listOf(AlbumMode.SYNC), AlbumMode.entries.filter { it.proxiesPhotos })
        assertEquals(listOf(AlbumMode.ARCHIVE), AlbumMode.entries.filter { it.removesLocal })
        assertEquals(false, AlbumMode.OFF.uploads)
    }
}
