package com.gallery.sync.domain.backup

import com.gallery.sync.data.local.entity.AlbumMode
import com.gallery.sync.data.local.entity.AlbumPreferenceEntity
import com.gallery.sync.data.local.entity.BackupEntryEntity
import com.gallery.sync.data.local.entity.BackupState
import com.gallery.sync.data.local.entity.backupKeyOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * TASK-023: one folder under several spellings is one album, and merging it always ends Off.
 *
 * The cases are the ones the Moto G produced on 16 Sept 2026, not invented ones: a copied `camera`
 * folder beside the camera app's `Camera`, and a restore after the folder changed case, which left
 * two ledger rows for one file.
 */
class AlbumIdentityRulesTest {

    private fun entry(
        album: String,
        name: String = "IMG_1.jpg",
        size: Long = 1_000,
        mediaStoreId: Long = 1,
        state: BackupState = BackupState.UPLOADED,
        remoteItemId: String? = "remote-1",
        isProxied: Boolean = false,
        uploadedAt: Long? = 100,
        missingSince: Long? = null
    ) = BackupEntryEntity(
        id = backupKeyOf(album, name, size, 50),
        mediaStoreId = mediaStoreId,
        contentUri = "content://media/external/images/media/$mediaStoreId",
        displayName = name,
        album = album,
        sizeBytes = size,
        dateModifiedEpochSeconds = 50,
        mimeType = "image/jpeg",
        isVideo = false,
        state = state,
        remoteItemId = remoteItemId,
        remoteSizeBytes = if (remoteItemId != null) size else null,
        uploadedAtEpochMillis = uploadedAt,
        isProxied = isProxied,
        localMissingSinceEpochMillis = missingSince
    )

    private fun device(name: String, path: String = "DCIM/${name.lowercase()}") =
        DeviceAlbum(name, path)

    // ---------- when a merge happens ----------

    @Test
    fun `one spelling everywhere is left alone`() {
        val plan = AlbumIdentityRules.plan(
            deviceAlbums = listOf(device("Camera")),
            preferences = listOf(AlbumPreferenceEntity("Camera", AlbumMode.ARCHIVE)),
            cloudStatusNames = listOf("Camera"),
            entries = listOf(entry("Camera"))
        )
        assertTrue(plan.isEmpty())
    }

    @Test
    fun `a new album with nothing stored is not a discrepancy`() {
        val plan = AlbumIdentityRules.plan(listOf(device("Camera")), emptyList(), emptyList(), emptyList())
        assertTrue(plan.isEmpty())
    }

    @Test
    fun `Archive beside Off merges under the device spelling`() {
        val plan = AlbumIdentityRules.plan(
            deviceAlbums = listOf(device("Camera")),
            preferences = listOf(
                AlbumPreferenceEntity("camera", AlbumMode.ARCHIVE),
                AlbumPreferenceEntity("Camera", AlbumMode.OFF)
            ),
            cloudStatusNames = emptyList(),
            entries = listOf(entry("camera"))
        ).single()

        assertEquals("Camera", plan.target)
        assertEquals(listOf("Camera", "camera"), plan.spellings)
        assertEquals(mapOf("camera" to AlbumMode.ARCHIVE, "Camera" to AlbumMode.OFF), plan.previousModes)
    }

    @Test
    fun `two spellings both Backup still merge`() {
        val plan = AlbumIdentityRules.plan(
            deviceAlbums = listOf(device("Camera")),
            preferences = listOf(
                AlbumPreferenceEntity("camera", AlbumMode.BACKUP),
                AlbumPreferenceEntity("Camera", AlbumMode.BACKUP)
            ),
            cloudStatusNames = emptyList(),
            entries = emptyList()
        )
        assertEquals(1, plan.size)
    }

    @Test
    fun `two spellings both Off still merge, because Ian asked to be warned anyway`() {
        val plan = AlbumIdentityRules.plan(
            deviceAlbums = listOf(device("Camera")),
            preferences = listOf(
                AlbumPreferenceEntity("camera", AlbumMode.OFF),
                AlbumPreferenceEntity("Camera", AlbumMode.OFF)
            ),
            cloudStatusNames = emptyList(),
            entries = emptyList()
        )
        assertEquals(1, plan.size)
    }

    @Test
    fun `a single stored spelling the folder no longer has is renamed`() {
        // The folder was renamed on disk from camera to Camera. Nothing on the phone says camera now.
        val plan = AlbumIdentityRules.plan(
            deviceAlbums = listOf(device("Camera")),
            preferences = listOf(AlbumPreferenceEntity("camera", AlbumMode.ARCHIVE)),
            cloudStatusNames = listOf("camera"),
            entries = listOf(entry("camera"))
        ).single()

        assertEquals("Camera", plan.target)
        assertEquals("Camera", plan.entries.single().album)
    }

    @Test
    fun `names that differ by more than case are never merged`() {
        val plan = AlbumIdentityRules.plan(
            deviceAlbums = listOf(device("Camera"), device("Camera (1)")),
            preferences = listOf(
                AlbumPreferenceEntity("Camera", AlbumMode.ARCHIVE),
                AlbumPreferenceEntity("Camera (1)", AlbumMode.OFF)
            ),
            cloudStatusNames = emptyList(),
            entries = emptyList()
        )
        assertTrue(plan.isEmpty())
    }

    @Test
    fun `two different folders whose names differ only in case are left apart`() {
        val plan = AlbumIdentityRules.plan(
            deviceAlbums = listOf(
                DeviceAlbum("Camera", "dcim/camera"),
                DeviceAlbum("camera", "pictures/camera")
            ),
            preferences = listOf(
                AlbumPreferenceEntity("Camera", AlbumMode.ARCHIVE),
                AlbumPreferenceEntity("camera", AlbumMode.OFF)
            ),
            cloudStatusNames = emptyList(),
            entries = emptyList()
        )
        assertTrue(plan.isEmpty())
    }

    @Test
    fun `with the folder gone from the phone, the newest ledger spelling is kept`() {
        val plan = AlbumIdentityRules.plan(
            deviceAlbums = emptyList(),
            preferences = listOf(
                AlbumPreferenceEntity("camera", AlbumMode.ARCHIVE),
                AlbumPreferenceEntity("Camera", AlbumMode.OFF)
            ),
            cloudStatusNames = emptyList(),
            entries = listOf(
                entry("camera", name = "old.jpg", mediaStoreId = 10),
                entry("Camera", name = "new.jpg", mediaStoreId = 20)
            )
        ).single()

        assertEquals("Camera", plan.target)
    }

    // ---------- ledger rows ----------

    @Test
    fun `one file under two spellings collapses to one row keeping the cloud copy`() {
        // The restore test: camera/… uploaded, Camera/… pending with no remote id.
        val rows = listOf(
            entry("camera", remoteItemId = "F6D6!abc", state = BackupState.UPLOADED),
            entry("Camera", remoteItemId = null, state = BackupState.PENDING, uploadedAt = null)
        )

        val merged = AlbumIdentityRules.mergeEntries(rows, target = "Camera").single()

        assertEquals("F6D6!abc", merged.remoteItemId)
        assertEquals(BackupState.UPLOADED, merged.state)
        assertEquals("Camera", merged.album)
        assertEquals(backupKeyOf("Camera", "IMG_1.jpg", 1_000, 50), merged.id)
    }

    @Test
    fun `a proxied row survives the merge`() {
        val rows = listOf(
            entry("Camera", remoteItemId = "r", isProxied = false),
            entry("camera", remoteItemId = "r", isProxied = true)
        )
        assertTrue(AlbumIdentityRules.mergeEntries(rows, target = "Camera").single().isProxied)
    }

    @Test
    fun `a missing-file flag known only to the dropped row is carried over`() {
        val rows = listOf(
            entry("Camera", remoteItemId = "r", uploadedAt = 1),
            entry("camera", remoteItemId = null, state = BackupState.PENDING, missingSince = 777)
        )
        assertEquals(777L, AlbumIdentityRules.mergeEntries(rows, "Camera").single().localMissingSinceEpochMillis)
    }

    @Test
    fun `different files are all kept, whatever their spelling`() {
        val rows = listOf(
            entry("camera", name = "a.jpg", mediaStoreId = 1),
            entry("Camera", name = "b.jpg", mediaStoreId = 2)
        )
        assertEquals(2, AlbumIdentityRules.mergeEntries(rows, "Camera").size)
    }

    @Test
    fun `rows sharing a reused MediaStore id but not a file are not collapsed`() {
        // MediaStore reuses ids, so an id is never identity. See BackupEntryEntity.id.
        val rows = listOf(
            entry("camera", name = "a.jpg", mediaStoreId = 5),
            entry("Camera", name = "b.jpg", mediaStoreId = 5)
        )
        assertEquals(2, AlbumIdentityRules.mergeEntries(rows, "Camera").size)
    }

    @Test
    fun `a second plan over merged state changes nothing`() {
        val first = AlbumIdentityRules.plan(
            deviceAlbums = listOf(device("Camera")),
            preferences = listOf(
                AlbumPreferenceEntity("camera", AlbumMode.ARCHIVE),
                AlbumPreferenceEntity("Camera", AlbumMode.OFF)
            ),
            cloudStatusNames = listOf("camera"),
            entries = listOf(entry("camera"), entry("Camera", remoteItemId = null))
        ).single()

        val second = AlbumIdentityRules.plan(
            deviceAlbums = listOf(device("Camera")),
            preferences = listOf(AlbumPreferenceEntity(first.target, AlbumMode.OFF)),
            cloudStatusNames = emptyList(),
            entries = first.entries
        )
        assertTrue(second.isEmpty())
    }

    // ---------- storage ----------

    @Test
    fun `a warning survives encoding`() {
        val warning = AlbumMergeWarning(
            albumName = "Camera",
            spellings = listOf("Camera", "camera"),
            previousModes = mapOf("camera" to AlbumMode.ARCHIVE, "Camera" to AlbumMode.OFF),
            atEpochMillis = 1_789_600_000_000
        )
        assertEquals(warning, AlbumIdentityRules.decode(AlbumIdentityRules.encode(warning)))
    }

    @Test
    fun `a warning with no stored modes survives encoding`() {
        val warning = AlbumMergeWarning("car show", listOf("Car Show", "car show"), emptyMap(), 5)
        assertEquals(warning, AlbumIdentityRules.decode(AlbumIdentityRules.encode(warning)))
    }

    @Test
    fun `an unreadable warning is dropped rather than half shown`() {
        assertNull(AlbumIdentityRules.decode("garbage"))
    }
}
