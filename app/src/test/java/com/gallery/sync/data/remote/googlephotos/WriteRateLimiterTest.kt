package com.gallery.sync.data.remote.googlephotos

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WriteRateLimiterTest {

    @Test
    fun `writes are spaced by the minimum interval`() = runTest {
        val limiter = WriteRateLimiter().also { it.clock = { currentTime } }

        limiter.acquire()
        val first = currentTime
        limiter.acquire()
        limiter.acquire()

        assertEquals(WriteRateLimiter.MIN_INTERVAL_MILLIS * 2, currentTime - first)
    }

    @Test
    fun `a full minute of writes stays under the quota of thirty`() = runTest {
        val limiter = WriteRateLimiter().also { it.clock = { currentTime } }
        var calls = 0
        while (true) {
            limiter.acquire()
            if (currentTime >= 60_000) break
            calls++
        }
        assertTrue("$calls calls in a minute", calls <= 30)
    }
}
