package com.gallery.sync.domain.backup

/**
 * Files whose upload keeps being interrupted without getting anywhere, so one file cannot hold up the queue for ever
 * (Ian, 23 Nov 2026: *"Some kind of loop counter ... to first flag this file to the user and then bypass it so other
 * files can continue"*).
 *
 * Android stops a backup run after about ten minutes and the next run carries on; that is normal, and a file that
 * resumes is not stuck. An interruption counts here only when it gained nothing that will be kept: for OneDrive, no
 * byte confirmed by the server since the run started on that file; for the other Clouds, every interruption mid-file,
 * because none of them resumes yet, so whatever was sent is lost. Found on the Fold 8: an 8.3 GB video reset to zero
 * every twenty minutes, because a live OneDrive session was being read as expired (fixed in `ChunkedUploader`).
 *
 * After [STALLED_AFTER] such interruptions in a row the file is **stalled**: it goes to the end of each batch, so every
 * other file is sent first, and its album page says it could not finish uploading. It is never dropped and never
 * marked failed; it keeps being tried after the rest. Any interruption that does gain ground, or a finished upload,
 * clears it.
 *
 * Kept as `"<row id>\t<count>"` strings in settings: bookkeeping, no schema change.
 */
object UploadStalls {

    const val STALLED_AFTER = 2

    fun decode(stored: Set<String>): Map<String, Int> = stored.mapNotNull { line ->
        val tab = line.lastIndexOf('\t')
        if (tab <= 0) return@mapNotNull null
        val count = line.substring(tab + 1).toIntOrNull() ?: return@mapNotNull null
        line.substring(0, tab) to count
    }.toMap()

    fun encode(counts: Map<String, Int>): Set<String> =
        counts.filterValues { it > 0 }.mapTo(HashSet()) { (id, n) -> "$id\t$n" }

    /** The record after [id]'s upload was interrupted. [gainedGround]: something was kept that the next run builds on. */
    fun afterInterruption(counts: Map<String, Int>, id: String, gainedGround: Boolean): Map<String, Int> =
        if (gainedGround) counts - id else counts + (id to (counts[id] ?: 0) + 1)

    /** The record after [id] was sent, or left the queue some other way. */
    fun cleared(counts: Map<String, Int>, id: String): Map<String, Int> = counts - id

    fun stalled(counts: Map<String, Int>): Set<String> = counts.filterValues { it >= STALLED_AFTER }.keys

    /** [pending] with every stalled file moved to the end, in its own order. */
    fun <T> lastIfStalled(pending: List<T>, stalled: Set<String>, idOf: (T) -> String): List<T> {
        if (stalled.isEmpty()) return pending
        val (stuck, others) = pending.partition { idOf(it) in stalled }
        return others + stuck
    }
}
