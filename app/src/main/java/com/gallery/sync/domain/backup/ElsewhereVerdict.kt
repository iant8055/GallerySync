package com.gallery.sync.domain.backup

import com.gallery.sync.domain.repository.RemoteCheck

/**
 * What Archive concludes about one file whose cloud copy is not in OneDrive — TASK-027.
 *
 * Pure, so the rule that decides whether a file may leave the phone is unit tested rather than read off a screen.
 * Only [CONFIRMED] permits removal, and it needs the cloud to have answered, live, that the file is there at the
 * size the phone holds. **If we could not ask, we do not remove**: every other path lands on a verdict that keeps
 * the file.
 */
enum class ElsewhereVerdict {

    /** The cloud has it, at the phone's size. The only verdict that permits removal. */
    CONFIRMED,

    /** The cloud has a file of that name at another size. It protects nothing, and the screen says so. */
    WRONG_SIZE,

    /** Not there, or not uploaded yet. Work to do, not a verdict: the Archive tab backs the file up and asks again. */
    MISSING,

    /** No adapter to ask, or the cloud gave no clear answer. Not evidence either way. */
    UNCONFIRMED;

    companion object {

        fun of(uploaded: Boolean, hasVerifier: Boolean, check: RemoteCheck?, expectedBytes: Long): ElsewhereVerdict = when {
            !uploaded -> MISSING
            !hasVerifier -> UNCONFIRMED
            check is RemoteCheck.Present ->
                if (check.sizeBytes == expectedBytes && expectedBytes > 0L) CONFIRMED else WRONG_SIZE
            check == RemoteCheck.Gone -> MISSING
            else -> UNCONFIRMED
        }
    }
}
