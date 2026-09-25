package com.gallery.sync.domain.backup

/**
 * The clouds other than OneDrive whose uploaded rows may be offered for Sync (optimise) and restored in place.
 *
 * Written once as a SQL list because the candidate queries in `BackupEntryDao` cannot ask the capability table;
 * `SyncLocationsTest` fails if this drifts from [CloudCapabilities], so a cloud cannot be switched on in one
 * place and forgotten in the other. TASK-027 stage 2.
 */
object SyncLocations {

    const val SQL_LIST = "'DROPBOX','GOOGLE_DRIVE','BACKBLAZE_B2','IDRIVE_E2'"
}
