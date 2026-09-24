package com.gallery.sync.data.remote.googlephotos

import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Keeps Google Photos write calls under the Library API's quota.
 *
 * Measured on the Moto G, 24 Sept 2026: the project's quota is **30 write requests per minute per
 * user**, and one photo costs two writes (the raw upload, then `batchCreate`). Unpaced, a run hit HTTP 429
 * within seconds, and because every 429 was booked as that file's failure, 19 files burned attempts in
 * one batch and the wizard's first backup sat at 100% with 2,168 files still to go.
 *
 * One write every [minIntervalMillis] is 27 a minute, safely under 30. A photo therefore takes about
 * four and a half seconds, which is the honest speed of this API for one project — a higher quota has to
 * be requested from Google. Singleton, because the quota is per user, not per call site.
 */
@Singleton
class WriteRateLimiter @Inject constructor() {

    private val mutex = Mutex()
    private var lastWriteAt = 0L

    internal var minIntervalMillis = MIN_INTERVAL_MILLIS
    internal var clock: () -> Long = { System.currentTimeMillis() }

    /** Suspends until it is safe to make one more write call. */
    suspend fun acquire() {
        mutex.withLock {
            val wait = lastWriteAt + minIntervalMillis - clock()
            if (wait > 0) delay(wait)
            lastWriteAt = clock()
        }
    }

    companion object {
        const val MIN_INTERVAL_MILLIS = 2_200L

        /** How long to stand down after Google says the quota is spent; the quota window is a minute. */
        const val BACKOFF_MILLIS = 65_000L
    }
}
