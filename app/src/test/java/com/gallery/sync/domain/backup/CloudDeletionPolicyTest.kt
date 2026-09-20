package com.gallery.sync.domain.backup

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * What happens to the OneDrive copy when a file leaves the phone.
 *
 * (These lived in `CloudDeletionGraceTest` beside the waiting period, which was removed on
 * 19 Sept 2026 when the review moved into a window that opens with the app.)
 */
class CloudDeletionPolicyTest {

    @Test
    fun theDefaultPolicyLeavesCloudCopiesAlone() {
        assertEquals(CloudDeletionPolicy.LEAVE, CloudDeletionPolicy.DEFAULT)
    }

    /**
     * There is no automatic option, and there must not be one. To delete automatically the app has
     * to infer a deletion from a scan, and a bad scan would then reach the whole library.
     */
    @Test
    fun thereIsNoAutomaticPolicy() {
        assertEquals(
            listOf(CloudDeletionPolicy.LEAVE, CloudDeletionPolicy.ASK),
            CloudDeletionPolicy.entries.toList()
        )
    }
}
