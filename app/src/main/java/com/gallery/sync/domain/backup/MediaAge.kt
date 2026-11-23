package com.gallery.sync.domain.backup

import java.time.Duration
import java.time.Instant

/**
 * How old a clip must be before Sync's video optimizing may reach it: Settings → Sync → *Older than*.
 *
 * **The same two menus as Archive's *Only show files older than*** (Ian, 14 Nov 2026: a number from
 * 1 to 9 and hour, day, week, month or year, with the zero entry reading *Straight away*). The value is
 * an [ArchiveAge]; this object holds only what differs, the default and the names the old fixed list
 * (Straight away, 1 hour, 12 hours, 1 day, 1 week) saved. 12 hours has no place on a 1-9 scale and reads
 * as 1 day, the side that waits longer, so a saved choice can only ever optimize less than it did.
 *
 * **One vocabulary, every setting.** Ian has held to this through two revisions: on 19 Aug 2026 the
 * values were Immediately / 1 week / 1 month / 1 year, and on 28 Aug he replaced them with hours
 * after deciding photos and video should be asked about the same way. A user made to learn two sets
 * of ages has been handed the app's internal seams as homework.
 *
 * ### The shorter scale changes something real, and it should not pass unnoticed
 *
 * MILESTONES calls *"recent video is never touched"* the requirement rather than a compromise, and
 * under a one-hour age a clip shot this morning can be reduced to 480p. Two things make that
 * defensible, and both are evidence rather than argument:
 *
 * - **The founding failure was absence, not quality.** Ian could not find a video ten minutes after
 *   shooting it, because backing up had moved it off the phone. Optimizing leaves the clip in the
 *   gallery, playing normally, under its own name. What it costs is detail, not availability.
 * - **480p proved indistinguishable** on the Fold's inner display, the hardest screen this app runs
 *   on. A wait measured in months was protecting against a difference nobody could see.
 *
 * What survives is the edit case: an edit started from the smaller copy begins with less detail
 * until the original is fetched back. That is what the age gate is now for, and why the shortest
 * option carries a warning.
 *
 * ### What it gates, and what it must never gate
 *
 * **Only the local operation.** A clip is uploaded the moment it qualifies, whatever its age. An age
 * threshold that held new video out of OneDrive would rebuild the founding failure — recording
 * something and finding it unavailable minutes later — while wearing the name of the fix. MILESTONES
 * is explicit: *"It gates downscaling only and never uploading."*
 *
 * ### It is asked of each file, not of the setting
 *
 * The value is global; the **test is per file**, against that file's own modification time. So
 * "1 day" does not mean "wait a day and then do everything" — it means each file becomes eligible one
 * day after it was last modified.
 *
 * The consequence worth stating in the UI: **within a Sync album, turning this on makes everything
 * old eligible at once**, because every old file passed its threshold long ago. The age gate only
 * ever holds back the recent end; it does nothing about the four hundred clips shot before that.
 *
 * **Scoped to Sync albums**, which Ian pointed out when an earlier version of this comment said
 * "entire back catalogue". `proxyCandidates` joins on `album_preferences WHERE mode = SYNC`, so
 * nothing outside a Sync album is ever a candidate.
 *
 * That is a real limit and not quite a reassuring one. Gate 2's *"Back up and free space"* sets the
 * optimize cutoff to `EVERYTHING`, so every file in a Sync album is in range from the first sweep.
 * The whole-library case is one tap away at setup, taken by someone who has not yet met this
 * setting — which is an argument for the first sweep being announced rather than for the age value
 * being expected to restrain it.
 *
 * Note what that tap does *not* do: no install choice writes an album's mode, only the user does.
 * The bulk mode writer that once existed was deleted with the orphaned wizard in TASK-022.
 *
 * ### Measured against modification time
 *
 * `dateModifiedEpochSeconds`, which the scanner already carries and which needs no schema change. It
 * errs toward leaving files alone: a clip that has been edited looks recent again, and recent means
 * untouched.
 */
object MediaAge {

    /**
     * A day, the cautious end of the old short scale, and still the default.
     */
    val DEFAULT = ArchiveAge(1, ArchiveAgeUnit.Day)

    /** The zero entry, *Straight away*: a clip shot this morning qualifies. */
    val Immediately = ArchiveAge.All

    private val OLD_NAMES = mapOf(
        "Immediately" to Immediately,
        "OneHour" to ArchiveAge(1, ArchiveAgeUnit.Hour),
        "TwelveHours" to ArchiveAge(1, ArchiveAgeUnit.Day),
        "OneDay" to ArchiveAge(1, ArchiveAgeUnit.Day),
        "OneWeek" to ArchiveAge(1, ArchiveAgeUnit.Week)
    )

    fun fromNameOrDefault(name: String?): ArchiveAge =
        name?.let { OLD_NAMES[it] ?: ArchiveAge.parse(it) } ?: DEFAULT
}
