package com.gallery.sync.domain.backup

/**
 * When a run that works file by file should give up: after [limit] failures with nothing succeeding in between.
 *
 * One file that cannot be written (a trashed file, one the app has no write access to) is that file's problem and
 * the run goes on without it. Several in a row look like a problem with the run itself, such as no space or a revoked
 * grant, where carrying on would only repeat it. On 25 Sept 2026 one such file, first in a largest-first queue,
 * stopped every photo behind it.
 */
class FailureStreak(private val limit: Int = DEFAULT_LIMIT) {

    private var inARow = 0

    /** A file failed. True when this makes [limit] in a row and the run should stop. */
    fun failed(): Boolean {
        inARow++
        return inARow >= limit
    }

    /** A file was done or correctly left alone: the run is healthy, so the streak starts again. */
    fun succeeded() {
        inARow = 0
    }

    companion object {
        const val DEFAULT_LIMIT = 3
    }
}
