package com.gallery.sync.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Schema migrations.
 *
 * Every one is additive or explicitly preserving. `fallbackToDestructiveMigration` is deliberately
 * never used: it would silently discard the ledger, and a lost ledger means the app re-uploads a
 * user's entire library — or worse, believes files are backed up that were never sent.
 */
object Migrations {

    /**
     * 1 → 2: adds the backup ledger and per-album preferences.
     *
     * Purely additive. Both existing tables are untouched, so no user data moves or is lost.
     */
    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `backup_entries` (
                    `id` TEXT NOT NULL,
                    `mediaStoreId` INTEGER NOT NULL,
                    `contentUri` TEXT NOT NULL,
                    `displayName` TEXT NOT NULL,
                    `album` TEXT NOT NULL,
                    `sizeBytes` INTEGER NOT NULL,
                    `dateModifiedEpochSeconds` INTEGER NOT NULL,
                    `mimeType` TEXT NOT NULL,
                    `isVideo` INTEGER NOT NULL,
                    `state` TEXT NOT NULL,
                    `remoteItemId` TEXT,
                    `remoteSizeBytes` INTEGER,
                    `uploadedAtEpochMillis` INTEGER,
                    `attemptCount` INTEGER NOT NULL,
                    `lastError` TEXT,
                    PRIMARY KEY(`id`)
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_backup_entries_state` ON `backup_entries` (`state`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_backup_entries_album` ON `backup_entries` (`album`)")
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_backup_entries_album_state` " +
                    "ON `backup_entries` (`album`, `state`)"
            )

            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `album_preferences` (
                    `albumName` TEXT NOT NULL,
                    `isEnabled` INTEGER NOT NULL,
                    PRIMARY KEY(`albumName`)
                )
                """.trimIndent()
            )
        }
    }

    /**
     * 2 → 3: records that a local file has been replaced by a downscaled proxy.
     *
     * Additive. `sizeBytes` keeps meaning the original's size, because that is what is in the
     * cloud; `localProxySizeBytes` is what the phone now holds.
     */
    val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "ALTER TABLE `backup_entries` ADD COLUMN `isProxied` INTEGER NOT NULL DEFAULT 0"
            )
            db.execSQL(
                "ALTER TABLE `backup_entries` ADD COLUMN `localProxySizeBytes` INTEGER"
            )
        }
    }

    /**
     * 3 → 4: `album_preferences.isEnabled` becomes a four-valued `mode`.
     *
     * The only migration so far that reinterprets existing data rather than adding to it, and the
     * table it touches is the one that cannot be rebuilt from OneDrive or from the files. So the
     * mapping is the whole risk, not the SQL:
     *
     *  - `isEnabled = 1` → `BACKUP`, **not** `SYNC`. An enabled album today is uploaded and nothing
     *    local is touched; optimising has always needed a deliberate tap. Mapping to `SYNC` would
     *    switch on space management nobody chose, and the first the user would know is their photos
     *    being rewritten.
     *  - `isEnabled = 0` → `OFF`.
     *  - Nothing maps to `ARCHIVE`, ever. It removes files from the phone.
     *
     * SQLite cannot drop a column, so the table is recreated. The copy runs before the drop and the
     * rename runs last, so an interrupted migration leaves either the old table or the new one —
     * never a half-populated replacement.
     */
    val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `album_preferences_new` (
                    `albumName` TEXT NOT NULL,
                    `mode` TEXT NOT NULL,
                    PRIMARY KEY(`albumName`)
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                INSERT INTO `album_preferences_new` (`albumName`, `mode`)
                SELECT `albumName`, CASE WHEN `isEnabled` = 0 THEN 'OFF' ELSE 'BACKUP' END
                FROM `album_preferences`
                """.trimIndent()
            )
            db.execSQL("DROP TABLE `album_preferences`")
            db.execSQL("ALTER TABLE `album_preferences_new` RENAME TO `album_preferences`")
        }
    }

    /**
     * 4 → 5: records that a file was examined and found not worth proxying.
     *
     * Additive, and defaulting to 0 is right for every existing row: a file that has never been
     * examined must stay a candidate. Marking them skipped instead would silently exclude photos
     * that genuinely could shrink.
     */
    val MIGRATION_4_5 = object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "ALTER TABLE `backup_entries` ADD COLUMN `isProxySkipped` INTEGER NOT NULL DEFAULT 0"
            )
        }
    }

    /**
     * 5 → 6: remembers an in-flight resumable upload session across runs.
     *
     * Additive, and null is right for every existing row: nothing has a session outstanding at the
     * moment of upgrade, so every file simply starts fresh as it does today.
     */
    val MIGRATION_5_6 = object : Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `backup_entries` ADD COLUMN `uploadSessionUrl` TEXT")
            db.execSQL(
                "ALTER TABLE `backup_entries` ADD COLUMN `uploadSessionExpiresAtEpochMillis` INTEGER"
            )
        }
    }

    /**
     * 6 -> 7: records when a backed-up file stopped being on the phone.
     *
     * Additive, and null is right for every existing row: nothing is known to be missing at the
     * moment of upgrade, and the next scan fills it in properly. Defaulting to a timestamp would
     * claim the entire library had been deleted.
     */
    val MIGRATION_6_7 = object : Migration(6, 7) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "ALTER TABLE `backup_entries` ADD COLUMN `localMissingSinceEpochMillis` INTEGER"
            )
        }
    }

    /** Every migration, in order, for the database builder. */
    val ALL = arrayOf(
        MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7
    )
}
