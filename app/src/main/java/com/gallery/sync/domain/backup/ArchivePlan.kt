package com.gallery.sync.domain.backup

import com.gallery.sync.data.local.media.LocalMediaItem

/**
 * Where one file has got to in an archive run.
 *
 * Two passes over the same list, and the marks say which pass is running. Validating and removing
 * look alike on screen and mean opposite things, so the state a row is in has to be unambiguous.
 */
enum class ArchiveMark {
    /** Listed, not yet examined. */
    WAITING,

    /** Being checked against OneDrive right now. */
    CHECKING,

    /** Not in OneDrive, so it is being uploaded — Ian's rule: a missing file is work, not an error. */
    BACKING_UP,

    /** The full version is confirmed in OneDrive. Only these may be removed. */
    CONFIRMED,

    /** Could not be confirmed. Stays on the phone. */
    FAILED,

    /** Being moved to the trash. */
    REMOVING,

    /** Gone from the phone. */
    REMOVED
}

/**
 * Why a file did not earn its tick.
 *
 * Two reasons, kept apart because they ask different things of the user — the same distinction
 * `CloudConfirmation` draws between *missing* and *unconfirmed*, and for the same reason: "OneDrive
 * does not have this" and "we could not ask" are not the same fact.
 */
enum class ArchiveFailure {
    /** The listing failed. We could not ask, so we do not remove. */
    COULD_NOT_CHECK,

    /** We asked, it was not there, and the upload that would have fixed that did not succeed. */
    NOT_BACKED_UP,

    /**
     * It **is** on the drive, under this name, at a size that is not this file's.
     *
     * Split out 28 Aug 2026. A zero-byte item on the drive was being reported as "Not in OneDrive",
     * which is both untrue and unactionable — the user cannot tell a file the app declined to
     * upload from one whose cloud copy is damaged, and only the second is worth their attention.
     * The file stays on the phone either way: a wrong-sized copy is not a backup.
     */
    WRONG_SIZE_IN_CLOUD
}

/** One row on the Archive screen. */
data class ArchiveEntry(
    val item: LocalMediaItem,
    val mark: ArchiveMark = ArchiveMark.WAITING,
    val failure: ArchiveFailure? = null
) {
    val name: String get() = item.displayName
    val album: String get() = item.album
    val sizeBytes: Long get() = item.sizeBytes
}

/**
 * Everything the Archive screen is working on.
 *
 * ### The guarantee is a property of [confirmed], not of a check somewhere
 *
 * CLAUDE.md requires that a file which could not be confirmed is never removed. That holds here
 * because [confirmed] is the only list the removal acts on, and nothing but a live confirmation puts
 * a row into it. A red row is not excluded by a guard that has to be remembered — it is simply not
 * in the set. Ian, 26 Aug 2026, choosing the red X over a blocking error is what makes that possible.
 */
data class ArchivePlan(
    val entries: List<ArchiveEntry> = emptyList(),
    /** True once every row has reached a settled mark, so the prompt may be shown. */
    val validated: Boolean = false
) {
    val albums: List<String> get() = entries.map { it.album }.distinct().sorted()

    val confirmed: List<ArchiveEntry> get() = entries.filter { it.mark == ArchiveMark.CONFIRMED }
    val failed: List<ArchiveEntry> get() = entries.filter { it.mark == ArchiveMark.FAILED }
    val removed: List<ArchiveEntry> get() = entries.filter { it.mark == ArchiveMark.REMOVED }

    /** What removing the confirmed set would actually free. Never the whole album's size. */
    val freeableBytes: Long get() = confirmed.sumOf { it.sizeBytes }

    val isEmpty: Boolean get() = entries.isEmpty()

    /**
     * Nothing survived validation, so there is nothing to offer.
     *
     * The screen must say this rather than show a button that archives an empty set — TASK-014's
     * rule: never offer an action that cannot succeed.
     */
    val allFailed: Boolean get() = entries.isNotEmpty() && confirmed.isEmpty()

    /** Some green, some red. The prompt has to describe only the green half. */
    val isPartial: Boolean get() = confirmed.isNotEmpty() && failed.isNotEmpty()

    fun withMark(id: Long, mark: ArchiveMark, failure: ArchiveFailure? = null): ArchivePlan =
        copy(
            entries = entries.map {
                if (it.item.mediaStoreId == id) it.copy(mark = mark, failure = failure) else it
            }
        )

    fun withMarks(ids: Set<Long>, mark: ArchiveMark, failure: ArchiveFailure? = null): ArchivePlan =
        copy(
            entries = entries.map {
                if (it.item.mediaStoreId in ids) it.copy(mark = mark, failure = failure) else it
            }
        )
}

/**
 * How long the user asked to be left alone.
 *
 * Not repeat prompting in the sense CLAUDE.md forbids — the rule is about the app asking again on
 * its own. This is the user saying "ask me later", which is their choice to make.
 */
enum class ArchiveDelay(val hours: Long) {
    ONE_HOUR(1),
    TWELVE_HOURS(12),
    ONE_DAY(24)
}

/**
 * What is in the Archive albums right now: the files that may be archived, and the ones the user
 * has opted out.
 *
 * Only [ArchiveFiles.toArchive] is ever offered for removal. The opted-out half exists so the
 * Archive tab can show those files greyed out and let the user put one back, which is the whole
 * of what it is used for. See [FilePin].
 */
data class ArchiveFiles(
    val toArchive: List<LocalMediaItem> = emptyList(),
    val optedOut: List<LocalMediaItem> = emptyList()
)

/**
 * A plan matched to the phone.
 *
 * @property needsRecheck the check that was on screen no longer describes the list.
 * @property setAside files this check confirmed that the user has since swiped out, by MediaStore id,
 * kept so the confirmation can come back with the file. Empty once the check is withdrawn.
 */
data class ReconciledPlan(
    val plan: ArchivePlan,
    val needsRecheck: Boolean,
    val setAside: Map<Long, ArchiveEntry> = emptyMap()
)

/**
 * This plan matched to what the phone holds now, for when the user swipes a file out of Archive or
 * back in, or opens the tab after a file has arrived. Ian, 19 Sept 2026.
 *
 * - A file that is no longer archivable (opted out, or gone from the phone) leaves the plan.
 * - A file that is now archivable and was not in the plan (put back, or newly arrived) joins it
 *   **unchecked**, with one exception below.
 *
 * ### The exception: a file set aside keeps its confirmation
 *
 * Ian, 19 Sept 2026: swipe a checked file out and back and its green tick did not return, and every
 * other tick vanished with it. Verification is about the file and OneDrive, not about whether the
 * file was on the list a moment ago, so when a file this check **confirmed** is swiped out its
 * confirmation is kept in [setAside], and swiping it back returns it with the tick, without
 * disturbing the rest. This is no more trust than the removal step already places in the check,
 * which does not ask OneDrive again at Yes.
 *
 * It applies only to a file that is unchanged (same name, size and modified time), so a file edited
 * while it was set aside is a different file and must be checked again. And only a *confirmed* file
 * is remembered: a red cross is not.
 *
 * **Otherwise the plan only ever loses files or gains unchecked ones, so nothing that was not
 * confirmed can become confirmed by this.** That is the property the removal step relies on, since it
 * acts on [ArchivePlan.confirmed] alone.
 *
 * When [checkFinished] and either a file that has never been checked joined or nothing confirmed is
 * left, the check on screen no longer describes the list: every mark is reset and
 * [ReconciledPlan.needsRecheck] is true, so the caller returns to waiting for a check rather than
 * saying "all files validated" about a file nobody validated, or offering to archive nothing.
 */
fun ArchivePlan.reconciledWith(
    files: ArchiveFiles,
    checkFinished: Boolean,
    setAside: Map<Long, ArchiveEntry> = emptyMap()
): ReconciledPlan {
    val archivable = files.toArchive.mapTo(HashSet()) { it.mediaStoreId }
    val optedOut = files.optedOut.mapTo(HashSet()) { it.mediaStoreId }

    val kept = entries.filter { it.item.mediaStoreId in archivable }
    val known = kept.mapTo(HashSet()) { it.item.mediaStoreId }
    val arrivals = files.toArchive.filter { it.mediaStoreId !in known }

    // Confirmations to carry: those already set aside, and any confirmed file swiped out just now.
    val carried = if (checkFinished) {
        setAside + entries
            .filter { it.mark == ArchiveMark.CONFIRMED && it.item.mediaStoreId in optedOut }
            .associateBy { it.item.mediaStoreId }
    } else {
        emptyMap()
    }

    val restored = arrivals
        .filter { item ->
            val before = carried[item.mediaStoreId]?.item
            before != null &&
                before.displayName == item.displayName &&
                before.album == item.album &&
                before.sizeBytes == item.sizeBytes &&
                before.dateModifiedEpochSeconds == item.dateModifiedEpochSeconds
        }
    val restoredIds = restored.mapTo(HashSet()) { it.mediaStoreId }

    val returning = restored.map { ArchiveEntry(it, mark = ArchiveMark.CONFIRMED) }
    val fresh = arrivals.filter { it.mediaStoreId !in restoredIds }.map { ArchiveEntry(it) }

    var merged = (kept + returning + fresh).sortedWith(compareBy({ it.album }, { it.name }))
    var needsRecheck = false
    var stillValidated = validated

    if (checkFinished && (fresh.isNotEmpty() || merged.none { it.mark == ArchiveMark.CONFIRMED })) {
        merged = merged.map { it.copy(mark = ArchiveMark.WAITING, failure = null) }
        needsRecheck = true
    }
    if (needsRecheck || fresh.isNotEmpty()) stillValidated = false

    val nextSetAside = if (needsRecheck) emptyMap() else carried.filterKeys { it !in restoredIds && it in optedOut }
    return ReconciledPlan(copy(entries = merged, validated = stillValidated), needsRecheck, nextSetAside)
}
