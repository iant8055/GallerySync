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
