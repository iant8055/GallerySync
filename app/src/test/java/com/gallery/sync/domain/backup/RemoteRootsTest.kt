package com.gallery.sync.domain.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Where backups go, and where the app looks for them.
 *
 * The property that matters is [samsungIsAlwaysSearchedWhateverTheDestination]. The default is not
 * decoration: it mirrors the layout Samsung's own sync created, and that is the only reason the
 * skip-existing check finds anything — 6,278 of 6,371 files on the Fold 4, 24 Aug 2026. If changing
 * the destination also moved the search, a user picking a different folder would silently re-upload
 * a library they had already paid to store.
 */
class RemoteRootsTest {

    @Test
    fun theDefaultDestinationIsTheLayoutSamsungCreated() {
        assertEquals("Samsung Gallery/DCIM", RemoteRoots.DEFAULT_DESTINATION)
    }

    @Test
    fun samsungIsAlwaysSearchedWhateverTheDestination() {
        val order = RemoteRoots.searchOrder("Pictures/MyBackup")

        assertTrue(
            "the old root must stay searchable or reconciliation is lost",
            RemoteRoots.SAMSUNG_GALLERY in order
        )
        assertEquals("the destination is searched first", "Pictures/MyBackup", order.first())
    }

    @Test
    fun theDefaultDestinationIsNotSearchedTwice() {
        assertEquals(listOf(RemoteRoots.SAMSUNG_GALLERY), RemoteRoots.searchOrder(RemoteRoots.SAMSUNG_GALLERY))
    }

    @Test
    fun ordinaryFolderPathsAreAccepted() {
        assertTrue(RemoteRoots.isValidDestination("Pictures"))
        assertTrue(RemoteRoots.isValidDestination("Pictures/Backup"))
        assertTrue(RemoteRoots.isValidDestination("Samsung Gallery/DCIM"))
        assertTrue(RemoteRoots.isValidDestination("Holiday 2026/Crete & Rhodes"))
    }

    @Test
    fun pathsThatWouldNotResolveWhereTheyReadAreRefused() {
        assertFalse("empty", RemoteRoots.isValidDestination(""))
        assertFalse("blank", RemoteRoots.isValidDestination("   "))
        assertFalse("leading slash", RemoteRoots.isValidDestination("/Pictures"))
        assertFalse("trailing slash", RemoteRoots.isValidDestination("Pictures/"))
        assertFalse("empty segment", RemoteRoots.isValidDestination("Pictures//Backup"))
        assertFalse("parent traversal", RemoteRoots.isValidDestination("Pictures/../Secrets"))
        assertFalse("current dir", RemoteRoots.isValidDestination("Pictures/./Backup"))
    }

    @Test
    fun normaliseTidiesWhatAUserWouldPlausiblyType() {
        assertEquals("Pictures/Backup", RemoteRoots.normalise("  Pictures/Backup  "))
        assertEquals("Pictures/Backup", RemoteRoots.normalise("/Pictures/Backup/"))
        assertEquals("Pictures", RemoteRoots.normalise("Pictures"))
    }

    /** Normalising has to produce something valid, or the field rejects what it just cleaned up. */
    @Test
    fun normalisedInputPassesValidation() {
        for (raw in listOf("/Pictures/Backup/", "  Samsung Gallery/DCIM  ", "Pictures/")) {
            assertTrue(
                "normalise($raw) should be usable",
                RemoteRoots.isValidDestination(RemoteRoots.normalise(raw))
            )
        }
    }
}
