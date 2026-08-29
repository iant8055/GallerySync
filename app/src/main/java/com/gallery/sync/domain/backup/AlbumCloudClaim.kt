package com.gallery.sync.domain.backup

import com.gallery.sync.data.local.entity.AlbumCloudStatusEntity

/**
 * What an album row is entitled to say about OneDrive.
 *
 * The row used to say *"N backed up"* from `SUM(CASE WHEN state = UPLOADED)` over the local ledger —
 * a record of what this phone once sent, stated in the present tense about a drive nobody had asked.
 * Ian deleted a backup folder by hand on 28 Aug 2026 and the row kept claiming eight files were
 * backed up. His conclusion, and it is the rule this type exists to enforce: *"if the Album tab
 * never syncs with Cloud then it should not proclaim X files backed up."*
 *
 * So the claim is derived from the drive's own answer, and [NeverChecked] is a first-class state
 * rather than a zero. An album nobody has checked gets a sentence saying so, not a number borrowed
 * from somewhere else to fill the gap.
 *
 * Pure and Android-free, so the rule is unit tested rather than eyeballed in a screenshot.
 */
sealed interface AlbumCloudClaim {

    /** Never asked. Not zero, not a failure — unknown, and the row must say so. */
    data object NeverChecked : AlbumCloudClaim

    /** Asked, and the drive could not be reached. Different from never having asked. */
    data class Unreachable(val checkedAtEpochMillis: Long) : AlbumCloudClaim

    /** Asked, and the drive holds everything this album has. */
    data class AllPresent(val verified: Int, val checkedAtEpochMillis: Long) : AlbumCloudClaim

    /** Asked, and some of it is not there at the right size. */
    data class SomeMissing(
        val verified: Int,
        val missing: Int,
        val checkedAtEpochMillis: Long
    ) : AlbumCloudClaim

    companion object {

        /**
         * Reads the stored answer, or [NeverChecked] when there is none.
         *
         * Note what is deliberately absent: the ledger. Nothing in this function can see how many
         * rows say `UPLOADED`, which is what makes it impossible to accidentally reintroduce the
         * claim it was written to remove.
         */
        fun from(status: AlbumCloudStatusEntity?): AlbumCloudClaim = when {
            status == null -> NeverChecked
            status.couldNotCheck -> Unreachable(status.checkedAtEpochMillis)
            status.missingFiles > 0 ->
                SomeMissing(status.verifiedFiles, status.missingFiles, status.checkedAtEpochMillis)
            else -> AllPresent(status.verifiedFiles, status.checkedAtEpochMillis)
        }
    }
}
