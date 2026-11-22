package com.gallery.sync.domain.backup

import com.gallery.sync.data.local.media.MediaScanRules

/**
 * Which Cloud a file goes to, decided by the folder it lives in. TASK-026, 24 Sept 2026 — Ian: the choice is per
 * folder, never per album, and it replaces the single app-wide picker.
 *
 * **The most specific folder wins (Ian, 22 Nov 2026).** Every top-level folder (`DCIM`, `Pictures`...) has a choice,
 * and a folder inside one (`DCIM/Funny stuff`) can have its own, which then covers that folder and everything under
 * it while the rest of `DCIM` keeps the top-level folder's choice. Until 22 Nov a folder added under `DCIM` was
 * reduced to `DCIM`, so choosing pCloud for `Funny stuff` moved the whole of `DCIM` to pCloud.
 *
 * A key with no `/` is a top-level folder; a key with one is a folder inside it. Folders are matched ignoring case,
 * because MediaStore keeps each writer's spelling, but a key spelled exactly like the path is preferred.
 *
 * The app-wide setting is kept only as the fallback for what has no row of its own: a folder never chosen, or an item
 * with no `RELATIVE_PATH` at all (API < 29).
 */
object FolderDestination {

    /** `DCIM/Funny stuff/` → `DCIM/Funny stuff`; null for nothing usable, as [MediaScanRules.topLevelFolderOf]. */
    fun normalise(path: String?): String? {
        val parts = path?.split('/')?.filter { it.isNotEmpty() }.orEmpty()
        if (parts.isEmpty() || parts.first().startsWith(".")) return null
        return parts.joinToString("/")
    }

    /** Whether [key] is [path] itself or a folder [path] lies inside. Both already normalised. */
    private fun covers(key: String, path: String): Boolean =
        path.equals(key, ignoreCase = true) || path.startsWith("$key/", ignoreCase = true)

    /**
     * The key whose choice governs [path]: the longest chosen key with a `/` that covers it, or else the path's
     * top-level folder (whether or not that has a row yet). Null when the path has no usable folder.
     */
    fun routeOf(path: String?, keys: Collection<String>): String? {
        val p = normalise(path) ?: return null
        val top = MediaScanRules.topLevelFolderOf(p) ?: return null
        return keys
            .filter { it.contains('/') }
            .mapNotNull { key -> normalise(key)?.takeIf { covers(it, p) }?.let { key to it } }
            .maxWithOrNull(compareBy<Pair<String, String>>({ it.second.length }, { p.startsWith(it.second) }))
            ?.first
            ?: top
    }

    /** [routeOf]'s key looked up in [chosen], exact spelling first. */
    fun resolve(
        path: String?,
        chosen: Map<String, BackupLocation>,
        fallback: BackupLocation
    ): BackupLocation {
        val key = routeOf(path, chosen.keys) ?: return fallback
        return chosen[key] ?: chosen.entries.firstOrNull { it.key.equals(key, ignoreCase = true) }?.value ?: fallback
    }

    /** Whether [key] names a folder inside a top-level folder rather than a top-level folder itself. */
    fun isSubfolder(key: String): Boolean = normalise(key)?.contains('/') == true
}
