package com.gallery.sync.domain.backup

import com.gallery.sync.data.local.dao.BackupEntryDao
import com.gallery.sync.data.local.entity.BackupEntryEntity
import javax.inject.Inject

/**
 * Brings back everything one cloud holds that is not at full size on this phone — the "restore them to the
 * phone" answer to the sign-out question (Ian, 25 Sept 2026).
 *
 * It is the Restore tab's two verbs run over a whole cloud instead of a ticked selection: a file this app
 * shrank goes back to its original in place ([RestoreProxyInPlace]), and a file that has left the phone
 * is downloaded ([DownloadMissingFile]). Both check every download against the size recorded at upload, so
 * nothing is replaced by a short read. It removes nothing anywhere and asks nothing of the cloud but the bytes.
 *
 * Runs **before** the sign-out: signing out first would cut off the very download this exists to do.
 */
class RestoreEverythingFrom @Inject constructor(
    private val entryDao: BackupEntryDao,
    private val engine: BackupEngine,
    private val restorer: RestoreProxyInPlace,
    private val downloader: DownloadMissingFile
) {

    /** What there is to bring back from one cloud. [proxies] are restored in place; [missing] are downloaded. */
    data class Plan(
        val proxies: List<BackupEntryEntity>,
        val missing: List<BackupEntryEntity>
    ) {
        val total: Int get() = proxies.size + missing.size

        /** The originals' combined size: what will be written to the phone. */
        val bytes: Long get() = (proxies + missing).sumOf { it.sizeBytes }
    }

    data class Outcome(val restored: Int, val downloaded: Int, val failed: Int)

    /** No network: both lists come from the ledger and the device scan, like the Restore tab's. */
    suspend fun plan(location: BackupLocation): Plan {
        val proxies = entryDao.restorableProxies().filter { it.location == location }
        val proxyIds = proxies.mapTo(HashSet()) { it.id }
        // A file is one list or the other, never both, exactly as on the Restore tab.
        val missing = engine.filesNotOnThePhone().filter { it.location == location && it.id !in proxyIds }
        return Plan(proxies, missing)
    }

    /**
     * Runs the plan one file at a time. [onProgress] is told how many are finished, the total, and the
     * name of the file in flight. Cancelling stops after the file in flight is dropped; what is back stays back.
     */
    suspend fun run(plan: Plan, onProgress: (finished: Int, total: Int, current: String) -> Unit): Outcome {
        var restored = 0
        var downloaded = 0
        var failed = 0
        var finished = 0

        val all = plan.proxies.map { it to true } + plan.missing.map { it to false }
        for ((entry, inPlace) in all) {
            onProgress(finished, all.size, entry.displayName)
            val result = if (inPlace) restorer.restore(entry) else downloader.download(entry)
            when (result) {
                is RestoreInPlaceResult.Restored -> if (inPlace) restored++ else downloaded++
                else -> failed++
            }
            finished++
        }
        onProgress(finished, all.size, "")
        return Outcome(restored, downloaded, failed)
    }
}
