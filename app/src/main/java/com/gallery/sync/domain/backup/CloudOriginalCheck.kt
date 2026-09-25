package com.gallery.sync.domain.backup

import com.gallery.sync.data.local.entity.BackupEntryEntity
import com.gallery.sync.data.local.entity.BackupState
import com.gallery.sync.domain.repository.CloudVerifiers
import com.gallery.sync.util.Logger
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The gate every Sync overwrite passes — TASK-027 stage 2.
 *
 * Sync replaces the file on the phone with a smaller copy **in place, with no trash step**, so from that moment the
 * cloud holds the only original. OneDrive rows are trusted on the size recorded when Graph created the item, as they
 * always have been. A row held by another cloud carries no remembered size by design, so it is asked, live, right
 * before the write: the cloud must say the original is there at exactly its size ([ElsewhereVerdict]). Anything else,
 * including "could not ask", leaves the file untouched.
 *
 * A file that fails the check is **held**: skipped by the candidate lists for [HOLD_MILLIS], in memory only. Without
 * that the same unverifiable file would be the first candidate of every batch, and a chain that re-queues while work
 * remains would never end. Held is not a verdict against the file, and nothing is recorded on its row.
 */
@Singleton
class CloudOriginalCheck @Inject constructor(
    private val verifiers: CloudVerifiers
) {

    private val heldUntil = ConcurrentHashMap<String, Long>()

    /** Replaced in tests. */
    internal var clock: () -> Long = System::currentTimeMillis

    fun isHeld(id: String): Boolean {
        val until = heldUntil[id] ?: return false
        if (until > clock()) return true
        heldUntil.remove(id)
        return false
    }

    /**
     * Skips a file that could not be written for now, so the rest of the queue is not stuck behind it. Held in memory
     * for the same six hours, so the next start tries it again.
     */
    fun holdFailed(entry: BackupEntryEntity, reason: String) {
        heldUntil[entry.id] = clock() + HOLD_MILLIS
        Logger.w(TAG, "holding ${entry.displayName}: could not be replaced ($reason)")
    }

    /** Whether [entry]'s original may be replaced right now. Fails closed. */
    suspend fun confirms(entry: BackupEntryEntity): Boolean {
        if (entry.location == BackupLocation.ONEDRIVE) return true

        val verifier = verifiers.of(entry.location)
        val uploaded = entry.state == BackupState.UPLOADED && !entry.remoteItemId.isNullOrBlank()
        val check = if (uploaded && verifier != null) verifier.sizeOf(entry.remoteItemId!!) else null
        val verdict = ElsewhereVerdict.of(uploaded, verifier != null, check, entry.sizeBytes)

        if (verdict == ElsewhereVerdict.CONFIRMED) return true

        heldUntil[entry.id] = clock() + HOLD_MILLIS
        Logger.w(TAG, "holding ${entry.displayName}: ${entry.location} did not confirm the original ($verdict)")
        return false
    }

    private companion object {
        const val TAG = "CloudOriginalCheck"
        const val HOLD_MILLIS = 6 * 60 * 60 * 1000L
    }
}
