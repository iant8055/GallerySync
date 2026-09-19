package com.gallery.sync.domain.backup

/**
 * One file OneDrive holds, offered back to the user.
 *
 * ### Why this is not a ledger row
 *
 * Retrieval used to read `observeRetrievable()`, which offers a file only when it is in our ledger
 * **and** already gone from the phone. That answers "what have I lost from this device?", which is a
 * narrower question than the one a restore feature promises to answer.
 *
 * The case that settles it is a **new phone**. The ledger records what left *this* device, so on a
 * fresh install it is empty by construction — the user signs in, their whole library is sitting in
 * OneDrive, and a ledger-driven list offers them nothing at all. That is the moment someone most
 * wants a restore, and it is exactly the moment the ledger knows least. Ian, 25 Aug 2026: *if we are
 * going to offer restore then we need to be able to restore any file, not just the ones we backed
 * up, synced or archived.*
 *
 * So this is built from the remote listing. The ledger keeps deciding what to upload and what may
 * safely be removed, and stops gatekeeping what may be fetched.
 *
 * ### [alreadyOnDevice] is a label, not a veto
 *
 * A file still on the phone is shown and marked, never hidden and never disabled. Retrieving one is
 * allowed — it is simply a deliberate act rather than something that quietly produces a second copy
 * of six videos. What lands carries `RestoredAlbum.SUFFIX`, which is what keeps the two apart.
 */
data class RestorableFile(
    val remoteItemId: String,
    val displayName: String,
    val mimeType: String,
    val sizeBytes: Long,
    /** Matched on name and size with any `_restored` suffix stripped. See `RestoredAlbum`. */
    val alreadyOnDevice: Boolean
) {
    val isVideo: Boolean get() = mimeType.startsWith("video/")
}

/**
 * What OneDrive listed, folder by folder: the slow, network half of the Restore tab's answer.
 *
 * Kept apart from the comparison with the phone so it can be **cached**. Listing the drive is the
 * expensive step, and the tab re-reads it on every entry unless it is held on to; comparing a held
 * listing with the phone as it is now costs a scan and stays correct after an archive or a restore
 * has changed the phone, which a held *answer* would not.
 */
data class DriveListing(
    /** Files by name, per OneDrive folder that could be listed. */
    val folders: Map<String, Map<String, RemoteFileRef>>,
    /** The folder list, or one of the folders, could not be read. Then `folders` is incomplete. */
    val couldNotList: Boolean
)

/**
 * What OneDrive holds that the ledger has no row for, as the Restore tab needs it.
 *
 * Both lists are **transient entities**, never inserted: a row for a file that was never on this phone
 * would sit in the ledger with its local copy "missing", which is exactly the shape the cloud-deletion
 * review looks for. A row is written only when a download succeeds, when the file is really here.
 */
data class DriveRestoreFiles(
    /** In OneDrive, not on the phone, unknown to the ledger. Offered as downloads. */
    val missing: List<com.gallery.sync.data.local.entity.BackupEntryEntity>,
    /** In OneDrive and on the phone at full size. Shown greyed out, so the tab explains itself. */
    val here: List<com.gallery.sync.data.local.entity.BackupEntryEntity>,
    /** A folder could not be listed. What is shown is then incomplete, not "nothing to restore". */
    val couldNotList: Boolean
) {
    companion object {
        val NONE = DriveRestoreFiles(emptyList(), emptyList(), couldNotList = false)
    }
}

/**
 * One cloud folder, as the restore screen lists it.
 *
 * ### Both counts are cheap, and neither is an identity claim
 *
 * [fileCount] and [sizeBytes] come from the folder item Graph already returns — no extra request.
 * [onDeviceCount] comes from one local scan grouped by album name, also no request.
 *
 * They are therefore two independent counts of things that share a name, **not** a statement that
 * those particular files match. Seven in OneDrive and six here means one of them is worth opening
 * the folder to look at; it does not say which, and the screen must not pretend otherwise. The
 * per-file view answers that properly, by comparing content signatures.
 *
 * [fileCount] counts every child Graph reports, which includes any sub-folders. Media folders rarely
 * nest, and over-counting by a sub-folder is a cosmetic error where listing ninety folders to avoid
 * it is a screen nobody waits for.
 */
data class RestorableFolder(
    val name: String,
    val fileCount: Int,
    val sizeBytes: Long,
    val onDeviceCount: Int
) {
    /** Nothing in it, so there is nothing to restore and nothing to compare against. */
    val isEmpty: Boolean get() = fileCount == 0

    /** True when the phone appears to hold everything the folder does. */
    val looksComplete: Boolean get() = fileCount > 0 && onDeviceCount >= fileCount
}
