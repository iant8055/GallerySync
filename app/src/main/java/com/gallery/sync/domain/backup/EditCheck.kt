package com.gallery.sync.domain.backup

/**
 * Whether a file this app shrank has been changed on the phone since, so that putting the original back
 * over it would throw a person's edit away.
 *
 * Measured on the Moto G, 26 Sept 2026 (Ian asked for the test): an in-place edit of an optimised photo
 * keeps its MediaStore id, so the upload scan skips it, and Restore then replaced it with the Cloud original
 * without a word. The ledger records the size of the copy the app wrote (`localProxySizeBytes`); a re-save
 * in any editor changes the byte count, so a file that is no longer that size is one somebody edited.
 *
 * Unknown is not edited: if either size is missing the answer is no, and Restore proceeds as it always did.
 * A file that is not a proxy is never "edited" here, because Restore does not replace those.
 */
object EditCheck {

    fun isEdited(isProxied: Boolean, recordedProxySizeBytes: Long?, currentSizeBytes: Long?): Boolean =
        isProxied &&
            recordedProxySizeBytes != null &&
            currentSizeBytes != null &&
            currentSizeBytes != recordedProxySizeBytes

    /**
     * The ledger scan's form of the same rule: a file on a proxied row whose size matches neither the copy the app
     * wrote ([proxySizeBytes]) nor the original ([originalSizeBytes]) is an edit.
     *
     * The original counts as "not edited" on purpose. The proxy path does not wait for MediaStore to catch up
     * after it writes, so for a moment the index still reports the original's size for a file that has already
     * been shrunk. Calling that an edit would send every shrunk photo to the Cloud as a new file. An edit that
     * lands on exactly the original's byte count is the price, and it is never worth a duplicate library.
     */
    fun isEditedNow(currentSizeBytes: Long, originalSizeBytes: Long, proxySizeBytes: Long?): Boolean =
        proxySizeBytes != null && currentSizeBytes != proxySizeBytes && currentSizeBytes != originalSizeBytes

    /**
     * More than this many "edits" in one scan, and more than one in twenty of the shrunk files, is not a person
     * editing. It is a stale or wrong index, and the scan stands down instead of uploading a copy of the library.
     */
    const val MAX_EDITS_PER_SCAN = 10
    const val MAX_EDIT_FRACTION_DENOMINATOR = 20
}
