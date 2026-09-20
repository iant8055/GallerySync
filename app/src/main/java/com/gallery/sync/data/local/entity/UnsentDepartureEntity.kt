package com.gallery.sync.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A file that left the phone **without ever having been sent to OneDrive**, kept so the app can ask
 * what should become of it. Ian, 19 Sept 2026.
 *
 * ### Why this is its own table
 *
 * The ledger deliberately forgets a pending row whose file has gone (`forgetPendingFilesThatAreGone`):
 * a kept row makes the upload queue chase a file it cannot open, and every count that reads pending
 * rows would include work that no longer exists. That reasoning stands, so those rows are still
 * forgotten. This table is what is written first, so that forgetting the row no longer means
 * forgetting the file.
 *
 * Keeping the departure in the ledger instead, flagged missing, would have meant teaching every
 * pending query — the upload queue, the wizard's counts, the byte totals — to skip it. This way none
 * of them can see it.
 *
 * ### What it is for
 *
 * Ian's rule for the window that opens with the app is that it covers **all** deleted files, not
 * only the ones this app put in OneDrive. For a file the app never sent, the answer to "is there a
 * copy in the cloud?" comes from the drive, and the answer to "can it still be saved?" comes from the
 * phone's trash, whose bytes are still readable (measured on the Moto G, 19 Sept 2026).
 *
 * A row is removed the moment the file is decided — backed up, or left in the trash — and when the
 * file comes back to the phone. It is never a record of anything in OneDrive.
 */
@Entity(tableName = "unsent_departures")
data class UnsentDepartureEntity(

    /** The ledger key the row had, so a file that comes back can be matched to it. */
    @PrimaryKey val id: String,

    /**
     * Where the file was. Reading it through this URI is how a trashed file is uploaded: a trashed
     * item keeps its MediaStore id and stays readable.
     */
    val mediaStoreId: Long,
    val contentUri: String,
    val displayName: String,
    val album: String,
    val sizeBytes: Long,
    val dateModifiedEpochSeconds: Long,
    val mimeType: String,
    val isVideo: Boolean,

    /** When a scan first found the file gone. Not when it was deleted, which the phone does not tell us. */
    val goneSinceEpochMillis: Long
)
