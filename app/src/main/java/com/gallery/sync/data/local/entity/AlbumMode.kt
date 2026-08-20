package com.gallery.sync.data.local.entity

/**
 * What the user wants done with an album.
 *
 * A ladder, ordered by how much of the album stays on the phone. Each mode is a different promise
 * rather than a degree of the same one, so the difference between them is what happens to the
 * **local** file — the cloud copy is identical in all three modes that upload.
 *
 * See `.claude/tasks/TASK-012.md` for the reasoning, including why the destructive mode is called
 * [ARCHIVE] and not "backup".
 */
enum class AlbumMode {

    /** Not uploaded. Local files untouched. */
    OFF,

    /**
     * Uploaded; the local file is never touched.
     *
     * The safe mode and the default. "Backup" in the sense the rest of the industry uses it — a
     * copy is made and the original is left exactly as it was.
     */
    BACKUP,

    /**
     * Uploaded; space is managed while the file stays visible.
     *
     * Photos are replaced by proxies, so the gallery keeps working and the phone stops filling up.
     * Video is left whole today. Downscaling **old** clips in Sync albums is a decided v0.3 item
     * that is not built yet; recent video is never touched either way.
     */
    SYNC,

    /**
     * Uploaded; the local file is **removed**, so the album leaves the gallery.
     *
     * Not yet implemented — defined here so the schema supports it without a second migration.
     * Nothing writes this value today, and it must not ship before retrieval exists in v0.4: once
     * an album is archived, fetching it back is the only route to it.
     */
    ARCHIVE;

    /** Whether files in this album are sent to the cloud at all. */
    val uploads: Boolean get() = this != OFF

    /** Whether photos may be replaced by proxies. [ARCHIVE] removes them instead of shrinking them. */
    val proxiesPhotos: Boolean get() = this == SYNC

    /** Whether the local file is removed once the cloud copy is verified. */
    val removesLocal: Boolean get() = this == ARCHIVE

    companion object {

        /**
         * The factory value of the "default mode for new albums" setting: **nothing happens**.
         *
         * Changed from [BACKUP] to [OFF] by Ian, 19 Aug 2026, and it is the *initial* value of a
         * preference the user can change — see [canBeDefault] for what they may change it to.
         *
         * The previous reasoning was that someone creates an album, assumes an app whose purpose is
         * keeping files safe is keeping it safe, and silently loses it — so the failure mode should
         * be "uploaded something you did not need" rather than "lost something you did".
         *
         * Two things since made [OFF] the better starting point:
         *
         * - **The scan follows the granted directory trees** (TASK-014). An album appears only
         *   because the user picked the directory it lives in, and first run asks outright what
         *   should happen to what it found. A default that starts uploading overrides an answer the
         *   user has already given.
         * - **Adding a directory later would otherwise begin an upload with no further prompt.**
         *   [OFF] removes that edge: widening the scope shows new albums instead of sending them.
         *
         * The old failure mode is now the UI's job rather than the default's — an app with nothing
         * selected must say so plainly instead of sitting idle looking like it works.
         */
        val DEFAULT = OFF

        /**
         * The modes the user may choose as the default for new albums.
         *
         * Everything except [ARCHIVE], per Ian 19 Aug 2026 and TASK-012. A default that empties the
         * gallery would apply to albums the user has not seen yet, which is the one place the
         * per-album confirmation cannot reach — the mode would be set by a rule rather than by a
         * choice about that album.
         */
        val canBeDefault: List<AlbumMode> get() = entries.filter { it != ARCHIVE }
    }
}
