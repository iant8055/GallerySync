package com.gallery.sync.domain.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Which folders the scan is allowed to look in.
 *
 * [aGrantDoesNotLeakIntoASimilarlyNamedFolder] is the one that matters. A grant on `DCIM` covering
 * `DCIMBackup` would mean scanning — and later writing to — a folder the user never granted, which
 * is the failure this whole mechanism exists to prevent.
 */
class TreeScopeTest {

    // ---------- document ids ----------

    @Test
    fun aTreeDocumentIdYieldsThePathAfterTheVolume() {
        assertEquals("DCIM", TreeScope.pathFromTreeDocumentId("primary:DCIM"))
        assertEquals("Pictures/Backup", TreeScope.pathFromTreeDocumentId("primary:Pictures/Backup"))
    }

    /** Removable storage uses a volume id rather than "primary"; the shape is the same. */
    @Test
    fun anSdCardVolumeIsHandledTheSameWay() {
        assertEquals("DCIM", TreeScope.pathFromTreeDocumentId("1234-5678:DCIM"))
    }

    /**
     * A whole-volume grant is refused. Treating it as a scope would silently re-include everything
     * the user was narrowing away from, which is the opposite of what picking a folder means.
     */
    @Test
    fun aWholeVolumeIsNotAScope() {
        assertNull(TreeScope.pathFromTreeDocumentId("primary:"))
        assertNull(TreeScope.pathFromTreeDocumentId("primary:/"))
        assertNull(TreeScope.pathFromTreeDocumentId("nonsense"))
    }

    // ---------- scope matching ----------

    @Test
    fun aFolderIsInsideItsOwnGrant() {
        assertTrue(TreeScope.isInScope("DCIM/", listOf("DCIM")))
    }

    @Test
    fun aSubfolderIsInsideTheGrant() {
        assertTrue(TreeScope.isInScope("DCIM/Camera/", listOf("DCIM")))
        assertTrue(TreeScope.isInScope("DCIM/Camera/Trips/", listOf("DCIM")))
    }

    @Test
    fun aGrantDoesNotLeakIntoASimilarlyNamedFolder() {
        assertFalse("DCIMBackup is not inside DCIM", TreeScope.isInScope("DCIMBackup/", listOf("DCIM")))
        assertFalse(TreeScope.isInScope("DCIM2/Camera/", listOf("DCIM")))
    }

    @Test
    fun anUngrantedFolderIsOutside() {
        assertFalse(TreeScope.isInScope("Pictures/Screenshots/", listOf("DCIM")))
    }

    /** No grants means nothing is in scope — the engine must have nothing to do until Gate 1. */
    @Test
    fun nothingIsInScopeBeforeAnythingIsGranted() {
        assertFalse(TreeScope.isInScope("DCIM/Camera/", emptyList()))
    }

    @Test
    fun aMissingRelativePathIsOutside() {
        assertFalse(TreeScope.isInScope(null, listOf("DCIM")))
    }

    @Test
    fun anyOneOfSeveralGrantsIsEnough() {
        val granted = listOf("DCIM", "Pictures/Saved")
        assertTrue(TreeScope.isInScope("Pictures/Saved/2026/", granted))
        assertTrue(TreeScope.isInScope("DCIM/Camera/", granted))
        assertFalse(TreeScope.isInScope("Download/", granted))
    }

    // ---------- tidying ----------

    @Test
    fun aNestedGrantIsDroppedWhenItsParentIsAlsoGranted() {
        assertEquals(
            listOf("DCIM"),
            TreeScope.withoutRedundant(listOf("DCIM", "DCIM/Camera"))
        )
    }

    @Test
    fun unrelatedGrantsAreAllKept() {
        assertEquals(
            listOf("DCIM", "Pictures"),
            TreeScope.withoutRedundant(listOf("DCIM", "Pictures"))
        )
    }

    @Test
    fun duplicatesAndSlashesAreTidiedAway() {
        assertEquals(listOf("DCIM"), TreeScope.withoutRedundant(listOf("DCIM", "/DCIM/", "DCIM")))
    }

    @Test
    fun theDisplayNameIsTheFolderTheUserPicked() {
        assertEquals("Camera", TreeScope.displayNameOf("DCIM/Camera"))
        assertEquals("DCIM", TreeScope.displayNameOf("DCIM"))
        assertEquals("Backup", TreeScope.displayNameOf("/Pictures/Backup/"))
    }
}
