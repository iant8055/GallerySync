package com.gallery.sync.domain.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * When the first whole-library upload is allowed to start.
 *
 * The midnight wrap is the case worth testing hardest, because an overnight window is the *normal*
 * configuration here rather than an edge one — a window starting at 22:00 spends most of its life on
 * the far side of midnight, and the usual two-branch comparison gets one end of it wrong.
 */
class FirstBackupWindowTest {

    @Test
    fun theWindowOpensAtTheChosenHour() {
        assertTrue(FirstBackupWindow.isOpen(hourOfDay = 1, startHour = 1))
        assertFalse(FirstBackupWindow.isOpen(hourOfDay = 0, startHour = 1))
    }

    @Test
    fun theWindowClosesAfterItsLength() {
        // 1am start, six hours: 1..6 open, 7 closed.
        assertTrue(FirstBackupWindow.isOpen(hourOfDay = 6, startHour = 1))
        assertFalse(FirstBackupWindow.isOpen(hourOfDay = 7, startHour = 1))
    }

    @Test
    fun aWindowStartingLateInTheEveningRunsPastMidnight() {
        // 22:00 start, six hours: 22, 23, 00, 01, 02, 03.
        for (hour in listOf(22, 23, 0, 1, 2, 3)) {
            assertTrue("$hour:00 should be inside", FirstBackupWindow.isOpen(hour, startHour = 22))
        }
        for (hour in listOf(4, 12, 21)) {
            assertFalse("$hour:00 should be outside", FirstBackupWindow.isOpen(hour, startHour = 22))
        }
    }

    @Test
    fun hoursUntilOpenIsZeroWhileOpen() {
        assertEquals(0, FirstBackupWindow.hoursUntilOpen(hourOfDay = 2, startHour = 1))
    }

    @Test
    fun hoursUntilOpenCountsForwardAcrossMidnight() {
        assertEquals(3, FirstBackupWindow.hoursUntilOpen(hourOfDay = 22, startHour = 1))
        assertEquals(13, FirstBackupWindow.hoursUntilOpen(hourOfDay = 12, startHour = 1))
    }

    @Test
    fun chargingIsRequiredByDefault() {
        assertFalse(
            "inside the window but unplugged",
            FirstBackupWindow.mayRunNow(hourOfDay = 2, isCharging = false)
        )
        assertTrue(
            "inside the window and plugged in",
            FirstBackupWindow.mayRunNow(hourOfDay = 2, isCharging = true)
        )
    }

    @Test
    fun chargingCanBeWaived() {
        assertTrue(
            FirstBackupWindow.mayRunNow(
                hourOfDay = 2,
                isCharging = false,
                requiresCharging = false
            )
        )
    }

    /** Waiving charging does not open the window — the two conditions are independent. */
    @Test
    fun waivingChargingDoesNotIgnoreTheClock() {
        assertFalse(
            FirstBackupWindow.mayRunNow(
                hourOfDay = 12,
                isCharging = true,
                requiresCharging = false
            )
        )
    }

    /**
     * The screen has to say *which* condition is holding it. "Waiting until 1am" and "waiting for
     * you to plug in" ask different things of the user; "waiting" asks nothing and explains nothing.
     */
    @Test
    fun theReasonForWaitingIsDistinguishable() {
        assertEquals(
            FirstBackupHold.OUTSIDE_WINDOW,
            FirstBackupWindow.heldBecause(hourOfDay = 12, isCharging = true)
        )
        assertEquals(
            FirstBackupHold.NOT_CHARGING,
            FirstBackupWindow.heldBecause(hourOfDay = 2, isCharging = false)
        )
        assertNull(FirstBackupWindow.heldBecause(hourOfDay = 2, isCharging = true))
    }

    /** The clock is reported first: plugging in at noon still would not start it. */
    @Test
    fun theClockIsReportedBeforeCharging() {
        assertEquals(
            FirstBackupHold.OUTSIDE_WINDOW,
            FirstBackupWindow.heldBecause(hourOfDay = 12, isCharging = false)
        )
    }

    @Test
    fun everyHourOfTheDayIsSelectable() {
        assertEquals(24, FirstBackupWindow.SELECTABLE_HOURS.count())
        for (start in FirstBackupWindow.SELECTABLE_HOURS) {
            assertTrue("a window starting at $start must open at $start", FirstBackupWindow.isOpen(start, start))
        }
    }
}
