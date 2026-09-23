package com.gallery.sync.data.remote.auth

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class GoogleSignInResultBridgeTest {

    @Test
    fun `a delivered result reaches the matching wait`() = runTest {
        val bridge = GoogleSignInResultBridge()
        val deferred = bridge.beginWait()

        bridge.deliver(SignInResult.Success("Google Photos"))

        assertEquals(SignInResult.Success("Google Photos"), deferred.await())
    }

    @Test
    fun `a second wait replaces the first, leaving it unresolved`() = runTest {
        val bridge = GoogleSignInResultBridge()
        val abandoned = bridge.beginWait()
        val current = bridge.beginWait()

        bridge.deliver(SignInResult.Cancelled)

        assertEquals(SignInResult.Cancelled, current.await())
        assertFalse("the superseded wait must not be silently completed too", abandoned.isCompleted)
    }

    @Test
    fun `delivering with nothing waiting is a no-op, not a crash`() = runTest {
        val bridge = GoogleSignInResultBridge()

        bridge.deliver(SignInResult.Failed("no_response"))
        // Reaching this line without an exception is the assertion.
    }
}
