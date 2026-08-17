package com.gallery.sync.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.gallery.sync.data.local.dao.MediaFolderDao
import com.gallery.sync.data.local.dao.MediaItemDao
import com.gallery.sync.data.local.entity.MediaFolderEntity
import com.gallery.sync.data.local.entity.MediaItemEntity
import com.gallery.sync.data.local.entity.MediaSource
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * DAO tests for [GallerySyncDatabase].
 *
 * TODO(instrumented): REQUIRES A CONNECTED DEVICE OR EMULATOR.
 *  Room needs a real SQLite implementation, which the local JVM unit-test classpath does not have.
 *  Robolectric is deliberately NOT wired into this project, so these live in `androidTest` and run
 *  only via `./gradlew connectedDebugAndroidTest`. They have NEVER been executed in CI or on the
 *  development machine as of v0.1.0 — do not treat them as passing.
 *
 * Everything exercised here is cache/index management. No assertion in this file implies, or
 * should ever be extended to imply, deleting a user's cloud file.
 */
@RunWith(AndroidJUnit4::class)
class GallerySyncDatabaseTest {

    private lateinit var database: GallerySyncDatabase
    private lateinit var itemDao: MediaItemDao
    private lateinit var folderDao: MediaFolderDao

    @Before
    fun createDatabase() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            GallerySyncDatabase::class.java
        ).build()
        itemDao = database.mediaItemDao()
        folderDao = database.mediaFolderDao()
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    // ---------- MediaItemDao ----------

    @Test
    fun upsertThenObserveByParentReturnsTheChildren() = runTest {
        itemDao.upsertAll(
            listOf(
                item(remoteId = "CHILD_B", name = "b.jpg", parentFolderId = "onedrive:FOLDER"),
                item(remoteId = "CHILD_A", name = "a.jpg", parentFolderId = "onedrive:FOLDER"),
                item(remoteId = "ELSEWHERE", name = "z.jpg", parentFolderId = "onedrive:OTHER")
            )
        )

        val children = itemDao.observeByParent("onedrive:FOLDER").first()

        // The query carries ORDER BY name, so the expected order is alphabetical, not insert order.
        assertEquals(listOf("a.jpg", "b.jpg"), children.map { it.name })
    }

    @Test
    fun observeByParentWithNullReturnsOnlyRootRows() = runTest {
        itemDao.upsertAll(
            listOf(
                item(remoteId = "ROOT_ONE", name = "root1.jpg", parentFolderId = null),
                item(remoteId = "ROOT_TWO", name = "root2.jpg", parentFolderId = null),
                item(remoteId = "NESTED", name = "nested.jpg", parentFolderId = "onedrive:FOLDER")
            )
        )

        val roots = itemDao.observeByParent(null).first()

        // `parentFolderId = NULL` is never true in SQL; the IS NULL branch is what makes this work.
        assertEquals(listOf("root1.jpg", "root2.jpg"), roots.map { it.name })
        assertTrue(roots.all { it.parentFolderId == null })
    }

    @Test
    fun observeAllReturnsEveryRowOrderedByName() = runTest {
        itemDao.upsertAll(
            listOf(
                item(remoteId = "C", name = "c.jpg", parentFolderId = "onedrive:FOLDER"),
                item(remoteId = "A", name = "a.jpg", parentFolderId = null)
            )
        )

        assertEquals(listOf("a.jpg", "c.jpg"), itemDao.observeAll().first().map { it.name })
    }

    @Test
    fun upsertReplacesAnExistingRowRatherThanDuplicatingIt() = runTest {
        itemDao.upsertAll(listOf(item(remoteId = "SAME", name = "before.jpg")))
        itemDao.upsertAll(listOf(item(remoteId = "SAME", name = "after.jpg", sizeBytes = 99L)))

        val all = itemDao.observeAll().first()

        assertEquals(1, all.size)
        assertEquals("after.jpg", all.single().name)
        assertEquals(99L, all.single().sizeBytes)
    }

    @Test
    fun getByIdReturnsTheRowAndNullForAnUnknownId() = runTest {
        itemDao.upsertAll(listOf(item(remoteId = "ABC123", name = "found.jpg")))

        assertNotNull(itemDao.getById(MediaItemEntity.buildId(MediaSource.ONEDRIVE, "ABC123")))
        assertNull(itemDao.getById("onedrive:NOPE"))
    }

    @Test
    fun touchLastAccessedUpdatesTheTimestamp() = runTest {
        val id = MediaItemEntity.buildId(MediaSource.ONEDRIVE, "ABC123")
        itemDao.upsertAll(listOf(item(remoteId = "ABC123", lastAccessedUtc = null)))

        itemDao.touchLastAccessed(id, 1_705_314_600_000L)

        assertEquals(1_705_314_600_000L, itemDao.getById(id)?.lastAccessedUtc)
    }

    @Test
    fun clearLocalCacheRefNullsThePathButLeavesTheRowPresent() = runTest {
        val id = MediaItemEntity.buildId(MediaSource.ONEDRIVE, "ABC123")
        itemDao.upsertAll(
            listOf(
                item(
                    remoteId = "ABC123",
                    localCachePath = "/data/cache/abc.jpg",
                    cachedAtUtc = 1_705_314_600_000L
                )
            )
        )

        itemDao.clearLocalCacheRef(id)

        val row = itemDao.getById(id)
        // The row surviving is the whole point: this is cache eviction, never an index delete and
        // certainly never a remote delete.
        assertNotNull("clearLocalCacheRef must not remove the index row", row)
        assertNull(row?.localCachePath)
        assertNull(row?.cachedAtUtc)
        assertEquals("photo.jpg", row?.name)
    }

    @Test
    fun deleteIndexRowsBySourceRemovesOnlyThatSource() = runTest {
        itemDao.upsertAll(
            listOf(
                item(source = MediaSource.ONEDRIVE, remoteId = "OD", name = "od.jpg"),
                item(source = MediaSource.GOOGLE_PHOTOS, remoteId = "GP", name = "gp.jpg")
            )
        )

        itemDao.deleteIndexRowsBySource(MediaSource.ONEDRIVE)

        val remaining = itemDao.observeAll().first()
        assertEquals(listOf("gp.jpg"), remaining.map { it.name })
    }

    @Test
    fun observeByParentEmitsAgainWhenTheUnderlyingRowsChange() = runTest {
        assertTrue(itemDao.observeByParent(null).first().isEmpty())

        itemDao.upsertAll(listOf(item(remoteId = "NEW", name = "new.jpg", parentFolderId = null)))

        assertEquals(1, itemDao.observeByParent(null).first().size)
    }

    // ---------- MediaFolderDao ----------

    @Test
    fun folderObserveChildrenHandlesTheNullRootCase() = runTest {
        folderDao.upsertAll(
            listOf(
                folder(remoteId = "ROOT", name = "Pictures", parentFolderId = null),
                folder(remoteId = "SUB", name = "Holiday", parentFolderId = "onedrive:ROOT")
            )
        )

        assertEquals(listOf("Pictures"), folderDao.observeChildren(null).first().map { it.name })
        assertEquals(
            listOf("Holiday"),
            folderDao.observeChildren("onedrive:ROOT").first().map { it.name }
        )
    }

    @Test
    fun folderGetByIdRoundTripsTheComposite() = runTest {
        folderDao.upsertAll(listOf(folder(remoteId = "ROOT", name = "Pictures")))

        val stored = folderDao.getById(MediaFolderEntity.buildId(MediaSource.ONEDRIVE, "ROOT"))

        assertEquals("Pictures", stored?.name)
        assertEquals(MediaSource.ONEDRIVE, stored?.source)
    }

    @Test
    fun folderDeleteIndexRowsBySourceRemovesOnlyThatSource() = runTest {
        folderDao.upsertAll(
            listOf(
                folder(source = MediaSource.ONEDRIVE, remoteId = "OD", name = "OneDrive Folder"),
                folder(source = MediaSource.GOOGLE_PHOTOS, remoteId = "GP", name = "Photos Album")
            )
        )

        folderDao.deleteIndexRowsBySource(MediaSource.GOOGLE_PHOTOS)

        assertEquals(
            listOf("OneDrive Folder"),
            folderDao.observeChildren(null).first().map { it.name }
        )
    }

    @Test
    fun aChildIndexedBeforeItsParentIsAccepted() = runTest {
        // There is deliberately no FOREIGN KEY on parentFolderId: cloud listings arrive out of
        // order, and a real constraint would reject this insert.
        itemDao.upsertAll(
            listOf(item(remoteId = "ORPHAN", parentFolderId = "onedrive:NOT_YET_INDEXED"))
        )

        assertEquals(1, itemDao.observeByParent("onedrive:NOT_YET_INDEXED").first().size)
    }

    // ---------- fixtures ----------

    private fun item(
        source: MediaSource = MediaSource.ONEDRIVE,
        remoteId: String = "ABC123",
        name: String = "photo.jpg",
        parentFolderId: String? = null,
        sizeBytes: Long = 1024L,
        localCachePath: String? = null,
        cachedAtUtc: Long? = null,
        lastAccessedUtc: Long? = null
    ) = MediaItemEntity(
        id = MediaItemEntity.buildId(source, remoteId),
        remoteId = remoteId,
        source = source,
        name = name,
        mimeType = "image/jpeg",
        sizeBytes = sizeBytes,
        widthPx = 4032,
        heightPx = 3024,
        createdAtUtc = 1_705_314_600_000L,
        modifiedAtUtc = 1_705_314_600_000L,
        parentFolderId = parentFolderId,
        eTag = "\"{GUID},1\"",
        localCachePath = localCachePath,
        cachedAtUtc = cachedAtUtc,
        lastAccessedUtc = lastAccessedUtc
    )

    private fun folder(
        source: MediaSource = MediaSource.ONEDRIVE,
        remoteId: String = "ROOT",
        name: String = "Pictures",
        parentFolderId: String? = null
    ) = MediaFolderEntity(
        id = MediaFolderEntity.buildId(source, remoteId),
        remoteId = remoteId,
        source = source,
        name = name,
        parentFolderId = parentFolderId,
        childCount = 3,
        modifiedAtUtc = 1_705_314_600_000L,
        path = "/drive/root:/$name"
    )
}
