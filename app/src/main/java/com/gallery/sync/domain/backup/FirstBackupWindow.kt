package com.gallery.sync.domain.backup

/**
 * When the very first backup is allowed to run.
 *
 * ### Why the first one is special
 *
 * The initial whole-library upload is the heaviest thing this app ever does. Measured on a real
 * device: 148 GB across 8,520 files, which at the ~3 MB/s observed is around fourteen hours of
 * continuous transfer. Starting that at whatever moment the user finishes setup means a hot phone,
 * a flat battery, and — on a metered connection — a bill.
 *
 * Every later run is incremental and small, so this restriction lifts as soon as the first one
 * finishes. It is a gate on the backlog, not a permanent schedule.
 *
 * ### What it does not gate
 *
 * Only automatic runs. A user who presses "Sync now" has asked, and asking is always allowed —
 * the window exists to stop the app choosing a bad moment on its own, not to stop the user
 * choosing a moment the app disagrees with.
 */
object FirstBackupWindow {

    /** 1am. Late enough that the phone is likely charging, early enough to finish before morning. */
    const val DEFAULT_START_HOUR = 1

    /** How long the window stays open once it starts. */
    const val WINDOW_HOURS = 6

    val SELECTABLE_HOURS = 0..23

    /**
     * Whether the window is open at [hourOfDay].
     *
     * Wraps midnight, which is the normal case rather than an edge one — an overnight window
     * starting at 22:00 runs into the small hours, and modular arithmetic handles that without the
     * two-branch comparison that usually gets one end wrong.
     */
    fun isOpen(
        hourOfDay: Int,
        startHour: Int = DEFAULT_START_HOUR,
        windowHours: Int = WINDOW_HOURS
    ): Boolean = hoursSinceOpening(hourOfDay, startHour) < windowHours

    /** Hours until the window next opens; 0 when it is open now. */
    fun hoursUntilOpen(hourOfDay: Int, startHour: Int = DEFAULT_START_HOUR): Int =
        if (isOpen(hourOfDay, startHour)) 0 else (startHour - hourOfDay + 24) % 24

    private fun hoursSinceOpening(hourOfDay: Int, startHour: Int): Int =
        (hourOfDay - startHour + 24) % 24

    /**
     * Whether an automatic first-backup run may proceed right now.
     *
     * [isCharging] is checked here rather than left to a WorkManager constraint because the answer
     * has to be explainable: a run that silently never happens because the phone was not plugged in
     * looks identical to a broken app. The screen reads this same function to say why it is waiting.
     */
    fun mayRunNow(
        hourOfDay: Int,
        isCharging: Boolean,
        startHour: Int = DEFAULT_START_HOUR,
        requiresCharging: Boolean = true,
        windowHours: Int = WINDOW_HOURS
    ): Boolean = isOpen(hourOfDay, startHour, windowHours) && (isCharging || !requiresCharging)

    /** Why a first-backup run is being held, or null when nothing is holding it. */
    fun heldBecause(
        hourOfDay: Int,
        isCharging: Boolean,
        startHour: Int = DEFAULT_START_HOUR,
        requiresCharging: Boolean = true,
        windowHours: Int = WINDOW_HOURS
    ): FirstBackupHold? = when {
        !isOpen(hourOfDay, startHour, windowHours) -> FirstBackupHold.OUTSIDE_WINDOW
        requiresCharging && !isCharging -> FirstBackupHold.NOT_CHARGING
        else -> null
    }
}

/**
 * Why the first backup has not started.
 *
 * Named rather than a boolean so the screen can say which of the two it is. "Waiting until 1am" and
 * "waiting for you to plug in" call for different actions from the user, and collapsing them into
 * "waiting" leaves someone staring at a phone that appears to be doing nothing.
 */
enum class FirstBackupHold {
    OUTSIDE_WINDOW,
    NOT_CHARGING
}
