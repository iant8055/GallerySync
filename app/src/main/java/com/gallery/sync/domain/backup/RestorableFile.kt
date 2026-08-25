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
