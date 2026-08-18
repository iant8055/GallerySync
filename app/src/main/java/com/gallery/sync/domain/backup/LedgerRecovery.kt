package com.gallery.sync.domain.backup

/**
 * Recognising work that was already done, when the ledger no longer remembers it.
 *
 * The ledger is lost by an uninstall and absent on a new phone. For whole files that costs
 * nothing: the same name and the same size in OneDrive is proof enough, and the upload is skipped.
 *
 * A proxy breaks that test. Its local copy is deliberately smaller than the original it came from,
 * so name-and-size never matches and the file looks unbacked-up — and uploading it would put a
 * 2048px copy in OneDrive beside the full-size original it was made from. The remote copy is
 * renamed rather than overwritten, so nothing is destroyed, but the user is left with a low-res
 * duplicate of every photo they optimised.
 */
object LedgerRecovery {

    /**
     * Whether a local file is a proxy of a remote original that is already backed up.
     *
     * All three conditions are required, and each rules out a different mistake:
     *
     *  - [carriesProxyMarker] — the file says GallerySync wrote it. Without this an ordinary photo
     *    that happens to share a name with something bigger in OneDrive would be skipped, and a
     *    real backup would silently never happen.
     *  - a same-named remote file exists at all.
     *  - the remote copy is *larger*. A proxy is always smaller than its original, so anything
     *    else means these two files are not what they appear to be, and uploading is the safe
     *    answer.
     */
    fun isBackedUpProxy(
        localSizeBytes: Long,
        remoteSizeBytes: Long?,
        carriesProxyMarker: Boolean
    ): Boolean =
        carriesProxyMarker && remoteSizeBytes != null && remoteSizeBytes > localSizeBytes
}
