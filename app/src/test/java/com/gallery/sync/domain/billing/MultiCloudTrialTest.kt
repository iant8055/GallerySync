package com.gallery.sync.domain.billing

import com.gallery.sync.domain.billing.MultiCloudTrial.State
import org.junit.Assert.assertEquals
import org.junit.Test

class MultiCloudTrialTest {

    private val day = 24L * 60 * 60 * 1000
    private val start = 1_000_000_000_000L

    @Test
    fun `never started is on offer`() {
        assertEquals(State.NotStarted, MultiCloudTrial.stateOf(null, start))
    }

    @Test
    fun `the first day has thirty days left`() {
        assertEquals(State.Active(30), MultiCloudTrial.stateOf(start, start))
    }

    @Test
    fun `a partial day counts as a day left`() {
        assertEquals(State.Active(1), MultiCloudTrial.stateOf(start, start + 29 * day + 1))
    }

    @Test
    fun `exactly thirty days on it has ended`() {
        assertEquals(State.Ended, MultiCloudTrial.stateOf(start, start + 30 * day))
    }

    @Test
    fun `long after it stays ended`() {
        assertEquals(State.Ended, MultiCloudTrial.stateOf(start, start + 400 * day))
    }

    @Test
    fun `a clock set back before the start does not extend the trial`() {
        assertEquals(State.Active(30), MultiCloudTrial.stateOf(start, start - 5 * day))
    }
}
