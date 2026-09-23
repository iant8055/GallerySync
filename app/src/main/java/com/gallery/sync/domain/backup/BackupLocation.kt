package com.gallery.sync.domain.backup

/**
 * Where a backup can go, and the one rule about turning them off: **there is always at least one.**
 *
 * Ian, 20 Sept 2026, sketching the Backup section of Settings as one block per location, each with a
 * box to switch it on or off: *"not allowing user NOT to have at least ONE backup location."* Backing
 * up to nowhere would leave the app running and protecting nothing, which is the worst way for it to
 * be quiet.
 *
 * Only OneDrive is usable today. Google Photos (Pro, v0.5) and a USB drive were part of Ian's sketch and
 * are not built; they were drawn in Settings as placeholders to show the idea, and taken out again at
 * his word. With OneDrive the only location in use, its box is locked on. The rule is here, and tested, so
 * that the day a second location works the box unlocks by itself and the last one still cannot be
 * removed.
 */
enum class BackupLocation {
    ONEDRIVE,
    GOOGLE_PHOTOS,
    USB_DRIVE;

    /** Whether this location can be switched on in this build. */
    val isUsable: Boolean get() = this == ONEDRIVE

    companion object {
        /**
         * Every album's destination, implicitly, before this was a per-album choice at all — see
         * TASK-026. The migration that added the column existing rows and albums default to this,
         * and it is not a placeholder: every row in the ledger today genuinely did go to OneDrive.
         */
        val DEFAULT = ONEDRIVE

        fun fromNameOrDefault(name: String?): BackupLocation =
            entries.firstOrNull { it.name == name } ?: DEFAULT
    }
}

object BackupLocations {

    /** The locations in use in this build: what is switched on, out of what can be. */
    val inUse: Set<BackupLocation> = BackupLocation.entries.filter { it.isUsable }.toSet()

    /** May [which] be switched off, given what is [active]? Never if it would leave nothing. */
    fun canSwitchOff(active: Set<BackupLocation>, which: BackupLocation): Boolean =
        which in active && active.size > 1
}
