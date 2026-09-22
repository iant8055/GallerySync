package com.gallery.sync.domain.backup

/**
 * Whether a "files have come of age" notification is worth sending.
 *
 * ### Grown, not merely nonzero
 *
 * The same batch of ready files can sit waiting for days while nothing else about it changes — the
 * Albums tab summons and the exit-warning dialog are already there for that, unconditionally. A
 * notification fired on every check of an unchanged batch would be the annoyance CLAUDE.md's own
 * notes on `ExitWarning` already argued against, just relocated. So this fires only when the ready
 * count has **grown** past the last count it was asked about — a new file joined the batch — never
 * merely because it is still above zero.
 *
 * A count that fell (some archived, some opted out) is not "shrunk past zero and grown again" the
 * next time a handful more arrive; the caller is expected to persist the new count after every call,
 * whatever the answer, so the baseline always tracks the last thing the user was told about.
 */
object ArchiveReadyNotice {
    fun shouldNotify(lastSeenCount: Int, currentCount: Int): Boolean = currentCount > lastSeenCount
}
