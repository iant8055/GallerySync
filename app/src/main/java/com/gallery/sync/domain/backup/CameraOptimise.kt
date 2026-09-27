package com.gallery.sync.domain.backup

import com.gallery.sync.data.local.entity.BackupEntryEntity
import com.gallery.sync.data.local.entity.BackupState
import java.time.Duration
import java.time.Instant

/**
 * How old a Camera file must be for the header's age to select it.
 *
 * Ian's list, 20 Sept 2026: 1 day, 1 week, 1 month, 6 months, 1 year. It is a separate scale from
 * [MediaAge] on purpose. That one gates the ongoing video setting and is measured in hours to a week;
 * this one is picked by hand for a folder full of recent shots, and wants to reach months.
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

    companion object {
        /** All, out of the box (Ian, 27 Sept 2026). With Photos and Videos both off it still selects nothing. */
        val DEFAULT = All

        fun fromNameOrDefault(name: String?): CameraOptimiseAge =
            entries.firstOrNull { it.name == name } ?: DEFAULT
    }
}

/**
 * The three choices on the Camera screen's header: how old, and which kinds.
 *
 * They start at the Camera defaults in Settings and can be changed on the screen for that visit. Leaving with a
 * choice that differs from the defaults asks whether to make it the default (Ian, 27 Sept 2026); nothing
 * else writes them back.
 */
data class CameraOptimiseChoice(
    val age: CameraOptimiseAge,
    val photos: Boolean,
    val videos: Boolean
) {
    /** Neither kind is chosen, so the header selects nothing. The list still shows every file. */
    val nothingChosen: Boolean get() = !photos && !videos

    companion object {
        /** Settings' defaults out of the box: both kinds off, age All. Ian, 27 Sept 2026. */
        val DEFAULT = CameraOptimiseChoice(CameraOptimiseAge.DEFAULT, photos = false, videos = false)
    }
}

/**
 * Which files in the camera folder *Sync now* will optimise.
 *
 * ### What is listed and what is selected are separate (Ian, 27 Sept 2026)
 *
 * The screen lists **every** file in the folder, always; nothing on the header or in Settings hides one.
 * The header's age, Photos and Videos *select* files, highlighted as on the Restore tab, and the person
 * can then swipe any file right to select it or left to deselect it. Changing a header choice re-selects
 * by the new choice. The selection lasts for the visit and is not stored; it is what *Sync now* acts on.
 *
 * ### Which files can be selected
 *
 * The same bar as every rewrite in this app: uploaded **and** OneDrive reported the size it has here, so the
 * full-quality original is safe before the local copy shrinks; not already smaller, not already declined,
 * still on the phone. Anything else is listed, greyed, with its usual marks, and cannot be selected.
 *
 * ### What the header selects
 *
 * Every selectable file old enough for the age whose kind is chosen, **except** a file kept at full size
 * ([FilePin], set by Restore): that one starts deselected. Swiping it in selects it for this run, and
 * *Sync now* then clears its pin, so the pin never disagrees with what was done.
 */
object CameraSelection {

    /** Whether [entry] can be optimised at all. Not selectable means listed, greyed, and never acted on. */
    fun isSelectable(entry: BackupEntryEntity): Boolean =
        entry.state == BackupState.UPLOADED &&
            entry.remoteSizeBytes != null &&
            entry.remoteSizeBytes == entry.sizeBytes &&
            !entry.isProxied &&
            !entry.isProxySkipped &&
            entry.localMissingSinceEpochMillis == null

    /** Whether the header's [choice], with its age turned into [modifiedBeforeEpochSeconds], selects [entry]. */
    fun matches(entry: BackupEntryEntity, choice: CameraOptimiseChoice, modifiedBeforeEpochSeconds: Long): Boolean =
        isSelectable(entry) &&
            !FilePin.isPinned(entry.modeOverride) &&
            (if (entry.isVideo) choice.videos else choice.photos) &&
            entry.dateModifiedEpochSeconds <= modifiedBeforeEpochSeconds

    /** What the header selects: the starting selection, and the one a changed choice replaces it with. */
    fun byChoice(
        entries: List<BackupEntryEntity>,
        choice: CameraOptimiseChoice,
        modifiedBeforeEpochSeconds: Long
    ): Set<String> =
        entries.filter { matches(it, choice, modifiedBeforeEpochSeconds) }.mapTo(LinkedHashSet()) { it.id }

    /**
     * The files a run will optimise: those in [selectedIds] that can still be optimised, largest first. The only
     * list the worker acts on. A file that stopped being selectable since it was selected (done, gone, declined)
     * drops out here; nothing outside [selectedIds] can get in.
     */
    fun toOptimise(entries: List<BackupEntryEntity>, selectedIds: Set<String>): List<BackupEntryEntity> =
        entries.filter { it.id in selectedIds && isSelectable(it) }.sortedByDescending { it.sizeBytes }

    /**
     * What optimising [files] is expected to give back, in bytes.
     *
     * An estimate and labelled as one wherever it is drawn. It is the size times the measured saving for that
     * kind, so it over-promises for a photo that is already small (skipped, no saving) and for a clip that will
     * not shrink. Both are examined only when the work runs.
     */
    fun estimatedSavedBytes(files: List<BackupEntryEntity>, photoSavingPercent: Int, videoSavingPercent: Int): Long =
        files.sumOf { it.sizeBytes * (if (it.isVideo) videoSavingPercent else photoSavingPercent) / 100 }
}
