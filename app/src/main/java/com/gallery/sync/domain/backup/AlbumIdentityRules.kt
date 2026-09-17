package com.gallery.sync.domain.backup

import com.gallery.sync.data.local.entity.AlbumMode
import com.gallery.sync.data.local.entity.AlbumPreferenceEntity
import com.gallery.sync.data.local.entity.BackupEntryEntity
import com.gallery.sync.data.local.entity.BackupState
import java.util.Locale

/**
 * An album the device scan reports, with the folder it came from.
 *
 * [folderKey] is `MediaScanRules.folderKeyOf`, and it is what separates one folder under two
 * spellings from two folders that happen to share a name.
 */
data class DeviceAlbum(val name: String, val folderKey: String)

/**
 * Tells the user that one folder had been stored under more than one name, and that its album is now
 * Off.
 *
 * Kept until they press Dismiss.
 */
data class AlbumMergeWarning(
    val albumName: String,
    /** Every spelling that was merged, the kept one included. */
    val spellings: List<String>,
    /** The mode each spelling held before the merge. A spelling with no stored mode is absent. */
    val previousModes: Map<String, AlbumMode>,
    val atEpochMillis: Long
)

/** One merge to apply, worked out in full before anything is written. */
data class AlbumMerge(
    val target: String,
    val spellings: List<String>,
    val previousModes: Map<String, AlbumMode>,
    /** Ledger rows to write under [target], one per file. Replaces every row of every spelling. */
    val entries: List<BackupEntryEntity>
)

/**
 * TASK-023: every case-only spelling of a folder is one album, and merging them sets that album Off.
 *
 * ### Why a merge always ends Off
 *
 * Ian, 16 Sept 2026: *"If there is ever a discrepancy in any type of folder/album conflict - the mode
 * should switch to OFF - then a warning given to the user."* And, on whether to warn when both
 * spellings were already Off: *"yes warn anyway"*. This is the one exception CLAUDE.md allows to
 * "album modes are set only by the user", and it only ever writes Off, which cannot remove a file.
 *
 * The reason to take no mode forward is the Archive rule. An Archive album's membership is not a free
 * variable: a second spelling carrying Archive into the merged album would widen what is removed under
 * a choice made for a different name.
 *
 * ### Why this is not a migration
 *
 * A spelling can change at any time: a folder renamed on disk, a library copied over, a file restored
 * after the folder changed case (all seen on the Moto G, 16 Sept 2026). So this is checked on every
 * scan, not run once.
 */
object AlbumIdentityRules {

    fun plan(
        deviceAlbums: List<DeviceAlbum>,
        preferences: List<AlbumPreferenceEntity>,
        cloudStatusNames: List<String>,
        entries: List<BackupEntryEntity>
    ): List<AlbumMerge> {
        val storedNames = (preferences.map { it.albumName } + cloudStatusNames + entries.map { it.album })
            .distinct()
        val groups = (storedNames + deviceAlbums.map { it.name }).distinct().groupBy(::foldCase)

        return groups.mapNotNull { (key, _) ->
            val onDevice = deviceAlbums.filter { foldCase(it.name) == key }
            // Two different folders whose names differ only in case, such as `DCIM/Camera` beside
            // `Pictures/camera`. Merging them would pour one folder's files into the other's mode,
            // so they are left exactly as they were.
            if (onDevice.map { it.folderKey }.distinct().size > 1) return@mapNotNull null

            val inGroupEntries = entries.filter { foldCase(it.album) == key }
            val stored = storedNames.filter { foldCase(it) == key }
            val target = onDevice.firstOrNull()?.name
                ?: inGroupEntries.maxByOrNull { it.mediaStoreId }?.album
                ?: stored.minOrNull()
                ?: return@mapNotNull null

            if (stored.all { it == target }) return@mapNotNull null

            AlbumMerge(
                target = target,
                spellings = (stored + target).distinct().sorted(),
                previousModes = preferences
                    .filter { foldCase(it.albumName) == key }
                    .associate { it.albumName to it.mode },
                entries = mergeEntries(inGroupEntries, target)
            )
        }
    }

    /**
     * Every row of the merged album renamed to [target], one row per file.
     *
     * A file can have a row under each spelling. The restore test on 16 Sept 2026 left
     * `camera/IMG…` and `Camera/IMG…`, both pointing at the same OneDrive item. Renamed, they share
     * an id, and one of them is kept.
     *
     * Matched on the full key, not on `mediaStoreId`, because MediaStore reuses ids (see
     * [BackupEntryEntity.id]). The dropped row is bookkeeping only: nothing here reaches the trash,
     * OneDrive or the missing-file flag.
     */
    internal fun mergeEntries(rows: List<BackupEntryEntity>, target: String): List<BackupEntryEntity> =
        rows
            .map { it.copy(id = renamedId(it, target), album = target) }
            .groupBy { it.id }
            .map { (_, sameFile) -> keepOne(sameFile) }

    private fun renamedId(row: BackupEntryEntity, target: String): String {
        val prefix = "${row.album}/"
        return if (row.id.startsWith(prefix)) "$target/${row.id.removePrefix(prefix)}" else row.id
    }

    /**
     * The row that knows most about the file, topped up with anything only the others knew.
     *
     * Proxied first, because a proxied row is what stops the shrunken file being uploaded as a new one.
     * Then the row holding a cloud copy, because that copy is what retrieval and Archive rely on.
     */
    private fun keepOne(rows: List<BackupEntryEntity>): BackupEntryEntity {
        if (rows.size == 1) return rows.single()

        val best = rows.sortedWith(
            compareByDescending<BackupEntryEntity> { it.isProxied }
                .thenByDescending { !it.remoteItemId.isNullOrEmpty() }
                .thenByDescending { it.state == BackupState.UPLOADED }
                .thenBy { it.uploadedAtEpochMillis ?: Long.MAX_VALUE }
        ).first()

        return best.copy(
            localProxySizeBytes = best.localProxySizeBytes ?: rows.firstNotNullOfOrNull { it.localProxySizeBytes },
            modeOverride = best.modeOverride ?: rows.firstNotNullOfOrNull { it.modeOverride },
            localMissingSinceEpochMillis = best.localMissingSinceEpochMillis
                ?: rows.firstNotNullOfOrNull { it.localMissingSinceEpochMillis }
        )
    }

    fun warningFor(merge: AlbumMerge, now: Long) = AlbumMergeWarning(
        albumName = merge.target,
        spellings = merge.spellings,
        previousModes = merge.previousModes,
        atEpochMillis = now
    )

    /** Case folding for grouping only. Never used as a stored or displayed name. */
    fun foldCase(name: String): String = name.lowercase(Locale.ROOT)

    // ---------- storage in DataStore, which holds strings ----------

    private const val FIELD = '\u001F'
    private const val ITEM = '\u001E'

    /**
     * One warning as a single string.
     *
     * Control characters as separators, because they cannot appear in a folder name.
     */
    fun encode(warning: AlbumMergeWarning): String = listOf(
        warning.albumName,
        warning.spellings.joinToString(ITEM.toString()),
        warning.previousModes.entries.joinToString(ITEM.toString()) { "${it.key}=${it.value.name}" },
        warning.atEpochMillis.toString()
    ).joinToString(FIELD.toString())

    /** Null for anything unreadable, which is dropped rather than shown half-parsed. */
    fun decode(stored: String): AlbumMergeWarning? {
        val fields = stored.split(FIELD)
        if (fields.size != 4) return null
        val at = fields[3].toLongOrNull() ?: return null
        val modes = fields[2].split(ITEM).filter { it.isNotEmpty() }.associate { pair ->
            val name = pair.substringBeforeLast('=')
            val mode = AlbumMode.entries.firstOrNull { it.name == pair.substringAfterLast('=') }
                ?: return null
            name to mode
        }
        return AlbumMergeWarning(
            albumName = fields[0],
            spellings = fields[1].split(ITEM).filter { it.isNotEmpty() },
            previousModes = modes,
            atEpochMillis = at
        )
    }
}
