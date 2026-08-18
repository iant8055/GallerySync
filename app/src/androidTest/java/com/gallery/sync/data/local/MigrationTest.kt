package com.gallery.sync.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Verifies the schema migrations against a real SQLite database.
 *
 * This cannot be a JVM unit test, and it earns its keep: `runMigrationsAndValidate` compares the
 * tables the migration actually creates against the schema Room expects. A mistyped column or a
 * missing index in hand-written `CREATE TABLE` SQL is invisible until it crashes on a user's device
 * during an upgrade, which is the one moment a backup app must not fail.
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        GallerySyncDatabase::class.java
    )

    @Test
    fun migrate1To2_producesTheSchemaRoomExpects() {
        helper.createDatabase(TEST_DB, 1).close()

        // Throws if the migrated schema differs from the generated one in any way.
        helper.runMigrationsAndValidate(TEST_DB, 2, true, Migrations.MIGRATION_1_2).close()
    }

    @Test
    fun migrate2To3_producesTheSchemaRoomExpects() {
        helper.createDatabase(TEST_DB, 2).close()

        helper.runMigrationsAndValidate(TEST_DB, 3, true, Migrations.MIGRATION_2_3).close()
    }

    @Test
    fun migrate2To3_defaultsExistingRowsToNotProxied() {
        // An existing backed-up file must not be mistaken for a proxy after upgrading, or the
        // scanner would skip it forever and it would never be backed up again.
        helper.createDatabase(TEST_DB, 2).apply {
            execSQL(
                """
                INSERT INTO backup_entries
                    (id, mediaStoreId, contentUri, displayName, album, sizeBytes,
                     dateModifiedEpochSeconds, mimeType, isVideo, state, attemptCount)
                VALUES
                    ('k1', 42, 'content://media/external/images/media/42', 'IMG_1.jpg',
                     'Camera', 1024, 1700000000, 'image/jpeg', 0, 'UPLOADED', 0)
                """.trimIndent()
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(TEST_DB, 3, true, Migrations.MIGRATION_2_3)

        db.query("SELECT isProxied, localProxySizeBytes, sizeBytes FROM backup_entries WHERE id = 'k1'")
            .use { cursor ->
                cursor.moveToFirst()
                assertEquals("existing rows must not read as proxied", 0, cursor.getInt(0))
                assertEquals("no proxy size yet", true, cursor.isNull(1))
                // The original's size must survive: it is what the cloud copy is checked against.
                assertEquals(1024, cursor.getLong(2))
            }
        db.close()
    }

    @Test
    fun migrateAllTheWayFrom1To3() {
        // Someone upgrading from the first build skips version 2 entirely.
        helper.createDatabase(TEST_DB, 1).close()

        helper.runMigrationsAndValidate(TEST_DB, 3, true, *Migrations.ALL).close()
    }

    @Test
    fun migrate1To2_leavesTheNewTablesUsable() {
        helper.createDatabase(TEST_DB, 1).close()

        val db = helper.runMigrationsAndValidate(TEST_DB, 2, true, Migrations.MIGRATION_1_2)

        db.query("SELECT COUNT(*) FROM backup_entries").use { cursor ->
            cursor.moveToFirst()
            assertEquals(0, cursor.getInt(0))
        }
        db.query("SELECT COUNT(*) FROM album_preferences").use { cursor ->
            cursor.moveToFirst()
            assertEquals(0, cursor.getInt(0))
        }
        db.close()
    }

    private companion object {
        const val TEST_DB = "migration-test.db"
    }
}
