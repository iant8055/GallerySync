package com.gallery.sync.domain.backup

/**
 * Which folders the app is allowed to look in.
 *
 * ### One grant, two jobs
 *
 * The folders the user picks to pull *from* are the folders the app later needs to write *into* —
 * proxying rewrites a photo in place. A persisted `ACTION_OPEN_DOCUMENT_TREE` grant carries both, so
 * there is one picker rather than two, and no album can be given a mode the app cannot carry out.
 * Established on hardware 19 Aug 2026: a persisted tree grant writes to media this app does not own
 * with no dialog.
 *
 * ### Why scoping matters at all
 *
 * `MediaScanner` sees everything MediaStore returns, which on a real device is ninety albums —
 * WhatsApp thumbnails, screenshots, every app's cache folder. Almost none of that is what someone
 * means by "my photos", and offering it all makes the album list unusable and the first upload
 * enormous.
 *
 * ### Narrowing hides, it never forgets
 *
 * An album outside the granted set is not scanned and not listed, but its ledger rows and its chosen
 * mode stay exactly where they are. Re-granting the folder brings them back with their history
 * intact and re-uploads nothing. That is why this is a filter over the scan rather than a delete,
 * and why pruning must never be driven by a scoped result — see `BackupEngine.refreshLedger`.
 */
object TreeScope {

    /**
     * The relative path a SAF tree document id refers to.
     *
     * Document ids look like `primary:DCIM` or `1234-5678:DCIM/Camera` — a volume, a colon, then a
     * path relative to that volume's root. MediaStore's `RELATIVE_PATH` uses the same path without
     * the volume, which is what makes the two comparable at all.
     *
     * Returns null for an id with no path after the volume: that is the whole volume, which is not
     * a meaningful scope and would silently re-include everything the user was narrowing away from.
     */
    fun pathFromTreeDocumentId(documentId: String): String? {
        val afterVolume = documentId.substringAfter(':', missingDelimiterValue = "")
        return afterVolume.trim('/').takeIf { it.isNotEmpty() }
    }

    /**
     * Whether [relativePath] sits inside any of [granted].
     *
     * `RELATIVE_PATH` carries a trailing slash (`DCIM/Camera/`) and granted paths do not, so both
     * are normalised before comparison.
     *
     * The boundary check is the part that matters: a grant on `DCIM` covers `DCIM/Camera` but must
     * not cover `DCIMBackup`. Comparing with a plain `startsWith` gets that wrong, and getting it
     * wrong means scanning a folder the user did not grant.
     */
    fun isInScope(relativePath: String?, granted: Collection<String>): Boolean {
        if (granted.isEmpty()) return false
        val path = relativePath?.trim('/') ?: return false

        return granted.asSequence()
            .map { it.trim('/') }
            .filter { it.isNotEmpty() }
            .any { root -> path == root || path.startsWith("$root/") }
    }

    /**
     * Removes any granted path already covered by another.
     *
     * Granting `DCIM` and then `DCIM/Camera` is not an error, but keeping both would count the
     * nested one twice when reporting how many albums a directory brings.
     */
    fun withoutRedundant(granted: Collection<String>): List<String> {
        val cleaned = granted.map { it.trim('/') }.filter { it.isNotEmpty() }.distinct()
        return cleaned.filterNot { candidate ->
            cleaned.any { other -> other != candidate && isInScope(candidate, listOf(other)) }
        }
    }

    /**
     * The folder name to show for a granted path.
     *
     * The last segment, because that is what the user picked and recognises — a row reading
     * `Camera` is clearer than one reading `DCIM/Camera`, and the full path is still available
     * where it matters.
     */
    fun displayNameOf(path: String): String =
        path.trim('/').substringAfterLast('/').ifEmpty { path }
}
