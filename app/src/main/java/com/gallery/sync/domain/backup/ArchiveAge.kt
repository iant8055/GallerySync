package com.gallery.sync.domain.backup

import com.gallery.sync.data.local.media.LocalMediaItem
import java.time.Duration
import java.time.Instant

/**
 * How old a file must be before the Archive tab's *Only show files older than* control will offer
 * it for checking.
 *
 * A number from 1 to 9 and a unit, or All (Ian, 2 Nov 2026; until then a fixed list of 1 hour, 1 day,
 * 1 week, 1 month, 1 year and All, which are still what a value saved back then reads as). A month is
 * 30 days and a year 365, as they always were here. A separate scale from
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
data class ArchiveAge(val count: Int, val unit: ArchiveAgeUnit) {

    val duration: Duration get() = unit.duration.multipliedBy(count.toLong())

    /** What Settings stores. Read back by [fromNameOrDefault]. */
    val storedName: String get() = if (unit == ArchiveAgeUnit.All) unit.name else "$count ${unit.name}"

    /** The newest date, in seconds, a file may have been taken and still be old enough. */
    fun thresholdEpochSeconds(now: Instant = Instant.now()): Long = now.minus(duration).epochSecond

    companion object {

        /**
         * Out of the box this changes nothing: every Archive-eligible file is offered, exactly as
         * before this control existed. Matches the app's rule that no default narrows what already
         * happens — see `project_defaults_contract`.
         */
        val All = ArchiveAge(1, ArchiveAgeUnit.All)
        val DEFAULT = All

        /** What the number's menu offers. */
        val COUNTS = 1..9

        /** The names the fixed list saved, so a default chosen before 2 Nov 2026 reads as what it was. */
        private val OLD_NAMES = mapOf(
            "OneHour" to ArchiveAge(1, ArchiveAgeUnit.Hour),
            "OneDay" to ArchiveAge(1, ArchiveAgeUnit.Day),
            "OneWeek" to ArchiveAge(1, ArchiveAgeUnit.Week),
            "OneMonth" to ArchiveAge(1, ArchiveAgeUnit.Month),
            "OneYear" to ArchiveAge(1, ArchiveAgeUnit.Year)
        )

        fun fromNameOrDefault(name: String?): ArchiveAge {
            if (name == null) return DEFAULT
            OLD_NAMES[name]?.let { return it }
            val parts = name.split(' ')
            val count = parts.getOrNull(0)?.toIntOrNull()
            val unit = ArchiveAgeUnit.entries.firstOrNull { it.name == parts.getOrNull(1) }
            return if (count != null && count in COUNTS && unit != null && unit != ArchiveAgeUnit.All) {
                ArchiveAge(count, unit)
            } else {
                DEFAULT
            }
        }
    }
}

/** The unit's menu, in order. */
enum class ArchiveAgeUnit(val duration: Duration) {
    Hour(Duration.ofHours(1)),
    Day(Duration.ofDays(1)),
    Week(Duration.ofDays(7)),
    Month(Duration.ofDays(30)),
    Year(Duration.ofDays(365)),

    /** No wait at all: a file taken this very minute qualifies. The Settings default. */
    All(Duration.ZERO)
}

/** What [ArchiveAge.split] returns: the files old enough to offer, and the ones held back by age. */
data class ArchiveAgeSplit(
    val eligible: List<LocalMediaItem>,
    val hiddenByAge: List<LocalMediaItem>
)

/**
 * Splits files in Archive albums by [age], measured against when each file was taken, as
 * [CameraOptimiseAge] is. Not its modified date, which optimising rewrites: every file in a Sync album
 * would read as new (Ian, 1 Nov 2026).
 *
 * A file exactly on the line is old enough; the test is `<=`, matching `CameraSelection.matches`.
 */
fun ArchiveAge.split(items: List<LocalMediaItem>, now: Instant = Instant.now()): ArchiveAgeSplit {
    val threshold = thresholdEpochSeconds(now)
    val (eligible, hidden) = items.partition { it.dateForSorting <= threshold }
    return ArchiveAgeSplit(eligible = eligible, hiddenByAge = hidden)
}
