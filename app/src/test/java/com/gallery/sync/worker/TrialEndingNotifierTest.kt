package com.gallery.sync.worker

import com.gallery.sync.domain.billing.MultiCloudTrial
import com.gallery.sync.worker.TrialEndingNotifier.Companion.STAGE_ENDED
import com.gallery.sync.worker.TrialEndingNotifier.Companion.STAGE_ENDING
import com.gallery.sync.worker.TrialEndingNotifier.Companion.nextNotice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TrialEndingNotifierTest {

    @Test
    fun `nothing while more than three days are left`() {
        assertNull(nextNotice(MultiCloudTrial.State.Active(4), 0, pairedElsewhere = true))
        assertNull(nextNotice(MultiCloudTrial.State.Active(30), 0, pairedElsewhere = true))
    }

    @Test
    fun `ends soon once, at three days or fewer`() {
        assertEquals(STAGE_ENDING, nextNotice(MultiCloudTrial.State.Active(3), 0, pairedElsewhere = true))
        assertEquals(STAGE_ENDING, nextNotice(MultiCloudTrial.State.Active(1), 0, pairedElsewhere = true))
        assertNull(nextNotice(MultiCloudTrial.State.Active(1), STAGE_ENDING, pairedElsewhere = true))
    }

    @Test
    fun `ended once, with or without the warning before it`() {
        assertEquals(STAGE_ENDED, nextNotice(MultiCloudTrial.State.Ended, STAGE_ENDING, pairedElsewhere = true))
        assertEquals(STAGE_ENDED, nextNotice(MultiCloudTrial.State.Ended, 0, pairedElsewhere = true))
        assertNull(nextNotice(MultiCloudTrial.State.Ended, STAGE_ENDED, pairedElsewhere = true))
    }

    @Test
    fun `nobody is told when no folder goes to an extra Cloud`() {
        assertNull(nextNotice(MultiCloudTrial.State.Active(2), 0, pairedElsewhere = false))
        assertNull(nextNotice(MultiCloudTrial.State.Ended, 0, pairedElsewhere = false))
    }

    @Test
    fun `a trial never started is never announced`() {
        assertNull(nextNotice(MultiCloudTrial.State.NotStarted, 0, pairedElsewhere = true))
    }
}
