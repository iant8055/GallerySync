package com.gallery.sync.domain.backup

/**
 * Which cloud a file goes to, decided one level above the album: by the top-level folder it lives under
 * (`DCIM`, `Pictures`, `Movies`...). TASK-026, redesigned 24 Sept 2026 — Ian: the choice is per folder,
 * never per album, and it replaces the single app-wide picker.
 *
 * The app-wide setting is kept only as the fallback for what has no row of its own: a folder never
 * chosen, or an item with no `RELATIVE_PATH` at all (API < 29).
 */
object FolderDestination {

    fun resolve(
        topLevelFolder: String?,
        chosen: Map<String, BackupLocation>,
        fallback: BackupLocation
    ): BackupLocation = topLevelFolder?.let { chosen[it] } ?: fallback
}
