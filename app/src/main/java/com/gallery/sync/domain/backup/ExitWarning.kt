package com.gallery.sync.domain.backup

import java.time.Instant

/**
 * Whether leaving the app should stop and say that files are waiting to be archived.
 *
 * ### Why this exists at all
 *
 * Archive cannot run unattended — `MediaStore.createTrashRequest` only launches from an Activity —
 * so something has to bring the user back once files become eligible. That is the **summons**, and
 * it is not a second consent: the album mode is the consent, given once when the mode was set.
 * See `ArchiveScreen` and CLAUDE.md.
 *
 * The summons already appears on the Albums tab. This adds one more surface, at the moment the user
 * is about to walk away from a half-finished job. Requested by Ian, 28 Aug 2026, in place of a
 * notification: it needs no permission, cannot be denied, and cannot be silently switched off —
 * which is exactly the failure mode `POST_NOTIFICATIONS` carries, where a denial means batches
 * accumulate and the user is never told.
 *
 * ### It is a net, not a guarantee
 *
 * Android has no general "app is closing" event. Only the back gesture from the root screen can be
 * intercepted; Home and a swipe from Recents cannot, and on gesture navigation Home is the common
 * way out. So this catches some departures and not most, and the Albums tab summons remains the
 * surface that is always there. Do not let anything become reachable only from here.
 */
object ExitWarning {

    /**
     * True when there is something waiting and the user has not asked to be left alone.
     *
     * [delayedUntilEpochMillis] is `0` when no delay was ever set. A delay that has expired is the
     * same as none — the point of the snooze is that it runs out.
     */
    fun shouldWarn(
        readyCount: Int,
        delayedUntilEpochMillis: Long,
        now: Instant
    ): Boolean {
        if (readyCount <= 0) return false
        if (delayedUntilEpochMillis <= 0L) return true
        return now.toEpochMilli() >= delayedUntilEpochMillis
    }
}
