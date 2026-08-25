package com.gallery.sync.domain.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * How long a file must have been gone before its cloud copy may be offered for deletion.
 *
 * The guard exists because "never infers deletion from absence alone" cannot be satisfied by
 * examining one scan more carefully. What turns absence into evidence is that it keeps being true:
 * an unmounted card is back by lunchtime, a deleted photo is still gone next week.
 *
 * [aFileThatHasNeverBeenSeenMissingIsNeverEligible] is the one that matters most — a null means the
 * file is on the phone, and nothing about it should ever reach a deletion prompt.
 */
class CloudDeletionGraceTest {

    private val day = 24L * 60 * 60 * 1000
    private val now = 1_800_000_000_000L

    @Test
    fun theDefaultPolicyLeavesCloudCopiesAlone() {
        assertEquals(CloudDeletionPolicy.LEAVE, CloudDeletionPolicy.DEFAULT)
    }

    /**
     * There is no automatic option, and there must not be one. To delete automatically the app has
     * to infer a deletion from a scan, and a bad scan would then reach the whole library.
     */
    @Test
    fun thereIsNoAutomaticPolicy() {
        assertEquals(
            listOf(CloudDeletionPolicy.LEAVE, CloudDeletionPolicy.ASK),
            CloudDeletionPolicy.entries.toList()
        )
    }

    @Test
    fun aFileThatHasNeverBeenSeenMissingIsNeverEligible() {
        assertFalse(
            CloudDeletionGrace.isEligible(missingSinceEpochMillis = null, nowEpochMillis = now)
        )
    }

    @Test
    fun aFileMissingLessThanTheGraceIsNotEligible() {
        assertFalse(
            CloudDeletionGrace.isEligible(
                missingSinceEpochMillis = now - 3 * day,
                graceDays = 7,
                nowEpochMillis = now
            )
        )
    }

    @Test
    fun aFileMissingLongerThanTheGraceIsEligible() {
        assertTrue(
            CloudDeletionGrace.isEligible(
                missingSinceEpochMillis = now - 8 * day,
                graceDays = 7,
                nowEpochMillis = now
            )
        )
    }

    @Test
    fun theBoundaryItselfCounts() {
        assertTrue(
            CloudDeletionGrace.isEligible(
                missingSinceEpochMillis = now - 7 * day,
                graceDays = 7,
                nowEpochMillis = now
            )
        )
    }

    @Test
    fun daysRemainingCountsDownAndStopsAtZero() {
        assertEquals(
            4,
            CloudDeletionGrace.daysRemaining(now - 3 * day, graceDays = 7, nowEpochMillis = now)
        )
        assertEquals(
            0,
            CloudDeletionGrace.daysRemaining(now - 9 * day, graceDays = 7, nowEpochMillis = now)
        )
    }

    /** Rounded up, so "nearly there" never reads as "ready". */
    @Test
    fun apartialDayCountsAsAWholeOneRemaining() {
        assertEquals(
            1,
            CloudDeletionGrace.daysRemaining(
                missingSinceEpochMillis = now - (7 * day - 3600_000),
                graceDays = 7,
                nowEpochMillis = now
            )
        )
    }

    @Test
    fun aFileStillOnThePhoneReportsTheFullGrace() {
        assertEquals(7, CloudDeletionGrace.daysRemaining(null, graceDays = 7, nowEpochMillis = now))
    }
}
