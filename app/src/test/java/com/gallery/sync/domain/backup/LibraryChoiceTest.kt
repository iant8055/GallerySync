package com.gallery.sync.domain.backup

import com.gallery.sync.data.local.entity.AlbumMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Gate 2 — what happens to the library already on the phone.
 *
 * Two properties carry the weight. [archiveIsNeverABulkChoice] guards the rule that the largest
 * irreversible action in the product cannot be taken in a wizard. [aVideoHeavyLibraryReportsA
 * MarginalSaving] guards against promising space that will not be freed.
 */
class LibraryChoiceTest {

    private val gb = 1024L * 1024 * 1024

    @Test
    fun theDefaultChangesNothing() {
        assertNull(LibraryChoice.CHOOSE_PER_ALBUM.mode)
        assertFalse(LibraryChoice.CHOOSE_PER_ALBUM.uploads)
    }

    @Test
    fun theTwoActiveChoicesMapToTheirModes() {
        assertEquals(AlbumMode.BACKUP, LibraryChoice.BACK_UP_EVERYTHING.mode)
        assertEquals(AlbumMode.SYNC, LibraryChoice.BACK_UP_AND_FREE_SPACE.mode)
        assertTrue(LibraryChoice.BACK_UP_EVERYTHING.uploads)
        assertTrue(LibraryChoice.BACK_UP_AND_FREE_SPACE.uploads)
    }

    /**
     * Archive in a wizard would set every album at once to the one mode that removes files, chosen
     * before the user has watched the app work and before retrieval exists to undo it.
     */
    @Test
    fun archiveIsNeverABulkChoice() {
        assertFalse(
            "no Gate 2 option may map to Archive",
            LibraryChoice.entries.any { it.mode == AlbumMode.ARCHIVE }
        )
    }

    // ---------- the estimate ----------

    /**
     * The predicate Step 7's estimate and `OptimizeWorker` both read.
     *
     * Pinned because they disagreed on 6 Sept 2026 and nothing caught it: the card described the
     * outstanding population for every choice, while the worker optimized everything eligible. A
     * `BACK_UP_AND_FREE_SPACE` install promised 60 MB and reclaimed 2.26 GB.
     */
    @Test
    fun onlyTheTwoOptimizingChoicesOptimizeAtInstall() {
        assertFalse(LibraryChoice.BACK_UP_EVERYTHING.optimizesAtInstall)
        assertTrue(LibraryChoice.BACK_UP_AND_FREE_SPACE.optimizesAtInstall)
        assertTrue(LibraryChoice.BACK_UP_AND_OPTIMIZE_NEW.optimizesAtInstall)
        assertFalse(LibraryChoice.CHOOSE_PER_ALBUM.optimizesAtInstall)
    }

    /** It must stay the same question `WizardBulkOptimize` asks, or the two can drift apart again. */
    @Test
    fun theEstimateAndTheWorkerAgreeOnEveryChoice() {
        LibraryChoice.entries.forEach { choice ->
            assertEquals(
                choice.name,
                choice.optimizesAtInstall,
                WizardBulkOptimize.shouldContinueToVideo(setupComplete = false, choice = choice)
            )
        }
    }

    /**
     * The two choices that run no install-time optimize, so no wizard card may promise a saving.
     *
     * Step 8 promised one anyway until 6 Sept 2026, gated on the optimize switches alone — Ian,
     * having chosen #1: *"why the savings if I'm just uploading and not optimizing?"*
     */
    @Test
    fun theChoicesThatUploadOnlyPromiseNoSaving() {
        assertFalse(LibraryChoice.BACK_UP_EVERYTHING.optimizesAtInstall)
        assertFalse(LibraryChoice.CHOOSE_PER_ALBUM.optimizesAtInstall)
    }

    /**
     * #2 and #3 must not be the same install. Ian, 6 Sept 2026: #2 *"optimizes ALL files on
     * phone"*; #3 *"ONLY optimizes files that were actually backed up in the previous step"*.
     *
     * They *were* the same until that day, because the cutoff that separates them was written only
     * by a bulk applier reachable from two screens that nothing rendered. Both were deleted in
     * TASK-022; `ReconcileViewModel.setLibraryChoice` is now the only writer.
     */
    @Test
    fun onlyFreeSpaceOptimizesTheWholeLibrary() {
        assertTrue(LibraryChoice.BACK_UP_AND_FREE_SPACE.optimizesWholeLibrary)
        assertFalse(LibraryChoice.BACK_UP_AND_OPTIMIZE_NEW.optimizesWholeLibrary)
        assertFalse(LibraryChoice.BACK_UP_EVERYTHING.optimizesWholeLibrary)
        assertFalse(LibraryChoice.CHOOSE_PER_ALBUM.optimizesWholeLibrary)
    }

    /** The cutoff is the mechanism behind that, and it must line up with the flag. */
    @Test
    fun theCutoffMatchesTheWholeLibraryFlag() {
        val now = 1_788_000_000_000L
        LibraryChoice.entries.forEach { choice ->
            val actsOnEverything = choice.cutoffFor(now) == OptimizeCutoff.EVERYTHING
            if (choice.optimizesAtInstall) {
                assertEquals(choice.name, choice.optimizesWholeLibrary, actsOnEverything)
            }
        }
        assertEquals(now, LibraryChoice.BACK_UP_AND_OPTIMIZE_NEW.cutoffFor(now))
    }
}
