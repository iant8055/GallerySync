package com.gallery.sync.ui.deleted

import com.gallery.sync.data.local.entity.BackupEntryEntity
import com.gallery.sync.data.local.entity.BackupState
import com.gallery.sync.data.local.settings.BackupPreferences
import com.gallery.sync.data.local.settings.BackupSettings
import com.gallery.sync.domain.backup.BackupEngine
import com.gallery.sync.domain.backup.CloudDeletionPolicy
import com.gallery.sync.domain.backup.DeletionOutcome
import com.gallery.sync.domain.backup.SyncDeletionsToCloud
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
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * The window that opens with the app when files have left the phone. Ian, 19 Sept 2026.
 *
 * What these pin down is his rules: it shows only when something is new; every undecided file stays
 * in it until decided; nothing starts ticked; a file is removed only when it is ticked **and** the
 * confirmation is accepted; leaving any other way removes nothing; and an unticked file is left alone.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DeletedFilesViewModelTest {

    private val engine: BackupEngine = mock()
    private val deletions: SyncDeletionsToCloud = mock()
    private val settings: BackupSettings = mock()

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun file(name: String, missingSince: Long, size: Long = 1_000L) = BackupEntryEntity(
        id = "Camera/$name|$size|1",
        mediaStoreId = 1L,
        contentUri = "content://media/external/images/media/1",
        displayName = name,
        album = "Camera",
        sizeBytes = size,
        dateModifiedEpochSeconds = 1L,
        mimeType = "image/jpeg",
        isVideo = false,
        state = BackupState.UPLOADED,
        remoteItemId = "remote-$name",
        remoteSizeBytes = size,
        localMissingSinceEpochMillis = missingSince
    )

    private suspend fun viewModel(
        policy: CloudDeletionPolicy = CloudDeletionPolicy.ASK,
        seenUpTo: Long = 0L,
        offered: List<BackupEntryEntity> = emptyList()
    ): DeletedFilesViewModel {
        whenever(settings.current()).thenReturn(
            BackupPreferences(cloudDeletionPolicy = policy, deletionPromptSeenUpToEpochMillis = seenUpTo)
        )
        whenever(deletions.candidates()).thenReturn(offered)
        whenever(deletions.delete(any())).thenReturn(DeletionOutcome(deleted = 1))
        return DeletedFilesViewModel(engine, deletions, settings)
    }

    private val a = file("a.jpg", missingSince = 100L)
    private val b = file("b.jpg", missingSince = 200L)
    private val c = file("c.jpg", missingSince = 300L)

    // ── When it shows ───────────────────────────────────────────────────────

    @Test
    fun `under Leave nothing shows and nothing is even scanned for`() = runTest {
        val vm = viewModel(policy = CloudDeletionPolicy.LEAVE, offered = listOf(a))

        vm.evaluate(now = 1_000_000L)

        assertEquals(DeletedFilesPhase.HIDDEN, vm.state.value.phase)
        verify(engine, never()).refreshLedger()
        verify(deletions, never()).candidates()
    }

    @Test
    fun `nothing shows when no file has left the phone`() = runTest {
        val vm = viewModel(offered = emptyList())

        vm.evaluate(now = 1_000_000L)

        assertEquals(DeletedFilesPhase.HIDDEN, vm.state.value.phase)
    }

    @Test
    fun `it lists the files with nothing ticked to start with`() = runTest {
        val vm = viewModel(offered = listOf(a, b))

        vm.evaluate(now = 1_000_000L)

        assertEquals(DeletedFilesPhase.LISTING, vm.state.value.phase)
        assertEquals(listOf(a, b), vm.state.value.files)
        assertTrue("Ian: default selections are off", vm.state.value.selected.isEmpty())
    }

    @Test
    fun `it does not show again for files it was already shown for`() = runTest {
        val vm = viewModel(seenUpTo = 200L, offered = listOf(a, b))

        vm.evaluate(now = 1_000_000L)

        assertEquals("nothing is newer than what it last showed", DeletedFilesPhase.HIDDEN, vm.state.value.phase)
    }

    @Test
    fun `a newer file brings it back, and the older undecided files are on the list too`() = runTest {
        val vm = viewModel(seenUpTo = 200L, offered = listOf(a, b, c))

        vm.evaluate(now = 1_000_000L)

        assertEquals(DeletedFilesPhase.LISTING, vm.state.value.phase)
        assertEquals("every undecided file stays until decided", listOf(a, b, c), vm.state.value.files)
    }

    @Test
    fun `the moment it appears it records that it has been shown`() = runTest {
        val vm = viewModel(offered = listOf(a, c))

        vm.evaluate(now = 1_000_000L)

        verify(settings).setDeletionPromptSeenUpTo(300L)
    }

    @Test
    fun `it looks at most once a minute`() = runTest {
        val vm = viewModel(offered = listOf(a))

        vm.evaluate(now = 1_000_000L)
        vm.decideLater()
        vm.evaluate(now = 1_000_000L + 30_000L)

        assertEquals("within a minute: no second look", DeletedFilesPhase.HIDDEN, vm.state.value.phase)
        verify(deletions, times(1)).candidates()

        vm.evaluate(now = 1_000_000L + 120_000L)
        assertEquals("a minute later it looks again", DeletedFilesPhase.LISTING, vm.state.value.phase)
        verify(deletions, times(2)).candidates()
    }

    @Test
    fun `a window that is up is never disturbed by another look`() = runTest {
        val vm = viewModel(offered = listOf(a))
        vm.evaluate(now = 1_000_000L)
        vm.toggle(a.id)
        whenever(deletions.candidates()).thenReturn(listOf(a, b))

        vm.evaluate(now = 1_000_000L + 400_000L)

        assertEquals(DeletedFilesPhase.LISTING, vm.state.value.phase)
        assertEquals("the list is not swapped from under the user", listOf(a), vm.state.value.files)
        assertEquals("and their tick is still there", setOf(a.id), vm.state.value.selected)
        verify(deletions, times(1)).candidates()
    }

    // ── Choosing ────────────────────────────────────────────────────────────

    @Test
    fun `tapping ticks and unticks, select all and clear work`() = runTest {
        val vm = viewModel(offered = listOf(a, b, c))
        vm.evaluate(now = 1_000_000L)

        vm.toggle(a.id)
        assertEquals(setOf(a.id), vm.state.value.selected)
        vm.toggle(a.id)
        assertTrue(vm.state.value.selected.isEmpty())

        vm.selectAll()
        assertEquals(setOf(a.id, b.id, c.id), vm.state.value.selected)
        vm.clearSelection()
        assertTrue(vm.state.value.selected.isEmpty())
    }

    @Test
    fun `deciding later closes it and decides nothing`() = runTest {
        val vm = viewModel(offered = listOf(a, b))
        vm.evaluate(now = 1_000_000L)
        vm.toggle(a.id)

        vm.decideLater()

        assertEquals(DeletedFilesPhase.HIDDEN, vm.state.value.phase)
        verify(deletions, never()).delete(any())
        verify(deletions, never()).keep(any())
    }

    @Test
    fun `keep all leaves every copy alone whatever is ticked`() = runTest {
        val vm = viewModel(offered = listOf(a, b))
        vm.evaluate(now = 1_000_000L)
        vm.toggle(a.id)

        vm.keepAll()

        verify(deletions).keep(listOf(a, b))
        verify(deletions, never()).delete(any())
        assertEquals(DeletedFilesPhase.DONE, vm.state.value.phase)
        assertEquals(2, vm.state.value.keptCount)
    }

    // ── Removing ────────────────────────────────────────────────────────────

    @Test
    fun `nothing can be removed without ticking something and confirming`() = runTest {
        val vm = viewModel(offered = listOf(a, b))
        vm.evaluate(now = 1_000_000L)

        vm.askToRemove()
        assertFalse("nothing ticked, so there is nothing to confirm", vm.state.value.confirming)

        vm.toggle(a.id)
        vm.confirmRemoval()
        verify(deletions, never()).delete(any())

        vm.askToRemove()
        assertTrue(vm.state.value.confirming)
        verify(deletions, never()).delete(any())
    }

    @Test
    fun `cancelling the confirmation removes nothing and keeps the ticks`() = runTest {
        val vm = viewModel(offered = listOf(a, b))
        vm.evaluate(now = 1_000_000L)
        vm.toggle(a.id)
        vm.askToRemove()

        vm.dismissConfirmation()

        assertFalse(vm.state.value.confirming)
        assertEquals(setOf(a.id), vm.state.value.selected)
        assertEquals(DeletedFilesPhase.LISTING, vm.state.value.phase)
        verify(deletions, never()).delete(any())
        verify(deletions, never()).keep(any())
    }

    @Test
    fun `confirming removes only the ticked files and leaves the unticked ones alone for good`() = runTest {
        val vm = viewModel(offered = listOf(a, b, c))
        vm.evaluate(now = 1_000_000L)
        vm.toggle(b.id)
        vm.askToRemove()

        vm.confirmRemoval()

        verify(deletions).delete(listOf(b))
        verify(deletions).keep(listOf(a, c))
        assertEquals(DeletedFilesPhase.DONE, vm.state.value.phase)
        assertEquals(2, vm.state.value.keptCount)
        assertEquals(1, vm.state.value.outcome?.deleted)
    }

    @Test
    fun `a file that could not be removed is not marked as kept`() = runTest {
        val vm = viewModel(offered = listOf(a, b))
        whenever(deletions.delete(any())).thenReturn(DeletionOutcome(deleted = 0, failed = 1))
        vm.evaluate(now = 1_000_000L)
        vm.toggle(a.id)
        vm.askToRemove()

        vm.confirmRemoval()

        // Only the unticked file is decided. The one that failed stays undecided, so it stays listed.
        verify(deletions).keep(listOf(b))
        assertEquals(1, vm.state.value.outcome?.failed)
    }

    @Test
    fun `finishing puts the window away`() = runTest {
        val vm = viewModel(offered = listOf(a))
        vm.evaluate(now = 1_000_000L)
        vm.keepAll()

        vm.finish()

        assertEquals(DeletedFilesPhase.HIDDEN, vm.state.value.phase)
    }
}
