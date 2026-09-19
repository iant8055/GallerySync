package com.gallery.sync.domain.backup

import com.gallery.sync.data.local.entity.AlbumMode
import com.gallery.sync.data.local.entity.BackupEntryEntity
import com.gallery.sync.data.local.entity.BackupState

/**
 * "Keep this file at full size" — a per-file exception the user can see and undo.
 *
 * ### What it is
 *
 * A file's own mode wins over its album's (`backup_entries.modeOverride`), and this is the one value
 * it takes: [MODE], Backup. In a Sync album that stops the file being shrunk. In an
 * Archive album it stops the file being offered for removal. In any other album it changes nothing
 * today, and is remembered for the day the album's mode changes.
 *
 * **Only Restore sets it.** Since 27 Aug 2026, as a hidden flag, so that a file the user pulled back at
 * full size was not shrunk again the same evening. Ian, 18 Sept 2026: make it visible, let the user
 * switch it off, and let the Albums tab count it. The tick box in an album's file list therefore
 * appears only beside files Restore has put back, and only ever *clears* the pin or puts it back
 * again; a file Restore never touched cannot be pinned from the UI.
 *
 * ### Why only this direction
 *
 * It can only make the app do **less** to a file, never more. Nothing is removed, shrunk or sent
 * because of a pin, so it needs no confirmation of its own. A per-file Archive or Sync would be
 * removal or rewriting decided one file at a time, which is a different consent from the album's
 * mode and was deliberately not built.
 */
object FilePin {

    /** The override that means "pinned". The same value Restore writes. */
    val MODE: AlbumMode = AlbumMode.BACKUP

    fun isPinned(modeOverride: AlbumMode?): Boolean = modeOverride == MODE

    /** What to store: the pin, or nothing, which returns the file to following its album. */
    fun overrideFor(pinned: Boolean): AlbumMode? = if (pinned) MODE else null

    /**
     * [items] without the ones the user has kept at full size.
     *
     * Matched two ways because a file has two identities that drift apart: its ledger key changes
     * when a restore rewrites its modification time, and its MediaStore id survives that. Either
     * matching is enough to keep a file back, since the cost of a false match is a file that stays
     * on the phone.
     */
    fun <T> withoutPinned(
        items: List<T>,
        pinnedIds: Set<String>,
        pinnedMediaStoreIds: Set<Long>,
        idOf: (T) -> String,
        mediaStoreIdOf: (T) -> Long
    ): List<T> {
        if (pinnedIds.isEmpty() && pinnedMediaStoreIds.isEmpty()) return items
        return items.filterNot { idOf(it) in pinnedIds || mediaStoreIdOf(it) in pinnedMediaStoreIds }
    }
}

/** How the file list inside an album can be ordered. */
enum class FileSort { NAME, DATE, STATUS }

/**
 * Orders an album's files.
 *
 * - **Name:** A to Z, ignoring case.
 * - **Date:** the file's own modification date, newest first. A restored file carries the date it was
 *   restored, which is the date the phone gives it.
 * - **Status:** the ones that need attention first — failed, then pending — then optimised, then
 *   backed up. Ties fall back to name, so the order is stable and predictable.
 */
object AlbumFileSort {

    fun sorted(entries: List<BackupEntryEntity>, sort: FileSort): List<BackupEntryEntity> {
        val byName = compareBy<BackupEntryEntity> { it.displayName.lowercase() }
        return when (sort) {
            FileSort.NAME -> entries.sortedWith(byName)
            FileSort.DATE -> entries.sortedWith(
                compareByDescending<BackupEntryEntity> { it.dateModifiedEpochSeconds }.then(byName)
            )
            FileSort.STATUS -> entries.sortedWith(compareBy<BackupEntryEntity> { statusRank(it) }.then(byName))
        }
    }

    /** Lower is earlier. Mirrors the marks the list draws, so the order matches what is on screen. */
    internal fun statusRank(entry: BackupEntryEntity): Int = when {
        entry.state == BackupState.FAILED -> 0
        entry.state == BackupState.PENDING -> 1
        entry.isProxied && entry.state == BackupState.UPLOADED -> 2
        entry.isProxied -> 3
        entry.state == BackupState.UPLOADED -> 4
        else -> 5
    }
}
