package com.gallery.sync.domain.backup

/**
 * Decides what the Restore tab may offer, and it asks a different question from the deletion guard.
 *
 * ### Two questions that look like one
 *
 * "Has this file left the phone?" has two defensible answers, and this project needs both:
 *
 * - **Is this content anywhere on the phone?** Name and size, folder ignored. What
 *   `localMissingSinceEpochMillis` records, and what `cloudDeletionCandidates` keys on. Deliberately
 *   the cautious reading: a file the user still has *somewhere* must never put its cloud copy in
 *   line for removal.
 * - **Is this file in the folder it belongs to?** Album, name and size. What Restore needs, because
 *   a copy in an unrelated album is not an answer to "get that album back".
 *
 * They came apart on the Moto G, 28 Aug 2026. Ian archived eight videos from `PauseTest` while
 * byte-identical copies sat in `BudgetVideo`; the content test said the files were still on the
 * phone, nothing was marked, and the Restore tab offered him none of what he had just archived.
 *
 * The tempting fix — make the marking stricter — would have quietly widened what is eligible for
 * deletion from OneDrive, since the same column feeds both. So the column keeps the cautious answer
 * and Restore asks its own question here, where being wrong costs a redundant download rather than
 * a cloud copy.
 *
 * Pure, so the rule is testable without a device.
 */
object RestoreScope {

    /** Album, name and size — the same file, in the folder it is supposed to be in. */
    fun signature(album: String, displayName: String, sizeBytes: Long): String =
        "$album/$displayName|$sizeBytes"

    /**
     * The size a row's file has **on the phone**, which is what a device scan reports.
     *
     * For an optimised file that is the proxy, not the original: the ledger keeps the original's size
     * in `sizeBytes` (it is what the cloud copy is checked against) and the proxy's in
     * `localProxySizeBytes`. Comparing the original against a scan would never match a proxy that is
     * still in its folder, and would offer to download a file the user is looking at.
     *
     * Added 18 Sept 2026 with the fix that lets an archived proxy appear on the Restore tab at all.
     * Until then proxied rows were left out of the download list wholesale, which hid this problem
     * and also hid every optimised file that had been archived.
     */
    fun onDiskSizeBytes(isProxied: Boolean, localProxySizeBytes: Long?, sizeBytes: Long): Long =
        if (isProxied && localProxySizeBytes != null) localProxySizeBytes else sizeBytes

    /**
     * Which of [candidates] are not present on the device, judged per folder.
     *
     * [presentOnDevice] is every [signature] the scan found. **An empty set returns nothing**, never
     * everything: a scan that failed is not evidence that the phone has been wiped, and the caller
     * would otherwise offer the user's entire library as missing. Same guard, same reason, as
     * `markWhatIsNoLongerOnTheDevice`.
     */
    fun <T> notOnTheDevice(
        candidates: List<T>,
        presentOnDevice: Set<String>,
        signatureOf: (T) -> String
    ): List<T> {
        if (presentOnDevice.isEmpty()) return emptyList()
        return candidates.filterNot { signatureOf(it) in presentOnDevice }
    }
}
