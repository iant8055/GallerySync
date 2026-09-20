package com.gallery.sync.domain.backup

/**
 * What happens to the OneDrive copy when a file leaves the phone.
 *
 * ### Why there is no automatic option
 *
 * Ian asked on 25 Aug 2026 for three: leave, ask, and delete automatically. The third was dropped,
 * and the reason is worth keeping next to the type.
 *
 * CLAUDE.md is explicit that deleting a photo from the phone does not delete its backup unless the
 * user confirms **that specific action**, and MILESTONES names silent bidirectional delete as the
 * Samsung behaviour this project exists to replace. But the stronger objection is mechanical rather
 * than rhetorical: to delete automatically the app must *notice* a file has gone, and the only
 * signal available is absence from a scan. v0.4 says "never infers deletion from absence alone" —
 * and an unmounted card, a revoked permission or a partial scan are indistinguishable from a
 * deletion. An automatic mode would turn a bad scan into cloud deletions across a whole library.
 *
 * [ASK] keeps a human between that inference and the consequence, which is the entire safeguard.
 */
enum class CloudDeletionPolicy {

    /**
     * Nothing is ever removed from OneDrive. The default, and the setting hardest to regret.
     *
     * A cloud copy left behind costs storage. A cloud copy removed in error costs the photo, since
     * the local one is already gone — that asymmetry decides the default on its own.
     */
    LEAVE,

    /**
     * Offer to remove cloud copies of files that have left the phone, and act only on a yes.
     *
     * Batched, never per-file: a prompt for each of eight hundred photos is not consent, it is a
     * queue someone taps through. One prompt naming the count and the total size is a decision that
     * can actually be made.
     */
    ASK;

    companion object {
        val DEFAULT = LEAVE
    }
}
