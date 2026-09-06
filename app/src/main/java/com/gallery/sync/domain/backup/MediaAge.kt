package com.gallery.sync.domain.backup

import java.time.Duration
import java.time.Instant

/**
 * How old a file must be before something is allowed to happen to it.
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
 *   shooting it, because backing up had moved it off the phone. Optimising leaves the clip in the
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
 * That is a real limit and not quite a reassuring one. Gate 2's *"Back up and free space"* maps to
 * `AlbumMode.SYNC` and `ApplyLibraryChoice` applies it to **every album at once**, with `REPLACE`.
 * So the whole-library case is one tap away at setup, taken by someone who has not yet met this
 * setting — which is an argument for the first sweep being announced rather than for the age value
 * being expected to restrain it.
 *
 * ### Measured against modification time
 *
 * `dateModifiedEpochSeconds`, which the scanner already carries and which needs no schema change. It
 * errs toward leaving files alone: a clip that has been edited looks recent again, and recent means
 * untouched.
 */
enum class MediaAge(val duration: Duration) {

    /**
     * No wait at all.
     *
     * Honest about what it costs, and the cost is real: **it reaches a clip shot this morning.** That
     * sits in tension with "recent video is never touched", which is the requirement rather than a
     * compromise — the clip stays in the gallery and stays playable, but an edit from it starts from
     * the smaller copy until the original is fetched back. MILESTONES flags this rather than
     * pretending the tension is resolved, and any UI offering it has to say so.
     */
    Immediately(Duration.ZERO),

    OneHour(Duration.ofHours(1)),

    TwelveHours(Duration.ofHours(12)),

    OneDay(Duration.ofDays(1)),

    OneWeek(Duration.ofDays(7));

    /** True when a file last modified at [modifiedAt] is old enough for this threshold. */
    fun hasElapsedFor(modifiedAt: Instant, now: Instant): Boolean =
        !modifiedAt.plus(duration).isAfter(now)

    companion object {

        /**
         * A day — the longest wait on offer, and the cautious end of a short scale.
         *
         * Chosen here rather than by Ian, who specified the four values and not which one leads.
         * Worth changing if he disagrees: it is one constant.
         */
        val DEFAULT = OneDay

        fun fromNameOrDefault(name: String?): MediaAge =
            entries.firstOrNull { it.name == name } ?: DEFAULT
    }

    /**
     * The `dateModified` a file must be at or below to have waited out this age.
     *
     * Shared so photos and video answer it the same way. Video had a private copy and photos had no
     * age check at all, which is how `photoOptimiseAge` came to be a Settings control that changed
     * nothing — see the 6 Sept 2026 entry.
     */
    fun thresholdEpochSeconds(now: Instant = Instant.now()): Long =
        now.minus(duration).epochSecond
}
