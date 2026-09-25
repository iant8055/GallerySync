package com.gallery.sync.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.gallery.sync.data.local.dao.BackupEntryDao
import com.gallery.sync.data.local.entity.AlbumMode
import com.gallery.sync.data.local.entity.AlbumPreferenceEntity
import com.gallery.sync.data.local.entity.BackupEntryEntity
import com.gallery.sync.data.local.entity.BackupState
import com.gallery.sync.data.local.entity.CloudCopyDecision
import com.gallery.sync.domain.backup.BackupLocation
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * TASK-027 stage 2: which uploaded rows Sync (optimise) may consider, and which proxies Restore may put back.
 *
 * The clouds that can Sync carry no remembered size by design, so they are matched on having uploaded with an id.
 * Google Photos and pCloud must never appear. The live per-file check still stands in front of every overwrite.
 */
@RunWith(AndroidJUnit4::class)
class SyncCandidatesTest {

    private lateinit var database: GallerySyncDatabase
    private lateinit var entryDao: BackupEntryDao

    @Before
    fun createDatabase() = runTest {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            GallerySyncDatabase::class.java
        ).build()
        entryDao = database.backupEntryDao()
        database.albumPreferenceDao().insertIfNew(listOf(AlbumPreferenceEntity("Sync", AlbumMode.SYNC)))
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    private fun row(
        name: String,
        location: BackupLocation,
        state: BackupState = BackupState.UPLOADED,
        remoteId: String? = "id:$name",
        remoteSize: Long? = null,
        isVideo: Boolean = false,
        proxied: Boolean = false,
        missingSince: Long? = null,
        decision: CloudCopyDecision? = null
    ) = BackupEntryEntity(
        id = "Sync/$name",
        mediaStoreId = name.hashCode().toLong(),
        contentUri = "content://media/external/images/media/${name.hashCode()}",
        displayName = name,
        album = "Sync",
        sizeBytes = 1_024L,
        dateModifiedEpochSeconds = 1_000L,
        mimeType = if (isVideo) "video/mp4" else "image/jpeg",
        isVideo = isVideo,
        state = state,
        remoteItemId = remoteId,
        remoteSizeBytes = remoteSize,
        isProxied = proxied,
        localProxySizeBytes = if (proxied) 400L else null,
        localMissingSinceEpochMillis = missingSince,
        cloudDecision = decision,
        location = location
    )

    @Test
    fun photoCandidatesIncludeTheFourCloudsAndVerifiedOneDriveOnly() = runTest {
        entryDao.insertIfNew(
            listOf(
                row("onedrive-verified.jpg", BackupLocation.ONEDRIVE, remoteSize = 1_024L),
                row("onedrive-unverified.jpg", BackupLocation.ONEDRIVE),
                row("dropbox.jpg", BackupLocation.DROPBOX),
                row("drive.jpg", BackupLocation.GOOGLE_DRIVE),
                row("b2.jpg", BackupLocation.BACKBLAZE_B2),
                row("idrive.jpg", BackupLocation.IDRIVE_E2),
                row("gphotos.jpg", BackupLocation.GOOGLE_PHOTOS),
                row("pcloud.jpg", BackupLocation.PCLOUD),
                row("pending.jpg", BackupLocation.DROPBOX, state = BackupState.PENDING),
                row("noid.jpg", BackupLocation.DROPBOX, remoteId = ""),
                row("video.mp4", BackupLocation.DROPBOX, isVideo = true)
            )
        )

        val names = entryDao.proxyCandidates().map { it.displayName }.toSet()

        assertEquals(
            setOf("onedrive-verified.jpg", "dropbox.jpg", "drive.jpg", "b2.jpg", "idrive.jpg"),
            names
        )
    }

    @Test
    fun videoCandidatesFollowTheSameRule() = runTest {
        entryDao.insertIfNew(
            listOf(
                row("d.mp4", BackupLocation.DROPBOX, isVideo = true),
                row("g.mp4", BackupLocation.GOOGLE_PHOTOS, isVideo = true),
                row("o.mp4", BackupLocation.ONEDRIVE, isVideo = true, remoteSize = 1_024L)
            )
        )

        val names = entryDao.videoOptimiseCandidatesAll(cutoffMillis = 0L).map { it.displayName }.toSet()

        assertEquals(setOf("d.mp4", "o.mp4"), names)
    }

    @Test
    fun restorableProxiesIncludeAnotherCloudsRowWithAnIdAndNoSize() = runTest {
        entryDao.insertIfNew(
            listOf(
                row("dropbox.jpg", BackupLocation.DROPBOX, proxied = true),
                row("onedrive.jpg", BackupLocation.ONEDRIVE, remoteSize = 1_024L, proxied = true),
                row("onedrive-unverified.jpg", BackupLocation.ONEDRIVE, proxied = true),
                row("gphotos.jpg", BackupLocation.GOOGLE_PHOTOS, proxied = true),
                row("noid.jpg", BackupLocation.DROPBOX, remoteId = null, proxied = true)
            )
        )

        val names = entryDao.restorableProxies().map { it.displayName }.toSet()

        assertEquals(setOf("dropbox.jpg", "onedrive.jpg"), names)
    }

    /**
     * 25 Sept 2026: Temp0's rows for files Archive had removed led the Sync queue, failed on the trashed file, and
     * stopped every photo behind them. A row whose file is gone has nothing to shrink.
     */
    @Test
    fun rowsWhoseFileHasGoneAreNeverCandidates() = runTest {
        entryDao.insertIfNew(
            listOf(
                row("here.jpg", BackupLocation.DROPBOX),
                row("archived.jpg", BackupLocation.DROPBOX, decision = CloudCopyDecision.ARCHIVED),
                row("missing.jpg", BackupLocation.ONEDRIVE, remoteSize = 1_024L, missingSince = 5L)
            )
        )

        assertEquals(setOf("here.jpg"), entryDao.proxyCandidates().map { it.displayName }.toSet())
        assertEquals(setOf("here.jpg"), entryDao.proxyCandidatesAll(cutoffMillis = 0L).map { it.displayName }.toSet())
    }
}
