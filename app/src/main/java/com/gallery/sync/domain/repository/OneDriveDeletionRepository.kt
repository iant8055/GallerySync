package com.gallery.sync.domain.repository

import com.gallery.sync.domain.model.DataResult

/**
 * Moves a cloud file to the OneDrive recycle bin.
 *
 * ### Its own interface, deliberately
 *
 * [OneDriveRepository] is read-only and [OneDriveUploadRepository] adds files. Neither can delete,
 * and neither should gain the ability — the same reasoning that split uploading out from browsing,
 * applied to the one capability where a mistake costs a photo. A caller that browses, or backs up,
 * or fetches a file back, cannot be handed this by accident.
 *
 * ### What it does and does not do
 *
 * A delete here is a **soft** delete: Graph moves the item to the drive's recycle bin, where the
 * user can restore it. CLAUDE.md permits that and forbids everything stronger — nothing in this
 * interface may ever empty a bin, permanently delete, or bypass the bin.
 *
 * Nothing here may be called on a timer, from a worker, or as a consequence of a scan. It exists to
 * be called immediately after a person has looked at a list of files and said yes.
 */
interface OneDriveDeletionRepository {

    /**
     * Sends one item to the recycle bin.
     *
     * A 404 counts as success: the file is already not in the drive, which is the state the caller
     * was asking for. Reporting that as a failure would leave a ledger row describing a file that
     * exists nowhere, and invite the user to try again forever.
     */
    suspend fun moveToRecycleBin(itemId: String): DataResult<Unit>
}
