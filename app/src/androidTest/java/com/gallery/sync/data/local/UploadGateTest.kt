package com.gallery.sync.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.gallery.sync.data.local.dao.AlbumPreferenceDao
import com.gallery.sync.data.local.dao.BackupEntryDao
import com.gallery.sync.data.local.entity.AlbumMode
import com.gallery.sync.data.local.entity.AlbumPreferenceEntity
import com.gallery.sync.data.local.entity.BackupEntryEntity
import com.gallery.sync.data.local.entity.BackupState
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The consent gate: which albums a background run is allowed to upload from.
 *
 * TODO(instrumented): REQUIRES A CONNECTED DEVICE OR EMULATOR — Room needs real SQLite, and
 *  `./gradlew connectedDebugAndroidTest` **uninstalls the app**, taking the ledger and the signed-in
 *  session with it. Do not run this against a working install.
 *
 * ### Why this file exists
 *
 * Observed 24 Aug 2026 on a fresh Fold 8 install. The gate read
 * `album NOT IN (SELECT albumName FROM album_preferences WHERE mode = 'OFF')`, and `album_preferences`
 * was still empty because only the UI ever wrote to it. `NOT IN` over an empty set is true for every
 * row, so a content-triggered run that fired before the user had opened the album screen treated the
 * entire library as chosen and uploaded 23 files from five albums — including a 75 MB video — that
 * nobody had selected.
 *
 * Nothing was deleted, because local removal needs an Activity and a tap. But files left the phone
 * that the user had not chosen to send, and the rule in CLAUDE.md is that removal — and by the same
 * reasoning, sending — follows from a mode the user set and from nothing else.
 *
 * [unknownAlbumIsNotEligible] is the regression test for exactly that. If it ever fails, the gate has
 * been flipped back to opt-out.
 */
@RunWith(AndroidJUnit4::class)
class UploadGateTest {

    private lateinit var database: GallerySyncDatabase
    private lateinit var entryDao: BackupEntryDao
    private lateinit var albumDao: AlbumPreferenceDao

    @Before
    fun createDatabase() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            GallerySyncDatabase::class.java
        ).build()
        entryDao = database.backupEntryDao()
        albumDao = database.albumPreferenceDao()
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    private fun entry(album: String, name: String) = BackupEntryEntity(
        id = "$album/$name",
        mediaStoreId = name.hashCode().toLong(),
        contentUri = "content://media/external/images/media/${name.hashCode()}",
        displayName = name,
        album = album,
        sizeBytes = 1_024L,
        dateModifiedEpochSeconds = 1_700_000_000L,
        mimeType = "image/jpeg",
        isVideo = false,
        state = BackupState.PENDING
    )

    private suspend fun queue() = entryDao.nextPending(limit = 100, maxAttempts = 3)

    /** The defect this file was written for: no row must mean "not chosen", never "chosen". */
    @Test
    fun unknownAlbumIsNotEligible() = runTest {
        entryDao.insertIfNew(listOf(entry("Camera", "a.jpg"), entry("WhatsApp Images", "b.jpg")))
        // album_preferences deliberately left empty — the exact state of a fresh install.

        assertEquals(emptyList<BackupEntryEntity>(), queue())
        assertEquals(0, entryDao.countPendingInSelectedAlbums())
    }

    @Test
    fun albumInAnUploadingModeIsEligible() = runTest {
        entryDao.insertIfNew(listOf(entry("Camera", "a.jpg"), entry("Drafts", "b.jpg")))
        albumDao.setPreference(AlbumPreferenceEntity("Camera", AlbumMode.BACKUP))

        assertEquals(listOf("a.jpg"), queue().map { it.displayName })
        assertEquals(1, entryDao.countPendingInSelectedAlbums())
    }

    @Test
    fun albumExplicitlySwitchedOffIsNotEligible() = runTest {
        entryDao.insertIfNew(listOf(entry("Camera", "a.jpg")))
        albumDao.setPreference(AlbumPreferenceEntity("Camera", AlbumMode.OFF))

        assertEquals(emptyList<BackupEntryEntity>(), queue())
        assertEquals(0, entryDao.countPendingInSelectedAlbums())
    }

    /**
     * The count drives the UI while the queue drives the worker. If they disagree the screen either
     * promises work that never happens, or reports nothing outstanding during an upload.
     */
    @Test
    fun theCountAgreesWithTheQueue() = runTest {
        entryDao.insertIfNew(
            listOf(
                entry("Camera", "a.jpg"),
                entry("Camera", "b.jpg"),
                entry("Drafts", "c.jpg"),
                entry("Unseen", "d.jpg")
            )
        )
        albumDao.setPreference(AlbumPreferenceEntity("Camera", AlbumMode.BACKUP))
        albumDao.setPreference(AlbumPreferenceEntity("Drafts", AlbumMode.OFF))
        // "Unseen" gets no row at all.

        assertEquals(2, queue().size)
        assertEquals(2, entryDao.countPendingInSelectedAlbums())
    }

    /** Seeding runs on every scan, so it must never overwrite what the user chose. */
    @Test
    fun seedingDoesNotOverwriteAnExistingChoice() = runTest {
        albumDao.setPreference(AlbumPreferenceEntity("Camera", AlbumMode.BACKUP))

        albumDao.insertIfNew(
            listOf(
                AlbumPreferenceEntity("Camera", AlbumMode.OFF),
                AlbumPreferenceEntity("Holiday", AlbumMode.OFF)
            )
        )

        assertEquals(AlbumMode.BACKUP, albumDao.modeOrNull("Camera"))
        assertEquals(AlbumMode.OFF, albumDao.modeOrNull("Holiday"))
    }

    /** A seeded album is inert until chosen — seeding records existence, not consent. */
    @Test
    fun seedingAnAlbumDoesNotMakeItEligible() = runTest {
        entryDao.insertIfNew(listOf(entry("Camera", "a.jpg")))
        albumDao.insertIfNew(listOf(AlbumPreferenceEntity("Camera", AlbumMode.OFF)))

        assertEquals(emptyList<BackupEntryEntity>(), queue())
    }
}
