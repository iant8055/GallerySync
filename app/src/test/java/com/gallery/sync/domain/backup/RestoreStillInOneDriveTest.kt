package com.gallery.sync.domain.backup

import com.gallery.sync.data.local.entity.BackupEntryEntity
import com.gallery.sync.data.local.entity.BackupState
import org.junit.Assert.assertEquals
import org.junit.Test

class RestoreStillInOneDriveTest {

    private fun row(
        album: String,
        name: String,
        size: Long = 1_000L,
        remoteId: String? = "id-$name",
        location: BackupLocation = BackupLocation.ONEDRIVE
    ) = BackupEntryEntity(
        id = "$album/$name",
        mediaStoreId = 1L,
        contentUri = "content://media/external/images/media/1",
        displayName = name,
        album = album,
        sizeBytes = size,
        dateModifiedEpochSeconds = 50,
        mimeType = "image/jpeg",
        isVideo = false,
        state = BackupState.UPLOADED,
        remoteItemId = remoteId,
        remoteSizeBytes = size,
        location = location
    )

    private fun ref(id: String, size: Long?) = RemoteFileRef(id, size, "image/jpeg", 0L)

    private fun listing(vararg folders: Pair<String, Map<String, RemoteFileRef>>, partial: Boolean = false) =
        DriveListing(folders.toMap(), couldNotList = partial)

    private fun kept(rows: List<BackupEntryEntity>, listing: DriveListing?) =
        RestoreStillInOneDrive.keep(rows, listing).map { it.id }

    @Test
    fun `a folder OneDrive no longer has drops every row in it`() {
        val rows = listOf(row("Car Show", "a.jpg"), row("PauseTest", "p.mp4"))
        val l = listing("PauseTest" to mapOf("p.mp4" to ref("id-p.mp4", 1_000L)))
        assertEquals(listOf("PauseTest/p.mp4"), kept(rows, l))
    }

    @Test
    fun `a file deleted from a folder that is still there is dropped`() {
        val rows = listOf(row("Pics", "a.jpg"), row("Pics", "b.jpg"))
        val l = listing("Pics" to mapOf("a.jpg" to ref("id-a.jpg", 1_000L)))
        assertEquals(listOf("Pics/a.jpg"), kept(rows, l))
    }

    @Test
    fun `no listing keeps every row`() {
        val rows = listOf(row("Car Show", "a.jpg"))
        assertEquals(listOf("Car Show/a.jpg"), kept(rows, null))
    }

    @Test
    fun `a partial listing keeps every row`() {
        val rows = listOf(row("Car Show", "a.jpg"))
        assertEquals(listOf("Car Show/a.jpg"), kept(rows, listing(partial = true)))
    }

    @Test
    fun `rows for another Cloud are never judged by OneDrive`() {
        val rows = listOf(row("DropBox", "d.jpg", location = BackupLocation.DROPBOX))
        assertEquals(listOf("DropBox/d.jpg"), kept(rows, listing()))
    }

    @Test
    fun `found by the upload id under another name`() {
        val rows = listOf(row("Pics", "old.jpg", remoteId = "id-9"))
        val l = listing("Pics" to mapOf("renamed.jpg" to ref("id-9", 1_000L)))
        assertEquals(listOf("Pics/old.jpg"), kept(rows, l))
    }

    @Test
    fun `found by name when no id was recorded`() {
        val rows = listOf(row("Pics", "a.jpg", remoteId = null))
        val l = listing("Pics" to mapOf("a.jpg" to ref("other", 1_000L)))
        assertEquals(listOf("Pics/a.jpg"), kept(rows, l))
    }

    @Test
    fun `same name at another size is not this file`() {
        val rows = listOf(row("Pics", "a.jpg", remoteId = null))
        val l = listing("Pics" to mapOf("a.jpg" to ref("other", 2_000L)))
        assertEquals(emptyList<String>(), kept(rows, l))
    }

    @Test
    fun `an entry listed with no size is unknown and kept`() {
        val rows = listOf(row("Pics", "a.jpg"))
        val l = listing("Pics" to mapOf("a.jpg" to ref("x", null)))
        assertEquals(listOf("Pics/a.jpg"), kept(rows, l))
    }

    @Test
    fun `folder names match ignoring case`() {
        val rows = listOf(row("camera", "a.jpg"))
        val l = listing("Camera" to mapOf("a.jpg" to ref("id-a.jpg", 1_000L)))
        assertEquals(listOf("camera/a.jpg"), kept(rows, l))
    }
    @Test
    fun `an earlier folder finds this copy's own file by id or by name, at its size`() {
        val rows = listOf(
            row("Camera", "byid.jpg"),
            row("Camera", "byname.jpg", remoteId = null),
            row("Camera", "resized.jpg"),
            row("Camera", "absent.jpg")
        )
        val index = mapOf(
            "renamed.jpg" to ref("id-byid.jpg", 1_000L),
            "byname.jpg" to ref("other", 1_000L),
            "resized.jpg" to ref("id-resized.jpg", 999L)
        )
        assertEquals(
            setOf("Camera/byid.jpg", "Camera/byname.jpg"),
            RestoreStillInOneDrive.foundIn(rows, index)
        )
    }

    @Test
    fun `an earlier folder cannot offer a file this copy has no row for`() {
        val index = mapOf("main-gallery.jpg" to ref("id-main", 1_000L))
        assertEquals(emptySet<String>(), RestoreStillInOneDrive.foundIn(emptyList(), index))
    }
}
