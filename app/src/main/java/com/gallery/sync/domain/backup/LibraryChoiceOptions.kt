package com.gallery.sync.domain.backup

/**
 * The cutoff that makes "optimise only what is not backed up yet" expressible.
 *
 * ### Why a timestamp rather than an album mode
 *
 * Ian's middle option at Gate 2, 28 Aug 2026: *"we back them up first and leave an opt file
 * behind"* — optimise the files still to be uploaded, and leave alone the thousands already safe in
 * OneDrive.
 *
 * Album modes cannot say that. They are per album, and this is per file: two photos in one folder,
 * one uploaded last year and one uploading tonight, get different answers. So the choice is stored
 * as a moment, and the candidate query asks whether a file's `uploadedAtEpochMillis` falls after it.
 *
 * [EVERYTHING] is zero — every file ever uploaded is eligible, which is the ordinary case and the
 * one the whole feature is for.
 *
 * ### What it is not
 *
 * Not an age gate. [MediaAge] asks how old the **file** is and protects recent footage from being
 * degraded before its owner has finished with it. This asks when the file was **backed up**, and
 * exists so a cautious user can decline a large one-off rewrite of a library they already have. A
 * file can pass one and fail the other.
 *
 * ### The number it needs beside it
 *
 * On a real first install most of the library is already in OneDrive, because Samsung's own sync put
 * it there — MILESTONES records 8,482 local files reducing to 206 actually needing upload. So this
 * option can touch about 2% of a library while sounding like it does something substantial, and the
 * screen offering it **must show both counts**. Without them the honest option looks broken.
 */
object OptimiseCutoff {

    /** No cutoff: everything already backed up is eligible. */
    const val EVERYTHING = 0L
}
