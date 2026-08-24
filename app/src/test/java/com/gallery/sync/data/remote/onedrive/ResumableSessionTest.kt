package com.gallery.sync.data.remote.onedrive

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Expiry decisions for a stored upload session.
 *
 * Small surface, but the direction of the unknown case is what matters. Treating "Graph did not tell
 * us when this expires" as "expired" would throw away perfectly good sessions and reintroduce the
 * restart-from-zero this whole mechanism exists to prevent.
 */
class ResumableSessionTest {

    private val now = 1_700_000_000_000L

    private fun session(expiresAt: Long?) = ResumableSession("https://example/upload", expiresAt)

    @Test
    fun aSessionPastItsExpiryHasExpired() {
        assertTrue(session(now - 1).hasExpired(now))
    }

    @Test
    fun aSessionExpiringInTheFutureHasNot() {
        assertTrue(!session(now + 60_000).hasExpired(now))
    }

    /** Graph gives roughly five hours; the boundary itself counts as gone rather than usable. */
    @Test
    fun theExpiryInstantItselfCountsAsExpired() {
        assertTrue(session(now).hasExpired(now))
    }

    /**
     * The important one. Unknown must not mean expired — the session status request is the
     * authority, and discarding a session we were simply not told about would restart the upload.
     */
    @Test
    fun anUnknownExpiryIsNotTreatedAsExpired() {
        assertFalse(session(null).hasExpired(now))
    }
}
