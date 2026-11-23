package com.gallery.sync.domain.backup

import com.gallery.sync.data.local.entity.BackupEntryEntity

/**
 * Which of the ledger's Restore rows OneDrive can still serve, judged against a listing of the drive.
 *
 * The Restore tab's rows start from the ledger (what this app sent and the phone no longer holds), and a
 * listing of OneDrive was only ever *added* to them. So a file deleted from OneDrive stayed on the tab for
 * ever: Moto G, 23 Nov 2026, OneDrive holding one folder (PauseTest) while the tab listed seventeen, with
 * 2,000-odd files offered for download that were no longer anywhere (Ian: *"the list of folders hasn't
 * updated"*).
 *
 * The rule is the Albums tab's ([MissingFromCloud.inOneDrive]): a row is gone when neither the id recorded at
 * upload nor its name is in its album's folder at the recorded size; an entry listed with no size is unknown,
 * and unknown is not gone. A folder the listing does not have at all is gone with everything in it.
 *
 * **Failing to ask is not evidence of absence.** With no listing, or a partial one, every row is kept. Rows
 * for any other Cloud are kept too: this listing says nothing about them. Nothing is written; a dropped row
 * only stops being offered.
 */
object RestoreStillInOneDrive {

    fun keep(rows: List<BackupEntryEntity>, listing: DriveListing?): List<BackupEntryEntity> {
        if (listing == null || listing.couldNotList) return rows
        val folders = listing.folders.mapKeys { it.key.lowercase() }
        return rows.filter { row ->
            if (row.location != BackupLocation.ONEDRIVE) return@filter true
            val index = folders[row.album.lowercase()] ?: return@filter false
            val size = row.remoteSizeBytes ?: row.sizeBytes
            val byId = row.remoteItemId?.let { id -> index.values.firstOrNull { it.id == id } }
            listOfNotNull(byId, index[row.displayName]).any { it.sizeBytes == null || it.sizeBytes == size }
        }
    }
}
