package com.gallery.sync.domain.backup

import com.gallery.sync.data.local.entity.AlbumMode

/**
 * "Do not back up this file" — a per-file exception on the album's page (Ian, 23 Nov 2026: *"allow the user to flag
 * an individual file inside an album as Do Not Backup"*, set by a swipe left, undone by a swipe right).
 *
 * Stored in the same column as [FilePin] (`backup_entries.modeOverride`), as Off: the file is treated as if its
 * album were Off. No schema change. It replaces a pin if there was one, and clearing it returns the file to following
 * its album.
 *
 * What it does, all of it in the direction of doing less:
 * - never uploaded, by any run, to any Cloud: every pending query skips it, so it is also not counted as outstanding;
 * - never optimized and never offered for Archive, even if it was backed up before it was flagged: nothing may shrink
 *   or remove a file the user asked to keep out of the app's hands;
 * - a copy already in a Cloud stays there. Removing one is its own decision, under the deletion rules, and this never
 *   makes it.
 */
object FileSkip {

    val MODE: AlbumMode = AlbumMode.OFF

    fun isSkipped(modeOverride: AlbumMode?): Boolean = modeOverride == MODE

    /** What to store: Off, or nothing, which returns the file to following its album. */
    fun overrideFor(skipped: Boolean): AlbumMode? = if (skipped) MODE else null
}
