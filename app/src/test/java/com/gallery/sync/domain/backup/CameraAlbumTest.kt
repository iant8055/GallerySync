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
import com.gallery.sync.domain.repository.CloudUploaders
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
 * The camera folder never takes Sync. Ian, 20 Sept 2026: *"I don't want a user to take a picture/video
 * and then BAM it's optimized already."* And since 27 Sept 2026 there is no camera folder until the person
 * turns on *Special settings for Camera* and picks one: nothing is assumed, not even the album named Camera.
 *
 * Three doors lead to a mode being written, and each has to be shut for the camera folder: the menu, the
 * seeding of a newly found album, and Select all. The rule is only about what may be *chosen or
 * seeded*, so an album already at Sync is not rewritten here: that would be this code setting a mode.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CameraAlbumTest {

    /** Ian, 27 Sept 2026: the switch starts at No, "otherwise we are assuming what folder the system is saving camera to". */
    @Test
    fun `with the switch off or no folder picked there is no camera folder, not even one named Camera`() {
        assertEquals(null, CameraAlbum.chosen(enabled = false, folder = "Camera"))
        assertEquals(null, CameraAlbum.chosen(enabled = true, folder = ""))
        assertEquals(null, CameraAlbum.chosen(enabled = false, folder = ""))
        assertFalse(CameraAlbum.isCamera("Camera", null))
        assertEquals(AlbumMode.entries.toList(), CameraAlbum.modesFor("Camera", null))
        assertTrue(CameraAlbum.canChoose("Camera", AlbumMode.SYNC, null))
        assertEquals(AlbumMode.SYNC, CameraAlbum.seeded("Camera", AlbumMode.SYNC, null))
    }

    /** Other… in Settings: the album name comes from the picked folder's tree document id. Ian, 27 Sept 2026. */
    @Test
    fun `a folder picked with the picker is named by its last folder, and a storage root is not a folder`() {
        assertEquals("OpenCamera", CameraAlbum.folderNameFromTreeDocumentId("primary:DCIM/OpenCamera"))
        assertEquals("Camera", CameraAlbum.folderNameFromTreeDocumentId("primary:DCIM/Camera/"))
        assertEquals("DCIM", CameraAlbum.folderNameFromTreeDocumentId("1A2B-3C4D:DCIM"))
        assertEquals(null, CameraAlbum.folderNameFromTreeDocumentId("primary:"))
        assertEquals(null, CameraAlbum.folderNameFromTreeDocumentId("primary:/"))
    }

    @Test
    fun `with the switch on the picked folder is the camera folder`() {
        assertEquals("OpenCamera", CameraAlbum.chosen(enabled = true, folder = "OpenCamera"))
    }

    @Test
    fun `the camera folder is recognised by name whatever the case`() {
        assertTrue(CameraAlbum.isCamera("Camera", "Camera"))
        assertTrue(CameraAlbum.isCamera("camera", "Camera"))
        assertTrue(CameraAlbum.isCamera("CAMERA", "Camera"))
        assertFalse(CameraAlbum.isCamera("Camera Roll", "Camera"))
        assertFalse(CameraAlbum.isCamera("Screenshots", "Camera"))
        assertFalse(CameraAlbum.isCamera("", "Camera"))
    }

    @Test
    fun `a picked folder takes the Camera rules and the album named Camera loses them`() {
        val chosen = "OpenCamera"
        assertTrue(CameraAlbum.isCamera("opencamera", chosen))
        assertFalse(CameraAlbum.isCamera("Camera", chosen))
        assertFalse(CameraAlbum.canChoose("OpenCamera", AlbumMode.SYNC, chosen))
        assertTrue(CameraAlbum.canChoose("Camera", AlbumMode.SYNC, chosen))
        assertEquals(AlbumMode.BACKUP, CameraAlbum.seeded("OpenCamera", AlbumMode.SYNC, chosen))
        assertEquals(AlbumMode.SYNC, CameraAlbum.seeded("Camera", AlbumMode.SYNC, chosen))
    }

    @Test
    fun `the camera folder's menu offers Off, Backup and Archive, and not Sync`() {
        assertEquals(
            listOf(AlbumMode.OFF, AlbumMode.BACKUP, AlbumMode.ARCHIVE),
            CameraAlbum.modesFor("Camera", "Camera")
        )
    }

    @Test
    fun `every other album keeps all four modes`() {
        assertEquals(AlbumMode.entries.toList(), CameraAlbum.modesFor("Screenshots", "Camera"))
        assertTrue(CameraAlbum.canChoose("Screenshots", AlbumMode.SYNC, "Camera"))
    }

    @Test
    fun `the camera folder cannot be given Sync and can be given the rest`() {
        assertFalse(CameraAlbum.canChoose("Camera", AlbumMode.SYNC, "Camera"))
        assertTrue(CameraAlbum.canChoose("Camera", AlbumMode.OFF, "Camera"))
        assertTrue(CameraAlbum.canChoose("Camera", AlbumMode.BACKUP, "Camera"))
        assertTrue(CameraAlbum.canChoose("Camera", AlbumMode.ARCHIVE, "Camera"))
    }

    @Test
    fun `seeding turns Sync into Backup for the camera folder and touches nothing else`() {
        assertEquals(AlbumMode.BACKUP, CameraAlbum.seeded("Camera", AlbumMode.SYNC, "Camera"))
        assertEquals(AlbumMode.OFF, CameraAlbum.seeded("Camera", AlbumMode.OFF, "Camera"))
        assertEquals(AlbumMode.BACKUP, CameraAlbum.seeded("Camera", AlbumMode.BACKUP, "Camera"))
        assertEquals(AlbumMode.SYNC, CameraAlbum.seeded("Screenshots", AlbumMode.SYNC, "Camera"))
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
        uploaders = mock<CloudUploaders>(),
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
    fun `every new album, Camera included, is seeded at Off`() = runTest {
        whenever(settings.current()).thenReturn(BackupPreferences())
        val items = listOf(scanned("Camera", 1L), scanned("Screenshots", 2L))
        whenever(scanner.access()).thenReturn(MediaAccess.FULL)
        whenever(scanner.scanAll()).thenReturn(items)
        whenever(scanner.scanEverything()).thenReturn(items)
        whenever(entryDao.proxiedSizes()).thenReturn(emptyList())
        whenever(entryDao.uploadedKeys()).thenReturn(emptyList())
        whenever(entryDao.pendingKeys()).thenReturn(emptyList())
        whenever(unsentDao.all()).thenReturn(emptyList())
        whenever(entryDao.countRetrievableOutsideDevice(any(), any())).thenReturn(0)
        whenever(entryDao.forgetAlbumsNotOnDevice(any(), any())).thenReturn(0)

        engine.refreshLedger()

        val seeded = argumentCaptor<List<AlbumPreferenceEntity>>()
        verify(albumDao).insertIfNew(seeded.capture())
        val byName = seeded.firstValue.associate { it.albumName to it.mode }
        assertEquals(AlbumMode.OFF, byName["Camera"])
        assertEquals(AlbumMode.OFF, byName["Screenshots"])
    }
}
