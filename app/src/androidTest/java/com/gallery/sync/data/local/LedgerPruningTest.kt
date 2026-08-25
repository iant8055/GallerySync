package com.gallery.sync.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.gallery.sync.data.local.dao.BackupEntryDao
import com.gallery.sync.data.local.entity.BackupEntryEntity
import com.gallery.sync.data.local.entity.BackupState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * What the ledger is allowed to forget.
 *
 * TODO(instrumented): REQUIRES A DEVICE — Room needs real SQLite. `connectedDebugAndroidTest`
 *  uninstalls the app; drive `am instrument` directly against a device holding a real ledger.
 *
 * ### Why this file exists
 *
 * Observed on the Fold 4, 25 Aug 2026, while verifying retrieval end to end. A file was backed up
 * and verified, its local copy was removed, and the very next scan deleted its ledger row — because
 * removing the album's only file made the whole album absent, and the prune fires on absent albums.
 *
 * That is the Archive path exactly. Archive takes files off the phone; an album emptied that way
 * disappears from the scan; the prune then erases the record of everything ever backed up from it.
 * The user cannot fetch any of it back and nothing explains why.
 *
 * [aVerifiedRowSurvivesItsAlbumLeavingTheDevice] is the guard.
 */
@RunWith(AndroidJUnit4::class)
class LedgerPruningTest {

    private lateinit var database: GallerySyncDatabase
    private lateinit var entryDao: BackupEntryDao

    @Before
    fun createDatabase() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            GallerySyncDatabase::class.java
        ).build()
        entryDao = database.backupEntryDao()
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    private fun entry(
        album: String,
        name: String,
        state: BackupState = BackupState.PENDING,
        remoteItemId: String? = null,
        remoteSizeBytes: Long? = null
    ) = BackupEntryEntity(
        id = "$album/$name",
        mediaStoreId = name.hashCode().toLong(),
        contentUri = "content://media/external/images/media/${name.hashCode()}",
        displayName = name,
        album = album,
        sizeBytes = 1_024L,
        dateModifiedEpochSeconds = 1_700_000_000L,
        mimeType = "image/jpeg",
        isVideo = false,
        state = state,
        remoteItemId = remoteItemId,
        remoteSizeBytes = remoteSizeBytes
    )

    /** The regression test. Without the exemption, Archive makes its own files unrecoverable. */
    @Test
    fun aVerifiedRowSurvivesItsAlbumLeavingTheDevice() = runTest {
        entryDao.insertIfNew(
            listOf(
                entry(
                    album = "Holiday",
                    name = "beach.jpg",
                    state = BackupState.UPLOADED,
                    remoteItemId = "REMOTE-1",
                    remoteSizeBytes = 1_024L
                )
            )
        )

        // The album is gone from the device — every file in it was removed.
        entryDao.forgetAlbumsNotOnDevice(listOf("Camera"))

        assertNotNull(
            "a file still in OneDrive must stay retrievable",
            entryDao.entriesForAlbum("Holiday").firstOrNull()
        )
    }

    @Test
    fun anUnbackedUpRowIsStillForgotten() = runTest {
        entryDao.insertIfNew(listOf(entry(album = "Holiday", name = "beach.jpg")))

        entryDao.forgetAlbumsNotOnDevice(listOf("Camera"))

        assertEquals(
            "nothing holds this file anywhere; the row is just stale",
            emptyList<BackupEntryEntity>(),
            entryDao.entriesForAlbum("Holiday")
        )
    }

    /** Uploaded but never size-checked is not proof, and is not worth keeping either. */
    @Test
    fun anUnverifiedUploadIsForgotten() = runTest {
        entryDao.insertIfNew(
            listOf(
                entry(
                    album = "Holiday",
                    name = "beach.jpg",
                    state = BackupState.UPLOADED,
                    remoteItemId = "REMOTE-1",
                    remoteSizeBytes = 512L
                )
            )
        )

        entryDao.forgetAlbumsNotOnDevice(listOf("Camera"))

        assertNull(entryDao.entriesForAlbum("Holiday").firstOrNull())
    }

    /**
     * The trap this whole area turns on.
     *
     * The skip-existing path used to record `remoteItemId = ""` — enough to say "already backed up",
     * not enough to ever fetch it back. On the Fold 4 that path covered 6,278 of 6,371 files, so
     * retrieval would have been able to offer almost nothing while the ledger claimed everything was
     * safe. A row without a usable id is not retrievable and must not be presented as if it were.
     */
    @Test
    fun anUploadWithNoRemoteIdIsNotRetrievable() = runTest {
        entryDao.insertIfNew(
            listOf(
                entry(
                    album = "Holiday",
                    name = "beach.jpg",
                    state = BackupState.UPLOADED,
                    remoteItemId = "",
                    remoteSizeBytes = 1_024L
                )
            )
        )
        entryDao.markLocalMissing(listOf("Holiday/beach.jpg"), now = 1_700_000_000_000L)

        assertEquals(emptyList<BackupEntryEntity>(), entryDao.observeRetrievable().first())
    }

    @Test
    fun albumsStillOnTheDeviceAreUntouched() = runTest {
        entryDao.insertIfNew(listOf(entry(album = "Camera", name = "a.jpg")))

        entryDao.forgetAlbumsNotOnDevice(listOf("Camera"))

        assertEquals(1, entryDao.entriesForAlbum("Camera").size)
    }

    /** A retrievable row is exactly what the "get back" list is built from. */
    @Test
    fun aSurvivingRowIsOfferedForRetrievalOnceItsFileIsGone() = runTest {
        entryDao.insertIfNew(
            listOf(
                entry(
                    album = "Holiday",
                    name = "beach.jpg",
                    state = BackupState.UPLOADED,
                    remoteItemId = "REMOTE-1",
                    remoteSizeBytes = 1_024L
                )
            )
        )

        entryDao.markLocalMissing(listOf("Holiday/beach.jpg"), now = 1_700_000_000_000L)
        entryDao.forgetAlbumsNotOnDevice(listOf("Camera"))

        val retrievable = entryDao.observeRetrievable().first()
        assertEquals(listOf("beach.jpg"), retrievable.map { it.displayName })
    }
}
