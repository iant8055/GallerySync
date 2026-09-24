package com.gallery.sync.domain.billing

import com.gallery.sync.data.local.settings.BackupPreferences
import com.gallery.sync.data.local.settings.BackupSettings
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class MultiCloudEntitlementTest {

    private val day = 24L * 60 * 60 * 1000
    private val start = 1_000_000_000_000L
    private val billing: BillingRepository = mock()
    private val settings: BackupSettings = mock()
    private val entitlement = MultiCloudEntitlement(billing, settings)

    private suspend fun given(purchased: Boolean, trialStart: Long?) {
        whenever(billing.isPurchased()).thenReturn(purchased)
        whenever(settings.current()).thenReturn(BackupPreferences(multiCloudTrialStartedAtEpochMillis = trialStart))
    }

    @Test
    fun `bought is entitled whatever the trial says`() = runTest {
        given(purchased = true, trialStart = start - 100 * day)
        assertTrue(entitlement.isEntitled(now = start))
    }

    @Test
    fun `inside the trial is entitled without a purchase`() = runTest {
        given(purchased = false, trialStart = start)
        assertTrue(entitlement.isEntitled(now = start + 10 * day))
    }

    @Test
    fun `an ended trial with no purchase is the hard gate`() = runTest {
        given(purchased = false, trialStart = start)
        assertFalse(entitlement.isEntitled(now = start + 31 * day))
        assertEquals(MultiCloudTrial.State.Ended, entitlement.trialState(now = start + 31 * day))
    }

    @Test
    fun `a trial never started is not entitled`() = runTest {
        given(purchased = false, trialStart = null)
        assertFalse(entitlement.isEntitled(now = start))
    }
}
