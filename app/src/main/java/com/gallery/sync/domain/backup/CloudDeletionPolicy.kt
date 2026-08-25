package com.gallery.sync.domain.backup

/**
 * What happens to the OneDrive copy when a file leaves the phone.
 *
 * ### Why there is no automatic option
 *
 * Ian asked on 25 Aug 2026 for three: leave, ask, and delete automatically. The third was dropped,
 * and the reason is worth keeping next to the type.
 *
 * CLAUDE.md is explicit that deleting a photo from the phone does not delete its backup unless the
 * user confirms **that specific action**, and MILESTONES names silent bidirectional delete as the
 * Samsung behaviour this project exists to replace. But the stronger objection is mechanical rather
 * than rhetorical: to delete automatically the app must *notice* a file has gone, and the only
 * signal available is absence from a scan. v0.4 says "never infers deletion from absence alone" —
 * and an unmounted card, a revoked permission or a partial scan are indistinguishable from a
 * deletion. An automatic mode would turn a bad scan into cloud deletions across a whole library.
 *
 * [ASK] keeps a human between that inference and the consequence, which is the entire safeguard.
 */
enum class CloudDeletionPolicy {

    /**
     * Nothing is ever removed from OneDrive. The default, and the setting hardest to regret.
     *
     * A cloud copy left behind costs storage. A cloud copy removed in error costs the photo, since
     * the local one is already gone — that asymmetry decides the default on its own.
     */
    LEAVE,

    /**
     * Offer to remove cloud copies of files that have left the phone, and act only on a yes.
     *
     * Batched, never per-file: a prompt for each of eight hundred photos is not consent, it is a
     * queue someone taps through. One prompt naming the count and the total size is a decision that
     * can actually be made.
     */
    ASK;

    companion object {
        val DEFAULT = LEAVE
    }
}

/**
 * How long a file must have been absent before its cloud copy may even be offered for deletion.
 *
 * ### The point is not patience, it is corroboration
 *
 * "Never infers deletion from absence alone" cannot be satisfied by looking harder at one scan. What
 * turns a single observation into evidence is that it keeps being true — a file still missing a week
 * later was deleted; a file missing because the SD card was out at 9am is back by lunchtime.
 *
 * This is the guard that survives the cases the marking logic cannot see. `refreshLedger` already
 * refuses to mark anything when media access is partial or the scan came back empty, but it cannot
 * tell a deliberate deletion from a folder that happens to be unavailable right now.
 */
object CloudDeletionGrace {

    /**
     * Seven days.
     *
     * Long enough that a transient cause — storage unmounted, permission revoked, a phone restored
     * from backup — has resolved itself. Short enough that the feature still does what someone
     * turning it on wants.
     *
     * A guess, and flagged as one: no measurement supports seven specifically, and it is a setting
     * so that being wrong is cheap to correct.
     */
    const val DEFAULT_DAYS = 7

    val SELECTABLE_DAYS = listOf(1, 7, 30, 90)

    /** Whether a file missing since [missingSinceEpochMillis] has been gone long enough. */
    fun isEligible(
        missingSinceEpochMillis: Long?,
        graceDays: Int = DEFAULT_DAYS,
        nowEpochMillis: Long = System.currentTimeMillis()
    ): Boolean {
        val since = missingSinceEpochMillis ?: return false
        return nowEpochMillis - since >= graceDays * MILLIS_PER_DAY
    }

    /** Whole days until [missingSinceEpochMillis] becomes eligible; 0 when it already is. */
    fun daysRemaining(
        missingSinceEpochMillis: Long?,
        graceDays: Int = DEFAULT_DAYS,
        nowEpochMillis: Long = System.currentTimeMillis()
    ): Int {
        val since = missingSinceEpochMillis ?: return graceDays
        val elapsed = nowEpochMillis - since
        val remaining = graceDays * MILLIS_PER_DAY - elapsed
        if (remaining <= 0) return 0
        // Rounded up: something with an hour to go has "1 day" left rather than none.
        return ((remaining + MILLIS_PER_DAY - 1) / MILLIS_PER_DAY).toInt()
    }

    private const val MILLIS_PER_DAY = 24L * 60 * 60 * 1000
}
