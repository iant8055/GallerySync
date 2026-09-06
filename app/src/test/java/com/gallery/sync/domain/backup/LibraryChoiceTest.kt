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

    @Test
    fun proxyingReclaimsMostOfAPhotoLibrary() {
        // 10 GB of photos, about a tenth left behind.
        assertEquals(9 * gb, LibraryEstimate.spaceFreedBySync(10 * gb))
    }

    @Test
    fun videoIsNeverCountedAsFreeable() {
        // Sync leaves video whole, so the same photo total gives the same answer regardless.
        assertEquals(
            LibraryEstimate.spaceFreedBySync(10 * gb),
            LibraryEstimate.spaceFreedBySync(10 * gb)
        )
        assertEquals(0L, LibraryEstimate.spaceFreedBySync(0))
    }

    /**
     * Ian's own library: 16 GB of photos against 130 GB of video. Proxying every photo reclaims
     * about 14 GB — under 10% — so calling that "free space" without qualification would invite him
     * to expect most of 148 GB back.
     */
    @Test
    fun aVideoHeavyLibraryReportsAMarginalSaving() {
        assertTrue(
            LibraryEstimate.isSavingMarginal(photoBytes = 16 * gb, videoBytes = 130 * gb)
        )
    }

    @Test
    fun aPhotoHeavyLibraryDoesNot() {
        assertFalse(
            LibraryEstimate.isSavingMarginal(photoBytes = 100 * gb, videoBytes = 10 * gb)
        )
    }

    @Test
    fun anEmptyLibraryIsNotDescribedAsMarginal() {
        assertFalse(LibraryEstimate.isSavingMarginal(photoBytes = 0, videoBytes = 0))
    }

    /**
     * The predicate Step 7's estimate and `OptimiseWorker` both read.
     *
     * Pinned because they disagreed on 6 Sept 2026 and nothing caught it: the card described the
     * outstanding population for every choice, while the worker optimised everything eligible. A
     * `BACK_UP_AND_FREE_SPACE` install promised 60 MB and reclaimed 2.26 GB.
     */
    @Test
    fun onlyTheTwoOptimisingChoicesOptimiseAtInstall() {
        assertFalse(LibraryChoice.BACK_UP_EVERYTHING.optimisesAtInstall)
        assertTrue(LibraryChoice.BACK_UP_AND_FREE_SPACE.optimisesAtInstall)
        assertTrue(LibraryChoice.BACK_UP_AND_OPTIMISE_NEW.optimisesAtInstall)
        assertFalse(LibraryChoice.CHOOSE_PER_ALBUM.optimisesAtInstall)
    }

    /** It must stay the same question `WizardBulkOptimise` asks, or the two can drift apart again. */
    @Test
    fun theEstimateAndTheWorkerAgreeOnEveryChoice() {
        LibraryChoice.entries.forEach { choice ->
            assertEquals(
                choice.name,
                choice.optimisesAtInstall,
                WizardBulkOptimise.shouldContinueToVideo(setupComplete = false, choice = choice)
            )
        }
    }

    /**
     * The two choices that run no install-time optimise, so no wizard card may promise a saving.
     *
     * Step 8 promised one anyway until 6 Sept 2026, gated on the optimise switches alone — Ian,
     * having chosen #1: *"why the savings if I'm just uploading and not optimizing?"*
     */
    @Test
    fun theChoicesThatUploadOnlyPromiseNoSaving() {
        assertFalse(LibraryChoice.BACK_UP_EVERYTHING.optimisesAtInstall)
        assertFalse(LibraryChoice.CHOOSE_PER_ALBUM.optimisesAtInstall)
    }

    /**
     * #2 and #3 must not be the same install. Ian, 6 Sept 2026: #2 *"optimizes ALL files on
     * phone"*; #3 *"ONLY optimizes files that were actually backed up in the previous step"*.
     *
     * They *were* the same until that day, because the cutoff that separates them was written only
     * by `ApplyLibraryChoice` — reachable from two screens that nothing renders.
     */
    @Test
    fun onlyFreeSpaceOptimisesTheWholeLibrary() {
        assertTrue(LibraryChoice.BACK_UP_AND_FREE_SPACE.optimisesWholeLibrary)
        assertFalse(LibraryChoice.BACK_UP_AND_OPTIMISE_NEW.optimisesWholeLibrary)
        assertFalse(LibraryChoice.BACK_UP_EVERYTHING.optimisesWholeLibrary)
        assertFalse(LibraryChoice.CHOOSE_PER_ALBUM.optimisesWholeLibrary)
    }

    /** The cutoff is the mechanism behind that, and it must line up with the flag. */
    @Test
    fun theCutoffMatchesTheWholeLibraryFlag() {
        val now = 1_788_000_000_000L
        LibraryChoice.entries.forEach { choice ->
            val actsOnEverything = choice.cutoffFor(now) == OptimiseCutoff.EVERYTHING
            if (choice.optimisesAtInstall) {
                assertEquals(choice.name, choice.optimisesWholeLibrary, actsOnEverything)
            }
        }
        assertEquals(now, LibraryChoice.BACK_UP_AND_OPTIMISE_NEW.cutoffFor(now))
    }
}
