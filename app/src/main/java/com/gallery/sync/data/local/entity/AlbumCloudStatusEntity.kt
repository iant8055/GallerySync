package com.gallery.sync.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * What OneDrive said about one album, the last time it was actually asked.
 *
 * ### Why this is its own table
 *
 * [AlbumPreferenceEntity] carries the modes, and its own documentation says it is *"the one part of
 * the database that cannot be rebuilt"* — pure user intent, existing nowhere else. Everything here
 * is the opposite: derived, disposable, and reproducible by asking the drive again. Putting a cache
 * in the table that must survive a phone move would make the careful thing and the throwaway thing
 * share a schema.
 *
 * ### What it is for
 *
 * The Albums tab said *"N backed up"* from the ledger alone — a `SUM(CASE WHEN state = UPLOADED)`
 * over local rows, which is a record of what this phone once sent, not a statement about what the
 * drive holds now. Ian deleted a backup folder from OneDrive by hand on 28 Aug 2026 and the row went
 * on claiming eight files were backed up, because nothing in that screen had ever asked. His words:
 * *"if the Album tab never syncs with Cloud then it should not proclaim X files backed up."*
 *
 * The reconciliation already walked every album against the drive and already computed exactly these
 * numbers per album — `ReconcileWithCloud` summed them into a total for the setup wizard and dropped
 * the per-album detail on the floor. This table keeps it.
 *
 * ### Absence is a state, and it is the important one
 *
 * No row means this album has never been checked against the drive. That is not zero and it is not
 * a failure; it is "we do not know", and the row must say so rather than borrowing the ledger's
 * number to fill the space.
 */
@Entity(tableName = "album_cloud_status")
data class AlbumCloudStatusEntity(

    @PrimaryKey val albumName: String,

    /** When the drive was last asked about this album. */
    val checkedAtEpochMillis: Long,

    /** Files the drive confirmed by name and matching size. */
    val verifiedFiles: Int,

    /** Files the drive was asked about and does not hold at the right size. */
    val missingFiles: Int,

    /**
     * True when the album's listing failed, so the numbers above describe nothing.
     *
     * Kept rather than simply not writing a row, because "we tried at 4pm and could not reach the
     * drive" and "we have never tried" are different things to tell somebody.
     */
    val couldNotCheck: Boolean
)
