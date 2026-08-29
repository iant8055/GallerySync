package com.gallery.sync.ui.archive

import android.content.Context
import android.content.IntentSender
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.gallery.sync.data.local.media.LocalCopyRemover
import com.gallery.sync.data.local.settings.BackupSettings
import com.gallery.sync.domain.backup.ArchiveDelay
import com.gallery.sync.domain.backup.ArchiveEntry
import com.gallery.sync.domain.backup.ArchiveFailure
import com.gallery.sync.domain.backup.ArchiveMark
import com.gallery.sync.domain.backup.ArchivePlan
import com.gallery.sync.domain.backup.BackupEngine
import com.gallery.sync.util.Logger
import com.gallery.sync.worker.BackupScheduling
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject

/** Which of the two passes the screen is showing. */
enum class ArchivePhase { IDLE, VALIDATING, READY, REMOVING, DONE }

data class ArchiveUiState(
    val plan: ArchivePlan = ArchivePlan(),
    val phase: ArchivePhase = ArchivePhase.IDLE,
    val isSupported: Boolean = true,
    /** Set when the user chose Delay, so the screen can say what it is waiting for. */
    val delayedUntil: Instant? = null,
    /** How many system dialogs the removal will need, and which one we are on. */
    val batchTotal: Int = 0,
    val batchIndex: Int = 0,
    /**
     * What the last removal achieved, kept apart from [plan].
     *
     * The list is reloaded from the device once a removal finishes, so the rows that were removed
     * are gone from it — they are not on the phone any more and a list of them is a list of things
     * that no longer exist. The outcome still has to be reportable, so it lives here instead of
     * being derived from rows that have been cleared. Ian, 27 Aug 2026.
     */
    val removedCount: Int = 0,
    val removedBytes: Long = 0L,
    /** Albums set to Archive, even ones with nothing left in them. */
    val archiveAlbums: List<String> = emptyList()
) {
    val showPrompt: Boolean get() = phase == ArchivePhase.READY && delayedUntil == null
}

/**
 * Drives the Archive tab: validate, confirm, then remove.
 *
 * ### Two passes, never one
 *
 * Validation writes nothing and removes nothing, so leaving the screen mid-check costs the user
 * exactly nothing. Only [remove] touches the phone, and it acts on [ArchivePlan.confirmed] — the
 * list the user was shown and said yes to.
 */
@HiltViewModel
class ArchiveViewModel @Inject constructor(
    private val engine: BackupEngine,
    private val localCopyRemover: LocalCopyRemover,
    private val settings: BackupSettings,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _state = MutableStateFlow(ArchiveUiState())
    val state: StateFlow<ArchiveUiState> = _state.asStateFlow()

    init {
        _state.value = _state.value.copy(isSupported = localCopyRemover.isSupported())
        load()

        // The snooze is persisted, so it outlives the app being closed — which is the case it now
        // has to cover, since the exit warning fires at exactly the moment someone who chose Delay
        // is walking away. See BackupPreferences.archiveDelayedUntilEpochMillis.
        viewModelScope.launch {
            settings.preferences.collect { prefs ->
                val millis = prefs.archiveDelayedUntilEpochMillis
                _state.value = _state.value.copy(
                    delayedUntil = if (millis > 0L) Instant.ofEpochMilli(millis) else null
                )
            }
        }
    }

    /** Lists what is in Archive albums. Cheap, and safe to call whenever the screen appears. */
    fun load() {
        viewModelScope.launch {
            val files = engine.filesInArchiveAlbums()
            val albums = engine.archiveAlbumNames()
            _state.value = _state.value.copy(
                plan = ArchivePlan(entries = files.map { ArchiveEntry(it) }),
                archiveAlbums = albums,
                phase = ArchivePhase.IDLE,
                batchTotal = 0,
                batchIndex = 0,
                removedCount = 0,
                removedBytes = 0L
            )
        }
    }

    /**
     * Asks OneDrive about every file, uploading the ones it does not have.
     *
     * Ian, 26 Aug 2026: *"If a file can not be found the local copy will be backed up."* So the
     * missing category is work rather than an error — it becomes a backup run, and only a file that
     * verifies afterwards earns its tick. A file that still cannot be confirmed gets a red X and is
     * left on the phone, which is CLAUDE.md's *"if we could not ask, we do not remove"* expressed as
     * membership of a list rather than as a rule someone has to remember.
     */
    fun validate() {
        val entries = _state.value.plan.entries
        if (entries.isEmpty()) return

        viewModelScope.launch {
            _state.value = _state.value.copy(
                phase = ArchivePhase.VALIDATING,
                plan = _state.value.plan.copy(
                    entries = entries.map { it.copy(mark = ArchiveMark.CHECKING, failure = null) },
                    validated = false
                )
            )

            val confirmation = engine.confirmStillInCloud(entries.map { it.item })

            var plan = _state.value.plan
                .withMarks(confirmation.confirmed.mapTo(HashSet()) { it.mediaStoreId }, ArchiveMark.CONFIRMED)
                .withMarks(
                    confirmation.unconfirmed.mapTo(HashSet()) { it.mediaStoreId },
                    ArchiveMark.FAILED,
                    ArchiveFailure.COULD_NOT_CHECK
                )

            val missing = confirmation.missing.mapTo(HashSet()) { it.mediaStoreId }
            if (missing.isNotEmpty()) {
                plan = plan.withMarks(missing, ArchiveMark.BACKING_UP)
                _state.value = _state.value.copy(plan = plan)

                Logger.i(TAG, "validate: ${missing.size} files are not in OneDrive — backing them up")
                plan = backUpAndRecheck(plan, missing)
            }

            _state.value = _state.value.copy(
                plan = plan.copy(validated = true),
                phase = ArchivePhase.READY
            )
            Logger.i(
                TAG,
                "validate: ${plan.confirmed.size} confirmed, ${plan.failed.size} could not be archived"
            )
        }
    }

    /**
     * Runs the ordinary backup chain, then asks OneDrive again about just these files.
     *
     * Reuses `BackupWorker` rather than uploading from here. That worker already chains itself until
     * the backlog clears, persists upload sessions, respects the byte budget and reports its stop
     * reason — none of which would be true of a second upload path written for this screen, and
     * FIX-001 exists because a caller once did exactly that.
     */
    private suspend fun backUpAndRecheck(plan: ArchivePlan, missing: Set<Long>): ArchivePlan {
        val stillMissing = plan.entries.filter { it.item.mediaStoreId in missing }

        // Make the files visible to the uploader before asking it to upload them.
        //
        // `nextPending` selects `state != UPLOADED`, and a file reaches this method precisely
        // because its row already says UPLOADED against a drive that does not have it. Without this
        // the run below selects nothing: measured on the Moto G, 28 Aug 2026, "backup run finished:
        // 0 uploaded, 0 remaining" in 600 ms, and the file then reported as not backed up — which
        // read to the user as the app refusing to try.
        //
        // Wired in, removed, and wired back the same evening. It was taken out because it did not
        // rescue a file whose cloud copy was present but zero bytes — re-uploading files a renamed
        // sibling rather than replacing the bad item, so it bought traffic and no correctness. What
        // brought it back was Ian deleting a backup folder from OneDrive by hand: all eight rows
        // still said UPLOADED, the drive held nothing, and this is the case the method was written
        // for. A wrong-sized remote item is the narrow case and is tracked separately; a file simply
        // gone from the drive is the common one, and re-uploading it is exactly right.
        engine.requeueMissingFromCloud(stillMissing.map { it.item })

        val workManager = WorkManager.getInstance(context)
        BackupScheduling.enqueueManualRun(workManager, settings.current().allowMeteredNetwork)

        // Wait for the chain to finish before re-asking. A recheck against a half-finished upload
        // would mark files as not-backed-up that are seconds from being safe.
        workManager.getWorkInfosForUniqueWorkFlow(BackupScheduling.MANUAL_WORK)
            .first { infos -> infos.isNotEmpty() && infos.all { it.state.isFinished } }

        val recheck = engine.confirmStillInCloud(stillMissing.map { it.item })

        // Absent from the drive and present-but-wrong-size are both refusals, and the file stays on
        // the phone for either. They are labelled apart because only one of them is the user's to
        // act on: a damaged cloud copy is worth knowing about, and "we did not upload it" — which is
        // what the single old message implied — is not what happened.
        val wrongSize = recheck.missing.filter { it.mediaStoreId in recheck.presentAtWrongSize }
        val absent = recheck.missing.filterNot { it.mediaStoreId in recheck.presentAtWrongSize }

        return plan
            .withMarks(recheck.confirmed.mapTo(HashSet()) { it.mediaStoreId }, ArchiveMark.CONFIRMED)
            .withMarks(
                absent.mapTo(HashSet()) { it.mediaStoreId },
                ArchiveMark.FAILED,
                ArchiveFailure.NOT_BACKED_UP
            )
            .withMarks(
                wrongSize.mapTo(HashSet()) { it.mediaStoreId },
                ArchiveMark.FAILED,
                ArchiveFailure.WRONG_SIZE_IN_CLOUD
            )
            .withMarks(
                recheck.unconfirmed.mapTo(HashSet()) { it.mediaStoreId },
                ArchiveMark.FAILED,
                ArchiveFailure.COULD_NOT_CHECK
            )
    }

    /**
     * Builds the next system trash request, marking its files as being removed.
     *
     * Returns null when there is nothing left to ask about, which is how the caller knows the
     * operation is over rather than merely between dialogs.
     */
    suspend fun nextRemovalRequest(): IntentSender? {
        val plan = _state.value.plan
        val batches = localCopyRemover.batch(plan.confirmed)
        val index = _state.value.batchIndex
        if (index >= batches.size) return null

        val batch = batches[index]
        _state.value = _state.value.copy(
            phase = ArchivePhase.REMOVING,
            batchTotal = batches.size,
            plan = plan.withMarks(batch.mapTo(HashSet()) { it.item.mediaStoreId }, ArchiveMark.REMOVING)
        )

        return localCopyRemover.createMoveToBackupRequest(batch.map { it.item.contentUri })
    }

    /**
     * Called once Android's dialog closes.
     *
     * The outcome is read from a rescan rather than from the dialog's result code, because the user
     * may have allowed some and not others, and the files themselves are the only honest record of
     * what happened.
     */
    fun onRemovalDialogClosed() {
        viewModelScope.launch {
            val stillHere = engine.filesInArchiveAlbums().mapTo(HashSet()) { it.mediaStoreId }
            val plan = _state.value.plan
            val settled = plan.copy(
                entries = plan.entries.map {
                    when {
                        it.mark != ArchiveMark.REMOVING -> it
                        it.item.mediaStoreId in stillHere -> it.copy(mark = ArchiveMark.CONFIRMED)
                        else -> it.copy(mark = ArchiveMark.REMOVED)
                    }
                }
            )

            val next = _state.value.batchIndex + 1
            val more = next < localCopyRemover.batch(settled.confirmed).size

            if (more) {
                _state.value = _state.value.copy(
                    plan = settled,
                    batchIndex = next,
                    phase = ArchivePhase.REMOVING
                )
                return@launch
            }

            // Finished. Re-read the album from the device rather than keeping the settled plan:
            // every row that was removed describes a file that is no longer there, and a list of
            // those is a list of things that do not exist. What survives is the count and the
            // bytes, which is what the screen actually needs to report.
            val removed = settled.removed

            // Tell the ledger the files have gone, before re-reading anything.
            //
            // `refreshLedger` is the only thing that stamps `localMissingSinceEpochMillis`, and the
            // Restore tab's download half selects on exactly that column — so until it runs, the app
            // still believes every archived file is on the phone and the one screen that could fetch
            // them back cannot see them. It ran at the start of a backup run and nowhere near this
            // path.
            //
            // Found by Ian immediately after the first successful archive on the Moto G, 28 Aug 2026:
            // eight files trashed, and a Restore tab offering none of them. The rows were intact and
            // correct in every other respect — state UPLOADED, remote id, matching size — and simply
            // had nobody to tell them the local copy was gone.
            //
            // A full rescan rather than marking the removed ids directly: this is the same diff the
            // engine already does, it costs about half a second against 3,335 files, and a second
            // implementation of "what is no longer on this phone" is exactly the kind of thing that
            // drifts from the first.
            engine.refreshLedger()

            val remaining = engine.filesInArchiveAlbums()
            _state.value = _state.value.copy(
                plan = ArchivePlan(entries = remaining.map { ArchiveEntry(it) }),
                batchIndex = 0,
                batchTotal = 0,
                phase = ArchivePhase.DONE,
                removedCount = removed.size,
                removedBytes = removed.sumOf { it.sizeBytes }
            )
            Logger.i(TAG, "archive: ${removed.size} files removed from this phone")
        }
    }

    /** The user said no. Nothing is remembered — the album is still Archive, so it will offer again. */
    fun dismiss() {
        _state.value = _state.value.copy(phase = ArchivePhase.IDLE)
    }

    /** The user asked to be left alone for a while. Their choice, not the app deciding to re-ask. */
    fun delay(delay: ArchiveDelay) {
        val until = Instant.now().plusSeconds(delay.hours * 3600)
        _state.value = _state.value.copy(delayedUntil = until)
        viewModelScope.launch { settings.setArchiveDelayedUntil(until.toEpochMilli()) }
    }

    private companion object {
        const val TAG = "ArchiveViewModel"
    }
}
