package com.gallery.sync.domain.billing

/**
 * The 30-day trial of using more than one cloud (TASK-026, Ian 24 Sept 2026).
 *
 * **A hard gate, not an auto-charge** — Ian: "definately a hard gate". Nothing here ever charges
 * anything, and Play cannot auto-charge a one-time product anyway. When the 30 days end, sending to a
 * second cloud simply stops until `pro_unlock` is bought; OneDrive and everything else carry on
 * untouched. The user has to start the trial themselves, with the terms on screen, so it can never
 * begin by accident.
 *
 * Tracked locally (one timestamp in DataStore), which a reinstall or clock change can defeat. That is
 * accepted: the cost of the trial being beatable is a second cloud's uploads, never a file at risk.
 */
object MultiCloudTrial {

    const val TRIAL_DAYS = 30
    private const val DAY_MILLIS = 24L * 60 * 60 * 1000

    sealed interface State {
        /** Never started, so still on offer. */
        data object NotStarted : State

        data class Active(val daysLeft: Int) : State

        data object Ended : State
    }

    fun stateOf(startedAtEpochMillis: Long?, nowEpochMillis: Long): State {
        if (startedAtEpochMillis == null) return State.NotStarted
        val elapsed = nowEpochMillis - startedAtEpochMillis
        // A clock set back to before the start reads as a fresh start rather than a negative elapsed,
        // and it must not be able to extend the trial past its 30 days from the recorded start.
        if (elapsed < 0) return State.Active(TRIAL_DAYS)
        val length = TRIAL_DAYS * DAY_MILLIS
        if (elapsed >= length) return State.Ended
        // Rounded up, so the last partial day still reads as one day left rather than zero.
        val left = ((length - elapsed + DAY_MILLIS - 1) / DAY_MILLIS).toInt()
        return State.Active(left)
    }

    val State.isActive: Boolean get() = this is State.Active
}
