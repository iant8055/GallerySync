package com.gallery.sync.domain.backup

import android.content.Context
import android.net.Uri
import com.gallery.sync.data.local.dao.AlbumPreferenceDao
import com.gallery.sync.data.local.dao.FolderPreferenceDao
import com.gallery.sync.data.local.dao.BackupEntryDao
import com.gallery.sync.data.local.dao.UnsentDepartureDao
import com.gallery.sync.data.local.entity.AlbumMode
import com.gallery.sync.data.local.entity.AlbumPreferenceEntity
import com.gallery.sync.data.local.media.LocalMediaItem
import com.gallery.sync.data.local.media.MediaAccess
import com.gallery.sync.data.local.media.MediaScanner
import com.gallery.sync.data.local.settings.BackupPreferences
import com.gallery.sync.data.local.settings.BackupSettings
import com.gallery.sync.domain.billing.MultiCloudEntitlement
import com.gallery.sync.domain.repository.GooglePhotosUploadRepository
import com.gallery.sync.domain.repository.OneDriveRepository
import com.gallery.sync.domain.repository.OneDriveUploadRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * The Camera album never takes Sync. Ian, 20 Sept 2026: *"I don't want a user to take a picture/video
 * and then BAM it's optimized already."*
 *
 * Three doors lead to a mode being written, and each has to be shut for this one album: the menu, the
 * seeding of a newly found album, and Select all. The rule is only about what may be *chosen or
 * seeded*, so an album already at Sync is not rewritten here: that would be this code setting a mode.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CameraAlbumTest {

    @Test
    fun `Camera is recognised by name whatever the case`() {
        assertTrue(CameraAlbum.isCamera("Camera"))
        assertTrue(CameraAlbum.isCamera("camera"))
        assertTrue(CameraAlbum.isCamera("CAMERA"))
        assertFalse(CameraAlbum.isCamera("Camera Roll"))
        assertFalse(CameraAlbum.isCamera("Screenshots"))
        assertFalse(CameraAlbum.isCamera(""))
    }

    @Test
    fun `the Camera menu offers Off, Backup and Archive, and not Sync`() {
        assertEquals(
            listOf(AlbumMode.OFF, AlbumMode.BACKUP, AlbumMode.ARCHIVE),
            CameraAlbum.modesFor("Camera")
        )
    }

    @Test
    fun `every other album keeps all four modes`() {
        assertEquals(AlbumMode.entries.toList(), CameraAlbum.modesFor("Screenshots"))
        assertTrue(CameraAlbum.canChoose("Screenshots", AlbumMode.SYNC))
    }

    @Test
    fun `Camera cannot be given Sync and can be given the rest`() {
        assertFalse(CameraAlbum.canChoose("Camera", AlbumMode.SYNC))
        assertTrue(CameraAlbum.canChoose("Camera", AlbumMode.OFF))
        assertTrue(CameraAlbum.canChoose("Camera", AlbumMode.BACKUP))
        assertTrue(CameraAlbum.canChoose("Camera", AlbumMode.ARCHIVE))
    }

    @Test
    fun `seeding turns Sync into Backup for Camera and touches nothing else`() {
        assertEquals(AlbumMode.BACKUP, CameraAlbum.seeded("Camera", AlbumMode.SYNC))
        assertEquals(AlbumMode.OFF, CameraAlbum.seeded("Camera", AlbumMode.OFF))
        assertEquals(AlbumMode.BACKUP, CameraAlbum.seeded("Camera", AlbumMode.BACKUP))
        assertEquals(AlbumMode.SYNC, CameraAlbum.seeded("Screenshots", AlbumMode.SYNC))
    }

    // ── The engine seeds a new Camera album ─────────────────────────────────

    private val scanner: MediaScanner = mock()
    private val entryDao: BackupEntryDao = mock()
    private val albumDao: AlbumPreferenceDao = mock()
    private val settings: BackupSettings = mock()
    private val unsentDao: UnsentDepartureDao = mock()

    private val engine = BackupEngine(
        scanner = scanner,
        entryDao = entryDao,
        albumDao = albumDao,
        folderDao = mock<FolderPreferenceDao>(),
        unsentDao = unsentDao,
        settings = settings,
        repository = mock<OneDriveRepository>(),
        uploadRepository = mock<OneDriveUploadRepository>(),
        googlePhotosUploadRepository = mock<GooglePhotosUploadRepository>(),
        entitlement = mock<MultiCloudEntitlement>(),
        proxyMarker = mock(),
        albumIdentity = mock<AlbumIdentityReconciler>(),
        context = mock<Context>(),
        dispatcher = UnconfinedTestDispatcher()
    )

    private fun scanned(album: String, id: Long) = LocalMediaItem(
        mediaStoreId = id,
        contentUri = mock<Uri>(),
        displayName = "$album-$id.jpg",
        album = album,
        sizeBytes = 1_000L,
        dateModifiedEpochSeconds = 1L,
        mimeType = "image/jpeg",
        isVideo = false,
        relativePath = "DCIM/$album/"
    )

    @Test
    fun `a new Camera album is seeded at Backup even when the default for new albums is Sync`() = runTest {
        whenever(settings.current()).thenReturn(BackupPreferences(defaultAlbumMode = AlbumMode.SYNC))
        val items = listOf(scanned("Camera", 1L), scanned("Screenshots", 2L))
        whenever(scanner.access()).thenReturn(MediaAccess.FULL)
        whenever(scanner.scanAll()).thenReturn(items)
        whenever(scanner.scanEverything()).thenReturn(items)
        whenever(entryDao.proxiedMediaStoreIds()).thenReturn(emptyList())
        whenever(entryDao.uploadedKeys()).thenReturn(emptyList())
        whenever(entryDao.pendingKeys()).thenReturn(emptyList())
        whenever(unsentDao.all()).thenReturn(emptyList())
        whenever(entryDao.countRetrievableOutsideDevice(any(), any())).thenReturn(0)
        whenever(entryDao.forgetAlbumsNotOnDevice(any(), any())).thenReturn(0)

        engine.refreshLedger()

        val seeded = argumentCaptor<List<AlbumPreferenceEntity>>()
        verify(albumDao).insertIfNew(seeded.capture())
        val byName = seeded.firstValue.associate { it.albumName to it.mode }
        assertEquals(AlbumMode.BACKUP, byName["Camera"])
        assertEquals(AlbumMode.SYNC, byName["Screenshots"])
    }
}
