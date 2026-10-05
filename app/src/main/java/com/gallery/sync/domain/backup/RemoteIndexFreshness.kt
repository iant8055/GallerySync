package com.gallery.sync.domain.backup

/**
 * Whether an album listing remembered from an earlier batch may be used again.
 *
 * Split out of `BackupEngine.remoteIndexCached` so the rule can be tested without a drive, a ledger
 * or a dispatcher. What it decides matters more than its size: a listing that is wrongly judged
 * fresh makes the app believe a file is in the Cloud when it may not be, and the file is then marked
 * backed up without being sent.
 *
 * It only ever answers "may this be reused". What is allowed into the cache in the first place —
 * never a failed listing, never a partial one — is decided at the call site, where the listing's own
 * outcome is known.
 */
object RemoteIndexFreshness {

    /**
     * True when [rememberedAtMillis] is recent enough and [rememberedRoot] is still where backups go.
     *
     * The root is compared because an index describes one place: change the destination and what was
     * learned about the old one says nothing about the new.
     *
     * Time is treated defensively. A clock that has gone backwards since the entry was stored — a
     * manual change, a network time correction — makes the age negative, and an age that is not a
     * sane positive number is not evidence of freshness, so the answer is no and the album is listed
     * again. Listing again costs a request; trusting a bad clock costs a backup.
     */
    fun isUsable(
        rememberedRoot: String,
        currentRoot: String,
        rememberedAtMillis: Long,
        nowMillis: Long,
        ttlMillis: Long
    ): Boolean {
        if (rememberedRoot != currentRoot) return false
        val age = nowMillis - rememberedAtMillis
        return age in 0 until ttlMillis
    }
}
