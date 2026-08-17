package com.gallery.sync.ui.browse

import com.gallery.sync.domain.model.DataResult
import com.gallery.sync.domain.model.FolderPage
import com.gallery.sync.domain.model.RemoteError
import com.gallery.sync.domain.model.RemoteMediaNode
import com.gallery.sync.data.remote.onedrive.UploadSource
import com.gallery.sync.domain.model.UploadedItem
import com.gallery.sync.domain.repository.OneDriveRepository
import com.gallery.sync.domain.repository.OneDriveUploadRepository
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BrowseViewModelTest {

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `lists the root on creation`() = runTest {
        val viewModel = browseViewModel(FakeRepository(root = page(folder("1", "Pictures"))))

        val content = viewModel.state.value as BrowseUiState.Content
        assertEquals("OneDrive", content.location)
        assertEquals(listOf("Pictures"), content.nodes.map { it.name })
        assertFalse(content.canGoBack)
    }

    @Test
    fun `folders sort before files and each group sorts alphabetically`() = runTest {
        val viewModel = browseViewModel(
            FakeRepository(
                root = page(
                    file("f1", "zebra.jpg"),
                    folder("d1", "Winter"),
                    file("f2", "apple.jpg"),
                    folder("d2", "Autumn")
                )
            )
        )

        val content = viewModel.state.value as BrowseUiState.Content
        assertEquals(
            listOf("Autumn", "Winter", "apple.jpg", "zebra.jpg"),
            content.nodes.map { it.name }
        )
    }

    @Test
    fun `opening a folder lists it and updates the location trail`() = runTest {
        val repository = FakeRepository(
            root = page(folder("1", "Pictures")),
            folders = mapOf("1" to page(file("9", "IMG_1.jpg")))
        )
        val viewModel = browseViewModel(repository)

        viewModel.open(folder("1", "Pictures"))

        val content = viewModel.state.value as BrowseUiState.Content
        assertEquals("OneDrive / Pictures", content.location)
        assertEquals(listOf("IMG_1.jpg"), content.nodes.map { it.name })
        assertTrue(content.canGoBack)
    }

    @Test
    fun `back returns to the parent and reports handled`() = runTest {
        val repository = FakeRepository(
            root = page(folder("1", "Pictures")),
            folders = mapOf("1" to page(file("9", "IMG_1.jpg")))
        )
        val viewModel = browseViewModel(repository)
        viewModel.open(folder("1", "Pictures"))

        val handled = viewModel.back()

        assertTrue(handled)
        val content = viewModel.state.value as BrowseUiState.Content
        assertEquals("OneDrive", content.location)
        assertFalse(content.canGoBack)
    }

    @Test
    fun `back at the root is not handled so the system can close the screen`() = runTest {
        val viewModel = browseViewModel(FakeRepository(root = page()))

        assertFalse(viewModel.back())
    }

    @Test
    fun `a failed listing surfaces the error`() = runTest {
        val viewModel = browseViewModel(FakeRepository(rootFailure = RemoteError.Unauthorized))

        val error = viewModel.state.value as BrowseUiState.Error
        assertEquals(RemoteError.Unauthorized, error.error)
    }

    @Test
    fun `sizes render in the largest unit that fits`() {
        assertEquals("512 B", formatBytes(512))
        assertEquals("2 KB", formatBytes(2048))
        assertEquals("3 MB", formatBytes(3L * 1024 * 1024))
        assertEquals("1.5 GB", formatBytes((1.5 * 1024 * 1024 * 1024).toLong()))
    }

    // ---------- helpers ----------

    /**
     * Builds the ViewModel with a no-op upload repository. These tests cover browsing only;
     * uploading is exercised in ChunkedUploaderTest against MockWebServer.
     */
    private fun browseViewModel(repository: OneDriveRepository) =
        BrowseViewModel(repository, NoOpUploadRepository)

    private object NoOpUploadRepository : OneDriveUploadRepository {
        override suspend fun upload(
            localFile: File,
            remoteFolderPath: String,
            onProgress: (Long, Long) -> Unit
        ): DataResult<UploadedItem> = DataResult.Failure(RemoteError.NoToken)

        override suspend fun upload(
            source: UploadSource,
            remoteFolderPath: String,
            onProgress: (Long, Long) -> Unit
        ): DataResult<UploadedItem> = DataResult.Failure(RemoteError.NoToken)
    }

    private fun page(vararg nodes: RemoteMediaNode) = FolderPage(nodes.toList(), nextPageToken = null)

    private fun folder(id: String, name: String) =
        RemoteMediaNode.Folder(id, name, modifiedAtUtc = 0L, childCount = 0, parentPath = null)

    private fun file(id: String, name: String) = RemoteMediaNode.File(
        id = id,
        name = name,
        modifiedAtUtc = 0L,
        mimeType = "image/jpeg",
        sizeBytes = 1024,
        widthPx = null,
        heightPx = null,
        eTag = null
    )

    private class FakeRepository(
        private val root: FolderPage = FolderPage(emptyList(), null),
        private val folders: Map<String, FolderPage> = emptyMap(),
        private val rootFailure: RemoteError? = null
    ) : OneDriveRepository {

        override suspend fun listRoot(): DataResult<FolderPage> =
            rootFailure?.let { DataResult.Failure(it) } ?: DataResult.Success(root)

        override suspend fun listFolder(folderId: String): DataResult<FolderPage> =
            folders[folderId]
                ?.let { DataResult.Success(it) }
                ?: DataResult.Failure(RemoteError.Http(404, null))

        override suspend fun listNextPage(nextPageToken: String): DataResult<FolderPage> =
            DataResult.Success(FolderPage(emptyList(), null))
    }
}
