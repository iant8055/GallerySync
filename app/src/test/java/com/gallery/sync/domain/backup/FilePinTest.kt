package com.gallery.sync.domain.backup

import com.gallery.sync.data.local.entity.AlbumMode
import com.gallery.sync.data.local.entity.BackupEntryEntity
import com.gallery.sync.data.local.entity.BackupState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The per-file "keep at full size" pin, and the ordering of an album's file list.
 *
 * The pin is a safety feature: it must only ever hold a file back, and it must be the same value
 * Restore writes, or a restored file would not show as kept.
 */
class FilePinTest {

    private data class F(val id: String, val mediaStoreId: Long)

    private fun withoutPinned(items: List<F>, ids: Set<String> = emptySet(), msIds: Set<Long> = emptySet()) =
        FilePin.withoutPinned(items, ids, msIds, idOf = { it.id }, mediaStoreIdOf = { it.mediaStoreId })

    @Test
    fun `the pin is Backup, the value Restore writes`() {
        assertEquals(AlbumMode.BACKUP, FilePin.MODE)
        assertTrue(FilePin.isPinned(AlbumMode.BACKUP))
    }

    @Test
    fun `only Backup counts as a pin`() {
        assertFalse(FilePin.isPinned(null))
        assertFalse(FilePin.isPinned(AlbumMode.SYNC))
        assertFalse(FilePin.isPinned(AlbumMode.ARCHIVE))
        assertFalse(FilePin.isPinned(AlbumMode.OFF))
    }

    @Test
    fun `unpinning returns the file to following its album`() {
        assertEquals(null, FilePin.overrideFor(pinned = false))
        assertEquals(AlbumMode.BACKUP, FilePin.overrideFor(pinned = true))
    }

    @Test
    fun `a pinned file is left out of what Archive may remove`() {
        val a = F("a", 1)
        val b = F("b", 2)
        assertEquals(listOf(b), withoutPinned(listOf(a, b), ids = setOf("a")))
    }

    /** A restore rewrites the modification time, so the key changes and the MediaStore id does not. */
    @Test
    fun `a file is held back by its MediaStore id even when its key has changed`() {
        val restored = F("new-key", 42)
        assertTrue(withoutPinned(listOf(restored), msIds = setOf(42L)).isEmpty())
    }

    @Test
    fun `with nothing pinned the list is untouched`() {
        val files = listOf(F("a", 1), F("b", 2))
        assertEquals(files, withoutPinned(files))
    }

    // ── Sorting ─────────────────────────────────────────────────────────────

    private fun entry(
        name: String,
        date: Long = 0,
        state: BackupState = BackupState.UPLOADED,
        proxied: Boolean = false
    ) = BackupEntryEntity(
        id = name,
        mediaStoreId = 1,
        contentUri = "content://x/$name",
        displayName = name,
        album = "test",
        sizeBytes = 10,
        dateModifiedEpochSeconds = date,
        mimeType = "image/jpeg",
        isVideo = false,
        state = state,
        isProxied = proxied
    )

    private fun names(list: List<BackupEntryEntity>) = list.map { it.displayName }

    @Test
    fun `name sorts A to Z ignoring case`() {
        val sorted = AlbumFileSort.sorted(listOf(entry("b.jpg"), entry("A.jpg"), entry("c.jpg")), FileSort.NAME)
        assertEquals(listOf("A.jpg", "b.jpg", "c.jpg"), names(sorted))
    }

    @Test
    fun `date puts the newest first and breaks ties by name`() {
        val sorted = AlbumFileSort.sorted(
            listOf(entry("old.jpg", 100), entry("b.jpg", 300), entry("a.jpg", 300)),
            FileSort.DATE
        )
        assertEquals(listOf("a.jpg", "b.jpg", "old.jpg"), names(sorted))
    }

    @Test
    fun `status puts what needs attention first`() {
        val sorted = AlbumFileSort.sorted(
            listOf(
                entry("plain.jpg"),
                entry("shrunk.jpg", proxied = true),
                entry("waiting.jpg", state = BackupState.PENDING),
                entry("broken.jpg", state = BackupState.FAILED)
            ),
            FileSort.STATUS
        )
        assertEquals(listOf("broken.jpg", "waiting.jpg", "shrunk.jpg", "plain.jpg"), names(sorted))
    }

    @Test
    fun `status falls back to name within a group`() {
        val sorted = AlbumFileSort.sorted(listOf(entry("b.jpg"), entry("a.jpg")), FileSort.STATUS)
        assertEquals(listOf("a.jpg", "b.jpg"), names(sorted))
    }
}
