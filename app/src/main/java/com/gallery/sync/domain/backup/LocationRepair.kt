package com.gallery.sync.domain.backup

/**
 * Putting the location back into copies already in the Cloud. Ian, 9 Oct 2026: *"build repair"*.
 *
 * Every file this app sent before the location fix (see `OriginalMedia`) reached the Cloud with its GPS blanked, at
 * exactly the original's size. The repair sends those files again, from the phone, **replacing** each Cloud copy
 * with the full-size original. Only a file that can be repaired is touched:
 *
 * - it is still on the phone, at full size: not optimised, not archived, the same size the ledger recorded, and
 *   carrying no proxy marker. An optimised copy must never replace an original, which is the failure the
 *   app's whole "never replace" rule exists to prevent;
 * - it actually has a location in it. A photo taken with location off gains nothing and is not sent;
 * - it was sent before the fix ([com.gallery.sync.data.local.settings.BackupPreferences.locationFixedAt]);
 * - its Cloud replaces a file of the same name in place: OneDrive (and then only the item of exactly the same
 *   size, named by its eTag), Dropbox, pCloud, IDrive e2 and Backblaze B2. Google Drive would file a second copy
 *   beside the first and Google Photos a duplicate, so neither is repaired.
 *
 * It removes nothing anywhere. The Cloud copy it replaces had the same bytes apart from the blanked location.
 */
object LocationRepair {

    /** The Clouds whose upload replaces a same-named file in place. Matches [repairs]. */
    const val SQL_LIST = "'ONEDRIVE','DROPBOX','PCLOUD','IDRIVE_E2','BACKBLAZE_B2'"

    fun repairs(location: BackupLocation): Boolean = location in setOf(
        BackupLocation.ONEDRIVE,
        BackupLocation.DROPBOX,
        BackupLocation.PCLOUD,
        BackupLocation.IDRIVE_E2,
        BackupLocation.BACKBLAZE_B2
    )

    /** Files per worker run. Each is one upload of a full-size original. */
    const val BATCH = 25
}

/** What one repair batch did. [lastId] is where the next batch starts; [stopped] means try again later. */
data class LocationRepairBatch(
    val checked: Int,
    val repaired: Int,
    val noLocation: Int,
    val skipped: Int,
    val failed: Int,
    val lastId: String?,
    val done: Boolean,
    val stopped: Boolean
)
