package com.gallery.sync.domain.backup

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The cost of each wrong answer is asymmetric, and the tests are written around that.
 *
 * A wrong "yes" skips a real upload and the photo is never backed up — silent data loss. A wrong
 * "no" uploads something already present, which costs bandwidth and leaves a duplicate. So every
 * uncertain case must answer no.
 */
class LedgerRecoveryTest {

    @Test
    fun `a marked file smaller than its remote namesake is a backed-up proxy`() {
        assertTrue(
            LedgerRecovery.isBackedUpProxy(
                localSizeBytes = 282_868,
                remoteSizeBytes = 3_977_394,
                carriesProxyMarker = true
            )
        )
    }

    @Test
    fun `an unmarked file is never assumed to be a proxy`() {
        // An ordinary photo sharing a name with something larger in OneDrive. Skipping it would
        // mean it is never backed up at all.
        assertFalse(
            LedgerRecovery.isBackedUpProxy(
                localSizeBytes = 282_868,
                remoteSizeBytes = 3_977_394,
                carriesProxyMarker = false
            )
        )
    }

    @Test
    fun `nothing of that name in OneDrive means it still needs uploading`() {
        assertFalse(
            LedgerRecovery.isBackedUpProxy(
                localSizeBytes = 282_868,
                remoteSizeBytes = null,
                carriesProxyMarker = true
            )
        )
    }

    @Test
    fun `a remote copy no larger than the local one is not the original`() {
        // A proxy is always smaller than what it was made from. Equal sizes are already handled by
        // the plain name-and-size check; larger-local means these are not what they appear to be.
        assertFalse(
            LedgerRecovery.isBackedUpProxy(
                localSizeBytes = 3_977_394,
                remoteSizeBytes = 282_868,
                carriesProxyMarker = true
            )
        )
        assertFalse(
            LedgerRecovery.isBackedUpProxy(
                localSizeBytes = 282_868,
                remoteSizeBytes = 282_868,
                carriesProxyMarker = true
            )
        )
    }
}
