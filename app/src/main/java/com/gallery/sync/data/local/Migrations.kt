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

    /** Every migration, in order, for the database builder. */
    val ALL = arrayOf(MIGRATION_1_2)
}
