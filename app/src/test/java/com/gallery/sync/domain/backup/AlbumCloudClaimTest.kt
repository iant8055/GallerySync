package com.gallery.sync.domain.backup

import com.gallery.sync.data.local.entity.AlbumCloudStatusEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class AlbumCloudClaimTest {

    private val checkedAt = 1_756_400_000_000L

    private fun status(
        verified: Int = 0,
        missing: Int = 0,
        couldNotCheck: Boolean = false
    ) = AlbumCloudStatusEntity(
        albumName = "PauseTest",
        checkedAtEpochMillis = checkedAt,
        verifiedFiles = verified,
        missingFiles = missing,
        couldNotCheck = couldNotCheck
    )

    @Test
    fun `no stored answer is never checked, not zero`() {
        assertEquals(AlbumCloudClaim.NeverChecked, AlbumCloudClaim.from(null))
    }

    @Test
    fun `a failed listing is unreachable, not missing`() {
        assertEquals(
            AlbumCloudClaim.Unreachable(checkedAt),
            AlbumCloudClaim.from(status(couldNotCheck = true))
        )
    }

    @Test
    fun `everything present reports the verified count`() {
        assertEquals(
            AlbumCloudClaim.AllPresent(verified = 8, checkedAtEpochMillis = checkedAt),
            AlbumCloudClaim.from(status(verified = 8))
        )
    }

    @Test
    fun `a partly backed up album reports both numbers`() {
        assertEquals(
            AlbumCloudClaim.SomeMissing(verified = 5, missing = 3, checkedAtEpochMillis = checkedAt),
            AlbumCloudClaim.from(status(verified = 5, missing = 3))
        )
    }

    /** The case Ian created by hand: the folder deleted from OneDrive, every file now missing. */
    @Test
    fun `a deleted cloud folder reports everything missing rather than backed up`() {
        assertEquals(
            AlbumCloudClaim.SomeMissing(verified = 0, missing = 8, checkedAtEpochMillis = checkedAt),
            AlbumCloudClaim.from(status(verified = 0, missing = 8))
        )
    }

    /** Unreachable wins over any counts, because those counts describe nothing. */
    @Test
    fun `an unreachable album ignores whatever counts were stored with it`() {
        assertEquals(
            AlbumCloudClaim.Unreachable(checkedAt),
            AlbumCloudClaim.from(status(verified = 4, missing = 4, couldNotCheck = true))
        )
    }
}
