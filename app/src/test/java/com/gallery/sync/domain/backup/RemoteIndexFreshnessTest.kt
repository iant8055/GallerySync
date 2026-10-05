package com.gallery.sync.domain.backup

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The rule that decides whether an album listing from an earlier batch may be reused.
 *
 * The cases that matter are the ones where saying "yes" wrongly means a file is marked backed up
 * without being sent: a stale entry, an entry from a different destination, and a clock that has
 * moved backwards.
 */
class RemoteIndexFreshnessTest {

    private val ttl = 10L * 60L * 1000L
    private val root = "MotoG/Gallery"

    @Test
    fun `a recent listing from the same root is reused`() {
        assertTrue(
            RemoteIndexFreshness.isUsable(
                rememberedRoot = root,
                currentRoot = root,
                rememberedAtMillis = 1_000_000L,
                nowMillis = 1_000_000L + 60_000L,
                ttlMillis = ttl
            )
        )
    }

    @Test
    fun `a listing taken this instant is reused`() {
        assertTrue(
            RemoteIndexFreshness.isUsable(root, root, 1_000_000L, 1_000_000L, ttl)
        )
    }

    @Test
    fun `a listing older than the window is listed again`() {
        assertFalse(
            RemoteIndexFreshness.isUsable(root, root, 1_000_000L, 1_000_000L + ttl + 1L, ttl)
        )
    }

    /** The window is exclusive at its end, so the entry expires rather than lingering a moment longer. */
    @Test
    fun `a listing exactly at the window is listed again`() {
        assertFalse(
            RemoteIndexFreshness.isUsable(root, root, 1_000_000L, 1_000_000L + ttl, ttl)
        )
    }

    @Test
    fun `a listing from another destination root is never reused`() {
        assertFalse(
            RemoteIndexFreshness.isUsable(
                rememberedRoot = "MotoG/Gallery",
                currentRoot = "Fold/Gallery",
                rememberedAtMillis = 1_000_000L,
                nowMillis = 1_000_000L + 1L,
                ttlMillis = ttl
            )
        )
    }

    /**
     * A clock that has gone backwards makes the age negative. That is not evidence of freshness, and
     * reading it as such would reuse an entry of unknown age — so the album is listed again.
     */
    @Test
    fun `a clock that has gone backwards forces a fresh listing`() {
        assertFalse(
            RemoteIndexFreshness.isUsable(root, root, 1_000_000L, 999_000L, ttl)
        )
    }

    /** A zero window turns the cache off: every album is listed every time. */
    @Test
    fun `a zero window reuses nothing`() {
        assertFalse(
            RemoteIndexFreshness.isUsable(root, root, 1_000_000L, 1_000_000L, 0L)
        )
    }
}
