package com.gallery.sync.ui.deleted

import com.gallery.sync.data.local.settings.BackupPreferences
import com.gallery.sync.data.local.settings.BackupSettings
import com.gallery.sync.domain.backup.BackupEngine
import com.gallery.sync.domain.backup.CloudDeletionPolicy
import com.gallery.sync.domain.backup.DeletedFile
import com.gallery.sync.domain.backup.DeletedFilesOffer
import com.gallery.sync.domain.backup.DeletionOutcome
import com.gallery.sync.domain.backup.DepartureOrigin
import com.gallery.sync.domain.backup.SyncDeletionsToCloud
import com.gallery.sync.domain.backup.TrashBackupOutcome
import kotlinx.coroutines.CompletableDeferred
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
import org.mockito.kotlin.doSuspendableAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * The window that opens with the app when files have left the phone. Ian, 19 Sept 2026.
 *
 * It is two windows, one after the other, each shown only if it has files: the files with a copy in
 * OneDrive (keep it or delete it), then the files with none (leave them in the trash or back them up).
 *
 * What these pin down is his rules: it shows only when something is new; every undecided file stays
 * in it until decided; nothing starts ticked; a OneDrive copy is removed only when its file is ticked
 * **and** the confirmation is accepted; leaving any other way removes nothing; and an unticked file
 * gets the passive choice.
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

    private fun file(name: String, departedAt: Long, sent: Boolean = true, size: Long = 1_000L) = DeletedFile(
        id = "Camera/$name|$size|1",
        origin = if (sent) DepartureOrigin.SENT else DepartureOrigin.NEVER_SENT,
        displayName = name,
        album = "Camera",
        sizeBytes = size,
        mediaStoreId = 1L,
        contentUri = "content://media/external/images/media/1",
        mimeType = "image/jpeg",
        isVideo = false,
        dateModifiedEpochSeconds = 1L,
        departedAtEpochMillis = departedAt,
        remoteItemId = if (sent) "remote-$name" else null
    )

    private suspend fun viewModel(
        policy: CloudDeletionPolicy = CloudDeletionPolicy.ASK,
        seenUpTo: Long = 0L,
        inCloud: List<DeletedFile> = emptyList(),
        notInCloud: List<DeletedFile> = emptyList(),
        complete: Boolean = true
    ): DeletedFilesViewModel {
        whenever(settings.current()).thenReturn(
            BackupPreferences(cloudDeletionPolicy = policy, deletionPromptSeenUpToEpochMillis = seenUpTo)
        )
        whenever(deletions.offer(any(), any())).thenReturn(DeletedFilesOffer(inCloud, notInCloud, complete))
        whenever(deletions.delete(any())).thenReturn(DeletionOutcome(deleted = 1))
        whenever(engine.backUpFromTrash(any(), any())).thenReturn(TrashBackupOutcome(uploaded = 1))
        return DeletedFilesViewModel(engine, deletions, settings)
    }

    private val a = file("a.jpg", departedAt = 100L)
    private val b = file("b.jpg", departedAt = 200L)
    private val c = file("c.jpg", departedAt = 300L)

    private val x = file("x.jpg", departedAt = 150L, sent = false)
    private val y = file("y.jpg", departedAt = 250L, sent = false)

    // ── Holding the app back until it is known ──────────────────────────────

    @Test
    fun `the app is held back until the first look has finished`() = runTest {
        val vm = viewModel()

        assertTrue("before the first look, the app must not be drawn", vm.state.value.checking)

        vm.evaluate(now = 1_000_000L)

        assertFalse(vm.state.value.checking)
    }

    @Test
    fun `the app is let through when the setting is Leave, without looking at anything`() = runTest {
        val vm = viewModel(policy = CloudDeletionPolicy.LEAVE)

        vm.evaluate(now = 1_000_000L)

        assertFalse(vm.state.value.checking)
        assertEquals(DeletedFilesPhase.HIDDEN, vm.state.value.phase)
    }

    @Test
    fun `skipping while OneDrive is being asked lets the app through and decides nothing`() = runTest {
        val vm = viewModel(inCloud = listOf(a))
        val gate = CompletableDeferred<Unit>()
        whenever(deletions.offer(any(), any())).doSuspendableAnswer { call ->
            @Suppress("UNCHECKED_CAST")
            (call.arguments[1] as () -> Unit)()
            gate.await()
            DeletedFilesOffer(listOf(a))
        }

        vm.evaluate(now = 1_000_000L)
        assertTrue(vm.state.value.checking)
        assertTrue("it says it is waiting for OneDrive", vm.state.value.askingOneDrive)

        vm.skipCheck()

        assertFalse(vm.state.value.checking)
        assertFalse(vm.state.value.askingOneDrive)
        assertEquals(DeletedFilesPhase.HIDDEN, vm.state.value.phase)
        verify(settings, never()).setDeletionPromptSeenUpTo(any())
        verify(deletions, never()).keep(any())
        verify(deletions, never()).delete(any())
    }

    // ── When it shows ───────────────────────────────────────────────────────

    @Test
    fun `under Leave nothing shows and nothing is even scanned for`() = runTest {
        val vm = viewModel(policy = CloudDeletionPolicy.LEAVE, inCloud = listOf(a), notInCloud = listOf(x))

        vm.evaluate(now = 1_000_000L)

        assertEquals(DeletedFilesPhase.HIDDEN, vm.state.value.phase)
        verify(engine, never()).refreshLedger()
        verify(deletions, never()).offer(any(), any())
    }

    @Test
    fun `nothing shows when no file has left the phone`() = runTest {
        val vm = viewModel()

        vm.evaluate(now = 1_000_000L)

        assertEquals(DeletedFilesPhase.HIDDEN, vm.state.value.phase)
    }

    @Test
    fun `the first window is the files with a copy in OneDrive, with nothing ticked to start with`() = runTest {
        val vm = viewModel(inCloud = listOf(a, b), notInCloud = listOf(x))

        vm.evaluate(now = 1_000_000L)

        assertEquals(DeletedFilesPhase.LISTING, vm.state.value.phase)
        assertEquals(DeletedFilesStep.IN_CLOUD, vm.state.value.step)
        assertEquals(listOf(a, b), vm.state.value.files)
        assertTrue("Ian: default selections are off", vm.state.value.selected.isEmpty())
    }

    @Test
    fun `with no copy anywhere it opens on the second window`() = runTest {
        val vm = viewModel(notInCloud = listOf(x, y))

        vm.evaluate(now = 1_000_000L)

        assertEquals(DeletedFilesStep.NOT_IN_CLOUD, vm.state.value.step)
        assertEquals(listOf(x, y), vm.state.value.files)
        assertTrue(vm.state.value.selected.isEmpty())
    }

    @Test
    fun `it does not show again for files it was already shown for`() = runTest {
        val vm = viewModel(seenUpTo = 200L, inCloud = listOf(a, b))

        vm.evaluate(now = 1_000_000L)

        assertEquals("nothing is newer than what it last showed", DeletedFilesPhase.HIDDEN, vm.state.value.phase)
    }

    @Test
    fun `a newer file brings it back, and the older undecided files are on the list too`() = runTest {
        val vm = viewModel(seenUpTo = 200L, inCloud = listOf(a, b, c))

        vm.evaluate(now = 1_000_000L)

        assertEquals(DeletedFilesPhase.LISTING, vm.state.value.phase)
        assertEquals("every undecided file stays until decided", listOf(a, b, c), vm.state.value.files)
    }

    @Test
    fun `the moment it appears it records the newest file of either window as shown`() = runTest {
        val vm = viewModel(inCloud = listOf(a, b), notInCloud = listOf(x, y))

        vm.evaluate(now = 1_000_000L)

        verify(settings).setDeletionPromptSeenUpTo(250L)
    }

    @Test
    fun `it is not recorded as shown when OneDrive could not be asked about some files`() = runTest {
        val vm = viewModel(inCloud = listOf(a), complete = false)

        vm.evaluate(now = 1_000_000L)

        assertEquals(DeletedFilesPhase.LISTING, vm.state.value.phase)
        verify(settings, never()).setDeletionPromptSeenUpTo(any())
    }

    @Test
    fun `it looks at most once a minute`() = runTest {
        val vm = viewModel(inCloud = listOf(a))

        vm.evaluate(now = 1_000_000L)
        vm.decideLater()
        vm.evaluate(now = 1_000_000L + 30_000L)

        assertEquals("within a minute: no second look", DeletedFilesPhase.HIDDEN, vm.state.value.phase)
        verify(deletions, times(1)).offer(any(), any())

        vm.evaluate(now = 1_000_000L + 120_000L)
        assertEquals("a minute later it looks again", DeletedFilesPhase.LISTING, vm.state.value.phase)
        verify(deletions, times(2)).offer(any(), any())
    }

    @Test
    fun `a window that is up is never disturbed by another look`() = runTest {
        val vm = viewModel(inCloud = listOf(a))
        vm.evaluate(now = 1_000_000L)
        vm.toggle(a.id)
        whenever(deletions.offer(any(), any())).thenReturn(DeletedFilesOffer(listOf(a, b), emptyList()))

        vm.evaluate(now = 1_000_000L + 400_000L)

        assertEquals(DeletedFilesPhase.LISTING, vm.state.value.phase)
        assertEquals("the list is not swapped from under the user", listOf(a), vm.state.value.files)
        assertEquals("and their tick is still there", setOf(a.id), vm.state.value.selected)
        verify(deletions, times(1)).offer(any(), any())
    }

    // ── Choosing ────────────────────────────────────────────────────────────

    @Test
    fun `tapping ticks and unticks, select all and clear work`() = runTest {
        val vm = viewModel(inCloud = listOf(a, b, c))
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
    fun `deciding later on the only window closes it and decides nothing`() = runTest {
        val vm = viewModel(inCloud = listOf(a, b))
        vm.evaluate(now = 1_000_000L)
        vm.toggle(a.id)

        vm.decideLater()

        assertEquals(DeletedFilesPhase.HIDDEN, vm.state.value.phase)
        verify(deletions, never()).delete(any())
        verify(deletions, never()).keep(any())
        verify(engine, never()).backUpFromTrash(any(), any())
    }

    @Test
    fun `deciding later on the first window goes on to the second with the ticks cleared`() = runTest {
        val vm = viewModel(inCloud = listOf(a), notInCloud = listOf(x))
        vm.evaluate(now = 1_000_000L)
        vm.toggle(a.id)

        vm.decideLater()

        assertEquals(DeletedFilesPhase.LISTING, vm.state.value.phase)
        assertEquals(DeletedFilesStep.NOT_IN_CLOUD, vm.state.value.step)
        assertTrue("a tick on one window is not a tick on the next", vm.state.value.selected.isEmpty())
        verify(deletions, never()).delete(any())
        verify(deletions, never()).keep(any())

        vm.decideLater()
        assertEquals("nothing was decided, so it just goes away", DeletedFilesPhase.HIDDEN, vm.state.value.phase)
    }

    @Test
    fun `keep all leaves every copy alone whatever is ticked, then offers the second window`() = runTest {
        val vm = viewModel(inCloud = listOf(a, b), notInCloud = listOf(x))
        vm.evaluate(now = 1_000_000L)
        vm.toggle(a.id)

        vm.keepAll()

        verify(deletions).keep(listOf(a, b))
        verify(deletions, never()).delete(any())
        assertEquals(DeletedFilesStep.NOT_IN_CLOUD, vm.state.value.step)
        assertEquals(DeletedFilesPhase.LISTING, vm.state.value.phase)

        vm.keepAll()

        verify(deletions).keep(listOf(x))
        verify(engine, never()).backUpFromTrash(any(), any())
        assertEquals(DeletedFilesPhase.DONE, vm.state.value.phase)
        assertEquals(2, vm.state.value.cloudCopiesKept)
        assertEquals(1, vm.state.value.leftInTrash)
    }

    // ── Removing OneDrive copies (first window) ─────────────────────────────

    @Test
    fun `nothing can be removed without ticking something and confirming`() = runTest {
        val vm = viewModel(inCloud = listOf(a, b))
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
        val vm = viewModel(inCloud = listOf(a, b))
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
    fun `confirming removes only the ticked copies and keeps the unticked ones for good`() = runTest {
        val vm = viewModel(inCloud = listOf(a, b, c))
        vm.evaluate(now = 1_000_000L)
        vm.toggle(b.id)
        vm.askToRemove()

        vm.confirmRemoval()

        verify(deletions).delete(listOf(b))
        verify(deletions).keep(listOf(a, c))
        assertEquals(DeletedFilesPhase.DONE, vm.state.value.phase)
        assertEquals(2, vm.state.value.cloudCopiesKept)
        assertEquals(1, vm.state.value.removal?.deleted)
    }

    @Test
    fun `after a removal the second window still follows`() = runTest {
        val vm = viewModel(inCloud = listOf(a), notInCloud = listOf(x))
        vm.evaluate(now = 1_000_000L)
        vm.toggle(a.id)
        vm.askToRemove()

        vm.confirmRemoval()

        assertEquals(DeletedFilesPhase.LISTING, vm.state.value.phase)
        assertEquals(DeletedFilesStep.NOT_IN_CLOUD, vm.state.value.step)
        assertEquals("what the first window did is kept for the report", 1, vm.state.value.removal?.deleted)
    }

    @Test
    fun `a copy that could not be removed is not marked as kept`() = runTest {
        val vm = viewModel(inCloud = listOf(a, b))
        whenever(deletions.delete(any())).thenReturn(DeletionOutcome(deleted = 0, failed = 1))
        vm.evaluate(now = 1_000_000L)
        vm.toggle(a.id)
        vm.askToRemove()

        vm.confirmRemoval()

        // Only the unticked file is decided. The one that failed stays undecided, so it stays listed.
        verify(deletions).keep(listOf(b))
        assertEquals(1, vm.state.value.removal?.failed)
    }

    // ── Backing up from the trash (second window) ───────────────────────────

    @Test
    fun `nothing is backed up until something is ticked`() = runTest {
        val vm = viewModel(notInCloud = listOf(x, y))
        vm.evaluate(now = 1_000_000L)

        vm.backUpSelected()

        verify(engine, never()).backUpFromTrash(any(), any())
        assertEquals(DeletedFilesPhase.LISTING, vm.state.value.phase)
    }

    @Test
    fun `backing up sends only the ticked files and leaves the unticked in the trash`() = runTest {
        val vm = viewModel(notInCloud = listOf(x, y))
        vm.evaluate(now = 1_000_000L)
        vm.toggle(y.id)

        vm.backUpSelected()

        verify(engine).backUpFromTrash(org.mockito.kotlin.eq(listOf(y)), any())
        verify(deletions).keep(listOf(x))
        assertEquals(DeletedFilesPhase.DONE, vm.state.value.phase)
        assertEquals(1, vm.state.value.backup?.uploaded)
        assertEquals(1, vm.state.value.leftInTrash)
        verify(deletions, never()).delete(any())
    }

    @Test
    fun `a file that could not be sent is not settled, so it is offered again`() = runTest {
        val vm = viewModel(notInCloud = listOf(x, y))
        whenever(engine.backUpFromTrash(any(), any())).thenReturn(TrashBackupOutcome(failed = 1))
        vm.evaluate(now = 1_000_000L)
        vm.toggle(x.id)

        vm.backUpSelected()

        // Only the unticked file is decided. The engine leaves the failed one's record in place.
        verify(deletions).keep(listOf(y))
        assertEquals(1, vm.state.value.backup?.failed)
    }

    @Test
    fun `progress is reported while a backup runs`() = runTest {
        val vm = viewModel(notInCloud = listOf(x, y))
        var seen = -1
        whenever(engine.backUpFromTrash(any(), any())).thenAnswer { call ->
            @Suppress("UNCHECKED_CAST")
            val report = call.arguments[1] as (Int, Int, String) -> Unit
            report(1, 2, "x.jpg")
            seen = vm.state.value.progressDone
            TrashBackupOutcome(uploaded = 2)
        }
        vm.evaluate(now = 1_000_000L)
        vm.selectAll()

        vm.backUpSelected()

        assertEquals(1, seen)
    }

    @Test
    fun `the windows only do their own job`() = runTest {
        val vm = viewModel(inCloud = listOf(a), notInCloud = listOf(x))
        vm.evaluate(now = 1_000_000L)
        vm.toggle(a.id)

        vm.backUpSelected()
        verify(engine, never()).backUpFromTrash(any(), any())

        vm.decideLater()
        vm.toggle(x.id)
        vm.askToRemove()
        assertFalse("nothing is ever deleted from OneDrive from the second window", vm.state.value.confirming)
        vm.confirmRemoval()
        verify(deletions, never()).delete(any())
    }

    @Test
    fun `leaving the second window after acting on the first reports instead of vanishing`() = runTest {
        val vm = viewModel(inCloud = listOf(a), notInCloud = listOf(x))
        vm.evaluate(now = 1_000_000L)
        vm.keepAll()

        vm.decideLater()

        assertEquals(DeletedFilesPhase.DONE, vm.state.value.phase)
    }

    @Test
    fun `finishing puts the window away`() = runTest {
        val vm = viewModel(inCloud = listOf(a))
        vm.evaluate(now = 1_000_000L)
        vm.keepAll()

        vm.finish()

        assertEquals(DeletedFilesPhase.HIDDEN, vm.state.value.phase)
    }
}
