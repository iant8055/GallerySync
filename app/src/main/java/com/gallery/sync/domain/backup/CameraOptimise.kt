package com.gallery.sync.domain.backup

import com.gallery.sync.data.local.entity.BackupEntryEntity
import com.gallery.sync.data.local.entity.BackupState
import java.time.Duration
import java.time.Instant

/**
 * How old a Camera file must be before the album's *Optimise* control will offer it.
 *
 * Ian's list, 20 Sept 2026: 1 day, 1 week, 1 month, 6 months, 1 year. It is a separate scale from
 * [MediaAge] on purpose. That one gates the ongoing video setting and is measured in hours to a week;
 * this one is picked once, by hand, for a folder full of recent shots, and wants to reach months.
 *
 * Months are fixed lengths (30, 182 and 365 days) rather than calendar arithmetic: nothing here is
 * worth a time-zone question, and a file a day either side of the line is the user's own boundary
 * case, visible in the list before anything is done to it.
 */
enum class CameraOptimiseAge(val duration: Duration) {
    OneDay(Duration.ofDays(1)),
    OneWeek(Duration.ofDays(7)),
    OneMonth(Duration.ofDays(30)),
    SixMonths(Duration.ofDays(182)),
    OneYear(Duration.ofDays(365)),

    /**
     * Every file, whatever its age (Ian, 20 Sept 2026). Zero wait, so a file modified a moment ago is
     * in. It is the user's own choice made on this screen, with the list in front of them and a swipe to
     * leave any file out, which is what keeps it from being the "optimised the moment I took it" that
     * Sync would be.
     */
    All(Duration.ZERO);

    /** The newest modification time, in seconds, a file may have and still be old enough. */
    fun thresholdEpochSeconds(now: Instant): Long = now.minus(duration).epochSecond
}

/** The Settings the Camera control obeys. Ian, 20 Sept 2026: it respects the same switches as Sync does. */
data class CameraOptimiseSettings(
    /** The master switch: *Optimise photos and videos*. */
    val enabled: Boolean,
    val photos: Boolean,
    val videos: Boolean,
    /** What a photo or a clip is expected to lose, in whole percent. Only an estimate. */
    val photoSavingPercent: Int,
    val videoSavingPercent: Int
) {
    fun allows(entry: BackupEntryEntity): Boolean =
        enabled && if (entry.isVideo) videos else photos

    /** True when Settings would optimise nothing at all, which is worth saying instead of "no files". */
    val everythingOff: Boolean get() = !enabled || (!photos && !videos)
}

/**
 * What the Camera album's *Only list Photos/Videos older than…* control will do, worked out from
 * the ledger alone.
 *
 * ### This is a manual optimise of one folder, not a mode
 *
 * Ian, 20 Sept 2026: *"this isn't a Backup - this is basically a Manual Optimization for a single
 * folder."* Nothing here is remembered as a rule, nothing sets the album's mode, and nothing runs
 * until the person taps the button beside the count. The plan is only ever the answer to "if I tapped
 * now, which files would it touch?", which is why the list can show it before anything happens.
 *
 * ### One test, used by the screen and by the worker
 *
 * The screen draws the list from [of], and the worker that does the work calls [of] again with the
 * same cutoff, so what was shown is what is done. A file can only leave the plan between the two
 * (swiped out, already done, gone from the phone), never join it, because the cutoff is fixed when
 * the person taps and is handed to the worker rather than recomputed.
 *
 * ### Who is in it
 *
 * The same bar as every rewrite in this app: the file is uploaded **and** OneDrive reported the
 * size it has here, so the full-quality original is safe before the local copy shrinks. Then the
 * usual "not already smaller, not already declined", the age against the file's own modification
 * time, and the Settings switch for its kind.
 *
 * A file the user has swiped out is pinned (`FilePin`). It is kept in [optedOut] and never in
 * [eligible], which is the property everything downstream depends on: the worker acts on
 * [eligible] and on nothing else.
 */
data class CameraOptimisePlan(
    /** What will be optimised, largest first. */
    val eligible: List<BackupEntryEntity>,
    /** Files that qualify but that the user swiped out. Shown greyed, never touched. */
    val optedOut: List<BackupEntryEntity>,
    val settings: CameraOptimiseSettings
) {
    val photoCount: Int get() = eligible.count { !it.isVideo }
    val videoCount: Int get() = eligible.count { it.isVideo }

    /**
     * What it is expected to give back, in bytes.
     *
     * An estimate and labelled as one wherever it is drawn. It is the size times the measured saving
     * for that kind, so it over-promises for a photo that is already small (skipped, no saving) and
     * for a clip that will not shrink. Both are examined only when the work runs.
     */
    val estimatedSavedBytes: Long
        get() = eligible.sumOf { it.sizeBytes * percentFor(it) / 100 }

    private fun percentFor(entry: BackupEntryEntity): Int =
        if (entry.isVideo) settings.videoSavingPercent else settings.photoSavingPercent

    companion object {

        fun of(
            entries: List<BackupEntryEntity>,
            modifiedBeforeEpochSeconds: Long,
            settings: CameraOptimiseSettings
        ): CameraOptimisePlan {
            val qualifying = entries
                .filter { isReady(it, modifiedBeforeEpochSeconds) && settings.allows(it) }
                .sortedByDescending { it.sizeBytes }
            val (pinned, free) = qualifying.partition { FilePin.isPinned(it.modeOverride) }
            return CameraOptimisePlan(eligible = free, optedOut = pinned, settings = settings)
        }

        /** The ledger's half of the test; the phone's half (still there? writable?) is asked at run time. */
        internal fun isReady(entry: BackupEntryEntity, modifiedBeforeEpochSeconds: Long): Boolean =
            entry.state == BackupState.UPLOADED &&
                entry.remoteSizeBytes != null &&
                entry.remoteSizeBytes == entry.sizeBytes &&
                !entry.isProxied &&
                !entry.isProxySkipped &&
                entry.localMissingSinceEpochMillis == null &&
                entry.dateModifiedEpochSeconds <= modifiedBeforeEpochSeconds
    }
}
