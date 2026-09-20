package com.gallery.sync.domain.backup

/**
 * Whether so many files have gone missing at once that the scan, not the user, is the likelier cause.
 *
 * A gallery app rebuilding its media index after a system update, a revoked or narrowed permission
 * that still reads as "some", or a volume that is briefly not mounted can each make a large share of
 * the library look deleted in one scan, and none of them is a deletion. The scan guards already
 * refuse an empty result and partial access; this catches the case that slips between them, a scan
 * that is short but not empty.
 *
 * When it trips, **nothing is offered**. That errs toward leaving OneDrive copies alone, which is the
 * setting hardest to regret, and it costs a real mass deletion (a phone cleaner, say) being invisible
 * to the window until the count falls back under the line. It is a net, and deliberately narrow: more
 * than [MIN_FILES] files, and more than half of everything ever uploaded.
 *
 * Pure so it is tested without a device.
 */
object MassAbsence {

    /** Below this many files it is an ordinary tidy-up, however large a share of a small library. */
    const val MIN_FILES = 20

    fun looksLikeABadScan(missing: Int, uploaded: Int): Boolean =
        missing > MIN_FILES && missing * 2 > uploaded
}
