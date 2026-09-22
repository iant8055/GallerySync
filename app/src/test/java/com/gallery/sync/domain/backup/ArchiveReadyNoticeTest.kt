package com.gallery.sync.domain.backup

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * When the "files have come of age" notification is worth sending. Ian, 22 Sept 2026.
 *
 * Grown, not merely nonzero — see the class doc. A batch that just sits there, unchanged, must not
 * notify on every check.
 */
class ArchiveReadyNoticeTest {

    @Test
    fun `a new file joining the batch is worth notifying about`() {
        assertTrue(ArchiveReadyNotice.shouldNotify(lastSeenCount = 3, currentCount = 5))
        assertTrue(ArchiveReadyNotice.shouldNotify(lastSeenCount = 0, currentCount = 1))
    }

    @Test
    fun `an unchanged batch does not notify again`() {
        assertFalse(ArchiveReadyNotice.shouldNotify(lastSeenCount = 5, currentCount = 5))
        assertFalse(ArchiveReadyNotice.shouldNotify(lastSeenCount = 0, currentCount = 0))
    }

    @Test
    fun `a shrunk batch does not notify, even though it changed`() {
        assertFalse(ArchiveReadyNotice.shouldNotify(lastSeenCount = 5, currentCount = 2))
        assertFalse(ArchiveReadyNotice.shouldNotify(lastSeenCount = 5, currentCount = 0))
    }

    @Test
    fun `growth past a lowered baseline notifies — files can archive, then grow again`() {
        // lastSeenCount already reflects the drop to 2 (the caller persists it every call), so
        // growing from there to 4 is new growth worth a notification.
        assertTrue(ArchiveReadyNotice.shouldNotify(lastSeenCount = 2, currentCount = 4))
    }
}
