package com.gallery.sync.domain.backup

import com.gallery.sync.data.local.media.LocalMediaItem
import java.time.Duration
import java.time.Instant

/**
 * How old a file must be before the Archive tab's *Only show files older than* control will offer
 * it for checking.
 *
 * Ian's list, 22 Sept 2026: 1 hour, 1 day, 1 week, 1 month, 1 year, All. A separate scale from
 * [CameraOptimiseAge] on purpose — that one is picked once, by hand, for a folder full of recent
 * shots; this one starts from a Settings default and governs every Archive album, and Archive
 * removes a file outright rather than shrinking it, so the shortest step is tighter (an hour, not a
 * day) to give a genuinely fresh file a real guard.
 *
 * ### This narrows what is offered, never what is protected
 *
 * A file younger than the cutoff is simply not in the set *Check these files* acts on this round —
 * it is not touched, not marked, not counted against the confirmed set. It still shows on the
 * Archive tab, greyed, the same way an opted-out file does, and it still leaves the album entirely
 * if the user swipes it out (`FilePin`), which is a permanent choice independent of this one, a
 * temporary view of the moment. Widening the filter, or waiting, brings it back into the set on its
 * own; nothing here forgets a file the way an opt-out does.
 */
enum class ArchiveAge(val duration: Duration) {
    OneHour(Duration.ofHours(1)),
    OneDay(Duration.ofDays(1)),
    OneWeek(Duration.ofDays(7)),
    OneMonth(Duration.ofDays(30)),
    OneYear(Duration.ofDays(365)),

    /** No wait at all: a file modified this very minute qualifies. The Settings default. */
    All(Duration.ZERO);

    /** The newest modification time, in seconds, a file may have and still be old enough. */
    fun thresholdEpochSeconds(now: Instant = Instant.now()): Long = now.minus(duration).epochSecond

    companion object {

        /**
         * Out of the box this changes nothing: every Archive-eligible file is offered, exactly as
         * before this control existed. Matches the app's rule that no default narrows what already
         * happens — see `project_defaults_contract`.
         */
        val DEFAULT = All

        fun fromNameOrDefault(name: String?): ArchiveAge =
            entries.firstOrNull { it.name == name } ?: DEFAULT
    }
}

/** What [ArchiveAge.split] returns: the files old enough to offer, and the ones held back by age. */
data class ArchiveAgeSplit(
    val eligible: List<LocalMediaItem>,
    val hiddenByAge: List<LocalMediaItem>
)

/**
 * Splits files in Archive albums by [age], measured against each file's own modification time —
 * the same field [CameraOptimiseAge] reads, needing no schema change.
 *
 * A file exactly on the line is old enough; the test is `<=`, matching `CameraOptimisePlan.isReady`.
 */
fun ArchiveAge.split(items: List<LocalMediaItem>, now: Instant = Instant.now()): ArchiveAgeSplit {
    val threshold = thresholdEpochSeconds(now)
    val (eligible, hidden) = items.partition { it.dateModifiedEpochSeconds <= threshold }
    return ArchiveAgeSplit(eligible = eligible, hiddenByAge = hidden)
}
