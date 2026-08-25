package com.gallery.sync.domain.backup

/**
 * Where backups live in OneDrive.
 *
 * ### Why there is more than one
 *
 * The destination is where *new* uploads go, and the user may change it. The search set is every
 * folder worth checking before deciding a file is missing — and it always includes
 * [SAMSUNG_GALLERY], whether or not that is still the destination.
 *
 * That separation is what makes the setting safe to change. `Samsung Gallery/DCIM` is not an
 * arbitrary default: it mirrors the layout Samsung's own sync created, which is the only reason the
 * skip-existing check finds anything. Measured on the Fold 4, 24 Aug 2026 — 6,278 of 6,371 files
 * already present, so a first run sends 2.8 GB rather than 120 GB.
 *
 * If changing the destination also moved the search, that reconciliation would vanish the moment
 * someone picked a different folder, and the app would re-upload a library the user had already
 * paid to store. Keeping the Samsung path permanently searchable means a changed destination
 * redirects new files without stranding old ones.
 */
object RemoteRoots {

    /**
     * The layout Samsung's own sync created, and the default destination.
     *
     * Always searched, even when it is no longer the destination.
     */
    const val SAMSUNG_GALLERY = "Samsung Gallery/DCIM"

    /** What a fresh install uploads into until told otherwise. */
    const val DEFAULT_DESTINATION = SAMSUNG_GALLERY

    /**
     * Every folder to look in before concluding a file is not backed up, destination first.
     *
     * Order matters only for duplicate names: the first root wins, so the destination's copy is
     * preferred over an older one elsewhere. Collisions are near-impossible in practice — the same
     * name in two roots is the same file — and this index is a reconciliation hint, not the
     * guarantee. What makes a removal safe is `remoteSizeBytes` recorded per ledger row.
     */
    fun searchOrder(destination: String): List<String> =
        (listOf(destination) + SAMSUNG_GALLERY).distinct()

    /**
     * Whether [path] is usable as a destination.
     *
     * Deliberately permissive — Graph accepts most folder names, and refusing something it would
     * have taken is its own kind of bug. This rejects only what cannot work: nothing, and paths
     * that would resolve somewhere other than where they read.
     */
    fun isValidDestination(path: String): Boolean {
        val trimmed = path.trim()
        return trimmed.isNotEmpty() &&
            !trimmed.startsWith("/") &&
            !trimmed.endsWith("/") &&
            !trimmed.contains("//") &&
            !trimmed.split("/").any { it.isBlank() || it == "." || it == ".." }
    }

    /** Tidies user input into the form the rest of the app expects. */
    fun normalise(path: String): String = path.trim().trim('/')
}
